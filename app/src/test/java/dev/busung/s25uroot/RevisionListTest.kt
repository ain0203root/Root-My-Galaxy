package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HEAD = "6e3223e689688540060ddd97a0927e927bfed207"
private const val PREVIOUS = "6dc16c2f11f0a6f1a3c1c6a2f2b3f0c9a1b2c3d4"

class RevisionListTest {

    @Test
    fun `a commit is listed with its first line, its date and its sha`() {
        val json = """
            [
              {
                "sha": "$HEAD",
                "commit": {
                  "message": "Merge pull request #285\n\nbody that must not be a label",
                  "committer": {"date": "2026-09-03T03:23:27Z"}
                }
              }
            ]
        """.trimIndent()

        val revisions = parseCommits(json)

        assertEquals(1, revisions.size)
        assertEquals(HEAD, revisions[0].commit)
        assertEquals("Merge pull request #285", revisions[0].label)
        assertEquals("2026-09-03", revisions[0].date)
        assertEquals(null, revisions[0].tag)
    }

    @Test
    fun `order is kept, because the first commit is the ref head`() {
        val json = listOf(HEAD, PREVIOUS).joinToString(",", "[", "]") { sha ->
            """{"sha":"$sha","commit":{"message":"m","committer":{"date":"2026-09-03T00:00:00Z"}}}"""
        }

        assertEquals(listOf(HEAD, PREVIOUS), parseCommits(json).map { it.commit })
    }

    @Test
    fun `a revision that is not a full commit is dropped`() {
        // Listing one would offer a pin that cannot be resolved again.
        val json = """
            [
              {"sha":"6e3223e","commit":{"message":"abbrev","committer":{"date":"2026-09-03T00:00:00Z"}}},
              {"sha":"$HEAD","commit":{"message":"real","committer":{"date":"2026-09-03T00:00:00Z"}}}
            ]
        """.trimIndent()

        assertEquals(listOf(HEAD), parseCommits(json).map { it.commit })
    }

    @Test
    fun `an error page is an empty list rather than a crash`() {
        assertTrue(parseCommits("Not Found").isEmpty())
        assertTrue(parseCommits("""{"message":"API rate limit exceeded"}""").isEmpty())
        assertTrue(parseCommits("").isEmpty())
    }

    @Test
    fun `the list is capped at what was asked for`() {
        val json = (1..30).joinToString(",", "[", "]") { index ->
            val sha = index.toString(16).padStart(40, '0')
            """{"sha":"$sha","commit":{"message":"m$index","committer":{"date":"2026-09-03T00:00:00Z"}}}"""
        }

        assertEquals(15, parseCommits(json, limit = 15).size)
    }

    @Test
    fun `a tag carries the commit it points at, which is what gets pinned`() {
        val json = """[{"name":"v1.2.3","commit":{"sha":"$HEAD"}}]"""

        val tags = parseTags(json)

        assertEquals(1, tags.size)
        assertEquals("v1.2.3", tags[0].tag)
        assertEquals("v1.2.3", tags[0].label)
        assertEquals(HEAD, tags[0].commit)
    }

    @Test
    fun `an empty tag list is normal, not a failure`() {
        assertTrue(parseTags("[]").isEmpty())
        assertTrue(parseTags("Not Found").isEmpty())
    }

    @Test
    fun `a label stops being a label before it becomes a paragraph`() {
        val message = "x".repeat(400)
        val json = """[{"sha":"$HEAD","commit":{"message":"$message","committer":{"date":"2026-09-03T00:00:00Z"}}}]"""

        assertEquals(96, parseCommits(json)[0].label.length)
    }
}
