package dev.busung.s25uroot

/**
 * The optional safeguard that marks the partitions an image could be written to read-only.
 *
 * It exists because of the shape of this app's own success: bootstrap root is real root, granted
 * before anything has verified that the kernel it is running on belongs to the firmware it was built
 * for. The reported failures are people using that window to write a boot or vbmeta image - the kind
 * of write that produces a device that boots to nothing and needs download mode to recover. Setting
 * those devices read-only first means the write fails instead.
 *
 * **Off unless asked for**, and the reason is not caution for its own sake. What this blocks is not
 * only mistakes: flashing a kernel image from the phone, a module that writes a partition directly,
 * and a KernelSU install that patches `boot` rather than loading at runtime are all legitimate and all
 * stop working while it is on. Nothing in the app can tell those apart from the mistake, so the person
 * who knows what they are doing on their own device decides. It is also per boot: `blockdev --setro`
 * affects the running kernel, so a reboot - the normal state for flashing - clears it.
 *
 * The script lives in assets because it runs as a file with bootstrap root, and the only thing the app
 * reads back is how many devices it managed to set.
 */
internal object PartitionReadOnly {

    /** The asset holding the script, so the name is not written out at each call site. */
    const val SCRIPT_ASSET = "set_ro_blocks.sh"

    /**
     * How many devices the script reported setting, given its output.
     *
     * The count is the last number it printed: the script prints nothing else, but a shell that
     * echoed something on the way in would otherwise be read as the answer. Anything unreadable, or
     * negative, counts as none - a run must not report protection it did not get.
     */
    fun countFrom(output: String): Int = output.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull(String::toIntOrNull)
        .lastOrNull()
        ?.takeIf { it > 0 }
        ?: 0
}
