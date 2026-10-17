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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
private val SlateFolder = Color(0xFF282E38)
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
                        composable("pick/{gesture}") { entry ->
                            val gesture = entry.arguments?.getString("gesture")!!
                            CollapsiblePicker(
                                gesture = gesture,
                                apps = installedApps,
                                onSelect = { action ->
                                    Mapping.set(context, gesture, action)
                                    nav.popBackStack()
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
    onOpenA11ySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenNotifListeners: () -> Unit,
    onPick: (String) -> Unit,
) {
    val context = LocalContext.current
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = contentPadding
        ) {
            item(key = "header_title") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        text = "Ultra Remapper",
                        color = UltraOrange,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (updateInfo != null) {
                item(key = "update_banner") {
                    Button(
                        onClick = { UpdateChecker.openReleasePage(context, updateInfo) },
                        label = {
                            Text(
                                "Update v${updateInfo.versionName} available",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        secondaryLabel = {
                            Text("Check GitHub release", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UltraOrange,
                                contentColor = Color.White,
                                secondaryContentColor = Color.White.copy(alpha = 0.85f)
                            ),
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
                            colors = ButtonDefaults.buttonColors(containerColor = SlateHighlight),
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
                            colors = ButtonDefaults.buttonColors(containerColor = SlateHighlight),
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
                            colors = ButtonDefaults.buttonColors(containerColor = SlateHighlight),
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
                    Text("Quick Button", color = Color(0xFFB0B8C4))
                }
            }

            items(Mapping.actionGestures, key = { it.first }) { (gesture, name) ->
                val currentAction = Mapping.get(context, gesture)
                val currentLabel = Mapping.label(context, currentAction)
                val isConfigured = currentAction != "none"
                Button(
                    onClick = { onPick(gesture) },
                    label = {
                        Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = {
                        Text(
                            currentLabel,
                            color = if (isConfigured) UltraOrange else Color(0xFF8A93A2),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateCard,
                        contentColor = Color.White,
                        secondaryContentColor = if (isConfigured) UltraOrange else Color(0xFF8A93A2)
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            item(key = "hdr_bottom") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text("Bottom Button", color = Color(0xFFB0B8C4))
                }
            }

            items(Mapping.bottomGestures, key = { it.first }) { (gesture, name) ->
                val currentAction = Mapping.get(context, gesture)
                val currentLabel = Mapping.label(context, currentAction)
                val isConfigured = currentAction != "none"
                Button(
                    onClick = { onPick(gesture) },
                    label = {
                        Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = {
                        Text(
                            currentLabel,
                            color = if (isConfigured) UltraOrange else Color(0xFF8A93A2),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateCard,
                        contentColor = Color.White,
                        secondaryContentColor = if (isConfigured) UltraOrange else Color(0xFF8A93A2)
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
private fun CollapsiblePicker(
    gesture: String,
    apps: List<AppEntry>,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val current = Mapping.get(context, gesture)
    val currentLabel = Mapping.label(context, current)
    val title = Mapping.gestures.toMap()[gesture] ?: gesture

    val isSamsung = Mapping.samsungActions.any { it.first == current }
    val isMedia = Mapping.mediaActions.any { it.first == current }
    val isApp = current.startsWith("app:")

    var expandSamsung by rememberSaveable { mutableStateOf(isSamsung) }
    var expandMedia by rememberSaveable { mutableStateOf(isMedia) }
    var expandApps by rememberSaveable { mutableStateOf(isApp) }

    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = contentPadding
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

            // 1. Samsung Functions collapsible section
            item(key = "sec_samsung") {
                val arrow = if (expandSamsung) "▼" else "▶"
                Button(
                    onClick = { expandSamsung = !expandSamsung },
                    label = {
                        Text(
                            "$arrow  Samsung Functions",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            if (isSamsung) "Selected: $currentLabel" else "${Mapping.samsungActions.size} system actions",
                            color = if (isSamsung) UltraOrange else Color(0xFF9EA7B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateFolder,
                        contentColor = Color.White,
                        secondaryContentColor = if (isSamsung) UltraOrange else Color(0xFF9EA7B8)
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            if (expandSamsung) {
                items(Mapping.samsungActions, key = { "sam_${it.first}" }) { (action, label) ->
                    val selected = action == current
                    Button(
                        onClick = { onSelect(action) },
                        label = {
                            Text(
                                label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = if (selected) {
                            ButtonDefaults.buttonColors(containerColor = UltraOrange, contentColor = Color.White)
                        } else {
                            ButtonDefaults.buttonColors(containerColor = SlateCard, contentColor = Color.White)
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .transformedHeight(this, transformationSpec)
                    )
                }
            }

            // 2. Media & Volume collapsible section
            item(key = "sec_media") {
                val arrow = if (expandMedia) "▼" else "▶"
                Button(
                    onClick = { expandMedia = !expandMedia },
                    label = {
                        Text(
                            "$arrow  Media & Volume",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            if (isMedia) "Selected: $currentLabel" else "KDE Connect, Play/Pause, Vol…",
                            color = if (isMedia) UltraOrange else Color(0xFF9EA7B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateFolder,
                        contentColor = Color.White,
                        secondaryContentColor = if (isMedia) UltraOrange else Color(0xFF9EA7B8)
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            if (expandMedia) {
                items(Mapping.mediaActions, key = { "med_${it.first}" }) { (action, label) ->
                    val selected = action == current
                    Button(
                        onClick = { onSelect(action) },
                        label = {
                            Text(
                                label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = if (selected) {
                            ButtonDefaults.buttonColors(containerColor = UltraOrange, contentColor = Color.White)
                        } else {
                            ButtonDefaults.buttonColors(containerColor = SlateCard, contentColor = Color.White)
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .transformedHeight(this, transformationSpec)
                    )
                }
            }

            // 3. Installed Apps collapsible section
            item(key = "sec_apps") {
                val arrow = if (expandApps) "▼" else "▶"
                Button(
                    onClick = { expandApps = !expandApps },
                    label = {
                        Text(
                            "$arrow  Installed Apps",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            if (isApp) "Selected: $currentLabel" else "${apps.size} watch apps",
                            color = if (isApp) UltraOrange else Color(0xFF9EA7B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateFolder,
                        contentColor = Color.White,
                        secondaryContentColor = if (isApp) UltraOrange else Color(0xFF9EA7B8)
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }

            if (expandApps) {
                items(apps, key = { it.action }) { app ->
                    val selected = app.action == current
                    Button(
                        onClick = { onSelect(app.action) },
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
                        colors = if (selected) {
                            ButtonDefaults.buttonColors(containerColor = UltraOrange, contentColor = Color.White)
                        } else {
                            ButtonDefaults.buttonColors(containerColor = SlateCard, contentColor = Color.White)
                        },
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .transformedHeight(this, transformationSpec)
                    )
                }
            }

            // 4. Disabled / Nothing option
            item(key = "sec_none") {
                val selected = current == "none"
                Button(
                    onClick = { onSelect("none") },
                    label = {
                        Text(
                            "Nothing (Disabled)",
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = if (selected) {
                        ButtonDefaults.buttonColors(containerColor = UltraOrange, contentColor = Color.White)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = SlateCard, contentColor = Color(0xFF9EA7B8))
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                )
            }
        }
    }
}
