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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

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

private fun drawableToImageBitmap(drawable: Drawable, sizePx: Int = 64): ImageBitmap {
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

                    LaunchedEffect(Unit) {
                        UpdateChecker.check(context) { info ->
                            updateInfo = info
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
                            CategoryPicker(
                                gesture = gesture,
                                onSelectCategory = { cat -> nav.navigate("pick_$cat/$gesture") },
                                onSelectDirect = { action ->
                                    Mapping.set(context, gesture, action)
                                    nav.popBackStack("home", inclusive = false)
                                }
                            )
                        }
                        composable("pick_samsung/{gesture}") { entry ->
                            val gesture = entry.arguments?.getString("gesture")!!
                            ActionListPicker(
                                title = "Samsung Actions",
                                gesture = gesture,
                                items = Mapping.samsungActions,
                                onDone = { nav.popBackStack("home", inclusive = false) }
                            )
                        }
                        composable("pick_media/{gesture}") { entry ->
                            val gesture = entry.arguments?.getString("gesture")!!
                            ActionListPicker(
                                title = "Media & Volume",
                                gesture = gesture,
                                items = Mapping.mediaActions,
                                onDone = { nav.popBackStack("home", inclusive = false) }
                            )
                        }
                        composable("pick_app/{gesture}") { entry ->
                            val gesture = entry.arguments?.getString("gesture")!!
                            AppPicker(
                                gesture = gesture,
                                onDone = { nav.popBackStack("home", inclusive = false) }
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
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding) {
            item {
                ListHeader {
                    Text(
                        text = "ULTRA REMAPPER",
                        color = UltraOrange,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (updateInfo != null) {
                item {
                    Button(
                        onClick = {
                            UpdateChecker.openReleasePage(context, updateInfo)
                        },
                        label = {
                            Text(
                                "Update v${updateInfo.versionName} available",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        secondaryLabel = {
                            Text("Check GitHub release on PC/Phone", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UltraOrange,
                            contentColor = Color.White,
                            secondaryContentColor = Color.White.copy(alpha = 0.9f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (!setup.allReady) {
                item {
                    ListHeader {
                        Text("Setup Diagnostics", color = WarnAmber)
                    }
                }
                if (!setup.a11yEnabled) {
                    item {
                        Button(
                            onClick = onOpenA11ySettings,
                            label = { Text("Enable Accessibility", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text("Required for Bottom button & Flashlight Boost", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UltraOrange,
                                contentColor = Color.White,
                                secondaryContentColor = Color.White.copy(alpha = 0.85f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (!setup.actionRouted) {
                    item {
                        Button(
                            onClick = onOpenAppInfo,
                            label = { Text("Quick Button Route Missing", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text("adb: settings put global setting_action_button_press ...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateHighlight),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (!setup.overlayAllowed) {
                    item {
                        Button(
                            onClick = onOpenAppInfo,
                            label = { Text("Overlay Permission Off", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text("adb: appops set ... SYSTEM_ALERT_WINDOW allow", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateHighlight),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (!setup.mediaListenerEnabled) {
                    item {
                        Button(
                            onClick = onOpenNotifListeners,
                            label = { Text("Media Bridge (KDE Connect)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text("Enable Notification Access for phone media", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateHighlight),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            for ((title, gestures) in listOf(
                "Quick Button (Orange)" to Mapping.actionGestures,
                "Bottom Button" to Mapping.bottomGestures
            )) {
                item {
                    ListHeader {
                        Text(title, color = Color(0xFFB0B8C4))
                    }
                }
                items(gestures) { (gesture, name) ->
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPicker(
    gesture: String,
    onSelectCategory: (String) -> Unit,
    onSelectDirect: (String) -> Unit,
) {
    val context = LocalContext.current
    val current = Mapping.get(context, gesture)
    val currentLabel = Mapping.label(context, current)
    val title = Mapping.gestures.toMap()[gesture] ?: gesture
    val state = rememberScalingLazyListState()

    val isSamsung = Mapping.samsungActions.any { it.first == current }
    val isMedia = Mapping.mediaActions.any { it.first == current }
    val isApp = current.startsWith("app:")

    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding) {
            item {
                ListHeader {
                    Text(title, color = UltraOrange, textAlign = TextAlign.Center)
                }
            }
            item {
                Button(
                    onClick = { onSelectCategory("samsung") },
                    label = { Text("Samsung Functions", fontWeight = FontWeight.SemiBold) },
                    secondaryLabel = {
                        Text(
                            if (isSamsung) "Active: $currentLabel" else "Flashlight, Workouts, Water Lock…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = if (isSamsung) {
                        ButtonDefaults.buttonColors(
                            containerColor = UltraOrange,
                            contentColor = Color.White,
                            secondaryContentColor = Color.White.copy(alpha = 0.9f)
                        )
                    } else {
                        ButtonDefaults.buttonColors(containerColor = SlateCard, contentColor = Color.White)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = { onSelectCategory("media") },
                    label = { Text("Media & Volume", fontWeight = FontWeight.SemiBold) },
                    secondaryLabel = {
                        Text(
                            if (isMedia) "Active: $currentLabel" else "Play/Pause, KDE Connect, Volume…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = if (isMedia) {
                        ButtonDefaults.buttonColors(
                            containerColor = UltraOrange,
                            contentColor = Color.White,
                            secondaryContentColor = Color.White.copy(alpha = 0.9f)
                        )
                    } else {
                        ButtonDefaults.buttonColors(containerColor = SlateCard, contentColor = Color.White)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = { onSelectCategory("app") },
                    label = { Text("Open App…", fontWeight = FontWeight.SemiBold) },
                    secondaryLabel = {
                        Text(
                            if (isApp) "Active: $currentLabel" else "Choose installed watch app",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = if (isApp) {
                        ButtonDefaults.buttonColors(
                            containerColor = UltraOrange,
                            contentColor = Color.White,
                            secondaryContentColor = Color.White.copy(alpha = 0.9f)
                        )
                    } else {
                        ButtonDefaults.buttonColors(containerColor = SlateCard, contentColor = Color.White)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = { onSelectDirect("none") },
                    label = { Text("Nothing (Disabled)") },
                    colors = if (current == "none") {
                        ButtonDefaults.buttonColors(containerColor = UltraOrange, contentColor = Color.White)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = SlateCard, contentColor = Color(0xFF9EA7B8))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ActionListPicker(
    title: String,
    gesture: String,
    items: List<Pair<String, String>>,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val current = Mapping.get(context, gesture)
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding) {
            item {
                ListHeader {
                    Text(title, color = UltraOrange, textAlign = TextAlign.Center)
                }
            }
            items(items) { (action, label) ->
                val selected = action == current
                Button(
                    onClick = {
                        Mapping.set(context, gesture, action)
                        onDone()
                    },
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private data class AppEntry(
    val action: String,
    val label: String,
    val icon: ImageBitmap?,
)

@Composable
private fun AppPicker(gesture: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val current = Mapping.get(context, gesture)
    val apps = remember {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
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
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding) {
            item {
                ListHeader {
                    Text("Open App", color = UltraOrange, textAlign = TextAlign.Center)
                }
            }
            items(apps) { app ->
                val selected = app.action == current
                Button(
                    onClick = {
                        Mapping.set(context, gesture, app.action)
                        onDone()
                    },
                    icon = if (app.icon != null) {
                        {
                            Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(26.dp)
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
