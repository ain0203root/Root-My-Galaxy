package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device check, tested on the readings it parses rather than on a device.
 *
 * Every parser here is one a wrong answer would be invisible in: an event id read out of a message that
 * happens to contain a number, a kallsyms head that is empty and would read as "no symbols", a page size
 * that is not 4 KB and would still read as a version. The file bodies are the shapes those files
 * actually have, including the one that matters most - a symbol table whose addresses are zeroed
 * because the reader is not root, which is the normal case for this app.
 */
class PayloadReadinessTest {

    // --- the tracefs event id -------------------------------------------------------------------------

    @Test
    fun `the event id is the number the file holds`() {
        assertEquals(106, parseTraceEventId("106"))
        assertEquals(106, parseTraceEventId("106\n"))
        assertEquals(106, parseTraceEventId("  106  \n"))
        assertEquals(87, parseTraceEventId("87"))
    }

    /** Zero is what an unwritten file reads as, and it is not an id anything can be built against. */
    @Test
    fun `nothing in the file is not an id`() {
        assertNull(parseTraceEventId(""))
        assertNull(parseTraceEventId("\n"))
        assertNull(parseTraceEventId("0"))
        assertNull(parseTraceEventId("cat: /sys/kernel/tracing/....: Permission denied"))
    }

    /** A directory the app cannot enter reads as empty rather than as an error. */
    @Test
    fun `an unreadable file and an empty one are the same non-answer`() {
        assertNull(parseTraceEventId(""))
        assertNull(parseTraceEventId("   "))
    }

    // --- the netfilter loggers --------------------------------------------------------------------------

    /**
     * The oracle is one line of this listing.
     *
     * The file's shape is `index name (module)`, and the name is the string the exploit leaks a pointer
     * to, so both the name and the module column are what a search may match. What matters is that a
     * kernel without the logger is not read as having it: every other line here is a logger that cannot
     * serve as the oracle.
     */
    @Test
    fun `the logger the leak needs is found in the listing`() {
        val listing = """
            2 nfnetlink_log (nfnetlink_log)
            1 nf_log_ipv4 (nf_log_ipv4)
            3 nf_log_arp (nf_log_arp)
        """.trimIndent()

        assertTrue(nfnetlinkLoggerRegistered(listing))
        assertFalse(nfnetlinkLoggerRegistered("1 nf_log_ipv4 (nf_log_ipv4)"))
        assertFalse(nfnetlinkLoggerRegistered(""))
    }

    // --- SELinux ----------------------------------------------------------------------------------------

    @Test
    fun `enforcing is one and permissive is zero, and nothing else is either`() {
        assertEquals(true, parseSelinuxEnforcing("1"))
        assertEquals(true, parseSelinuxEnforcing("1\n"))
        assertEquals(false, parseSelinuxEnforcing("0"))
        assertNull(parseSelinuxEnforcing(""))
        assertNull(parseSelinuxEnforcing("permissive"))
    }

    @Test
    fun `the restriction sysctls are read as integers`() {
        assertEquals(1, parseSysctlInt("1\n"))
        assertEquals(0, parseSysctlInt("0"))
        assertEquals(2, parseSysctlInt(" 2 "))
        assertNull(parseSysctlInt(""))
    }

    // --- kallsyms ---------------------------------------------------------------------------------------

    /**
     * Addresses are hidden from a reader without root and the names are not.
     *
     * This is the shape of `/proc/kallsyms` as an app sees it: every address zeroed, because
     * `kptr_restrict` is 1 on a stock Android kernel. A reader that required a non-zero address would
     * report this phone as having no symbol table, which is the one finding that would wrongly refuse
     * a port.
     */
    @Test
    fun `a symbol table with hidden addresses still has symbols`() {
        val head = """
            0000000000000000 T _text
            0000000000000000 T __init_begin
            0000000000000000 t do_one_initcall
        """.trimIndent()

        assertTrue(kallsymsHasSymbols(head))
    }

    @Test
    fun `an empty or shapeless file has no symbols`() {
        assertFalse(kallsymsHasSymbols(""))
        assertFalse(kallsymsHasSymbols("\n\n"))
        assertFalse(kallsymsHasSymbols("names\n"))
    }

    // --- the KMI ------------------------------------------------------------------------------------------

    /** `androidNN` and the release's major.minor, which is what a module's vermagic carries. */
    @Test
    fun `the kmi comes out of the release`() {
        assertEquals("android14-6.1", kmiOf("6.1.157-android14-11"))
        assertEquals("android12-5.10", kmiOf("5.10.198-android12-9"))
        assertEquals("android15-6.6", kmiOf("6.6.98-android15-8"))
    }

    /** A kernel with no `androidNN` token is one no published target's module claims. */
    @Test
    fun `a release with no android token names no kmi`() {
        assertNull(kmiOf("4.19.110"))
        assertNull(kmiOf("6.1.157"))
        assertNull(kmiOf("android14-"))
    }

    // --- the page size ------------------------------------------------------------------------------------

    /**
     * Every target header's offsets assume 4 KB pages.
     *
     * The P0 fingerprint table is generated over page offsets inside one page, so a 16 KB device is not
     * a scaled 4 KB device: it needs its own table and its own strides. This is the one check that can
     * read as a pass on a phone where nothing would work.
     */
    @Test
    fun `only four kilobyte pages are what the published numbers assume`() {
        assertTrue(pageSizeAsExpected(4096))
        assertFalse(pageSizeAsExpected(16384))
        assertFalse(pageSizeAsExpected(0))
    }

    // --- outcomes -------------------------------------------------------------------------------------------

    @Test
    fun `true is expected, false needs a port, and nothing read is unreadable`() {
        assertEquals(CheckOutcome.AsExpected, outcomeOf(true))
        assertEquals(CheckOutcome.NeedsPorting, outcomeOf(false))
        assertEquals(CheckOutcome.Unreadable, outcomeOf(null))
    }

    @Test
    fun `a report knows what it has and what it could not read`() {
        val report = PayloadPortability(
            checks = listOf(
                DeviceCheck(DeviceCheckKind.PageSize, "4096 bytes", CheckOutcome.AsExpected),
                DeviceCheck(DeviceCheckKind.Architecture, "aarch64", CheckOutcome.AsExpected),
                DeviceCheck(DeviceCheckKind.TracefsEvent, "not readable here", CheckOutcome.Unreadable),
                DeviceCheck(DeviceCheckKind.Ashmem, "not present", CheckOutcome.NeedsPorting),
            ),
        )

        assertTrue(report.needsPorting)
        assertEquals(listOf(DeviceCheckKind.TracefsEvent), report.unreadable.map { it.kind })
        assertEquals("4096 bytes", report.readingOf(DeviceCheckKind.PageSize))
        assertNull(report.readingOf(DeviceCheckKind.Selinux))
    }

    /** A device whose readings are all as expected still needs no port, whatever the catalog says. */
    @Test
    fun `a report of expected readings asks for no port`() {
        val report = PayloadPortability(
            checks = listOf(
                DeviceCheck(DeviceCheckKind.PageSize, "4096 bytes", CheckOutcome.AsExpected),
                DeviceCheck(DeviceCheckKind.Symbols, "readable", CheckOutcome.AsExpected),
            ),
        )

        assertFalse(report.needsPorting)
        assertTrue(report.unreadable.isEmpty())
    }

    // --- the catalog line -------------------------------------------------------------------------------------

    /**
     * The three answers a catalog can give, and why they are three.
     *
     * Sources that could not be read are not a device without a payload: folding them together would
     * make a rate limit look like a phone nobody has ported, which is the same mistake the run's own
     * refusal avoids.
     */
    @Test
    fun `the catalog line names the answer it got`() {
        assertTrue(
            catalogSummaryLine(CatalogVerdict(profileId = "galaxy-s25-series", matches = 2))
                .contains("galaxy-s25-series"),
        )
        assertTrue(
            catalogSummaryLine(CatalogVerdict(failure = "HTTP 403")).contains("HTTP 403"),
        )
        assertTrue(
            catalogSummaryLine(CatalogVerdict()).contains("no enabled source"),
        )
    }

    @Test
    fun `a gap names the entries that came closest`() {
        val line = catalogSummaryLine(
            CatalogVerdict(
                closest = listOf(
                    TargetGap.Closest(
                        displayName = "Galaxy S24 series",
                        kernels = "6.1.157",
                        reason = TargetGap.Reason.SameModel,
                    ),
                ),
            ),
        )

        assertTrue(line.contains("Galaxy S24 series"))
        assertTrue(line.contains("6.1.157"))
    }

    // --- the block that gets pasted -----------------------------------------------------------------------------

    /**
     * The copied block carries the identity a port is built from and every reading that was made.
     *
     * The identity is what makes a port possible at all - a target is written against one firmware - and
     * the readings save the porter from asking for the same numbers one at a time. A reading that was
     * not made is copied as the absence it is, because a porter needs to know which numbers are still
     * unknown rather than to receive a block that looks complete.
     */
    @Test
    fun `the copied block carries the identity and the readings`() {
        val snapshot = DeviceSnapshot(
            manufacturer = "samsung",
            model = "SM-S921B",
            device = "e1s",
            kernelRelease = "6.1.157-android14-11",
            kernelVersionInfo = "#1 SMP PREEMPT",
            machine = "aarch64",
            buildId = "BP4A.251205.006.S921BXXSFDZF2",
            fingerprint = "samsung/e1sxxx/essi:16/BP4A.251205.006/S921BXXSFDZF2:user/release-keys",
            androidRelease = "16",
            sdk = 36,
            abi = "arm64-v8a",
            pageSize = 4096,
        )
        val report = PayloadPortability(
            checks = listOf(
                DeviceCheck(DeviceCheckKind.KernelInterface, "android14-6.1", CheckOutcome.AsExpected),
                DeviceCheck(DeviceCheckKind.TracefsEvent, "not readable here", CheckOutcome.Unreadable),
                DeviceCheck(DeviceCheckKind.PageSize, "4096 bytes", CheckOutcome.AsExpected),
            ),
        )

        val catalog = CatalogVerdict(failure = "HTTP 403")
        val text = portRequestText(
            snapshot = snapshot,
            portability = report,
            catalog = catalog,
        )

        assertTrue(text.contains("model: SM-S921B"))
        assertTrue(text.contains("kernel release: 6.1.157-android14-11"))
        assertTrue(text.contains("fingerprint: samsung/e1sxxx/essi:16/"))
        assertTrue(text.contains("kmi: android14-6.1"))
        assertTrue(text.contains("page_size: 4096 bytes"))
        assertTrue(text.contains("tracefs_event: not readable here"))
        // The catalog's own line, with the reason it could not be read: a porter has to be able to tell
        // a device nobody has ported from sources that were never read.
        assertTrue(text.contains("catalog: " + catalogSummaryLine(catalog)))
        assertTrue(text.contains("HTTP 403"))
    }
}
