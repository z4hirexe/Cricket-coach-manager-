package com.cricketcoach.manager.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cricketcoach.manager.R
import com.cricketcoach.manager.auth.Auth
import com.cricketcoach.manager.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Shared premium frame for the account screens: brand panel on the left, form card on the right. */
@Composable
private fun AuthFrame(vm: GameVM, title: String, sub: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Image(painterResource(R.drawable.stadium_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.5f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0x99050A18), Color(0xE6050A18), Color(0xF7050A18)))))
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBars)).imePadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.logo_cmp), com.cricketcoach.manager.ui.Brand.FULL, Modifier.size(150.dp))
                Spacer(Modifier.height(6.dp))
                Text("YOUR CAREER. YOUR ACCOUNT.", color = C.Gold, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Careers, teams, coins, progress and settings are saved to your own private account and follow you to any phone.",
                    color = C.Sub, fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = C.Gold, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                    Text("Private by design - no other player can see your data.", color = C.Sub, fontSize = 10.sp)
                }
            }
            Column(Modifier.weight(1.1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xF2122453), Color(0xF20A1530))))
                .border(1.dp, Brush.verticalGradient(listOf(C.Gold.copy(alpha = 0.8f), Color(0x33E9B949))), RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vm.stack.size > 1 && vm.screen !is Screen.Login) IconButton(onClick = { vm.authError = null; vm.back() }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Text) }
                    Text(title.uppercase(), color = C.Gold, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.5.sp)
                }
                Sub(sub)
                Spacer(Modifier.height(10.dp))
                content()
                vm.authError?.let { Spacer(Modifier.height(8.dp)); Text(it, color = C.Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                if (vm.authBusy) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = C.Gold) }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false, email: Boolean = false, supporting: String? = null, isError: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), isError = isError,
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = when { password -> KeyboardType.Password; email -> KeyboardType.Email; else -> KeyboardType.Text }),
        trailingIcon = if (password) ({ TextButton(onClick = { show = !show }) { Text(if (show) "HIDE" else "SHOW", color = C.Gold, fontSize = 11.sp) } }) else null,
        supportingText = supporting?.let { { Text(it, fontSize = 11.sp) } },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Gold, focusedLabelColor = C.Gold, cursorColor = C.Gold))
}

@Composable
fun LoginScreen(vm: GameVM) {
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    AuthFrame(vm, "Log In", "Welcome back, coach.") {
        Field(email, { email = it.trim() }, "Email", email = true)
        Field(pw, { pw = it }, "Password", password = true)
        Spacer(Modifier.height(6.dp))
        GoldButton("LOG IN", Modifier.fillMaxWidth(), enabled = !vm.authBusy && email.isNotBlank() && pw.isNotBlank()) { vm.login(email, pw) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.authError = null; vm.nav(Screen.Forgot) }) { Text("Forgot password?", color = C.Gold, fontSize = 12.sp) }
            TextButton(onClick = { vm.authError = null; vm.nav(Screen.SignUp) }) { Text("Create account", color = C.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        HorizontalDivider(color = Color(0x33E9B949))
        Spacer(Modifier.height(6.dp))
        OutlineBtn("Play as Guest", Modifier.fillMaxWidth()) { vm.authError = null; vm.playAsGuest() }
        Sub("Guest mode keeps one temporary career on this device only (no cloud backup). Create a free account for 3 permanent career slots, cloud saves, coins and the online social feed.", size = 11)
    }
}

@Composable
fun SignUpScreen(vm: GameVM) {
    var email by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var display by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var avail by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(user) {
        avail = null
        if (Auth.validUsername(user)) { delay(450); avail = withContext(Dispatchers.IO) { Auth.usernameAvailable(user) } }
    }
    val userNote = when {
        user.isEmpty() -> "3-20 letters, numbers or _"
        !Auth.validUsername(user) -> "Only letters, numbers and _ (3-20)"
        avail == null -> "Checking..."
        avail == true -> "✓ Available"
        else -> "✗ Already taken"
    }
    val ok = Auth.validEmail(email) && Auth.validUsername(user) && avail != false && pw.length >= 6 && pw == pw2
    AuthFrame(vm, "Create Account", "One account per player. It takes 20 seconds.") {
        Field(email, { email = it.trim() }, "Email", email = true)
        Field(user, { user = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) }, "Username (public in the online feed)", supporting = userNote, isError = avail == false)
        Field(display, { display = it.take(40) }, "Display name (optional)")
        Field(pw, { pw = it }, "Password (min 6 characters)", password = true)
        Field(pw2, { pw2 = it }, "Confirm password", password = true, isError = pw2.isNotEmpty() && pw2 != pw, supporting = if (pw2.isNotEmpty() && pw2 != pw) "Passwords do not match" else null)
        Spacer(Modifier.height(6.dp))
        GoldButton("CREATE ACCOUNT", Modifier.fillMaxWidth(), enabled = ok && !vm.authBusy) { vm.signUp(email, pw, user, display) }
        Sub("Your email and password are handled by Supabase Auth (passwords are never stored in the game). Your careers and settings are visible only to you.", size = 11)
        TextButton(onClick = { vm.authError = null; vm.back() }) { Text("Already have an account? Log in", color = C.Gold, fontSize = 12.sp) }
    }
}

@Composable
fun ForgotScreen(vm: GameVM) {
    var email by remember { mutableStateOf("") }
    AuthFrame(vm, "Account Recovery", "We'll e-mail you a secure link to set a new password.") {
        Field(email, { email = it.trim() }, "Email of your account", email = true)
        Spacer(Modifier.height(6.dp))
        GoldButton("SEND RESET LINK", Modifier.fillMaxWidth(), enabled = Auth.validEmail(email) && !vm.authBusy) { vm.forgot(email) }
        Sub("Open the link on this phone - the game opens automatically and asks for your new password.", size = 11)
    }
}

@Composable
fun ResetPasswordScreen(vm: GameVM) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    AuthFrame(vm, "Set New Password", "Recovery link verified for ${vm.email}.") {
        Field(pw, { pw = it }, "New password (min 6)", password = true)
        Field(pw2, { pw2 = it }, "Confirm new password", password = true, isError = pw2.isNotEmpty() && pw != pw2)
        Spacer(Modifier.height(6.dp))
        GoldButton("UPDATE PASSWORD", Modifier.fillMaxWidth(), enabled = pw.length >= 6 && pw == pw2 && !vm.authBusy) { vm.setNewPassword(pw) }
    }
}

/** Center-crops and compresses a picked image to a JPEG (<= 1 MB). */
fun squareJpeg(bytes: ByteArray, size: Int, quality: Int = 85): ByteArray? = try {
    val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalArgumentException()
    val side = minOf(src.width, src.height)
    val crop = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val out = Bitmap.createScaledBitmap(crop, size, size, true)
    var q = quality; var data: ByteArray
    do { val bos = ByteArrayOutputStream(); out.compress(Bitmap.CompressFormat.JPEG, q, bos); data = bos.toByteArray(); q -= 10 } while (data.size > 900_000 && q > 30)
    data
} catch (_: Exception) { null }

@Composable
fun Avatar(bytes: ByteArray?, name: String, size: androidx.compose.ui.unit.Dp) {
    val img = remember(bytes) { bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() } }
    Box(Modifier.size(size).clip(CircleShape).background(C.GoldBrush).padding(2.dp).clip(CircleShape).background(C.Card), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.take(1).uppercase().ifEmpty { "?" }, color = C.Gold, fontWeight = FontWeight.Black, fontSize = (size.value * 0.42f).sp)
    }
}

@Composable
fun AccountScreen(vm: GameVM) {
    val p = vm.profile
    val ctx = LocalContext.current
    var user by remember(p?.username) { mutableStateOf(p?.username ?: "") }
    var display by remember(p?.displayName) { mutableStateOf(p?.displayName ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteText by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val raw = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            val jpg = raw?.let { squareJpeg(it, 512) }
            if (jpg == null) vm.msg("Could not read that image") else vm.uploadAvatar(jpg)
        }
    }
    if (!vm.signedIn) { LaunchedEffect(Unit) { vm.replaceTop(Screen.Login) }; return }
    ScreenScaffold(vm, "My Account") {
        Row(Modifier.fillMaxSize().padding(12.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable { picker.launch("image/*") }) {
                    Avatar(vm.avatar, p?.displayName?.ifBlank { p.username } ?: "?", 110.dp)
                    Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(C.Gold), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Edit, "Change photo", tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(p?.displayName?.ifBlank { p.username } ?: "Loading…", color = C.Gold, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Sub("@${p?.username ?: ""} • ${vm.email}")
                Spacer(Modifier.height(10.dp))
                Row {
                    StatChip("COINS", "${vm.coins}", Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    StatChip("LEVEL", "${vm.level}", Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    StatChip("XP", "${vm.xp}", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                CcmCard {
                    Text("CLOUD SAVES", color = C.Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Sub("${vm.saves.size}/3 careers • ${vm.syncStatus.ifEmpty { "ready" }}")
                    Sub("Last backup: ${vm.settings.lastSync}")
                    Spacer(Modifier.height(4.dp))
                    OutlineBtn("Back up now", Modifier.fillMaxWidth()) { vm.manualSave(); vm.syncInBackground() }
                }
            }
            Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState())) {
                CcmCard {
                    Text("PROFILE", color = C.Gold, fontWeight = FontWeight.Bold)
                    Field(user, { user = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) }, "Username")
                    Field(display, { display = it.take(40) }, "Display name")
                    Row {
                        GoldButton("Save profile", Modifier.weight(1f), enabled = !vm.authBusy && (user != p?.username || display != p?.displayName)) { vm.updateProfile(user, display) }
                        Spacer(Modifier.width(8.dp))
                        OutlineBtn("Change photo", Modifier.weight(1f)) { picker.launch("image/*") }
                    }
                    vm.authError?.let { Text(it, color = C.Red, fontSize = 12.sp) }
                }
                CcmCard {
                    Text("SECURITY & PRIVACY", color = C.Gold, fontWeight = FontWeight.Bold)
                    Sub("• Your careers, coins, progress, settings and photo are stored privately (row-level security): only your account can read them.")
                    Sub("• Only posts you choose to share in the online Social feed are visible to other players (with your username).")
                    Sub("• Your login is kept encrypted on this phone (Android Keystore).")
                    Spacer(Modifier.height(6.dp))
                    Row {
                        OutlineBtn("Reset password", Modifier.weight(1f)) { vm.nav(Screen.Forgot) }
                        Spacer(Modifier.width(8.dp))
                        GreenButton("Log out", Modifier.weight(1f)) { vm.logout() }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete my account permanently", color = C.Red, fontSize = 12.sp) }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = C.Surface,
        title = { Text("Delete account?", color = C.Red) },
        text = {
            Column {
                Text("This permanently deletes your account, all cloud careers, coins, posts and your photo. It cannot be undone.", color = C.Text)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(deleteText, { deleteText = it }, label = { Text("Type DELETE to confirm") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = deleteText == "DELETE" && !vm.authBusy, onClick = { confirmDelete = false; vm.deleteAccount() }) { Text("Delete forever", color = C.Red) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(C.Card).border(1.dp, Color(0x55E9B949), RoundedCornerShape(10.dp)).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = C.GoldLight, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(label, color = C.Sub, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

/** Offered once after the first login on a phone that has guest careers. */
@Composable
fun GuestImportDialog(vm: GameVM) {
    val offer = vm.guestImportOffer
    if (offer.isEmpty()) return
    AlertDialog(onDismissRequest = { vm.importGuestSaves(false) }, containerColor = C.Surface,
        title = { Text("Move device careers to your account?", color = C.Gold) },
        text = { Text("Found ${offer.size} career(s) saved on this phone in guest mode: ${offer.joinToString { it.club }}. Import them into your account so they are backed up and permanent?", color = C.Text) },
        confirmButton = { TextButton(onClick = { vm.importGuestSaves(true) }) { Text("Import", color = C.Gold) } },
        dismissButton = { TextButton(onClick = { vm.importGuestSaves(false) }) { Text("Not now") } })
}
