package com.aaronhrndz.ultraremapper.ui

import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.aaronhrndz.ultraremapper.ActionButtonService
import com.aaronhrndz.ultraremapper.CollapseChevron
import com.aaronhrndz.ultraremapper.ForwardChevron
import com.aaronhrndz.ultraremapper.SwitchLoadingSpinner
import com.aaronhrndz.ultraremapper.HapticFeedbackHelper
import com.aaronhrndz.ultraremapper.Mapping
import com.aaronhrndz.ultraremapper.MediaControlService
import com.aaronhrndz.ultraremapper.R
import com.aaronhrndz.ultraremapper.SystemRouteHelper
import com.aaronhrndz.ultraremapper.UpdateChecker
import com.aaronhrndz.ultraremapper.adb.AdbLocalManager
import com.aaronhrndz.ultraremapper.adb.AdbMdnsDiscovery
import com.aaronhrndz.ultraremapper.adb.LocalAdbClient
import com.aaronhrndz.ultraremapper.adb.PermissionType
import kotlin.concurrent.thread

private val SlateCard = Color(0xFF1F232A)
private val UltraOrange = Color(0xFFFF5500)
private val GreenOk = Color(0xFF4CAF50)
private val RedAlert = Color(0xFFF44336)

data class PermissionItem(
    val type: PermissionType,
    val name: String,
    val isGranted: Boolean,
    val description: String
)

fun readPermissionItems(context: Context): List<PermissionItem> {
    val overlayOk = Settings.canDrawOverlays(context)
    val secureOk = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED
    val actionRouteOk = try {
        val cur = Settings.Global.getString(context.contentResolver, "setting_action_button_press")
        cur == "${context.packageName}/${ActionButtonService::class.java.name}"
    } catch (_: Exception) {
        false
    }
    val notifOk = try {
        val expected = ComponentName(context, MediaControlService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
        enabled.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.any { it == expected }
    } catch (_: Exception) {
        false
    }
    val batteryOk = try {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    } catch (_: Exception) {
        false
    }

    return listOf(
        PermissionItem(PermissionType.OVERLAY, "Overlay", overlayOk, "Superposición en segundo plano"),
        PermissionItem(PermissionType.SECURE_SETTINGS, "Secure Settings", secureOk, "Ajustes del sistema"),
        PermissionItem(PermissionType.ACTION_ROUTE, "Botón de Acción", actionRouteOk, "Ruta de pulsación física"),
        PermissionItem(PermissionType.NOTIFICATION_LISTENER, "Notificaciones", notifOk, "Control multimedia activo"),
        PermissionItem(PermissionType.BATTERY_WHITELIST, "Sin restricción batería", batteryOk, "Evita cierre en reposo")
    )
}

@Composable
fun SettingsScreen(
    client: LocalAdbClient?,
    targetHighlight: String? = null,
    onClientConnected: ((LocalAdbClient) -> Unit)? = null,
    onOpenPermissions: () -> Unit,
    onOpenWizard: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onOpenDevOptions: () -> Unit = {},
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    var isMasterEnabled by remember { mutableStateOf(Mapping.isEnabled(context)) }
    val shouldExpandPermsInitially = targetHighlight != null && targetHighlight != "SAMSUNG_PAY"
    var isPermsExpanded by remember { mutableStateOf(shouldExpandPermsInitially) }
    var isAdbExpanded by remember { mutableStateOf(false) }
    var isHapticEnabled by remember { mutableStateOf(Mapping.isHapticFeedbackEnabled(context)) }
    var isSamsungPayEnabled by remember { mutableStateOf(AdbLocalManager.isSamsungPayEnabled(context)) }
    var permVersion by remember { mutableStateOf(0) }
    var statusToast by remember { mutableStateOf<String?>(null) }
    var samsungPayError by remember { mutableStateOf<String?>(null) }
    var isToastError by remember { mutableStateOf(false) }
    var activeLoadingItem by remember { mutableStateOf<String?>(null) }
    var isAutoConnecting by remember { mutableStateOf(false) }

    var connectedClientState by remember { mutableStateOf(client ?: LocalAdbClient.activeInstance) }
    val isAdbConnected = (connectedClientState?.isConnected == true)
    val isAdbPaired = LocalAdbClient.isPaired(context)
    val perms = remember(permVersion, isAdbConnected) { readPermissionItems(context) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    // Track handled target to avoid re-scrolling on back navigation
    var handledTarget by rememberSaveable { mutableStateOf<String?>(null) }

    // Flash highlight animation for target item
    val flashAnim = remember { Animatable(0f) }
    LaunchedEffect(targetHighlight) {
        if (targetHighlight != null && handledTarget != targetHighlight) {
            handledTarget = targetHighlight
            kotlinx.coroutines.delay(250)
            if (targetHighlight == "SAMSUNG_PAY") {
                // Index 8 is samsung_pay_switch
                columnState.animateScrollToItem(7, 0)
                repeat(3) {
                    flashAnim.animateTo(1f, tween(450))
                    flashAnim.animateTo(0f, tween(400))
                }
            }
        }
    }

    // Auto-connect on entering Settings if paired and not yet connected
    LaunchedEffect(Unit) {
        if (!isAdbConnected && isAdbPaired) {
            isAutoConnecting = true
            thread {
                val lastPort = LocalAdbClient.getLastPort(context)
                val testClient = LocalAdbClient("127.0.0.1", lastPort)
                val ok = testClient.connect(context, timeoutMs = 3000L)
                mainHandler.post {
                    isAutoConnecting = false
                    if (ok) {
                        connectedClientState = testClient
                        onClientConnected?.invoke(testClient)
                        permVersion++
                        onRefreshPermissions()
                        statusToast = null
                    }
                }
            }
        }
    }

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 36.dp, bottom = 46.dp, start = 10.dp, end = 10.dp)
        ) {
            // Header
            item(key = "settings_header") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        text = context.getString(R.string.settings_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Master Remapper Switch (First option in Settings)
            item(key = "master_remapper_switch") {
                Button(
                    onClick = {
                        val next = !isMasterEnabled
                        isMasterEnabled = next
                        SystemRouteHelper.onMasterToggle(context, next)
                    },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = context.getString(R.string.master_switch_title),
                                maxLines = 1,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f, fill = false).basicMarquee()
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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

            // Haptic Feedback Switch (Immediately below master switch)
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
                                    text = context.getString(R.string.haptic_switch_title),
                                    maxLines = 1,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.basicMarquee()
                                )
                                Text(
                                    text = context.getString(R.string.haptic_switch_subtitle),
                                    maxLines = 1,
                                    color = Color(0xFF9EA7B8),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(18.dp)
                                    .clip(CircleShape)
                                    .background(if (isHapticEnabled) UltraOrange else Color(0xFF383D47))
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
                        .padding(top = 4.dp)
                        .transformedHeight(this, transformationSpec)
                )
            }

            // SECTION 2: ADB SETTINGS
            item(key = "hdr_adb") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        text = context.getString(R.string.hdr_adb_section),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().basicMarquee()
                    )
                }
            }

            // Collapsible ADB Section Card
            item(key = "adb_collapsible_card") {
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
                                isAdbExpanded = !isAdbExpanded
                                HapticFeedbackHelper.playUiTap(context)
                            }
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = context.getString(R.string.settings_adb_section),
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                            val statusText = when {
                                isAutoConnecting -> "ADB: Conectando..."
                                isAdbConnected -> context.getString(R.string.settings_adb_status_connected)
                                isAdbPaired -> "ADB: Vinculado"
                                else -> context.getString(R.string.settings_adb_status_disconnected)
                            }
                            Text(
                                text = statusText,
                                color = when {
                                    isAutoConnecting -> Color(0xFFFFB74D)
                                    isAdbConnected -> GreenOk
                                    isAdbPaired -> Color(0xFF64B5F6)
                                    else -> Color(0xFF9EA7B8)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                        CollapseChevron(isExpanded = isAdbExpanded)
                    }

                    if (isAdbExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // ADB Wizard Button
                        Button(
                            onClick = onOpenWizard,
                            label = {
                                Text(
                                    text = context.getString(R.string.settings_adb_wizard_button),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                            },
                            secondaryLabel = {
                                Text(
                                    text = context.getString(R.string.settings_adb_wizard_desc),
                                    color = Color(0xFF9EA7B8),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF282E38),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Forget / Unpair ADB Button
                        if (isAdbPaired) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    LocalAdbClient.unpair(context)
                                    connectedClientState = null
                                    permVersion++
                                    onRefreshPermissions()
                                    HapticFeedbackHelper.playButtonHaptic(context)
                                    statusToast = context.getString(R.string.settings_adb_unpair_confirm)
                                    isToastError = false
                                },
                                label = {
                                    Text(
                                        text = context.getString(R.string.settings_adb_unpair_button),
                                        fontWeight = FontWeight.Bold,
                                        color = RedAlert,
                                        maxLines = 1,
                                        modifier = Modifier.basicMarquee()
                                    )
                                },
                                secondaryLabel = {
                                    Text(
                                        text = context.getString(R.string.settings_adb_unpair_desc),
                                        color = Color(0xFF9EA7B8),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        modifier = Modifier.basicMarquee()
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF282E38),
                                    contentColor = RedAlert
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Permissions entry button (Dedicated screen, placed under ADB section)
            item(key = "perms_screen_entry") {
                Button(
                    onClick = onOpenPermissions,
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = context.getString(R.string.settings_permissions_title),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                                val grantedCount = perms.count { it.isGranted }
                                Text(
                                    text = "$grantedCount de ${perms.size} activos",
                                    color = if (grantedCount == perms.size) GreenOk else Color(0xFF9EA7B8),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            ForwardChevron()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateCard,
                        contentColor = Color.White
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .transformedHeight(this, transformationSpec)
                )
            }

            // SECTION 3: SAMSUNG PAY
            item(key = "hdr_samsung_pay") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        text = context.getString(R.string.hdr_samsung_pay_section),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().basicMarquee()
                    )
                }
            }

            // Samsung Pay Freeze Switch
            item(key = "samsung_pay_switch") {
                val isPayLoading = (activeLoadingItem == "SAMSUNG_PAY")
                val isPayHighlighted = targetHighlight == "SAMSUNG_PAY"
                val payHighlightColor = if (isPayHighlighted && flashAnim.value > 0.05f) {
                    UltraOrange.copy(alpha = flashAnim.value)
                } else {
                    Color.Transparent
                }
                Button(
                    enabled = activeLoadingItem == null && !isAutoConnecting,
                    onClick = {
                        val currentClient = connectedClientState ?: client ?: LocalAdbClient.activeInstance
                        if (currentClient?.isConnected != true) {
                            onOpenWizard()
                            return@Button
                        }
                        val targetState = !isSamsungPayEnabled
                        activeLoadingItem = "SAMSUNG_PAY"
                        samsungPayError = null
                        thread {
                            val success = AdbLocalManager.toggleSamsungPay(currentClient, targetState)
                            Thread.sleep(300)
                            mainHandler.post {
                                activeLoadingItem = null
                                if (success) {
                                    isSamsungPayEnabled = AdbLocalManager.isSamsungPayEnabled(context)
                                    HapticFeedbackHelper.playButtonHaptic(context)
                                    samsungPayError = null
                                } else {
                                    samsungPayError = "Error al cambiar Samsung Pay"
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
                                    text = context.getString(R.string.settings_samsung_pay_title),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                                Text(
                                    text = if (isSamsungPayEnabled) context.getString(R.string.settings_samsung_pay_enabled) else context.getString(R.string.settings_samsung_pay_disabled),
                                    color = if (isSamsungPayEnabled) Color(0xFF9EA7B8) else UltraOrange,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isPayLoading) {
                                SwitchLoadingSpinner(modifier = Modifier.padding(horizontal = 6.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(18.dp)
                                        .clip(CircleShape)
                                        .background(if (isSamsungPayEnabled) UltraOrange else Color(0xFF383D47))
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .align(if (isSamsungPayEnabled) Alignment.CenterEnd else Alignment.CenterStart)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
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
                        .then(
                            if (isPayHighlighted && payHighlightColor != Color.Transparent) {
                                Modifier.border(2.dp, payHighlightColor, RoundedCornerShape(18.dp))
                            } else Modifier
                        )
                )
            }

            samsungPayError?.let { err ->
                item(key = "samsung_pay_error") {
                    Text(
                        text = err,
                        color = RedAlert,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
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

            // App version footer (Moved from MainActivity home screen)
            item(key = "app_version_footer") {
                val installedVersion = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.2.1"
                    } catch (e: Exception) {
                        "2.2.1"
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
                        .padding(top = 16.dp, bottom = 8.dp)
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
