package dev.busung.s25uroot

import java.net.URI

/**
 * Re-pins an artifact URL from a payload feed to the configured repository and immutable commit.
 *
 * Forked feeds commonly keep some artifact links pointing at their upstream parent. We accept
 * those links only when they use GitHub's raw-content host and name one of the payload directories
 * used by this project. The downloaded file is still fetched from the user-selected repository,
 * never from the URL's original owner/repository, so a feed cannot redirect the installer to an
 * unrelated host or silently mix artifacts from multiple repositories.
 */
internal fun pinPayloadArtifactUrl(
    source: PayloadSource,
    url: String,
    commit: String,
): String? {
    val revision = commit.trim()
    if (!PayloadSource.isCommitValid(revision)) return null

    val repositoryBase = "https://raw.githubusercontent.com/${source.repository}"
    val selectedBranchPrefix = "$repositoryBase/${source.branch}/"
    val builtInBranchPrefix =
        "https://raw.githubusercontent.com/${PayloadSource.DEFAULT.repository}/" +
            "${PayloadSource.DEFAULT.branch}/"
    val upstreamBranchPrefix =
        "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/"

    val relative = when {
        url.startsWith(selectedBranchPrefix) -> url.removePrefix(selectedBranchPrefix)
        url.startsWith(builtInBranchPrefix) -> url.removePrefix(builtInBranchPrefix)
        source.repository == PayloadSource.DEFAULT.repository &&
            url.startsWith(upstreamBranchPrefix) -> url.removePrefix(upstreamBranchPrefix)
        else -> payloadRelativePathFromGitHubRawUrl(url) ?: return null
    }

    if (!isSafePayloadRelativePath(relative)) return null
    return "$repositoryBase/${revision}/$relative"
}

/**
 * Reads just the relative artifact path from a GitHub raw URL.
 *
 * The repository/ref prefix is deliberately ignored for this fallback: a fork's feed may contain
 * links to its parent repository. We never download from that parent; we transplant only a safe
 * payload path and then fetch it from the configured repository at the resolved commit.
 */
internal fun payloadRelativePathFromGitHubRawUrl(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) ||
        !uri.host.equals("raw.githubusercontent.com", ignoreCase = true) ||
        uri.port != -1 ||
        uri.rawUserInfo != null ||
        uri.rawQuery != null ||
        uri.rawFragment != null ||
        uri.rawPath == null ||
        '%' in uri.rawPath
    ) {
        return null
    }

    val segments = uri.rawPath.split('/').drop(1)
    // owner / repository / ref / (artifacts|kernelsu) / filename
    if (segments.size < 5 || segments[0].isBlank() || segments[1].isBlank()) return null

    val payloadRootIndex = (3 until segments.size).firstOrNull {
        segments[it] == "artifacts" || segments[it] == "kernelsu"
    } ?: return null

    val relative = segments.drop(payloadRootIndex).joinToString("/")
    return relative.takeIf(::isSafePayloadRelativePath)
}

private fun isSafePayloadRelativePath(path: String): Boolean {
    if (path.isBlank() || path.startsWith('/') || '\\\\' in path || '%' in path) return false
    val segments = path.split('/')
    if (segments.size < 2 || segments.any { it.isBlank() || it == "." || it == ".." }) return false
    if (segments.first() != "artifacts" && segments.first() != "kernelsu") return false
    return segments.all { segment -> segment.none { it == '?' || it == '#' || it == ':' } }
}
