package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mapping behind the Overview status line.
 *
 * The third state is the one worth pinning. A status line wants a single word, and both of the wrong
 * ways to give it one have already cost this app a bug: calling "could not look" a no is what told a
 * rooted phone it was unrooted, and calling a refusal to answer a yes is how a start button came to
 * offer what was already running. So the two readings are kept as they are and the ambiguity is
 * reported as itself.
 */
class ReadinessTest {

    @Test
    fun `a reading that answers yes is loaded`() {
        assertEquals(KernelSuStatus.Active, kernelSuStatus(active = true, moduleLoaded = null))
    }

    @Test
    fun `the module list is evidence even when no shell of ours can run`() {
        // The case this hardware produces: KernelSU is loaded, nothing has granted this app a root
        // shell, so the only source that can speak is the kernel's own list.
        assertEquals(KernelSuStatus.Active, kernelSuStatus(active = false, moduleLoaded = true))
    }

    @Test
    fun `a list that was read and had no kernelsu is a no`() {
        assertEquals(KernelSuStatus.NotLoaded, kernelSuStatus(active = false, moduleLoaded = false))
    }

    @Test
    fun `nothing able to answer is neither yes nor no`() {
        assertEquals(
            KernelSuStatus.Unreadable,
            kernelSuStatus(active = false, moduleLoaded = null),
        )
    }

    @Test
    fun `a live root shell outranks a list that says otherwise`() {
        // Two sources disagree, which means one of them is being filtered rather than that the device
        // is in two states. A shell that ran `uid=0` is the stronger evidence, so the precedence is
        // stated here rather than left to the order of the branches.
        assertEquals(KernelSuStatus.Active, kernelSuStatus(active = true, moduleLoaded = false))
    }
}
