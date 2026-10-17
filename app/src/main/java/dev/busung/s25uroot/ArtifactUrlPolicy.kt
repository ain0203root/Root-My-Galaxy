package dev.busung.s25uroot

import java.net.URI

/**
 * A path on raw.githubusercontent.com, split before resolving the ref to a commit.
 *
 * Git branch names can contain slashes, so the segments after owner/repository are kept intact
 * until the caller can recognize the source's configured branch or resolve a candidate ref.
 */
internal data class RawGithubArtifactLocation(
    val repository: String,
    val refAndPathSegments: List<String>,
)

/**
 * Parses only canonical HTTPS GitHub raw URLs. Similar-looking hosts, credentials, query strings,
 * fragments, traversal components, and malformed repository paths are rejected.
 */
internal fun parseRawGithubArtifactLocation(url: String): RawGithubArtifactLocation? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (!uri.host.equals("raw.githubusercontent.com", ignoreCase = true)) return null
    if (uri.rawUserInfo != null || uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null) {
        return null
    }

    val path = uri.rawPath ?: return null
    if (!path.startsWith("/")) return null
    val segments = path.removePrefix("/").split("/")
    if (segments.size < 4 || segments.any(String::isEmpty)) return null

    val owner = segments[0]
    val name = segments[1]
    val safeRepositoryPart = Regex("^[A-Za-z0-9_.-]+$")
    if (!safeRepositoryPart.matches(owner) || !safeRepositoryPart.matches(name)) return null
    if (owner == "." || owner == ".." || name == "." || name == "..") return null

    val tail = segments.drop(2)
    if (tail.any { segment ->
        segment == "." || segment == ".." ||
            segment.contains("%2f", ignoreCase = true) ||
            segment.contains("%5c", ignoreCase = true) ||
            segment.contains("%2e", ignoreCase = true)
    }) return null

    return RawGithubArtifactLocation("$owner/$name", tail)
}
