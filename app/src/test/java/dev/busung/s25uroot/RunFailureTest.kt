package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunFailureTest {

    @Test
    fun evidenceKeepsTheEndOfTheLog() {
        val log = """
            [*] Running kernel exploit
            [+] slide-kaslr-ok
            [+] supervisor attempt 3
            [-] bootstrap did not obtain root
        """.trimIndent()

        assertEquals(
            listOf(
                "[*] Running kernel exploit",
                "[+] slide-kaslr-ok",
                "[+] supervisor attempt 3",
                "[-] bootstrap did not obtain root",
            ),
            failureEvidence(log),
        )
    }

    @Test
    fun evidenceDropsBlankLinesAndKeepsTheLastFewLines() {
        val log = "one\n\n   \ntwo\nthree\nfour\nfive\n"

        // Four lines is the default the failure report shows; a caller can ask for fewer.
        assertEquals(listOf("two", "three", "four", "five"), failureEvidence(log))
        assertEquals(listOf("four", "five"), failureEvidence(log, maxLines = 2))
    }

    @Test
    fun evidenceClipsLongPayloadLines() {
        val long = "x".repeat(400)

        val evidence = failureEvidence(long, maxLines = 1, maxLength = 20)

        assertEquals(1, evidence.size)
        assertEquals(20, evidence[0].length)
        assertTrue(evidence[0].endsWith("\u2026"))
    }

    @Test
    fun evidenceIsEmptyForARunThatProducedNothing() {
        assertEquals(emptyList<String>(), failureEvidence(""))
    }
}
