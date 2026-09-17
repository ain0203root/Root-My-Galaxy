package dev.busung.s25uroot

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import rikka.shizuku.Shizuku
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.busung.s25uroot.ui.theme.RootMyGalaxyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val installViewModel by viewModels<InstallViewModel>()
    private var resumedOnce = false
    private var accentColor by mutableStateOf(AccentColor.Dynamic)
    private var themeMode by mutableStateOf(AppThemeMode.System)
    private var advancedMode by mutableStateOf(false)
	private var disableKsuModules by mutableStateOf(false)
    private var loadKernelSu by mutableStateOf(true)
    private var kernelsuFlavor by mutableStateOf(KernelSuFlavor.Default)
    private var shizukuMode by mutableStateOf(false)
    private var payloadSources by mutableStateOf<List<PayloadSource>>(emptyList())
    private var bootRootMode by mutableStateOf(false)
    private var armedRetry by mutableStateOf<ArmedRetry?>(null)

    /**
     * The settings card another screen asked this one to open on, or null.
     *
     * Held here rather than in the composition because it arrives with an intent - the run screen's
     * failure card hands one over - and an intent outlives the composition it landed in.
     */
    private var settingsTarget by mutableStateOf<String?>(null)

    /**
     * The payload an armed retry would run, read from the attempt that armed it.
     *
     * On screen because a retry runs the attempt that failed rather than whatever the app would pick on
     * its own, and that difference is invisible until after the reboot otherwise - by which point the
     * phone has already been rooted with something the user did not choose. Null when nothing was
     * recorded, which is a state worth naming too: the restart then falls back to the cached payload.
     */
    private var retryPayload by mutableStateOf<CachedPayload?>(null)
    private var restartAfterRoot by mutableStateOf(false)
    private var shizukuBootMode by mutableStateOf(false)
    private var bootSettleSeconds by mutableStateOf(BootSettle.DEFAULT_SECONDS)
    private var autoRootSettleSeconds by mutableStateOf(BootSettle.AUTO_ROOT_DEFAULT_SECONDS)
    private var runLimits by mutableStateOf(
        RunLimitsSettings(
            totalSeconds = RunLimits.DEFAULT_TOTAL_SECONDS,
            stallSeconds = RunLimits.DEFAULT_STALL_SECONDS,
            helperSeconds = RunLimits.DEFAULT_HELPER_SECONDS,
        ),
    )
    private var shizukuToken by mutableStateOf("")
    private var partitionReadOnly by mutableStateOf(false)
    private var payloadMode by mutableStateOf(PayloadMode.Online)
    private var notificationPermissionAsked = false
    private var batteryUnrestricted by mutableStateOf(false)
    private var batteryPromptAsked = false
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun isBatteryUnrestricted(): Boolean =
        getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(packageName)
            ?: true

    /**
     * Battery optimisation is the restriction that can quietly sink a run nobody is watching: in
     * Doze an unattended install loses its network and its process priority, which is exactly when
     * the boot service needs them. Asked once per install; the settings card that mirrors the same
     * state stays available if the prompt is declined or dismissed.
     */
    private fun maybeRequestBatteryExemption() {
        if (batteryPromptAsked) return
        batteryPromptAsked = true
        if (batteryUnrestricted || AppPreferences.batteryPromptShown(this)) return
        AppPreferences.setBatteryPromptShown(this, true)
        requestBatteryExemption()
    }

    /**
     * Lint's BatteryLife check keeps apps out of Play's battery-whitelist flow; this build ships
     * from GitHub releases, and the exemption is what lets an unattended run reach the network
     * with the screen off.
     */
    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        if (isBatteryUnrestricted()) {
            // Nothing left to ask for — the system dialog would no-op — so show where it can be
            // undone instead of leaving the card unresponsive.
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            return
        }
        val requested = runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }.isSuccess
        if (!requested) {
            // Vendor builds without the direct dialog still have the settings list.
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    /**
     * Asks for POST_NOTIFICATIONS once, when the boot option is switched on, so the foreground
     * service's progress notification is visible. Asking at launch instead would prompt people
     * who never enable the feature.
     */
    private fun maybeRequestNotificationPermission() {
        if (notificationPermissionAsked) return
        notificationPermissionAsked = true
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        accentColor = AppPreferences.accentColor(this)
        themeMode = AppPreferences.themeMode(this)
        advancedMode = AppPreferences.advancedMode(this)
		disableKsuModules = AppPreferences.disableKsuModules(this)
        loadKernelSu = AppPreferences.loadKernelSu(this)
        kernelsuFlavor = AppPreferences.kernelsuFlavor(this)
        shizukuMode = AppPreferences.shizukuMode(this)
        payloadSources = AppPreferences.payloadSources(this)
        bootRootMode = AppPreferences.bootRootMode(this)
        armedRetry = readArmedRetry()
        retryPayload = readArmedRetryPayload()
        restartAfterRoot = AppPreferences.restartAfterRoot(this)
        shizukuBootMode = AppPreferences.shizukuBootMode(this)
        bootSettleSeconds = AppPreferences.bootSettleSeconds(this)
        autoRootSettleSeconds = AppPreferences.autoRootSettleSeconds(this)
        runLimits = AppPreferences.runLimits(this)
        shizukuToken = AppPreferences.shizukuAutomationToken(this)
        partitionReadOnly = AppPreferences.partitionReadOnlyMode(this)
        payloadMode = AppPreferences.payloadMode(this)
        settingsTarget = SettingsTarget.named(intent?.getStringExtra(SettingsTarget.EXTRA))
        batteryUnrestricted = isBatteryUnrestricted()
        setContent {
            RootMyGalaxyTheme(accentColor = accentColor, themeMode = themeMode) {
                RootApp(
                    installViewModel = installViewModel,
                    accentColor = accentColor,
                    themeMode = themeMode,
                    advancedMode = advancedMode,
					disableKsuModules = disableKsuModules,
                    loadKernelSu = loadKernelSu,
                    kernelsuFlavor = kernelsuFlavor,
                    shizukuMode = shizukuMode,
                    payloadSources = payloadSources,
                    bootRootMode = bootRootMode,
                    armedRetry = armedRetry,
                    retryPayload = retryPayload,
                    restartAfterRoot = restartAfterRoot,
                    shizukuBootMode = shizukuBootMode,
                    bootSettleSeconds = bootSettleSeconds,
                    autoRootSettleSeconds = autoRootSettleSeconds,
                    runLimits = runLimits,
                    shizukuToken = shizukuToken,
                    partitionReadOnly = partitionReadOnly,
                    payloadMode = payloadMode,
                    batteryUnrestricted = batteryUnrestricted,
                    onStartArmedRetry = ::startArmedRetry,
                    onCancelArmedRetry = ::cancelArmedRetry,
                    requestNotificationPermission = ::maybeRequestNotificationPermission,
                    onRequestBatteryExemption = ::requestBatteryExemption,
                    onAccentColorChanged = { color ->
                        AppPreferences.setAccentColor(this, color)
                        accentColor = color
                    },
                    onThemeModeChanged = { mode ->
                        AppPreferences.setThemeMode(this, mode)
                        themeMode = mode
                    },
                    onAdvancedModeChanged = { enabled ->
                        AppPreferences.setAdvancedMode(this, enabled)
                        advancedMode = enabled
                    },
					onDisableKsuModulesChanged = { enabled ->
						AppPreferences.setDisableKsuModules(this, enabled)
						disableKsuModules = enabled
					},
                    onLoadKernelSuChanged = { enabled ->
                        AppPreferences.setLoadKernelSu(this, enabled)
                        loadKernelSu = enabled
                    },
                    onKernelsuFlavorChanged = { flavor ->
                        AppPreferences.setKernelsuFlavor(this, flavor)
                        kernelsuFlavor = flavor
                    },
                    // Stored per flavour, so naming one for KernelSU does not name one for
                    // KernelSU-Next as well - they are different projects with different versions.
                    onManagerVersionChanged = { version ->
                        AppPreferences.setManagerVersion(this, kernelsuFlavor, version)
                    },
                    onShizukuModeChanged = { enabled ->
                        AppPreferences.setShizukuMode(this, enabled)
                        shizukuMode = enabled
                    },
                    onPayloadSourcesChanged = { sources ->
                        AppPreferences.setPayloadSources(this, sources)
                        payloadSources = sources
                    },
                    onBootRootModeChanged = { enabled ->
                        AppPreferences.setBootRootMode(this, enabled)
                        bootRootMode = enabled
                        // Turning it off has to reach a gate that is already waiting, not just the
                        // next boot: a foreground service left running would install anyway.
                        if (!enabled) AutoRootService.stop(this)
                    },
                    onRestartAfterRootChanged = { enabled ->
                        AppPreferences.setRestartAfterRoot(this, enabled)
                        restartAfterRoot = enabled
                    },
                    onBootSettleChanged = { seconds ->
                        AppPreferences.setBootSettleSeconds(this, seconds)
                        bootSettleSeconds = seconds
                    },
                    onAutoRootSettleChanged = { seconds ->
                        AppPreferences.setAutoRootSettleSeconds(this, seconds)
                        autoRootSettleSeconds = seconds
                    },
                    onRunLimitChanged = { limit, seconds ->
                        AppPreferences.setRunLimit(this, limit, seconds)
                        // Read back rather than patched in place, so a stored value that was normalized
                        // on the way in is what the row shows.
                        runLimits = AppPreferences.runLimits(this)
                    },
                    onShizukuTokenChanged = { token ->
                        AppPreferences.setShizukuAutomationToken(this, token)
                        shizukuToken = token
                    },
                    onPartitionReadOnlyChanged = { enabled ->
                        AppPreferences.setPartitionReadOnlyMode(this, enabled)
                        partitionReadOnly = enabled
                    },
                    onPayloadModeChanged = { mode ->
                        AppPreferences.setPayloadMode(this, mode)
                        payloadMode = mode
                    },
                    onForgetCachedPayload = {
                        // Says nothing on success: the row it was pressed from already shows
                        // "Nothing cached yet" once this returns.
                        KnownGoodPayloadStore.clear(this)
                    },
                    onShizukuBootModeChanged = { enabled ->
                        AppPreferences.setShizukuBootMode(this, enabled)
                        shizukuBootMode = enabled
                    },
                    openInstaller = ::openInstaller,
                    settingsTarget = settingsTarget,
                    onSettingsTargetHandled = { settingsTarget = null },
                )
            }
        }
        maybeRequestBatteryExemption()
    }

    /**
     * A target that arrives while this activity is already open.
     *
     * The run screen's failure card is the one caller that does this, and it asks for the existing
     * window rather than a second one: the settings page it wants is in the activity that is already
     * in the back stack, and a fresh instance would put the app's own screens on top of each other.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        settingsTarget = SettingsTarget.named(intent.getStringExtra(SettingsTarget.EXTRA))
    }

    private fun openInstaller(selectionId: String? = null) {
        val installer = Intent(this, InstallActivity::class.java)
            .putExtra(InstallActivity.EXTRA_INSTALL_REQUEST_ID, UUID.randomUUID().toString())
        if (selectionId != null) {
            installer.putExtra(InstallActivity.EXTRA_PROFILE_ID, selectionId)
        }
        startActivity(installer)
    }

    /** The retry this device has armed, read together with the boot it would run in. */
    private fun readArmedRetry(): ArmedRetry? =
        ArmedRetry.of(AppPreferences.retryArmedInBoot(this), kernelBootToken())

    /**
     * The payload the armed retry would run, or null when there is no retry or nothing was recorded.
     *
     * Read only while a retry is armed: the record outlives the retry it was made for, and showing a
     * payload on a screen with no retry on it would imply the next boot was about to run something.
     */
    private fun readArmedRetryPayload(): CachedPayload? =
        if (AppPreferences.retryArmed(this)) AttemptedPayloadStore.describe(this) else null

    /**
     * Starts the install a retry was armed for.
     *
     * Offered on Home rather than taken on the way in, which is what it used to be: the app started the
     * run itself the first time it was opened after the boot the retry was armed for, so a phone could
     * begin installing because its owner opened the app to look at something else. The retry is still
     * theirs to take - it is just a tap now, and the screen says what it is for.
     *
     * Consumed before the install starts, so a process death mid-run cannot leave an armed retry that
     * would start another one on the next launch.
     */
    private fun startArmedRetry() {
        AppPreferences.setRetryAfterReboot(this, null)
        armedRetry = null
        retryPayload = null
        openInstaller()
    }

    /** Takes the retry back, which is the only way to stop it running at the next boot. */
    private fun cancelArmedRetry() {
        AppPreferences.setRetryAfterReboot(this, null)
        armedRetry = null
        retryPayload = null
    }

    override fun onResume() {
        super.onResume()
        // Battery optimisation is a system setting, so it can change while the app is backgrounded.
        batteryUnrestricted = isBatteryUnrestricted()
        // A retry is armed on the run screen and consumed by a boot, so coming back from either is
        // exactly when this screen can be wrong about it.
        armedRetry = readArmedRetry()
        retryPayload = readArmedRetryPayload()
        if (resumedOnce) installViewModel.refresh() else resumedOnce = true
    }
}

private enum class AppPage(@StringRes val label: Int, val icon: ImageVector) {
    Overview(R.string.nav_overview, Icons.Rounded.Home),
    History(R.string.nav_history, Icons.Rounded.History),
    Logs(R.string.nav_logs, Icons.Rounded.Terminal),
    Settings(R.string.nav_settings, Icons.Rounded.Settings),
}

private data class LanguageOption(@StringRes val label: Int, val tag: String)

private enum class CompatibilityWarning {
    Device,
    KernelVersion,
}

private val languageOptions = listOf(
    LanguageOption(R.string.language_system, ""),
    LanguageOption(R.string.language_korean, "ko"),
    LanguageOption(R.string.language_english, "en"),
    LanguageOption(R.string.language_german, "de"),
    LanguageOption(R.string.language_japanese, "ja"),
    LanguageOption(R.string.language_chinese, "zh-CN"),
    LanguageOption(R.string.language_chinese_traditional, "zh-TW"),
    LanguageOption(R.string.language_turkish, "tr"),
    LanguageOption(R.string.language_brazillian_portuguese, "pt-BR"),
    LanguageOption(R.string.language_russian, "ru"),
    LanguageOption(R.string.language_vietnamese, "vi"),
    LanguageOption(R.string.language_uzbek, "uz"),
)

/**
 * The KernelSU project's own documentation, which is about KernelSU rather than one flavour.
 *
 * What the app installs is per flavour and lives in [KernelSuFlavor]; this is only the link offered
 * beside the general explanation, and it stays the upstream project's page for both.
 */
private const val KERNEL_SU_HOME_URL = "https://kernelsu.org/"
private const val SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.manager"
private const val SHIZUKU_MANAGER_URL = "https://github.com/thedjchi/Shizuku/releases/"

private fun openShizukuManager(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_MANAGER_PACKAGE)
    if (launch != null) {
        context.startActivity(launch)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_MANAGER_URL)))
    }
}

@Composable
private fun RootApp(
    installViewModel: InstallViewModel,
    accentColor: AccentColor,
    themeMode: AppThemeMode,
    advancedMode: Boolean,
	disableKsuModules: Boolean,
    loadKernelSu: Boolean,
    kernelsuFlavor: KernelSuFlavor,
    shizukuMode: Boolean,
    payloadSources: List<PayloadSource>,
    bootRootMode: Boolean,
    armedRetry: ArmedRetry?,
    retryPayload: CachedPayload?,
    restartAfterRoot: Boolean,
    shizukuBootMode: Boolean,
    bootSettleSeconds: Int,
    autoRootSettleSeconds: Int,
    runLimits: RunLimitsSettings,
    shizukuToken: String,
    partitionReadOnly: Boolean,
    payloadMode: PayloadMode,
    batteryUnrestricted: Boolean,
    onStartArmedRetry: () -> Unit,
    onCancelArmedRetry: () -> Unit,
    onAccentColorChanged: (AccentColor) -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onAdvancedModeChanged: (Boolean) -> Unit,
	onDisableKsuModulesChanged: (Boolean) -> Unit,
    onLoadKernelSuChanged: (Boolean) -> Unit,
    onKernelsuFlavorChanged: (KernelSuFlavor) -> Unit,
    onManagerVersionChanged: (String) -> Unit,
    onShizukuModeChanged: (Boolean) -> Unit,
    onPayloadSourcesChanged: (List<PayloadSource>) -> Unit,
    onBootRootModeChanged: (Boolean) -> Unit,
    onRestartAfterRootChanged: (Boolean) -> Unit,
    onShizukuBootModeChanged: (Boolean) -> Unit,
    onBootSettleChanged: (Int) -> Unit,
    onAutoRootSettleChanged: (Int) -> Unit,
    onRunLimitChanged: (RunLimit, Int) -> Unit,
    onShizukuTokenChanged: (String) -> Unit,
    onPartitionReadOnlyChanged: (Boolean) -> Unit,
    onPayloadModeChanged: (PayloadMode) -> Unit,
    onForgetCachedPayload: () -> Unit,
    requestNotificationPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    openInstaller: (String?) -> Unit,
    /** A settings card another screen asked this one to open on, or null. */
    settingsTarget: String?,
    onSettingsTargetHandled: () -> Unit,
) {
    val installState by installViewModel.state.collectAsStateWithLifecycle()
    val history by installViewModel.history.collectAsStateWithLifecycle()
    val targetCatalog by installViewModel.targetCatalog.collectAsStateWithLifecycle()
    var selectedPage by remember { mutableStateOf(AppPage.Overview) }
    // A card to open on is only reachable from the settings page, so the page comes first and the jump
    // is left to the page itself: it is the only thing that knows where its own rows are.
    LaunchedEffect(settingsTarget) {
        if (settingsTarget != null) selectedPage = AppPage.Settings
    }
    var showInstallConfirmation by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf<TargetProfile?>(null) }
    var compatibilityWarning by remember { mutableStateOf<CompatibilityWarning?>(null) }
    val device = remember { DeviceSnapshot.current() }
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }
    var updateCardDismissed by remember { mutableStateOf(false) }
    // The updater stands down while a run is in flight, and says so when it is asked.
    //
    // A run's delicate part is the payload's own timing, and an update check or a download beside it is
    // network, CPU and - if it succeeds - a package install prompt, none of which the run asked for. The
    // validated baseline this app's runtime is measured against keeps its own updater inert for exactly
    // this reason; keeping the feature and refusing to use it during a run is the narrower version of
    // that, and the honest one for an app that does ship update notifications.
    val runInFlight = installState.busy
    var updateRefusedDuringRun by remember { mutableStateOf(false) }
    val checkForUpdate: () -> Unit = {
        if (runInFlight) {
            updateRefusedDuringRun = true
        } else if (!updateStatus.busy) {
            updateStatus = UpdateStatus.Checking
            scope.launch {
                val info = AppUpdater.fetchLatestRelease()
                updateStatus = when {
                    info == null -> UpdateStatus.Failed
                    AppUpdater.isUpdateAvailable(info.versionName, BuildConfig.VERSION_NAME) ->
                        UpdateStatus.Available(info)
                    else -> UpdateStatus.UpToDate
                }
            }
        }
    }
    // What the cache holds, kept here because the plan needs it and the plan is built synchronously.
    // Reloaded whenever a run's phase changes, since a finished run is what publishes a cache entry.
    var cachedPayload by remember { mutableStateOf<CachedPayload?>(null) }
    LaunchedEffect(installState.phase) {
        cachedPayload = withContext(Dispatchers.IO) { KnownGoodPayloadStore.describe(context) }
    }
    // Built on demand rather than on every recomposition: it reads the boot id to report the
    // cached offset, and only the run-plan dialog needs it.
    val runPlan: () -> RunPlanDisplay = {
        // Offline mode resolves nothing: the run it is about to start is the cached payload, so the
        // plan describes that one. Reading the catalog here would describe a run the mode exists to
        // avoid, and an offline plan that named some other target's source would be a plan about
        // another app's run.
        val resolved = if (payloadMode == PayloadMode.Offline) {
            cachedPayload?.profile()
        } else {
            targetCatalog.profiles.resolveFor(device)
        }
        val freshSession = resolved?.requiresFreshP0Session == true
        val cachedOffset = installViewModel.cachedOffsetForThisBoot()
        RunPlanDisplay(
            deviceLabel = "${device.model} \u00b7 ${device.kernelRelease}",
            targetLabel = resolved?.let { "${it.displayName} (${it.profileId})" },
            // The revision is named when the catalog resolved one, because "which branch" and "which
            // revision of it" are different answers and only the second one is reproducible.
            sourceLabel = resolved?.sourceLabel?.takeIf(String::isNotBlank)?.let { label ->
                resolved.sourceCommit.takeIf(String::isNotBlank)
                    ?.let { "$label @ ${it.take(7)}" }
                    ?: label
            },
            unresolvedNote = if (resolved != null) {
                null
            } else when {
                payloadMode == PayloadMode.Offline ->
                    context.getString(R.string.run_plan_offline_no_cache)
                targetCatalog.loading -> context.getString(R.string.run_plan_catalog_loading)
                targetCatalog.error != null -> targetCatalog.error
                targetCatalog.profiles.isEmpty() -> context.getString(R.string.run_plan_catalog_empty)
                else -> context.getString(R.string.run_plan_no_target)
            },
            freshSession = freshSession,
            shizuku = shizukuMode,
            payloadMode = payloadMode,
            partitionReadOnly = partitionReadOnly,
            cachedOffset = cachedOffset,
            // The profile's own policy, so the preview shows the environment the run will get.
            plan = InstallViewModel.exploitPlan(
                freshSession,
                cachedOffset,
                shizukuMode,
                resolved?.routePolicy ?: ExploitRoutePolicy.LEGACY,
                AppPreferences.bootSettleSeconds(context),
                // The same resolution a run performs, from the same stored values: a plan that showed
                // the defaults while the run enforced the user's choices would be a plan about another
                // app's run.
                RunLimits.resolve(AppPreferences.runLimits(context), freshSession),
            ),
        )
    }
    val startDownload: (UpdateInfo) -> Unit = { info ->
        val apkUrl = info.apkUrl
        if (runInFlight) {
            updateRefusedDuringRun = true
        } else if (apkUrl == null) {
            AppUpdater.openReleasesPage(context)
        } else {
            updateStatus = UpdateStatus.Downloading(info, 0f)
            scope.launch {
                val apk = AppUpdater.downloadApk(context, apkUrl) { progress ->
                    updateStatus = UpdateStatus.Downloading(info, progress)
                }
                if (apk == null || !AppUpdater.installApk(context, apk)) {
                    Toast.makeText(context, context.getString(R.string.updater_download_failed), Toast.LENGTH_SHORT).show()
                    AppUpdater.openReleasesPage(context)
                }
                updateStatus = UpdateStatus.Available(info)
            }
        }
    }
    // Not asked for at all while a run is in flight: an automatic check the user did not request is the
    // last thing that should reach the network next to an exploit, and the card can wait for the run.
    LaunchedEffect(Unit) { if (!installState.busy) checkForUpdate() }

    if (updateRefusedDuringRun) {
        AlertDialog(
            onDismissRequest = { updateRefusedDuringRun = false },
            icon = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(stringResource(R.string.updater_run_in_progress_title))
            },
            text = { Text(stringResource(R.string.updater_run_in_progress)) },
            confirmButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    updateRefusedDuringRun = false
                }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    if (showTargetPicker) {
        TargetSelectionSheet(
            device = device,
            catalog = targetCatalog,
            onDismiss = { showTargetPicker = false },
            onRetry = installViewModel::loadTargetCatalog,
            onNext = { profile ->
                selectedProfile = profile
                showTargetPicker = false
                compatibilityWarning = when {
                    !profile.matchesDevice(device) -> CompatibilityWarning.Device
                    !profile.matchesKernelVersion(device) -> CompatibilityWarning.KernelVersion
                    else -> null
                }
                if (compatibilityWarning == null) showInstallConfirmation = true
            },
        )
    }

    compatibilityWarning?.let { warning ->
        val profile = selectedProfile ?: return@let
        AlertDialog(
            onDismissRequest = {
                compatibilityWarning = null
                showTargetPicker = true
            },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(
                    stringResource(when (warning) {
                        CompatibilityWarning.Device -> R.string.device_mismatch_title
                        CompatibilityWarning.KernelVersion -> R.string.kernel_version_mismatch_title
                    }),
                )
            },
            text = {
                Text(
                    when (warning) {
                        CompatibilityWarning.Device -> stringResource(
                            R.string.device_mismatch_body,
                            device.model,
                            profile.supportedModels,
                        )
                        CompatibilityWarning.KernelVersion -> stringResource(
                            R.string.kernel_version_mismatch_body,
                            device.kernelVersion,
                            profile.supportedKernelVersions,
                        )
                    },
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        clickHaptic(view)
                        compatibilityWarning = when (warning) {
                            CompatibilityWarning.Device -> if (!profile.matchesKernelVersion(device)) {
                                CompatibilityWarning.KernelVersion
                            } else {
                                null
                            }
                            CompatibilityWarning.KernelVersion -> null
                        }
                        if (compatibilityWarning == null) {
                            showInstallConfirmation = true
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clickHaptic(view)
                        compatibilityWarning = null
                        showTargetPicker = true
                    },
                ) {
                    Text(stringResource(R.string.action_back))
                }
            },
        )
    }

    if (showInstallConfirmation) {
        AlertDialog(
            onDismissRequest = { showInstallConfirmation = false },
            icon = { Icon(Icons.Rounded.Security, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(stringResource(R.string.install_confirm_title))
            },
            text = { Text(stringResource(R.string.install_confirm_body)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    clickHaptic(view)
                    showInstallConfirmation = false
                    openInstaller(selectedProfile?.selectionId)
                    selectedProfile = null
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    showInstallConfirmation = false
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                AppPage.entries.forEach { page ->
                    NavigationBarItem(
                        selected = selectedPage == page,
                        onClick = {
                            clickHaptic(view)
                            selectedPage = page
                        },
                        modifier = Modifier.padding(top = 4.dp),
                        icon = { Icon(page.icon, contentDescription = null) },
                        label = { Text(stringResource(page.label)) },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        AnimatedContent(targetState = selectedPage, label = "page") { page ->
            when (page) {
                AppPage.Overview -> OverviewPage(
                    padding = padding,
                    device = device,
                    installState = installState,
                    armedRetry = armedRetry,
                    retryPayload = retryPayload,
                    updateStatus = updateStatus,
                    updateCardDismissed = updateCardDismissed,
                    onDismissUpdateCard = { updateCardDismissed = true },
                    onStartDownload = startDownload,
                    onStartArmedRetry = onStartArmedRetry,
                    onCancelArmedRetry = onCancelArmedRetry,
                    onOpenSettings = { selectedPage = AppPage.Settings },
                    onInstall = {
                        selectedProfile = null
                        if (advancedMode) {
                            showTargetPicker = true
                            installViewModel.loadTargetCatalog()
                        } else {
                            showInstallConfirmation = true
                        }
                    },
                )
                AppPage.History -> HistoryPage(
                    padding,
                    history,
                    onDeleteEntries = installViewModel::deleteHistoryEntries,
                )
                AppPage.Logs -> LogsPage(padding)
                AppPage.Settings -> SettingsPage(
                    padding = padding,
                    device = device,
                    accentColor = accentColor,
                    themeMode = themeMode,
                    advancedMode = advancedMode,
					disableKsuModules = disableKsuModules,
                    loadKernelSu = loadKernelSu,
                    kernelsuFlavor = kernelsuFlavor,
                    shizukuMode = shizukuMode,
                    payloadSources = payloadSources,
                    bootRootMode = bootRootMode,
                    restartAfterRoot = restartAfterRoot,
                    shizukuBootMode = shizukuBootMode,
                    bootSettleSeconds = bootSettleSeconds,
                    autoRootSettleSeconds = autoRootSettleSeconds,
                    runLimits = runLimits,
                    shizukuToken = shizukuToken,
                    partitionReadOnly = partitionReadOnly,
                    payloadMode = payloadMode,
                    batteryUnrestricted = batteryUnrestricted,
                    updateStatus = updateStatus,
                    onCheckForUpdate = checkForUpdate,
                    onStartDownload = startDownload,
                    onAccentColorChanged = onAccentColorChanged,
                    onThemeModeChanged = onThemeModeChanged,
                    onAdvancedModeChanged = onAdvancedModeChanged,
					onDisableKsuModulesChanged = onDisableKsuModulesChanged,
                    onLoadKernelSuChanged = onLoadKernelSuChanged,
                    onKernelsuFlavorChanged = onKernelsuFlavorChanged,
                    onManagerVersionChanged = onManagerVersionChanged,
                    onShizukuModeChanged = onShizukuModeChanged,
                    onPayloadSourcesChanged = onPayloadSourcesChanged,
                    onBootRootModeChanged = onBootRootModeChanged,
                    onRestartAfterRootChanged = onRestartAfterRootChanged,
                    onShizukuBootModeChanged = onShizukuBootModeChanged,
                    onBootSettleChanged = onBootSettleChanged,
                    onAutoRootSettleChanged = onAutoRootSettleChanged,
                    onRunLimitChanged = onRunLimitChanged,
                    onShizukuTokenChanged = onShizukuTokenChanged,
                    onPartitionReadOnlyChanged = onPartitionReadOnlyChanged,
                    onPayloadModeChanged = onPayloadModeChanged,
                    onForgetCachedPayload = onForgetCachedPayload,
                    onRequestNotificationPermission = requestNotificationPermission,
                    onRequestBatteryExemption = onRequestBatteryExemption,
                    runPlan = runPlan,
                    openTarget = settingsTarget,
                    onOpenTargetHandled = onSettingsTargetHandled,
                )
            }
        }
    }
}

@Composable
private fun AppVersionText(
    style: TextStyle,
    color: Color,
) {
    Text(
        text = stringResource(
            R.string.version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        ),
        style = style,
        color = color,
    )
}

private fun clickHaptic(view: View) {
    view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        },
    )
}

@Composable
private fun DialogDimAmount(amount: Float) {
    val window = (LocalView.current.parent as DialogWindowProvider).window
    SideEffect { window.setDimAmount(amount) }
}

@Composable
private fun OverviewPage(
    padding: PaddingValues,
    device: DeviceSnapshot,
    installState: InstallUiState,
    armedRetry: ArmedRetry?,
    retryPayload: CachedPayload?,
    updateStatus: UpdateStatus,
    updateCardDismissed: Boolean,
    onDismissUpdateCard: () -> Unit,
    onStartDownload: (UpdateInfo) -> Unit,
    onStartArmedRetry: () -> Unit,
    onCancelArmedRetry: () -> Unit,
    onInstall: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    // Read live, because these change without this screen doing anything: Shizuku hands out its binder
    // after it starts, a grant can be made or revoked in the Shizuku app, KernelSU is loaded per boot,
    // and a manager app can be installed or removed - and the one thing that changes all of them at
    // once is a run finishing, which is why the phase is a key below. Started from the cheap readings
    // so the card is never blank, then refined.
    var readiness by remember {
        mutableStateOf(
            Readiness(
                kernelSu = if (RootStatusProbe.isActiveQuick()) {
                    KernelSuStatus.Active
                } else {
                    KernelSuStatus.Unreadable
                },
                shizuku = ShizukuController.availability(),
                // The package walk is the one reading that is not cheap, so the card opens saying
                // "nothing found" and is corrected a moment later rather than holding up the screen.
                managers = ManagerPresence(),
            ),
        )
    }
    var resumeTick by remember { mutableStateOf(0) }
    LaunchedEffect(installState.phase, resumeTick) {
        // Off the main thread: the KernelSU reading may start `su`, and finding the manager apps walks
        // the package list - neither is worth a frozen frame.
        readiness = withContext(Dispatchers.IO) {
            Readiness(
                kernelSu = KernelSuRuntime.status(),
                shizuku = ShizukuController.availability(),
                // A run can install a manager's daemon without the manager app being present, and coming
                // back from one is when that changes - which is why the phase is a key above.
                managers = ManagerPresence.of(KernelSuManager.installedManagers(context)),
            )
        }
    }
    DisposableEffect(Unit) {
        val received = Shizuku.OnBinderReceivedListener {
            readiness = readiness.copy(shizuku = ShizukuController.availability())
        }
        val dead = Shizuku.OnBinderDeadListener {
            readiness = readiness.copy(shizuku = ShizukuController.availability())
        }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Coming back from the Shizuku app is when a grant may have changed, and that is the one
            // change Shizuku sends no callback for. A tick rather than a read here, so the work still
            // happens off the main thread.
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 54.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(modifier = Modifier.weight(1f))
                AppVersionText(
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            }
        }
        if (
            !updateCardDismissed &&
            updateStatus.info != null &&
            // While a run is in flight the card is off the screen rather than unresponsive: an offer to
            // update is not something to weigh next to a run, and a dimmed button would only invite a tap
            // that cannot be honoured.
            !installState.busy
        ) {
            item {
                UpdateCard(
                    status = updateStatus,
                    onDismiss = onDismissUpdateCard,
                    onStartDownload = onStartDownload,
                )
            }
        }
        item { InstallStatusCard(installState, onInstall) }
        // Above the readiness card, and above everything else that is only information: this one is
        // waiting on a decision, and it changes what the next boot does.
        if (armedRetry != null && !installState.busy) {
            item {
                ArmedRetryCard(
                    retry = armedRetry,
                    payload = retryPayload,
                    onStart = onStartArmedRetry,
                    onCancel = onCancelArmedRetry,
                )
            }
        }
        item { ReadinessCard(readiness, onOpenSettings) }
        item { DeviceCard(device) }
        item { HowItWorksCard() }
    }
}

private sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateStatus
    data object UpToDate : UpdateStatus
    data object Failed : UpdateStatus
}

private val UpdateStatus.busy: Boolean
    get() = this is UpdateStatus.Checking || this is UpdateStatus.Downloading

private val UpdateStatus.info: UpdateInfo?
    get() = when (this) {
        is UpdateStatus.Available -> this.info
        is UpdateStatus.Downloading -> this.info
        else -> null
    }

@Composable
private fun UpdateCard(
    status: UpdateStatus,
    onDismiss: () -> Unit,
    onStartDownload: (UpdateInfo) -> Unit,
) {
    val view = LocalView.current
    val info = status.info
    if (info == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.updater_available_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        clickHaptic(view)
                        onDismiss()
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.updater_available_body, info.versionName),
                style = MaterialTheme.typography.bodyMedium,
            )
            when (status) {
                is UpdateStatus.Downloading -> {
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = LocalContentColor.current,
                        trackColor = LocalContentColor.current.copy(alpha = 0.2f),
                        drawStopIndicator = {},
                    )
                    Text(
                        text = stringResource(R.string.updater_downloading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.78f),
                    )
                }
                else -> {
                    FilledTonalButton(onClick = {
                        clickHaptic(view)
                        onStartDownload(info)
                    }) {
                        Text(stringResource(R.string.updater_button_download))
                    }
                }
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.how_it_works), style = MaterialTheme.typography.titleMedium)
            installerSteps.forEach { step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(step.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(step.title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(step.detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The one-shot retry, on screen from the moment it is armed until it is used or cancelled.
 *
 * This card is the whole answer to "root on boot was off, and the phone installed anyway". An armed
 * retry overrides that setting by design - the gate's rule is that either way of asking gets the boot's
 * single attempt - and while it was invisible the only conclusion left was that the setting had been
 * ignored. It is not a new promise: the retry ran before and runs now. It can just be seen, taken, and
 * taken back.
 *
 * The two states differ in what it is honest to offer. Before the reboot, only cancelling: the run was
 * armed *because* the user chose a restart over an immediate retry, so offering to start it now would
 * be offering the attempt they turned down. After the reboot, starting it is the one thing left - the
 * boot gate runs it by itself only where a cached payload exists to run from, and the device whose last
 * run failed is the device with no cache.
 */
@Composable
private fun ArmedRetryCard(
    retry: ArmedRetry,
    payload: CachedPayload?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val view = LocalView.current
    // Where the payload came from, when the record says. The commit is shown short, the way the history
    // shows it: what a pinned source means is "these bytes", and seven characters are enough to answer
    // "is this the revision I pinned?".
    val payloadSource = payload?.takeIf { it.sourceLabel.isNotBlank() }?.let { attempted ->
        if (attempted.sourceCommit.isBlank()) {
            stringResource(R.string.retry_armed_payload_from, attempted.sourceLabel)
        } else {
            stringResource(
                R.string.retry_armed_payload_from_at,
                attempted.sourceLabel,
                attempted.sourceCommit.take(7),
            )
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            if (retry.afterReboot) {
                                R.string.retry_armed_pending_title
                            } else {
                                R.string.retry_armed_next_boot_title
                            },
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(
                            if (retry.afterReboot) {
                                R.string.retry_armed_pending_body
                            } else {
                                R.string.retry_armed_next_boot_body
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // Named before the restart, not only in the log afterwards: what a retry runs is the
                    // attempt that failed, which is not necessarily the payload the app would choose by
                    // itself - and on a device somebody is testing payloads on, that is the whole point.
                    if (payload != null) {
                        Text(
                            stringResource(
                                R.string.retry_armed_payload,
                                payload.displayName.ifBlank { payload.profileId },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        payloadSource?.let { source ->
                            Text(source, style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Text(
                            stringResource(R.string.retry_armed_payload_none),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    onClick = {
                        clickHaptic(view)
                        onCancel()
                    },
                ) {
                    Text(stringResource(R.string.retry_armed_cancel))
                }
                if (retry.afterReboot) {
                    Button(
                        onClick = {
                            clickHaptic(view)
                            onStart()
                        },
                    ) {
                        Text(stringResource(R.string.retry_armed_start))
                    }
                }
            }
        }
    }
}

/**
 * One version in the manager chooser.
 *
 * The default is marked rather than given a row of its own, because it is a version like any other: what
 * makes it the default is that it is what the app installs when nothing is named, and a user picking it
 * is asking for that same thing.
 */
@Composable
private fun ManagerVersionRow(
    version: String,
    isDefault: Boolean,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                clickHaptic(view)
                onPick()
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            version,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (isDefault) {
            Text(
                stringResource(R.string.settings_manager_versions_default),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** How tall the version list may grow before it scrolls, so the dialog still fits a short screen. */
private val MANAGER_VERSION_LIST_MAX = 220.dp

@Composable
private fun InstallStatusCard(installState: InstallUiState, onInstall: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val uriHandler = LocalUriHandler.current
    // Read here rather than passed in, because the card has to name whichever KernelSU the app is set
    // to: the two managers are different apps with different packages and only one of them is the one
    // a finished run leaves needing to be opened.
    val managerFlavor = remember(installState) { AppPreferences.kernelsuFlavor(context) }
    val managerInstalled = remember(installState, managerFlavor) {
        KernelSuManager.isInstalled(context, managerFlavor)
    }
    Card(
        onClick = {
            clickHaptic(view)
            when {
                installState.busy -> Unit
                installState.phase == InstallPhase.Installed -> {
                    // A named version that is not the default has to be looked up before there is a
                    // download to offer, so the card says what it is doing rather than appearing to
                    // ignore the tap.
                    KernelSuManager.open(context, managerFlavor) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
                else -> onInstall()
            }
        },
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = expressiveClickableCardShape(interactionSource),
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                installState.busy -> LoadingIndicator(
                    modifier = Modifier.size(44.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                installState.phase == InstallPhase.Installed -> Icon(
                    Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(44.dp),
                )
                installState.phase == InstallPhase.RootOnly -> Icon(
                    Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(44.dp),
                )
                installState.phase == InstallPhase.Failed -> Icon(
                    Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(44.dp),
                )
                else -> Icon(
                    Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(44.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                if (installState.phase == InstallPhase.Installed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_kernelsu),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.status_ksu_active),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    Text(
                        text = when (installState.phase) {
                            InstallPhase.Ready -> stringResource(R.string.status_not_installed)
                            else -> installState.message
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = when (installState.phase) {
                        InstallPhase.Installed -> stringResource(
                            if (managerInstalled) {
                                R.string.install_tap_open_manager
                            } else {
                                R.string.install_tap_manager
                            },
                        )
                        InstallPhase.Failed -> stringResource(R.string.install_tap_retry)
                        else -> stringResource(R.string.install_tap_start)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The two things a run needs, on the screen the app opens on.
 *
 * Both are facts about the device rather than about this app's settings, which is exactly why they
 * belong here: *is KernelSU loaded this boot* and *can this app use Shizuku* decide what a run can even
 * attempt, and answering them by opening Settings and reading two rows about preferences is the long way
 * round. The rows lead there anyway, because a state that is wrong is something the user will want to
 * fix rather than merely know.
 *
 * KernelSU is loaded **per boot**, so this is a statement about the current boot and not about the
 * device's history - a phone that was rooted yesterday reads as not loaded, which is the reason root on
 * boot exists.
 */
@Composable
private fun ReadinessCard(readiness: Readiness, onOpenSettings: () -> Unit) {
    val view = LocalView.current
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.readiness), style = MaterialTheme.typography.titleMedium)
            InfoRow(
                icon = Icons.Rounded.Security,
                label = stringResource(R.string.readiness_kernelsu),
                value = stringResource(
                    when (readiness.kernelSu) {
                        KernelSuStatus.Active -> R.string.readiness_ksu_active
                        KernelSuStatus.NotLoaded -> R.string.readiness_ksu_not_loaded
                        KernelSuStatus.Unreadable -> R.string.readiness_ksu_unreadable
                    },
                ),
                onClick = {
                    clickHaptic(view)
                    onOpenSettings()
                },
            )
            InfoRow(
                icon = Icons.Rounded.VerifiedUser,
                label = stringResource(R.string.readiness_shizuku),
                value = stringResource(
                    when (readiness.shizuku) {
                        ShizukuAvailability.Ready -> R.string.settings_shizuku_state_running
                        ShizukuAvailability.WithoutPermission ->
                            R.string.settings_shizuku_state_needs_permission
                        ShizukuAvailability.NotRunning -> R.string.readiness_shizuku_not_running
                    },
                ),
                onClick = {
                    clickHaptic(view)
                    onOpenSettings()
                },
            )
            ManagerRow(
                label = stringResource(R.string.readiness_manager_kernelsu),
                installed = readiness.managers.kernelsu,
                onClick = onOpenSettings,
            )
            ManagerRow(
                label = stringResource(R.string.readiness_manager_next),
                installed = readiness.managers.kernelsuNext,
                onClick = onOpenSettings,
            )
        }
    }
}

/**
 * Whether a manager app is on the phone, which is not the same question as whether root is loaded.
 *
 * Both are worth a line because the states they describe need different things done about them: root
 * with no manager is a phone that cannot be managed without installing one, and a manager with no root
 * is a phone whose install has not been run yet - or whose manager is simply the other flavour's.
 */
@Composable
private fun ManagerRow(label: String, installed: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    InfoRow(
        icon = Icons.Rounded.SystemUpdate,
        label = label,
        value = stringResource(
            if (installed) R.string.readiness_installed else R.string.readiness_not_installed,
        ),
        onClick = {
            clickHaptic(view)
            onClick()
        },
    )
}

@Composable
private fun DeviceCard(device: DeviceSnapshot) {
    val view = LocalView.current
    var kernelExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InfoRow(Icons.Rounded.Memory, stringResource(R.string.device), "${device.manufacturer} ${device.model} (${device.device})")
            InfoRow(Icons.Rounded.Code, stringResource(R.string.firmware), device.buildId)
            InfoRow(Icons.Rounded.Info, stringResource(R.string.system), "Android ${device.androidRelease} (API ${device.sdk})")
            InfoRow(
                icon = Icons.Rounded.Info,
                label = stringResource(R.string.kernel),
                value = if (kernelExpanded) device.kernelVersionFull else device.kernelRelease,
                onClick = {
                    clickHaptic(view)
                    kernelExpanded = !kernelExpanded
                },
            )
            InfoRow(Icons.Rounded.Security, stringResource(R.string.system_abi), "${device.abi} (${device.pageSize / 1024}K)")
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
        } else {
            Modifier
        },
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryPage(
    padding: PaddingValues,
    history: List<InstallHistoryEntry>,
    onDeleteEntries: (Set<String>) -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    var selectedHistoryId by remember { mutableStateOf<String?>(null) }
    var selectionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteIds by remember { mutableStateOf<Set<String>?>(null) }
    // saveable: picking a destination starts another activity, which can recreate this one while the
    // picker is up, and the ids are the only record of what the export was for.
    var pendingExportIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val exportLogsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val ids = pendingExportIds.toSet()
        pendingExportIds = arrayListOf()
        result.data?.data?.let { uri ->
            val entries = history.filter { it.id in ids && it.result != InstallRunResult.Running }
            if (entries.isNotEmpty()) HistoryLogExporter.saveArchive(context, uri, entries)
        }
    }
    val launchExport: (Set<String>) -> Unit = { ids ->
        // A run that is still going has no finished log to archive, so it cannot be part of one.
        val entries = history.filter { it.id in ids && it.result != InstallRunResult.Running }
        if (entries.isNotEmpty()) {
            pendingExportIds = ArrayList(entries.map { it.id })
            exportLogsLauncher.launch(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, HistoryLogExporter.archiveFileName(entries))
                },
            )
        }
    }
    val selectedEntry = history.firstOrNull { it.id == selectedHistoryId }
    val selectableIds = history
        .filter { it.result != InstallRunResult.Running }
        .map { it.id }
        .toSet()
    val selecting = selectionIds.isNotEmpty()
    BackHandler(enabled = selectedEntry != null || selecting) {
        if (selecting) {
            selectionIds = emptySet()
        } else {
            selectedHistoryId = null
        }
    }

    pendingDeleteIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingDeleteIds = null },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(pluralStringResource(R.plurals.history_delete_selected_title, ids.size, ids.size))
            },
            text = { Text(pluralStringResource(R.plurals.history_delete_selected_body, ids.size, ids.size)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    clickHaptic(view)
                    onDeleteEntries(ids)
                    selectionIds = emptySet()
                    pendingDeleteIds = null
                }) {
                    Text(stringResource(R.string.history_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    pendingDeleteIds = null
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    AnimatedContent(
        targetState = selectedEntry,
        contentKey = { it?.id ?: "history-list" },
        label = "history-detail",
    ) { entry ->
        if (entry == null) {
            HistoryList(
                padding = padding,
                history = history,
                selectionIds = selectionIds,
                selectableIds = selectableIds,
                onToggleSelection = { id ->
                    selectionIds = if (id in selectionIds) {
                        selectionIds - id
                    } else {
                        selectionIds + id
                    }
                },
                onSelectAll = {
                    selectionIds = if (selectionIds.size == selectableIds.size) {
                        emptySet()
                    } else {
                        selectableIds
                    }
                },
                onClearSelection = { selectionIds = emptySet() },
                onEntryClick = { selectedHistoryId = it.id },
                onDeleteSelected = { pendingDeleteIds = selectionIds },
                onExportSelected = { launchExport(selectionIds) },
            )
        } else {
            HistoryDetail(
                padding = padding,
                entry = entry,
                onBack = { selectedHistoryId = null },
            )
        }
    }
}

@Composable
private fun HistoryList(
    padding: PaddingValues,
    history: List<InstallHistoryEntry>,
    selectionIds: Set<String>,
    selectableIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onEntryClick: (InstallHistoryEntry) -> Unit,
    onDeleteSelected: () -> Unit,
    onExportSelected: () -> Unit,
) {
    val view = LocalView.current
    val selecting = selectionIds.isNotEmpty()
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = stringResource(R.string.history_title),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                    }
                    AnimatedVisibility(
                        visible = selecting,
                        enter = fadeIn() + scaleIn(initialScale = 0.9f),
                        exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    ) {
                        Row {
                            IconButton(onClick = {
                                clickHaptic(view)
                                onSelectAll()
                            }) {
                                Icon(
                                    Icons.Rounded.SelectAll,
                                    contentDescription = stringResource(R.string.history_select_all),
                                )
                            }
                            IconButton(onClick = {
                                clickHaptic(view)
                                onClearSelection()
                            }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.history_clear_selection),
                                )
                            }
                        }
                    }
                }
            }
            if (history.isEmpty()) {
                item { EmptyHistoryCard() }
            } else {
                itemsIndexed(history, key = { _, entry -> entry.id }) { _, entry ->
                    HistoryEntryCard(
                        entry = entry,
                        selectionMode = selecting,
                        isSelected = entry.id in selectionIds,
                        selectable = entry.id in selectableIds,
                        onClick = {
                            if (selecting) {
                                onToggleSelection(entry.id)
                            } else {
                                onEntryClick(entry)
                            }
                        },
                        onLongClick = {
                            if (entry.id in selectableIds) onToggleSelection(entry.id)
                        },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = selecting,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        clickHaptic(view)
                        onExportSelected()
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    icon = { Icon(Icons.Rounded.Save, contentDescription = null) },
                    text = { Text(stringResource(R.string.history_export_selected, selectionIds.size)) },
                )
                ExtendedFloatingActionButton(
                    onClick = {
                        clickHaptic(view)
                        onDeleteSelected()
                    },
                    icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    text = { Text(stringResource(R.string.history_delete_selected, selectionIds.size)) },
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(32.dp))
            Column {
                Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.history_empty_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: InstallHistoryEntry,
    selectionMode: Boolean,
    isSelected: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = expressiveClickableCardShape(interactionSource)
    val containerColor = historyResultContainerColor(entry.result)
    val contentColor = historyResultContentColor(entry.result)
    val borderWidth by animateDpAsState(
        targetValue = if (selectionMode && isSelected) 2.dp else 0.dp,
        label = "history-card-border",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = {
                    clickHaptic(view)
                    onClick()
                },
                onLongClick = {
                    clickHaptic(view)
                    onLongClick()
                },
            ),
        shape = shape,
        border = if (borderWidth > 0.dp) {
            BorderStroke(borderWidth, MaterialTheme.colorScheme.secondary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 15.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Crossfade(
                targetState = selectionMode,
                label = "history-leading",
                modifier = Modifier.size(48.dp),
            ) { selecting ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (selecting) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            enabled = selectable,
                        )
                    } else {
                        Icon(historyResultIcon(entry.result), contentDescription = null, modifier = Modifier.size(30.dp))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(historyResultLabel(entry.result), style = MaterialTheme.typography.titleMedium)
                Text(
                    formatHistoryTime(entry.startedAtMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.78f),
                )
            }
            if (!selectionMode) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun HistoryDetail(
    padding: PaddingValues,
    entry: InstallHistoryEntry,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val exportLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let { uri -> HistoryLogExporter.saveLog(context, uri, entry) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = {
                    clickHaptic(view)
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    stringResource(R.string.history_detail_title),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    clickHaptic(view)
                    copyLogToClipboard(context, entry.log)
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy_log))
                }
                IconButton(onClick = {
                    clickHaptic(view)
                    exportLogLauncher.launch(
                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TITLE, HistoryLogExporter.entryFileName(entry))
                        },
                    )
                }) {
                    Icon(Icons.Rounded.Save, contentDescription = stringResource(R.string.export_log))
                }
            }
        }
        item { HistoryResultCard(entry) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Text(
                    text = entry.log.ifBlank { stringResource(R.string.history_log_empty) },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun HistoryResultCard(entry: InstallHistoryEntry) {
    val containerColor = historyResultContainerColor(entry.result)
    val contentColor = historyResultContentColor(entry.result)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(historyResultIcon(entry.result), contentDescription = null, modifier = Modifier.size(38.dp))
            Column {
                Text(historyResultLabel(entry.result), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.history_started, formatHistoryTime(entry.startedAtMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.78f),
                )
                entry.completedAtMillis?.let { completedAt ->
                    Text(
                        stringResource(R.string.history_completed, formatHistoryTime(completedAt)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                }
                entry.profileId?.let { profileId ->
                    Text(
                        stringResource(R.string.history_payload, profileId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                }
                // Names the catalog, and the revision of it, that this run's payload came from:
                // two sources can offer the same payload id, so the id alone does not say which
                // catalog defined the payload that ran.
                entry.sourceLabel?.let { label ->
                    val commit = entry.sourceCommit
                    Text(
                        if (commit.isNullOrBlank()) {
                            stringResource(R.string.history_source, label)
                        } else {
                            stringResource(R.string.history_source_commit, label, commit.take(7))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                }
                entry.failureStage?.let { stage ->
                    Text(
                        stringResource(
                            R.string.history_failure,
                            stringResource(stage.label),
                            entry.failureReason.orEmpty(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                }
                Text(
                    stringResource(
                        if (entry.usedShizuku) {
                            R.string.history_shizuku_used
                        } else {
                            R.string.history_shizuku_not_used
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun historyResultLabel(result: InstallRunResult): String = stringResource(
    when (result) {
        InstallRunResult.Running -> R.string.history_running
        InstallRunResult.Succeeded -> R.string.history_succeeded
        InstallRunResult.RootOnly -> R.string.history_root_only
        InstallRunResult.Failed -> R.string.history_failed
        InstallRunResult.Stopped -> R.string.history_stopped
    },
)

private fun historyResultIcon(result: InstallRunResult): ImageVector = when (result) {
    InstallRunResult.Running -> Icons.Rounded.Schedule
    InstallRunResult.Succeeded -> Icons.Rounded.CheckCircle
    InstallRunResult.RootOnly -> Icons.Rounded.LockOpen
    InstallRunResult.Failed -> Icons.Rounded.Error
    InstallRunResult.Stopped -> Icons.Rounded.Block
}

@Composable
private fun historyResultContainerColor(result: InstallRunResult): Color = when (result) {
    InstallRunResult.Running -> MaterialTheme.colorScheme.tertiaryContainer
    InstallRunResult.Succeeded -> MaterialTheme.colorScheme.primaryContainer
    InstallRunResult.RootOnly -> MaterialTheme.colorScheme.secondaryContainer
    InstallRunResult.Failed -> MaterialTheme.colorScheme.errorContainer
    InstallRunResult.Stopped -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun historyResultContentColor(result: InstallRunResult): Color = when (result) {
    InstallRunResult.Running -> MaterialTheme.colorScheme.onTertiaryContainer
    InstallRunResult.Succeeded -> MaterialTheme.colorScheme.onPrimaryContainer
    InstallRunResult.RootOnly -> MaterialTheme.colorScheme.onSecondaryContainer
    InstallRunResult.Failed -> MaterialTheme.colorScheme.onErrorContainer
    InstallRunResult.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun formatHistoryTime(timestamp: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(timestamp, locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
            .format(Date(timestamp))
    }
}

/**
 * The app's own log, as a tab.
 *
 * Separate from History on purpose, because the two answer different questions. History is what a run
 * did - one entry per run, with the payload's whole output, kept because a result is worth keeping.
 * This is what the app did, line by line and in order, across runs and between them: which transport
 * was chosen, why a boot install stood down, what a download was refused for. It is the thing to read
 * first when something did not work, which is why it is a tab rather than a file.
 */
@Composable
private fun LogsPage(padding: PaddingValues) {
    val view = LocalView.current
    val context = LocalContext.current
    val entries by AppLog.log.collectAsStateWithLifecycle()
    var minLevel by remember { mutableStateOf(AppLogLevel.Debug) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    // The file is the record and this process is not the only one that writes to it: a boot install,
    // and everything logged before this screen existed, is in there and nowhere else. Read on opening
    // the tab and off the main thread, since it can be half a megabyte.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { AppLog.reload() }
    }

    val shown = remember(entries, minLevel, query) {
        entries
            .filter { AppLogFormat.matches(it, minLevel, query) }
            .takeLast(MAX_LOG_ROWS)
    }
    val hiddenRows = remember(entries, minLevel, query) {
        (entries.count { AppLogFormat.matches(it, minLevel, query) } - shown.size).coerceAtLeast(0)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(stringResource(R.string.logs_clear_title))
            },
            text = { Text(stringResource(R.string.logs_clear_body)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    clickHaptic(view)
                    AppLog.clear()
                    query = ""
                    minLevel = AppLogLevel.Debug
                    confirmClear = false
                }) {
                    Text(stringResource(R.string.logs_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    confirmClear = false
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.logs_title),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = if (hiddenRows > 0) {
                            stringResource(R.string.logs_count_window, shown.size, hiddenRows)
                        } else {
                            pluralStringResource(R.plurals.logs_count, shown.size, shown.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    enabled = shown.isNotEmpty(),
                    onClick = {
                        clickHaptic(view)
                        copyLogToClipboard(context, AppLog.asText(shown))
                    },
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.logs_copy),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    enabled = entries.isNotEmpty(),
                    onClick = {
                        clickHaptic(view)
                        confirmClear = true
                    },
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.logs_clear),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // A floor rather than a match: a warning is what makes someone open this tab, and the
                // reason for it is in the lines below it.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LogLevelChip(AppLogLevel.Debug, R.string.logs_filter_all, minLevel) {
                        minLevel = it
                    }
                    LogLevelChip(AppLogLevel.Info, R.string.logs_filter_info, minLevel) {
                        minLevel = it
                    }
                    LogLevelChip(AppLogLevel.Warn, R.string.logs_filter_warn, minLevel) {
                        minLevel = it
                    }
                    LogLevelChip(AppLogLevel.Error, R.string.logs_filter_error, minLevel) {
                        minLevel = it
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.logs_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (query.isEmpty()) {
                        null
                    } else {
                        {
                            IconButton(onClick = {
                                clickHaptic(view)
                                query = ""
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = null)
                            }
                        }
                    },
                )
            }
        }
        if (shown.isEmpty()) {
            item { EmptyLogsCard(filtered = entries.isNotEmpty()) }
        } else {
            // Newest first: the line someone is looking for is almost always the last thing that
            // happened, and a log that opens at the top of a scroll is a log nobody reads to the end.
            item { LogSectionLabel(stringResource(R.string.logs_newest_first)) }
            items(shown.asReversed()) { entry -> LogEntryRow(entry) }
        }
    }
}

/**
 * How many lines the tab keeps for the screen.
 *
 * The log itself holds more; what this bounds is what one filter pass and one list have to carry while
 * a run is printing. The count beside the title says when it is hiding some, so a missing line is
 * stated rather than silent.
 */
private const val MAX_LOG_ROWS = 1000

@Composable
private fun LogLevelChip(
    level: AppLogLevel,
    label: Int,
    selected: AppLogLevel,
    onSelected: (AppLogLevel) -> Unit,
) {
    val view = LocalView.current
    FilterChip(
        selected = level == selected,
        onClick = {
            clickHaptic(view)
            onSelected(level)
        },
        label = { Text(stringResource(label)) },
    )
}

@Composable
private fun LogSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * One line of the log: when, how loud, who - then what.
 *
 * Two rows rather than one, because a message is the thing being read and a tag is only how it is
 * found; side by side, the tag's width would decide how much of the message fits. Monospace for the
 * same reason a terminal uses it: a stack trace or a path lines up with the one above it.
 */
@Composable
private fun LogEntryRow(entry: AppLogEntry) {
    val color = logLevelColor(entry.level)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = AppLogFormat.stamp(entry.atMillis),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.level.mark.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = entry.tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (entry.level == AppLogLevel.Warn || entry.level == AppLogLevel.Error) {
                color
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun logLevelColor(level: AppLogLevel): Color = when (level) {
    AppLogLevel.Debug -> MaterialTheme.colorScheme.onSurfaceVariant
    AppLogLevel.Info -> MaterialTheme.colorScheme.primary
    AppLogLevel.Warn -> MaterialTheme.colorScheme.tertiary
    AppLogLevel.Error -> MaterialTheme.colorScheme.error
}

@Composable
private fun EmptyLogsCard(filtered: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    if (filtered) R.string.logs_empty_filtered_title else R.string.logs_empty_title,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (filtered) R.string.logs_empty_filtered_body else R.string.logs_empty_body,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsPage(
    padding: PaddingValues,
    device: DeviceSnapshot,
    accentColor: AccentColor,
    themeMode: AppThemeMode,
    advancedMode: Boolean,
	disableKsuModules: Boolean,
    loadKernelSu: Boolean,
    kernelsuFlavor: KernelSuFlavor,
    shizukuMode: Boolean,
    payloadSources: List<PayloadSource>,
    bootRootMode: Boolean,
    restartAfterRoot: Boolean,
    shizukuBootMode: Boolean,
    bootSettleSeconds: Int,
    autoRootSettleSeconds: Int,
    runLimits: RunLimitsSettings,
    shizukuToken: String,
    partitionReadOnly: Boolean,
    payloadMode: PayloadMode,
    batteryUnrestricted: Boolean,
    updateStatus: UpdateStatus,
    onCheckForUpdate: () -> Unit,
    onStartDownload: (UpdateInfo) -> Unit,
    onAccentColorChanged: (AccentColor) -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onAdvancedModeChanged: (Boolean) -> Unit,
	onDisableKsuModulesChanged: (Boolean) -> Unit,
    onLoadKernelSuChanged: (Boolean) -> Unit,
    onKernelsuFlavorChanged: (KernelSuFlavor) -> Unit,
    onManagerVersionChanged: (String) -> Unit,
    onShizukuModeChanged: (Boolean) -> Unit,
    onPayloadSourcesChanged: (List<PayloadSource>) -> Unit,
    onBootRootModeChanged: (Boolean) -> Unit,
    onRestartAfterRootChanged: (Boolean) -> Unit,
    onShizukuBootModeChanged: (Boolean) -> Unit,
    onBootSettleChanged: (Int) -> Unit,
    onAutoRootSettleChanged: (Int) -> Unit,
    onRunLimitChanged: (RunLimit, Int) -> Unit,
    onShizukuTokenChanged: (String) -> Unit,
    onPartitionReadOnlyChanged: (Boolean) -> Unit,
    onPayloadModeChanged: (PayloadMode) -> Unit,
    onForgetCachedPayload: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    runPlan: () -> RunPlanDisplay,
    /** A card to open on, handed over by a screen that was told to open it, or null. */
    openTarget: String? = null,
    /** Called once the jump has been started, so nothing replays it. */
    onOpenTargetHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFlavorDialog by remember { mutableStateOf(false) }
    var showManagerVersionDialog by remember { mutableStateOf(false) }
    var managerVersionDraft by remember { mutableStateOf("") }
    var flavorMenuTop by remember { mutableStateOf(0.dp) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showShizukuMissingDialog by remember { mutableStateOf(false) }
    var shizukuStarting by remember { mutableStateOf(false) }
    // Read live, not once at composition: Shizuku hands out its binder asynchronously after the
    // service starts, so a snapshot taken while the screen is being built can say "not running" about
    // a service that is already up - which is how this row came to offer a start that had nothing to
    // do. The sticky listener below fires immediately with the current state and again whenever the
    // binder arrives or goes away, so the row follows the service instead of a frame in time.
    var shizukuAvailability by remember { mutableStateOf(ShizukuController.availability()) }
    DisposableEffect(Unit) {
        val received = Shizuku.OnBinderReceivedListener {
            shizukuAvailability = ShizukuController.availability()
        }
        val dead = Shizuku.OnBinderDeadListener {
            shizukuAvailability = ShizukuController.availability()
        }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
        }
    }
    // The third way the answer changes, and the only one Shizuku will not tell us about: a grant or a
    // revocation made in the Shizuku app itself, which sends this app no callback and does not kill the
    // binder. Reading the state again whenever this screen comes back is what keeps the rows below
    // describing the device rather than a snapshot from the last time the app was in front.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                shizukuAvailability = ShizukuController.availability()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var shizukuStartResult by remember { mutableStateOf<String?>(null) }
    // What the attempt said as it went. The result line alone was not enough to act on: "Shizuku could
    // not be started" over three routes with different fixes reads as nothing having happened, which is
    // exactly how it read.
    var shizukuStartLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var showPayloadSourcesSheet by remember { mutableStateOf(false) }
    var showLocalPayloadDialog by remember { mutableStateOf(false) }
    var showRunPlanDialog by remember { mutableStateOf(false) }
    var localPayloadName by remember { mutableStateOf(LocalPayload.displayName(context)) }
    var languageMenuTop by remember { mutableStateOf(32.dp) }
    var colorMenuTop by remember { mutableStateOf(32.dp) }
    var bootSettleMenuTop by remember { mutableStateOf(32.dp) }
    var showBootSettleDialog by remember { mutableStateOf(false) }
    var showRunLimitsDialog by remember { mutableStateOf(false) }
    var autoRootSettleMenuTop by remember { mutableStateOf(32.dp) }
    var showAutoRootSettleDialog by remember { mutableStateOf(false) }
    var showShizukuTokenDialog by remember { mutableStateOf(false) }
    var tokenDraft by remember { mutableStateOf("") }
    var showWirelessAdbDialog by remember { mutableStateOf(false) }
    var wirelessSnapshot by remember { mutableStateOf<WirelessAdbSnapshot?>(null) }
    var wirelessBusy by remember { mutableStateOf(false) }
    var payloadModeMenuTop by remember { mutableStateOf(32.dp) }
    var showPayloadModeDialog by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val currentLanguageTag = AppPreferences.languageTag(context)

    val startShizuku: () -> Unit = {
        if (!shizukuStarting) {
            shizukuStarting = true
            scope.launch {
                // Written from the start attempt's own thread (it runs on IO) and read here once it is
                // done, so the list is the attempt's, not the screen's.
                val lines = Collections.synchronizedList(mutableListOf<String>())
                val outcome = ShizukuStarter.start(
                    context = context,
                    shell = { command ->
                        KernelSuRuntime.rootShell(command) ?: ShizukuController.ShellResult(
                            NO_ROOT_SHELL_EXIT,
                            context.getString(R.string.error_shizuku_start_no_root),
                        )
                    },
                    onLog = { line -> lines += line },
                )
                shizukuStarting = false
                shizukuStartLog = lines.toList()
                shizukuStartResult =
                    if (outcome.started) context.getString(R.string.status_shizuku_started)
                    else outcome.detail.ifBlank { context.getString(R.string.error_shizuku_start_no_root) }
            }
        }
    }

    shizukuStartResult?.let { result ->
        AlertDialog(
            onDismissRequest = { shizukuStartResult = null },
            icon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(stringResource(R.string.settings_shizuku_start))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(result)
                    if (shizukuStartLog.isNotEmpty()) {
                        Text(
                            stringResource(R.string.shizuku_start_attempt_log),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = shizukuStartLog.takeLast(SHIZUKU_START_LOG_LINES).joinToString("\n"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = SHIZUKU_START_LOG_MAX_HEIGHT)
                                .verticalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    shizukuStartResult = null
                }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    if (showShizukuMissingDialog) {
        AlertDialog(
            onDismissRequest = { showShizukuMissingDialog = false },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(stringResource(R.string.shizuku_not_running_title))
            },
            text = { Text(stringResource(R.string.shizuku_not_running_body)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    clickHaptic(view)
                    showShizukuMissingDialog = false
                    openShizukuManager(context)
                }) {
                    Text(stringResource(R.string.action_download_shizuku))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    showShizukuMissingDialog = false
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showRunPlanDialog) {
        RunPlanDialog(display = runPlan(), onDismiss = { showRunPlanDialog = false })
    }

    if (showRunLimitsDialog) {
        RunLimitsDialog(
            limits = runLimits,
            onChanged = onRunLimitChanged,
            // Put through the same callback the menu uses, once per ceiling: a reset is three ordinary
            // changes, and going around the path that persists and re-reads them would be a second way
            // for the stored values to be written.
            onReset = {
                RunLimit.entries.forEach { limit ->
                    onRunLimitChanged(limit, RunLimits.defaultSeconds(limit))
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.run_limits_reset_done),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { showRunLimitsDialog = false },
        )
    }

    if (showLocalPayloadDialog) {
        LocalPayloadDialog(
            initialName = localPayloadName,
            onDismiss = { showLocalPayloadDialog = false },
            onNameChanged = { name -> localPayloadName = name },
        )
    }

    if (showLanguageDialog) {
        SideChoiceMenu(
            choices = languageOptions.map { stringResource(it.label) },
            selectedIndex = languageOptions.indexOfFirst { languageMatches(it, currentLanguageTag) }
                .coerceAtLeast(0),
            topOffset = languageMenuTop,
            onSelected = { index ->
                showLanguageDialog = false
                AppPreferences.setLanguage(context, languageOptions[index].tag)
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showColorDialog) {
        val colors = AccentColor.entries
        SideChoiceMenu(
            choices = colors.map { accentLabel(it) },
            selectedIndex = colors.indexOf(accentColor),
            topOffset = colorMenuTop,
            onSelected = { index ->
                showColorDialog = false
                onAccentColorChanged(colors[index])
            },
            onDismiss = { showColorDialog = false },
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showPayloadModeDialog) {
        SideChoiceMenu(
            // Online first, because it is the default and the one that follows the configured sources.
            choices = listOf(
                stringResource(R.string.settings_payload_mode_online),
                stringResource(R.string.settings_payload_mode_offline),
            ),
            selectedIndex = if (payloadMode == PayloadMode.Offline) 1 else 0,
            topOffset = payloadModeMenuTop,
            onSelected = { index ->
                showPayloadModeDialog = false
                onPayloadModeChanged(if (index == 1) PayloadMode.Offline else PayloadMode.Online)
            },
            onDismiss = { showPayloadModeDialog = false },
        )
    }

    if (showFlavorDialog) {
        SideChoiceMenu(
            choices = KernelSuFlavor.entries.map { it.label },
            selectedIndex = KernelSuFlavor.entries.indexOf(kernelsuFlavor).coerceAtLeast(0),
            topOffset = flavorMenuTop,
            onSelected = { index ->
                showFlavorDialog = false
                onKernelsuFlavorChanged(KernelSuFlavor.entries[index])
            },
            onDismiss = { showFlavorDialog = false },
        )
    }

    if (showManagerVersionDialog) {
        // What the phone is running, read when the dialog opens rather than passed in: this is the
        // version a manager has to match, and the picker beside it is where that gets acted on.
        var runningVersion by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(kernelsuFlavor) {
            runningVersion = withContext(Dispatchers.IO) {
                KernelSuVersionProbe.read(context).daemon
            }
        }
        // Asked for while the dialog is open and forgotten with it, because a version list is about the
        // releases that exist right now: keeping one would offer a version the user has already seen.
        var available by remember { mutableStateOf<Result<List<String>>?>(null) }
        LaunchedEffect(kernelsuFlavor) {
            available = withContext(Dispatchers.IO) {
                KernelSuManager.availableVersions(kernelsuFlavor)
            }
        }
        // What a download would take now: the name in the field, or the flavour's default when it is empty.
        val selectedVersion = managerVersionDraft.trim().ifBlank { kernelsuFlavor.defaultManagerVersion }
        val published = available
        AlertDialog(
            onDismissRequest = { showManagerVersionDialog = false },
            title = { Text(stringResource(R.string.settings_manager_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(
                            R.string.settings_manager_dialog_help,
                            kernelsuFlavor.label,
                            kernelsuFlavor.defaultManagerVersion,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // The reading the picker exists for, next to the picker: it is the one version
                    // that is certainly right, and naming it is what turns "which do I install" into
                    // one tap. Offered only when it is not the version this app already installs by
                    // default, because that one is already the default row below.
                    runningVersion?.let { running ->
                        if (running != kernelsuFlavor.defaultManagerVersion) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.settings_manager_running_version,
                                        kernelsuFlavor.label,
                                        running,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { managerVersionDraft = running }) {
                                    Text(
                                        stringResource(
                                            R.string.settings_manager_running_use,
                                            running,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.settings_manager_versions_heading),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    when {
                        published == null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LoadingIndicator(modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.settings_manager_versions_loading),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        published.isFailure -> Text(
                            // With the reason: "could not read" alone is the same sentence for a rate
                            // limit, a refused answer and a wrong URL, and they need different things
                            // from the person reading it.
                            stringResource(
                                R.string.settings_manager_versions_failed,
                                published.exceptionOrNull()?.message
                                    ?: published.exceptionOrNull()?.javaClass?.simpleName.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )

                        else -> {
                            // The flavour's own default leads, and is listed even when the listing no
                            // longer carries it: it is the version this app installs when nothing is
                            // named, so it has to be selectable whether or not the network answered.
                            val versions = (
                                listOf(kernelsuFlavor.defaultManagerVersion) +
                                    published.getOrDefault(emptyList())
                                ).distinct()
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = MANAGER_VERSION_LIST_MAX),
                            ) {
                                items(versions, key = { it }) { version ->
                                    ManagerVersionRow(
                                        version = version,
                                        isDefault = version == kernelsuFlavor.defaultManagerVersion,
                                        selected = version == selectedVersion,
                                        onPick = {
                                            // Picking the default stores no name at all, which is what
                                            // "the default" already means everywhere else - so a later
                                            // change of default moves with it rather than pinning it.
                                            managerVersionDraft = if (
                                                version == kernelsuFlavor.defaultManagerVersion
                                            ) {
                                                ""
                                            } else {
                                                version
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = managerVersionDraft,
                        onValueChange = { managerVersionDraft = it },
                        label = { Text(stringResource(R.string.settings_manager_version_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            // Both actions in one slot, for the same reason the token dialog puts them there: split
            // across the two slots the button beside Save ends up orphaned on its own line.
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (managerVersionDraft.isNotBlank()) {
                        TextButton(
                            onClick = {
                                showManagerVersionDialog = false
                                onManagerVersionChanged("")
                            },
                        ) {
                            Text(
                                stringResource(
                                    R.string.settings_manager_version_reset,
                                    kernelsuFlavor.defaultManagerVersion,
                                ),
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            showManagerVersionDialog = false
                            onManagerVersionChanged(managerVersionDraft)
                        },
                    ) { Text(stringResource(R.string.action_save)) }
                }
            },
        )
    }

    if (showBootSettleDialog) {
        val settled = BootSettle.allowedSeconds
        SideChoiceMenu(
            choices = settled.map { BootSettle.label(it) },
            selectedIndex = settled.indexOf(bootSettleSeconds).coerceAtLeast(0),
            topOffset = bootSettleMenuTop,
            onSelected = { index ->
                showBootSettleDialog = false
                onBootSettleChanged(settled[index])
            },
            onDismiss = { showBootSettleDialog = false },
        )
    }

    if (showAutoRootSettleDialog) {
        val settled = BootSettle.allowedSeconds
        SideChoiceMenu(
            choices = settled.map { BootSettle.label(it) },
            selectedIndex = settled.indexOf(autoRootSettleSeconds).coerceAtLeast(0),
            topOffset = autoRootSettleMenuTop,
            onSelected = { index ->
                showAutoRootSettleDialog = false
                onAutoRootSettleChanged(settled[index])
            },
            onDismiss = { showAutoRootSettleDialog = false },
        )
    }

    if (showShizukuTokenDialog) {
        AlertDialog(
            onDismissRequest = { showShizukuTokenDialog = false },
            title = { Text(stringResource(R.string.shizuku_token_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.shizuku_token_dialog_help),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = tokenDraft,
                        onValueChange = { tokenDraft = it },
                        label = { Text(stringResource(R.string.shizuku_token_dialog_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            // All three actions in one slot. Split across the confirm and dismiss slots they interleave:
            // a stacked dismiss column is placed beside the confirm button, so Delete ended up next to
            // Save with Cancel orphaned on a line of its own below them.
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (shizukuToken.isNotBlank()) {
                        TextButton(
                            onClick = {
                                showShizukuTokenDialog = false
                                onShizukuTokenChanged("")
                            },
                        ) { Text(stringResource(R.string.history_delete)) }
                    }
                    TextButton(onClick = { showShizukuTokenDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            showShizukuTokenDialog = false
                            onShizukuTokenChanged(tokenDraft)
                        },
                    ) { Text(stringResource(R.string.action_save)) }
                }
            },
        )
    }

    // The payload-sources editor takes this page over rather than opening a dialog or a bottom sheet.
    // Both of those are a second window, and typing into this form tore that window down and rebuilt
    // it, which closed the keyboard after every character; as content in this window there is nothing
    // to tear down. The list below is skipped while it is open, so its scroll state is remembered out
    // here, where it survives the editor.
    val settingsList = rememberLazyListState()
    // Where a jump is headed: the link the run screen handed in, or one from this page's own repair
    // section. One state for both, because to the list they are the same jump.
    var jumpTarget by remember { mutableStateOf<String?>(null) }
    var cardHighlighted by remember { mutableStateOf(false) }
    LaunchedEffect(openTarget) {
        if (openTarget != null) {
            jumpTarget = openTarget
            onOpenTargetHandled()
        }
    }
    LaunchedEffect(jumpTarget) {
        val wanted = jumpTarget ?: return@LaunchedEffect
        jumpToSettingCard(settingsList, wanted)
        cardHighlighted = true
        delay(SETTINGS_HIGHLIGHT_MILLIS)
        cardHighlighted = false
        jumpTarget = null
    }
    if (showPayloadSourcesSheet) {
        PayloadSourcesEditor(
            padding = padding,
            device = device,
            initialSources = payloadSources,
            onDismiss = { showPayloadSourcesSheet = false },
            onSave = { sources ->
                showPayloadSourcesSheet = false
                onPayloadSourcesChanged(sources)
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        state = settingsList,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(top = 20.dp, bottom = 18.dp)) {
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge)
                AppVersionText(
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SectionLabel(stringResource(R.string.appearance)) }
        item {
            ThemeModeSelector(themeMode, onThemeModeChanged)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsCard(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        colorMenuTop = with(density) { coordinates.positionInWindow().y.toDp() }
                    },
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.material_color),
                    description = stringResource(R.string.material_color_description),
                    value = accentLabel(accentColor),
                    position = SettingsCardPosition.Top,
                    onClick = {
                        clickHaptic(view)
                        showColorDialog = true
                    },
                )
                SettingsCard(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        languageMenuTop = with(density) { coordinates.positionInWindow().y.toDp() }
                    },
                    icon = Icons.Rounded.Language,
                    title = stringResource(R.string.language),
                    description = stringResource(R.string.language_description),
                    value = languageLabel(currentLanguageTag),
                    position = SettingsCardPosition.Bottom,
                    onClick = {
                        clickHaptic(view)
                        showLanguageDialog = true
                    },
                )
            }
        }

        item { SectionLabel(stringResource(R.string.settings_section_payloads)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsCard(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        payloadModeMenuTop = with(density) { coordinates.positionInWindow().y.toDp() }
                    },
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.settings_payload_mode),
                    description = stringResource(
                        if (payloadMode == PayloadMode.Offline) {
                            R.string.settings_payload_mode_summary_offline
                        } else {
                            R.string.settings_payload_mode_summary_online
                        },
                    ),
                    value = stringResource(
                        if (payloadMode == PayloadMode.Offline) {
                            R.string.settings_payload_mode_offline
                        } else {
                            R.string.settings_payload_mode_online
                        },
                    ),
                    position = SettingsCardPosition.Top,
                    onClick = {
                        clickHaptic(view)
                        showPayloadModeDialog = true
                    },
                )
                // Read when the section is opened rather than on every recomposition: it is a file
                // read, and what it describes changes only when a run finishes.
                var cached by remember { mutableStateOf<CachedPayload?>(null) }
                var showCachedDialog by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    cached = withContext(Dispatchers.IO) { KnownGoodPayloadStore.describe(context) }
                }
                SettingsCard(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.settings_cached_payload),
                    description = stringResource(R.string.settings_cached_payload_summary),
                    // The profile's *name*, not its id. An id like
                    // "galaxy-s25-series-2026-06-07" is an identifier - it has no spaces to wrap at,
                    // so it took both lines the value is allowed and left the description a column
                    // two words wide. The name says which device this is cached for, and the id is
                    // still stated in full in the dialog, which is where a precise string belongs.
                    value = cached?.displayName?.takeIf { it.isNotBlank() }
                        ?: cached?.profileId
                        ?: stringResource(R.string.settings_cached_payload_none),
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showCachedDialog = true
                    },
                )
                if (showCachedDialog) {
                    CachedPayloadDialog(
                        cached = cached,
                        onForget = {
                            showCachedDialog = false
                            cached = null
                            onForgetCachedPayload()
                        },
                        onDismiss = { showCachedDialog = false },
                    )
                }
                SettingsCard(
                    icon = Icons.Rounded.Link,
                    title = stringResource(R.string.payload_sources),
                    description = stringResource(R.string.payload_sources_description),
                    // Flat on top: this card sits between two others in the payloads group, and a
                    // second group top there drew it as the start of another list.
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showPayloadSourcesSheet = true
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.UploadFile,
                    title = stringResource(R.string.local_payload),
                    description = stringResource(
                        if (localPayloadName == null) {
                            R.string.local_payload_description
                        } else {
                            R.string.local_payload_description_set
                        },
                    ),
                    value = localPayloadName ?: stringResource(R.string.local_payload_none),
                    position = SettingsCardPosition.Bottom,
                    onClick = {
                        clickHaptic(view)
                        showLocalPayloadDialog = true
                    },
                )
            }
        }

        item { SectionLabel(stringResource(R.string.settings_section_run)) }

        // Keyed by the card something else in the app may ask for: the run screen's read-only failure
        // names this setting and hands its key over, and a key is what lets the page find the row without
        // an index that a new card above it would silently invalidate.
        item(key = SettingsTarget.PartitionReadOnly) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSwitchCard(
                    icon = Icons.Rounded.Memory,
                    title = stringResource(R.string.advanced_mode),
                    description = stringResource(R.string.advanced_mode_description),
                    checked = advancedMode,
                    position = SettingsCardPosition.Top,
                    onCheckedChange = {
                        clickHaptic(view)
                        onAdvancedModeChanged(it)
                    },
                )
                SettingsSwitchCard(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.disable_ksu_modules),
                    // Moving the modules aside is something a run does *around the load*, so with no
                    // load there is nothing for them to sit out and the setting would quietly do
                    // nothing. Said here rather than left to be discovered.
                    description = stringResource(
                        if (loadKernelSu) {
                            R.string.disable_ksu_modules_description
                        } else {
                            R.string.disable_ksu_modules_needs_load
                        },
                    ),
                    checked = disableKsuModules,
                    position = SettingsCardPosition.Middle,
                    enabled = loadKernelSu,
                    onCheckedChange = {
                        clickHaptic(view)
                        onDisableKsuModulesChanged(it)
                    },
                )
                // Outlined, not marked in some other way, because what a jump has to answer is "which
                // of these rows is it" - and the outline sits exactly on the card's own edge, at its own
                // corner radius, so it reads as the row being pointed at rather than as a new control.
                //
                // Drawn rather than added as a border modifier: a border is laid out with the content it
                // wraps, so a transparent one held open for the outline to appear in would leave this row
                // a few pixels narrower than the two cards stacked with it, on every frame. This changes
                // nothing but the pixels.
                val highlightColor = MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier.drawWithContent {
                        drawContent()
                        if (cardHighlighted) {
                            drawOutline(
                                outline = settingsCardRestingShape(SettingsCardPosition.Middle)
                                    .createOutline(size, layoutDirection, this),
                                color = highlightColor,
                                style = Stroke(width = SETTINGS_HIGHLIGHT_WIDTH.toPx()),
                            )
                        }
                    },
                ) {
                    SettingsSwitchCard(
                        icon = Icons.Rounded.Lock,
                        title = stringResource(R.string.partition_read_only),
                        description = stringResource(R.string.partition_read_only_description),
                        checked = partitionReadOnly,
                        position = SettingsCardPosition.Middle,
                        onCheckedChange = {
                            clickHaptic(view)
                            onPartitionReadOnlyChanged(it)
                        },
                    )
                }
                SettingsCard(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        bootSettleMenuTop = with(density) { coordinates.positionInWindow().y.toDp() }
                    },
                    icon = Icons.Rounded.HourglassEmpty,
                    title = stringResource(R.string.settings_boot_settle),
                    description = stringResource(R.string.settings_boot_settle_summary),
                    value = BootSettle.label(bootSettleSeconds),
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showBootSettleDialog = true
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.Timer,
                    title = stringResource(R.string.settings_run_limits),
                    description = stringResource(R.string.settings_run_limits_summary),
                    // No value. Three ceilings listed as numbers beside a row are three numbers to
                    // read on every pass through Settings, and they answer a question the dialog
                    // answers properly - which ceiling is which, and what it decides. The row's job
                    // is to say the settings exist and to open them, and the run plan states the
                    // ceilings that are actually in force where a run is about to be started.
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showRunLimitsDialog = true
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.Schedule,
                    title = stringResource(R.string.run_plan),
                    description = stringResource(R.string.run_plan_description),
                    value = "",
                    position = SettingsCardPosition.Bottom,
                    onClick = {
                        clickHaptic(view)
                        showRunPlanDialog = true
                    },
                )
            }
        }
        item { SectionLabel(stringResource(R.string.settings_section_shizuku)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsSwitchCard(
                    // The transport a run is handed to, which is why it sits with the other two
                    // Shizuku decisions rather than under appearance.
                    icon = Icons.Rounded.VerifiedUser,
                    title = stringResource(R.string.shizuku_mode),
                    // The preference is the user's intent and is left alone when Shizuku cannot honour
                    // it; what changes here is that the row stops describing an unusable preference as
                    // if it were working. Read live, so a permission revoked in the Shizuku app - which
                    // this app gets no callback for - shows up here rather than at the next run.
                    description = stringResource(
                        when {
                            !shizukuMode -> R.string.shizuku_mode_description
                            shizukuAvailability == ShizukuAvailability.WithoutPermission ->
                                R.string.shizuku_mode_without_permission
                            shizukuAvailability == ShizukuAvailability.NotRunning ->
                                R.string.shizuku_mode_not_running
                            else -> R.string.shizuku_mode_description
                        },
                    ),
                    checked = shizukuMode,
                    position = SettingsCardPosition.Top,
                    onCheckedChange = { enabled ->
                        clickHaptic(view)
                        if (!enabled) {
                            onShizukuModeChanged(false)
                        } else {
                            // Read at the moment of the tap, not from what was drawn: between the two
                            // the user may have just come back from the Shizuku app.
                            when (shizukuModeEnableRoute(ShizukuController.availability())) {
                                ShizukuModeEnable.Enable -> onShizukuModeChanged(true)
                                ShizukuModeEnable.RequestPermission -> scope.launch {
                                    // Stored only once the grant lands. Turning the preference on first
                                    // and asking afterwards leaves the app preferring a transport it is
                                    // not allowed to use - and leaves this switch saying it is.
                                    if (ShizukuController.requestPermission()) {
                                        onShizukuModeChanged(true)
                                    }
                                    shizukuAvailability = ShizukuController.availability()
                                }
                                ShizukuModeEnable.ExplainMissing -> showShizukuMissingDialog = true
                            }
                        }
                    },
                )
                // The row says which state Shizuku is in and offers the one action that is still
                // useful, because a card whose title is a command has to be a command that will do
                // something: start it when it is not running, ask for the grant when it is running
                // without one, and nothing at all when it is running and allowed - where a start
                // attempt would only report, in a dialog, what this row should have said on the
                // screen.
                SettingsCard(
                    icon = Icons.Rounded.PowerSettingsNew,
                    title = stringResource(
                        when (shizukuAvailability) {
                            ShizukuAvailability.NotRunning -> R.string.settings_shizuku_start
                            ShizukuAvailability.WithoutPermission -> R.string.settings_shizuku_allow
                            ShizukuAvailability.Ready -> R.string.settings_shizuku_running
                        },
                    ),
                    description = stringResource(
                        when (shizukuAvailability) {
                            ShizukuAvailability.NotRunning -> R.string.settings_shizuku_start_summary
                            ShizukuAvailability.WithoutPermission -> R.string.settings_shizuku_allow_summary
                            ShizukuAvailability.Ready -> R.string.settings_shizuku_running_summary
                        },
                    ),
                    value = when {
                        shizukuStarting -> stringResource(R.string.status_shizuku_starting)
                        shizukuAvailability == ShizukuAvailability.Ready ->
                            stringResource(R.string.settings_shizuku_state_running)
                        shizukuAvailability == ShizukuAvailability.WithoutPermission ->
                            stringResource(R.string.settings_shizuku_state_needs_permission)
                        else -> ""
                    },
                    position = SettingsCardPosition.Middle,
                    enabled = shizukuAvailability != ShizukuAvailability.Ready,
                    onClick = {
                        // Asked again here rather than trusting what was drawn: permission can be
                        // granted from the Shizuku app while this screen sits in the background, and
                        // Shizuku offers no callback for a grant this app did not request.
                        shizukuAvailability = ShizukuController.availability()
                        when (shizukuAvailability) {
                            ShizukuAvailability.NotRunning -> startShizuku()
                            ShizukuAvailability.WithoutPermission -> scope.launch {
                                ShizukuController.requestPermission()
                                shizukuAvailability = ShizukuController.availability()
                            }
                            ShizukuAvailability.Ready -> Unit
                        }
                    },
                )
                SettingsCard(                        icon = Icons.Rounded.LockOpen,
                    title = stringResource(R.string.settings_shizuku_token),
                    description = stringResource(R.string.settings_shizuku_token_summary),
                    value = if (shizukuToken.isBlank()) {
                        stringResource(R.string.settings_shizuku_token_value_unset)
                    } else {
                        stringResource(R.string.settings_shizuku_token_value_set)
                    },
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        tokenDraft = shizukuToken
                        showShizukuTokenDialog = true
                    },
                )
                SettingsSwitchCard(
                    icon = Icons.Rounded.Bolt,
                    title = stringResource(R.string.settings_shizuku_boot),
                    description = stringResource(R.string.settings_shizuku_boot_summary),
                    checked = shizukuBootMode,
                    position = SettingsCardPosition.Bottom,
                    onCheckedChange = { enabled ->
                        clickHaptic(view)
                        if (enabled) onRequestNotificationPermission()
                        onShizukuBootModeChanged(enabled)
                        // The setting is only worth having if it works on this device, so switching
                        // it on proves it there and then instead of at the next reboot.
                        if (enabled) startShizuku()
                    },
                )
            }
        }

        item { SectionLabel(stringResource(R.string.settings_section_wireless_adb)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Read when the screen is built rather than on every recomposition: it is a file read
                // plus a settings lookup, and what it describes changes only when something is done
                // to it.
                LaunchedEffect(Unit) {
                    wirelessSnapshot = WirelessAdbDiagnostics.passiveSnapshot(context)
                }
                val snapshot = wirelessSnapshot
                SettingsCard(
                    icon = Icons.Rounded.Link,
                    title = stringResource(R.string.settings_wireless_adb),
                    description = stringResource(R.string.settings_wireless_adb_summary),
                    value = if (snapshot == null) {
                        ""
                    } else {
                        wirelessAdbStateLabel(snapshot.authState)
                    },
                    position = SettingsCardPosition.GroupedSingle,
                    onClick = {
                        clickHaptic(view)
                        wirelessSnapshot = WirelessAdbDiagnostics.passiveSnapshot(context)
                        showWirelessAdbDialog = true
                    },
                )
                if (showWirelessAdbDialog) {
                    WirelessAdbDialog(
                        snapshot = wirelessSnapshot,
                        busy = wirelessBusy,
                        writeSecureSettingsMissing = !PermissionGrant.hasPermission(context),
                        onGrantPermission = {
                            wirelessBusy = true
                            scope.launch {
                                val outcome = PermissionGrant.writeSecureSettings(context)
                                // Re-read rather than assume: the transport that worked is worth naming,
                                // and a grant that did not take has to leave the screen saying so.
                                wirelessSnapshot = WirelessAdbDiagnostics.passiveSnapshot(context)
                                wirelessBusy = false
                                Toast.makeText(
                                    context,
                                    outcome.message(context),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        onPair = { forceRepair ->
                            // The code field lives in a notification, so pairing is started by an
                            // activity that asks for the permission first and clears a stale pairing
                            // when asked for a fresh one.
                            context.startActivity(
                                AdbPairingSetupActivity.pairingIntent(context, forceRepair),
                            )
                            showWirelessAdbDialog = false
                        },
                        onOpenDeveloperOptions = {
                            // Closed first: the screen this opens is where the user has to be next, and
                            // a dialog left behind it would be in the way on the way back.
                            showWirelessAdbDialog = false
                            if (!DeveloperOptions.open(context)) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.developer_options_unavailable),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        onTest = {
                            wirelessBusy = true
                            scope.launch {
                                wirelessSnapshot = WirelessAdbDiagnostics.testConnection(context)
                                wirelessBusy = false
                            }
                        },
                        onForget = {
                            AdbCredentialStore.forgetLocalCredential(context)
                            wirelessSnapshot = WirelessAdbDiagnostics.passiveSnapshot(context)
                        },
                        onDismiss = { showWirelessAdbDialog = false },
                    )
                }
            }
        }

        item { SectionLabel(stringResource(R.string.settings_section_root)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // The flavour is first because everything below it is about this flavour's module:
                // which daemon a run stages, which manager opens afterwards, and which module root
                // on boot puts back.
                // Re-read when the flavour changes, because the marker below is exactly the state a
                // change produces.
                val loadedFlavor = remember(kernelsuFlavor) { AppPreferences.loadedFlavor(context) }
                SettingsCard(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        flavorMenuTop = with(density) { coordinates.positionInWindow().y.toDp() }
                    },
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.settings_ksu_flavor),
                    description = stringResource(kernelsuFlavor.summaryRes),
                    // The selected flavour sits in the band every other row puts its setting in, so it
                    // lands on their centre line instead of riding up beside the title. That band is
                    // measured before the text column next to it, which is why these two descriptions
                    // are one line long: anything longer wraps into a second line at half the card's
                    // width, and reads as a row that overflowed rather than one that fits.
                    value = kernelsuFlavor.label,
                    // The pending marker is the only warning this screen can give: the two flavours
                    // cannot both be in the kernel, so a switch made in a boot that already carries
                    // one only takes effect after a restart. It says which flavour this boot is
                    // holding, because "after a restart" on its own leaves the reason to be guessed.
                    notice = loadedFlavor
                        ?.takeIf { it != kernelsuFlavor }
                        ?.let { stringResource(R.string.settings_ksu_flavor_pending, it.label) },
                    position = SettingsCardPosition.Top,
                    onClick = {
                        clickHaptic(view)
                        showFlavorDialog = true
                    },
                )
                val offeredManagerVersion = AppPreferences.managerVersion(context, kernelsuFlavor)
                    ?: kernelsuFlavor.defaultManagerVersion
                val installedManager = remember(kernelsuFlavor, offeredManagerVersion) {
                    KernelSuManager.installedFor(context, kernelsuFlavor)
                }
                // Read here, where the rows that say what it means are, and off the main thread: the
                // read is a root shell, which on a device that has not answered its grant prompt is a
                // wait rather than a failure - and a settings list is not worth a frozen frame.
                var runningKernelSu by remember { mutableStateOf<KernelSuVersionReading?>(null) }
                LaunchedEffect(kernelsuFlavor) {
                    runningKernelSu = withContext(Dispatchers.IO) {
                        KernelSuVersionProbe.read(context)
                    }
                }
                val managerVersion = installedManager?.versionName
                val managerState = remember(managerVersion, runningKernelSu) {
                    managerVersionState(managerVersion, runningKernelSu?.daemon)
                }
                SettingsCard(
                    icon = Icons.Rounded.VerifiedUser,
                    title = stringResource(R.string.settings_manager),
                    description = if (installedManager != null) {
                        stringResource(
                            R.string.settings_manager_summary_installed,
                            offeredManagerVersion,
                            installedManager.label,
                        )
                    } else {
                        stringResource(R.string.settings_manager_summary, offeredManagerVersion)
                    },
                    // What is on the phone, not what the app would install: the offered version is
                    // the row below this one, and the two were the same number in the same place
                    // until a manager from another line could be installed without the app noticing.
                    value = managerVersion ?: offeredManagerVersion,
                    // The pair, said out loud. A mismatch is the one thing this screen could never see:
                    // any manager talks to the loaded module over KernelSU's socket, so a manager from
                    // one line against a kernel from another installs and runs exactly like a matching
                    // one. Tapping the row still opens whatever manager is installed.
                    notice = runningKernelSu?.daemon?.let { kernelVersion ->
                        when (managerState) {
                            ManagerVersionState.Differing -> stringResource(
                                R.string.settings_manager_mismatch,
                                managerVersion.orEmpty(),
                                kernelsuFlavor.label,
                                kernelVersion,
                            )
                            ManagerVersionState.Matching -> stringResource(
                                R.string.settings_manager_running,
                                kernelsuFlavor.label,
                                kernelVersion,
                            )
                            // Nothing was read, so there is nothing to say about the manager: the
                            // absence of a reading is not a finding.
                            ManagerVersionState.Unknown -> null
                        }
                    },
                    position = SettingsCardPosition.Middle,
                    // Opens whatever manager is on the phone, of whatever version; the download is
                    // only offered when there is none. Nothing here rejects a version the user
                    // installed themselves, which is the point of not pinning this.
                    onClick = {
                        clickHaptic(view)
                        KernelSuManager.open(context, kernelsuFlavor) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.SystemUpdate,
                    title = stringResource(R.string.settings_manager_version),
                    description = stringResource(R.string.settings_manager_version_summary),
                    value = offeredManagerVersion,
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        managerVersionDraft = AppPreferences.managerVersion(context, kernelsuFlavor).orEmpty()
                        showManagerVersionDialog = true
                    },
                )
                // The load decision follows the flavour because the rest of the group depends on it:
                // root on boot exists to put KernelSU back after a reboot, and a boot run with
                // nothing to load is not a boot run at all.
                SettingsSwitchCard(
                    icon = Icons.Rounded.Memory,
                    title = stringResource(R.string.settings_ksu_load),
                    description = stringResource(R.string.settings_ksu_load_summary),
                    checked = loadKernelSu,
                    position = SettingsCardPosition.Middle,
                    onCheckedChange = { enabled ->
                        clickHaptic(view)
                        onLoadKernelSuChanged(enabled)
                    },
                )
                SettingsSwitchCard(
                    icon = Icons.Rounded.RestartAlt,
                    title = stringResource(R.string.settings_boot_root),
                    // Disabled rather than turned off: the stored choice is kept, so turning loading
                    // back on restores it exactly - and the reason is on the row either way.
                    description = stringResource(
                        if (loadKernelSu) {
                            R.string.settings_boot_root_summary
                        } else {
                            R.string.settings_boot_root_needs_load
                        },
                    ),
                    checked = bootRootMode,
                    position = SettingsCardPosition.Middle,
                    enabled = loadKernelSu,
                    onCheckedChange = { enabled ->
                        clickHaptic(view)
                        if (enabled) onRequestNotificationPermission()
                        onBootRootModeChanged(enabled)
                    },
                )
                // Follows the load decision for the same reason root on boot does: what it applies its
                // modules to is the KernelSU a run loaded, and with loading off there is nothing to
                // apply. It is not tied to root on boot - a manual run can load KernelSU with that off.
                SettingsSwitchCard(
                    icon = Icons.Rounded.RestartAlt,
                    title = stringResource(R.string.settings_restart_after_root),
                    description = stringResource(R.string.settings_restart_after_root_summary),
                    checked = restartAfterRoot,
                    position = SettingsCardPosition.Middle,
                    enabled = loadKernelSu,
                    onCheckedChange = { enabled ->
                        clickHaptic(view)
                        onRestartAfterRootChanged(enabled)
                    },
                )
                SettingsCard(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        autoRootSettleMenuTop =
                            with(density) { coordinates.positionInWindow().y.toDp() }
                    },
                    icon = Icons.Rounded.HourglassEmpty,
                    title = stringResource(R.string.settings_autoroot_settle),
                    description = stringResource(R.string.settings_autoroot_settle_summary),
                    value = BootSettle.label(autoRootSettleSeconds),
                    position = SettingsCardPosition.Bottom,
                    enabled = loadKernelSu,
                    onClick = {
                        clickHaptic(view)
                        showAutoRootSettleDialog = true
                    },
                )
            }
        }

        item { SectionLabel(stringResource(R.string.settings_recovery)) }
        item {
            RootRecoverySection(
                // Root on boot is what would bring root back, so it is turned off before the reboot
                // is asked for and this screen has to follow whatever was stored.
                onBootRootModeChanged = onBootRootModeChanged,
                // Every action here consumes the root a verified load installed, so with loading
                // switched off they are not offered as things that will work.
                kernelSuLoadingEnabled = loadKernelSu,
                // The card a refusal points at is in this same list, so the jump is a scroll rather than
                // a new window.
                onOpenSetting = { target -> jumpTarget = target },
            )
        }

        item { SectionLabel(stringResource(R.string.settings_section_system)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsCard(
                    icon = Icons.Rounded.BatterySaver,
                    title = stringResource(R.string.settings_battery),
                    description = stringResource(
                        if (batteryUnrestricted) {
                            R.string.settings_battery_summary_allowed
                        } else {
                            R.string.settings_battery_summary_restricted
                        },
                    ),
                    value = stringResource(
                        if (batteryUnrestricted) {
                            R.string.settings_battery_allowed
                        } else {
                            R.string.settings_battery_allow
                        },
                    ),
                    position = SettingsCardPosition.GroupedSingle,
                    onClick = onRequestBatteryExemption,
                )
            }
        }

        item { SectionLabel(stringResource(R.string.about)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                UpdateSettingsCard(
                    status = updateStatus,
                    position = SettingsCardPosition.Top,
                    onCheckForUpdate = onCheckForUpdate,
                    onStartDownload = onStartDownload,
                )
                SettingsCard(
                    // Named for the app rather than for the section: the label above it already says
                    // About, and a card that repeated it would be a title under a title.
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.app_name),
                    description = stringResource(R.string.settings_about_app),
                    // The build this is, not just the version it is: two installs of the same
                    // version differ only by this label.
                    value = BuildConfig.BUILD_LABEL,
                    position = SettingsCardPosition.Bottom,
                    onClick = {
                        clickHaptic(view)
                        showAboutDialog = true
                    },
                )
            }
        }
    }
}

@Composable
private fun UpdateSettingsCard(
    status: UpdateStatus,
    position: SettingsCardPosition,
    onCheckForUpdate: () -> Unit,
    onStartDownload: (UpdateInfo) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    val busy = status.busy
    Card(
        onClick = {
            clickHaptic(view)
            when {
                busy -> Unit
                status is UpdateStatus.Available -> onStartDownload(status.info)
                else -> onCheckForUpdate()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = expressiveClickableCardShape(interactionSource, position),
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                status is UpdateStatus.Checking -> LoadingIndicator(modifier = Modifier.size(28.dp))
                status is UpdateStatus.Downloading -> CircularProgressIndicator(
                    progress = { status.progress },
                    modifier = Modifier.size(28.dp),
                )
                else -> Icon(
                    Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (status) {
                        is UpdateStatus.Available, is UpdateStatus.Downloading ->
                            stringResource(R.string.updater_available_title)
                        else -> stringResource(R.string.updater_check)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = when {
                        status is UpdateStatus.Downloading -> stringResource(R.string.updater_downloading)
                        status is UpdateStatus.Checking -> stringResource(R.string.updater_checking)
                        status is UpdateStatus.Available ->
                            stringResource(R.string.updater_available_body_short, status.info.versionName)
                        status is UpdateStatus.UpToDate -> stringResource(R.string.updater_up_to_date)
                        status is UpdateStatus.Failed -> stringResource(R.string.updater_failed)
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (status is UpdateStatus.Available) {
                Text(
                    text = stringResource(R.string.updater_button_download),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSelectionSheet(
    device: DeviceSnapshot,
    catalog: TargetCatalogUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onNext: (TargetProfile) -> Unit,
) {
    var showOnlyMyDevice by remember { mutableStateOf(true) }
    var selectedSelectionId by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current
    val visibleProfiles = remember(catalog.profiles, showOnlyMyDevice, device) {
        if (showOnlyMyDevice) {
            catalog.profiles.filter { it.matches(device) }
        } else {
            catalog.profiles
        }
    }
    val selectedProfile = catalog.profiles.firstOrNull { it.selectionId == selectedSelectionId }

    // Preselect what the catalog prefers, so a device whose feed lists an exact kernel release
    // starts on that profile instead of an arbitrary three-part sibling. Only fills an empty
    // selection: a profile the user picked is never replaced by a catalog reload.
    LaunchedEffect(catalog.profiles) {
        if (selectedSelectionId == null) {
            selectedSelectionId = catalog.profiles.resolveFor(device)?.selectionId
        }
    }

    ModalBottomSheet(
        // Fully expanded, not peeking: the payloads above the fold are the ones a device actually
        // fits, and opening half-height hid them behind a swipe that looked like the list ending.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.select_device_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.select_device_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = showOnlyMyDevice,
                        role = Role.Checkbox,
                        onValueChange = { enabled ->
                            clickHaptic(view)
                            showOnlyMyDevice = enabled
                            if (enabled && selectedProfile?.matches(device) == false) {
                                selectedSelectionId = null
                            }
                        },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Checkbox(checked = showOnlyMyDevice, onCheckedChange = null)
                Text(stringResource(R.string.show_my_device_only), style = MaterialTheme.typography.titleMedium)
            }

            if (catalog.sourceFailures.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.payload_sources_failed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    catalog.sourceFailures.forEach { failure ->
                        Text(
                            failure,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            when {
                catalog.loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(color = MaterialTheme.colorScheme.onSurface)
                }
                catalog.error != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(catalog.error, color = MaterialTheme.colorScheme.error)
                    FilledTonalButton(onClick = onRetry) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                visibleProfiles.isEmpty() -> Text(
                    stringResource(R.string.no_matching_devices),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleProfiles, key = { it.selectionId }) { profile ->
                        val selected = selectedSelectionId == profile.selectionId
                        val matchingModel = profile.models.firstOrNull {
                            it.equals(device.model, ignoreCase = true)
                        }
                        val modelLabel = matchingModel ?: profile.models.take(3).joinToString().let {
                            if (profile.models.size > 3) "$it +${profile.models.size - 3}" else it
                        }
                        // Regional siblings share a model and a three-part kernel version, so the
                        // only thing telling them apart in this list is whether the feed ties the
                        // profile to this build's full release.
                        val kernelMatch = profile.kernelMatch(device)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            clickHaptic(view)
                                            selectedSelectionId = profile.selectionId
                                        },
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        profile.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        modelLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        when (kernelMatch) {
                                            KernelMatch.Exact ->
                                                stringResource(R.string.kernel_match_exact)
                                            KernelMatch.Version -> stringResource(
                                                R.string.kernel_match_version,
                                                device.kernelVersion,
                                            )
                                            KernelMatch.None ->
                                                stringResource(R.string.kernel_match_none)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (kernelMatch) {
                                            KernelMatch.Exact -> MaterialTheme.colorScheme.primary
                                            KernelMatch.Version -> MaterialTheme.colorScheme.onSurfaceVariant
                                            KernelMatch.None -> MaterialTheme.colorScheme.error
                                        },
                                    )
                                    if (profile.sourceLabel.isNotEmpty()) {
                                        Text(
                                            profile.sourceLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = {
                    clickHaptic(view)
                    onDismiss()
                }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        clickHaptic(view)
                        selectedProfile?.let(onNext)
                    },
                    enabled = selectedProfile != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_next))
                }
            }
        }
    }
}

/**
 * Everything the run-plan dialog reports: which target the app would pick, which transport it
 * would use, and the environment and ceilings that go with them.
 */
private data class RunPlanDisplay(
    val deviceLabel: String,
    val targetLabel: String?,
    val sourceLabel: String?,
    val unresolvedNote: String?,
    val freshSession: Boolean,
    val shizuku: Boolean,
    val payloadMode: PayloadMode,
    val partitionReadOnly: Boolean,
    val cachedOffset: String?,
    val plan: ExploitPlan,
)

/**
 * What is cached for offline use, and the way to get rid of it.
 *
 * The digests are shown because they are the reason to trust the cache at all: every one of them is
 * checked again before a run uses the files, so what is displayed here is what will be enforced.
 */
@Composable
private fun CachedPayloadDialog(
    cached: CachedPayload?,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
        title = {
            DialogDimAmount(0.34f)
            Text(stringResource(R.string.cached_payload_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (cached == null) {
                    Text(stringResource(R.string.settings_cached_payload_none))
                } else {
                    // The name first and the id under it, which together are what the settings row
                    // used to carry on one line of value - and the id is here in full, in the place
                    // where a long precise string costs nothing.
                    RunPlanRow(
                        stringResource(R.string.cached_payload_device),
                        cached.displayName,
                        first = true,
                    )
                    RunPlanRow(stringResource(R.string.cached_payload_profile), cached.profileId)
                    RunPlanRow(
                        stringResource(R.string.cached_payload_exploit_sha),
                        cached.exploit.sha256 ?: stringResource(R.string.cached_payload_no_digest),
                    )
                    RunPlanRow(
                        stringResource(R.string.cached_payload_kernelsu_sha),
                        cached.kernelSu.sha256 ?: stringResource(R.string.cached_payload_no_digest),
                    )
                    Text(
                        stringResource(R.string.cached_payload_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clickHaptic(view)
                onDismiss()
            }) {
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = if (cached == null) {
            null
        } else {
            {
                TextButton(onClick = {
                    clickHaptic(view)
                    onForget()
                }) {
                    Text(stringResource(R.string.cached_payload_forget))
                }
            }
        },
    )
}

/**
 * Shows what a run will be handed before it is started, so a run that ends at a ceiling says so
 * here first. Every value comes from the same constants the run uses.
 */
@Composable
private fun RunPlanDialog(
    display: RunPlanDisplay,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
        title = {
            DialogDimAmount(0.34f)
            Text(stringResource(R.string.run_plan_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RunPlanRow(stringResource(R.string.run_plan_device), display.deviceLabel, first = true)
                RunPlanRow(
                    stringResource(R.string.run_plan_target),
                    display.targetLabel ?: display.unresolvedNote.orEmpty(),
                )
                // Always shown, even with nothing to put in it. It used to be dropped when the profile
                // had no source, which made a cached run look like a target from nowhere - and the
                // question "which catalog is this from" is exactly the one the row is for.
                RunPlanRow(
                    stringResource(R.string.run_plan_source),
                    display.sourceLabel
                        ?: stringResource(R.string.run_plan_source_none),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_transport),
                    stringResource(
                        if (display.shizuku) R.string.run_plan_transport_shizuku
                        else R.string.run_plan_transport_direct,
                    ),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_boot_settle),
                    BootSettle.label(display.plan.bootSettleSeconds),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_payload_mode),
                    stringResource(
                        if (display.payloadMode == PayloadMode.Offline) {
                            R.string.settings_payload_mode_offline
                        } else {
                            R.string.settings_payload_mode_online
                        },
                    ),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_partition_read_only),
                    stringResource(
                        if (display.partitionReadOnly) R.string.run_plan_partition_read_only_on
                        else R.string.run_plan_partition_read_only_off,
                    ),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_session),
                    stringResource(
                        if (display.freshSession) R.string.run_plan_fresh_yes
                        else R.string.run_plan_fresh_no,
                    ),
                )
                RunPlanSection(stringResource(R.string.run_plan_variables))
                // Where these come from, said where they are shown: they are the payload profile's own
                // decisions, which is why none of them has a setting.
                Text(
                    stringResource(R.string.run_plan_variables_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (display.plan.environment.isEmpty()) {
                    RunPlanMonospace(stringResource(R.string.run_plan_variables_defaults))
                } else {
                    display.plan.environment.forEach { (name, value) ->
                        RunPlanMonospace("$name=$value")
                    }
                }
                if (display.plan.shizukuArguments.isNotEmpty()) {
                    RunPlanSection(stringResource(R.string.run_plan_shizuku_arguments))
                    display.plan.shizukuArguments.forEach { (name, value) ->
                        RunPlanMonospace("$name=$value")
                    }
                }
                RunPlanRow(
                    stringResource(R.string.run_plan_cached_offset),
                    display.cachedOffset
                        ?: stringResource(R.string.run_plan_cached_offset_none),
                )
                RunPlanSection(stringResource(R.string.run_plan_limits))
                Text(
                    stringResource(R.string.run_plan_limits_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_stall),
                    display.plan.stallLimitMillis
                        ?.let(::formatDuration)
                        ?: stringResource(R.string.run_plan_stall_none),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_total),
                    formatDuration(display.plan.totalLimitMillis),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_helper),
                    formatDuration(display.plan.helperLimitMillis),
                )
                Text(
                    stringResource(R.string.run_plan_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clickHaptic(view)
                onDismiss()
            }) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

/**
 * The three ceilings, all in one dialog.
 *
 * One dialog rather than a menu per row: they are three answers to one question - how long this app lets
 * a run go on - and a value only makes sense beside the other two. Each group says what it does and, for
 * the two that have one, the rule that can override the choice: a fresh session keeps the app's own hour
 * whatever the whole-run setting says, and a stall limit is never applied to one.
 */
@Composable
private fun RunLimitsDialog(
    limits: RunLimitsSettings,
    onChanged: (RunLimit, Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
        title = {
            DialogDimAmount(0.34f)
            Text(stringResource(R.string.run_limits_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RunLimitGroup(
                    title = stringResource(R.string.run_limits_total),
                    note = stringResource(R.string.run_limits_total_note),
                    limit = RunLimit.Total,
                    selected = limits.totalSeconds,
                    onChanged = onChanged,
                )
                RunLimitGroup(
                    title = stringResource(R.string.run_limits_stall),
                    note = stringResource(R.string.run_limits_stall_note),
                    limit = RunLimit.Stall,
                    selected = limits.stallSeconds,
                    onChanged = onChanged,
                )
                RunLimitGroup(
                    title = stringResource(R.string.run_limits_helper),
                    note = stringResource(R.string.run_limits_helper_note),
                    limit = RunLimit.Helper,
                    selected = limits.helperSeconds,
                    onChanged = onChanged,
                )
                // The other half of the answer, and the reason three of the run plan's rows cannot be
                // touched here: they are the payload's numbers, handed over as its own variables.
                Text(
                    stringResource(R.string.run_limits_payload_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clickHaptic(view)
                onDismiss()
            }) {
                Text(stringResource(R.string.action_close))
            }
        },
        // In the dismiss slot, which is where the "other" action belongs: it is not the way out of the
        // dialog, and it is the one thing here that changes more than the value just tapped.
        dismissButton = {
            TextButton(onClick = {
                clickHaptic(view)
                onReset()
            }) {
                Text(stringResource(R.string.run_limits_reset))
            }
        },
    )
}

@Composable
private fun RunLimitGroup(
    title: String,
    note: String,
    limit: RunLimit,
    selected: Int,
    onChanged: (RunLimit, Int) -> Unit,
) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // A wrapped row of choices rather than a menu: three values fit on one line, six do not, and a
        // scrollable list of six per group would bury the value in use.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RunLimits.options(limit).forEach { seconds ->
                FilterChip(
                    selected = seconds == selected,
                    onClick = {
                        clickHaptic(view)
                        onChanged(limit, seconds)
                    },
                    label = { Text(RunLimits.label(seconds)) },
                )
            }
        }
    }
}

/**
 * One row of the plan: its name, its value, and the hairline that separates it from the next.
 *
 * The line is what makes this a list rather than a paragraph. Fourteen label-and-value pairs stacked
 * with nothing between them read as one block, and the pair a reader is looking for is the one they
 * have to hunt through; a rule per row is what lets the eye run down the names instead.
 */
@Composable
private fun RunPlanRow(label: String, value: String, first: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!first) {
            HorizontalDivider(
                modifier = Modifier.padding(bottom = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RunPlanSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun RunPlanMonospace(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    return when {
        seconds % 3600 == 0L -> "${seconds / 3600} h"
        seconds % 60 == 0L -> "${seconds / 60} min"
        else -> "$seconds s"
    }
}

/**
 * Import, replace, or drop the payload used in place of the downloaded exploit. The file is copied
 * into app storage here rather than referenced by URI, so an unattended run at boot can use it.
 */
@Composable
private fun LocalPayloadDialog(
    initialName: String?,
    onDismiss: () -> Unit,
    onNameChanged: (String?) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var name by remember(initialName) { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { LocalPayload.import(context, uri) }
            .onSuccess { imported ->
                name = imported
                error = null
                onNameChanged(imported)
            }
            .onFailure { failure ->
                // The previously imported payload is still in place; only the message changes.
                error = failure.message ?: failure.javaClass.simpleName
            }
    }
    val current = name
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.UploadFile, contentDescription = null) },
        title = {
            DialogDimAmount(0.34f)
            Text(stringResource(R.string.local_payload_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (current == null) {
                        stringResource(R.string.local_payload_summary_none)
                    } else {
                        stringResource(R.string.local_payload_summary_set, current)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) {
                    Text(
                        error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                clickHaptic(view)
                // Some providers report .so files as octet-stream and others as nothing usable, so
                // the picker is left unfiltered and the import validates what comes back.
                picker.launch(
                    arrayOf("application/octet-stream", "application/x-sharedlib", "*/*"),
                )
            }) {
                Text(
                    stringResource(
                        if (current == null) R.string.local_payload_choose
                        else R.string.local_payload_replace,
                    ),
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current != null) {
                    TextButton(onClick = {
                        clickHaptic(view)
                        LocalPayload.clear(context)
                        name = null
                        error = null
                        onNameChanged(null)
                    }) {
                        Text(stringResource(R.string.local_payload_remove))
                    }
                }
                TextButton(onClick = {
                    clickHaptic(view)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun PayloadSourcesEditor(
    padding: PaddingValues,
    device: DeviceSnapshot,
    initialSources: List<PayloadSource>,
    onDismiss: () -> Unit,
    onSave: (List<PayloadSource>) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var sources by remember(initialSources) { mutableStateOf(initialSources) }
    var showAddSource by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    // The pin is a choice of revision, so the lock opens a picker around one source rather than
    // pinning to whatever the branch happens to point at the moment it is tapped.
    var revisionTarget by remember { mutableStateOf<PayloadSource?>(null) }
    // What each source was found to read, keyed by source id and kept for the life of the sheet.
    // Keying by id is what makes the add form work: a repository that failed to read leaves its
    // failure under that candidate id, so editing the field clears the message without any extra
    // state, and the entry a successful check stored is the one the added row then shows.
    var checks by remember { mutableStateOf<Map<String, Result<SourceCoverage>>>(emptyMap()) }
    var checking by remember { mutableStateOf<String?>(null) }
    var repository by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf(PayloadSource.DEFAULT_BRANCH) }
    var duplicate by remember { mutableStateOf(false) }

    // Reading a source is also how it is validated: an unreachable repository, a missing manifest,
    // or a schema this app cannot read must not reach the saved list, where it would sit failing on
    // every later load. Shared with the row action, since both ask what a source actually covers.
    fun checkSource(source: PayloadSource, onCovered: () -> Unit = {}) {
        scope.launch {
            checking = source.id
            val outcome = runCatching {
                withContext(Dispatchers.IO) { PayloadRepository(context).inspect(source, device) }
            }
            checks = checks + (source.id to outcome)
            checking = null
            if (outcome.isSuccess) onCovered()
        }
    }
    // No rule about the shape of what is typed here on purpose. A pattern cannot tell a repository that
    // exists from one that does not, so a half-typed owner only means the app argues with a form the
    // user has not finished; the read that adding a source performs is the thing that can tell, and it
    // reports what it found. The fields show examples instead of enforcing a format.
    val candidate = remember(repository, branch) { PayloadSource.create(repository, branch) }
    val candidateCheck = candidate?.let { checks[it.id] }
    val addError = candidateCheck?.exceptionOrNull()?.let {
        stringResource(
            R.string.payload_source_check_failed,
            it.message ?: it.javaClass.simpleName,
        )
    } ?: if (duplicate) {
        stringResource(R.string.payload_source_duplicate)
    } else {
        null
    }
    val enabledCount = sources.count { it.enabled }
    // The picker takes the whole screen rather than sitting above the list, so the one scroll this
    // content needs moves with it: the picker is taller than the space above a keyboard and scrolls as
    // a whole, while the list scrolls inside a fixed frame and leaves the form above it alone.
    val revisionPickerOpen = revisionTarget != null
    // Content in this app's own window, and deliberately neither a dialog nor a bottom sheet: both of
    // those are a second window, and a second window here was torn down and rebuilt as soon as the
    // form was typed into - the keyboard went with it, once per character. Nothing below can tear a
    // window down, so the cursor keeps its input connection however often this recomposes.
    //
    // Back leaves the editor, or steps out of the revision picker inside it first: the picker is a
    // step within this screen, and back must not skip both.
    BackHandler {
        if (revisionPickerOpen) revisionTarget = null else onDismiss()
    }
    Surface(
        modifier = Modifier.fillMaxSize().padding(padding),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The keyboard is the one inset this screen has to answer for itself: it takes the
                // space the footer needs, and the list above gives it up rather than scrolling under
                // the keys.
                .imePadding()
                .then(
                    if (revisionPickerOpen) {
                        Modifier.verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 20.dp)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Inside the same dialog rather than a second one: two of them would fight over the same
            // dismiss and back handling, and this one is the host the add form above needs anyway.
            revisionTarget?.let { target ->
                RevisionPicker(
                    source = target,
                    device = device,
                    onBack = { revisionTarget = null },
                    onPick = { commit ->
                        clickHaptic(view)
                        revisionTarget = null
                        val updated = if (commit == null) {
                            sources.withSourceUnpinned(target.id)
                        } else {
                            sources.withSourcePinned(target.id, commit)
                        }
                        sources = updated
                        pinError = null
                        // What a revision serves is the question a pin raises, so the picked source
                        // is read again and its coverage replaces the previous one.
                        updated.firstOrNull { it.id == target.id }?.let { checkSource(it) }
                    },
                )
                return@Column
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Link, contentDescription = null)
                Text(
                    stringResource(R.string.payload_sources_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Text(
                stringResource(R.string.payload_sources_summary, enabledCount, sources.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Above the list and collapsed by default: a long list of added sources can then
            // never push it out of reach.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clickHaptic(view)
                        showAddSource = !showAddSource
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.payload_source_add),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (showAddSource) {
                        Icons.Rounded.ExpandLess
                    } else {
                        Icons.Rounded.ExpandMore
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (showAddSource) {
                OutlinedTextField(
                    value = repository,
                    onValueChange = {
                        repository = it
                        duplicate = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = duplicate,
                    label = { Text(stringResource(R.string.payload_repository_label)) },
                    placeholder = { Text(PayloadSource.DEFAULT_REPOSITORY) },
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = {
                        branch = it
                        duplicate = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = false,
                    label = { Text(stringResource(R.string.payload_branch)) },
                    placeholder = { Text(PayloadSource.DEFAULT_BRANCH) },
                    supportingText = { Text(stringResource(R.string.payload_branch_hint)) },
                )
                addError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val candidateChecking = candidate != null && checking == candidate.id
                Button(
                    onClick = {
                        clickHaptic(view)
                        val source = candidate ?: return@Button
                        if (sources.any { it.id == source.id }) {
                            duplicate = true
                        } else {
                            // Added only once the source has been read, so the list never holds a
                            // repository nobody has confirmed serves a catalog.
                            checkSource(source) {
                                sources = sources.withSourceAdded(source)
                                repository = ""
                                branch = PayloadSource.DEFAULT_BRANCH
                                duplicate = false
                            }
                        }
                    },
                    enabled = candidate != null && !candidateChecking,
                ) {
                    if (candidateChecking) {
                        LoadingIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (candidateChecking) R.string.payload_source_checking
                            else R.string.payload_source_add_action,
                        ),
                    )
                }
            }

            HorizontalDivider()

            if (sources.isEmpty()) {
                Text(
                    stringResource(R.string.payload_sources_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    // The only part that scrolls, and it takes the space the form above leaves: the
                    // dialog's height depends on the screen rather than on what the list holds, so
                    // adding a source cannot move the fields out from under the cursor.
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sources, key = { it.id }) { source ->
                        PayloadSourceRow(
                            source = source,
                            device = device,
                            coverage = checks[source.id]?.getOrNull(),
                            checkFailure = checks[source.id]?.exceptionOrNull()?.let {
                                it.message ?: it.javaClass.simpleName
                            },
                            checking = checking == source.id,
                            onCheck = { checkSource(source) },
                            onEnabledChange = { checked ->
                                clickHaptic(view)
                                sources = sources.withSourceEnabled(source.id, checked)
                            },
                            onPinChange = {
                                clickHaptic(view)
                                pinError = null
                                revisionTarget = source
                            },
                            onRemove = {
                                clickHaptic(view)
                                sources = sources.withSourceRemoved(source.id)
                            },
                        )
                    }
                }
            }

            pinError?.let { message ->
                Text(
                    stringResource(R.string.payload_source_pin_failed, message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (sources.none { it.id == PayloadSource.DEFAULT.id }) {
                TextButton(onClick = {
                    clickHaptic(view)
                    sources = sources.withSourceAdded(PayloadSource.DEFAULT)
                }) {
                    Text(stringResource(R.string.payload_source_default))
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = {
                        clickHaptic(view)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        clickHaptic(view)
                        onSave(sources)
                    },
                    enabled = enabledCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

/**
 * Choosing the revision a source is pinned to.
 *
 * The lock used to pin whatever the branch pointed at the moment it was tapped, which left the
 * actual revision - the thing a pin is - out of the user's hands. This lists what there is to pin:
 * the ref itself (no pin), the repository's tags, and its most recent commits, each with the date
 * and the first line of its message. Naming a branch, tag, or commit by hand covers the revision
 * that is not in either list, and naming a tag resolves it to the commit it points at now, because
 * a tag can be moved onto another commit.
 */
@Composable
private fun RevisionPicker(
    source: PayloadSource,
    device: DeviceSnapshot,
    onBack: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var loading by remember(source.id) { mutableStateOf(true) }
    var revisions by remember(source.id) { mutableStateOf<List<SourceRevision>>(emptyList()) }
    var listFailure by remember(source.id) { mutableStateOf<String?>(null) }
    var manual by remember(source.id) { mutableStateOf("") }
    var applying by remember(source.id) { mutableStateOf(false) }
    var applyFailure by remember(source.id) { mutableStateOf<String?>(null) }
    // What is being considered, which is not yet what is pinned: the revision is chosen first, then
    // read, and only then stored. Tapping used to store the pin and describe it afterwards, which is
    // the wrong order for the one decision a catalog cannot take back.
    // Opening the picker already has a subject: what the source is on now, so the first thing shown is
    // what the pin currently means rather than an empty panel.
    var choice by remember(source.id) {
        mutableStateOf<RevisionChoice?>(
            if (source.isPinned) RevisionChoice.Commit(source.pinnedCommit) else RevisionChoice.Branch,
        )
    }
    var coverage by remember(source.id) { mutableStateOf<SourceCoverage?>(null) }
    var coverageFailure by remember(source.id) { mutableStateOf<String?>(null) }
    var reading by remember(source.id) { mutableStateOf(false) }

    LaunchedEffect(source.id) {
        loading = true
        runCatching {
            withContext(Dispatchers.IO) { PayloadRepository(context).revisions(source) }
        }.onSuccess { listed ->
            revisions = listed
        }.onFailure { failure ->
            listFailure = failure.message ?: failure.javaClass.simpleName
        }
        loading = false
    }

    LaunchedEffect(source.id, choice) {
        val chosen = choice ?: return@LaunchedEffect
        reading = true
        coverage = null
        coverageFailure = null
        runCatching {
            withContext(Dispatchers.IO) {
                val repository = PayloadRepository(context)
                when (chosen) {
                    // Following the branch has an answer too, and it is the one a branch's coverage has
                    // to be read at: the branch's head, with no pin in the way.
                    RevisionChoice.Branch ->
                        repository.inspect(source.copy(pinnedCommit = ""), device)
                    is RevisionChoice.Commit -> repository.inspectAt(source, device, chosen.commit)
                }
            }
        }.onSuccess { read ->
            coverage = read
        }.onFailure { failure ->
            coverageFailure = failure.message ?: failure.javaClass.simpleName
        }
        reading = false
    }

    // A ref's head is the newest commit that is not a tag, which is the first one listed.
    val head = revisions.firstOrNull { it.tag == null }?.commit

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = {
                clickHaptic(view)
                onBack()
            }) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.payload_pin_title, source.repository),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (source.isPinned) {
                        stringResource(R.string.payload_pin_pinned_at, source.pinnedCommit.take(7))
                    } else {
                        stringResource(R.string.payload_pin_follow_summary)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        RevisionRow(
            title = stringResource(R.string.payload_source_unpin),
            subtitle = source.branch,
            detail = null,
            selected = choice == RevisionChoice.Branch,
            icon = Icons.Rounded.LockOpen,
            pinned = !source.isPinned,
            onClick = { choice = RevisionChoice.Branch },
        )

        HorizontalDivider()

        OutlinedTextField(
            value = manual,
            onValueChange = {
                manual = it
                applyFailure = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.payload_pin_manual)) },
            supportingText = { Text(stringResource(R.string.payload_pin_manual_hint)) },
        )
        applyFailure?.let { reason ->
            Text(
                stringResource(R.string.payload_source_pin_failed, reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(
            onClick = {
                clickHaptic(view)
                val ref = manual.trim()
                if (ref.isEmpty() || applying) return@OutlinedButton
                scope.launch {
                    applying = true
                    applyFailure = null
                    runCatching {
                        withContext(Dispatchers.IO) {
                            PayloadRepository(context).resolveNamedRevision(source.repository, ref)
                        }
                    }.onSuccess { commit -> choice = RevisionChoice.Commit(commit) }
                        .onFailure { failure ->
                            applyFailure = failure.message ?: failure.javaClass.simpleName
                        }
                    applying = false
                }
            },
            enabled = manual.isNotBlank() && !applying,
        ) {
            if (applying) {
                LoadingIndicator(modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.payload_pin_resolve))
        }

        // What the chosen revision serves, stated before it is what the source is pinned to. The
        // lists are the whole catalog's, because a pin is a decision about the catalog and not only
        // about this phone, and the last line of the block answers the phone's half of it.
        choice?.let { chosen ->
            Text(
                if (chosen is RevisionChoice.Commit) {
                    stringResource(R.string.payload_pin_serves_at, chosen.commit.take(7))
                } else {
                    stringResource(R.string.payload_pin_serves_branch, source.branch)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            val read = coverage
            val failure = coverageFailure
            when {
                reading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.payload_pin_reading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                read != null -> SourceCoverageBlock(read, device, inset = 0.dp)
                failure != null -> Text(
                    stringResource(R.string.payload_pin_read_failed, failure),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Button(
            onClick = {
                clickHaptic(view)
                onPick((choice as? RevisionChoice.Commit)?.commit)
            },
            // Deliberately not gated on the read having succeeded: a pin is a decision about a
            // revision, and a network refusal while summarising it is not a reason to leave the user
            // unable to pin or to stop following a branch at all.
            enabled = choice != null && !reading && !applying,
        ) {
            Icon(
                if (choice is RevisionChoice.Commit) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (choice is RevisionChoice.Commit) {
                    stringResource(R.string.payload_pin_apply)
                } else {
                    stringResource(R.string.payload_pin_follow_action)
                },
            )
        }

        // The revision list sits below the decision, not above it. It is the longest thing on this
        // screen and the only one that is a browse rather than a choice, so putting it first pushed
        // the branch field and the action past the fold on a phone - the two controls a user who
        // already knows the ref they want came here for.
        if (loading) {
            LoadingIndicator(modifier = Modifier.size(24.dp))
        } else {
            listFailure?.let { reason ->
                Text(
                    stringResource(R.string.payload_pin_list_failed, reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (revisions.isNotEmpty()) {
                Text(
                    stringResource(R.string.payload_pin_recent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(revisions, key = { "${it.tag ?: ""}:${it.commit}" }) { revision ->
                        RevisionRow(
                            title = revision.tag ?: revision.label.ifBlank { revision.commit.take(7) },
                            // A tag names the row, and the commit under it is what a pin stores, so
                            // pinning by tag is still visibly a decision about a commit.
                            subtitle = revision.commit.take(7),
                            detail = revision.date.ifBlank { null },
                            selected = choice == RevisionChoice.Commit(revision.commit),
                            current = revision.commit == head,
                            pinned = source.pinnedCommit == revision.commit,
                            icon = if (revision.tag == null) Icons.Rounded.Lock else Icons.Rounded.Link,
                            onClick = { choice = RevisionChoice.Commit(revision.commit) },
                        )
                    }
                }
            }
        }
    }
}

/** Which revision is being considered: the branch as it stands, or one commit of it. */
private sealed interface RevisionChoice {
    data object Branch : RevisionChoice

    data class Commit(val commit: String) : RevisionChoice
}

/** One revision as a selectable line: what it is, the commit, and when. */
@Composable
private fun RevisionRow(
    title: String,
    subtitle: String,
    detail: String?,
    selected: Boolean,
    icon: ImageVector,
    current: Boolean = false,
    pinned: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (selected) Icons.Rounded.CheckCircle else icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        detail?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Exactly one label, because the two states are alternatives rather than degrees: `current`
        // is where the branch points now, `pinned` is what the source is frozen at.
        if (!selected) {
            val label = when {
                current -> R.string.payload_pin_current
                pinned -> R.string.payload_pin_pinned
                else -> null
            }
            label?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PayloadSourceRow(
    source: PayloadSource,
    device: DeviceSnapshot,
    coverage: SourceCoverage?,
    checkFailure: String?,
    checking: Boolean,
    onCheck: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPinChange: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(checked = source.enabled, onCheckedChange = onEnabledChange)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    source.repository,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    source.refLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (source.isPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (checking) {
                LoadingIndicator(modifier = Modifier.size(20.dp))
            } else {
                IconButton(onClick = onCheck) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.payload_source_check),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = onPinChange) {
                Icon(
                    if (source.isPinned) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = stringResource(
                        if (source.isPinned) {
                            R.string.payload_source_unpin
                        } else {
                            R.string.payload_source_pin
                        },
                    ),
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.payload_source_remove),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        checkFailure?.let { message ->
            Text(
                stringResource(R.string.payload_source_check_failed, message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = SOURCE_ROW_INSET, top = 2.dp),
            )
        }
        coverage?.let { SourceCoverageBlock(it, device) }
    }
}

/** Lines up a row's detail with the text column, past the checkbox and the row spacing. */
private val SOURCE_ROW_INSET = 52.dp

/**
 * What a checked source covers, as the sheet reports it.
 *
 * The models and kernel versions are the whole catalog's, not this device's, because the point of
 * reading a source before saving it is to see what it is for. The last line answers the other half
 * of the question, which the lists alone cannot: whether any of it fits this phone.
 */
@Composable
private fun SourceCoverageBlock(
    coverage: SourceCoverage,
    device: DeviceSnapshot,
    // The sheet's rows align their detail past a checkbox; the picker's panel has none to clear.
    inset: Dp = SOURCE_ROW_INSET,
) {
    val models = coverage.models.joinToString()
    val kernels = coverage.kernelVersions.joinToString()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, top = 4.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.payload_source_verified, coverage.commit.take(7)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.payload_source_coverage_payloads, coverage.payloadCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (models.isNotEmpty()) {
            Text(
                stringResource(R.string.payload_source_coverage_models, models),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (kernels.isNotEmpty()) {
            Text(
                stringResource(R.string.payload_source_coverage_kernels, kernels),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val deviceProfileId = coverage.deviceProfileId
        if (deviceProfileId != null) {
            // A catalog usually has one payload per device, but a regional sibling makes two; the
            // count is how a user learns the other one is there without opening the picker.
            Text(
                if (coverage.deviceProfileCount > 1) {
                    stringResource(
                        R.string.payload_source_device_match_more,
                        deviceProfileId,
                        coverage.deviceProfileCount - 1,
                    )
                } else {
                    stringResource(R.string.payload_source_device_match, deviceProfileId)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                stringResource(R.string.payload_source_device_none, device.model),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 2.dp),
    )
}

/**
 * Where a card sits in the list it is drawn in.
 *
 * The corners are the whole difference between one group and several: a card that names the wrong
 * one starts a new container on screen or leaves a group of one. Internal, because the Recovery
 * actions are a group too and belong to the same list as everything else on the screen.
 */
internal enum class SettingsCardPosition {
    Single,
    GroupedSingle,
    Top,
    Middle,
    Bottom,
}

/**
 * The corner radius a card of this position rests at, given which end of it is being asked about.
 *
 * One rule, read by the card's own shape and by anything drawn *on* a card. The outline a settings jump
 * draws is the reason it is not inlined into the shape: an outline that guessed the radius would sit
 * off the card's own curve wherever it guessed wrong, and the row would look like it had grown a second
 * border rather than like it was being pointed at.
 */
internal fun settingsCardRestingRadius(position: SettingsCardPosition, top: Boolean): Dp = when {
    position == SettingsCardPosition.Single -> 16.dp
    position in setOf(SettingsCardPosition.GroupedSingle, SettingsCardPosition.Top) && top -> 24.dp
    position in setOf(SettingsCardPosition.GroupedSingle, SettingsCardPosition.Bottom) && !top -> 24.dp
    else -> 6.dp
}

/** The shape a card rests at, for something that has to be drawn around one. */
internal fun settingsCardRestingShape(position: SettingsCardPosition): RoundedCornerShape =
    RoundedCornerShape(
        topStart = settingsCardRestingRadius(position, top = true),
        topEnd = settingsCardRestingRadius(position, top = true),
        bottomStart = settingsCardRestingRadius(position, top = false),
        bottomEnd = settingsCardRestingRadius(position, top = false),
    )

/**
 * How much of a card a trailing value may take.
 *
 * Every band here is short - a state, a count, a mode - so a value that wants more than this is
 * either a sentence that belongs below the description or one that needs shortening. The cap exists
 * because the value is measured before the text column beside it, so an uncapped one silently
 * claimed the row.
 */
private val SETTINGS_VALUE_MAX_WIDTH = 140.dp

/** How thick the outline a settings jump draws is. Thin enough to read as a pointer, not a control. */
private val SETTINGS_HIGHLIGHT_WIDTH = 2.dp

/**
 * How much of a failed Shizuku start is worth showing.
 *
 * The tail, because the lines that matter are the last route's: an attempt tries the routes in order and
 * says what each one found, so the end of the list is the reason the whole thing stopped. Bounded on
 * both axes for the same reason the run log is - a dialog is not a log viewer.
 */
private const val SHIZUKU_START_LOG_LINES = 14
private val SHIZUKU_START_LOG_MAX_HEIGHT = 220.dp

/**
 * One row of the settings list: an icon, a title, a description, and an optional trailing value.
 *
 * [busy] is for a row that starts something the app has to wait on. It keeps the row in place and
 * stops it being tapped again while the work runs, instead of the row changing shape or a second
 * tap starting the same thing twice.
 */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    value: String = "",
    /**
     * One short line of state that is not a setting: why this card is not doing what it says.
     *
     * Kept to a single line on purpose. It used to be a sentence written like body copy - the same
     * size and colour as the description above it - which made a row that carried one into a paragraph
     * six lines tall, and made state read as more description. A small icon and a line beside it is
     * what tells the two apart at a glance, and what keeps this row the height of its neighbours.
     * Centred, because it belongs to the whole row rather than to the text column it would otherwise
     * hang off, aligned under an icon it has nothing to do with.
     */
    notice: String? = null,
    noticeIcon: ImageVector = Icons.Rounded.RestartAlt,
    position: SettingsCardPosition = SettingsCardPosition.Single,
    busy: Boolean = false,
    /**
     * False when the row's action cannot do anything right now, so the card dims and takes no tap.
     *
     * It is for actions that are *already done* rather than merely likely to fail: a row whose only
     * remaining outcome is a dialog saying "this was already the case" reads as a button that does
     * not work, where a dimmed row reads as a state.
     */
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    Card(
        enabled = enabled && !busy,
        onClick = {
            clickHaptic(view)
            onClick()
        },
        modifier = modifier.fillMaxWidth(),
        shape = expressiveClickableCardShape(interactionSource, position),
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    // Wraps like SettingsSwitchCard rather than ellipsising: a description that
                    // needs a second line is still worth reading.
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (value.isNotBlank()) {
                    Text(
                        value,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        // Capped, because an unweighted Row child is measured before the weighted one
                        // beside it: a long value took the whole row and left the title and the
                        // description one character per line. Two lines and end alignment keeps the
                        // text column readable without cutting the value off at one line.
                        modifier = Modifier.widthIn(max = SETTINGS_VALUE_MAX_WIDTH),
                        textAlign = TextAlign.End,
                        maxLines = 2,
                    )
                }
            }
            if (notice != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        noticeIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        notice,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (busy) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    position: SettingsCardPosition = SettingsCardPosition.Single,
    /**
     * False when the setting cannot take effect right now, so the switch dims and refuses to move.
     *
     * The stored value is left alone: a setting that is inert because something it depends on is off
     * comes back as it was when that is turned back on, and silently clearing it would lose a choice
     * the user made deliberately.
     */
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    Card(
        enabled = enabled,
        onClick = {
            clickHaptic(view)
            onCheckedChange(!checked)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = expressiveClickableCardShape(interactionSource, position),
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // `onCheckedChange = null` already, so the switch is a reading of the card rather than a
            // second control; a disabled card simply stops the whole row taking a tap.
            Switch(checked = checked, enabled = enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun ThemeModeSelector(
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
) {
    val view = LocalView.current
    val themeModes = AppThemeMode.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        themeModes.forEachIndexed { index, mode ->
            ToggleButton(
                checked = themeMode == mode,
                onCheckedChange = {
                    clickHaptic(view)
                    onThemeModeChanged(mode)
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    themeModes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Icon(
                    imageVector = when (mode) {
                        AppThemeMode.System -> Icons.Rounded.BrightnessAuto
                        AppThemeMode.Light -> Icons.Rounded.LightMode
                        AppThemeMode.Dark -> Icons.Rounded.DarkMode
                    },
                    contentDescription = null,
                )
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(themeModeLabel(mode), maxLines = 1)
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogDimAmount(0.34f)
            Text(stringResource(R.string.about_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.about_body))
                AppVersionText(
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Surface(
                    onClick = {
                        clickHaptic(view)
                        uriHandler.openUri(KERNEL_SU_HOME_URL)
                    },
                    color = Color.Transparent,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_kernelsu), contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.kernelsu_card_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.kernelsu_card_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.Link, contentDescription = stringResource(R.string.open_github))
                    }
                }
                Surface(
                    onClick = {
                        clickHaptic(view)
                        uriHandler.openUri(ROOT_MY_GALAXY_URL)
                    },
                    color = Color.Transparent,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_github), contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.github_card_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.github_card_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.Link, contentDescription = stringResource(R.string.open_github))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clickHaptic(view)
                onDismiss()
            }) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun expressiveClickableCardShape(
    interactionSource: MutableInteractionSource,
    position: SettingsCardPosition = SettingsCardPosition.Single,
): RoundedCornerShape {
    val pressed by interactionSource.collectIsPressedAsState()
    val topRadius by animateDpAsState(
        // Pressed is the one radius that is not a resting one: the card swells to show it took the tap.
        targetValue = if (pressed) 28.dp else settingsCardRestingRadius(position, top = true),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "clickable-card-top-corner",
    )
    val bottomRadius by animateDpAsState(
        targetValue = if (pressed) 28.dp else settingsCardRestingRadius(position, top = false),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "clickable-card-bottom-corner",
    )
    return RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )
}

@Composable
private fun SideChoiceMenu(
    choices: List<String>,
    selectedIndex: Int,
    topOffset: Dp,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 0.34f else 0f,
        animationSpec = tween(durationMillis = if (visible) 160 else 180),
        label = "menu-scrim",
    )

    fun closeMenu(afterAnimation: () -> Unit) {
        if (closing) return
        closing = true
        visible = false
        coroutineScope.launch {
            delay(MENU_EXIT_WAIT_MILLIS)
            afterAnimation()
        }
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    Popup(
        onDismissRequest = { closeMenu(onDismiss) },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        ),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val estimatedHeight = 16.dp + 56.dp * choices.size
            val constrainedTop = minOf(
                topOffset,
                maxHeight - estimatedHeight - 24.dp,
            ).coerceAtLeast(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { closeMenu(onDismiss) },
                    ),
            )
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = constrainedTop, end = 18.dp),
                enter = scaleIn(
                    animationSpec = keyframes {
                        durationMillis = 200
                        1.025f at 95
                        0.995f at 155
                    },
                    initialScale = 0.94f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ),
                exit = scaleOut(
                    animationSpec = tween(durationMillis = MENU_EXIT_ANIMATION_MILLIS),
                    targetScale = 0.86f,
                    transformOrigin = TransformOrigin(1f, 0.5f),
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 160,
                        delayMillis = 20,
                    ),
                ),
            ) {
                Surface(
                    modifier = Modifier
                        .width(196.dp)
                        .heightIn(max = 620.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(choices) { index, choice ->
                            val selected = index == selectedIndex
                            Surface(
                                onClick = {
                                    clickHaptic(view)
                                    closeMenu { onSelected(index) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = if (selected) {
                                    MaterialTheme.shapes.extraLarge
                                } else {
                                    MaterialTheme.shapes.medium
                                },
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    Text(
                                        text = choice,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MENU_EXIT_ANIMATION_MILLIS = 180
private const val MENU_EXIT_WAIT_MILLIS = 200L

@Composable
private fun languageLabel(tag: String): String =
    languageOptions.firstOrNull { languageMatches(it, tag) }
        ?.let { stringResource(it.label) }
        ?: stringResource(R.string.language_system)

private fun languageMatches(option: LanguageOption, currentTag: String): Boolean {
    if (option.tag.isEmpty()) return currentTag.isEmpty()
    return currentTag == option.tag || currentTag.startsWith("$option.tag-")
}

@Composable
private fun accentLabel(color: AccentColor): String = when (color) {
    AccentColor.Dynamic -> stringResource(R.string.color_dynamic)
    AccentColor.Blue -> stringResource(R.string.color_blue)
    AccentColor.Violet -> stringResource(R.string.color_violet)
    AccentColor.Green -> stringResource(R.string.color_green)
    AccentColor.Orange -> stringResource(R.string.color_orange)
}

@Composable
private fun themeModeLabel(themeMode: AppThemeMode): String = when (themeMode) {
    AppThemeMode.System -> stringResource(R.string.theme_system)
    AppThemeMode.Light -> stringResource(R.string.theme_light)
    AppThemeMode.Dark -> stringResource(R.string.theme_dark)
}
