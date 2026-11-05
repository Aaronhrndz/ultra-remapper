package com.aaronhrndz.ultraremapper.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.aaronhrndz.ultraremapper.HapticFeedbackHelper
import com.aaronhrndz.ultraremapper.R
import com.aaronhrndz.ultraremapper.SwitchLoadingSpinner
import com.aaronhrndz.ultraremapper.adb.AdbLocalManager
import com.aaronhrndz.ultraremapper.adb.LocalAdbClient
import kotlin.concurrent.thread

private val UltraOrange = Color(0xFFFF5500)
private val RedAlert = Color(0xFFF44336)

@Composable
fun PermissionsScreen(
    client: LocalAdbClient?,
    targetHighlight: String? = null,
    onClientConnected: ((LocalAdbClient) -> Unit)? = null,
    onOpenWizard: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    var permVersion by remember { mutableStateOf(0) }
    var statusToast by remember { mutableStateOf<String?>(null) }
    var activeLoadingItem by remember { mutableStateOf<String?>(null) }

    var connectedClientState by remember { mutableStateOf(client ?: LocalAdbClient.activeInstance) }
    val isAdbConnected = (connectedClientState?.isConnected == true)
    val perms = remember(permVersion, isAdbConnected) { readPermissionItems(context) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    var handledTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val flashAnim = remember { Animatable(0f) }

    LaunchedEffect(targetHighlight) {
        if (targetHighlight != null && handledTarget != targetHighlight) {
            handledTarget = targetHighlight
            kotlinx.coroutines.delay(200)
            val permIdx = perms.indexOfFirst { it.type.name == targetHighlight }
            // header is index 0, items are 1..N
            val targetIndex = if (permIdx >= 0) 1 + permIdx else 1
            columnState.animateScrollToItem(targetIndex, 0)

            repeat(3) {
                flashAnim.animateTo(1f, tween(450))
                flashAnim.animateTo(0f, tween(400))
            }
        }
    }

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 34.dp, bottom = 46.dp, start = 10.dp, end = 10.dp)
        ) {
            item(key = "permissions_header") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        text = context.getString(R.string.settings_permissions_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            itemsIndexed(perms, key = { _, item -> "perm_${item.type.name}" }) { _, perm ->
                val isThisLoading = (activeLoadingItem == perm.type.name)
                val isHighlighted = targetHighlight == perm.type.name
                val highlightColor = if (isHighlighted && flashAnim.value > 0.05f) {
                    UltraOrange.copy(alpha = flashAnim.value)
                } else {
                    Color.Transparent
                }
                Button(
                    enabled = activeLoadingItem == null,
                    onClick = {
                        val currentClient = connectedClientState ?: client ?: LocalAdbClient.activeInstance
                        if (currentClient?.isConnected != true) {
                            onOpenWizard()
                            return@Button
                        }
                        val targetState = !perm.isGranted
                        activeLoadingItem = perm.type.name
                        statusToast = null
                        thread {
                            val ok = AdbLocalManager.setPermissionState(
                                currentClient,
                                context.packageName,
                                perm.type,
                                targetState
                            )
                            Thread.sleep(300)
                            mainHandler.post {
                                activeLoadingItem = null
                                if (ok) {
                                    permVersion++
                                    onRefreshPermissions()
                                    HapticFeedbackHelper.playButtonHaptic(context)
                                    statusToast = null
                                } else {
                                    statusToast = "Fallo al cambiar ${perm.name}"
                                }
                            }
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
                                    text = perm.name,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                                Text(
                                    text = perm.description,
                                    color = Color(0xFF9EA7B8),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isThisLoading) {
                                SwitchLoadingSpinner(modifier = Modifier.padding(horizontal = 6.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(18.dp)
                                        .clip(CircleShape)
                                        .background(if (perm.isGranted) UltraOrange else Color(0xFF383D47))
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .align(if (perm.isGranted) Alignment.CenterEnd else Alignment.CenterStart)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF282E38),
                        contentColor = Color.White
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .then(
                            if (isHighlighted && highlightColor != Color.Transparent) {
                                Modifier.border(2.dp, highlightColor, RoundedCornerShape(16.dp))
                            } else Modifier
                        )
                )
            }

            statusToast?.let { msg ->
                item(key = "status_toast") {
                    Text(
                        text = msg,
                        color = RedAlert,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
