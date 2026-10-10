package dev.busung.s25uroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PayloadArtifactUrlsTest {
    private val source = PayloadSource("NORTCHOT/Root-My-Galaxy-Payloads", "main")
    private val commit = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun forkCanUseArtifactLinksInheritedFromItsUpstreamParent() {
        assertEquals(
            "https://raw.githubusercontent.com/NORTCHOT/Root-My-Galaxy-Payloads/$commit/" +
                "artifacts/r12s-S721BXXSCDZF3/cve-2026-43499-app.so",
            pinPayloadArtifactUrl(
                source,
                "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/" +
                    "artifacts/r12s-S721BXXSCDZF3/cve-2026-43499-app.so",
                commit,
            ),
        )
        assertEquals(
            "https://raw.githubusercontent.com/NORTCHOT/Root-My-Galaxy-Payloads/$commit/" +
                "kernelsu/ksud-r12s-S721BXXSCDZF3-kdp",
            pinPayloadArtifactUrl(
                source,
                "https://raw.githubusercontent.com/ain0203root/Root-My-Galaxy-Payloads/main/" +
                    "kernelsu/ksud-r12s-S721BXXSCDZF3-kdp",
                commit,
            ),
        )
    }

    @Test
    fun chosenRepositoryUrlsAreRepinnedToItsResolvedCommit() {
        assertEquals(
            "https://raw.githubusercontent.com/NORTCHOT/Root-My-Galaxy-Payloads/$commit/" +
                "artifacts/device/payload.so",
            pinPayloadArtifactUrl(
                source,
                "https://raw.githubusercontent.com/NORTCHOT/Root-My-Galaxy-Payloads/main/" +
                    "artifacts/device/payload.so",
                commit,
            ),
        )
    }

    @Test
    fun rawGitHubLinksFromOtherPayloadReposCanBeMappedWithoutTrustingTheirHostPath() {
        assertEquals(
            "https://raw.githubusercontent.com/NORTCHOT/Root-My-Galaxy-Payloads/$commit/" +
                "kernelsu/ksud-device",
            pinPayloadArtifactUrl(
                source,
                "https://raw.githubusercontent.com/some-owner/some-fork/dev/kernelsu/ksud-device",
                commit,
            ),
        )
    }

    @Test
    fun rejectsExternalHostsAndPathsOutsidePayloadDirectories() {
        assertNull(
            pinPayloadArtifactUrl(
                source,
                "https://example.com/artifacts/device/payload.so",
                commit,
            ),
        )
        assertNull(
            pinPayloadArtifactUrl(
                source,
                "https://raw.githubusercontent.com/some-owner/some-fork/main/other/payload.so",
                commit,
            ),
        )
    }

    @Test
    fun rejectsTraversalQueryMalformedUrlAndInvalidCommit() {
        assertNull(pinPayloadArtifactUrl(source, "not a URL", commit))
        assertNull(
            pinPayloadArtifactUrl(
                source,
                "https://raw.githubusercontent.com/some-owner/some-fork/main/artifacts/../secrets",
                commit,
            ),
        )
        assertNull(
            pinPayloadArtifactUrl(
                source,
                "https://raw.githubusercontent.com/some-owner/some-fork/main/artifacts/payload.so?download=1",
                commit,
            ),
        )
        assertNull(
            pinPayloadArtifactUrl(
                source,
                "https://raw.githubusercontent.com/some-owner/some-fork/main/artifacts/payload.so",
                "not-a-commit",
            ),
        )
    }
}
