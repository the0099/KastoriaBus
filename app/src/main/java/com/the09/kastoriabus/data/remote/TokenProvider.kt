package com.the09.kastoriabus.data.remote

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Gives out the bearer token for rest.citybus.gr.
 *
 * There is no login API: the token is written inside the HTML of the stops page.
 * We download that page, cut the token out, and keep it (in memory and in
 * SharedPreferences, so it survives app restarts) until shortly before it expires.
 *
 * These functions block, so call them only from a background thread. OkHttp
 * already runs interceptors on background threads, which is where we use them.
 * They only throw IOException, which OkHttp turns into a normal network error.
 */
class TokenProvider(
    context: Context,
    private val pageClient: OkHttpClient, // a client WITHOUT the auth interceptor
) {
    private val prefs = context.getSharedPreferences("citybus_token", Context.MODE_PRIVATE)
    private val lock = Any()

    private var token: String? = prefs.getString(KEY_TOKEN, null)
    private var expiresAt: Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    /** Returns a valid token, downloading a new one if needed. */
    fun getToken(): String = synchronized(lock) {
        val current = token
        if (current != null && System.currentTimeMillis() < expiresAt - SAFETY_MARGIN_MS) {
            current
        } else {
            downloadNewToken()
        }
    }

    /**
     * Called after the server rejected [rejectedToken] with 401.
     * If another request already got a new token meanwhile, we reuse it
     * instead of downloading the page again.
     */
    fun refreshToken(rejectedToken: String?): String = synchronized(lock) {
        val current = token
        if (current != null && current != rejectedToken) current else downloadNewToken()
    }

    private fun downloadNewToken(): String {
        val request = Request.Builder().url(STOPS_PAGE_URL).build()
        val html = pageClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Stops page answered HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Stops page was empty")
        }
        val newToken = TokenParser.extractToken(html)
            ?: throw IOException("No token found in the stops page")
        // If we can't read "exp", assume the shortest life we have seen minus a bit.
        val newExpiresAt = TokenParser.expiryMillis(newToken)
            ?: (System.currentTimeMillis() + FALLBACK_LIFETIME_MS)

        token = newToken
        expiresAt = newExpiresAt
        prefs.edit()
            .putString(KEY_TOKEN, newToken)
            .putLong(KEY_EXPIRES_AT, newExpiresAt)
            .apply()
        return newToken
    }

    private companion object {
        const val STOPS_PAGE_URL = "https://kastoria.citybus.gr/el/stops"
        const val KEY_TOKEN = "token"
        const val KEY_EXPIRES_AT = "expires_at"
        // Refresh 30 minutes early: the page may be cached, so the token can be a bit old.
        const val SAFETY_MARGIN_MS = 30L * 60 * 1000
        const val FALLBACK_LIFETIME_MS = 12L * 60 * 60 * 1000
    }
}
