package dev.busung.s25uroot

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The KernelSU manager app: what the app offers to install, and opening what is already there.
 *
 * The manager is not part of the payload. It is a plain app that talks to the loaded module over
 * KernelSU's socket, so it can be any version and can be replaced at any time without touching the
 * kernel - which is exactly why nothing here refuses a version it did not choose. The app offers one
 * release unprompted and otherwise does what the user asks, including asking upstream for a version
 * it has never heard of.
 */
internal object KernelSuManager {
    /** Whether [flavor]'s manager is installed. */
    fun isInstalled(context: Context, flavor: KernelSuFlavor): Boolean =
        runCatching {
            context.packageManager.getLaunchIntentForPackage(flavor.managerPackage) != null
        }.getOrDefault(false)

    /**
     * Which flavour's manager is on the phone, when exactly one of them is.
     *
     * An observation rather than a decision: the two managers can both be installed, and a phone can
     * have a manager while the other flavour is the one loaded. Only one answer is useful here, so
     * two managers answer null and the caller says nothing rather than guessing.
     */
    fun installedFlavor(context: Context): KernelSuFlavor? =
        KernelSuFlavor.entries.singleOrNull { isInstalled(context, it) }

    /**
     * The release the app offers for [flavor]: the version the user named, or the flavour's default.
     *
     * No network for a default, because its asset name is known; a named version is resolved on the
     * way to installing it, where a failed lookup is worth a message.
     */
    fun offeredRelease(context: Context, flavor: KernelSuFlavor): ManagerRelease {
        val named = AppPreferences.managerVersion(context, flavor) ?: return flavor.defaultManagerRelease
        if (named == flavor.defaultManagerVersion) return flavor.defaultManagerRelease
        return flavor.defaultManagerRelease.copy(version = named, url = releasePageUrl(flavor, named))
    }

    /**
     * Opens the manager, or the download for the offered release when it is not installed.
     *
     * The installed case wins deliberately: a manager of any version is what drives the loaded module,
     * and sending a user who already has one to a download page would be an upgrade they did not ask
     * for.
     */
    fun open(context: Context, flavor: KernelSuFlavor, onMessage: (String) -> Unit) {
        val launch = runCatching {
            context.packageManager.getLaunchIntentForPackage(flavor.managerPackage)
        }.getOrNull()
        if (launch != null) {
            context.startActivity(launch)
            return
        }
        val named = AppPreferences.managerVersion(context, flavor)
        if (named == null || named == flavor.defaultManagerVersion) {
            view(context, flavor.defaultManagerRelease.url)
            return
        }
        onMessage(context.getString(R.string.settings_manager_version_looking, flavor.label, named))
        val resolved = resolve(context, flavor, named)
        if (resolved == null) {
            onMessage(context.getString(R.string.settings_manager_version_missing, flavor.label, named))
            return
        }
        view(context, resolved.url)
    }

    /**
     * The APK for one version, resolved through the releases API.
     *
     * Null when the version has no release, the release carries no APK, or the network refused - all
     * three are the same answer to the caller, which is that this version could not be turned into a
     * download. Going through the API is what lets a version be named at all: the asset's file name
     * carries a build number (`KernelSU_v3.2.5_32525-release.apk`) that the version does not.
     */
    fun resolve(context: Context, flavor: KernelSuFlavor, version: String): ManagerRelease? {
        val url = managerReleaseApiUrl(flavor, version)
        val body = runCatching { downloadText(url) }.getOrNull() ?: return null
        val apk = managerApkInRelease(body) ?: return null
        return ManagerRelease(flavor = flavor, version = version, url = apk)
    }

    private fun view(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun downloadText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "S25URoot/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json")
            connect()
            require(responseCode == HttpURLConnection.HTTP_OK) { "HTTP $responseCode" }
        }
        return connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_RELEASE_BYTES) { "release response too large" }
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }.also { connection.disconnect() }
    }

    /** The release's own page, which is where a human looks when the API cannot answer. */
    private fun releasePageUrl(flavor: KernelSuFlavor, version: String): String =
        "https://github.com/${flavor.repository}/releases/tag/v$version"

    private fun managerReleaseApiUrl(flavor: KernelSuFlavor, version: String): String =
        "https://api.github.com/repos/${flavor.repository}/releases/tags/v$version"

    /** A releases answer is a few KB; the ceiling only bounds memory on a wrong URL. */
    private const val MAX_RELEASE_BYTES = 1024 * 1024
}
