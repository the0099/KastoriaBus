package com.the09.kastoriabus.data

import com.the09.kastoriabus.data.remote.TokenParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class TokenParserTest {

    // Builds a fake JWT with the given payload (not a real token).
    private fun fakeJwt(payloadJson: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = enc.encodeToString(payloadJson.toByteArray())
        return "$header.$payload.fakesignature"
    }

    @Test
    fun extractToken_findsTokenInPageHtml() {
        val jwt = fakeJwt("""{"exp":1792054847}""")
        val html = """
            <script>
                const agencyCode = 115;
                const apiUrl = 'https://rest.citybus.gr';
                const token = '$jwt';
                const language = 'el';
            </script>
        """.trimIndent()
        assertEquals(jwt, TokenParser.extractToken(html))
    }

    @Test
    fun extractToken_returnsNullWhenMissing() {
        assertNull(TokenParser.extractToken("<html>no token here</html>"))
    }

    @Test
    fun expiryMillis_readsExpInMilliseconds() {
        assertEquals(1792054847000L, TokenParser.expiryMillis(fakeJwt("""{"exp":1792054847}""")))
    }

    @Test
    fun expiryMillis_returnsNullForGarbage() {
        assertNull(TokenParser.expiryMillis("not-a-jwt"))
        assertNull(TokenParser.expiryMillis(fakeJwt("""{"foo":1}""")))
    }
}
