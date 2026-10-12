package com.the09.kastoriabus.data.remote

import com.google.gson.JsonParser
import java.util.Base64

/**
 * Pure helper functions for the bearer token. They don't touch the network,
 * which is why they can be tested with plain unit tests.
 */
object TokenParser {

    // The stops page contains a line like:  const token = 'eyJhbGciOi...';
    private val TOKEN_REGEX = Regex("""const\s+token\s*=\s*'([^']+)'""")

    /** Finds the token inside the HTML of /el/stops, or returns null. */
    fun extractToken(html: String): String? =
        TOKEN_REGEX.find(html)?.groupValues?.get(1)

    /**
     * Reads the "exp" field of a JWT and returns it in milliseconds, or null.
     * A JWT is three Base64 parts joined by dots: header.payload.signature.
     * The payload is JSON like {"exp":1792054847} (seconds since 1970).
     */
    fun expiryMillis(jwt: String): Long? = try {
        val payload = jwt.split('.')[1]
        val json = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        JsonParser.parseString(json).asJsonObject.get("exp")?.asLong?.times(1000)
    } catch (e: Exception) {
        null
    }
}
