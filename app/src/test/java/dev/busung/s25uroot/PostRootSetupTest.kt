package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grant that a rooted run makes on the app's behalf.
 *
 * It is a shell command built from a package name, so the two things worth pinning are that a
 * permission already held is not asked for again, and that the name is quoted rather than pasted
 * into the command.
 */
class PostRootSetupTest {

    @Test
    fun `a granted permission is not asked for again`() {
        assertNull(PostRootSetup.grantCommand("dev.busung.s25uroot", alreadyGranted = true))
    }

    @Test
    fun `the grant names this app and the secure-settings permission`() {
        assertEquals(
            "pm grant 'dev.busung.s25uroot' android.permission.WRITE_SECURE_SETTINGS",
            PostRootSetup.grantCommand("dev.busung.s25uroot", alreadyGranted = false),
        )
    }

    @Test
    fun `a package name with a quote in it stays one argument`() {
        val command = PostRootSetup.grantCommand("app'; id; echo '", alreadyGranted = false)
            ?: error("a name that is not already granted always produces a command")

        assertTrue(
            "the quote has to be escaped rather than closing the argument: $command",
            command.contains("'app'\\''; id; echo '\\'''"),
        )
        // Every quote in the command therefore comes in the pairs-and-a-break shape of one escaped
        // argument, and the trailing one closes it.
        assertTrue("the argument is never left open: $command", command.endsWith("WRITE_SECURE_SETTINGS"))
    }
}
