package com.bennybar.ultraremapper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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

/** Settings: pick what each Action button gesture does. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppScaffold {
                    val nav = rememberSwipeDismissableNavController()
                    SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                        composable("home") { Home(onPick = { nav.navigate("pick/$it") }) }
                        composable("pick/{gesture}") {
                            Picker(it.arguments?.getString("gesture")!!, onDone = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Home(onPick: (String) -> Unit) {
    val context = LocalContext.current
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding) {
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
