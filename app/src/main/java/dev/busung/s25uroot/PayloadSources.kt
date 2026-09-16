package dev.busung.s25uroot

/**
 * A GitHub repository serving a payload catalog. Several sources can be configured at once;
 * each is fetched on its own and every target keeps the identity of the source that provided
 * it, so two sources may offer the same payload without either one shadowing the other.
 */
data class PayloadSource(
    val repository: String,
    val branch: String,
    val enabled: Boolean = true,
) {
    val id: String
        get() = "$repository@$branch"

    val label: String
        get() = "$repository @ $branch"

    companion object {
        const val DEFAULT_REPOSITORY = "BuSung-dev/Root-My-Galaxy-Payloads"
        const val DEFAULT_BRANCH = "main"

        val REPOSITORY_PATTERN = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
        val BRANCH_PATTERN = Regex("^[A-Za-z0-9_.\\-/]+$")

        val DEFAULT = PayloadSource(
            repository = DEFAULT_REPOSITORY,
            branch = DEFAULT_BRANCH,
            enabled = true,
        )

        fun isRepositoryValid(repository: String): Boolean =
            REPOSITORY_PATTERN.matches(repository.trim())

        fun isBranchValid(branch: String): Boolean = BRANCH_PATTERN.matches(branch.trim())

        /** Builds a source from raw input, or null when the repository or branch is unusable. */
        fun create(
            repository: String,
            branch: String,
            enabled: Boolean = true,
        ): PayloadSource? {
            val owner = repository.trim()
            val ref = branch.trim()
            if (!isRepositoryValid(owner) || !isBranchValid(ref)) return null
            return PayloadSource(owner, ref, enabled)
        }
    }
}

private const val SELECTION_SEPARATOR = "|"

/**
 * Identifies a target across sources. A source id cannot contain the separator (it is built
 * from the repository and branch patterns), so the first separator always splits the two parts.
 */
fun selectionIdFor(sourceId: String, profileId: String): String =
    if (sourceId.isEmpty()) profileId else "$sourceId$SELECTION_SEPARATOR$profileId"

fun sourceFromSelectionId(selectionId: String): String? {
    val index = selectionId.indexOf(SELECTION_SEPARATOR)
    return if (index <= 0) null else selectionId.substring(0, index)
}

fun profileFromSelectionId(selectionId: String): String {
    val index = selectionId.indexOf(SELECTION_SEPARATOR)
    return if (index <= 0) selectionId else selectionId.substring(index + 1)
}

fun List<PayloadSource>.enabledSources(): List<PayloadSource> = filter { it.enabled }

fun List<PayloadSource>.withSourceAdded(source: PayloadSource): List<PayloadSource> =
    if (any { it.id == source.id }) this else this + source

fun List<PayloadSource>.withSourceRemoved(sourceId: String): List<PayloadSource> =
    filterNot { it.id == sourceId }

fun List<PayloadSource>.withSourceEnabled(
    sourceId: String,
    enabled: Boolean,
): List<PayloadSource> = map { if (it.id == sourceId) it.copy(enabled = enabled) else it }
