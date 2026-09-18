package dev.busung.s25uroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The check's two contracts: how a stat's errno is read, and that the catalog is the whole list.
 *
 * The errno rule is the one that decides whether a denied read can ever be shown as a clean device,
 * and the catalog rule is the one that decides whether a file this app writes can be missing from the
 * list - either of which would make the check report good news about residue that is still there. Both
 * are testable without a device, which is why they are the two halves that were built that way: the
 * `Context`-free part of the reading is [StagedResidue.readingFor], and the catalog is checked against
 * the sources the same way [ManifestPermissionTest] checks permissions against the manifest.
 */
class StagedResidueTest {

    @Test
    fun `a file that is not there is the only thing read as an absence`() {
        assertEquals(ResidueReading.Gone, StagedResidue.readingFor(ENOENT))
        // A path that is not a directory is the same answer arrived at differently: the name is simply
        // not there to be found.
        assertEquals(ResidueReading.Gone, StagedResidue.readingFor(ENOTDIR))
    }

    @Test
    fun `a denied read is never reported as an absence`() {
        // The failure this prevents: an app that cannot see /data/local/tmp reporting a clean device,
        // which is the same statement as a device with nothing on it and the opposite of the truth.
        assertEquals(ResidueReading.Unreadable, StagedResidue.readingFor(EACCES))
        assertEquals(ResidueReading.Unreadable, StagedResidue.readingFor(EPERM))
    }

    @Test
    fun `an errno this app has never seen is no answer either`() {
        // The default is the point, and it has to survive an errno nobody anticipated: anything that is
        // not "no such file" must come back as unknown rather than as good news.
        assertEquals(ResidueReading.Unreadable, StagedResidue.readingFor(ELOOP))
        assertEquals(ResidueReading.Unreadable, StagedResidue.readingFor(Int.MIN_VALUE))
        assertEquals(ResidueReading.Unreadable, StagedResidue.readingFor(0))
    }

    @Test
    fun `a readable reading that found nothing is not a blind one`() {
        // The two have to look different, because one is a device with nothing left on it and the other
        // is a check that cannot see. Reporting the second as the first is the whole risk of this code.
        val clean = ResidueReport(
            findings = StagedResidue.catalog.map { ResidueFinding(it, ResidueReading.Gone) },
            directoryVisible = true,
        )
        assertFalse(clean.blind)
        assertTrue(clean.present.isEmpty())

        val blind = ResidueReport(
            findings = StagedResidue.catalog.map { ResidueFinding(it, ResidueReading.Unreadable) },
            directoryVisible = false,
        )
        assertTrue(blind.blind)
    }

    @Test
    fun `one readable path is enough to make a reading that is not blind`() {
        // A reading can be mixed: the files this app owns are world-readable while a root-owned script
        // is not stat'able, and the answer for the list as a whole is still a real one.
        val mixed = ResidueReport(
            findings = StagedResidue.catalog.mapIndexed { index, staged ->
                ResidueFinding(
                    staged,
                    if (index == 0) {
                        ResidueReading.Present(sizeBytes = 10L, modifiedAtMillis = 0L)
                    } else {
                        ResidueReading.Unreadable
                    },
                )
            },
            directoryVisible = true,
        )
        assertFalse(mixed.blind)
        assertEquals(1, mixed.present.size)
        assertEquals(10L, mixed.totalBytes)
    }

    @Test
    fun `the totals are what the list shows`() {
        val staged = StagedResidue.catalog.first()
        val other = StagedResidue.catalog.last()
        val report = ResidueReport(
            findings = listOf(
                ResidueFinding(staged, ResidueReading.Present(2_000L, 1_000L)),
                ResidueFinding(other, ResidueReading.Present(3_000L, 500L)),
                ResidueFinding(StagedResidue.catalog[1], ResidueReading.Gone),
            ),
            directoryVisible = true,
        )
        assertEquals(5_000L, report.totalBytes)
        // The oldest, not the newest: it is the one that says how long this has been accumulating.
        assertEquals(500L, report.oldestMillis)
    }

    @Test
    fun `an unreadable path is not counted in the size`() {
        val report = ResidueReport(
            findings = listOf(
                ResidueFinding(StagedResidue.catalog[0], ResidueReading.Unreadable),
                ResidueFinding(StagedResidue.catalog[1], ResidueReading.Present(1_500L, 0L)),
            ),
            directoryVisible = true,
        )
        // A path whose size nobody could read contributes nothing rather than a zero, so the total is
        // a floor on what is there and never a claim that something empty is.
        assertEquals(1_500L, report.totalBytes)
        assertEquals(1, report.present.size)
    }

    @Test
    fun `a size reads the way a file manager would print it`() {
        assertEquals("512 B", StagedResidue.sizeLabel(512L))
        assertEquals("21 KB", StagedResidue.sizeLabel(21_464L))
        assertEquals("5.1 MB", StagedResidue.sizeLabel(5_095_160L))
        assertEquals("0 B", StagedResidue.sizeLabel(0L))
    }

    @Test
    fun `an age is counted in whole days, and the oldest thing says so plainly`() {
        val now = 1_800_000_000_000L
        val day = 86_400_000L
        assertEquals("today", StagedResidue.ageLabelOf(now, now))
        assertEquals("yesterday", StagedResidue.ageLabelOf(now - day, now))
        assertEquals("3 days", StagedResidue.ageLabelOf(now - 3 * day, now))
        assertEquals("a week", StagedResidue.ageLabelOf(now - 8 * day, now))
        assertEquals("6 weeks", StagedResidue.ageLabelOf(now - 42 * day, now))
        assertEquals("3 months", StagedResidue.ageLabelOf(now - 100 * day, now))
        // A timestamp in the future is a clock that moved, not a file from tomorrow.
        assertEquals("today", StagedResidue.ageLabelOf(now + 5 * day, now))
        assertEquals("unknown", StagedResidue.ageLabelOf(null, now))
    }

    @Test
    fun `every path in the catalog is under the directory the check reads`() {
        assertTrue(StagedResidue.catalog.isNotEmpty())
        StagedResidue.catalog.forEach { staged ->
            assertTrue(
                "${staged.path} is not under ${StagedResidue.DIRECTORY}",
                staged.path.startsWith("${StagedResidue.DIRECTORY}/"),
            )
            assertTrue("${staged.path} is not a name", staged.name.isNotBlank())
        }
    }

    @Test
    fun `the catalog lists nothing twice`() {
        // A duplicate would be a row counted twice in the summary, and would hide the path it was
        // meant to be alongside.
        val paths = StagedResidue.catalog.map { it.path }
        assertEquals(paths.size, paths.distinct().size)
    }

    @Test
    fun `every path the app stages is in the catalog`() {
        val sources = sourceFiles()
        assertTrue("no sources were found; the scan is looking at the wrong directory", sources.isNotEmpty())
        val staged = sources
            .filter { it.name != "StagedResidue.kt" }
            .flatMap { source ->
                STAGING_PATH.findAll(source.readText()).map { match -> match.value }.toList()
            }
            .distinct()
        assertTrue("the scan found no staged paths at all", staged.isNotEmpty())

        val catalogued = StagedResidue.catalog.map { it.path }.toSet()
        val missing = staged.filterNot { catalogued.contains(it) }.sorted()
        assertEquals(
            "these paths are staged by the app but are not in StagedResidue.catalog, so the check " +
                "would report a device with nothing on it while they are sitting there",
            emptyList<String>(),
            missing,
        )
    }

    private fun sourceFiles(): List<File> = candidateRoots()
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun candidateRoots(): List<File> = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).filter(File::isDirectory)

    private companion object {
        /**
         * A staged path as the sources write it: the directory, then one name of unquoted characters.
         *
         * Deliberately blind to how the path is put together - a literal, or a constant the staging
         * code refers to - because the point is to catch the name wherever it is written.
         */
        val STAGING_PATH = Regex("""/data/local/tmp/[A-Za-z0-9._-]+""")

        /*
         * The errno numbers written out, rather than read from `OsConstants`.
         *
         * A local unit test is compiled against the mockable `android.jar` AGP generates, in which
         * every platform constant is zeroed - so `OsConstants.EACCES` is 0 here and would make these
         * assertions test nothing but each other. The numbers are the ones Linux and the device use,
         * and writing them down is what makes this test able to disagree with the app's own use of the
         * platform constants: ENOENT 2, EACCES 13, EPERM 1, ELOOP 40, ENOTDIR 20.
         */
        const val ENOENT = 2
        const val ENOTDIR = 20
        const val EACCES = 13
        const val EPERM = 1
        const val ELOOP = 40
    }
}
