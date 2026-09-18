package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The launcher's restart shortcut, and the three files that have to agree for it to work.
 *
 * The action is a string written twice - once in the shortcut XML the launcher reads, once in the constant the
 * activity compares against - and nothing in the build joins them. A mismatch is invisible to the compiler and
 * to every run of the app from a tap: the long press opens the app and does nothing else, which reads as a
 * shortcut that is broken rather than as a typo.
 *
 * The same for the shortcut's own attributes: the launcher requires a short label and an icon, and one that is
 * missing an attribute it requires is not an error anywhere - the entry simply does not appear.
 */
class RestartShortcutTest {

    @Test
    fun `the shortcut sends the action the app handles`() {
        val intent = attributesOf(shortcutXml(), "intent")

        assertEquals(
            "the launcher sends an action nothing compares against, so the long press does nothing",
            ACTION_RESTART_OPTIONS,
            intent.attribute("android:action"),
        )
    }

    @Test
    fun `the shortcut opens the app's own window`() {
        val intent = attributesOf(shortcutXml(), "intent")
        val applicationId = "dev.busung.s25uroot"

        assertEquals(applicationId, intent.attribute("android:targetPackage"))
        assertEquals(
            "the shortcut targets something other than the launcher activity, so it opens a screen that " +
                "does not know about restart",
            "$applicationId.MainActivity",
            intent.attribute("android:targetClass"),
        )
    }

    @Test
    fun `the shortcut carries everything a launcher needs to show it`() {
        val shortcut = attributesOf(shortcutXml(), "shortcut")

        listOf("android:shortcutId", "android:icon", "android:shortcutShortLabel").forEach { attribute ->
            assertTrue(
                "the shortcut has no $attribute, and a launcher that cannot fill one in shows nothing at all",
                shortcut.attribute(attribute).isNotBlank(),
            )
        }
        // The long label is what a launcher with room shows, and it is the sheet's own title: the two saying
        // the same thing is the point of the shortcut, since what it opens is that sheet.
        assertEquals("@string/reboot_sheet_title", shortcut.attribute("android:shortcutLongLabel"))
    }

    @Test
    fun `the manifest hands the shortcut file to the launcher activity`() {
        val manifest = source("main/AndroidManifest.xml")
        val activity = manifest.substringAfter("android:name=\".MainActivity\"").substringBefore("</activity>")

        assertTrue(
            "the shortcut file is not declared, so the launcher never reads it",
            activity.contains("android:name=\"android.app.shortcuts\"") &&
                activity.contains("android:resource=\"@xml/shortcuts\""),
        )
    }

    private fun shortcutXml(): String = source("main/res/xml/shortcuts.xml")

    /**
     * The attributes of the first [tag] element.
     *
     * Matched on a name followed by a space rather than by a plain search for the name: `<shortcut` is a prefix
     * of the `<shortcuts>` root, and the difference is reading the root's namespace instead of the shortcut.
     */
    private fun attributesOf(xml: String, tag: String): String =
        requireNotNull(Regex("<$tag\\s([^>]*)>").find(xml)) { "no <$tag> element in the shortcut file" }
            .groupValues[1]

    /** The value of one attribute in a fragment of XML, or "" when the fragment does not carry it. */
    private fun String.attribute(name: String): String =
        substringAfter("$name=\"", "").substringBefore("\"")

    private fun source(relative: String): String {
        val file = candidateRoots().map { File(it, relative) }.firstOrNull(File::isFile)
        requireNotNull(file) { "$relative was not found; the scan is looking at the wrong directory" }
        return file.readText()
    }

    private fun candidateRoots(): List<String> = listOf("app/src", "src").filter { File(it).isDirectory }
}
