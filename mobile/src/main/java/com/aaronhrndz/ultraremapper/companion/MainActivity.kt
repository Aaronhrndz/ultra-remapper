package com.aaronhrndz.ultraremapper.companion

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch

private val UltraOrange = Color(0xFFFF5500)
private val DarkBg = Color(0xFF121418)
private val CardBg = Color(0xFF1E222B)
private val GreenActive = Color(0xFF4CAF50)
private val RedRevoke = Color(0xFFE53935)

enum class WatchPermission(
    val title: String,
    val description: String,
    val grantCmd: String,
    val revokeCmd: String,
    val checkCmd: String,
    val expectedGrantedOutput: String
) {
    WRITE_SECURE(
        title = "Write Secure Settings",
        description = "Permite alternar Modo Guantes, LTE y control del sistema sin root.",
        grantCmd = "pm grant com.aaronhrndz.ultraremapper android.permission.WRITE_SECURE_SETTINGS",
        revokeCmd = "pm revoke com.aaronhrndz.ultraremapper android.permission.WRITE_SECURE_SETTINGS",
        checkCmd = "dumpsys package com.aaronhrndz.ultraremapper | grep 'WRITE_SECURE_SETTINGS: granted=true'",
        expectedGrantedOutput = "granted=true"
    ),
    OVERLAY(
        title = "Superposición de Pantalla",
        description = "Permite abrir linterna al máximo brillo e interfaces sobre cualquier app.",
        grantCmd = "appops set com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW allow",
        revokeCmd = "appops set com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW default",
        checkCmd = "appops get com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW",
        expectedGrantedOutput = "allow"
    ),
    ACTION_ROUTING(
        title = "Enrutado Botón de Acción",
        description = "Asigna el botón físico naranja de tu Ultra a Ultra Remapper.",
        grantCmd = "settings put global setting_action_button_press com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.ActionButtonService",
        revokeCmd = "settings put global setting_action_button_press com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService",
        checkCmd = "settings get global setting_action_button_press",
        expectedGrantedOutput = "com.aaronhrndz.ultraremapper"
    ),
    MEDIA_BRIDGE(
        title = "Puente Multimedia",
        description = "Permite controlar volumen/canciones y ceder en entrenos de Samsung Health.",
        grantCmd = "cmd notification allow_listener com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.MediaControlService",
        revokeCmd = "cmd notification disallow_listener com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.MediaControlService",
        checkCmd = "settings get secure enabled_notification_listeners",
        expectedGrantedOutput = "com.aaronhrndz.ultraremapper"
    )
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = DarkBg,
                    surface = CardBg,
                    primary = UltraOrange
                )
            ) {
                CompanionHomeScreen(activity = this)
            }
        }
    }
}

@Composable
fun CompanionHomeScreen(activity: ComponentActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adbClient = remember { AdbClient(context) }

    var connectedWatch by remember { mutableStateOf<Node?>(null) }
    var isBetaEnabled by remember { mutableStateOf(UpdateChecker.isBetaChannel(context)) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var devOptionsUnlocked by remember { mutableStateOf(isBetaEnabled) }

    // ADB Connection fields
    var watchIp by remember { mutableStateOf("") }
    var watchPort by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var adbStatusMessage by remember { mutableStateOf("") }

    // Live permission states
    val permissionStates = remember { mutableStateMapOf<WatchPermission, Boolean?>() }

    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    // Auto-discovery via NSD / Zeroconf
    DisposableEffect(Unit) {
        adbClient.startDiscovery { watch ->
            if (watchIp.isEmpty()) {
                watchIp = watch.host
                watchPort = watch.port.toString()
                adbStatusMessage = "Reloj detectado en red Wi-Fi: ${watch.name}"
            }
        }

        val nodeClient = Wearable.getNodeClient(activity)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            connectedWatch = nodes.firstOrNull()
        }

        val listener = CapabilityClient.OnCapabilityChangedListener { info ->
            connectedWatch = info.nodes.firstOrNull()
        }
        Wearable.getCapabilityClient(activity).addListener(
            listener,
            "ultra_remapper_watch"
        )

        onDispose {
            adbClient.stopDiscovery()
            Wearable.getCapabilityClient(activity).removeListener(listener)
        }
    }

    suspend fun refreshPermissionStates() {
        val port = watchPort.toIntOrNull() ?: return
        if (watchIp.isBlank()) return
        for (perm in WatchPermission.values()) {
            val res = adbClient.runShell(watchIp, port, perm.checkCmd)
            permissionStates[perm] = res.contains(perm.expectedGrantedOutput)
        }
    }

    Scaffold(
        containerColor = DarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column {
                    Text(
                        text = stringResource(R.string.title_companion),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.subtitle_companion),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Watch Status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (connectedWatch != null) GreenActive else Color.Gray)
                        )
                        Column {
                            Text(
                                text = connectedWatch?.displayName ?: stringResource(R.string.status_watch),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (connectedWatch != null) stringResource(R.string.watch_connected) else stringResource(R.string.watch_disconnected),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // ADB Connection Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Depuración Inalámbrica",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Conexión directa por Wi-Fi sin ordenador ni cables.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = watchIp,
                                onValueChange = { watchIp = it },
                                label = { Text("IP Reloj") },
                                placeholder = { Text("192.168.1.X") },
                                modifier = Modifier.weight(1.8f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = UltraOrange,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            OutlinedTextField(
                                value = watchPort,
                                onValueChange = { watchPort = it },
                                label = { Text("Puerto") },
                                placeholder = { Text("44609") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1.2f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = UltraOrange,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }

                        if (adbStatusMessage.isNotEmpty()) {
                            Text(
                                text = adbStatusMessage,
                                fontSize = 12.sp,
                                color = UltraOrange
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Conceder Todos
                            Button(
                                onClick = {
                                    val port = watchPort.toIntOrNull()
                                    if (watchIp.isBlank() || port == null) {
                                        Toast.makeText(context, "Introduce IP y Puerto", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch {
                                        isConnecting = true
                                        adbStatusMessage = "Concediendo todos los permisos..."
                                        for (perm in WatchPermission.values()) {
                                            adbClient.runShell(watchIp, port, perm.grantCmd)
                                        }
                                        refreshPermissionStates()
                                        isConnecting = false
                                        adbStatusMessage = "¡Todos los permisos concedidos con éxito!"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = UltraOrange),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isConnecting) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                } else {
                                    Text("Conceder Todos", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Revocar Todos
                            Button(
                                onClick = {
                                    val port = watchPort.toIntOrNull()
                                    if (watchIp.isBlank() || port == null) {
                                        Toast.makeText(context, "Introduce IP y Puerto", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch {
                                        isConnecting = true
                                        adbStatusMessage = "Revocando todos los permisos..."
                                        for (perm in WatchPermission.values()) {
                                            adbClient.runShell(watchIp, port, perm.revokeCmd)
                                        }
                                        refreshPermissionStates()
                                        isConnecting = false
                                        adbStatusMessage = "Todos los permisos revocados."
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C323E)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Revocar Todos", color = RedRevoke, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Individual Permissions List
            item {
                Text(
                    text = "Control Individual de Permisos",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(WatchPermission.values().size) { idx ->
                val perm = WatchPermission.values()[idx]
                val isGranted = permissionStates[perm]

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = perm.title,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            if (isGranted != null) {
                                Text(
                                    text = if (isGranted) "Concedido" else "Falta",
                                    fontSize = 12.sp,
                                    color = if (isGranted) GreenActive else UltraOrange,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = perm.description,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.65f),
                            lineHeight = 16.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val port = watchPort.toIntOrNull() ?: return@Button
                                    scope.launch {
                                        adbClient.runShell(watchIp, port, perm.grantCmd)
                                        val res = adbClient.runShell(watchIp, port, perm.checkCmd)
                                        permissionStates[perm] = res.contains(perm.expectedGrantedOutput)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = UltraOrange),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Conceder", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    val port = watchPort.toIntOrNull() ?: return@Button
                                    scope.launch {
                                        adbClient.runShell(watchIp, port, perm.revokeCmd)
                                        val res = adbClient.runShell(watchIp, port, perm.checkCmd)
                                        permissionStates[perm] = res.contains(perm.expectedGrantedOutput)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C323E)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Revocar", fontSize = 12.sp, color = RedRevoke)
                            }
                        }
                    }
                }
            }

            // Developer / Beta Channel Card
            if (devOptionsUnlocked) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Canal Beta (Companion)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Recibir pre-releases inestables de prueba en el móvil",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = isBetaEnabled,
                                onCheckedChange = { checked ->
                                    isBetaEnabled = checked
                                    UpdateChecker.setBetaChannel(context, checked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = UltraOrange
                                )
                            )
                        }
                    }
                }
            }

            // Footer version & 7-tap gesture
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "v$currentVersion${if (isBetaEnabled) " [Beta]" else ""} • Companion",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.clickable {
                            versionTapCount++
                            if (versionTapCount >= 7) {
                                if (!devOptionsUnlocked) {
                                    devOptionsUnlocked = true
                                    Toast.makeText(context, "¡Opciones de desarrollador desbloqueadas!", Toast.LENGTH_SHORT).show()
                                }
                            } else if (versionTapCount >= 4) {
                                Toast.makeText(context, "Quedan ${7 - versionTapCount} toques para desarrollador", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
