package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*

/*
 * Yorker (v2.6) - the in-game text + photo network. Original name, mark (three stumps and a ball) and layout; it does
 * not use any third-party logo or branding. Accent: the design system's single cool accent.
 */

private val YAccent = DS.Accent

/** Yorker wordmark: the stumps-and-ball mark in a rounded tile + the name. */
@Composable
fun YorkerMark(size: Dp = 26.dp, withName: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = Yorker.NAME }) {
        Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(YAccent), contentAlignment = Alignment.Center) { Glyph("chat", Color.White, size * 0.7f) }
        if (withName) {
            Spacer(Modifier.width(8.dp))
            Text(Yorker.NAME, color = DS.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.8f).sp, letterSpacing = 0.5.sp, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun YorkerScreen(vm: GameVM) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val tk = vm.tick
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(s) { if (!s.yorker.seeded) { Yorker.ensureSeeded(s); vm.persist() } }
    LaunchedEffect(s.yorker.posts.size) { vm.markYorkerRead() }
    ScreenScaffold(vm, Yorker.NAME) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 820.dp
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().background(DS.Navy2).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        YorkerMark(24.dp)
                        Spacer(Modifier.width(14.dp))
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for ((i, l) in listOf("Home", "Trending", "Following", "Search").withIndex()) YTab(l, tab == i) { tab = i }
                        }
                        YIcon(Icons.Filled.Person, "My Yorker profile") { vm.nav(Screen.YorkerProfile("me")) }
                    }
                    when (tab) {
                        0 -> Feed(vm, s, Yorker.home(s), header = { Composer(vm, s) })
                        1 -> TrendingTab(vm, s) { t -> query = "#$t"; tab = 3 }
                        2 -> {
                            val f = Yorker.followingFeed(s)
                            if (f.isEmpty()) Empty("You are not following anyone yet. Follow players and teams from Search or their profiles.") else Feed(vm, s, f)
                        }
                        else -> SearchTab(vm, s, query) { query = it }
                    }
                }
                if (wide) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(DS.Line))
                    Column(Modifier.width(280.dp).fillMaxHeight().padding(12.dp)) {
                        DsLabel("Trending")
                        Spacer(Modifier.height(4.dp))
                        Yorker.trending(s, 6).forEach { (t, n) -> TrendRow(t, n) { query = "#$t"; tab = 3 } }
                        Spacer(Modifier.height(12.dp))
                        DsLabel("Who to follow")
                        Spacer(Modifier.height(4.dp))
                        Yorker.suggestions(s, 4).forEach { a -> AcctRow(vm, s, a, compact = true) }
                        Spacer(Modifier.weight(1f))
                        Text(Yorker.ESTIMATE_NOTE, color = DS.Muted, fontSize = 9.sp, lineHeight = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun YTab(label: String, on: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(6.dp)).semantics { contentDescription = label }.clickable(role = Role.Tab) { GameAudio.click(); onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = if (on) DS.White else DS.Sub, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, maxLines = 1, softWrap = false)
        Box(Modifier.padding(top = 3.dp).width(22.dp).height(2.dp).background(if (on) YAccent else Color.Transparent))
    }
}

@Composable
private fun YIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color = DS.Sub, onClick: () -> Unit) =
    Icon(icon, desc, tint = tint, modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { GameAudio.click(); onClick() }.padding(10.dp))

@Composable
private fun Empty(t: String) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(t, color = DS.Sub, fontSize = 12.sp) }

@Composable
private fun Feed(vm: GameVM, s: CareerSave, posts: List<YorkerPost>, header: (@Composable () -> Unit)? = null) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (header != null) item { header() }
        if (posts.isEmpty()) item { Text("Nothing here yet. Play or simulate matches - results, signings and big performances appear here automatically.", color = DS.Sub, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
        items(posts, key = { it.id }) { p -> YPost(vm, s, p) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Composer(vm: GameVM, s: CareerSave) {
    var text by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().background(DS.Navy).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            YAvatar(s, "me", 36.dp)
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(text, { text = it.take(280) }, placeholder = { Text("What's happening in your dressing room?", fontSize = 12.sp) }, modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp), minLines = 1, maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = YAccent, unfocusedBorderColor = DS.Line, cursorColor = YAccent, focusedTextColor = DS.White, unfocusedTextColor = DS.White))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(46.dp))
            // attach one of the team photos (recoloured to your team)
            Text(if (photo.isEmpty()) "+ Photo" else "Photo: $photo ✕", color = YAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                    photo = when (photo) { "" -> "win"; "win" -> "trophy"; else -> "" }
                }.padding(horizontal = 6.dp, vertical = 8.dp))
            Spacer(Modifier.weight(1f))
            Text("${280 - text.length}", color = if (text.length > 260) DS.Bad else DS.Muted, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            DsButton("Post", height = DS.BtnHSmall, enabled = text.isNotBlank()) { if (Yorker.userPost(s, text, photo) != null) { text = ""; photo = ""; vm.persist() } }
        }
        if (photo.isNotEmpty()) PhotoMoment(photo, s.clubs[s.userClubId]?.primary ?: 0xFF4F8EF7, Modifier.padding(start = 46.dp, top = 6.dp).size(120.dp).clip(RoundedCornerShape(8.dp)))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(DS.Line))
    }
}

/** Avatar for any Yorker account: player face, club badge, the coach's monogram or the media desk mark. */
@Composable
private fun YAvatar(s: CareerSave, key: String, size: Dp) {
    when {
        key.startsWith("p:") -> PlayerFace(key.substring(2), s.players[key.substring(2)]?.name ?: "", size, DS.Line)
        key.startsWith("t:") -> Box(Modifier.size(size).clip(CircleShape).background(DS.Navy3).border(1.dp, DS.Line, CircleShape), contentAlignment = Alignment.Center) { ClubBadge(s.clubs[key.substring(2)], size * 0.8f) }
        key.startsWith("m:") -> Box(Modifier.size(size).clip(CircleShape).background(DS.Navy4).border(1.dp, DS.Gold.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) { Glyph("chat", DS.Gold, size * 0.6f) }
        else -> Box(Modifier.size(size).clip(CircleShape).background(DS.Gold), contentAlignment = Alignment.Center) { Text(s.manager.take(1).uppercase(), color = DS.GoldInk, fontWeight = FontWeight.Black, fontSize = (size.value * 0.42f).sp) }
    }
}

/** Post text with #hashtags and @handles in the accent colour. */
private fun richText(t: String) = buildAnnotatedString {
    var i = 0
    val rx = Regex("[#@][\\w]+")
    for (m in rx.findAll(t)) {
        append(t.substring(i, m.range.first))
        withStyle(SpanStyle(color = YAccent)) { append(m.value) }
        i = m.range.last + 1
    }
    append(t.substring(i))
}

@Composable
private fun YPost(vm: GameVM, s: CareerSave, p: YorkerPost, inThread: Boolean = false) {
    val a = remember(p.author, vm.tick) { Yorker.account(s, p.author) } ?: return
    Column(Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().clickable(enabled = !inThread) { GameAudio.click(); vm.nav(Screen.YorkerThread(p.id)) }.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row {
            Box(Modifier.clickable { vm.nav(Screen.YorkerProfile(p.author)) }) { YAvatar(s, p.author, 40.dp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.name, color = DS.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (a.verified) { Spacer(Modifier.width(3.dp)); Icon(Icons.Filled.CheckCircle, "verified", tint = YAccent, modifier = Modifier.size(13.dp)) }
                    Spacer(Modifier.width(4.dp))
                    Text("${a.handle} · ${p.date.ifBlank { "S${p.season} D${p.day}" }}", color = DS.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                if (p.kind in setOf("RESULT", "AUCTION", "TRANSFER", "TROPHY", "CENTURY", "FIVEFOR", "HATTRICK")) Text(when (p.kind) { "RESULT" -> "MATCH UPDATE"; "AUCTION" -> "AUCTION"; "TRANSFER" -> "TRANSFER NEWS"; "TROPHY" -> "CHAMPIONS"; "FIVEFOR" -> "FIVE-FOR"; "HATTRICK" -> "HAT-TRICK"; else -> p.kind },
                    color = DS.Gold, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                Text(richText(p.text), color = Color(0xFFE4E9F2), fontSize = if (inThread) 15.sp else 13.sp, lineHeight = if (inThread) 20.sp else 18.sp, modifier = Modifier.padding(top = 2.dp))
                if (p.photo.isNotEmpty()) {
                    val col = s.clubs[p.club]?.primary ?: s.players[p.pid]?.clubId?.let { s.clubs[it]?.primary } ?: 0xFF4F8EF7
                    BoxWithConstraints(Modifier.padding(top = 8.dp).fillMaxWidth()) {
                        PhotoMoment(p.photo, col, Modifier.size(minOf(maxWidth, 360.dp)).clip(RoundedCornerShape(12.dp)).border(1.dp, DS.Line, RoundedCornerShape(12.dp)))
                    }
                }
                Row(Modifier.padding(top = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Act(Icons.Filled.Email, Stumps.compact(p.replies.toLong()), "Replies") { vm.nav(Screen.YorkerThread(p.id)) }
                    Act(Icons.Filled.Refresh, Stumps.compact(p.reposts.toLong()), if (p.reposted) "Undo repost" else "Repost", if (p.reposted) DS.Good else DS.Sub) { Yorker.toggleRepost(s, p); vm.persist() }
                    Act(if (p.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, Stumps.compact(p.likes.toLong()), if (p.liked) "Unlike" else "Like", if (p.liked) DS.Bad else DS.Sub) { Yorker.toggleLike(p); vm.persist() }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(DS.Line.copy(alpha = 0.6f)))
    }
}

@Composable
private fun Act(icon: androidx.compose.ui.graphics.vector.ImageVector, n: String, desc: String, tint: Color = DS.Sub, onClick: () -> Unit) {
    Row(Modifier.padding(end = 18.dp).clip(RoundedCornerShape(16.dp)).semantics { contentDescription = desc }.clickable(role = Role.Button) { GameAudio.click(); onClick() }
        .padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(" $n", color = tint, fontSize = 11.sp)
    }
}

@Composable
private fun TrendRow(tag: String, n: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { GameAudio.click(); onClick() }.padding(vertical = 6.dp, horizontal = 4.dp)) {
        Text("Trending in cricket", color = DS.Muted, fontSize = 9.sp)
        Text("#$tag", color = DS.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        Text("$n post${if (n == 1) "" else "s"}", color = DS.Muted, fontSize = 9.sp)
    }
}

@Composable
private fun TrendingTab(vm: GameVM, s: CareerSave, onTag: (String) -> Unit) {
    val tr = Yorker.trending(s, 12)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item { Column { Spacer(Modifier.height(8.dp)); DsLabel("Trending now"); Spacer(Modifier.height(4.dp)) } }
        if (tr.isEmpty()) item { Text("No trends yet - results and signings create them.", color = DS.Sub, fontSize = 12.sp) }
        items(tr, key = { it.first }) { (t, n) -> Column { TrendRow(t, n) { onTag(t) }; Box(Modifier.fillMaxWidth().height(1.dp).background(DS.Line.copy(alpha = 0.5f))) } }
        item { Column { Spacer(Modifier.height(12.dp)); DsLabel("Who to follow"); Spacer(Modifier.height(4.dp)) } }
        items(Yorker.suggestions(s, 6), key = { it.key }) { a -> AcctRow(vm, s, a) }
        item { Text(Yorker.ESTIMATE_NOTE, color = DS.Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)) }
    }
}

@Composable
private fun SearchTab(vm: GameVM, s: CareerSave, query: String, onQuery: (String) -> Unit) {
    val (accts, posts) = remember(query, vm.tick) { Yorker.search(s, query) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, { onQuery(it.take(40)) }, placeholder = { Text("Search players, teams, #tags", fontSize = 12.sp) }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = DS.Sub) }, modifier = Modifier.fillMaxWidth().padding(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = YAccent, unfocusedBorderColor = DS.Line, cursorColor = YAccent, focusedTextColor = DS.White, unfocusedTextColor = DS.White))
        LazyColumn(Modifier.weight(1f)) {
            if (query.trim().length < 2) item { Text("Type at least 2 letters.", color = DS.Sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
            if (accts.isNotEmpty()) { item { DsLabel("Accounts", Modifier.padding(horizontal = 12.dp)) }; items(accts, key = { "a" + it.key }) { a -> Box(Modifier.padding(horizontal = 12.dp)) { AcctRow(vm, s, a) } } }
            if (posts.isNotEmpty()) { item { DsLabel("Posts", Modifier.padding(start = 12.dp, top = 8.dp)) }; items(posts, key = { "p" + it.id }) { p -> YPost(vm, s, p) } }
            if (query.trim().length >= 2 && accts.isEmpty() && posts.isEmpty()) item { Text("No results for \"$query\".", color = DS.Sub, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun AcctRow(vm: GameVM, s: CareerSave, a: Yorker.Acct, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { GameAudio.click(); vm.nav(Screen.YorkerProfile(a.key)) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        YAvatar(s, a.key, if (compact) 32.dp else 38.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, color = DS.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (a.verified) { Spacer(Modifier.width(3.dp)); Icon(Icons.Filled.CheckCircle, "verified", tint = YAccent, modifier = Modifier.size(12.dp)) }
            }
            Text("${a.handle} · ${a.followersLabel}", color = DS.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (a.key != "me") FollowBtn(vm, s, a.key)
    }
}

@Composable
private fun FollowBtn(vm: GameVM, s: CareerSave, key: String) {
    val on = Yorker.isFollowing(s, key)
    Text(if (on) "Following" else "Follow", color = if (on) DS.White else DS.Navy, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Color.Transparent else DS.White).border(1.dp, if (on) DS.Line else DS.White, RoundedCornerShape(16.dp))
            .semantics { contentDescription = if (on) "Unfollow" else "Follow" }.clickable(role = Role.Button) { GameAudio.click(); Yorker.toggleFollow(s, key); vm.persist() }
            .padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
fun YorkerProfileScreen(vm: GameVM, key: String) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val tk = vm.tick
    val a = remember(key, vm.tick) { Yorker.account(s, key) }
    ScreenScaffold(vm, Yorker.NAME) {
        if (a == null) { Empty("This account does not exist."); return@ScreenScaffold }
        val posts = Yorker.postsBy(s, key)
        LazyColumn(Modifier.fillMaxSize()) {
            item { Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(86.dp).background(Color(a.color).copy(alpha = 0.55f)))
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.offset(y = (-28).dp)) {
                        Box(Modifier.clip(CircleShape).border(3.dp, DS.Navy, CircleShape)) { YAvatar(s, key, 72.dp) }
                        Spacer(Modifier.weight(1f))
                        if (key != "me") FollowBtn(vm, s, key)
                        else if (a.pid.isEmpty() && key == "me") Text("Your account", color = DS.Sub, fontSize = 11.sp)
                    }
                    Column(Modifier.offset(y = (-20).dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.name, color = DS.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (a.verified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.CheckCircle, "verified", tint = YAccent, modifier = Modifier.size(16.dp)) }
                        }
                        Text(a.handle, color = DS.Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(a.bio, color = DS.Sub, fontSize = 12.sp, lineHeight = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Text(buildAnnotatedString { withStyle(SpanStyle(color = DS.White, fontWeight = FontWeight.Bold)) { append(a.following.toString()) }; append(" Following") }, color = DS.Muted, fontSize = 12.sp)
                            Spacer(Modifier.width(14.dp))
                            Text(buildAnnotatedString { withStyle(SpanStyle(color = DS.White, fontWeight = FontWeight.Bold)) { append(Stumps.compact(a.followers)) }; append(if (a.estimated) " Followers (est.)" else " Followers") }, color = DS.Muted, fontSize = 12.sp)
                        }
                        if (a.pid.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            DsButton("Player profile", kind = DsKind.SECONDARY, height = DS.BtnHSmall) { vm.nav(Screen.Profile(a.pid)) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) { DsLabel("Posts · ${posts.size}", color = DS.Sub) }
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(DS.Line))
            } }
            if (posts.isEmpty()) item { Text(if (key == "me") "You have not posted yet. Use the box on the Home tab." else "No posts yet this career.", color = DS.Sub, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
            items(posts, key = { it.id }) { p -> YPost(vm, s, p) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun YorkerThreadScreen(vm: GameVM, postId: Int) {
    val s = vm.save ?: return
    @Suppress("UNUSED_VARIABLE") val tk = vm.tick
    val p = s.yorker.posts.firstOrNull { it.id == postId }
    ScreenScaffold(vm, "Post") {
        if (p == null) { Empty("This post is no longer available."); return@ScreenScaffold }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 760.dp
            val thread: @Composable (Modifier) -> Unit = { m ->
                CommentThreadView(key = p.id, total = p.replies, stored = p.thread, generatedTotal = Yorker.generatedReplies(s, p), page = { from, n -> Yorker.replyPage(s, p, from, n) },
                    meName = "Coach ${s.manager}", version = vm.tick, modifier = m, title = "Replies", accent = YAccent) { text, replyTo -> Yorker.reply(s, p, text, replyTo); vm.persist() }
            }
            if (wide) Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) { YPost(vm, s, p, inThread = true) }
                Box(Modifier.width(1.dp).fillMaxHeight().background(DS.Line))
                thread(Modifier.width(380.dp).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                YPost(vm, s, p, inThread = true)
                thread(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}
