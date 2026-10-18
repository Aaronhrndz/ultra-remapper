package com.bennybar.ultraremapper

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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val expected = ComponentName(context, BottomButtonService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.split(':')
        .mapNotNull { ComponentName.unflattenFromString(it) }
        .any { it == expected }
}

private fun hasHardwareActionButton(context: Context): Boolean {
    // 1. Check if Samsung's action button setting exists in global settings
    val currentSetting = try {
        Settings.Global.getString(context.contentResolver, "setting_action_button_press")
    } catch (_: Throwable) {
        null
    }
    if (currentSetting != null) return true

    // 2. Fallback check for Galaxy Watch Ultra / Ultra 2 / Watch 8 Classic hardware signatures
    val model = android.os.Build.MODEL.uppercase()
    val product = android.os.Build.PRODUCT.uppercase()
    val device = android.os.Build.DEVICE.uppercase()
    val ultraKeywords = listOf("ULTRA", "CLASSIC", "SM-L700", "SM-L705", "SM-R9", "SM-L3")
    return ultraKeywords.any { kw -> model.contains(kw) || product.contains(kw) || device.contains(kw) }
}

private fun isActionButtonRouted(context: Context): Boolean {
    if (!hasHardwareActionButton(context)) return true
    val expected = "${context.packageName}/${ActionButtonService::class.java.name}"
    val current = try {
        Settings.Global.getString(context.contentResolver, "setting_action_button_press")
    } catch (_: Throwable) {
        null
    }
    return current == expected
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
) {
    val allReady: Boolean
        get() = (!hasActionButton || actionRouted) && overlayAllowed && a11yEnabled && mediaListenerEnabled && secureSettingsGranted
}

private fun readSetupState(context: Context): SetupState = SetupState(
    hasActionButton = hasHardwareActionButton(context),
    actionRouted = isActionButtonRouted(context),
    overlayAllowed = Settings.canDrawOverlays(context),
    a11yEnabled = isAccessibilityServiceEnabled(context),
    mediaListenerEnabled = isNotificationListenerEnabled(context),
    secureSettingsGranted = hasSecureSettingsPermission(context),
    haInstalled = isPackageInstalled(context, "io.homeassistant.companion.android"),
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
                                setup = readSetupState(context)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    val nav = rememberSwipeDismissableNavController()
                    LaunchedEffect(targetDestination, updateInfo) {
                        if (targetDestination == "update_details" && updateInfo != null) {
                            nav.navigate("update_details")
                        }
                    }
                    SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            Home(
                                setup = setup,
                                updateInfo = updateInfo,
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
                                onPick = { nav.navigate("pick/$it") }
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

@Composable
private fun Home(
    setup: SetupState,
    updateInfo: ReleaseUpdate?,
    onOpenUpdateDetails: () -> Unit,
    onOpenA11ySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenNotifListeners: () -> Unit,
    onPick: (String) -> Unit,
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
        containerColor = SlateCard,
        contentColor = Color.White,
        secondaryContentColor = UltraOrange
    )
    val cardUnconfiguredColors = ButtonDefaults.buttonColors(
        containerColor = SlateCard,
        contentColor = Color.White,
        secondaryContentColor = Color(0xFF8A93A2)
    )

    val actionRows = remember {
        Mapping.actionGestures.map { (gesture, nameRes) ->
            val act = Mapping.get(context, gesture)
            Triple(gesture, context.getString(nameRes), act to Mapping.label(context, act))
        }
    }
    val bottomRows = remember {
        Mapping.bottomGestures.map { (gesture, nameRes) ->
            val act = Mapping.get(context, gesture)
            Triple(gesture, context.getString(nameRes), act to Mapping.label(context, act))
        }
    }

    val allConfiguredActions = remember(actionRows, bottomRows) {
        (actionRows.map { it.third.first } + bottomRows.map { it.third.first }).toSet()
    }

    val needsA11y = remember(allConfiguredActions, bottomRows) {
        bottomRows.any { it.third.first != "none" } || allConfiguredActions.contains("flashlight")
    }
    val needsSecureSettings = remember(allConfiguredActions) {
        allConfiguredActions.any { it in setOf("glove_mode", "lte_toggle", "adb_mode") }
    }
    val needsMediaBridge = remember(allConfiguredActions) {
        allConfiguredActions.any { act -> Mapping.mediaActions.any { it.first == act } }
    }

    val hasPendingRequiredAction = (!setup.a11yEnabled && needsA11y) ||
            (!setup.actionRouted) ||
            (!setup.overlayAllowed) ||
            (!setup.mediaListenerEnabled && needsMediaBridge) ||
            (!setup.secureSettingsGranted && needsSecureSettings)

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 26.dp, bottom = 76.dp, start = 10.dp, end = 10.dp)
        ) {
            if (updateInfo != null) {
                item(key = "update_banner") {
                    Button(
                        onClick = onOpenUpdateDetails,
                        label = {
                            Text(
                                context.getString(R.string.update_banner_title, updateInfo.versionName),
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
                if (!setup.actionRouted) {
                    item(key = "diag_route") {
                        Button(
                            onClick = onOpenAppInfo,
                            label = { Text(context.getString(R.string.diag_route_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text(context.getString(R.string.diag_route_sub), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = highlightColors,
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }
                if (!setup.overlayAllowed) {
                    item(key = "diag_overlay") {
                        Button(
                            onClick = onOpenAppInfo,
                            label = { Text(context.getString(R.string.diag_overlay_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text(context.getString(R.string.diag_overlay_sub), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = highlightColors,
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
                            onClick = onOpenNotifListeners,
                            label = { Text(context.getString(R.string.diag_media_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text(context.getString(R.string.diag_media_sub), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = highlightColors,
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
                            onClick = {},
                            label = { Text(context.getString(R.string.diag_secure_settings), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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

            if (setup.hasActionButton) {
                item(key = "hdr_action") {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_watch_quick_button),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = context.getString(R.string.hdr_center_button),
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                items(actionRows, key = { it.first }) { (gesture, name, actPair) ->
                    val (currentAction, currentLabel) = actPair
                    val isConfigured = currentAction != "none"
                    Button(
                        onClick = { onPick(gesture) },
                        label = {
                            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        secondaryLabel = {
                            Text(
                                currentLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = if (isConfigured) cardConfiguredColors else cardUnconfiguredColors,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    )
                }

                item(key = "section_spacer") {
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            item(key = "hdr_bottom") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_watch_bottom_button),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = context.getString(R.string.hdr_bottom_button),
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(bottomRows, key = { it.first }) { (gesture, name, actPair) ->
                val (currentAction, currentLabel) = actPair
                val isConfigured = currentAction != "none"
                Button(
                    onClick = { onPick(gesture) },
                    label = {
                        Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = {
                        Text(
                            currentLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = if (isConfigured) cardConfiguredColors else cardUnconfiguredColors,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            item(key = "app_version_footer") {
                val installedVersion = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.1.0"
                    } catch (e: Exception) {
                        "1.1.0"
                    }
                }
                Text(
                    text = context.getString(R.string.app_version, installedVersion),
                    color = Color(0xFF6A717E),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 4.dp)
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

            // Utility Actions Section (separated with 20.dp top padding)
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

            // Home Assistant Section (separated with 20.dp top padding, grayed out if not installed)
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

            // Samsung Functions Section (separated with 20.dp top padding)
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

            // Media & Volume Section (separated with 20.dp top padding)
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
                    Text(
                        context.getString(R.string.update_details_title, update.versionName),
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
                        .transformedHeight(this, transformationSpec)
                )
            }

            if (isDownloading && downloadFraction > 0f) {
                item(key = "upd_progress_indicator") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(downloadFraction.coerceIn(0.02f, 1f))
                                    .height(6.dp)
                                    .background(UltraOrange)
                            )
                        }
                    }
                }
            }

            item(key = "upd_open_phone") {
                Button(
                    onClick = onOpenOnPhone,
                    label = {
                        Text(context.getString(R.string.update_open_phone), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = {
                        Text(context.getString(R.string.update_open_phone_sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateCard,
                        contentColor = Color.White,
                        secondaryContentColor = Color(0xFF9EA7B8)
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            item(key = "upd_adb_hint") {
                Button(
                    onClick = {},
                    label = {
                        Text(context.getString(R.string.update_adb_hint_title), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = {
                        Text("adb install -r ultra-remapper-v${update.versionName}.apk", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateCard,
                        contentColor = Color.White,
                        secondaryContentColor = Color(0xFF9EA7B8)
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
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

