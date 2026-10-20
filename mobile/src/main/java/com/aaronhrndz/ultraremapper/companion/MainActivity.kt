package com.aaronhrndz.ultraremapper.companion

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.rotate
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
private val DarkBg = Color(0xFF111317)
private val CardBg = Color(0xFF1B1E26)
private val CardBorder = Color(0xFF282D3A)
private val GreenActive = Color(0xFF34C759)
private val RedRevoke = Color(0xFFFF453A)
private val NeutralSlate = Color(0xFF242A36)
private val TextMuted = Color(0xFF8E95A5)

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
    val adbHelper = remember { AdbHelper.getInstance(context) }
    val adbClient = remember { AdbClient(context) }

    var connectedWatch by remember { mutableStateOf<Node?>(null) }
    var isBetaEnabled by remember { mutableStateOf(UpdateChecker.isBetaChannel(context)) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var devOptionsUnlocked by remember { mutableStateOf(isBetaEnabled) }

    // Wizard Step: 1, 2, 3 (Pairing), 4 (Grant & Verify)
    var wizardStep by remember { mutableIntStateOf(1) }

    // State for Step 3: Pairing
    var pairIp by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }
    var pairStatusMessage by remember { mutableStateOf("") }
    var isPairSuccess by remember { mutableStateOf(false) }

    // State for Step 4: Connecting & Granting
    var watchIp by remember { mutableStateOf("") }
    var watchPort by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var adbStatusMessage by remember { mutableStateOf("") }
    var isStatusSuccess by remember { mutableStateOf(false) }

    // Collapsible Permissions in Step 4
    var showIndividualPermissions by remember { mutableStateOf(false) }
    var isCheckingPermissions by remember { mutableStateOf(false) }
    val permissionStates = remember { mutableStateMapOf<WatchPermission, Boolean?>() }

    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.1.0"
        } catch (_: Exception) {
            "1.1.0"
        }
    }

    // Auto-discovery via Zeroconf/mDNS (ONLY in Step 3/4, never in Step 1/2)
    DisposableEffect(wizardStep) {
        if (wizardStep >= 3) {
            adbClient.startDiscovery { watch ->
                if (pairIp.isEmpty()) pairIp = watch.host
                if (watchIp.isEmpty()) watchIp = watch.host
                if (wizardStep == 4 && watchPort.isEmpty()) {
                    watchPort = watch.port.toString()
                }
            }
        }
        onDispose {
            adbClient.stopDiscovery()
        }
    }

    // Query DataLayer connected watch node (silent query)
    LaunchedEffect(Unit) {
        try {
            Wearable.getCapabilityClient(context)
                .getCapability("ultra_remapper_watch", CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { cap ->
                    connectedWatch = cap.nodes.firstOrNull()
                }
            if (connectedWatch == null) {
                Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
                    connectedWatch = nodes.firstOrNull()
                }
            }
        } catch (_: Exception) {}
    }

    fun refreshPermissionStates() {
        val port = watchPort.toIntOrNull()
        if (watchIp.isBlank() || port == null) return
        scope.launch {
            isCheckingPermissions = true
            for (perm in WatchPermission.values()) {
                val output = adbHelper.executeCommand(watchIp, port, perm.checkCmd)
                permissionStates[perm] = output.contains(perm.expectedGrantedOutput)
            }
            isCheckingPermissions = false
        }
    }

    LaunchedEffect(showIndividualPermissions) {
        if (showIndividualPermissions && watchIp.isNotBlank() && watchPort.isNotBlank() && !isCheckingPermissions) {
            refreshPermissionStates()
        }
    }

    Scaffold(
        containerColor = DarkBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ultra Remapper",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(UltraOrange.copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "COMPANION",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = UltraOrange
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.subtitle_companion),
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    // Version Tag (7 toques para alternar opciones de desarrollador)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardBg)
                            .clickable {
                                versionTapCount++
                                if (versionTapCount >= 7) {
                                    devOptionsUnlocked = !devOptionsUnlocked
                                    versionTapCount = 0
                                    Toast.makeText(
                                        context,
                                        if (devOptionsUnlocked) "Opciones de desarrollo activadas" else "Opciones de desarrollo desactivadas",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "v$currentVersion",
                            fontSize = 11.sp,
                            color = if (isBetaEnabled || devOptionsUnlocked) UltraOrange else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Wizard Step Indicators (Paso 1, 2, 3, 4)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in 1..4) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (wizardStep >= i) UltraOrange else CardBorder
                                )
                        )
                    }
                }
            }

            // Wizard Main Card
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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (wizardStep) {
                            1 -> {
                                // PASO 1
                                Text(
                                    text = stringResource(R.string.step_1_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = stringResource(R.string.step_1_desc),
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    lineHeight = 18.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(NeutralSlate)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.step_1_tip),
                                        fontSize = 12.sp,
                                        color = TextMuted,
                                        lineHeight = 16.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { wizardStep = 2 },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = UltraOrange,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.btn_next), fontWeight = FontWeight.Bold)
                                }
                            }

                            2 -> {
                                // PASO 2
                                Text(
                                    text = stringResource(R.string.step_2_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = stringResource(R.string.step_2_desc),
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    lineHeight = 18.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(NeutralSlate)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.step_2_dev_tip),
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        lineHeight = 16.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { wizardStep = 1 },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.btn_back))
                                    }
                                    Button(
                                        onClick = { wizardStep = 3 },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = UltraOrange,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1.4f)
                                    ) {
                                        Text(stringResource(R.string.btn_already_enabled), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            3 -> {
                                // PASO 3: VINCULAR DISPOSITIVO CON CÓDIGO
                                Text(
                                    text = stringResource(R.string.step_3_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = stringResource(R.string.step_3_desc),
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    lineHeight = 18.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = pairIp,
                                        onValueChange = {
                                            pairIp = it
                                            if (watchIp.isEmpty()) watchIp = it
                                        },
                                        label = { Text(stringResource(R.string.label_watch_ip)) },
                                        placeholder = { Text("192.168.1.X") },
                                        modifier = Modifier.weight(1.8f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = UltraOrange,
                                            unfocusedBorderColor = CardBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                    OutlinedTextField(
                                        value = pairPort,
                                        onValueChange = { pairPort = it },
                                        label = { Text(stringResource(R.string.label_pair_port)) },
                                        placeholder = { Text("38291") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1.4f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = UltraOrange,
                                            unfocusedBorderColor = CardBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                }

                                OutlinedTextField(
                                    value = pairingCode,
                                    onValueChange = { pairingCode = it },
                                    label = { Text(stringResource(R.string.label_pairing_code)) },
                                    placeholder = { Text("123456") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = UltraOrange,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { wizardStep = 2 },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.btn_back))
                                    }

                                    Button(
                                        onClick = {
                                            val port = pairPort.toIntOrNull()
                                            if (pairIp.isBlank() || port == null || pairingCode.isBlank()) {
                                                Toast.makeText(context, "Rellena IP, Puerto y Código", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            scope.launch {
                                                isPairing = true
                                                val (ok, msg) = adbHelper.pairDevice(pairIp, port, pairingCode)
                                                isPairing = false
                                                isPairSuccess = ok
                                                pairStatusMessage = msg
                                                if (ok) {
                                                    if (watchIp.isEmpty()) watchIp = pairIp
                                                    wizardStep = 4
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = UltraOrange,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(2f)
                                    ) {
                                        if (isPairing) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                        } else {
                                            Text(stringResource(R.string.btn_pair_watch), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (pairStatusMessage.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isPairSuccess) Color(0xFF132A1C) else Color(0xFF26201B)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isPairSuccess) GreenActive else UltraOrange)
                                            )
                                            Text(
                                                text = pairStatusMessage,
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.skip_to_step_4),
                                    fontSize = 12.sp,
                                    color = UltraOrange,
                                    modifier = Modifier
                                        .clickable {
                                            if (watchIp.isEmpty()) watchIp = pairIp
                                            wizardStep = 4
                                        }
                                        .padding(top = 2.dp)
                                )
                            }

                            4 -> {
                                // PASO 4: CONECTAR Y CONCEDER PERMISOS
                                Text(
                                    text = stringResource(R.string.step_4_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = stringResource(R.string.step_4_desc),
                                    fontSize = 13.sp,
                                    color = TextMuted
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = watchIp,
                                        onValueChange = { watchIp = it },
                                        label = { Text(stringResource(R.string.label_watch_ip)) },
                                        placeholder = { Text("192.168.1.X") },
                                        modifier = Modifier.weight(1.8f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = UltraOrange,
                                            unfocusedBorderColor = CardBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                    OutlinedTextField(
                                        value = watchPort,
                                        onValueChange = { watchPort = it },
                                        label = { Text(stringResource(R.string.label_main_port)) },
                                        placeholder = { Text("44609") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1.2f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = UltraOrange,
                                            unfocusedBorderColor = CardBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { wizardStep = 3 },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.btn_back))
                                    }

                                    Button(
                                        onClick = {
                                            val port = watchPort.toIntOrNull()
                                            if (watchIp.isBlank() || port == null) {
                                                Toast.makeText(context, "Introduce IP y Puerto", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            scope.launch {
                                                isConnecting = true
                                                adbStatusMessage = "Conectando al reloj..."
                                                isStatusSuccess = false
                                                var hadError = false
                                                var errorMsg: String? = null

                                                for (perm in WatchPermission.values()) {
                                                    val res = adbHelper.executeCommand(watchIp, port, perm.grantCmd)
                                                    if (res.startsWith("ERROR:") && errorMsg == null) {
                                                        hadError = true
                                                        errorMsg = res
                                                    }
                                                }
                                                refreshPermissionStates()
                                                isConnecting = false

                                                if (hadError) {
                                                    adbStatusMessage = "Fallo de conexión: $errorMsg. Comprueba que el reloj no haya cambiado de puerto."
                                                    isStatusSuccess = false
                                                } else {
                                                    adbStatusMessage = "¡Todos los permisos concedidos con éxito!"
                                                    isStatusSuccess = true
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = UltraOrange,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(2f)
                                    ) {
                                        if (isConnecting) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                        } else {
                                            Text(stringResource(R.string.btn_grant_all), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (adbStatusMessage.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isStatusSuccess) Color(0xFF132A1C) else Color(0xFF26201B)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isStatusSuccess) GreenActive else UltraOrange)
                                            )
                                            Text(
                                                text = adbStatusMessage,
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // DESPLEGABLE INTEGRADO EN UNA SOLA CAJA UNIFICADA (SOLO PASO 4)
            if (wizardStep == 4) {
                item {
                    val chevronRotation by animateFloatAsState(targetValue = if (showIndividualPermissions) 180f else 0f)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showIndividualPermissions = !showIndividualPermissions }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.individual_permissions_title),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = stringResource(R.string.individual_permissions_subtitle),
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expandir",
                                    tint = UltraOrange,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .rotate(chevronRotation)
                                )
                            }

                            AnimatedVisibility(
                                visible = showIndividualPermissions,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(CardBorder)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val port = watchPort.toIntOrNull()
                                                if (watchIp.isBlank() || port == null) {
                                                    Toast.makeText(context, "Introduce IP y Puerto", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                scope.launch {
                                                    isConnecting = true
                                                    for (perm in WatchPermission.values()) {
                                                        adbHelper.executeCommand(watchIp, port, perm.revokeCmd)
                                                    }
                                                    refreshPermissionStates()
                                                    isConnecting = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeutralSlate),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(R.string.btn_revoke_all), color = RedRevoke, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }

                                        Button(
                                            onClick = { refreshPermissionStates() },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeutralSlate),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (isCheckingPermissions) {
                                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            } else {
                                                Text(stringResource(R.string.btn_scan_status), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            }
                                        }
                                    }

                                    for (perm in WatchPermission.values()) {
                                        val isGranted = permissionStates[perm]
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(NeutralSlate)
                                                .padding(12.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = perm.title,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        fontSize = 13.sp
                                                    )

                                                    if (isCheckingPermissions && isGranted == null) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(12.dp),
                                                            color = TextMuted,
                                                            strokeWidth = 2.dp
                                                        )
                                                    } else if (isGranted != null) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(
                                                                    if (isGranted) GreenActive.copy(alpha = 0.16f) else RedRevoke.copy(alpha = 0.16f)
                                                                )
                                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = if (isGranted) stringResource(R.string.status_granted) else stringResource(R.string.status_missing),
                                                                fontSize = 11.sp,
                                                                color = if (isGranted) GreenActive else RedRevoke,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = perm.description,
                                                    fontSize = 11.sp,
                                                    color = TextMuted,
                                                    lineHeight = 15.sp
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            val port = watchPort.toIntOrNull() ?: return@Button
                                                            scope.launch {
                                                                adbHelper.executeCommand(watchIp, port, perm.grantCmd)
                                                                val res = adbHelper.executeCommand(watchIp, port, perm.checkCmd)
                                                                permissionStates[perm] = res.contains(perm.expectedGrantedOutput)
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = CardBg,
                                                            contentColor = Color.White
                                                        ),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(stringResource(R.string.btn_grant), fontSize = 11.sp, color = Color.White)
                                                    }

                                                    Button(
                                                        onClick = {
                                                            val port = watchPort.toIntOrNull() ?: return@Button
                                                            scope.launch {
                                                                adbHelper.executeCommand(watchIp, port, perm.revokeCmd)
                                                                val res = adbHelper.executeCommand(watchIp, port, perm.checkCmd)
                                                                permissionStates[perm] = res.contains(perm.expectedGrantedOutput)
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(stringResource(R.string.btn_revoke), fontSize = 11.sp, color = RedRevoke)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
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
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dev_options_title),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.beta_channel_title),
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = stringResource(R.string.beta_channel_desc),
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                                Switch(
                                    checked = isBetaEnabled,
                                    onCheckedChange = { checked ->
                                        isBetaEnabled = checked
                                        UpdateChecker.setBetaChannel(context, checked)
                                        Toast.makeText(
                                            context,
                                            if (checked) "Canal Beta activado" else "Canal Producción activado",
                                            Toast.LENGTH_SHORT
                                        ).show()
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
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
