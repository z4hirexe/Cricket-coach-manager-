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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.auth.OnlineComment
import com.cricketcoach.manager.auth.OnlinePost
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.ui.*

private fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> "%.1fK".format(n / 1000.0)
    else -> n.toString()
}

private fun kindColor(kind: String): Color = when (kind) {
    "CENTURY", "FIFTY", "MILESTONE" -> Color(0xFFE9B949)
    "FIVEFOR", "WICKETS" -> Color(0xFF4FC3F7)
    "WIN", "TITLE", "TROPHY" -> Color(0xFF43C66B)
    "LOSS" -> Color(0xFFEF5350)
    "SIGNING", "AUCTION", "TRANSFER" -> Color(0xFFBA68C8)
    "RECORD" -> Color(0xFFFF7043)
    else -> Color(0xFF90A4C8)
}

/** Social Media hub: offline career feed (auto posts, likes, comments, followers, trending) + online Supabase feed. */
@Composable
fun SocialScreen(vm: GameVM) {
    @Suppress("UNUSED_VARIABLE") val t = vm.tick
    val s = vm.save
    var tab by remember { mutableIntStateOf(if (s != null) 0 else 1) }
    var composer by remember { mutableStateOf("") }
    var openComments by remember { mutableStateOf<OnlinePost?>(null) }
    LaunchedEffect(tab) { if (tab == 1 && vm.signedIn) vm.loadOnlineFeed() }
    ScreenScaffold(vm, "Social Media") {
        Row(Modifier.fillMaxSize().padding(8.dp)) {
            // ---- left: profile card + trending
            Column(Modifier.width(210.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF16306B), Color(0xFF0A1530)))).border(1.dp, C.Gold.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s != null) ClubBadge(s.userClub(), 40.dp) else Avatar(vm.avatar, vm.profile?.username ?: "G", 40.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(s?.manager ?: vm.profile?.displayName?.ifBlank { null } ?: vm.profile?.username ?: "Guest", color = C.GoldLight, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                        Text(if (vm.signedIn) "@${vm.profile?.username ?: ""}" else "offline profile", color = C.Sub, fontSize = 10.sp)
                    }
                }
                if (s != null) {
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Column(Modifier.weight(1f)) { Text(compact(s.followers), color = C.Text, fontWeight = FontWeight.Black, fontSize = 15.sp); Sub("followers", size = 10) }
                        Column(Modifier.weight(1f)) { Text("${s.posts.size}", color = C.Text, fontWeight = FontWeight.Black, fontSize = 15.sp); Sub("posts", size = 10) }
                        Column(Modifier.weight(1f)) { Text("${s.boardRating}%", color = C.Text, fontWeight = FontWeight.Black, fontSize = 15.sp); Sub("board", size = 10) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("TRENDING", color = C.Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    val trend = remember(vm.tick, s.posts.size) { Social.trending(s) }
                    if (trend.isEmpty()) Sub("Play matches to start trends", size = 10)
                    LazyColumn(Modifier.weight(1f)) {
                        items(trend) { (tag, n) ->
                            Column(Modifier.padding(vertical = 3.dp)) {
                                Text(tag, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Sub("${compact(n)} posts", size = 10)
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Sub("Load or start a career to get your club's feed: automatic posts for centuries, five-fors, wins, records, signings and titles.", size = 11)
                    Spacer(Modifier.weight(1f))
                }
            }
            // ---- right: feed
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp)) {
                Tabs(listOf("Career feed", "Online feed"), tab) { tab = it }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    OutlinedTextField(composer, { composer = it.take(200) }, placeholder = { Text(if (tab == 0) "Post to your followers…" else "Share with all players online…", fontSize = 12.sp) },
                        singleLine = true, modifier = Modifier.weight(1f), textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
                    Spacer(Modifier.width(6.dp))
                    GoldButton("POST", enabled = composer.isNotBlank() && (tab == 1 && vm.signedIn || tab == 0 && s != null)) {
                        if (tab == 0 && s != null) { Social.userPost(s, composer.trim()); vm.persist() }
                        else if (tab == 1) { val p = SocialPost(kind = "NEWS", text = composer.trim(), mine = true); vm.shareOnline(p) }
                        composer = ""
                    }
                }
                if (tab == 0) {
                    if (s == null) EmptyNote("No career loaded.")
                    else if (s.posts.isEmpty()) EmptyNote("No posts yet - play your first match!")
                    else LazyColumn(Modifier.fillMaxSize()) {
                        items(s.posts, key = { it.id }) { p -> LocalPostCard(vm, s, p) }
                    }
                } else {
                    if (!vm.signedIn) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("Online feed needs an account", color = C.Gold, fontWeight = FontWeight.Bold)
                            Sub("See and like posts from other managers, comment and follow them.")
                            Spacer(Modifier.height(8.dp))
                            Row { GoldButton("Log in") { vm.nav(Screen.Login) }; Spacer(Modifier.width(8.dp)); OutlineBtn("Create account") { vm.nav(Screen.SignUp) } }
                        }
                    } else {
                        if (vm.onlineStatus.isNotEmpty()) Sub(vm.onlineStatus)
                        Row { TextButton(onClick = { vm.loadOnlineFeed() }) { Icon(Icons.Filled.Refresh, null, tint = C.Gold); Text(" Refresh", color = C.Gold, fontSize = 12.sp) } }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(vm.onlinePosts, key = { it.id }) { p -> OnlinePostCard(vm, p) { openComments = p } }
                        }
                    }
                }
            }
        }
    }
    openComments?.let { p -> CommentsDialog(vm, p) { openComments = null } }
}

@Composable
private fun EmptyNote(t: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Sub(t) }

@Composable
private fun AuthorRow(name: String, handle: String, verified: Boolean, kind: String, meta: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(kindColor(kind).copy(alpha = 0.25f)).border(1.dp, kindColor(kind), CircleShape), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = kindColor(kind), fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                if (verified) { Spacer(Modifier.width(3.dp)); Icon(Icons.Filled.CheckCircle, "verified", tint = C.Gold, modifier = Modifier.size(12.dp)) }
            }
            Text("$handle • $meta", color = C.Sub, fontSize = 10.sp, maxLines = 1)
        }
        Text(kind, color = kindColor(kind), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.border(1.dp, kindColor(kind).copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
    }
}

@Composable
private fun LocalPostCard(vm: GameVM, s: CareerSave, p: SocialPost) {
    var showC by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(C.Card).border(1.dp, Color(0x22E9B949), RoundedCornerShape(12.dp)).padding(10.dp)) {
        AuthorRow(p.author, p.handle, p.verified, p.kind, "S${p.season} D${p.day}")
        Spacer(Modifier.height(5.dp))
        Text(p.text, color = C.Text, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clickable { Social.toggleLike(p); vm.persist() }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (p.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "like", tint = if (p.liked) C.Red else C.Sub, modifier = Modifier.size(16.dp))
                Text(" ${compact(p.likes)}", color = C.Sub, fontSize = 11.sp)
            }
            Spacer(Modifier.width(10.dp))
            Row(Modifier.clickable { showC = !showC }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Email, "comments", tint = C.Sub, modifier = Modifier.size(16.dp)); Text(" ${p.comments.size}", color = C.Sub, fontSize = 11.sp)
            }
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Filled.Refresh, "reposts", tint = C.Sub, modifier = Modifier.size(16.dp)); Text(" ${compact(p.reposts)}", color = C.Sub, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            if (vm.signedIn && !p.shared && (p.mine || p.kind != "NEWS")) TextButton(onClick = { vm.shareOnline(p) }) { Icon(Icons.Filled.Share, null, tint = C.Gold, modifier = Modifier.size(14.dp)); Text(" Share online", color = C.Gold, fontSize = 11.sp) }
            else if (p.shared) Sub("Shared online", size = 10)
        }
        if (showC) for (c in p.comments) Row(Modifier.padding(start = 38.dp, top = 3.dp)) {
            Text(c.author, color = C.GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("  ${c.text}", color = C.Text, fontSize = 11.sp)
        }
    }
}

@Composable
private fun OnlinePostCard(vm: GameVM, p: OnlinePost, onComments: () -> Unit) {
    val mine = p.userId == com.cricketcoach.manager.auth.Auth.uid
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(C.Card).border(1.dp, Color(0x22E9B949), RoundedCornerShape(12.dp)).padding(10.dp)) {
        AuthorRow(p.author, if (p.club.isNotBlank()) p.club else "manager", false, p.kind, p.createdAt.take(10))
        Spacer(Modifier.height(5.dp))
        Text(p.body, color = C.Text, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val liked = p.id in vm.onlineLiked
            Row(Modifier.clickable { vm.toggleOnlineLike(p) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "like", tint = if (liked) C.Red else C.Sub, modifier = Modifier.size(16.dp))
                Text(" ${p.likes}", color = C.Sub, fontSize = 11.sp)
            }
            Spacer(Modifier.width(10.dp))
            Row(Modifier.clickable(onClick = onComments).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Email, "comments", tint = C.Sub, modifier = Modifier.size(16.dp)); Text(" ${p.comments}", color = C.Sub, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            if (mine) TextButton(onClick = { vm.deleteOnlinePost(p.id) }) { Text("Delete", color = C.Red, fontSize = 11.sp) }
            else {
                val f = p.userId in vm.following
                TextButton(onClick = { vm.toggleFollow(p.userId) }) { Text(if (f) "Following" else "+ Follow", color = if (f) C.Sub else C.Gold, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun CommentsDialog(vm: GameVM, p: OnlinePost, close: () -> Unit) {
    var list by remember { mutableStateOf<List<OnlineComment>?>(null) }
    var text by remember { mutableStateOf("") }
    var followers by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(p.id) { vm.loadComments(p.id) { list = it }; vm.followerCount(p.userId) { followers = it } }
    AlertDialog(onDismissRequest = close, containerColor = C.Surface,
        title = { Column { Text(p.author, color = C.Gold); Sub("${followers ?: "…"} followers") } },
        text = {
            Column {
                Text(p.body, color = C.Text, fontSize = 13.sp)
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0x33E9B949))
                when {
                    list == null -> CircularProgressIndicator(color = C.Gold, modifier = Modifier.size(20.dp))
                    list!!.isEmpty() -> Sub("No comments yet")
                    else -> LazyColumn(Modifier.heightIn(max = 160.dp)) { items(list!!, key = { it.id }) { c -> Row(Modifier.padding(vertical = 2.dp)) { Text(c.author, color = C.GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("  ${c.body}", color = C.Text, fontSize = 11.sp) } } }
                }
                OutlinedTextField(text, { text = it.take(200) }, placeholder = { Text("Write a comment…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { val b = text.trim(); vm.addComment(p.id, b) { text = ""; vm.loadComments(p.id) { list = it } } }) { Text("Comment", color = C.Gold) } },
        dismissButton = { TextButton(onClick = close) { Text("Close") } })
}
