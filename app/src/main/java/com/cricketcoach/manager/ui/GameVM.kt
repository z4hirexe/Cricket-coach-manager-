package com.cricketcoach.manager.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cricketcoach.manager.data.Repo
import com.cricketcoach.manager.game.*
import com.cricketcoach.manager.save.CloudSync
import com.cricketcoach.manager.save.SaveManager
import com.cricketcoach.manager.save.SaveMeta
import com.cricketcoach.manager.save.Settings
import com.cricketcoach.manager.save.DevicePrefs
import com.cricketcoach.manager.auth.Auth
import com.cricketcoach.manager.auth.Profile
import com.cricketcoach.manager.auth.OnlinePost
import com.cricketcoach.manager.audio.GameAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

sealed class Screen(val title: String) {
    object Main : Screen("Cricket Manager Pro")
    object Career : Screen("Career Mode")
    object NewCareer : Screen("New Career")
    object Dashboard : Screen("Dashboard")
    object MyClub : Screen("My Club")
    data class Squad(val clubId: String? = null) : Screen("Squad")
    object Database : Screen("Player Database")
    data class Profile(val pid: String) : Screen("Player Profile")
    object AuctionScr : Screen("Auction")
    object Transfers : Screen("Transfers")
    object Fixtures : Screen("Fixtures")
    object PreMatch : Screen("Match Centre")
    object Live : Screen("Live Match")
    object Table : Screen("League Table")
    object TournamentsScr : Screen("Tournaments")
    object Statistics : Screen("Statistics")
    object Finances : Screen("Club Finances")
    object SettingsScr : Screen("Settings")
    object SeasonSummary : Screen("Season Review")
    // v2: accounts, career types, social
    object Login : Screen("Log In")
    object SignUp : Screen("Create Account")
    object Forgot : Screen("Forgot Password")
    object ResetPassword : Screen("Set New Password")
    object Account : Screen("My Account")
    object CareerType : Screen("Career Type")
    data class ChooseTeam(val mode: String) : Screen("Choose Team")
    object NewTeam : Screen("New Team")
    object Selection : Screen("Squad Selection")
    object Social : Screen("Social Media")
    object Inbox : Screen("Inbox")
}

class PendingMatch(val compId: String, val fixtureId: String)

class GameVM(app: Application) : AndroidViewModel(app) {
    val device = DevicePrefs(app)
    /** Per-account storage: every user has separate saves, settings, coins and progress. */
    var sm by mutableStateOf(SaveManager(app))
        private set
    var settings by mutableStateOf(Settings(app))
        private set
    var profile by mutableStateOf<Profile?>(null)
    var signedIn by mutableStateOf(false)
    var email by mutableStateOf("")
    var avatar by mutableStateOf<ByteArray?>(null)
    var coins by mutableIntStateOf(1000)
    var xp by mutableIntStateOf(0)
    var authBusy by mutableStateOf(false)
    var authError by mutableStateOf<String?>(null)
    var guestImportOffer by mutableStateOf<List<SaveMeta>>(emptyList())
    // live commentary
    val comLines = mutableStateListOf<ComLine>()
    private var commentary: Commentary? = null
    private var comFor: MatchSim? = null
    private var comDone = 0
    // online social feed
    var onlinePosts by mutableStateOf<List<OnlinePost>>(emptyList())
    var onlineLiked by mutableStateOf<Set<Long>>(emptySet())
    var following by mutableStateOf<Set<String>>(emptySet())
    var onlineStatus by mutableStateOf("")
    var loading by mutableStateOf(true)
    var loadError by mutableStateOf<String?>(null)
    var save by mutableStateOf<CareerSave?>(null)
    var tick by mutableIntStateOf(0)
    val stack = mutableStateListOf<Screen>(Screen.Main)
    var toast by mutableStateOf<String?>(null)
    var pending by mutableStateOf<PendingMatch?>(null)
    var sim by mutableStateOf<MatchSim?>(null)
    var simTick by mutableIntStateOf(0)
    var autoPlay by mutableStateOf(false)
    var matchApplied by mutableStateOf(false)
    var syncStatus by mutableStateOf("")
    var busy by mutableStateOf(false)
    var saves by mutableStateOf<List<SaveMeta>>(emptyList())
    private val rnd = Random.Default

    init {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { Repo.load(app); Auth.init(app) }
                val uid = Auth.uid
                if (uid != null) switchUser(uid, Auth.session?.email ?: "") else { coins = settings.coins; xp = settings.xp }
                saves = withContext(Dispatchers.IO) { sm.list() }
                if (uid == null && !device.guestChosen) stack.add(Screen.Login)
                if (uid != null) refreshAccount(firstLogin = false)
            } catch (e: Exception) {
                loadError = "Could not load dataset: ${e.message}"
            }
            loading = false
            syncInBackground()
        }
    }

    val screen: Screen get() = stack.last()
    fun nav(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun replaceTop(s: Screen) { if (stack.isNotEmpty()) stack.removeAt(stack.size - 1); stack.add(s) }
    fun goHome() { stack.clear(); stack.add(Screen.Main); if (save != null) stack.add(Screen.Dashboard) }
    /** Sidebar navigation: keep the back stack shallow (Main > Home > section). */
    fun navRoot(s: Screen) { stack.clear(); stack.add(Screen.Main); if (save != null) stack.add(Screen.Dashboard); if (s != Screen.Dashboard && s != Screen.Main) stack.add(s) }
    /** Inbox: news items newer than the last one the user opened. */
    fun unreadNews(): Int { val s = save ?: return 0; if (s.news.isEmpty()) return 0; val i = s.news.indexOf(s.newsSeen); return if (i >= 0) i else minOf(s.news.size, 99) }
    fun markNewsRead() { val s = save ?: return; val top = s.news.firstOrNull() ?: return; if (s.newsSeen != top) { s.newsSeen = top; bump() } }
    fun toggleMusic() { settings.music = !settings.music; com.cricketcoach.manager.audio.GameAudio.refresh(); bump() }
    fun bump() { tick++ }
    fun msg(s: String?) { if (s != null) toast = s }

    /* ---------------- saving ---------------- */
    fun persist() {
        bump()
        val s = save ?: return
        drainRewards(s)
        if (!settings.autosave) return
        val json = sm.toJson(s)
        val meta = sm.metaOf(s)
        viewModelScope.launch(Dispatchers.IO) {
            sm.write(meta, json)
            val list = sm.list()
            withContext(Dispatchers.Main) { saves = list }
            syncInBackground()
        }
    }

    fun manualSave() {
        val s = save ?: return
        val json = sm.toJson(s); val meta = sm.metaOf(s)
        viewModelScope.launch(Dispatchers.IO) {
            sm.write(meta, json)
            val list = sm.list()
            withContext(Dispatchers.Main) { saves = list; toast = "Game saved to slot ${s.slot}" }
            syncInBackground()
        }
    }

    fun manualSaveSilent() {
        val s = save ?: return
        val json = sm.toJson(s); val meta = sm.metaOf(s)
        viewModelScope.launch(Dispatchers.IO) { sm.write(meta, json) }
    }

    fun syncInBackground() {
        val ctx = getApplication<Application>()
        val m = sm; val st = settings
        viewModelScope.launch(Dispatchers.IO) {
            val r = CloudSync.syncUp(ctx, m, st)
            if (st.profileDirty && Auth.signedIn && m === sm) pushProfile()
            withContext(Dispatchers.Main) { syncStatus = r }
        }
    }

    fun cloudRestore(slot: Int) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val r = CloudSync.download(ctx, sm, slot)
            val list = sm.list()
            withContext(Dispatchers.Main) { toast = r; saves = list }
        }
    }

    fun refreshSaves() { viewModelScope.launch(Dispatchers.IO) { val l = sm.list(); withContext(Dispatchers.Main) { saves = l } } }

    /* ---------------- accounts ---------------- */
    val isGuest: Boolean get() = !signedIn
    /** Guests get one device-only career slot; an account unlocks 3 permanent slots with cloud backup. */
    fun slotAllowed(slot: Int) = signedIn || slot == 1
    val level: Int get() = 1 + xp / 500

    private fun switchUser(key: String, mail: String) {
        val app = getApplication<Application>()
        if (save != null) manualSaveSilent()
        save = null; pending = null; sim = null; comLines.clear()
        sm = SaveManager(app, key); settings = Settings(app, key)
        signedIn = key != SaveManager.GUEST; email = mail
        coins = settings.coins; xp = settings.xp
        GameAudio.bind(settings); Faces.enabled = settings.photos
        onlinePosts = emptyList(); onlineLiked = emptySet(); following = emptySet(); avatar = null
        if (!signedIn) profile = null
    }

    private fun afterAuth(firstLogin: Boolean) {
        val uid = Auth.uid ?: return
        switchUser(uid, Auth.session?.email ?: "")
        refreshSaves()
        stack.clear(); stack.add(Screen.Main)
        refreshAccount(firstLogin)
    }

    /** Pulls profile, settings, coins, avatar and cloud careers of the signed-in user. */
    fun refreshAccount(firstLogin: Boolean) {
        val ctx = getApplication<Application>()
        val m = sm; val st = settings
        viewModelScope.launch(Dispatchers.IO) {
            val p = Auth.profile() ?: return@launch
            if (st.profileDirty) pushProfile() else {
                if (p.settings != null && p.settings.size() > 0) st.applyJson(p.settings)
                st.coins = p.coins; st.xp = p.xp
            }
            val av = p.avatarPath?.let { Auth.downloadAvatar(it) }
            // restore cloud careers that are not on this device yet (e.g. new phone)
            val local = m.list().map { it.slot }.toSet()
            var restored = 0
            if (CloudSync.online(ctx)) Auth.listSaves()?.filter { it.slot !in local }?.forEach { cs -> if (CloudSync.download(ctx, m, cs.slot).startsWith("Cloud career")) restored++ }
            val list = m.list()
            val guestSaves = if (device.importedGuestFor != p.id) SaveManager(ctx).list() else emptyList()
            withContext(Dispatchers.Main) {
                if (m !== sm) return@withContext
                profile = if (st.profileDirty) p.copy(coins = st.coins, xp = st.xp) else p
                coins = st.coins; xp = st.xp; avatar = av; saves = list
                GameAudio.refresh(); Faces.enabled = st.photos
                if (restored > 0) toast = "Restored $restored career(s) from your cloud account"
                if (guestSaves.isNotEmpty()) guestImportOffer = guestSaves
                else if (firstLogin) toast = "Welcome, ${p.displayName.ifBlank { p.username }}!"
            }
        }
    }

    private fun pushProfile() {
        val st = settings
        if (Auth.updateProfile(mapOf("coins" to st.coins, "xp" to st.xp, "settings" to st.toJson())) == null) st.profileDirty = false
    }

    fun saveSettingsToCloud() { settings.profileDirty = true; if (signedIn) viewModelScope.launch(Dispatchers.IO) { pushProfile() } }

    private fun authCall(firstLogin: Boolean, f: () -> String?) {
        authBusy = true; authError = null
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { f() }
            authBusy = false
            if (err != null) authError = err else { device.guestChosen = false; afterAuth(firstLogin) }
        }
    }

    fun login(email: String, pw: String) = authCall(true) { Auth.signIn(email, pw) }
    fun signUp(email: String, pw: String, username: String, display: String) = authCall(true) { Auth.signUp(email, pw, username, display) }

    fun forgot(email: String) {
        authBusy = true; authError = null
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { Auth.recover(email) }
            authBusy = false
            if (err != null) authError = err else { toast = "Password reset link sent to $email. Open it on this phone."; back() }
        }
    }

    /** Handles cricketmanagerpro://auth-callback#access_token=...&type=recovery from the reset e-mail. */
    fun handleDeepLink(uri: String) {
        val params = Auth.parseCallback(uri)
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { Auth.acceptCallback(params) }
            if (err != null) { toast = err; return@launch }
            afterAuth(false)
            if (params["type"] == "recovery") nav(Screen.ResetPassword) else toast = "Signed in"
        }
    }

    fun setNewPassword(pw: String) {
        authBusy = true; authError = null
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { Auth.updatePassword(pw) }
            authBusy = false
            if (err != null) authError = err else { toast = "Password updated"; back() }
        }
    }

    fun playAsGuest() { device.guestChosen = true; if (signedIn) logout() else { stack.clear(); stack.add(Screen.Main) } }

    fun logout() {
        manualSaveSilent()
        val m = sm; val st = settings
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CloudSync.syncUp(getApplication(), m, st); if (st.profileDirty) pushProfile(); Auth.signOut() }
            switchUser(SaveManager.GUEST, ""); refreshSaves()
            stack.clear(); stack.add(Screen.Main); stack.add(Screen.Login)
            toast = "Logged out. Your careers are safe in your account."
        }
    }

    fun deleteAccount() {
        authBusy = true
        val ctx = getApplication<Application>(); val key = sm.userKey
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { Auth.deleteAccount() }
            authBusy = false
            if (err != null) { toast = err; return@launch }
            save = null
            withContext(Dispatchers.IO) {
                ctx.deleteDatabase("ccm_saves_${SaveManager.safe(key)}.db")
                java.io.File(ctx.filesDir, "accounts/${SaveManager.safe(key)}").deleteRecursively()
                ctx.deleteSharedPreferences("ccm_settings_${SaveManager.safe(key)}")
            }
            switchUser(SaveManager.GUEST, ""); refreshSaves()
            stack.clear(); stack.add(Screen.Main); stack.add(Screen.Login)
            toast = "Account and all its data were permanently deleted."
        }
    }

    fun updateProfile(username: String, display: String) {
        authBusy = true; authError = null
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) {
                if (!Auth.validUsername(username)) "Username: 3-20 letters, numbers or _"
                else Auth.updateProfile(mapOf("username" to username, "display_name" to display.take(40)))
            }
            authBusy = false
            if (err != null) authError = err else { profile = profile?.copy(username = username, displayName = display); toast = "Profile updated" }
        }
    }

    fun uploadAvatar(jpeg: ByteArray) {
        authBusy = true
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { Auth.uploadAvatar(jpeg) }
            authBusy = false
            if (err != null) toast = err else { avatar = jpeg; profile = profile?.copy(avatarPath = "${Auth.uid}/avatar.jpg"); toast = "Profile photo updated" }
        }
    }

    /** Copies device (guest) careers into the signed-in account's free slots. */
    fun importGuestSaves(accept: Boolean) {
        val offer = guestImportOffer; guestImportOffer = emptyList()
        val uid = Auth.uid ?: return
        device.importedGuestFor = uid
        if (!accept) return
        val ctx = getApplication<Application>(); val m = sm
        viewModelScope.launch(Dispatchers.IO) {
            val guest = SaveManager(ctx)
            var n = 0
            for (g in offer) {
                val free = (1..3).firstOrNull { sl -> m.list().none { it.slot == sl } } ?: break
                val json = guest.rawJson(g.slot) ?: continue
                if (m.importJson(free, json) != null) n++
            }
            val list = m.list()
            withContext(Dispatchers.Main) { saves = list; toast = if (n > 0) "Imported $n device career(s) into your account" else "No free slot to import into" }
            CloudSync.syncUp(ctx, m, settings)
        }
    }

    /* ---------------- coins / xp ---------------- */
    private fun drainRewards(s: CareerSave) {
        if (s.pendingCoins == 0 && s.pendingXp == 0) return
        val gained = s.pendingCoins
        coins = (coins + s.pendingCoins).coerceAtLeast(0); xp += s.pendingXp
        s.pendingCoins = 0; s.pendingXp = 0
        settings.coins = coins; settings.xp = xp; settings.profileDirty = true
        profile = profile?.copy(coins = coins, xp = xp)
        if (gained >= 100) toast = "+$gained coins earned!"
    }

    fun spendCoins(n: Int, what: String): Boolean {
        if (coins < n) { toast = "Not enough coins ($coins / $n)"; return false }
        coins -= n; settings.coins = coins; settings.profileDirty = true
        profile = profile?.copy(coins = coins)
        GameAudio.play("coin"); toast = "Spent $n coins: $what"
        syncInBackground()
        return true
    }

    /* ---------------- online social feed (Supabase) ---------------- */
    fun loadOnlineFeed(onlyUser: String? = null) {
        if (!signedIn) { onlineStatus = "Log in to see the online feed"; return }
        onlineStatus = "Loading…"
        viewModelScope.launch(Dispatchers.IO) {
            val posts = Auth.feed(50, onlyUser)
            val liked = posts?.let { Auth.myLikes(it.map { p -> p.id }) } ?: emptySet()
            val fol = Auth.following()
            withContext(Dispatchers.Main) {
                if (posts == null) onlineStatus = "Offline - showing your local feed only"
                else { onlinePosts = posts; onlineLiked = liked; following = fol; onlineStatus = if (posts.isEmpty()) "No posts yet - be the first to share!" else "" }
            }
        }
    }

    fun shareOnline(p: SocialPost) {
        val s = save
        if (!signedIn) { toast = "Log in to share online"; return }
        viewModelScope.launch(Dispatchers.IO) {
            val err = Auth.publish(s?.userClub()?.name ?: "", p.kind, p.text)
            withContext(Dispatchers.Main) { if (err == null) { p.shared = true; toast = "Shared to the online feed"; persist(); loadOnlineFeed() } else toast = err }
        }
    }

    fun toggleOnlineLike(p: OnlinePost) {
        val like = p.id !in onlineLiked
        onlineLiked = if (like) onlineLiked + p.id else onlineLiked - p.id
        onlinePosts = onlinePosts.map { if (it.id == p.id) it.copy(likes = it.likes + if (like) 1 else -1) else it }
        viewModelScope.launch(Dispatchers.IO) { Auth.setLike(p.id, like)?.let { e -> withContext(Dispatchers.Main) { toast = e } } }
    }

    fun toggleFollow(userId: String) {
        val f = userId !in following
        following = if (f) following + userId else following - userId
        viewModelScope.launch(Dispatchers.IO) { Auth.setFollow(userId, f)?.let { e -> withContext(Dispatchers.Main) { toast = e; following = following - userId } } }
    }

    fun deleteOnlinePost(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { val e = Auth.deletePost(id); withContext(Dispatchers.Main) { if (e == null) onlinePosts = onlinePosts.filter { it.id != id } else toast = e } }
    }

    fun loadComments(postId: Long, done: (List<com.cricketcoach.manager.auth.OnlineComment>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { val c = Auth.comments(postId) ?: emptyList(); withContext(Dispatchers.Main) { done(c) } }
    }

    fun addComment(postId: Long, text: String, done: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val e = Auth.comment(postId, text)
            withContext(Dispatchers.Main) { if (e != null) toast = e else { onlinePosts = onlinePosts.map { if (it.id == postId) it.copy(comments = it.comments + 1) else it }; done() } }
        }
    }

    fun followerCount(userId: String, done: (Int?) -> Unit) { viewModelScope.launch(Dispatchers.IO) { val n = Auth.followerCount(userId); withContext(Dispatchers.Main) { done(n) } } }

    /* ---------------- commentary ---------------- */
    /** Generates commentary for new deliveries of [m]; returns the newest line (to be spoken). */
    fun updateCommentary(m: MatchSim): ComLine? {
        if (comFor !== m) {
            comFor = m; comDone = 0; comLines.clear()
            commentary = Commentary(settings.comLang, settings.comStyle, expert = settings.comExpert)
        }
        val c = commentary ?: return null
        val evs = m.events
        if (evs.size <= comDone) return null
        val start = if (evs.size - comDone > 8) evs.size - 8 else comDone // fast-forwarded: describe only the latest balls (stats still tracked)
        var last: ComLine? = null
        for (i in comDone until evs.size) {
            val lines = c.process(evs[i], m, emit = i >= start)
            for (l in lines) { if (l.kind == "OVER" && !settings.comOverCards) continue; comLines.add(0, l) }
            lines.firstOrNull { it.kind == evs[i].kind }?.let { last = it }
        }
        while (comLines.size > 400) comLines.removeAt(comLines.size - 1)
        comDone = evs.size
        return last
    }

    /** Commentators on air for the current match (lead, analyst). */
    fun booth(): List<Caster> = commentary?.booth ?: Commentary.boothFor(settings.comLang, settings.comStyle)

    /** Speaks everything said about delivery [evId]: the lead's call, then queued special calls and the analyst. */
    fun speakBall(evId: Int) {
        val c = commentary ?: return
        val lines = comLines.filter { it.evId == evId && it.spoken }.reversed()
        var first = true
        for (l in lines) { GameAudio.speakAs(l.text, l.lang, c.booth.getOrNull(l.speaker), first); first = false }
    }

    /* ---------------- career lifecycle ---------------- */
    fun newModeCareer(slot: Int, manager: String, mode: String, teamId: String) {
        if (!slotAllowed(slot)) { msg("Create a free account to use career slot $slot."); return }
        busy = true
        viewModelScope.launch {
            val s = withContext(Dispatchers.Default) { Modes.newCareer(slot, manager.ifBlank { profile?.displayName?.ifBlank { null } ?: "Coach" }, mode, teamId, settings.economy.toDouble(), settings.difficulty) }
            save = s; settings.lastSlot = slot; busy = false
            pending = null; sim = null
            stack.clear(); stack.add(Screen.Main); stack.add(Screen.Dashboard)
            persist()
        }
    }

    fun newCareer(slot: Int, manager: String, club: String, short: String, country: String, primary: Long, secondary: Long, logo: Int, jersey: Int, setup: String,
                  stadium: String = "", logoFile: String = "", boost: Boolean = false) {
        if (!slotAllowed(slot)) { msg("Create a free account to use career slot $slot."); return }
        if (boost && !spendCoins(500, "transfer budget boost")) return
        busy = true
        viewModelScope.launch {
            val s = withContext(Dispatchers.Default) {
                Career.newCareer(slot, manager.ifBlank { "Coach" }, club.ifBlank { "My Club" }, short.ifBlank { "MYC" }, country,
                    primary, secondary, logo, jersey, setup, settings.economy.toDouble(), settings.difficulty)
            }
            s.userClub().let { u ->
                if (stadium.isNotBlank()) u.stadium = stadium.take(40)
                if (logoFile.isNotBlank()) u.logoFile = logoFile
                if (boost) u.balance += 10.0 * s.economy
            }
            Social.post(s, Social.OFFICIAL, "NEW CLUB: ${s.userClub().name} are founded with ${s.manager} as head coach. Next stop: Division 1! #${s.userClub().short}", "NEWS", 600, rnd)
            save = s; settings.lastSlot = slot; busy = false
            pending = null; sim = null
            stack.clear(); stack.add(Screen.Main); stack.add(Screen.Dashboard)
            persist()
            if (s.auction != null) nav(Screen.AuctionScr)
        }
    }

    fun loadCareer(slot: Int) {
        busy = true
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) { sm.load(slot) }
            busy = false
            if (s == null) { toast = "Could not load slot $slot"; return@launch }
            save = s; settings.lastSlot = slot; pending = null; sim = null
            stack.clear(); stack.add(Screen.Main); stack.add(Screen.Dashboard)
        }
    }

    fun deleteSlot(slot: Int) {
        val m = sm
        viewModelScope.launch(Dispatchers.IO) {
            m.delete(slot); if (!m.isGuest) Auth.deleteSave(slot)
            val l = m.list(); withContext(Dispatchers.Main) { saves = l; if (save?.slot == slot) save = null }
        }
    }

    fun exitToMenu() { manualSave(); stack.clear(); stack.add(Screen.Main) }

    /* ---------------- calendar ---------------- */
    fun continueAction() {
        val s = save ?: return
        if (pending != null) { nav(Screen.PreMatch); return }
        when (s.phase) {
            "PRESEASON" -> { val err = Career.startSeason(s); if (err != null) msg(err) else msg("Season ${s.season} is under way!"); persist() }
            "LEAGUE", "PLAYOFFS" -> runDays(false, 1)
            "OFFSEASON" -> nav(Screen.SeasonSummary)
        }
    }

    private sealed class DayOut { object Next : DayOut(); object SeasonEnd : DayOut(); class Match(val pm: PendingMatch) : DayOut() }

    /** Engine-only day step (safe off the main thread). */
    private fun advanceDay(s: CareerSave, simUser: Boolean): DayOut {
        if (Career.leagueFinished(s)) { Career.endSeason(s, rnd); return DayOut.SeasonEnd }
        val nextDay = s.league.flatMap { it.fixtures }.filter { !it.played }.minOfOrNull { it.day }
            ?: run { Career.endSeason(s, rnd); return DayOut.SeasonEnd }
        s.day = nextDay
        if (s.mode == Modes.CLUB && nextDay > Career.LEAGUE_DAYS) s.phase = "PLAYOFFS"
        val list = Career.fixturesOnDay(s, nextDay)
        val hosts = list.map { it.second.home }.toSet()
        var userFx: Pair<Competition, Fixture>? = null
        for ((c, f) in list) {
            if (f.home == s.userClubId || f.away == s.userClubId) { if (simUser || userFx != null) Career.quickSim(s, c, f, rnd) else userFx = c to f }
            else Career.quickSim(s, c, f, rnd)
        }
        if (s.weeklyDoneDay != nextDay) { Career.weeklyUpdate(s, hosts, rnd); s.weeklyDoneDay = nextDay }
        if (userFx != null) return DayOut.Match(PendingMatch(userFx.first.id, userFx.second.id))
        if (Career.leagueFinished(s)) { Career.endSeason(s, rnd); return DayOut.SeasonEnd }
        return DayOut.Next
    }

    private fun runDays(simUser: Boolean, maxDays: Int) {
        val s = save ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            val out = withContext(Dispatchers.Default) {
                var o: DayOut = DayOut.Next; var n = 0
                while (n++ < maxDays && s.phase != "OFFSEASON") { o = advanceDay(s, simUser); if (o !is DayOut.Next) break }
                o
            }
            busy = false
            if (save !== s) return@launch
            when (out) {
                is DayOut.SeasonEnd -> { persist(); nav(Screen.SeasonSummary) }
                is DayOut.Match -> { pending = out.pm; sim = null; matchApplied = false; persist(); nav(Screen.PreMatch) }
                else -> { persist(); if (s.phase == "OFFSEASON") nav(Screen.SeasonSummary) }
            }
        }
    }

    fun simToSeasonEnd() {
        val s = save ?: return
        if (s.phase == "PRESEASON") { val err = Career.startSeason(s); if (err != null) { msg(err); return } }
        if (s.phase == "OFFSEASON") { nav(Screen.SeasonSummary); return }
        runDays(true, 500)
    }

    fun beginNextSeason() {
        val s = save ?: return
        Career.beginNextSeason(s)
        persist()
        goHome()
        if (s.auction != null) nav(Screen.AuctionScr)
    }

    /* ---------------- matches ---------------- */
    fun compById(id: String): Competition? { val s = save ?: return null; return s.league.firstOrNull { it.id == id } ?: s.tournament?.takeIf { it.id == id } }
    fun pendingComp(): Competition? = pending?.let { compById(it.compId) }
    fun pendingFixture(): Fixture? { val p = pending ?: return null; return pendingComp()?.fixtures?.firstOrNull { it.id == p.fixtureId } }
    fun userSideId(): String? = pendingComp()?.userTeam

    fun prepareSim(): MatchSim? {
        val s = save ?: return null
        val c = pendingComp() ?: return null; val f = pendingFixture() ?: return null
        if (sim == null) { sim = Career.makeSim(s, c, f, rnd).also { it.userTeam = c.userTeam ?: "" }; matchApplied = false }
        return sim
    }

    /** User called the toss. Returns true if the user won. */
    fun callToss(heads: Boolean): Boolean {
        GameAudio.play("coin")
        val m = prepareSim() ?: return false
        val coin = rnd.nextBoolean()
        val won = coin == heads
        if (!won) {
            val other = m.other(userSideId() ?: m.home.id)
            m.doToss(other, m.autoTossDecision(other)); simTick++
        }
        return won
    }

    fun chooseToss(bat: Boolean) {
        val m = prepareSim() ?: return
        m.doToss(userSideId() ?: m.home.id, if (bat) "BAT" else "BOWL"); simTick++
    }

    fun watchLive() { prepareSim()?.let { if (!it.started) it.autoToss() }; autoPlay = false; nav(Screen.Live) }

    fun quickSimMatch() {
        val m = prepareSim() ?: return
        m.simToEnd(); simTick++
        applyMatch()
        nav(Screen.Live)
    }

    fun stepBall() { sim?.nextBall(); simTick++; if (sim?.finished == true) { autoPlay = false; applyMatch() } }
    fun stepOver() { sim?.nextOver(); simTick++; if (sim?.finished == true) { autoPlay = false; applyMatch() } }
    fun stepInnings() { sim?.endInnings(); simTick++; if (sim?.finished == true) { autoPlay = false; applyMatch() } }
    fun simRest() { sim?.simToEnd(); simTick++; autoPlay = false; applyMatch() }
    fun declare() { sim?.declare(); simTick++; if (sim?.finished == true) applyMatch() }
    fun setAggression(a: Int) { val m = sim ?: return; val id = userSideId() ?: return; m.club(id).aggression = a; simTick++; bump() }

    fun applyMatch() {
        if (matchApplied) return
        val s = save ?: return
        val m = sim ?: return
        if (!m.finished) return
        val c = pendingComp() ?: return; val f = pendingFixture() ?: return
        Career.finishFixture(s, c, f, m)
        matchApplied = true
        persist()
    }

    fun leaveMatch() {
        applyMatch()
        val wasTournament = pendingComp()?.let { it.id == save?.tournament?.id } == true
        pending = null; sim = null; autoPlay = false
        goHome()
        if (wasTournament) nav(Screen.TournamentsScr)
        val s = save
        if (s != null && !wasTournament && Career.leagueFinished(s) && s.phase != "OFFSEASON") { Career.endSeason(s, rnd); persist(); nav(Screen.SeasonSummary) }
    }

    /* ---------------- tournaments ---------------- */
    fun startTournament(type: String, userTeam: String, format: String?, teams: List<String>, matches: Int, double: Boolean, knockout: String) {
        val s = save ?: return
        Tournaments.create(s, type, userTeam, format, teams, matches, double, knockout)
        persist()
    }

    fun playNextTournament() {
        val s = save ?: return
        val t = s.tournament ?: return
        val fx = CompEngine.nextUnplayed(t) ?: run { msg("Tournament complete!"); return }
        if (fx.home == t.userTeam || fx.away == t.userTeam) {
            pending = PendingMatch(t.id, fx.id); sim = null; matchApplied = false; nav(Screen.PreMatch)
        } else { Career.quickSim(s, t, fx, rnd); persist() }
    }

    fun simTournamentUntilMine() {
        val s = save ?: return
        val t = s.tournament ?: return
        var g = 0
        while (g++ < 200) {
            val fx = CompEngine.nextUnplayed(t) ?: break
            if (fx.home == t.userTeam || fx.away == t.userTeam) break
            Career.quickSim(s, t, fx, rnd)
        }
        persist()
    }

    fun simWholeTournament() {
        val s = save ?: return
        val t = s.tournament ?: return
        var g = 0
        while (g++ < 300) { val fx = CompEngine.nextUnplayed(t) ?: break; Career.quickSim(s, t, fx, rnd) }
        persist()
    }

    fun closeTournament() { save?.tournament = null; persist() }

    /* ---------------- auction ---------------- */
    fun startMiniAuction() {
        val s = save ?: return
        if (s.phase != "PRESEASON") { msg("Auctions happen in pre-season."); return }
        if (s.auctionDoneThisSeason) { msg("This season's auction is complete."); return }
        if (s.auction == null) s.auction = Auction.start(s, "MINI")
        persist(); nav(Screen.AuctionScr)
    }
    fun confirmRetention(ids: List<String>) { val s = save ?: return; val a = s.auction ?: return; Auction.confirmRetention(s, a, ids); persist() }
    fun auctionBid() { val s = save ?: return; val a = s.auction ?: return; msg(Auction.userBid(s, a)); bump() }
    fun auctionTick() { val s = save ?: return; val a = s.auction ?: return; val n = a.sold.size; if (Auction.tick(s, a, rnd)) { if (a.sold.size > n) GameAudio.play("hammer"); persist() } else bump() }
    fun auctionPass() { val s = save ?: return; val a = s.auction ?: return; val n = a.sold.size; Auction.resolveLot(s, a, rnd); if (a.sold.size > n) GameAudio.play("hammer"); persist() }
    fun auctionAuto() { val s = save ?: return; val a = s.auction ?: return; Auction.autoComplete(s, a, rnd); persist() }

    /* ---------------- generic actions ---------------- */
    fun act(f: (CareerSave) -> String?) { val s = save ?: return; msg(f(s)); persist() }
}
