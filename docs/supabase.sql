-- Cricket Manager Pro - Supabase schema (exported from the live project's catalog, migrations
-- cmp_accounts_and_saves, cmp_social_feed, cmp_fix_delete_account). Run in a fresh project's SQL editor
-- to reproduce the backend. Every user-owned row is protected by Row Level Security.

-- ========== accounts ==========
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique check (char_length(username) between 3 and 20 and username ~ '^[A-Za-z0-9_]+$'),
  display_name text check (display_name is null or char_length(display_name) <= 40),
  avatar_path text,
  coins integer not null default 1000 check (coins between 0 and 100000000),
  xp integer not null default 0 check (xp >= 0),
  settings jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index profiles_username_lower on public.profiles (lower(username));

create table public.career_saves (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  slot smallint not null check (slot between 1 and 3),
  mode text not null default 'CLUB' check (mode in ('CLUB','INTL','DOMESTIC','IPL')),
  manager text, club text, season integer default 1,
  data text not null check (octet_length(data) < 8000000),
  updated_at timestamptz not null default now(),
  primary key (user_id, slot)
);

create or replace function public.touch_updated_at() returns trigger language plpgsql set search_path = '' as
$$ begin new.updated_at = now(); return new; end; $$;
create trigger profiles_touch before update on public.profiles for each row execute function public.touch_updated_at();
create trigger saves_touch before update on public.career_saves for each row execute function public.touch_updated_at();

create or replace function public.handle_new_user() returns trigger language plpgsql security definer set search_path = '' as $$
declare u text := coalesce(nullif(regexp_replace(new.raw_user_meta_data->>'username', '[^A-Za-z0-9_]', '', 'g'), ''), 'coach_' || substr(replace(new.id::text, '-', ''), 1, 8));
begin
  if char_length(u) < 3 then u := u || '_' || substr(replace(new.id::text, '-', ''), 1, 4); end if;
  u := left(u, 20);
  if exists (select 1 from public.profiles p where lower(p.username) = lower(u)) then
    u := left(u, 15) || '_' || substr(replace(new.id::text, '-', ''), 1, 4);
  end if;
  insert into public.profiles (id, username, display_name) values (new.id, u, left(coalesce(new.raw_user_meta_data->>'display_name', u), 40));
  return new;
end; $$;
create trigger on_auth_user_created after insert on auth.users for each row execute function public.handle_new_user();

alter table public.profiles enable row level security;
alter table public.career_saves enable row level security;
create policy "profiles: owner can read"   on public.profiles for select using ((select auth.uid()) = id);
create policy "profiles: owner can insert" on public.profiles for insert with check ((select auth.uid()) = id);
create policy "profiles: owner can update" on public.profiles for update using ((select auth.uid()) = id) with check ((select auth.uid()) = id);
create policy "saves: owner can read"   on public.career_saves for select using ((select auth.uid()) = user_id);
create policy "saves: owner can insert" on public.career_saves for insert with check ((select auth.uid()) = user_id);
create policy "saves: owner can update" on public.career_saves for update using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "saves: owner can delete" on public.career_saves for delete using ((select auth.uid()) = user_id);

create or replace function public.username_available(name text) returns boolean language sql stable security definer set search_path = '' as $$
  select char_length(name) between 3 and 20 and name ~ '^[A-Za-z0-9_]+$' and not exists (select 1 from public.profiles where lower(username) = lower(name));
$$;

create or replace function public.delete_my_account() returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  delete from auth.users where id = auth.uid();   -- cascades to profiles, saves, social rows
end; $$;
revoke execute on function public.delete_my_account() from anon;

-- private avatar bucket: <uid>/avatar.jpg, owner-only
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', false, 1048576, array['image/jpeg','image/png','image/webp']);
create policy "avatars: owner read"   on storage.objects for select using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "avatars: owner insert" on storage.objects for insert with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "avatars: owner update" on storage.objects for update using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "avatars: owner delete" on storage.objects for delete using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- ========== social feed (only posts the user explicitly shares) ==========
create table public.social_posts (
  id bigint generated always as identity primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  author_name text not null default '',
  club text not null default '' check (char_length(club) <= 40),
  kind text not null default 'POST' check (kind ~ '^[A-Z_]{2,20}$'),
  body text not null check (char_length(body) between 1 and 400),
  likes_count integer not null default 0,
  comments_count integer not null default 0,
  created_at timestamptz not null default now()
);
create index social_posts_created_idx on public.social_posts (created_at desc);
create index social_posts_user_idx on public.social_posts (user_id);

create table public.social_likes (
  post_id bigint not null references public.social_posts(id) on delete cascade,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);
create table public.social_comments (
  id bigint generated always as identity primary key,
  post_id bigint not null references public.social_posts(id) on delete cascade,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  author_name text not null default '',
  body text not null check (char_length(body) between 1 and 200),
  created_at timestamptz not null default now()
);
create index social_comments_post_idx on public.social_comments (post_id, created_at);
create table public.social_follows (
  follower uuid not null default auth.uid() references auth.users(id) on delete cascade,
  followee uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower, followee),
  check (follower <> followee)
);

create or replace function public.social_set_author() returns trigger language plpgsql security definer set search_path = 'public' as $$
begin
  new.user_id := auth.uid();
  select coalesce(nullif(p.display_name,''), p.username) into new.author_name from public.profiles p where p.id = auth.uid();
  new.author_name := coalesce(new.author_name, 'manager');
  if tg_table_name = 'social_posts' then
    new.likes_count := 0; new.comments_count := 0; new.created_at := now();
    if (select count(*) from public.social_posts s where s.user_id = auth.uid() and s.created_at > now() - interval '1 hour') >= 30 then
      raise exception 'post limit reached, try again later';
    end if;
  end if;
  return new;
end $$;
create trigger social_posts_author before insert on public.social_posts for each row execute function public.social_set_author();
create trigger social_comments_author before insert on public.social_comments for each row execute function public.social_set_author();

create or replace function public.social_counts() returns trigger language plpgsql security definer set search_path = 'public' as $$
begin
  if tg_table_name = 'social_likes' then
    update public.social_posts set likes_count = (select count(*) from public.social_likes l where l.post_id = coalesce(new.post_id, old.post_id)) where id = coalesce(new.post_id, old.post_id);
  else
    update public.social_posts set comments_count = (select count(*) from public.social_comments c where c.post_id = coalesce(new.post_id, old.post_id)) where id = coalesce(new.post_id, old.post_id);
  end if;
  return null;
end $$;
create trigger social_likes_count after insert or delete on public.social_likes for each row execute function public.social_counts();
create trigger social_comments_count after insert or delete on public.social_comments for each row execute function public.social_counts();

create or replace function public.follower_count(uid uuid) returns integer language sql stable security definer set search_path = 'public' as $$
  select count(*)::int from public.social_follows where followee = uid;
$$;

alter table public.social_posts enable row level security;
alter table public.social_likes enable row level security;
alter table public.social_comments enable row level security;
alter table public.social_follows enable row level security;
create policy posts_read   on public.social_posts for select to authenticated using (true);
create policy posts_insert on public.social_posts for insert to authenticated with check (user_id = auth.uid());
create policy posts_delete on public.social_posts for delete to authenticated using (user_id = auth.uid());
create policy likes_read_own on public.social_likes for select to authenticated using (user_id = auth.uid());
create policy likes_insert   on public.social_likes for insert to authenticated with check (user_id = auth.uid());
create policy likes_delete   on public.social_likes for delete to authenticated using (user_id = auth.uid());
create policy comments_read   on public.social_comments for select to authenticated using (true);
create policy comments_insert on public.social_comments for insert to authenticated with check (user_id = auth.uid());
create policy comments_delete on public.social_comments for delete to authenticated using (user_id = auth.uid());
create policy follows_read_own on public.social_follows for select to authenticated using (follower = auth.uid() or followee = auth.uid());
create policy follows_insert   on public.social_follows for insert to authenticated with check (follower = auth.uid());
create policy follows_delete   on public.social_follows for delete to authenticated using (follower = auth.uid());
revoke all on public.social_posts, public.social_likes, public.social_comments, public.social_follows from anon;
