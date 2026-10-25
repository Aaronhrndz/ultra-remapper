package com.aaronhrndz.ultraremapper

import com.aaronhrndz.ultraremapper.R

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.RadioButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val UltraOrange = Color(0xFFFF5500)
private val SlateCard = Color(0xFF1F232A)
private val SlateHighlight = Color(0xFF2B313B)
private val WarnAmber = Color(0xFFFF9800)



private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val a11yGlobal = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
    if (!a11yGlobal) return false
    val expected = ComponentName(context, BottomButtonService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.split(':')
        .mapNotNull { ComponentName.unflattenFromString(it) }
        .any { it == expected }
}

private fun hasHardwareActionButton(context: Context): Boolean = true

private const val SAMSUNG_HEALTH_ACTION_SERVICE = "com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService"

private const val DEFAULT_RECENT_APPS_ALIAS = "com.samsung.android.apps.wearable.recent/com.samsung.android.apps.wearable.recent.RecentActivityAlias"

private fun syncSamsungRoutes(context: Context) {
    if (!hasSecureSettingsPermission(context)) return
    try {
        val isMasterOn = Mapping.isEnabled(context)

        // 1. Top button long press (Assistant route)
        val topLongAction = Mapping.get(context, "top_long")
        if (isMasterOn && topLongAction != "none") {
            val currentAssistant = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
                ?: Settings.Secure.getString(context.contentResolver, "assistant")
            val isOurs = currentAssistant?.contains(context.packageName) == true
            if (isOurs) {
                Settings.Global.putString(context.contentResolver, "setting_home_key_long_press", "googlevoiceassistant/googlevoiceassistant")
            }
        }

        // 2. Top button double press
        val topDoubleAction = Mapping.get(context, "top_double")
        val expectedTopDouble = if (isMasterOn && topDoubleAction != "none") {
            "${context.packageName}/${TopDoubleTrampolineActivity::class.java.name}"
        } else {
            DEFAULT_RECENT_APPS_ALIAS
        }
        val currentTopDouble = Settings.Global.getString(context.contentResolver, "setting_home_key_double_press")
        if (currentTopDouble != expectedTopDouble) {
            Settings.Global.putString(context.contentResolver, "setting_home_key_double_press", expectedTopDouble)
        }

        // 3. Toc-toc (Wrist Knock gesture)
        val toctocAction = Mapping.get(context, "toctoc")
        if (isMasterOn && toctocAction != "none") {
            Settings.Global.putString(context.contentResolver, "setting_gesture_launch_on", "1")
            Settings.Global.putString(context.contentResolver, "setting_gesture_launch_app", "app/${context.packageName}")
            Settings.Global.putString(
                context.contentResolver,
                "setting_gesture_launch_selected_app",
                "${context.packageName}/${TocTocTrampolineActivity::class.java.name}"
            )
        } else if (!isMasterOn || toctocAction == "none") {
            val currentLaunchApp = Settings.Global.getString(context.contentResolver, "setting_gesture_launch_app")
            if (currentLaunchApp?.contains(context.packageName) == true) {
                Settings.Global.putString(context.contentResolver, "setting_gesture_launch_on", "0")
                Settings.Global.putString(context.contentResolver, "setting_gesture_launch_app", "fixed/turn_on_flashlight")
                Settings.Global.putString(context.contentResolver, "setting_gesture_launch_selected_app", "")
            }
        }
    } catch (_: Throwable) {}
}

private fun syncTopButtonRoute(context: Context) = syncSamsungRoutes(context)

private fun isActionButtonRouted(context: Context, autoFix: Boolean = true): Boolean {
    if (!hasHardwareActionButton(context)) return true

    // Do NOT fight with Samsung Health if workout is currently active
    if (BottomButtonService.instance?.isSamsungHealthWorkoutActive() == true) {
        return true
    }

    val remapperTarget = "${context.packageName}/${ActionButtonService::class.java.name}"
    val isMasterOn = Mapping.isEnabled(context)
    val expected = if (!isMasterOn) {
        SAMSUNG_HEALTH_ACTION_SERVICE
    } else {
        remapperTarget
    }

    val current = try {
        Settings.Global.getString(context.contentResolver, "setting_action_button_press")
    } catch (_: Throwable) {
        null
    }
    if (current == expected) return true

    // Only auto-route if explicitly requested (e.g. user toggled setting or onResume)
    // NEVER mutate Settings.Global silently inside periodic background polling!
    if (autoFix && hasSecureSettingsPermission(context)) {
        try {
            Settings.Global.putString(context.contentResolver, "setting_action_button_press", expected)
            return true
        } catch (_: Throwable) {}
    }
    return false
}

private fun isTocTocConfigured(context: Context): Boolean {
    return try {
        val launchApp = Settings.Global.getString(context.contentResolver, "setting_gesture_launch_app")
        val launchOn = Settings.Global.getInt(context.contentResolver, "setting_gesture_launch_on", 0)
        launchOn == 1 && launchApp?.contains(context.packageName) == true
    } catch (_: Throwable) {
        false
    }
}

private fun openTocTocSettings(context: Context) {
    try {
        val intent = Intent("android.settings.BUTTONS_GESTURES_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Toast.makeText(context, context.getString(R.string.toast_select_toctoc), Toast.LENGTH_LONG).show()
    } catch (_: Throwable) {
        try {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            Toast.makeText(context, context.getString(R.string.toast_select_toctoc), Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {}
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val expected = ComponentName(context, MediaControlService::class.java)
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return enabledListeners.split(':')
        .mapNotNull { ComponentName.unflattenFromString(it) }
        .any { it == expected }
}

private fun drawableToImageBitmap(drawable: Drawable, sizePx: Int = 56): ImageBitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap.asImageBitmap()
    }
    val bmp = Bitmap.createBitmap(
        drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(sizePx),
        drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(sizePx),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp.asImageBitmap()
}

private fun hasSecureSettingsPermission(context: Context): Boolean =
    context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun isDefaultAssistant(context: Context): Boolean {
    return try {
        val cur = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
            ?: Settings.Secure.getString(context.contentResolver, "assistant")
        cur?.contains(context.packageName) == true
    } catch (_: Throwable) {
        false
    }
}

private fun openAssistantSettings(context: Context) {
    try {
        val intent = Intent("android.settings.VOICE_INPUT_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Toast.makeText(context, context.getString(R.string.toast_select_assistant), Toast.LENGTH_LONG).show()
    } catch (_: Throwable) {
        try {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            Toast.makeText(context, context.getString(R.string.toast_select_assistant), Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {}
    }
}

private fun isPackageInstalled(context: Context, pkg: String): Boolean =
    try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

private data class SetupState(
    val hasActionButton: Boolean,
    val actionRouted: Boolean,
    val overlayAllowed: Boolean,
    val a11yEnabled: Boolean,
    val mediaListenerEnabled: Boolean,
    val secureSettingsGranted: Boolean,
    val haInstalled: Boolean,
    val isAssistant: Boolean,
    val isTocTocConfigured: Boolean,
) {
    val allReady: Boolean
        get() = (!hasActionButton || actionRouted) && overlayAllowed && a11yEnabled && mediaListenerEnabled && secureSettingsGranted
}

private fun readSetupState(context: Context): SetupState = SetupState(
    hasActionButton = hasHardwareActionButton(context),
    actionRouted = isActionButtonRouted(context, autoFix = false),
    overlayAllowed = Settings.canDrawOverlays(context),
    a11yEnabled = isAccessibilityServiceEnabled(context),
    mediaListenerEnabled = isNotificationListenerEnabled(context),
    secureSettingsGranted = hasSecureSettingsPermission(context),
    haInstalled = isPackageInstalled(context, "io.homeassistant.companion.android"),
    isAssistant = isDefaultAssistant(context),
    isTocTocConfigured = isTocTocConfigured(context),
)

private data class AppEntry(
    val action: String,
    val label: String,
    val icon: ImageBitmap?,
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UpdateChecker.scheduleDailyCheck(this)
        // Clean any leftover APKs in cache asynchronously to keep watch storage minimal
        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            try {
                java.io.File(cacheDir, "updates").listFiles()?.forEach { file -> file.delete() }
            } catch (_: Exception) {}
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        val targetDestination = intent?.getStringExtra("open_destination") ?: "home"

        setContent {
            MaterialTheme {
                AppScaffold {
                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current
                    var setup by remember { mutableStateOf(readSetupState(context)) }
                    var updateInfo by remember { mutableStateOf<ReleaseUpdate?>(null) }
                    var installedApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
                    var mappingVersion by remember { mutableStateOf(0) }

                    LaunchedEffect(Unit) {
                        UpdateChecker.check(context) { info ->
                            updateInfo = info
                        }
                        installedApps = withContext(Dispatchers.IO) {
                            val pm = context.packageManager
                            pm.queryIntentActivities(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                                0
                            )
                                .map { it.activityInfo.packageName }
                                .filter { it != context.packageName }
                                .distinct()
                                .map { pkg ->
                                    val iconBmp = try {
                                        drawableToImageBitmap(pm.getApplicationIcon(pkg))
                                    } catch (_: Throwable) {
                                        null
                                    }
                                    AppEntry(
                                        action = "app:$pkg",
                                        label = Mapping.label(context, "app:$pkg"),
                                        icon = iconBmp
                                    )
                                }
                                .sortedBy { it.label.lowercase() }
                        }
                    }

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                isActionButtonRouted(context)
                                syncTopButtonRoute(context)
                                setup = readSetupState(context)
                                UpdateChecker.check(context) { info ->
                                    updateInfo = info
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    val nav = rememberSwipeDismissableNavController()
                    var splashVisible by remember { mutableStateOf(targetDestination == "home") }

                    LaunchedEffect(targetDestination, updateInfo) {
                        if (targetDestination == "update_details" && updateInfo != null) {
                            nav.navigate("update_details")
                        }
                    }

                    if (splashVisible) {
                        AnimatedSplashScreen(onFinished = { splashVisible = false })
                    } else {
                        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            Home(
                                setup = setup,
                                updateInfo = updateInfo,
                                mappingVersion = mappingVersion,
                                onOpenUpdateDetails = {
                                    nav.navigate("update_details")
                                },
                                onOpenA11ySettings = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    } catch (_: Throwable) {}
                                },
                                onOpenAppInfo = {
                                    try {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                        )
                                    } catch (_: Throwable) {}
                                },
                                onOpenNotifListeners = {
                                    try {
                                        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                                    } catch (_: Throwable) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                        } catch (_: Throwable) {}
                                    }
                                },
                                onShowAdbHelp = { helpText ->
                                    nav.navigate("help/${Uri.encode(helpText)}")
                                },
                                onPick = { nav.navigate("pick/$it") },
                                onOpenDevOptions = { nav.navigate("dev_options") }
                            )
                        }
                        composable("help/{text}") { entry ->
                            val text = Uri.decode(entry.arguments?.getString("text") ?: "")
                            AdbHelpScreen(text = text, onDone = { nav.popBackStack() })
                        }
                        composable("dev_options") {
                            DevOptionsScreen(
                                onDone = {
                                    UpdateChecker.check(context) { info ->
                                        updateInfo = info
                                    }
                                    nav.popBackStack()
                                }
                            )
                        }
                        composable("update_details") {
                            val info = updateInfo
                            if (info != null) {
                                UpdateDetailsScreen(
                                    update = info,
                                    onOpenOnPhone = { UpdateChecker.openReleaseOnPhone(context, info) }
                                )
                            }
                        }
                        composable("pick/{gesture}") { entry ->
                            val gesture = entry.arguments?.getString("gesture")!!
                            ActionPicker(
                                gesture = gesture,
                                appsCount = installedApps.size,
                                haInstalled = setup.haInstalled,
                                onOpenAppList = { nav.navigate("pick_app/$gesture") },
                                onSelect = { action ->
                                    Mapping.set(context, gesture, action)
                                    isActionButtonRouted(context)
                                    syncTopButtonRoute(context)
                                    mappingVersion++
                                    nav.popBackStack()
                                }
                            )
                        }
                        composable("pick_app/{gesture}") { entry ->
                            val gesture = entry.arguments?.getString("gesture")!!
                            AppListPicker(
                                gesture = gesture,
                                apps = installedApps,
                                onSelect = { action ->
                                    Mapping.set(context, gesture, action)
                                    isActionButtonRouted(context)
                                    syncTopButtonRoute(context)
                                    mappingVersion++
                                    nav.popBackStack("home", inclusive = false)
                                }
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun Home(
    setup: SetupState,
    updateInfo: ReleaseUpdate?,
    mappingVersion: Int,
    onOpenUpdateDetails: () -> Unit,
    onOpenA11ySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenNotifListeners: () -> Unit,
    onShowAdbHelp: (String) -> Unit,
    onPick: (String) -> Unit,
    onOpenDevOptions: () -> Unit,
) {
    val context = LocalContext.current
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val orangeColors = ButtonDefaults.buttonColors(
        containerColor = UltraOrange,
        contentColor = Color.White,
        secondaryContentColor = Color.White.copy(alpha = 0.9f)
    )
    val highlightColors = ButtonDefaults.buttonColors(containerColor = SlateHighlight)
    val cardConfiguredColors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFF282E38),
        contentColor = Color.White,
        secondaryContentColor = UltraOrange
    )
    val cardUnconfiguredColors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFF282E38),
        contentColor = Color.White,
        secondaryContentColor = Color(0xFF9EA7B8)
    )

    val actionRows = remember(mappingVersion) {
        Mapping.actionGestures.map { (gesture, nameRes) ->
            val act = Mapping.get(context, gesture)
            Triple(gesture, context.getString(nameRes), act to Mapping.label(context, act))
        }
    }
    val topRows = remember(mappingVersion) {
        Mapping.topGestures.map { (gesture, nameRes) ->
            val act = Mapping.get(context, gesture)
            Triple(gesture, context.getString(nameRes), act to Mapping.label(context, act))
        }
    }
    val bottomRows = remember(mappingVersion) {
        Mapping.bottomGestures.map { (gesture, nameRes) ->
            val act = Mapping.get(context, gesture)
            Triple(gesture, context.getString(nameRes), act to Mapping.label(context, act))
        }
    }

    val gestureRows = remember(mappingVersion) {
        Mapping.gestureList.map { (gesture, nameRes) ->
            val act = Mapping.get(context, gesture)
            Triple(gesture, context.getString(nameRes), act to Mapping.label(context, act))
        }
    }

    val allConfiguredActions = remember(actionRows, bottomRows, topRows, gestureRows) {
        (actionRows.map { it.third.first } + bottomRows.map { it.third.first } + topRows.map { it.third.first } + gestureRows.map { it.third.first }).toSet()
    }

    var isMasterEnabled by remember { mutableStateOf(Mapping.isEnabled(context)) }

    val needsA11y = remember(allConfiguredActions, bottomRows) {
        bottomRows.any { it.third.first != "none" } || allConfiguredActions.contains("flashlight")
    }
    val needsSecureSettings = remember(allConfiguredActions, gestureRows, topRows) {
        allConfiguredActions.any { it in setOf("glove_mode", "lte_toggle", "adb_mode") } ||
                gestureRows.any { it.third.first != "none" } ||
                Mapping.get(context, "top_double") != "none"
    }
    val needsMediaBridge = remember(allConfiguredActions, setup.a11yEnabled) {
        setup.a11yEnabled || allConfiguredActions.any { act -> Mapping.mediaActions.any { it.first == act } }
    }

    val needsOverlay = remember(allConfiguredActions) {
        allConfiguredActions.contains("red_flashlight")
    }
    val needsAssistant = remember(allConfiguredActions, isMasterEnabled) {
        isMasterEnabled && Mapping.get(context, "top_long") != "none"
    }

    val needsTocToc = remember(allConfiguredActions, isMasterEnabled) {
        isMasterEnabled && Mapping.get(context, "toctoc") != "none"
    }

    val hasPendingRequiredAction = (!setup.a11yEnabled && needsA11y) ||
            (setup.hasActionButton && !setup.actionRouted) ||
            (!setup.overlayAllowed && needsOverlay) ||
            (!setup.mediaListenerEnabled && needsMediaBridge) ||
            (!setup.secureSettingsGranted && needsSecureSettings) ||
            (!setup.isAssistant && needsAssistant) ||
            (!setup.isTocTocConfigured && needsTocToc)

    ScreenScaffold(scrollState = columnState) { _ ->
        var isHapticEnabled by remember { mutableStateOf(Mapping.isHapticFeedbackEnabled(context)) }
        var isTopExpanded by remember { mutableStateOf(true) }
        var isActionExpanded by remember { mutableStateOf(true) }
        var isBottomExpanded by remember { mutableStateOf(true) }
        var isGesturesExpanded by remember { mutableStateOf(true) }

        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 42.dp, bottom = 76.dp, start = 10.dp, end = 10.dp)
        ) {
            item(key = "master_switch") {
                Button(
                    onClick = {
                        val next = !isMasterEnabled
                        isMasterEnabled = next
                        Mapping.setEnabled(context, next)
                        isActionButtonRouted(context)
                        syncTopButtonRoute(context)
                    },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                context.getString(R.string.master_switch_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(20.dp)
                                    .clip(CircleShape)
                                    .background(if (isMasterEnabled) UltraOrange else Color(0xFF434954))
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .align(if (isMasterEnabled) Alignment.CenterEnd else Alignment.CenterStart)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateCard,
                        contentColor = Color.White
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }





            if (updateInfo != null) {
                item(key = "update_banner") {
                    Button(
                        onClick = onOpenUpdateDetails,
                        label = {
                            val bannerTitleRes = if (updateInfo.isPrerelease) {
                                R.string.update_banner_title_beta
                            } else {
                                R.string.update_banner_title
                            }
                            Text(
                                context.getString(bannerTitleRes, updateInfo.versionName),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        secondaryLabel = {
                            Text(context.getString(R.string.update_banner_sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        colors = orangeColors,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    )
                }
            }

            if (hasPendingRequiredAction) {
                item(key = "diag_header") {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text(context.getString(R.string.diag_header_status), color = UltraOrange, fontWeight = FontWeight.Bold)
                    }
                }
                if (!setup.isAssistant && needsAssistant) {
                    item(key = "diag_assistant") {
                        Button(
                            onClick = {
                                openAssistantSettings(context)
                            },
                            label = {
                                Text(
                                    context.getString(R.string.diag_assistant_title),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            secondaryLabel = {
                                Text(context.getString(R.string.diag_assistant_sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            colors = orangeColors,
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }
                if (!setup.isTocTocConfigured && needsTocToc) {
                    item(key = "diag_toctoc") {
                        Button(
                            onClick = {
                                openTocTocSettings(context)
                            },
                            label = {
                                Text(
                                    context.getString(R.string.diag_toctoc_title),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            secondaryLabel = {
                                Text(context.getString(R.string.diag_toctoc_sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            colors = orangeColors,
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }
                if (setup.hasActionButton && !setup.actionRouted) {
                    item(key = "diag_route") {
                        Button(
                            onClick = {
                                onShowAdbHelp(context.getString(R.string.diag_dialog_route_body))
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        context.getString(R.string.diag_route_title),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF2E323A))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ADB",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            secondaryLabel = { Text(context.getString(R.string.diag_route_sub), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = orangeColors,
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }
                if (!setup.overlayAllowed && needsOverlay) {
                    item(key = "diag_overlay") {
                        Button(
                            onClick = {
                                onShowAdbHelp(context.getString(R.string.diag_dialog_overlay_body))
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        context.getString(R.string.diag_overlay_title),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF2E323A))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ADB",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            secondaryLabel = { Text(context.getString(R.string.diag_overlay_sub), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = orangeColors,
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }
                if (!setup.a11yEnabled && needsA11y) {
                    item(key = "diag_a11y") {
                        Button(
                            onClick = onOpenA11ySettings,
                            label = { Text(context.getString(R.string.diag_a11y_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text(context.getString(R.string.diag_a11y_sub), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = orangeColors,
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }
                if (!setup.mediaListenerEnabled && needsMediaBridge) {
                    item(key = "diag_media") {
                        Button(
                            onClick = {
                                onShowAdbHelp(context.getString(R.string.diag_dialog_media_body))
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        context.getString(R.string.diag_media_title),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF2E323A))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ADB",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            secondaryLabel = { Text(context.getString(R.string.diag_media_sub), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = orangeColors,
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }
                if (!setup.secureSettingsGranted && needsSecureSettings) {
                    item(key = "diag_secure") {
                        Button(
                            onClick = {
                                onShowAdbHelp(context.getString(R.string.diag_dialog_secure_body))
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        context.getString(R.string.diag_secure_settings),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF2E323A))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ADB",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            secondaryLabel = { Text(context.getString(R.string.diag_secure_settings_sub), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = orangeColors,
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }
                item(key = "diag_bottom_spacer") {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Top Button Collapsible Section Card
            item(key = "master_bottom_spacer") {
                Spacer(modifier = Modifier.height(14.dp))
            }

            item(key = "card_top_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF181B22))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                isTopExpanded = !isTopExpanded
                                HapticFeedbackHelper.playButtonHaptic(context)
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_watch_top_button),
                                contentDescription = null,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = context.getString(R.string.hdr_top_button),
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        CollapseChevron(isExpanded = isTopExpanded)
                    }

                    if (isTopExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        topRows.forEachIndexed { idx, (gesture, name, actPair) ->
                            if (idx > 0) Spacer(modifier = Modifier.height(4.dp))
                            val (currentAction, currentLabel) = actPair
                            val isConfigured = currentAction != "none"
                            Button(
                                onClick = { onPick(gesture) },
                                label = {
                                    Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                secondaryLabel = {
                                    Text(currentLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                colors = if (isConfigured) cardConfiguredColors else cardUnconfiguredColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item(key = "top_section_spacer") {
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (setup.hasActionButton) {
                // Action Button Collapsible Section Card
                item(key = "card_action_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF181B22))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    isActionExpanded = !isActionExpanded
                                    HapticFeedbackHelper.playButtonHaptic(context)
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_watch_quick_button),
                                    contentDescription = null,
                                    modifier = Modifier.size(38.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = context.getString(R.string.hdr_center_button),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            CollapseChevron(isExpanded = isActionExpanded)
                        }

                        val hasShealthMapped = allConfiguredActions.contains("shealth_workout")
                        if (hasShealthMapped && isActionExpanded) {
                            Text(
                                text = context.getString(R.string.hdr_center_button_note),
                                color = Color(0xFF9EA7B8),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }

                        if (isActionExpanded) {
                            Spacer(modifier = Modifier.height(6.dp))
                            actionRows.forEachIndexed { idx, (gesture, name, actPair) ->
                                if (idx > 0) Spacer(modifier = Modifier.height(4.dp))
                                val (currentAction, currentLabel) = actPair
                                val isConfigured = currentAction != "none"
                                Button(
                                    onClick = { onPick(gesture) },
                                    label = {
                                        Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    secondaryLabel = {
                                        Text(currentLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    colors = if (isConfigured) cardConfiguredColors else cardUnconfiguredColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                item(key = "section_spacer") {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // Bottom Button Collapsible Section Card
            item(key = "card_bottom_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF181B22))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                isBottomExpanded = !isBottomExpanded
                                HapticFeedbackHelper.playButtonHaptic(context)
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_watch_bottom_button),
                                contentDescription = null,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = context.getString(R.string.hdr_bottom_button),
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        CollapseChevron(isExpanded = isBottomExpanded)
                    }

                    if (isBottomExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        bottomRows.forEachIndexed { idx, (gesture, name, actPair) ->
                            if (idx > 0) Spacer(modifier = Modifier.height(4.dp))
                            val (currentAction, currentLabel) = actPair
                            val isConfigured = currentAction != "none"
                            Button(
                                onClick = { onPick(gesture) },
                                label = {
                                    Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                secondaryLabel = {
                                    Text(currentLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                colors = if (isConfigured) cardConfiguredColors else cardUnconfiguredColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Samsung Gestures Collapsible Section Card
            item(key = "card_gestures_spacer") {
                Spacer(modifier = Modifier.height(14.dp))
            }

            item(key = "card_gestures_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF181B22))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                isGesturesExpanded = !isGesturesExpanded
                                HapticFeedbackHelper.playButtonHaptic(context)
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_wrist_gesture),
                                contentDescription = null,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = context.getString(R.string.hdr_gestures),
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        CollapseChevron(isExpanded = isGesturesExpanded)
                    }

                    if (isGesturesExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        gestureRows.forEachIndexed { idx, (gesture, name, actPair) ->
                            if (idx > 0) Spacer(modifier = Modifier.height(4.dp))
                            val (currentAction, currentLabel) = actPair
                            val isConfigured = currentAction != "none"
                            Button(
                                onClick = { onPick(gesture) },
                                label = {
                                    Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                secondaryLabel = {
                                    Text(currentLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                colors = if (isConfigured) cardConfiguredColors else cardUnconfiguredColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item(key = "haptic_top_spacer") {
                Spacer(modifier = Modifier.height(14.dp))
            }

            item(key = "haptic_switch") {
                Button(
                    onClick = {
                        val next = !isHapticEnabled
                        isHapticEnabled = next
                        Mapping.setHapticFeedbackEnabled(context, next)
                        if (next) {
                            HapticFeedbackHelper.playButtonHaptic(context)
                        }
                    },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    context.getString(R.string.haptic_switch_title),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    context.getString(R.string.haptic_switch_subtitle),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color(0xFF9EA7B8),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(18.dp)
                                    .clip(CircleShape)
                                    .background(if (isHapticEnabled) UltraOrange.copy(alpha = 0.85f) else Color(0xFF383D47))
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .align(if (isHapticEnabled) Alignment.CenterEnd else Alignment.CenterStart)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateCard,
                        contentColor = Color.White
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            item(key = "haptic_bottom_spacer") {
                Spacer(modifier = Modifier.height(14.dp))
            }

            item(key = "app_version_footer") {
                val installedVersion = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.4.0"
                    } catch (e: Exception) {
                        "1.4.0"
                    }
                }
                var tapCount by remember { mutableStateOf(0) }
                val isBetaInstalled = remember { installedVersion.contains("beta", ignoreCase = true) }
                val isBetaChannel = remember { UpdateChecker.isBetaChannel(context) }
                val displayText = if (isBetaInstalled) {
                    context.getString(R.string.app_version, "$installedVersion (Beta)")
                } else if (isBetaChannel) {
                    context.getString(R.string.app_version, "$installedVersion [Beta Channel]")
                } else {
                    context.getString(R.string.app_version, installedVersion)
                }

                Text(
                    text = displayText,
                    color = if (isBetaChannel) UltraOrange else Color(0xFF6A717E),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 4.dp)
                        .clickable {
                            tapCount++
                            if (tapCount >= 7) {
                                tapCount = 0
                                onOpenDevOptions()
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun ActionPicker(
    gesture: String,
    appsCount: Int,
    haInstalled: Boolean,
    onOpenAppList: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val current = remember(gesture) { Mapping.get(context, gesture) }
    val currentLabel = remember(current) { Mapping.label(context, current) }
    val titleRes = remember(gesture) { Mapping.gestures.toMap()[gesture] }
    val title = remember(titleRes, gesture) { if (titleRes != null) context.getString(titleRes) else gesture }
    val isApp = remember(current) { current.startsWith("app:") }

    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val radioColors = RadioButtonDefaults.radioButtonColors(
        selectedContainerColor = SlateCard,
        selectedContentColor = Color.White,
        selectedSecondaryContentColor = UltraOrange,
        selectedControlColor = UltraOrange,
        unselectedContainerColor = SlateCard,
        unselectedContentColor = Color.White,
        unselectedSecondaryContentColor = Color(0xFF9EA7B8),
        unselectedControlColor = Color(0xFF8A93A2)
    )

    val disabledRadioColors = RadioButtonDefaults.radioButtonColors(
        selectedContainerColor = SlateCard.copy(alpha = 0.5f),
        selectedContentColor = Color(0xFF6A717E),
        selectedSecondaryContentColor = Color(0xFF5A606C),
        selectedControlColor = Color(0xFF5A606C),
        unselectedContainerColor = SlateCard.copy(alpha = 0.5f),
        unselectedContentColor = Color(0xFF6A717E),
        unselectedSecondaryContentColor = Color(0xFF5A606C),
        unselectedControlColor = Color(0xFF4A505C)
    )

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 22.dp, bottom = 80.dp, start = 8.dp, end = 8.dp)
        ) {
            item(key = "picker_title") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        title,
                        color = UltraOrange,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Open App row at the top (navigates to app list with real icons)
            item(key = "open_app_entry") {
                RadioButton(
                    selected = isApp,
                    onSelect = onOpenAppList,
                    label = {
                        Text(
                            context.getString(R.string.open_app),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    secondaryLabel = {
                        Text(
                            if (isApp) currentLabel else context.getString(R.string.choose_from_apps, appsCount),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    colors = radioColors,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            item(key = "none_entry") {
                val selected = current == "none"
                RadioButton(
                    selected = selected,
                    onSelect = { onSelect("none") },
                    label = {
                        Text(
                            context.getString(R.string.nothing),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    colors = radioColors,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            // Samsung Health Section
            item(key = "hdr_shealth") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        context.getString(R.string.hdr_shealth),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(Mapping.shealthActions, key = { "sh_${it.first}" }) { (action, labelRes) ->
                val selected = action == current
                RadioButton(
                    selected = selected,
                    onSelect = { onSelect(action) },
                    label = {
                        Text(
                            context.getString(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    colors = radioColors,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            // Samsung Functions Section
            item(key = "hdr_samsung") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        context.getString(R.string.hdr_samsung),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(Mapping.samsungActions, key = { "sam_${it.first}" }) { (action, labelRes) ->
                val selected = action == current
                RadioButton(
                    selected = selected,
                    onSelect = { onSelect(action) },
                    label = {
                        Text(
                            context.getString(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    colors = radioColors,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            // Development / ADB Section
            item(key = "hdr_dev") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        context.getString(R.string.hdr_dev),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(Mapping.devActions, key = { "dev_${it.first}" }) { (action, labelRes) ->
                val selected = action == current
                RadioButton(
                    selected = selected,
                    onSelect = { onSelect(action) },
                    label = {
                        Text(
                            context.getString(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    colors = radioColors,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            // Utility Actions Section
            item(key = "hdr_utility") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        context.getString(R.string.hdr_utility),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(Mapping.utilityActions, key = { "ut_${it.first}" }) { (action, labelRes) ->
                val selected = action == current
                RadioButton(
                    selected = selected,
                    onSelect = { onSelect(action) },
                    label = {
                        Text(
                            context.getString(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    colors = radioColors,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            // Home Assistant Section
            item(key = "hdr_home_assistant") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        context.getString(R.string.hdr_home_assistant),
                        color = if (haInstalled) Color.White else Color(0xFF7A8392),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(Mapping.homeAssistantActions, key = { "ha_${it.first}" }) { (action, labelRes) ->
                val selected = action == current
                RadioButton(
                    selected = selected,
                    enabled = haInstalled,
                    onSelect = { if (haInstalled) onSelect(action) },
                    label = {
                        Text(
                            context.getString(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    secondaryLabel = if (!haInstalled) {
                        {
                            Text(
                                context.getString(R.string.app_not_installed),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    } else null,
                    colors = if (haInstalled) radioColors else disabledRadioColors,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            // Media & Volume Section
            item(key = "hdr_media") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        context.getString(R.string.hdr_media),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(Mapping.mediaActions, key = { "med_${it.first}" }) { (action, labelRes) ->
                val selected = action == current
                RadioButton(
                    selected = selected,
                    onSelect = { onSelect(action) },
                    label = {
                        Text(
                            context.getString(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    colors = radioColors,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }
        }
    }
}

@Composable
private fun AppListPicker(
    gesture: String,
    apps: List<AppEntry>,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val current = remember(gesture) { Mapping.get(context, gesture) }
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val radioColors = RadioButtonDefaults.radioButtonColors(
        selectedContainerColor = SlateCard,
        selectedContentColor = Color.White,
        selectedControlColor = UltraOrange,
        unselectedContainerColor = SlateCard,
        unselectedContentColor = Color.White,
        unselectedControlColor = Color(0xFF8A93A2)
    )

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 26.dp, bottom = 76.dp, start = 10.dp, end = 10.dp)
        ) {
            item(key = "app_list_hdr") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(context.getString(R.string.select_app), color = UltraOrange, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }

            items(apps, key = { it.action }) { app ->
                val selected = app.action == current
                RadioButton(
                    selected = selected,
                    onSelect = { onSelect(app.action) },
                    icon = if (app.icon != null) {
                        {
                            Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                        }
                    } else null,
                    label = {
                        Text(
                            app.label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = radioColors,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }
        }
    }
}

@Composable
private fun UpdateDetailsScreen(
    update: ReleaseUpdate,
    onOpenOnPhone: () -> Unit,
) {
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val cleanNotes = remember(update.releaseNotes) {
        update.releaseNotes
            .lines()
            .map { it.trim().removePrefix("###").removePrefix("##").removePrefix("#").trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace("**", "") }
    }

    val context = LocalContext.current
    var downloadProgress by remember { mutableStateOf("") }
    var downloadFraction by remember { mutableFloatStateOf(0f) }
    val isDownloading = downloadProgress.isNotEmpty()

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 26.dp, bottom = 76.dp, start = 10.dp, end = 10.dp)
        ) {
            item(key = "upd_title") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    val titleRes = if (update.isPrerelease) {
                        R.string.update_details_title_beta
                    } else {
                        R.string.update_details_title
                    }
                    Text(
                        context.getString(titleRes, update.versionName),
                        color = UltraOrange,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item(key = "upd_install_watch") {
                Button(
                    onClick = {
                        if (!isDownloading) {
                            UpdateChecker.downloadAndInstallOnWatch(context, update) { fraction, status ->
                                downloadFraction = fraction
                                downloadProgress = status
                            }
                        }
                    },
                    label = {
                        Text(
                            if (isDownloading) downloadProgress else context.getString(R.string.update_install_watch),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        if (isDownloading && downloadFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.35f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(downloadFraction.coerceIn(0f, 1f))
                                        .height(4.dp)
                                        .background(Color.White)
                                )
                            }
                        } else {
                            Text(context.getString(R.string.update_install_watch_sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UltraOrange,
                        contentColor = Color.White,
                        secondaryContentColor = Color.White.copy(alpha = 0.9f)
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 14.dp)
                        .transformedHeight(this, transformationSpec)
                )
            }

            if (cleanNotes.isNotEmpty()) {
                item(key = "upd_notes_hdr") {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text(context.getString(R.string.update_whats_new), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                items(cleanNotes, key = { it }) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD5DCE6),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                            .transformedHeight(this, transformationSpec)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdbHelpScreen(
    text: String,
    onDone: () -> Unit,
) {
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val context = LocalContext.current

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 26.dp, bottom = 76.dp, start = 10.dp, end = 10.dp)
        ) {
            item(key = "help_title") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        context.getString(R.string.diag_dialog_adb_title),
                        color = UltraOrange,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            item(key = "help_body") {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .transformedHeight(this, transformationSpec)
                )
            }
            item(key = "help_close") {
                Button(
                    onClick = onDone,
                    label = {
                        Text(
                            context.getString(R.string.diag_dialog_close),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UltraOrange,
                        contentColor = Color.White
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }
        }
    }
}

@Composable
private fun AnimatedSplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scale = remember { Animatable(0.85f) }
    val offsetY = remember { Animatable(0f) }
    val versionAlpha = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.3.21"
        } catch (_: Exception) {
            "1.3.21"
        }
    }

    LaunchedEffect(Unit) {
        // 1. Initial short pause centered (300ms)
        delay(300L)

        // 2. Displace upward while enlarging (scale 0.85 -> 1.18, offset 0 -> -20dp) over 500ms
        coroutineScope {
            launch {
                scale.animateTo(1.18f, animationSpec = tween(500, easing = FastOutSlowInEasing))
            }
            launch {
                offsetY.animateTo(-20f, animationSpec = tween(500, easing = FastOutSlowInEasing))
            }
            launch {
                // Version code fades in smoothly as logo reaches upper position
                delay(150L)
                versionAlpha.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing))
            }
        }

        // 3. Hold complete splash (350ms)
        delay(350L)

        // 4. Smooth exit fade-out transition into menu (300ms) -> total = 1.5s
        exitAlpha.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .offset(y = offsetY.value.dp)
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .scale(scale.value),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    alpha = exitAlpha.value,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            val isBetaChannel = remember { UpdateChecker.isBetaChannel(context) }
            val isBetaInstalled = remember { versionName.contains("beta", ignoreCase = true) }
            val splashVersionText = if (isBetaInstalled) {
                "v$versionName (Beta)"
            } else if (isBetaChannel) {
                "v$versionName [Beta]"
            } else {
                "v$versionName"
            }
            Text(
                text = splashVersionText,
                color = UltraOrange.copy(alpha = versionAlpha.value * exitAlpha.value),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}


@Composable
private fun DevOptionsScreen(
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var isBeta by remember { mutableStateOf(UpdateChecker.isBetaChannel(context)) }
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 36.dp, bottom = 40.dp, start = 14.dp, end = 14.dp)
        ) {
            item(key = "dev_header") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        text = context.getString(R.string.dev_options_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item(key = "beta_switch") {
                Button(
                    onClick = {
                        val next = !isBeta
                        isBeta = next
                        UpdateChecker.setBetaChannel(context, next)
                    },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = context.getString(R.string.dev_beta_title),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(20.dp)
                                    .clip(CircleShape)
                                    .background(if (isBeta) UltraOrange else Color(0xFF434954))
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .align(if (isBeta) Alignment.CenterEnd else Alignment.CenterStart)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    },
                    secondaryLabel = {
                        Text(
                            text = context.getString(R.string.dev_beta_desc),
                            color = Color(0xFF9EA7B8),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }
        }
    }
}
