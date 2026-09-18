package dev.busung.s25uroot

/**
 * What the target sheet shows, out of everything the sources returned.
 *
 * Pure and separate from the sheet because the sheet's two controls answer the same question - "which
 * of these could this phone run" - and the list they are drawn from is rebuilt on every recomposition.
 * Kept here, the two compose in one place instead of one filtering the other's output at the call
 * site, and the empty list has a nameable cause: the device filter, the search, or a catalog that
 * genuinely holds nothing.
 */
internal fun visibleTargets(
    profiles: List<TargetProfile>,
    device: DeviceSnapshot,
    fitsDeviceOnly: Boolean,
    query: String,
): List<TargetProfile> = profiles.filter { profile ->
    (!fitsDeviceOnly || profile.matches(device)) && profile.matchesQuery(query)
}

/**
 * Whether a target is what someone typed.
 *
 * Every field a row shows is searched, plus the flavour, because the sheet's text is the only account
 * of a target at a glance: a model, a kernel release and the source it came from are all things
 * someone arrives holding - "S928B", "6.1.99", "next" - and none of them is the profile's name.
 *
 * Case-insensitive, and a substring rather than a word: a kernel version is typed by its parts, and
 * nobody types the whole of `6.1.99-android14-11`.
 */
internal fun TargetProfile.matchesQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return displayName.contains(needle, ignoreCase = true) ||
        models.any { it.contains(needle, ignoreCase = true) } ||
        kernelVersions.any { it.contains(needle, ignoreCase = true) } ||
        sourceLabel.contains(needle, ignoreCase = true) ||
        flavor.name.contains(needle, ignoreCase = true)
}
