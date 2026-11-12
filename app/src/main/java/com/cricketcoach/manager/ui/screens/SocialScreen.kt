package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.R
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.auth.OnlineComment
import com.cricketcoach.manager.auth.OnlinePost
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*
import kotlin.math.abs
import kotlin.math.max

private fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> "%.1fK".format(n / 1000.0)
    else -> n.toString()
}

private fun kindColor(kind: String): Color = when (kind) {
    "CENTURY", "FIFTY", "MILESTONE", "FORM" -> Color(0xFFE9B949)
    "FIVEFOR", "WICKETS", "HATTRICK" -> Color(0xFF4FC3F7)
    "WIN", "TITLE", "TROPHY" -> Color(0xFF43C66B)
    "LOSS" -> Color(0xFFEF5350)
    "SIGNING", "AUCTION", "TRANSFER" -> Color(0xFFBA68C8)
    "RECORD" -> Color(0xFFFF7043)
    else -> Color(0xFF90A4C8)
}

private val avatarCols = listOf(Color(0xFFFACC15), Color(0xFFD946EF), Color(0xFFF43F5E), Color(0xFF0EA5E9), Color(0xFF22C55E), Color(0xFFF97316), Color(0xFF8B5CF6), Color(0xFF14B8A6))
private fun handleColor(h: String) = avatarCols[abs(h.hashCode()) % avatarCols.size]
private fun minutesAgo(seed: String) = "${abs(seed.hashCode()) % 58 + 1}m"

private val TABS = listOf("For You", "Following", "My Team", "Players", "Online")

/** Brand mark: a set of stumps + bails in a gradient tile. */
@Composable
fun StumpsLogo(size: androidx.compose.ui.unit.Dp = 26.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Brush.linearGradient(listOf(Color(0xFFFFE7A6), Color(0xFFE9B949), Color(0xFFB8862B)))), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(size * 0.22f)) {
            val w = this.size.width; val h = this.size.height; val c = Color(0xFF0A1530)
            for (k in 0..2) drawRoundRect(c, Offset(w * (0.12f + k * 0.32f), h * 0.2f), Size(w * 0.12f, h * 0.8f), CornerRadius(3f))
            drawRoundRect(c, Offset(w * 0.08f, h * 0.06f), Size(w * 0.38f, h * 0.08f), CornerRadius(3f))
            drawRoundRect(c, Offset(w * 0.54f, h * 0.06f), Size(w * 0.38f, h * 0.08f), CornerRadius(3f))
        }
    }
}

/** Stumpstagram: player accounts + auto posts from real match events (offline) and the online manager feed (Supabase). */
@Composable
fun SocialScreen(vm: GameVM, initialTab: Int = -1) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val s = vm.save
    var tab by remember { mutableIntStateOf(if (initialTab >= 0) initialTab else if (s != null) 0 else 4) }
    var composer by remember { mutableStateOf("") }
    var selLocal by remember { mutableStateOf<Int?>(null) }
    var selOnline by remember { mutableStateOf<OnlinePost?>(null) }
    var onlyFollowed by remember { mutableStateOf(false) }
    LaunchedEffect(tab) { if (tab == 4 && vm.signedIn) vm.loadOnlineFeed() }
    LaunchedEffect(s) { if (s != null && !s.stumps.seeded) { Stumps.ensureSeeded(s); vm.persist(); vm.tick++ } }

    val inShell = LocalInShell.current
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            // ---- header: brand + tabs + followers
            Row(Modifier.fillMaxWidth().height(46.dp).background(UI.Panel).drawBehind { drawLine(UI.Line, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f) }
                .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                StumpsLogo()
                Spacer(Modifier.width(8.dp))
                Text("Stumpstagram", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, fontStyle = FontStyle.Italic)
                Spacer(Modifier.width(10.dp))
                TABS.forEachIndexed { i, name ->
                    val on = i == tab
                    Column(Modifier.fillMaxHeight().clickable { GameAudio.click(); tab = i }.padding(horizontal = 9.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.weight(1f))
                        Text(name, color = if (on) Color.White else C.Sub, fontWeight = if (on) FontWeight.Black else FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.width(48.dp).height(3.dp).background(if (on) C.Gold else Color.Transparent))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (s != null) Column(horizontalAlignment = Alignment.End) {
                    Text(compact(s.followers), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text("COACH FOLLOWERS", color = C.Sub, fontSize = 8.sp, letterSpacing = 1.sp)
                }
            }
            if (tab == 3) {
                if (s == null) EmptyNote("Load a career to browse player accounts.") else PlayerDirectory(vm, s)
            } else Row(Modifier.weight(1f).fillMaxWidth()) {
                // ---- feed
                Column(Modifier.weight(0.56f).fillMaxHeight().padding(start = 10.dp, end = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        OutlinedTextField(composer, { composer = it.take(200) }, placeholder = { Text(if (tab < 4) "Post to your followers…" else "Share with all managers online…", fontSize = 12.sp) },
                            singleLine = true, modifier = Modifier.weight(1f).heightIn(min = 54.dp), textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
                        Spacer(Modifier.width(6.dp))
                        Button(onClick = {
                            GameAudio.click()
                            if (tab < 4 && s != null) { Social.userPost(s, composer.trim()); vm.persist() }
                            else if (tab == 4) vm.shareOnline(SocialPost(kind = "NEWS", text = composer.trim(), mine = true))
                            composer = ""
                        }, enabled = composer.isNotBlank() && (tab == 4 && vm.signedIn || tab < 4 && s != null), colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = Color(0xFF1A1205)), shape = RoundedCornerShape(10.dp)) {
                            Text("POST", fontWeight = FontWeight.Black)
                        }
                    }
                    if (tab < 4) {
                        val list = remember(vm.tick, tab, s?.posts?.size, s?.stumps?.following?.size) {
                            if (s == null) emptyList() else when (tab) {
                                0 -> Stumps.forYou(s)
                                1 -> Stumps.followingFeed(s)
                                else -> s.posts.filter { it.mine || it.club == s.userClubId || s.userClub().short in it.tags || it.pid in s.userClub().squad || it.authorPid in s.userClub().squad }
                            }
                        }
                        LaunchedEffect(tab, list.firstOrNull()?.id) { if (selLocal == null || list.none { it.id == selLocal }) selLocal = list.firstOrNull()?.id }
                        if (s == null) EmptyNote("Load a career to see your club's feed.")
                        else if (list.isEmpty()) EmptyNote(when (tab) { 0 -> "No posts yet - play your first match!"; 1 -> "Follow players in the Players tab (or on their profile) to fill this feed."; else -> "Nothing about your team yet." })
                        else LazyColumn(Modifier.fillMaxSize()) {
                            items(list, key = { it.id }) { p -> LocalPostCard(vm, s, p, p.id == selLocal) { selLocal = p.id } }
                            item { Spacer(Modifier.height(12.dp)) }
                        }
                    } else {
                        if (!vm.signedIn) SignInNote(vm)
                        else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterPill("All managers", !onlyFollowed) { onlyFollowed = false }
                                Spacer(Modifier.width(6.dp))
                                FilterPill("Managers I follow", onlyFollowed) { onlyFollowed = true }
                            }
                            val list = if (onlyFollowed) vm.onlinePosts.filter { it.userId in vm.following } else vm.onlinePosts
                            if (vm.onlineStatus.isNotEmpty()) Sub(vm.onlineStatus)
                            if (list.isEmpty()) EmptyNote(if (onlyFollowed) "Follow other managers to fill this feed." else "No online posts yet. Be the first!")
                            else LazyColumn(Modifier.fillMaxSize()) {
                                items(list, key = { it.id }) { p -> OnlinePostCard(vm, p, p.id == selOnline?.id) { selOnline = p } }
                            }
                        }
                    }
                }
                // ---- comments panel
                Column(Modifier.weight(0.44f).fillMaxHeight().background(Color(0xCC0B111C)).drawBehind { drawLine(UI.Line, Offset(0f, 0f), Offset(0f, size.height), 2f) }) {
                    if (tab < 4) {
                        val p = s?.posts?.firstOrNull { it.id == selLocal }
                        CommentsHeader(p?.handle)
                        if (p == null || s == null) EmptyNote("Select a post to read the comments.")
                        else LocalComments(vm, s, p)
                    } else {
                        val p = selOnline
                        CommentsHeader(p?.author)
                        if (p == null) EmptyNote(if (vm.signedIn) "Select a post to read the comments." else "Sign in to join the conversation.")
                        else OnlineComments(vm, p)
                    }
                }
            }
        }
    }
    if (inShell) body() else ScreenScaffold(vm, "Stumpstagram") { body() }
}

@Composable
private fun FilterPill(text: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (on) C.Gold else Color(0xFF13234A)).border(1.dp, if (on) C.Gold else UI.Line, RoundedCornerShape(15.dp))
        .clickable { GameAudio.click(); onClick() }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (on) Color(0xFF1A1205) else C.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/* ============================================================================ Players directory */

@Composable
private fun PlayerDirectory(vm: GameVM, s: CareerSave) {
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }     // 0 all, 1 my team, 2 following, 3 verified
    val all = remember(s.stumps.fDelta.size, vm.tick / 50) { Stumps.directory(s) }
    val list = remember(q, filter, all, s.stumps.following.size) {
        val qq = q.trim().lowercase()
        all.filter { a ->
            (qq.isEmpty() || a.name.lowercase().contains(qq) || a.username.contains(qq)) && when (filter) {
                1 -> a.pid in s.userClub().squad; 2 -> a.pid in s.stumps.following; 3 -> a.verified; else -> true
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it.take(30) }, placeholder = { Text("Search players or @usernames", fontSize = 12.sp) }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = C.Sub) }, modifier = Modifier.width(300.dp).heightIn(min = 52.dp), textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
            Spacer(Modifier.width(10.dp))
            listOf("All", "My team", "Following", "Verified").forEachIndexed { i, l -> FilterPill(l, filter == i) { filter = i }; Spacer(Modifier.width(6.dp)) }
            Spacer(Modifier.weight(1f))
            Text("${list.size} accounts", color = C.Sub, fontSize = 11.sp)
        }
        Text(Stumps.ESTIMATE_NOTE, color = C.Sub, fontSize = 9.5.sp)
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list.chunked(2), key = { it.first().pid }) { row ->
                Row(Modifier.fillMaxWidth()) {
                    for (a in row) AccountRow(vm, s, a, Modifier.weight(1f))
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun AccountRow(vm: GameVM, s: CareerSave, a: PlayerAccount, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.padding(3.dp).clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF101C3C), Color(0xFF0B1430)))).border(1.dp, UI.Line, shape)
        .clickable { GameAudio.click(); vm.nav(Screen.SocialProfile(a.pid)) }.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayerFace(a.pid, a.name, 40.dp, if (a.verified) C.Gold else UI.Line)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (a.verified) { Spacer(Modifier.width(3.dp)); VerifiedTick(13.dp) }
            }
            Text(a.handle, color = C.Sub, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Stumps.compact(a.followers), color = C.Gold, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text("FOLLOWERS · EST.", color = C.Sub, fontSize = 7.5.sp, letterSpacing = 0.5.sp)
        }
        Spacer(Modifier.width(8.dp))
        FollowButton(vm, s, a.pid, compactSize = true)
    }
}

@Composable
fun VerifiedTick(size: androidx.compose.ui.unit.Dp = 13.dp) = Icon(Icons.Filled.CheckCircle, "verified", tint = Color(0xFF3B82F6), modifier = Modifier.size(size))

@Composable
private fun FollowButton(vm: GameVM, s: CareerSave, pid: String, compactSize: Boolean = false) {
    val f = pid in s.stumps.following
    Box(Modifier.height(if (compactSize) 32.dp else 38.dp).widthIn(min = if (compactSize) 84.dp else 120.dp).clip(RoundedCornerShape(9.dp))
        .background(if (f) Brush.horizontalGradient(listOf(Color(0xFF1B2A4E), Color(0xFF1B2A4E))) else Brush.horizontalGradient(listOf(Color(0xFFFFE7A6), Color(0xFFE9B949))))
        .border(1.dp, if (f) UI.Line else Color(0xFFFFE7A6), RoundedCornerShape(9.dp))
        .clickable { GameAudio.click(); val now = Stumps.toggleFollow(s, pid); vm.persist(); vm.tick++; vm.msg(if (now) "Following ${s.players[pid]?.name ?: ""}" else null) }
        .padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(if (f) "Following ✓" else "+ Follow", color = if (f) C.Text else Color(0xFF1A1205), fontWeight = FontWeight.Black, fontSize = if (compactSize) 11.sp else 13.sp)
    }
}

/* ============================================================================ Player profile */

@Composable
fun SocialProfileScreen(vm: GameVM, pid: String) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val s = vm.save
    if (s == null) { ScreenScaffold(vm, "Stumpstagram") { EmptyNote("Load a career first.") }; return }
    val a = remember(pid, vm.tick) { Stumps.account(s, pid) }
    if (a == null) { ScreenScaffold(vm, "Stumpstagram") { EmptyNote("No account for this player.") }; return }
    val posts = remember(pid, s.posts.size, vm.tick) { Stumps.postsBy(s, pid) }
    var sel by remember(pid) { mutableStateOf<Int?>(null) }
    val gp = s.players[pid]
    val club = gp?.clubId?.let { s.clubs[it] }
    val prim = club?.let { Color(it.primary) } ?: Color(0xFF1565C0)
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(0.56f).fillMaxHeight().padding(start = 10.dp, end = 6.dp)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "hdr") {
                        // header card
                        val shape = RoundedCornerShape(16.dp)
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(shape)
                            .background(Brush.linearGradient(listOf(prim.copy(alpha = 0.55f), Color(0xFF0B1430), Color(0xFF070D20)))).border(1.dp, Color(0x66E9B949), shape).padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlayerFace(pid, a.name, 64.dp, if (a.verified) C.Gold else UI.Line)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(a.username, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        if (a.verified) { Spacer(Modifier.width(4.dp)); VerifiedTick(16.dp) }
                                    }
                                    Text(a.name + if (!a.real) "  ·  FICTIONAL" else "", color = C.Sub, fontSize = 11.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Row {
                                        ProfileCount(a.posts.toLong(), "posts")
                                        ProfileCount(a.followers, "followers", est = a.estimated)
                                        ProfileCount(a.following.toLong(), "following")
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(a.bio, color = C.Text, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FollowButton(vm, s, pid)
                                Spacer(Modifier.width(8.dp))
                                OutlineBtn("Player profile") { vm.nav(Screen.Profile(pid)) }
                                Spacer(Modifier.weight(1f))
                                if (club != null) TeamFlag(club, 34.dp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Followers: in-game estimate, not verified data · In-game account, not affiliated with the real player.", color = C.Sub, fontSize = 9.sp, lineHeight = 11.sp)
                        }
                        Text("POSTS", color = C.Gold, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    }
                    if (posts.isEmpty()) item(key = "none") { Box(Modifier.height(140.dp)) { EmptyNote("No posts yet in this career. Big performances, signings and trophies make players post.") } }
                    items(posts, key = { it.id }) { p -> LocalPostCard(vm, s, p, p.id == sel) { sel = p.id } }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
            Column(Modifier.weight(0.44f).fillMaxHeight().background(Color(0xCC0B111C)).drawBehind { drawLine(UI.Line, Offset(0f, 0f), Offset(0f, size.height), 2f) }) {
                val p = posts.firstOrNull { it.id == (sel ?: posts.firstOrNull()?.id) }
                CommentsHeader(p?.handle)
                if (p == null) EmptyNote("Select a post to read the comments.") else LocalComments(vm, s, p)
            }
        }
    }
    ScreenScaffold(vm, "${a.handle} · Stumpstagram") { body() }
}

@Composable
private fun ProfileCount(n: Long, label: String, est: Boolean = false) {
    Column(Modifier.padding(end = 18.dp)) {
        Text(Stumps.compact(n), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(if (est) "$label · est." else label, color = C.Sub, fontSize = 10.sp)
    }
}

@Composable
private fun EmptyNote(t: String) = Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { Text(t, color = C.Sub, fontSize = 12.sp, textAlign = TextAlign.Center) }

@Composable
private fun SignInNote(vm: GameVM) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("The online feed needs an account", color = C.Gold, fontWeight = FontWeight.Bold)
        Sub("See and like posts from other managers, comment and follow them.")
        Spacer(Modifier.height(8.dp))
        Row { GoldButton("Log in") { vm.nav(Screen.Login) }; Spacer(Modifier.width(8.dp)); OutlineBtn("Create account") { vm.nav(Screen.SignUp) } }
    }
}

@Composable
private fun CommentsHeader(handle: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Email, null, tint = UI.Pink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("COMMENTS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        if (handle != null) Text("on $handle's post", color = C.Sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
    }
}

@Composable
private fun CommentBubble(author: String, handle: String, text: String, time: String) {
    val col = handleColor(handle)
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(col).border(2.dp, Color(0x55FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF161E2E)).border(1.dp, UI.Line, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row {
                Text(handle.ifBlank { author }, color = col, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(time, color = C.Sub, fontSize = 9.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(text, color = Color(0xFFE2E8F0), fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun LocalComments(vm: GameVM, s: CareerSave, p: SocialPost) {
    var text by remember(p.id) { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            items(p.comments.size) { i -> val c = p.comments[i]; CommentBubble(c.author, c.handle, c.text, if (c.handle.startsWith("@coach_")) "now" else minutesAgo(c.handle + c.text)) }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it.take(160) }, placeholder = { Text("Reply as Coach ${s.manager}…", fontSize = 11.sp) }, singleLine = true,
                modifier = Modifier.weight(1f).heightIn(min = 54.dp), textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
            IconButton(onClick = {
                val b = text.trim(); if (b.isNotEmpty()) {
                    p.comments.add(SocialComment("${s.manager} (Coach)", "@coach_${s.manager.filter { it.isLetterOrDigit() }.lowercase().take(12)}", b, 0))
                    text = ""; vm.persist()
                }
            }) { Icon(Icons.Filled.Send, "Send", tint = UI.Pink) }
        }
    }
}

@Composable
private fun OnlineComments(vm: GameVM, p: OnlinePost) {
    var list by remember(p.id) { mutableStateOf<List<OnlineComment>?>(null) }
    var text by remember(p.id) { mutableStateOf("") }
    LaunchedEffect(p.id) { vm.loadComments(p.id) { list = it } }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when {
                list == null -> CircularProgressIndicator(color = UI.Pink, modifier = Modifier.align(Alignment.Center).size(22.dp))
                list!!.isEmpty() -> EmptyNote("No comments yet")
                else -> LazyColumn { items(list!!, key = { it.id }) { c -> CommentBubble(c.author, "@" + c.author.filter { it.isLetterOrDigit() || it == '_' }.lowercase(), c.body, c.createdAt.take(10)) } }
            }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it.take(200) }, placeholder = { Text("Write a comment…", fontSize = 11.sp) }, singleLine = true,
                modifier = Modifier.weight(1f).heightIn(min = 54.dp), textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
            IconButton(onClick = { val b = text.trim(); if (b.isNotEmpty()) vm.addComment(p.id, b) { text = ""; vm.loadComments(p.id) { list = it } } }) { Icon(Icons.Filled.Send, "Send", tint = UI.Pink) }
        }
    }
}

@Composable
private fun AuthorLine(name: String, handle: String, verified: Boolean, kind: String, pid: String = "", onOpen: (() -> Unit)? = null) {
    Row((if (onOpen != null) Modifier.clickable { GameAudio.click(); onOpen() } else Modifier), verticalAlignment = Alignment.CenterVertically) {
        if (pid.isNotEmpty()) PlayerFace(pid, name, 30.dp, if (verified) C.Gold else UI.Line)
        else Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.linearGradient(listOf(UI.Pink, UI.Violet))), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(handle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (verified) { Spacer(Modifier.width(3.dp)); Icon(Icons.Filled.CheckCircle, "verified", tint = Color(0xFF3B82F6), modifier = Modifier.size(13.dp)) }
            }
            Text(kindLabel(kind), color = kindColor(kind), fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, maxLines = 1, softWrap = false)
        }
    }
}

private fun kindLabel(kind: String) = when (kind) {
    "FIVEFOR" -> "FIVE-FOR"; "HATTRICK" -> "HAT-TRICK"; "NEWS" -> "POST"; else -> kind
}

@Composable
private fun Counter(icon: androidx.compose.ui.graphics.vector.ImageVector, n: String, tint: Color = Color(0xFFCBD5E1), onClick: (() -> Unit)? = null) {
    Row((if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(horizontal = 5.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp)); Text(" $n", color = Color(0xFFCBD5E1), fontSize = 11.sp)
    }
}

@Composable
private fun LocalPostCard(vm: GameVM, s: CareerSave, p: SocialPost, selected: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(Color(0xFF0E1522))
        .border(if (selected) 2.dp else 1.dp, if (selected) UI.Pink.copy(alpha = 0.85f) else UI.Line, shape).clickable { onSelect() }.padding(10.dp)) {
        if (p.headline.isNotEmpty()) { PostPoster(s, p); Spacer(Modifier.height(8.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                AuthorLine(p.author, p.handle, p.verified, p.kind, p.authorPid, if (p.authorPid.isNotEmpty()) ({ vm.nav(Screen.SocialProfile(p.authorPid)) }) else null)
            }
            Counter(if (p.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, compact(p.likes), if (p.liked) C.Red else Color(0xFFCBD5E1)) { Social.toggleLike(p); vm.persist() }
            Counter(Icons.Filled.Email, compact(max(p.comments.size, p.commentCount))) { onSelect() }
            Counter(Icons.Filled.Refresh, compact(p.reposts))
            if (vm.signedIn && !p.shared && (p.mine || p.kind != "NEWS")) Counter(Icons.Filled.Share, "", UI.Pink) { vm.shareOnline(p) }
        }
        Spacer(Modifier.height(4.dp))
        Row {
            Text(p.text, color = Color(0xFFCBD5E1), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("S${p.season} D${p.day}", color = C.Sub, fontSize = 9.sp)
        }
        if (p.shared) Text("Shared online", color = C.Sub, fontSize = 9.sp)
    }
}

/** Slogans for poster cards, picked deterministically per post. */
private fun slogan(p: SocialPost): Pair<String, String> {
    val opts = when (p.kind) {
        "WIN", "TITLE" -> listOf("GOOD RUN." to "THE CHARGE IS ON.", "UNSTOPPABLE." to "ONE TEAM. ONE DREAM.", "BIG WIN." to "THE MARCH CONTINUES.")
        "LOSS" -> listOf("REGROUP." to "WE GO AGAIN.", "TOUGH DAY." to "BOUNCE BACK.")
        "CENTURY", "RECORD" -> listOf("CLASS ACT." to "PURE TIMING.", "MILESTONE." to "FRAME THIS ONE.")
        "FIVEFOR" -> listOf("ON FIRE." to "UNPLAYABLE.", "STRIKE FORCE." to "FIVE IN THE BAG.")
        "FORM" -> listOf("GOOD RUN." to "THE CHARGE IS ON.", "IN THE ZONE." to "LEADING FROM THE FRONT.")
        "AUCTION", "TRANSFER" -> listOf("NEW ERA." to "WELCOME TO THE FAMILY.", "DONE DEAL." to "LET'S GO.")
        else -> listOf("MATCH DAY." to "LET'S GO.")
    }
    return opts[abs(p.id) % opts.size]
}

/** Broadcast-style poster graphic for a post: team colours, featured player, big headline and a stat table. */
@Composable
private fun PostPoster(s: CareerSave, p: SocialPost) {
    val club = s.clubs[p.club]
    val player = s.players[p.pid]
    val prim = Color(club?.primary ?: 0xFF1E3A8A); val sec = Color(club?.secondary ?: 0xFFE9B949)
    val accent = kindColor(p.kind)
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().height(268.dp).clip(shape).border(1.dp, Color(0x33FFFFFF), shape)) {
        // background: team colour wash over the stadium + diagonal light streaks
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(prim.copy(alpha = 0.95f), Color(0xFF0A0F1A), Color(0xFF05070C)))))
        Image(painterResource(R.drawable.stadium_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.28f)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            for (i in 0..2) {
                val x = w * (0.3f + i * 0.14f)
                val path = Path().apply { moveTo(x, 0f); lineTo(x + w * 0.06f, 0f); lineTo(x - w * 0.2f, h); lineTo(x - w * 0.26f, h); close() }
                drawPath(path, sec.copy(alpha = 0.07f + i * 0.03f))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC05070C)), startY = h * 0.55f, endY = h), Offset.Zero, size)
            // crowd-glow strip at the bottom (pink/team accent like a stadium LED board)
            drawRect(Brush.horizontalGradient(listOf(sec.copy(alpha = 0.0f), accent.copy(alpha = 0.55f), sec.copy(alpha = 0.0f))), Offset(0f, h - 8f), Size(w, 8f))
        }
        // featured player (photo, or a kit-coloured figure)
        Box(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 46.dp).width(130.dp).fillMaxHeight(0.78f)) {
            PosterFigure(p.pid, player?.name ?: club?.name ?: "", prim, sec)
        }
        // flag / badge
        Box(Modifier.align(Alignment.TopStart).padding(12.dp)) { TeamFlag(club, 46.dp) }
        // headline + stat table on the right
        Column(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 14.dp).widthIn(max = 200.dp), horizontalAlignment = Alignment.End) {
            val words = p.headline.split(' ')
            val shadow = TextStyle(shadow = Shadow(Color.Black, Offset(2f, 3f), 6f))
            if (words.size >= 2) {
                Text(words.first(), color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 18.sp, style = shadow, lineHeight = 19.sp)
                Text(words.drop(1).joinToString(" "), color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 28.sp, style = shadow, lineHeight = 29.sp)
            } else Text(p.headline, color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 34.sp, style = shadow, lineHeight = 34.sp)
            if (p.stat.isNotBlank()) Text(p.stat, color = accent, fontWeight = FontWeight.Black, fontSize = if (p.stat.length > 12) 13.sp else 22.sp, style = shadow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (player != null) {
                Spacer(Modifier.height(4.dp))
                Text(player.name.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF0F2744)).border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
            }
            if (p.lines.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Column(Modifier.width(184.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xB30A0F1A)).border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    p.lines.forEachIndexed { i, line ->
                        if (i > 0) HorizontalDivider(color = Color(0x22FFFFFF))
                        val l = line.substringBefore('|'); val r = line.substringAfter('|', "")
                        val col = when { r.startsWith("WON") -> UI.Neon; r.startsWith("LOST") -> C.Red; r.startsWith("DRAW") || r.startsWith("NO") -> UI.Amber; else -> Color.White }
                        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            val oppCode = if (l.startsWith("VS ")) l.removePrefix("VS ") else null
                            val oppClub = oppCode?.let { c -> s.clubs.values.firstOrNull { it.short == c } }
                            if (oppClub != null) { TeamFlag(oppClub, 18.dp); Spacer(Modifier.width(6.dp)) }
                            Text(l, color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(r.substringBefore(' '), color = col, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            if (r.contains(' ')) Text(" " + r.substringAfter(' '), color = Color(0xFF94A3B8), fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
        // slogan bottom-left
        val (a, b) = slogan(p)
        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 14.dp)) {
            Text(a, color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 18.sp, style = TextStyle(shadow = Shadow(Color.Black, Offset(2f, 2f), 6f)))
            Text(b, color = Color(0xFF93C5FD), fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 10.sp)
        }
    }
}

/** Real photo (credited Wikimedia portraits) if the player has one, otherwise a kit-coloured cricketer figure. */
@Composable
private fun PosterFigure(pid: String, name: String, prim: Color, sec: Color) {
    val hasImg = remember(pid) { pid.isNotEmpty() && Faces.fileFor(pid) != null }
    var img by remember(pid) { mutableStateOf<ImageBitmap?>(if (pid.isNotEmpty()) Faces.cached(pid) else null) }
    if (hasImg && img == null) LaunchedEffect(pid) { img = Faces.load(pid) }
    val b = img
    if (b != null) {
        Box(Modifier.fillMaxSize()) {
            Image(b, name, Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.86f).clip(RoundedCornerShape(topStart = 80.dp, topEnd = 80.dp)), contentScale = ContentScale.Crop)
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.35f).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE05070C)))))
        }
        return
    }
    val skin = listOf(Color(0xFF8D5524), Color(0xFFC68642), Color(0xFFE0AC69), Color(0xFF6B4423), Color(0xFFA0522D))[abs(pid.hashCode()) % 5]
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val cx = w * 0.5f
        // bat resting on the shoulder
        val bat = Path().apply { moveTo(cx + w * 0.18f, h * 0.48f); lineTo(cx + w * 0.42f, h * 0.06f); lineTo(cx + w * 0.5f, h * 0.1f); lineTo(cx + w * 0.26f, h * 0.52f); close() }
        drawPath(bat, Color(0xFFE8D3A2)); drawPath(bat, Color(0x55000000), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        // torso (jersey)
        val torso = Path().apply {
            moveTo(cx - w * 0.38f, h); lineTo(cx - w * 0.36f, h * 0.55f); quadraticBezierTo(cx - w * 0.34f, h * 0.42f, cx - w * 0.16f, h * 0.38f)
            lineTo(cx + w * 0.16f, h * 0.38f); quadraticBezierTo(cx + w * 0.34f, h * 0.42f, cx + w * 0.36f, h * 0.55f); lineTo(cx + w * 0.38f, h); close()
        }
        drawPath(torso, Brush.verticalGradient(listOf(prim, prim.copy(red = prim.red * 0.6f, green = prim.green * 0.6f, blue = prim.blue * 0.6f))))
        // collar + trim
        val collar = Path().apply { moveTo(cx - w * 0.1f, h * 0.38f); lineTo(cx, h * 0.47f); lineTo(cx + w * 0.1f, h * 0.38f) }
        drawPath(collar, sec, style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.03f))
        drawLine(sec, Offset(cx - w * 0.36f, h * 0.6f), Offset(cx - w * 0.3f, h * 0.6f), w * 0.04f)
        drawLine(sec, Offset(cx + w * 0.3f, h * 0.6f), Offset(cx + w * 0.36f, h * 0.6f), w * 0.04f)
        // glove on the bat handle
        drawCircle(Color.White, w * 0.06f, Offset(cx + w * 0.24f, h * 0.47f))
        // neck + head
        drawRect(skin, Offset(cx - w * 0.06f, h * 0.3f), Size(w * 0.12f, h * 0.1f))
        drawOval(skin, Offset(cx - w * 0.13f, h * 0.12f), Size(w * 0.26f, h * 0.22f))
        // helmet + grille
        drawArc(Color(0xFF0F172A), 180f, 180f, true, Offset(cx - w * 0.16f, h * 0.08f), Size(w * 0.32f, h * 0.2f))
        drawRoundRect(Color(0xFF0F172A), Offset(cx - w * 0.17f, h * 0.17f), Size(w * 0.34f, h * 0.025f), CornerRadius(4f))
        for (i in 0..3) drawLine(Color(0xFF94A3B8), Offset(cx - w * 0.12f, h * (0.2f + i * 0.03f)), Offset(cx + w * 0.12f, h * (0.2f + i * 0.03f)), 2.5f)
        drawLine(Color(0xFF94A3B8), Offset(cx, h * 0.19f), Offset(cx, h * 0.3f), 2.5f)
        drawCircle(sec, w * 0.035f, Offset(cx, h * 0.12f))
    }
}

@Composable
private fun OnlinePostCard(vm: GameVM, p: OnlinePost, selected: Boolean, onSelect: () -> Unit) {
    val mine = p.userId == com.cricketcoach.manager.auth.Auth.uid
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(Color(0xFF0E1522))
        .border(if (selected) 2.dp else 1.dp, if (selected) UI.Pink.copy(alpha = 0.85f) else UI.Line, shape).clickable { onSelect() }.padding(10.dp)) {
        AuthorLine(p.author, if (p.club.isNotBlank()) "${p.author} · ${p.club}" else p.author, false, p.kind)
        Spacer(Modifier.height(6.dp))
        Text(p.body, color = Color(0xFFE2E8F0), fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val liked = p.id in vm.onlineLiked
            Counter(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "${p.likes}", if (liked) C.Red else Color(0xFFCBD5E1)) { vm.toggleOnlineLike(p) }
            Counter(Icons.Filled.Email, "${p.comments}") { onSelect() }
            Spacer(Modifier.weight(1f))
            Text(p.createdAt.take(10), color = C.Sub, fontSize = 9.sp)
            if (mine) TextButton(onClick = { vm.deleteOnlinePost(p.id) }) { Text("Delete", color = C.Red, fontSize = 11.sp) }
            else {
                val f = p.userId in vm.following
                TextButton(onClick = { vm.toggleFollow(p.userId) }) { Text(if (f) "Following" else "+ Follow", color = if (f) C.Sub else UI.Pink, fontSize = 11.sp) }
            }
        }
    }
}
