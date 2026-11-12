package com.cricketcoach.manager.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Supabase project used by Cricket Manager Pro. The publishable key is PUBLIC by design: every table is protected by
 * Row Level Security, so a signed-in user can only ever read/write their own profile, saves and avatar.
 * The service-role / secret key is never shipped in the app.
 */
object SupaConfig {
    const val URL = "https://sxrnoiqijlljubqraldr.supabase.co"
    const val KEY = "sb_publishable_yuYNCRjxWixcQYTCyihmKg_Ga6kk-tT"
    const val REDIRECT = "cricketmanagerpro://auth-callback"
}

data class Session(val access: String, val refresh: String, val expiresAt: Long, val uid: String, val email: String)

data class Profile(
    val id: String = "", val username: String = "", val displayName: String = "", val avatarPath: String? = null,
    val coins: Int = 1000, val xp: Int = 0, val settings: JsonObject? = null
)

data class CloudSave(val slot: Int, val mode: String, val manager: String, val club: String, val season: Int, val updatedAt: String)

data class OnlinePost(
    val id: Long, val userId: String, val author: String, val club: String, val kind: String, val body: String,
    val likes: Int, val comments: Int, val createdAt: String
)

data class OnlineComment(val id: Long, val author: String, val body: String, val createdAt: String)

/** HTTP result. */
class Res(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
    fun error(): String {
        if (code == -1) return "No internet connection"
        return try {
            val o = JsonParser.parseString(body).asJsonObject
            val m = listOf("msg", "error_description", "message", "error").firstNotNullOfOrNull { k -> o[k]?.takeIf { it.isJsonPrimitive }?.asString }
            friendly(m ?: "HTTP $code")
        } catch (_: Exception) { friendly(body.take(140).ifBlank { "HTTP $code" }) }
    }

    private fun friendly(m: String): String = when {
        m.contains("Invalid login credentials", true) -> "Wrong email or password"
        m.contains("already registered", true) || m.contains("already been registered", true) -> "An account with this email already exists"
        m.contains("rate limit", true) -> "Too many attempts - please wait a minute and try again"
        m.contains("Password should be", true) -> "Password must be at least 6 characters"
        m.contains("email_address_invalid", true) || m.contains("is invalid", true) -> "Please enter a valid email address"
        m.contains("JWT expired", true) -> "Session expired - please log in again"
        else -> m
    }
}

/** Encrypts the auth tokens with an AES-256-GCM key that lives in the Android Keystore (never leaves secure hardware). */
class SecureStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("cmp_auth", Context.MODE_PRIVATE)
    private val alias = "cmp_auth_key"
    private var memory: String? = null

    private fun key(): SecretKey? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            generateKey()
        }
    } catch (_: Throwable) { null }

    fun put(value: String?) {
        if (value == null) { memory = null; sp.edit().remove("blob").apply(); return }
        memory = value
        val k = key() ?: return // no keystore (e.g. JVM tests): keep the session in memory only, never in plain text
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, k)
            val enc = c.doFinal(value.toByteArray())
            sp.edit().putString("blob", Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)).apply()
        } catch (_: Throwable) {}
    }

    fun get(): String? {
        memory?.let { return it }
        val blob = sp.getString("blob", null) ?: return null
        val k = key() ?: return null
        return try {
            val (iv, enc) = blob.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv))
            String(c.doFinal(enc)).also { memory = it }
        } catch (_: Throwable) { null }
    }
}

/**
 * Account system over the Supabase REST APIs (GoTrue auth, PostgREST, Storage). No SDK needed.
 * All calls are blocking: run them on Dispatchers.IO.
 */
object Auth {
    private val gson = Gson()
    private var store: SecureStore? = null
    @Volatile var session: Session? = null; private set
    var http: (String, String, String?, Map<String, String>) -> Res = ::realHttp

    fun init(ctx: Context) {
        if (store != null) return
        store = SecureStore(ctx.applicationContext)
        session = store?.get()?.let { runCatching { gson.fromJson(it, Session::class.java) }.getOrNull() }
    }

    val signedIn: Boolean get() = session != null
    val uid: String? get() = session?.uid

    private fun realHttp(method: String, url: String, body: String?, headers: Map<String, String>): Res = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 15000; c.readTimeout = 45000
        c.setRequestProperty("apikey", SupaConfig.KEY)
        for ((k, v) in headers) c.setRequestProperty(k, v)
        if (body != null) {
            c.doOutput = true
            if (headers["Content-Type"] == null) c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        Res(code, text)
    } catch (e: Exception) { Res(-1, e.message ?: "network error") }

    private fun bytesHttp(method: String, url: String, bytes: ByteArray?, headers: Map<String, String>): Pair<Int, ByteArray> = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 15000; c.readTimeout = 45000
        c.setRequestProperty("apikey", SupaConfig.KEY)
        for ((k, v) in headers) c.setRequestProperty(k, v)
        if (bytes != null) { c.doOutput = true; c.outputStream.use { it.write(bytes) } }
        val code = c.responseCode
        code to ((if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0))
    } catch (e: Exception) { -1 to ByteArray(0) }

    private fun setSession(json: String): String? {
        val o = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return "Unexpected server response"
        val at = o["access_token"]?.asString ?: return "Account created - please log in"
        val user = o["user"]?.asJsonObject
        val s = Session(at, o["refresh_token"]?.asString ?: "", System.currentTimeMillis() / 1000 + (o["expires_in"]?.asLong ?: 3600),
            user?.get("id")?.asString ?: "", user?.get("email")?.asString ?: "")
        session = s; store?.put(gson.toJson(s))
        return null
    }

    fun validUsername(u: String) = Regex("^[A-Za-z0-9_]{3,20}$").matches(u)
    fun validEmail(e: String) = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$").matches(e.trim())

    /** Returns null on success, or a user-facing error message. */
    fun signUp(email: String, password: String, username: String, displayName: String = ""): String? {
        if (!validEmail(email)) return "Please enter a valid email address"
        if (!validUsername(username)) return "Username: 3-20 letters, numbers or _"
        if (password.length < 6) return "Password must be at least 6 characters"
        if (usernameAvailable(username) == false) return "Username '$username' is already taken"
        val body = gson.toJson(mapOf("email" to email.trim(), "password" to password, "data" to mapOf("username" to username, "display_name" to displayName.ifBlank { username })))
        val r = http("POST", "${SupaConfig.URL}/auth/v1/signup", body, emptyMap())
        return if (r.ok) setSession(r.body) else r.error()
    }

    fun signIn(email: String, password: String): String? {
        if (!validEmail(email)) return "Please enter a valid email address"
        val r = http("POST", "${SupaConfig.URL}/auth/v1/token?grant_type=password", gson.toJson(mapOf("email" to email.trim(), "password" to password)), emptyMap())
        return if (r.ok) setSession(r.body) else r.error()
    }

    /** Refreshes the access token if it expires within a minute. Returns a usable token or null (signed out / offline). */
    @Synchronized
    fun token(): String? {
        val s = session ?: return null
        if (s.expiresAt - System.currentTimeMillis() / 1000 > 60) return s.access
        val r = http("POST", "${SupaConfig.URL}/auth/v1/token?grant_type=refresh_token", gson.toJson(mapOf("refresh_token" to s.refresh)), emptyMap())
        if (r.ok) { setSession(r.body); return session?.access }
        if (r.code in 400..499) { signOutLocal(); return null } // refresh token revoked / expired
        return null // offline: keep the session, try again later
    }

    private fun auth(): Map<String, String>? = token()?.let { mapOf("Authorization" to "Bearer $it") }

    fun recover(email: String): String? {
        if (!validEmail(email)) return "Please enter a valid email address"
        val r = http("POST", "${SupaConfig.URL}/auth/v1/recover?redirect_to=${enc(SupaConfig.REDIRECT)}", gson.toJson(mapOf("email" to email.trim())), emptyMap())
        return if (r.ok) null else r.error()
    }

    /** Parses cricketmanagerpro://auth-callback#access_token=..&refresh_token=..&type=recovery (or ?error_description=..). */
    fun parseCallback(uri: String): Map<String, String> {
        val part = uri.substringAfter('#', "").ifEmpty { uri.substringAfter('?', "") }
        return part.split('&').filter { '=' in it }.associate { kv ->
            val (k, v) = kv.split('=', limit = 2); k to java.net.URLDecoder.decode(v, "UTF-8")
        }
    }

    /** Signs in with the tokens from an email link (recovery). Returns null on success. */
    fun acceptCallback(params: Map<String, String>): String? {
        params["error_description"]?.let { return it }
        val at = params["access_token"] ?: return "Invalid or expired link"
        val r = http("GET", "${SupaConfig.URL}/auth/v1/user", null, mapOf("Authorization" to "Bearer $at"))
        if (!r.ok) return r.error()
        val u = JsonParser.parseString(r.body).asJsonObject
        val s = Session(at, params["refresh_token"] ?: "", System.currentTimeMillis() / 1000 + (params["expires_in"]?.toLongOrNull() ?: 3600),
            u["id"].asString, u["email"]?.asString ?: "")
        session = s; store?.put(gson.toJson(s))
        return null
    }

    fun updatePassword(newPassword: String): String? {
        if (newPassword.length < 6) return "Password must be at least 6 characters"
        val h = auth() ?: return "Please log in again"
        val r = http("PUT", "${SupaConfig.URL}/auth/v1/user", gson.toJson(mapOf("password" to newPassword)), h)
        return if (r.ok) null else r.error()
    }

    fun signOut() {
        session?.let { s -> http("POST", "${SupaConfig.URL}/auth/v1/logout", "{}", mapOf("Authorization" to "Bearer ${s.access}")) }
        signOutLocal()
    }

    fun signOutLocal() { session = null; store?.put(null) }

    /* ---------------- profile ---------------- */
    fun usernameAvailable(name: String): Boolean? {
        val r = http("POST", "${SupaConfig.URL}/rest/v1/rpc/username_available", gson.toJson(mapOf("name" to name)), emptyMap())
        return if (r.ok) r.body.trim() == "true" else null
    }

    fun profile(): Profile? {
        val h = auth() ?: return null
        val r = http("GET", "${SupaConfig.URL}/rest/v1/profiles?select=*&id=eq.${uid}", null, h)
        if (!r.ok) return null
        val a = JsonParser.parseString(r.body).asJsonArray
        if (a.size() == 0) return null
        val o = a[0].asJsonObject
        return Profile(o.str("id"), o.str("username"), o.str("display_name"), o.strOrNull("avatar_path"),
            o["coins"]?.takeIf { !it.isJsonNull }?.asInt ?: 0, o["xp"]?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            o["settings"]?.takeIf { it.isJsonObject }?.asJsonObject)
    }

    fun updateProfile(fields: Map<String, Any?>): String? {
        val h = auth() ?: return "Not signed in"
        val r = http("PATCH", "${SupaConfig.URL}/rest/v1/profiles?id=eq.${uid}", gson.toJson(fields), h + ("Prefer" to "return=minimal"))
        return if (r.ok) null else if (r.body.contains("profiles_username") || r.body.contains("duplicate")) "Username already taken" else r.error()
    }

    fun uploadAvatar(bytes: ByteArray): String? {
        if (bytes.size > 1_000_000) return "Photo is too large (max 1 MB)"
        val h = auth() ?: return "Not signed in"
        val path = "$uid/avatar.jpg"
        val (code, resp) = bytesHttp("POST", "${SupaConfig.URL}/storage/v1/object/avatars/$path", bytes, h + mapOf("Content-Type" to "image/jpeg", "x-upsert" to "true"))
        if (code !in 200..299) return if (code == -1) "No internet connection" else Res(code, String(resp)).error()
        return updateProfile(mapOf("avatar_path" to path))
    }

    fun downloadAvatar(path: String): ByteArray? {
        val h = auth() ?: return null
        val (code, b) = bytesHttp("GET", "${SupaConfig.URL}/storage/v1/object/authenticated/avatars/$path", null, h)
        return if (code in 200..299 && b.isNotEmpty()) b else null
    }

    /** Permanently deletes the account: avatar (Storage API), then the auth user (cascade removes profile, saves, posts). */
    fun deleteAccount(): String? {
        val h = auth() ?: return "Not signed in"
        bytesHttp("DELETE", "${SupaConfig.URL}/storage/v1/object/avatars/$uid/avatar.jpg", null, h)
        val r = http("POST", "${SupaConfig.URL}/rest/v1/rpc/delete_my_account", "{}", h)
        if (!r.ok) return r.error()
        signOutLocal(); return null
    }

    /* ---------------- cloud saves (table career_saves, owner-only RLS) ---------------- */
    fun upsertSave(slot: Int, mode: String, manager: String, club: String, season: Int, data: String): String? {
        val h = auth() ?: return "Not signed in"
        val body = gson.toJson(mapOf("slot" to slot, "mode" to mode, "manager" to manager.take(40), "club" to club.take(60), "season" to season, "data" to data))
        val r = http("POST", "${SupaConfig.URL}/rest/v1/career_saves?on_conflict=user_id,slot", body, h + ("Prefer" to "resolution=merge-duplicates,return=minimal"))
        return if (r.ok) null else r.error()
    }

    fun listSaves(): List<CloudSave>? {
        val h = auth() ?: return null
        val r = http("GET", "${SupaConfig.URL}/rest/v1/career_saves?select=slot,mode,manager,club,season,updated_at&order=slot", null, h)
        if (!r.ok) return null
        return JsonParser.parseString(r.body).asJsonArray.map { it.asJsonObject }.map { o ->
            CloudSave(o["slot"].asInt, o.str("mode"), o.str("manager"), o.str("club"), o["season"]?.asInt ?: 1, o.str("updated_at"))
        }
    }

    fun getSave(slot: Int): String? {
        val h = auth() ?: return null
        val r = http("GET", "${SupaConfig.URL}/rest/v1/career_saves?select=data&slot=eq.$slot", null, h)
        if (!r.ok) return null
        val a = JsonParser.parseString(r.body).asJsonArray
        return if (a.size() == 0) null else a[0].asJsonObject["data"].asString
    }

    fun deleteSave(slot: Int): String? {
        val h = auth() ?: return "Not signed in"
        val r = http("DELETE", "${SupaConfig.URL}/rest/v1/career_saves?slot=eq.$slot", null, h)
        return if (r.ok) null else r.error()
    }

    /* ---------------- online social feed ---------------- */
    fun feed(limit: Int = 40, onlyUser: String? = null): List<OnlinePost>? {
        val h = auth() ?: return null
        val f = onlyUser?.let { "&user_id=eq.$it" } ?: ""
        val r = http("GET", "${SupaConfig.URL}/rest/v1/social_posts?select=*&order=created_at.desc&limit=$limit$f", null, h)
        if (!r.ok) return null
        return JsonParser.parseString(r.body).asJsonArray.map { it.asJsonObject }.map { o ->
            OnlinePost(o["id"].asLong, o.str("user_id"), o.str("author_name"), o.str("club"), o.str("kind"), o.str("body"),
                o["likes_count"]?.asInt ?: 0, o["comments_count"]?.asInt ?: 0, o.str("created_at"))
        }
    }

    fun publish(club: String, kind: String, body: String): String? {
        val h = auth() ?: return "Log in to share online"
        val k = kind.uppercase().filter { it in 'A'..'Z' || it == '_' }.take(20).ifEmpty { "POST" }.let { if (it.length < 2) "POST" else it }
        val r = http("POST", "${SupaConfig.URL}/rest/v1/social_posts", gson.toJson(mapOf("club" to club.take(40), "kind" to k, "body" to body.take(400))), h + ("Prefer" to "return=minimal"))
        return if (r.ok) null else r.error()
    }

    fun deletePost(id: Long): String? {
        val h = auth() ?: return "Not signed in"
        val r = http("DELETE", "${SupaConfig.URL}/rest/v1/social_posts?id=eq.$id", null, h)
        return if (r.ok) null else r.error()
    }

    fun myLikes(ids: List<Long>): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        val h = auth() ?: return emptySet()
        val r = http("GET", "${SupaConfig.URL}/rest/v1/social_likes?select=post_id&post_id=in.(${ids.joinToString(",")})", null, h)
        if (!r.ok) return emptySet()
        return JsonParser.parseString(r.body).asJsonArray.map { it.asJsonObject["post_id"].asLong }.toSet()
    }

    fun setLike(id: Long, like: Boolean): String? {
        val h = auth() ?: return "Not signed in"
        val r = if (like) http("POST", "${SupaConfig.URL}/rest/v1/social_likes", gson.toJson(mapOf("post_id" to id)), h + ("Prefer" to "return=minimal"))
        else http("DELETE", "${SupaConfig.URL}/rest/v1/social_likes?post_id=eq.$id&user_id=eq.$uid", null, h)
        return if (r.ok || r.code == 409) null else r.error()
    }

    fun comments(postId: Long): List<OnlineComment>? {
        val h = auth() ?: return null
        val r = http("GET", "${SupaConfig.URL}/rest/v1/social_comments?select=id,author_name,body,created_at&post_id=eq.$postId&order=created_at.asc&limit=100", null, h)
        if (!r.ok) return null
        return JsonParser.parseString(r.body).asJsonArray.map { it.asJsonObject }.map { o -> OnlineComment(o["id"].asLong, o.str("author_name"), o.str("body"), o.str("created_at")) }
    }

    fun comment(postId: Long, body: String): String? {
        val h = auth() ?: return "Not signed in"
        val r = http("POST", "${SupaConfig.URL}/rest/v1/social_comments", gson.toJson(mapOf("post_id" to postId, "body" to body.take(200))), h + ("Prefer" to "return=minimal"))
        return if (r.ok) null else r.error()
    }

    fun following(): Set<String> {
        val h = auth() ?: return emptySet()
        val r = http("GET", "${SupaConfig.URL}/rest/v1/social_follows?select=followee&follower=eq.$uid", null, h)
        if (!r.ok) return emptySet()
        return JsonParser.parseString(r.body).asJsonArray.map { it.asJsonObject["followee"].asString }.toSet()
    }

    fun setFollow(other: String, follow: Boolean): String? {
        val h = auth() ?: return "Not signed in"
        if (other == uid) return "You can't follow yourself"
        val r = if (follow) http("POST", "${SupaConfig.URL}/rest/v1/social_follows", gson.toJson(mapOf("followee" to other)), h + ("Prefer" to "return=minimal"))
        else http("DELETE", "${SupaConfig.URL}/rest/v1/social_follows?follower=eq.$uid&followee=eq.$other", null, h)
        return if (r.ok || r.code == 409) null else r.error()
    }

    fun followerCount(user: String): Int? {
        val h = auth() ?: return null
        val r = http("POST", "${SupaConfig.URL}/rest/v1/rpc/follower_count", gson.toJson(mapOf("uid" to user)), h)
        return if (r.ok) r.body.trim().toIntOrNull() else null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun JsonObject.str(k: String) = this[k]?.takeIf { !it.isJsonNull }?.asString ?: ""
    private fun JsonObject.strOrNull(k: String) = this[k]?.takeIf { !it.isJsonNull }?.asString
    @Suppress("unused") private fun JsonArray.objs() = map { it.asJsonObject }
}
