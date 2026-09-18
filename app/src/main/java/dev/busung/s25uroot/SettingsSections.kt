package dev.busung.s25uroot

import androidx.annotation.StringRes

/**
 * The settings page, as the parts it is read by.
 *
 * The page is long enough that finding one switch in it meant scrolling past everything else, and most
 * visits are for one thing. So each part is a section that opens on its own and stays as it was left.
 *
 * [targets] is what makes a closed section safe for the cards other screens point at. A jump is a scroll to
 * a row's key, and a closed section has no rows at all - so the section that holds a card is the first
 * thing a jump opens, and the list of cards is kept here rather than inferred from the page, where the two
 * could drift apart one card at a time.
 */
internal enum class SettingsSection(
    @StringRes val title: Int,
    /** The cards another screen can ask for by key, which are drawn inside this section. */
    val targets: List<String> = emptyList(),
) {
    Appearance(R.string.appearance),
    Payloads(R.string.settings_section_payloads),
    Run(R.string.settings_section_run, targets = listOf(SettingsTarget.PartitionReadOnly)),
    Shizuku(R.string.settings_section_shizuku),
    WirelessAdb(R.string.settings_section_wireless_adb),
    Root(R.string.settings_section_root),
    Recovery(R.string.settings_recovery),
    System(R.string.settings_section_system);

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
