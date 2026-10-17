package com.bennybar.ultraremapper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

/** Settings: pick what each Action button gesture does. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppScaffold {
                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current
                    var a11yEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
                    var dismissedOnboarding by remember { mutableStateOf(false) }

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                a11yEnabled = isAccessibilityServiceEnabled(context)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    if (!a11yEnabled && !dismissedOnboarding) {
                        AccessibilityOnboarding(
                            onOpenSettings = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onSkip = { dismissedOnboarding = true }
                        )
                    } else {
                        val nav = rememberSwipeDismissableNavController()
                        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                            composable("home") {
                                Home(
                                    a11yEnabled = a11yEnabled,
                                    onOpenA11ySettings = {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    },
                                    onPick = { nav.navigate("pick/$it") }
                                )
                            }
                            composable("pick/{gesture}") {
                                Picker(it.arguments?.getString("gesture")!!, onDone = { nav.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessibilityOnboarding(onOpenSettings: () -> Unit, onSkip: () -> Unit) {
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding) {
            item {
                ListHeader {
                    Text("Setup Required", textAlign = TextAlign.Center)
                }
            }
            item {
                Text(
                    text = "Enable Ultra2 Remapper in Accessibility Settings so button presses work reliably.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            item {
                Button(
                    onClick = onOpenSettings,
                    label = { Text("Enable Accessibility", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UltraOrange,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = onSkip,
                    label = { Text("Continue to Settings", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun Home(a11yEnabled: Boolean, onOpenA11ySettings: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding) {
            if (!a11yEnabled) {
                item {
                    Button(
                        onClick = onOpenA11ySettings,
                        label = { Text("Enable Accessibility") },
                        secondaryLabel = { Text("Tap to activate service", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UltraOrange,
                            contentColor = Color.White,
                            secondaryContentColor = Color.White.copy(alpha = 0.85f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            for ((title, gestures) in listOf("Action Button" to Mapping.actionGestures, "Bottom Button" to Mapping.bottomGestures)) {
                item { ListHeader { Text(title) } }
                items(gestures) { (gesture, name) ->
                    Button(
                        onClick = { onPick(gesture) },
                        label = { Text(name) },
                        secondaryLabel = {
                            Text(Mapping.label(context, Mapping.get(context, gesture)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Picker(gesture: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val current = Mapping.get(context, gesture)
    val apps = remember {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
            .map { "app:$it" to Mapping.label(context, "app:$it") }
            .sortedBy { it.second.lowercase() }
    }
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding) {
            item { ListHeader { Text(if (gesture == "bottom_long") "Bottom long press" else Mapping.gestures.toMap()[gesture]!!) } }
            items(Mapping.actions + apps) { (action, label) ->
                Button(
                    onClick = {
                        Mapping.set(context, gesture, action)
                        onDone()
                    },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = if (action == current) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
