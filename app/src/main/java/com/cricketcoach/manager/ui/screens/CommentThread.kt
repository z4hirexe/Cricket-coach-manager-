package com.cricketcoach.manager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.audio.GameAudio
import com.cricketcoach.manager.game.SocialComment
import com.cricketcoach.manager.game.Stumps
import com.cricketcoach.manager.ui.*
import kotlin.math.abs

private val AV = listOf(Color(0xFF4F8EF7), Color(0xFFE9B949), Color(0xFF3DBE7A), Color(0xFFE5484D), Color(0xFF7FB2FF), Color(0xFFF59E5B), Color(0xFF9AA8C4))

/**
 * Shared comment thread for Stumpstagram and Yorker (v2.6).
 *  - [total] is the post's full comment count (e.g. 20.4K) - shown, never materialised.
 *  - Stored comments (pre-generated top comments, the coach's comments and the replies they got) come first.
 *  - Then generated comments are loaded lazily, 20 at a time, from [page]; each one is unique for the post.
 *  - Reply to any comment; replies are nested under their parent.
 */
@Composable
fun CommentThreadView(
    key: Any, total: Int, stored: List<SocialComment>, generatedTotal: Int, page: (Int, Int) -> List<SocialComment>,
    meName: String, version: Int, modifier: Modifier = Modifier, title: String = "Comments", accent: Color = DS.Gold, subtitle: String = "",
    onSend: (text: String, replyTo: Int) -> Unit,
) {
    var loaded by remember(key) { mutableStateOf(page(0, 20)) }
    var text by remember(key) { mutableStateOf("") }
    var replyTo by remember(key) { mutableStateOf<SocialComment?>(null) }
    val state = rememberLazyListState()
    @Suppress("UNUSED_VARIABLE") val v = version
    val snap = stored.toList()
    val top = snap.filter { it.replyTo == 0 }.sortedByDescending { if (it.mine) 1 else 0 }
    val repliesOf = snap.filter { it.replyTo != 0 }.groupBy { it.replyTo }
    val more = loaded.size < generatedTotal
    // lazy loading: when the list end comes into view, fetch the next page
    val nearEnd by remember(key) { derivedStateOf { val li = state.layoutInfo; (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 3 } }
    LaunchedEffect(nearEnd, loaded.size, generatedTotal) { if (nearEnd && loaded.size < generatedTotal) loaded = loaded + page(loaded.size, 20) }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), color = DS.White, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.6.sp)
            Spacer(Modifier.width(8.dp))
            Text(Stumps.compact(maxOf(total, snap.size).toLong()), color = accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(subtitle.ifEmpty { "${top.size + loaded.size} shown" }, color = DS.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(Modifier.weight(1f), state = state) {
            items(top, key = { "s" + it.id }) { c -> CommentItem(c, repliesOf[c.id].orEmpty(), accent) { replyTo = c } }
            items(loaded, key = { "g" + it.id }) { c -> CommentItem(c, repliesOf[c.id].orEmpty(), accent) { replyTo = c } }
            item {
                Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                    if (more) Text("Load more comments", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { loaded = loaded + page(loaded.size, 20) }.padding(horizontal = 12.dp, vertical = 8.dp))
                    else if (total > top.size + loaded.size) Text("Showing the most relevant ${top.size + loaded.size} of ${Stumps.compact(total.toLong())} comments", color = DS.Muted, fontSize = 10.sp)
                }
            }
        }
        val rt = replyTo
        if (rt != null) Row(Modifier.fillMaxWidth().background(DS.Navy3).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Replying to ${rt.handle}", color = DS.Sub, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.Close, "Cancel reply", tint = DS.Sub, modifier = Modifier.size(28.dp).clickable { replyTo = null }.padding(6.dp))
        }
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it.take(220) }, placeholder = { Text(if (rt != null) "Reply as $meName…" else "Comment as $meName…", fontSize = 11.sp, maxLines = 1) }, singleLine = true,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp), textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = DS.Line, cursorColor = accent, focusedTextColor = DS.White, unfocusedTextColor = DS.White))
            IconButton(onClick = {
                val b = text.trim()
                if (b.isNotEmpty()) { GameAudio.click(); onSend(b, rt?.id ?: 0); text = ""; replyTo = null }
            }) { Icon(Icons.Filled.Send, "Send", tint = accent) }
        }
    }
}

@Composable
private fun CommentItem(c: SocialComment, replies: List<SocialComment>, accent: Color, onReply: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        Bubble(c, accent, onReply)
        for (r in replies) Box(Modifier.padding(start = 34.dp, top = 4.dp)) { Bubble(r, accent, null) }
    }
}

@Composable
private fun Bubble(c: SocialComment, accent: Color, onReply: (() -> Unit)?) {
    val col = if (c.mine) accent else AV[abs(c.handle.hashCode()) % AV.size]
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(col.copy(alpha = 0.22f)).border(1.dp, col.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
            Text(c.author.take(1).uppercase(), color = col, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (c.pid.isNotEmpty()) DS.Navy4 else DS.Navy3).border(1.dp, if (c.pid.isNotEmpty()) accent.copy(alpha = 0.5f) else DS.Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.handle.ifBlank { c.author }, color = if (c.pid.isNotEmpty() || c.mine) accent else DS.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (c.pid.isNotEmpty()) { Spacer(Modifier.width(4.dp)); DsTag("Player", accent) }
                if (c.mine) { Spacer(Modifier.width(4.dp)); DsTag("You", accent) }
            }
            Text(c.text, color = Color(0xFFDDE3EE), fontSize = 12.sp, lineHeight = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                if (c.likes > 0) Text("♥ ${Stumps.compact(c.likes.toLong())}", color = DS.Muted, fontSize = 10.sp)
                if (onReply != null) Text("Reply", color = DS.Sub, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(4.dp)).clickable { onReply() }.padding(horizontal = 4.dp, vertical = 4.dp))
            }
        }
    }
}
