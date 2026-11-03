package com.aaronhrndz.ultraremapper.ui

import android.content.Context
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.aaronhrndz.ultraremapper.R
import com.aaronhrndz.ultraremapper.adb.AdbLocalManager
import com.aaronhrndz.ultraremapper.adb.LocalAdbClient
import com.aaronhrndz.ultraremapper.adb.PermissionType

private val SlateCard = Color(0xFF1E222A)
private val UltraOrange = Color(0xFFFF6D00)
private val GreenOk = Color(0xFF4CAF50)
private val RedAlert = Color(0xFFF44336)

data class PermissionItem(
    val type: PermissionType,
    val name: String,
    val isGranted: Boolean,
    val description: String
)

@Composable
fun SettingsScreen(
    client: LocalAdbClient?,
    onOpenWizard: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    var isPermsExpanded by remember { mutableStateOf(false) }
    var isSamsungPayEnabled by remember { mutableStateOf(AdbLocalManager.isSamsungPayEnabled(context)) }
    var paySwitchLoading by remember { mutableStateOf(false) }
    var grantErrorMsg by remember { mutableStateOf<String?>(null) }

    val isAdbConnected = client?.isConnected == true

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 36.dp, bottom = 40.dp, start = 14.dp, end = 14.dp)
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

            // 1. COLLAPSIBLE PERMISSIONS SECTION
            item(key = "perms_collapsible_header") {
                Button(
                    onClick = { isPermsExpanded = !isPermsExpanded },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = context.getString(R.string.settings_permissions_title),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isPermsExpanded) "▲" else "▼",
                                color = UltraOrange,
                                fontSize = 14.sp
                            )
                        }
                    },
                    secondaryLabel = {
                        Text(
                            text = context.getString(R.string.settings_permissions_subtitle),
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

            if (isPermsExpanded) {
                val perms = listOf(
                    PermissionItem(PermissionType.OVERLAY, "Overlay (Ventana)", android.provider.Settings.canDrawOverlays(context), "Superposición para linterna y segundo plano"),
                    PermissionItem(PermissionType.SECURE_SETTINGS, "Secure Settings", context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED, "Modos de red y toggles avanzados"),
                    PermissionItem(PermissionType.ACTION_ROUTE, "Botón de Acción", true, "setting_action_button_press"),
                    PermissionItem(PermissionType.NOTIFICATION_LISTENER, "Listener Notificaciones", true, "Control multimedia y proceso persistente"),
                    PermissionItem(PermissionType.BATTERY_WHITELIST, "Whitelist Batería", true, "Evita suspensión en reposo profundo")
                )

                items(perms, key = { it.type.name }) { perm ->
                    Button(
                        onClick = {
                            if (!perm.isGranted) {
                                if (client?.isConnected == true) {
                                    val ok = AdbLocalManager.executeSingleGrant(client, context.packageName, perm.type)
                                    if (ok) {
                                        onRefreshPermissions()
                                    } else {
                                        grantErrorMsg = "Error concediendo permiso"
                                    }
                                } else {
                                    onOpenWizard()
                                }
                            }
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = perm.name,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (perm.isGranted) "OK" else context.getString(R.string.grant_button_label),
                                    fontWeight = FontWeight.Bold,
                                    color = if (perm.isGranted) GreenOk else UltraOrange
                                )
                            }
                        },
                        secondaryLabel = {
                            Text(
                                text = perm.description,
                                color = Color(0xFF9EA7B8),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (perm.isGranted) Color(0xFF1B2420) else Color(0xFF2C1E18)
                        ),
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    )
                }
            }

            // 2. LOCAL ADB & FEATURES SECTION
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
                            color = Color.White
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = if (isAdbConnected) context.getString(R.string.settings_adb_status_connected) else context.getString(R.string.settings_adb_status_disconnected),
                            color = if (isAdbConnected) GreenOk else UltraOrange,
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

            // Samsung Pay Freeze Switch
            item(key = "samsung_pay_switch") {
                Button(
                    onClick = {
                        if (!isAdbConnected) {
                            onOpenWizard()
                            return@Button
                        }
                        paySwitchLoading = true
                        val targetState = !isSamsungPayEnabled
                        val success = AdbLocalManager.toggleSamsungPay(client!!, targetState)
                        if (success) {
                            isSamsungPayEnabled = targetState
                        } else {
                            grantErrorMsg = "Fallo al alternar Samsung Pay"
                        }
                        paySwitchLoading = false
                    },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = context.getString(R.string.settings_samsung_pay_title),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(20.dp)
                                    .clip(CircleShape)
                                    .background(if (isSamsungPayEnabled) UltraOrange else Color(0xFF434954))
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .align(if (isSamsungPayEnabled) Alignment.CenterEnd else Alignment.CenterStart)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    },
                    secondaryLabel = {
                        Text(
                            text = if (isSamsungPayEnabled) context.getString(R.string.settings_samsung_pay_enabled) else context.getString(R.string.settings_samsung_pay_disabled),
                            color = if (isSamsungPayEnabled) Color(0xFF9EA7B8) else UltraOrange,
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
