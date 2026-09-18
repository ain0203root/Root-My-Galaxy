package dev.busung.s25uroot

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The settings page, as the parts it is read by.
 *
 * The page is long enough that finding one switch in it meant scrolling past everything else, and most
 * visits are for one thing. So each part is a section that opens on its own and stays as it was left.
 *
 * A closed section is one row of the page's index - the card of eight rows at the top - and the index is
 * read as much as it is used, so a row says what it holds rather than only what it is called: [icon] is the
 * glyph it is found by at a glance, and the value beside it is the section's own state, handed in where the
 * section is drawn because only that row knows it.
 *
 * [targets] is what makes a closed section safe for the cards other screens point at. A jump is a scroll to
 * a row's key, and a closed section has no rows at all - so the section that holds a card is the first
 * thing a jump opens, and the list of cards is kept here rather than inferred from the page, where the two
 * could drift apart one card at a time.
 */
internal enum class SettingsSection(
    @StringRes val title: Int,
    /**
     * The glyph the index row is found by.
     *
     * One per section, and unlike the words around it a glyph cannot be read slowly: two sections sharing one
     * would be two rows the eye keeps landing on the wrong one of. A section's own first card may well use the
     * same icon - that is the row saying what it opens.
     */
    val icon: ImageVector,
    /** The cards another screen can ask for by key, which are drawn inside this section. */
    val targets: List<String> = emptyList(),
) {
    Appearance(R.string.appearance, Icons.Rounded.Palette),
    Payloads(R.string.settings_section_payloads, Icons.Rounded.Folder),
    Run(R.string.settings_section_run, Icons.Rounded.Bolt, targets = listOf(SettingsTarget.PartitionReadOnly)),
    Shizuku(R.string.settings_section_shizuku, Icons.Rounded.Terminal),
    WirelessAdb(R.string.settings_section_wireless_adb, Icons.Rounded.Link),
    Root(R.string.settings_section_root, Icons.Rounded.Security),
    Recovery(R.string.settings_recovery, Icons.Rounded.RestartAlt),
    System(R.string.settings_section_system, Icons.Rounded.Settings);

    companion object {
        /**
         * The section a jump is aimed at, or null when no section claims that key.
         *
         * Null rather than a default, because guessing would scroll somewhere the caller did not ask for;
         * a key with no section is a card that moved without its entry here being moved with it.
         */
        fun holding(target: String): SettingsSection? = entries.firstOrNull { target in it.targets }

        /**
         * The sections named by [names], which is the shape they are stored in.
         *
         * By name rather than by ordinal, so a stored set survives sections being reordered in a later
         * build - and unknown names are dropped, so one left by a build that had a section this one does
         * not is a section that no longer opens rather than a crash on the way into the page.
         */
        fun named(names: Set<String>): Set<SettingsSection> = entries.filter { it.name in names }.toSet()
    }
}

/**
 * Where this section's row sits in the index card, which is what its corners are cut for.
 *
 * The eight rows are one card, so the ends are rounded and everything between them is square - the same
 * arrangement the cards inside a section use, read the same way. It is derived from the order of the enum
 * rather than passed in at each of the eight call sites, because a position that is written by hand is a
 * position that can be left behind when a section is added or moved: the page would then draw two cards and
 * nobody would know which of the nine numbers was the stale one.
 */
internal fun SettingsSection.indexPosition(): SettingsCardPosition = when (ordinal) {
    0 -> SettingsCardPosition.Top
    SettingsSection.entries.lastIndex -> SettingsCardPosition.Bottom
    else -> SettingsCardPosition.Middle
}
