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
import androidx.compose.foundation.layout.Arrangement
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

private fun isActionButtonRouted(context: Context): Boolean {
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

private data class SetupState(
    val actionRouted: Boolean,
    val overlayAllowed: Boolean,
    val a11yEnabled: Boolean,
    val mediaListenerEnabled: Boolean,
) {
    val allReady: Boolean
        get() = actionRouted && overlayAllowed && a11yEnabled && mediaListenerEnabled
}

private fun readSetupState(context: Context): SetupState = SetupState(
    actionRouted = isActionButtonRouted(context),
    overlayAllowed = Settings.canDrawOverlays(context),
    a11yEnabled = isAccessibilityServiceEnabled(context),
    mediaListenerEnabled = isNotificationListenerEnabled(context),
)

private data class AppEntry(
    val action: String,
    val label: String,
    val icon: ImageBitmap?,
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            Home(
                                setup = setup,
                                updateInfo = updateInfo,
                                onOpenUpdateDetails = {
                                    updateInfo?.let { UpdateChecker.openReleaseOnPhone(context, it) }
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
        Mapping.actionGestures.map { (gesture, name) ->
            val act = Mapping.get(context, gesture)
            Triple(gesture, name, act to Mapping.label(context, act))
        }
    }
    val bottomRows = remember {
        Mapping.bottomGestures.map { (gesture, name) ->
            val act = Mapping.get(context, gesture)
            Triple(gesture, name, act to Mapping.label(context, act))
        }
    }

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
                                "Update v${updateInfo.versionName} available",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        secondaryLabel = {
                            Text("Tap for changelog & phone link", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        colors = orangeColors,
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    )
                }
            }

            if (!setup.allReady) {
                item(key = "diag_header") {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text("Setup Status", color = WarnAmber)
                    }
                }
                if (!setup.a11yEnabled) {
                    item(key = "diag_a11y") {
                        Button(
                            onClick = onOpenA11ySettings,
                            label = { Text("Enable Accessibility", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text("Bottom button & Flashlight Boost", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = orangeColors,
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }
                if (!setup.actionRouted) {
                    item(key = "diag_route") {
                        Button(
                            onClick = onOpenAppInfo,
                            label = { Text("Quick Button Route Off", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text("Run ADB setup step 3", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                            label = { Text("Overlay Permission Off", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text("Run ADB setup step 4", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = highlightColors,
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }
                if (!setup.mediaListenerEnabled) {
                    item(key = "diag_media") {
                        Button(
                            onClick = onOpenNotifListeners,
                            label = { Text("Media Bridge Off", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text("Enable for KDE Connect", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = highlightColors,
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }
            }

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
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Center Button",
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
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bottom Button",
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
        }
    }
}

@Composable
private fun ActionPicker(
    gesture: String,
    appsCount: Int,
    onOpenAppList: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val current = remember(gesture) { Mapping.get(context, gesture) }
    val currentLabel = remember(current) { Mapping.label(context, current) }
    val title = remember(gesture) { Mapping.gestures.toMap()[gesture] ?: gesture }
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

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 26.dp, bottom = 76.dp, start = 10.dp, end = 10.dp)
        ) {
            item(key = "picker_title") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(title, color = UltraOrange, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }

            // Open App row at the top (navigates to app list with real icons)
            item(key = "open_app_entry") {
                RadioButton(
                    selected = isApp,
                    onSelect = onOpenAppList,
                    label = {
                        Text(
                            "Open App…",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            if (isApp) currentLabel else "Choose from $appsCount apps ›",
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

            item(key = "none_entry") {
                val selected = current == "none"
                RadioButton(
                    selected = selected,
                    onSelect = { onSelect("none") },
                    label = {
                        Text(
                            "Nothing",
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

            item(key = "hdr_samsung") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text("Samsung Functions", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            items(Mapping.samsungActions, key = { "sam_${it.first}" }) { (action, label) ->
                val selected = action == current
                RadioButton(
                    selected = selected,
                    onSelect = { onSelect(action) },
                    label = {
                        Text(
                            label,
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

            item(key = "hdr_media") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text("Media & Volume", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            items(Mapping.mediaActions, key = { "med_${it.first}" }) { (action, label) ->
                val selected = action == current
                RadioButton(
                    selected = selected,
                    onSelect = { onSelect(action) },
                    label = {
                        Text(
                            label,
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
                    Text("Select App", color = UltraOrange, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
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
                        "Update v${update.versionName}",
                        color = UltraOrange,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item(key = "upd_open_phone") {
                Button(
                    onClick = onOpenOnPhone,
                    label = {
                        Text("Open on Phone", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = {
                        Text("Opens GitHub release page", maxLines = 1, overflow = TextOverflow.Ellipsis)
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

            item(key = "upd_adb_hint") {
                Button(
                    onClick = {},
                    label = {
                        Text("Install via ADB", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        Text("What's New", color = Color.White, fontWeight = FontWeight.Bold)
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

