package dev.busung.s25uroot

import android.content.Context

/**
 * Per-boot bookkeeping for the automatic install.
 *
 * Everything here is keyed by the kernel's boot id rather than by a timestamp, because the events
 * this has to tell apart are not the same kind of thing: a real reboot, a userspace restart that
 * re-emits `BOOT_COMPLETED` while the same kernel stays up, and two replies from the same boot.
 * A token that does not change is the only signal that distinguishes them.
 */
/** Why the gate does, or does not, start an automatic install for this boot. */
internal enum class AutoRootDecision {
    Run,
    SkipDisabled,
    SkipAlreadyRooted,
    SkipAlreadyVerified,
    SkipAttempted,
    NeedsPriorInstall,
}

/**
 * The gate's whole rule, as one pure decision.
 *
 * Order matters and is the reason this is not spread through the service: a boot that already has
 * root is not a boot that needs an install, a boot whose attempt is spent does not get a second one,
 * and an install that was verified *in this boot* stays verified across the userspace restarts that
 * re-emit BOOT_COMPLETED. Only the last case asks anything of the user.
 */
internal fun autoRootDecision(
    enabled: Boolean,
    kernelSuActive: Boolean,
    hasVerifiedInstall: Boolean,
    verifiedBootToken: String?,
    attemptedBootToken: String?,
    bootToken: String,
): AutoRootDecision = when {
    !enabled -> AutoRootDecision.SkipDisabled
    kernelSuActive -> AutoRootDecision.SkipAlreadyRooted
    verifiedBootToken == bootToken -> AutoRootDecision.SkipAlreadyVerified
    attemptedBootToken == bootToken -> AutoRootDecision.SkipAttempted
    !hasVerifiedInstall -> AutoRootDecision.NeedsPriorInstall
    else -> AutoRootDecision.Run
}

internal object AutoRootSupport {
    private const val RECEIPT = "install_receipt"
    private const val RECEIPT_VERIFIED = "verified"
    private const val RECEIPT_BOOT_TOKEN = "kernel_boot_id"
    private const val STATE = "auto_root_state"
    private const val LAST_BOOT_COMPLETED_TOKEN = "last_boot_completed_boot_id"
    private const val LAST_ATTEMPT_TOKEN = "last_attempt_boot_id"

    fun currentBootToken(): String? = kernelBootToken()

    /** Whether an install has been verified on this device at all, which is what a boot run needs. */
    fun hasVerifiedInstall(context: Context): Boolean =
        context.getSharedPreferences(RECEIPT, Context.MODE_PRIVATE)
            .getBoolean(RECEIPT_VERIFIED, false) && KnownGoodPayloadStore.hasValid(context)

    /** The boot an install was last verified in, or null when none has been. */
    fun verifiedBootToken(context: Context): String? {
        val preferences = context.getSharedPreferences(RECEIPT, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(RECEIPT_VERIFIED, false)) return null
        return preferences.getString(RECEIPT_BOOT_TOKEN, null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    /**
     * Whether this boot still needs an install.
     *
     * The token is the whole test: after a userspace restart the boot id is unchanged, and an install
     * that was verified in this boot is still valid, so a second `BOOT_COMPLETED` must not start one.
     */
    fun shouldRunForBoot(context: Context, bootToken: String): Boolean =
        verifiedBootToken(context) != bootToken

    /** Records that root was verified in [bootToken], which is what stops a re-run within it. */
    fun markVerifiedForBoot(context: Context, bootToken: String) {
        val stored = context.getSharedPreferences(RECEIPT, Context.MODE_PRIVATE)
            .edit()
            .putString(RECEIPT_BOOT_TOKEN, bootToken)
            .putBoolean(RECEIPT_VERIFIED, true)
            .commit()
        require(stored) { context.getString(R.string.error_receipt) }
    }

    /**
     * Consumes the framework's `BOOT_COMPLETED` for this kernel boot exactly once.
     *
     * A userspace restart can publish another one while the kernel stays up, and treating that as a
     * fresh boot is how a boot automation starts twice. Returns false when this boot has been seen.
     */
    @Synchronized
    fun claimBootCompletedForKernel(context: Context, bootToken: String): Boolean {
        val preferences = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        if (preferences.getString(LAST_BOOT_COMPLETED_TOKEN, null) == bootToken) return false
        return preferences.edit()
            .putString(LAST_BOOT_COMPLETED_TOKEN, bootToken)
            .commit()
    }

    fun hasAttemptedBoot(context: Context, bootToken: String): Boolean =
        context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
            .getString(LAST_ATTEMPT_TOKEN, null) == bootToken

    /**
     * Claims this boot's single attempt.
     *
     * One attempt per boot is the point: the exploit is a race, and spending it twice in the same
     * boot tells the user nothing the first attempt did not, while the second attempt is what a
     * half-finished first one would collide with.
     */
    @Synchronized
    fun claimAttempt(context: Context, bootToken: String): Boolean {
        val preferences = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        if (preferences.getString(LAST_ATTEMPT_TOKEN, null) == bootToken) return false
        return preferences.edit()
            .putString(LAST_ATTEMPT_TOKEN, bootToken)
            .commit()
    }

    /**
     * The gate's decision for this boot, from the rules in [autoRootDecision].
     *
     * [kernelSuActive] is passed in rather than probed here so the rule itself has no device in it,
     * and so a caller that has already probed does not have to probe again to ask the question.
     */
    fun decision(context: Context, bootToken: String, kernelSuActive: Boolean): AutoRootDecision =
        autoRootDecision(
            enabled = AppPreferences.bootRootMode(context),
            kernelSuActive = kernelSuActive,
            hasVerifiedInstall = hasVerifiedInstall(context),
            verifiedBootToken = verifiedBootToken(context),
            attemptedBootToken = bootToken.takeIf { hasAttemptedBoot(context, bootToken) },
            bootToken = bootToken,
        )

    /** Forgets the boot-scoped bookkeeping, so the next boot is treated as a fresh one. */
    @Synchronized
    fun reset(context: Context) {
        context.getSharedPreferences(STATE, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
