package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding a manager whose package name is not the published one.
 *
 * KernelSU-Next's spoofed manager build rewrites `com.rifsxd.ksunext` to three random words, freshly
 * generated on every release, so the app can never hold that name. What it can hold is the label,
 * which the spoof does not touch, and this is the decision that turns the two into a flavour.
 *
 * The unknown case matters as much as the others: a package that carries a KernelSU daemon but says
 * nothing about which project it is belongs to neither row rather than being guessed into one.
 */
class KernelSuManagerTest {

    @Test
    fun `a published package name decides on its own`() {
        val ksu = identifyManager("me.weishu.kernelsu", "KernelSU")
        assertEquals(KernelSuFlavor.KernelSu, ksu.flavor)
        assertFalse(ksu.spoofed)

        val next = identifyManager("com.rifsxd.ksunext", "KernelSU-Next")
        assertEquals(KernelSuFlavor.KernelSuNext, next.flavor)
        assertFalse(next.spoofed)
    }

    @Test
    fun `a published package name is matched without case`() {
        assertFalse(identifyManager("  COM.RIFSXD.KSUNEXT  ", "whatever").spoofed)
    }

    @Test
    fun `a rewritten package is attributed by its label`() {
        // What the spoofed build actually looks like: three random words, label untouched.
        val spoofed = identifyManager("qwerty.asdfgh.zxcvbn", "KernelSU-Next")
        assertEquals(KernelSuFlavor.KernelSuNext, spoofed.flavor)
        assertTrue(spoofed.spoofed)

        val spoofedKsu = identifyManager("qwerty.asdfgh.zxcvbn", "KernelSU")
        assertEquals(KernelSuFlavor.KernelSu, spoofedKsu.flavor)
        assertTrue(spoofedKsu.spoofed)
    }

    @Test
    fun `a label is read with its separators normalised`() {
        assertEquals(
            KernelSuFlavor.KernelSuNext,
            identifyManager("x.y.z", "KernelSU_Next").flavor,
        )
        assertEquals(
            KernelSuFlavor.KernelSu,
            identifyManager("x.y.z", "Kernel SU").flavor,
        )
        assertEquals(
            KernelSuFlavor.KernelSuNext,
            identifyManager("x.y.z", "kernelsu next manager").flavor,
        )
    }

    @Test
    fun `a label that says nothing leaves the flavour unknown`() {
        // It still carries a daemon, so it is still a manager; it just does not belong to a row.
        val unknown = identifyManager("qwerty.asdfgh.zxcvbn", "Superuser")

        assertNull(unknown.flavor)
        assertTrue(unknown.spoofed)
    }

    @Test
    fun `a manager's own name is shown only when it is not the flavour's`() {
        fun manager(label: String) = InstalledManager(
            packageName = "x.y.z",
            label = label,
            versionName = "3.3.0",
            flavor = KernelSuFlavor.KernelSu,
            spoofed = false,
        )

        // The published build: its label is the name the row above the version already carries, so
        // showing it is the duplication the version used to be in a different word.
        assertFalse(
            "the published manager's own name is printed beside a row that already names it",
            managerNameWorthShowing(manager("KernelSU"), KernelSuFlavor.KernelSu),
        )
        assertFalse(
            "the same name in different case is the same name",
            managerNameWorthShowing(manager("kernelsu "), KernelSuFlavor.KernelSu),
        )

        // The spoofed build: three random words whose package name changes every release, so the
        // label is the only thing on the phone that says which manager it is.
        assertTrue(
            "a manager that does not carry the flavour's name is printed without it",
            managerNameWorthShowing(manager("sturdy lantern pelican"), KernelSuFlavor.KernelSu),
        )
        assertTrue(
            "the other flavour's name is not this flavour's name",
            managerNameWorthShowing(manager("KernelSU-Next"), KernelSuFlavor.KernelSu),
        )
    }

    @Test
    fun `the offered release is the flavour's own default until one is named`() {
        assertEquals(
            "KernelSU_Next_v3.3.0_33214-release.apk",
            KernelSuFlavor.KernelSuNext.defaultManagerRelease.assetName,
        )
        assertEquals(
            "https://github.com/KernelSU-Next/KernelSU-Next/releases/download/v3.3.0/" +
                "KernelSU_Next_v3.3.0_33214-release.apk",
            KernelSuFlavor.KernelSuNext.defaultManagerRelease.url,
        )
    }
}
