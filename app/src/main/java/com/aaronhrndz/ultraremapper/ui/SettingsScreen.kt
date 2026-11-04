package com.aaronhrndz.ultraremapper.ui

import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
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
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.aaronhrndz.ultraremapper.ActionButtonService
import com.aaronhrndz.ultraremapper.CollapseChevron
import com.aaronhrndz.ultraremapper.SwitchLoadingSpinner
import com.aaronhrndz.ultraremapper.HapticFeedbackHelper
import com.aaronhrndz.ultraremapper.Mapping
import com.aaronhrndz.ultraremapper.MediaControlService
import com.aaronhrndz.ultraremapper.R
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

private fun readPermissionItems(context: Context): List<PermissionItem> {
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
    onClientConnected: ((LocalAdbClient) -> Unit)? = null,
    onOpenWizard: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    var isPermsExpanded by remember { mutableStateOf(false) }
    var isHapticEnabled by remember { mutableStateOf(Mapping.isHapticFeedbackEnabled(context)) }
    var isSamsungPayEnabled by remember { mutableStateOf(AdbLocalManager.isSamsungPayEnabled(context)) }
    var permVersion by remember { mutableStateOf(0) }
    var statusToast by remember { mutableStateOf<String?>(null) }
    var isToastError by remember { mutableStateOf(false) }
    var activeLoadingItem by remember { mutableStateOf<String?>(null) }
    var isAutoConnecting by remember { mutableStateOf(false) }

    var connectedClientState by remember { mutableStateOf(client ?: LocalAdbClient.activeInstance) }
    val isAdbConnected = (connectedClientState?.isConnected == true)
    val isAdbPaired = LocalAdbClient.isPaired(context)
    val perms = remember(permVersion, isAdbConnected) { readPermissionItems(context) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

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
                        statusToast = "ADB conectado en segundo plano"
                        isToastError = false
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

            // 1. COLLAPSIBLE PERMISSIONS SECTION (Same card style & CollapseChevron as Home buttons)
            item(key = "perms_collapsible_card") {
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
                                isPermsExpanded = !isPermsExpanded
                                HapticFeedbackHelper.playUiTap(context)
                            }
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.settings_permissions_title),
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        CollapseChevron(isExpanded = isPermsExpanded)
                    }

                    if (isPermsExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        perms.forEachIndexed { idx, perm ->
                            if (idx > 0) Spacer(modifier = Modifier.height(4.dp))
                            val isThisLoading = (activeLoadingItem == perm.type.name)
                            Button(
                                enabled = activeLoadingItem == null && !isAutoConnecting,
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
                                        // Brief pause to allow system to commit permission change
                                        Thread.sleep(300)
                                        mainHandler.post {
                                            activeLoadingItem = null
                                            if (ok) {
                                                permVersion++
                                                onRefreshPermissions()
                                                HapticFeedbackHelper.playButtonHaptic(context)
                                                statusToast = "${perm.name}: ${if (targetState) "Concedido" else "Revocado"}"
                                                isToastError = false
                                            } else {
                                                statusToast = "Fallo al cambiar ${perm.name}"
                                                isToastError = true
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
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = perm.description,
                                                color = Color(0xFF9EA7B8),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (isThisLoading) {
                                            SwitchLoadingSpinner(modifier = Modifier.padding(horizontal = 6.dp))
                                        } else {
                                            // Switch Pill
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
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item(key = "spacer_haptic") {
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 2. HAPTIC FEEDBACK SWITCH
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
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = context.getString(R.string.haptic_switch_subtitle),
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
                        .transformedHeight(this, transformationSpec)
                )
            }

            // 3. LOCAL ADB & SAMSUNG PAY SECTION
            item(key = "adb_section_header") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        text = context.getString(R.string.settings_adb_section),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ADB Status & Wizard Trigger
            item(key = "adb_wizard_card") {
                Button(
                    onClick = onOpenWizard,
                    label = {
                        Text(
                            text = context.getString(R.string.settings_adb_wizard_button),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        val statusText = when {
                            isAutoConnecting -> "ADB: Conectando..."
                            isAdbConnected -> context.getString(R.string.settings_adb_status_connected)
                            isAdbPaired -> "ADB: Vinculado (Desconectado)"
                            else -> context.getString(R.string.settings_adb_status_disconnected)
                        }
                        Text(
                            text = statusText,
                            color = when {
                                isAutoConnecting -> Color(0xFFFFB74D)
                                isAdbConnected -> GreenOk
                                isAdbPaired -> Color(0xFF64B5F6)
                                else -> UltraOrange
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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

            // Samsung Pay Freeze Switch
            item(key = "samsung_pay_switch") {
                val isPayLoading = (activeLoadingItem == "SAMSUNG_PAY")
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
                        statusToast = null
                        thread {
                            val success = AdbLocalManager.toggleSamsungPay(currentClient, targetState)
                            // Brief pause to allow PackageManager state to update
                            Thread.sleep(300)
                            mainHandler.post {
                                activeLoadingItem = null
                                if (success) {
                                    isSamsungPayEnabled = AdbLocalManager.isSamsungPayEnabled(context)
                                    HapticFeedbackHelper.playButtonHaptic(context)
                                    statusToast = if (isSamsungPayEnabled) "Samsung Pay Activado" else "Samsung Pay Congelado"
                                    isToastError = false
                                } else {
                                    statusToast = "Error al cambiar Samsung Pay"
                                    isToastError = true
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
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isSamsungPayEnabled) context.getString(R.string.settings_samsung_pay_enabled) else context.getString(R.string.settings_samsung_pay_disabled),
                                    color = if (isSamsungPayEnabled) Color(0xFF9EA7B8) else UltraOrange,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                )
            }

            statusToast?.let { msg ->
                item(key = "status_toast") {
                    Text(
                        text = msg,
                        color = if (isToastError) RedAlert else GreenOk,
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
