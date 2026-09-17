package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The claim that decides which writer still owns the install screen.
 *
 * The case it exists for is not hypothetical: a lookup that starts when the screen opens fetches the
 * support catalog without suspending, so cancelling it does nothing, and its write lands after the run
 * it was racing has already reported the exploit. These tests pin the rule that makes that write a
 * no-op rather than a screen that was live and wrong at once.
 */
class PublishClaimTest {

    @Test
    fun `a later claim takes the screen from an earlier one`() {
        val claim = PublishClaim()
        val lookup = claim.claim()
        val run = claim.claim()

        assertFalse("the lookup must not publish over the run", claim.holds(lookup))
        assertTrue("the run owns the screen", claim.holds(run))
    }

    @Test
    fun `the holder of the newest claim keeps it until something newer claims`() {
        val claim = PublishClaim()
        val run = claim.claim()

        assertTrue(claim.holds(run))
        assertTrue(claim.holds(run))
    }

    /** Two claims never share a token, so a stale one cannot pass by looking current again. */
    @Test
    fun `every claim is a token of its own`() {
        val claim = PublishClaim()
        val tokens = (1..5).map { claim.claim() }

        assertEquals(5, tokens.toSet().size)
    }

    /** A fresh claim holds, which is what lets the very first lookup publish at all. */
    @Test
    fun `the first claim holds until a run starts`() {
        val claim = PublishClaim()
        val first = claim.claim()

        assertTrue(claim.holds(first))
    }
}
