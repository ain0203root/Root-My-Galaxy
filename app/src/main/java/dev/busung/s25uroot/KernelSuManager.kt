package dev.busung.s25uroot

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A manager this app found on the phone, whether or not its package is the published one. */
internal data class InstalledManager(
    val packageName: String,
    val label: String,
    /** The flavour its package name or its label claims, or null when neither says. */
    val flavor: KernelSuFlavor?,
    /** Whether its package is not the one its project publishes. */
    val spoofed: Boolean,
)

/** What a package name and an app label say about which manager this is. */
internal data class ManagerIdentity(
    val flavor: KernelSuFlavor?,
    val spoofed: Boolean,
)

/**
 * Which project an installed manager belongs to, from the two things it carries.
 *
 * The package name is authoritative when it is one either project publishes. When it is not, the
 * label is the only remaining signal, and a label that says neither leaves the flavour unknown rather
 * than guessed: a package that carries a KernelSU daemon is still a manager worth offering, and
 * attributing it to the wrong project would put it in the wrong flavour's row.
 *
 * Pure, because the case this exists for is the odd one. KernelSU-Next's spoofed manager build
 * rewrites its package to three random words every time it is built, so that name can never be a
 * constant in this app and the label is what is left.
 */
internal fun identifyManager(packageName: String, label: String): ManagerIdentity {
    val published = KernelSuFlavor.entries.firstOrNull {
        it.managerPackage.equals(packageName.trim(), ignoreCase = true)
    }
    if (published != null) return ManagerIdentity(published, spoofed = false)

    val words = label.lowercase().replace('-', ' ').replace('_', ' ')
    return when {
        "next" in words -> ManagerIdentity(KernelSuFlavor.KernelSuNext, spoofed = true)
        "kernelsu" in words || "kernel su" in words -> ManagerIdentity(KernelSuFlavor.KernelSu, spoofed = true)
        else -> ManagerIdentity(null, spoofed = true)
    }
}

/**
 * The KernelSU manager app: which one is on the phone, what the app offers to install, and opening it.
 *
 * The manager is not part of the payload. It is a plain app that talks to the loaded module over
 * KernelSU's socket, so it can be any version and can be replaced at any time without touching the
 * kernel - which is why nothing here refuses a version it did not choose, and why the apk is found
 * by what it carries rather than by a name this app would have to know in advance.
 */
internal object KernelSuManager {
    /** The manager the app will open for [flavor], or null when none is installed. */
    fun installedFor(context: Context, flavor: KernelSuFlavor): InstalledManager? {
        // A package the user named wins, because that is the only way a build whose name changes on
        // every release can be addressed at all.
        AppPreferences.managerPackage(context, flavor)?.let { named ->
            if (isLaunchable(context, named)) {
                return InstalledManager(named, labelOf(context, named), flavor, spoofed = true)
            }
        }
        if (isLaunchable(context, flavor.managerPackage)) {
            return InstalledManager(
                packageName = flavor.managerPackage,
                label = labelOf(context, flavor.managerPackage),
                flavor = flavor,
                spoofed = false,
            )
        }
        // Nothing under a published name, so look for one under any name. A plain build is preferred
        // over a spoofed one only when both are present, which is a tie nobody has.
        val found = installedManagers(context).filter { it.flavor == flavor }
        return found.firstOrNull { !it.spoofed } ?: found.firstOrNull()
    }

    fun isInstalled(context: Context, flavor: KernelSuFlavor): Boolean =
        installedFor(context, flavor) != null

    /** The package the app will open for [flavor], installed or not. */
    fun packageFor(context: Context, flavor: KernelSuFlavor): String =
        installedFor(context, flavor)?.packageName ?: flavor.managerPackage

    /**
     * Every installed app that carries a KernelSU daemon.
     *
     * The daemon is the marker: a manager ships `libksud.so`, because the manager is what runs `ksud`
     * on the phone. It is what makes a spoofed build findable at all, since its package name is
     * rewritten to something different every time it is released.
     *
     * Needs `QUERY_ALL_PACKAGES` to see an app that is not already named in the manifest's `queries`,
     * which no fixed list can do for a name that changes per build.
     */
    fun installedManagers(context: Context): List<InstalledManager> {
        val manager = context.packageManager
        return runCatching {
            manager.getInstalledPackages(0).mapNotNull { installed ->
                val app = installed.applicationInfo ?: return@mapNotNull null
                if (app.packageName == context.packageName) return@mapNotNull null
                if (!carriesDaemon(app)) return@mapNotNull null
                if (!isLaunchable(context, app.packageName)) return@mapNotNull null
                val label = labelOf(context, app.packageName)
                val identity = identifyManager(app.packageName, label)
                InstalledManager(app.packageName, label, identity.flavor, identity.spoofed)
            }.sortedWith(compareBy({ it.spoofed }, { it.label }))
        }.getOrDefault(emptyList())
    }

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
        val installed = installedFor(context, flavor)
        if (installed != null) {
            val launch = runCatching {
                context.packageManager.getLaunchIntentForPackage(installed.packageName)
            }.getOrNull()
            if (launch != null) {
                context.startActivity(launch)
                return
            }
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
     * The versions [flavor] has published, newest first, or the failure that stopped the listing.
     *
     * One request for the newest page rather than one per version, and the API rather than the atom feed
     * the payload sources read: the releases endpoint answers with the tag *and* the assets, which is the
     * same endpoint the lookup for a single version already uses - so a version chosen from a listing and
     * a version typed into the field are resolved by one piece of code, and a version that appears here
     * is a version that can be downloaded.
     *
     * Left as a failure rather than flattened to an empty list, because the two mean different things to
     * the screen that asked: no versions at all is a project that has published nothing, and a listing
     * that could not be read is a network or a rate limit, which is what the manual field is for.
     */
    fun availableVersions(flavor: KernelSuFlavor): Result<List<String>> =
        runCatching { managerVersionsInReleases(downloadText(releasesApiUrl(flavor))) }

    /**
     * The APK for one version, resolved through the releases API.
     *
     * Null when the version has no release, the release carries no APK, or the network refused - all
     * three are the same answer to the caller, which is that this version could not be turned into a
     * download. Going through the API is what lets a version be named at all: the asset's file name
     * carries a build number (`KernelSU_v3.2.5_32525-release.apk`) that the version does not.
     */
    fun resolve(context: Context, flavor: KernelSuFlavor, version: String): ManagerRelease? {
        val body = runCatching { downloadText(managerReleaseApiUrl(flavor, version)) }.getOrNull()
            ?: return null
        val apk = managerApkInRelease(body) ?: return null
        return ManagerRelease(flavor = flavor, version = version, url = apk)
    }

    /** Whether a package is installed and has something to open. */
    private fun isLaunchable(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        }.getOrDefault(false)

    private fun labelOf(context: Context, packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    /**
     * Whether an app carries the KernelSU daemon.
     *
     * Read from the app's own native library directory, which is where an installed manager's
     * `libksud.so` lands, and which covers both projects: their managers embed `ksud` the same way.
     */
    private fun carriesDaemon(info: ApplicationInfo): Boolean {
        val directory = info.nativeLibraryDir ?: return false
        return runCatching { File(directory, DAEMON_LIBRARY).isFile }.getOrDefault(false)
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

    private fun releasesApiUrl(flavor: KernelSuFlavor): String =
        "https://api.github.com/repos/${flavor.repository}/releases?per_page=$VERSION_LIST_LIMIT"

    /**
     * How many releases one listing asks for.
     *
     * The API answers newest first, so this is "the versions anyone would pick from" rather than all of
     * them - a project with a hundred releases has a decade of them, and a chooser that long is worse
     * than the field beside it for anything older.
     */
    private const val VERSION_LIST_LIMIT = 30

    /** The name every manager's embedded daemon has once it is installed. */
    private const val DAEMON_LIBRARY = "libksud.so"

    /** A releases answer is a few KB; the ceiling only bounds memory on a wrong URL. */
    private const val MAX_RELEASE_BYTES = 1024 * 1024
}
