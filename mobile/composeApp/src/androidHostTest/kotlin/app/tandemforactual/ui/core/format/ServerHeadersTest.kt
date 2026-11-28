package app.tandemforactual.ui.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerHeadersTest {
    @Test
    fun headersAreReadOnePerLine() {
        val text = "CF-Access-Client-Id: abc.access\n\n  X-Token :  s3cr:et  \n"
        assertEquals(mapOf("CF-Access-Client-Id" to "abc.access", "X-Token" to "s3cr:et"), parseServerHeaders(text).getOrThrow())
        assertEquals(emptyMap(), parseServerHeaders("  \n").getOrThrow())
    }

    @Test
    fun aLineWithoutAValidNameIsRejected() {
        val problem = parseServerHeaders("Good: 1\nno colon here").exceptionOrNull()?.message.orEmpty()
        assertTrue("no colon here" in problem, problem)
        assertTrue(parseServerHeaders("Bad Name: x").isFailure)
    }

    @Test
    fun savedHeadersAreWrittenBackSoTheyReadTheSame() {
        val headers = mapOf("CF-Access-Client-Id" to "abc", "X-Token" to "s3cr:et")
        assertEquals("CF-Access-Client-Id: abc\nX-Token: s3cr:et", formatServerHeaders(headers))
        assertEquals(headers, parseServerHeaders(formatServerHeaders(headers)).getOrThrow())
        assertEquals("", formatServerHeaders(emptyMap()))
    }
}
