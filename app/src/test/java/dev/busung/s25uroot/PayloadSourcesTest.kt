package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadSourcesTest {
    private val official = PayloadSource.DEFAULT
    private val community = PayloadSource("example-org/payloads", "testing", enabled = false)

    @Test
    fun defaultSourceIsUsable() {
        assertTrue(PayloadSource.isRepositoryValid(official.repository))
        assertTrue(PayloadSource.isBranchValid(official.branch))
        assertTrue(official.enabled)
        assertEquals("BuSung-dev/Root-My-Galaxy-Payloads@main", official.id)
    }

    @Test
    fun repositoryNeedsExactlyOneOwnerAndName() {
        assertTrue(PayloadSource.isRepositoryValid("rushiranpise/Root-My-Galaxy-Payloads"))
        assertTrue(PayloadSource.isRepositoryValid("a/b"))
        assertFalse(PayloadSource.isRepositoryValid("owner-only"))
        assertFalse(PayloadSource.isRepositoryValid("owner/name/extra"))
        assertFalse(PayloadSource.isRepositoryValid("owner/ name"))
        assertFalse(PayloadSource.isRepositoryValid(""))
    }

    @Test
    fun branchExcludesCharactersThatBreakUrlsOrCommands() {
        assertTrue(PayloadSource.isBranchValid("main"))
        assertTrue(PayloadSource.isBranchValid("feature/multi-source"))
        assertTrue(PayloadSource.isBranchValid("release-1.2"))
        assertFalse(PayloadSource.isBranchValid("main;rm -rf /"))
        assertFalse(PayloadSource.isBranchValid("with space"))
        assertFalse(PayloadSource.isBranchValid(""))
    }

    @Test
    fun createTrimsInputAndRejectsUnusableSources() {
        assertEquals(
            PayloadSource("example-org/payloads", "main"),
            PayloadSource.create("  example-org/payloads  ", " main "),
        )
        assertNull(PayloadSource.create("example-org", "main"))
        assertNull(PayloadSource.create("example-org/payloads", "bad branch"))
    }

    @Test
    fun selectionIdKeepsSourcesApartWhenTheyOfferTheSamePayload() {
        val payload = "galaxy-s25-series-kernel-6.6.98"

        val officialSelection = selectionIdFor(official.id, payload)
        val communitySelection = selectionIdFor(community.id, payload)
        assertEquals(officialSelection, selectionIdFor(official.id, payload))
        assertFalse(officialSelection == communitySelection)
        assertEquals(official.id, sourceFromSelectionId(officialSelection))
        assertEquals(community.id, sourceFromSelectionId(communitySelection))
        assertEquals(payload, profileFromSelectionId(officialSelection))
        assertEquals(payload, profileFromSelectionId(communitySelection))
    }

    @Test
    fun unqualifiedSelectionStaysAPlainProfileId() {
        assertEquals("plain", selectionIdFor("", "plain"))
        assertNull(sourceFromSelectionId("plain"))
        assertEquals("plain", profileFromSelectionId("plain"))
    }

    @Test
    fun catalogEditsAddRemoveAndToggleSources() {
        var sources = listOf(official)

        sources = sources.withSourceAdded(community)
        assertEquals(listOf(official, community), sources)

        sources = sources.withSourceAdded(PayloadSource(community.repository, community.branch))
        assertEquals(2, sources.size)

        sources = sources.withSourceEnabled(community.id, true)
        assertEquals(listOf(official, community.copy(enabled = true)), sources)
        assertEquals(2, sources.enabledSources().size)

        sources = sources.withSourceEnabled(official.id, false)
        assertEquals(listOf(community.id), sources.enabledSources().map { it.id })

        sources = sources.withSourceRemoved(official.id)
        assertEquals(listOf(community.id), sources.map { it.id })
        assertTrue(sources.single().enabled)
    }

    @Test
    fun aTargetWithoutASourceStillResolvesToItsOwnId() {
        val profile = TargetProfile(
            profileId = "galaxy-s25-series-kernel-6.6.98",
            displayName = "Galaxy S25 series",
            models = setOf("SM-S931B"),
            kernelVersions = setOf("6.6.98"),
            exploit = RemoteArtifact("https://example.invalid/exploit", 1),
            kernelSu = RemoteArtifact("https://example.invalid/ksud", 1),
        )

        assertEquals(profile.profileId, profile.selectionId)

        val sourced = profile.copy(sourceId = community.id, sourceLabel = community.label)
        assertEquals(selectionIdFor(community.id, profile.profileId), sourced.selectionId)
        assertEquals(community.id, sourceFromSelectionId(sourced.selectionId))
        assertEquals(profile.profileId, profileFromSelectionId(sourced.selectionId))
    }
}
