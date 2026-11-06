package com.aaronhrndz.ultraremapper.companion

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val UltraOrange = Color(0xFFFF5500)
private val DarkBg = Color(0xFF111317)
private val CardBg = Color(0xFF1B1E26)
private val CardBorder = Color(0xFF282D3A)
private val GreenActive = Color(0xFF34C759)
private val RedRevoke = Color(0xFFFF453A)
private val NeutralSlate = Color(0xFF242A36)
private val TextMuted = Color(0xFF8E95A5)

private const val SAMSUNG_PAY_PKG = "com.samsung.android.samsungpay.gear"
private const val WATCH_APP_PKG = "com.aaronhrndz.ultraremapper"

enum class WatchPermission(
    val title: String,
    val description: String,
    val grantCmd: String,
    val revokeCmd: String,
    val checkCmd: String,
    val expectedGrantedOutput: String
) {
    OVERLAY(
        title = "Superposición (Overlay)",
        description = "Permite abrir aplicaciones desde segundo plano y mostrar la linterna al máximo brillo.",
        grantCmd = "appops set com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW allow",
        revokeCmd = "appops set com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW ignore",
        checkCmd = "appops get com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW",
        expectedGrantedOutput = "allow"
    ),
    WRITE_SECURE(
        title = "Ajustes del Sistema (Secure Settings)",
        description = "Permite alternar LTE, No Molestar y gestionar rutas del sistema sin root.",
        grantCmd = "pm grant com.aaronhrndz.ultraremapper android.permission.WRITE_SECURE_SETTINGS",
        revokeCmd = "pm revoke com.aaronhrndz.ultraremapper android.permission.WRITE_SECURE_SETTINGS",
        checkCmd = "dumpsys package com.aaronhrndz.ultraremapper | grep 'WRITE_SECURE_SETTINGS: granted=true'",
        expectedGrantedOutput = "granted=true"
    ),
    ACTION_ROUTING(
        title = "Ruta del Botón de Acción",
        description = "Asigna la pulsación física del botón central / naranja a Ultra Remapper.",
        grantCmd = "settings put global setting_action_button_press com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.ActionButtonService",
        revokeCmd = "settings put global setting_action_button_press com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService",
        checkCmd = "settings get global setting_action_button_press",
        expectedGrantedOutput = "com.aaronhrndz.ultraremapper"
    ),
    MEDIA_BRIDGE(
        title = "Notificaciones y Control Multimedia",
        description = "Permite controlar reproducción, volumen y detectar entrenamientos activos.",
        grantCmd = "cmd notification allow_listener com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.MediaControlService",
        revokeCmd = "cmd notification disallow_listener com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.MediaControlService",
        checkCmd = "settings get secure enabled_notification_listeners",
        expectedGrantedOutput = "com.aaronhrndz.ultraremapper"
    ),
    BATTERY_WHITELIST(
        title = "Sin Restricción de Batería",
        description = "Exime a Ultra Remapper de Doze para que los botones respondan al instante en reposo.",
        grantCmd = "cmd deviceidle whitelist +com.aaronhrndz.ultraremapper",
        revokeCmd = "cmd deviceidle whitelist -com.aaronhrndz.ultraremapper",
        checkCmd = "cmd deviceidle whitelist",
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
                CompanionHomeScreen()
            }
        }
    }
}

@Composable
fun CompanionHomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val adbHelper = remember { AdbHelper.getInstance(context) }
    val adbClient = remember { AdbClient(context) }

    var connectedWatch by remember { mutableStateOf<Node?>(null) }
    var isBetaEnabled by remember { mutableStateOf(UpdateChecker.isBetaChannel(context)) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var devOptionsUnlocked by remember { mutableStateOf(isBetaEnabled) }

    // OTA Update state (Companion + Latest Watch Release)
    var companionUpdate by remember { mutableStateOf<ReleaseUpdate?>(null) }
    var latestWatchRelease by remember { mutableStateOf<ReleaseUpdate?>(null) }
    var companionUpdateProgress by remember { mutableFloatStateOf(0f) }
    var companionUpdateStatus by remember { mutableStateOf("") }

    // Watch APK Installation state (via ADB)
    var installedWatchVersion by remember { mutableStateOf<String?>(null) }
    var isInstallingWatchApk by remember { mutableStateOf(false) }
    var watchApkProgress by remember { mutableFloatStateOf(0f) }
    var watchApkStatusMsg by remember { mutableStateOf("") }
    var watchApkSuccess by remember { mutableStateOf(false) }

    // 3 streamlined steps
    var wizardStep by remember { mutableIntStateOf(1) }
    var isAlreadyPaired by remember { mutableStateOf(adbHelper.isPaired()) }

    // Shared Watch IP & Ports
    var watchIp by remember { mutableStateOf(adbHelper.getLastHost()) }
    var pairPort by remember { mutableStateOf("") }
    var watchPort by remember { mutableStateOf(adbHelper.getLastPort()?.toString() ?: "") }
    var pairingCode by remember { mutableStateOf("") }

    var mdnsPairDetected by remember { mutableStateOf(false) }
    var mdnsConnectDetected by remember { mutableStateOf(false) }

    // Status states
    var isPairing by remember { mutableStateOf(false) }
    var pairStatusMessage by remember { mutableStateOf("") }
    var isPairSuccess by remember { mutableStateOf(false) }

    var isConnecting by remember { mutableStateOf(false) }
    var adbStatusMessage by remember { mutableStateOf("") }
    var isStatusSuccess by remember { mutableStateOf(false) }

    // Individual Permissions & Samsung Pay States
    var isCheckingPermissions by remember { mutableStateOf(false) }
    var activeLoadingItem by remember { mutableStateOf<String?>(null) }
    val permissionStates = remember { mutableStateMapOf<WatchPermission, Boolean?>() }
    var isSamsungPayEnabled by remember { mutableStateOf<Boolean?>(null) }
    var isTogglingSamsungPay by remember { mutableStateOf(false) }
    var samsungPayStatusMsg by remember { mutableStateOf<String?>(null) }

    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.2.0"
        } catch (_: Exception) {
            "2.2.0"
        }
    }

    // Check GitHub Releases on startup and whenever Beta Channel toggles
    LaunchedEffect(isBetaEnabled) {
        UpdateChecker.checkAll(context) { compUpd, latestRel ->
            companionUpdate = compUpd
            latestWatchRelease = latestRel
        }
    }

    // Reset scroll to top whenever wizardStep changes
    LaunchedEffect(wizardStep) {
        listState.animateScrollToItem(0)
    }

    // Dual mDNS Auto-Discovery (_adb-tls-pairing._tcp & _adb-tls-connect._tcp)
    DisposableEffect(wizardStep) {
        if (wizardStep >= 2) {
            adbClient.startDiscovery { endpoint ->
                if (watchIp.isBlank() || watchIp != endpoint.host) {
                    watchIp = endpoint.host
                }
                if (endpoint.isPairing) {
                    pairPort = endpoint.port.toString()
                    mdnsPairDetected = true
                } else {
                    watchPort = endpoint.port.toString()
                    mdnsConnectDetected = true
                }
            }
        }
        onDispose {
            adbClient.stopDiscovery()
        }
    }

    // Query DataLayer connected watch node
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

    fun refreshAllWatchStates() {
        val port = watchPort.toIntOrNull()
        if (watchIp.isBlank() || port == null) return
        scope.launch {
            isCheckingPermissions = true
            val connected = adbHelper.ensureConnected(watchIp, port)
            if (!connected) {
                isCheckingPermissions = false
                adbStatusMessage = "Fallo al conectar a $watchIp:$port. Verifica que Depuración Inalámbrica esté activa en el reloj."
                isStatusSuccess = false
                return@launch
            }
            isAlreadyPaired = true

            // 1. Check installed Ultra Remapper version on watch
            val verOut = adbHelper.executeCommand(watchIp, port, "dumpsys package $WATCH_APP_PKG | grep versionName")
            val verMatch = Regex("versionName=([^\\s]+)").find(verOut)
            installedWatchVersion = verMatch?.groupValues?.getOrNull(1)

            // 2. Check all 5 permissions
            for (perm in WatchPermission.values()) {
                val output = adbHelper.executeCommand(watchIp, port, perm.checkCmd)
                permissionStates[perm] = output.contains(perm.expectedGrantedOutput)
            }

            // 3. Check Samsung Pay status
            val disabledOut = adbHelper.executeCommand(watchIp, port, "pm list packages -d $SAMSUNG_PAY_PKG")
            isSamsungPayEnabled = !disabledOut.contains(SAMSUNG_PAY_PKG)

            isCheckingPermissions = false
            if (adbStatusMessage.isEmpty() || !isStatusSuccess) {
                adbStatusMessage = "Conectado al reloj ($watchIp:$port)"
                isStatusSuccess = true
            }
        }
    }

    // Local APK File Picker for installing custom APK onto the watch
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val port = watchPort.toIntOrNull()
        if (watchIp.isBlank() || port == null) {
            Toast.makeText(context, "Conecta primero el reloj (IP y Puerto)", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isInstallingWatchApk = true
            watchApkProgress = 0.02f
            watchApkStatusMsg = "Copiando archivo APK…"
            watchApkSuccess = false

            val tempFile = withContext(Dispatchers.IO) {
                try {
                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    val f = File(dir, "picked_watch.apk")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        f.outputStream().use { output -> input.copyTo(output) }
                    }
                    f
                } catch (_: Exception) {
                    null
                }
            }

            if (tempFile == null || !tempFile.exists()) {
                isInstallingWatchApk = false
                watchApkStatusMsg = "No se pudo leer el archivo APK seleccionado."
                return@launch
            }

            val (ok, msg) = adbHelper.installApkOnWatch(watchIp, port, tempFile) { frac, status ->
                scope.launch {
                    watchApkProgress = frac
                    watchApkStatusMsg = status
                }
            }
            watchApkSuccess = ok
            watchApkStatusMsg = msg
            isInstallingWatchApk = false
            if (ok) refreshAllWatchStates()
        }
    }

    // Auto-refresh states when entering Step 3 if IP and port are available
    LaunchedEffect(wizardStep, mdnsConnectDetected) {
        if (wizardStep == 3 && watchIp.isNotBlank() && watchPort.toIntOrNull() != null && !isCheckingPermissions && permissionStates.isEmpty()) {
            refreshAllWatchStates()
        }
    }

    Scaffold(
        containerColor = DarkBg
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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

                    // Version Tag (7 taps to unlock developer / beta options)
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
                            text = if (isBetaEnabled) "v$currentVersion (Beta)" else "v$currentVersion",
                            fontSize = 11.sp,
                            color = if (isBetaEnabled || devOptionsUnlocked) UltraOrange else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // COMPANION SELF-UPDATE BANNER (if a newer mobile APK exists on GitHub)
            companionUpdate?.let { upd ->
                item(key = "companion_update_banner") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, UltraOrange, RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF261812))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (upd.isPrerelease) {
                                    stringResource(R.string.companion_update_beta_title, upd.versionName)
                                } else {
                                    stringResource(R.string.companion_update_title, upd.versionName)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = UltraOrange
                            )
                            if (companionUpdateStatus.isNotEmpty()) {
                                LinearProgressIndicator(
                                    progress = { companionUpdateProgress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = UltraOrange,
                                    trackColor = CardBorder
                                )
                                Text(
                                    text = companionUpdateStatus,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            } else {
                                Button(
                                    onClick = {
                                        UpdateChecker.downloadAndInstallCompanion(context, upd) { frac, status ->
                                            companionUpdateProgress = frac
                                            companionUpdateStatus = status
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = UltraOrange,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.companion_update_btn), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 3-Step Progress Bar + Clickable Step Tabs
            item(key = "step_indicator") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (i in 1..3) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (wizardStep >= i) UltraOrange else CardBorder
                                    )
                                    .clickable { wizardStep = i }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1. Preparar",
                            fontSize = 11.sp,
                            fontWeight = if (wizardStep == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (wizardStep == 1) UltraOrange else TextMuted,
                            modifier = Modifier.clickable { wizardStep = 1 }
                        )
                        Text(
                            text = "2. Vincular PIN",
                            fontSize = 11.sp,
                            fontWeight = if (wizardStep == 2) FontWeight.Bold else FontWeight.Normal,
                            color = if (wizardStep == 2) UltraOrange else TextMuted,
                            modifier = Modifier.clickable { wizardStep = 2 }
                        )
                        Text(
                            text = "3. App, Permisos y Pay",
                            fontSize = 11.sp,
                            fontWeight = if (wizardStep == 3) FontWeight.Bold else FontWeight.Normal,
                            color = if (wizardStep == 3) UltraOrange else TextMuted,
                            modifier = Modifier.clickable { wizardStep = 3 }
                        )
                    }
                }
            }

            // Main Step Card
            item(key = "wizard_main_card") {
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
                                // STEP 1: Wi-Fi + Enable Wireless Debugging
                                Text(
                                    text = stringResource(R.string.step_1_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = stringResource(R.string.step_1_desc),
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    lineHeight = 19.sp
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
                                        lineHeight = 17.sp
                                    )
                                }

                                Button(
                                    onClick = { wizardStep = 2 },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = UltraOrange,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_already_enabled),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (isAlreadyPaired) {
                                    OutlinedButton(
                                        onClick = { wizardStep = 3 },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = stringResource(R.string.btn_direct_connect),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            2 -> {
                                // STEP 2: Pair with 6-Digit PIN + mDNS auto-detection
                                Text(
                                    text = stringResource(R.string.step_2_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = stringResource(R.string.step_2_desc),
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    lineHeight = 18.sp
                                )

                                // mDNS Live Discovery Banner
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (mdnsPairDetected) Color(0xFF132A1C) else NeutralSlate)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (!mdnsPairDetected) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                color = UltraOrange,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(GreenActive)
                                            )
                                        }
                                        Text(
                                            text = if (mdnsPairDetected) {
                                                stringResource(R.string.mdns_detected_pair, watchIp, pairPort)
                                            } else {
                                                stringResource(R.string.mdns_detecting)
                                            },
                                            fontSize = 12.sp,
                                            color = if (mdnsPairDetected) GreenActive else TextMuted,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // 6-Digit PIN Input Box
                                OutlinedTextField(
                                    value = pairingCode,
                                    onValueChange = { newValue ->
                                        pairingCode = newValue.filter { it.isDigit() }.take(6)
                                    },
                                    label = { Text(stringResource(R.string.label_pairing_code)) },
                                    placeholder = { Text("000000") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = UltraOrange,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                // IP and Pairing Port fields
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = watchIp,
                                        onValueChange = { watchIp = it },
                                        label = { Text(stringResource(R.string.label_watch_ip)) },
                                        placeholder = { Text("192.168.1.X") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.7f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = UltraOrange,
                                            unfocusedBorderColor = CardBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                    OutlinedTextField(
                                        value = pairPort,
                                        onValueChange = { pairPort = it.filter { c -> c.isDigit() } },
                                        label = { Text(stringResource(R.string.label_pair_port)) },
                                        placeholder = { Text("38291") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1.3f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = UltraOrange,
                                            unfocusedBorderColor = CardBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                }

                                if (pairStatusMessage.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isPairSuccess) Color(0xFF132A1C) else Color(0xFF2A1818)
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
                                                    .background(if (isPairSuccess) GreenActive else RedRevoke)
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

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
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
                                        enabled = !isPairing && pairingCode.length == 6,
                                        onClick = {
                                            val port = pairPort.toIntOrNull()
                                            if (watchIp.isBlank() || port == null || pairingCode.length != 6) {
                                                Toast.makeText(context, "Rellena IP, Puerto y PIN de 6 dígitos", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            scope.launch {
                                                isPairing = true
                                                pairStatusMessage = "Emparejando con el reloj..."
                                                val (ok, msg) = adbHelper.pairDevice(watchIp, port, pairingCode)
                                                isPairing = false
                                                isPairSuccess = ok
                                                pairStatusMessage = msg
                                                if (ok) {
                                                    isAlreadyPaired = true
                                                    wizardStep = 3
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
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text(stringResource(R.string.btn_pair_watch), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.skip_to_step_3),
                                    fontSize = 12.sp,
                                    color = UltraOrange,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { wizardStep = 3 }
                                        .padding(top = 2.dp)
                                )
                            }

                            3 -> {
                                // STEP 3: Connection & 1-Tap Grant All
                                Text(
                                    text = stringResource(R.string.step_3_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = stringResource(R.string.step_3_desc),
                                    fontSize = 13.sp,
                                    color = TextMuted
                                )

                                if (mdnsConnectDetected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF132A1C))
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.mdns_detected_connect, watchIp, watchPort),
                                            fontSize = 12.sp,
                                            color = GreenActive,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = watchIp,
                                        onValueChange = { watchIp = it },
                                        label = { Text(stringResource(R.string.label_watch_ip)) },
                                        placeholder = { Text("192.168.1.X") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.7f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = UltraOrange,
                                            unfocusedBorderColor = CardBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                    OutlinedTextField(
                                        value = watchPort,
                                        onValueChange = { watchPort = it.filter { c -> c.isDigit() } },
                                        label = { Text(stringResource(R.string.label_main_port)) },
                                        placeholder = { Text("42071") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1.3f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = UltraOrange,
                                            unfocusedBorderColor = CardBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                }

                                // Primary Action: Grant All Permissions in 1 Tap
                                Button(
                                    enabled = !isConnecting,
                                    onClick = {
                                        val port = watchPort.toIntOrNull()
                                        if (watchIp.isBlank() || port == null) {
                                            Toast.makeText(context, "Introduce IP y Puerto de conexión", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        scope.launch {
                                            isConnecting = true
                                            adbStatusMessage = "Conectando y aplicando todos los permisos..."
                                            isStatusSuccess = false
                                            var hadError = false
                                            var errorMsg: String? = null

                                            for (perm in WatchPermission.values()) {
                                                val res = adbHelper.executeCommand(watchIp, port, perm.grantCmd)
                                                if (res.startsWith("ERROR:") && errorMsg == null) {
                                                    hadError = true
                                                    errorMsg = res
                                                    break
                                                }
                                            }
                                            if (!hadError) {
                                                refreshAllWatchStates()
                                                adbStatusMessage = "¡Todos los permisos concedidos con éxito!"
                                                isStatusSuccess = true
                                            } else {
                                                adbStatusMessage = "Fallo: $errorMsg. Verifica que Depuración Inalámbrica siga encendida."
                                                isStatusSuccess = false
                                            }
                                            isConnecting = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = UltraOrange,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isConnecting) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(stringResource(R.string.btn_grant_all), fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { refreshAllWatchStates() },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isCheckingPermissions) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text(stringResource(R.string.btn_scan_status), fontSize = 12.sp)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            adbHelper.unpair()
                                            isAlreadyPaired = false
                                            permissionStates.clear()
                                            isSamsungPayEnabled = null
                                            installedWatchVersion = null
                                            adbStatusMessage = "Vinculación ADB eliminada."
                                            isStatusSuccess = false
                                            Toast.makeText(context, "Vinculación borrada", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedRevoke),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.btn_unpair_adb), fontSize = 12.sp)
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

            // DEDICATED WATCH APK INSTALLER SECTION (STEP 3)
            if (wizardStep == 3) {
                item(key = "watch_apk_installer_section") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.hdr_watch_apk),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = UltraOrange
                                    )
                                    Text(
                                        text = stringResource(R.string.watch_apk_sub),
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (installedWatchVersion != null) GreenActive.copy(alpha = 0.16f) else NeutralSlate
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (installedWatchVersion != null) {
                                            "Instalada: v$installedWatchVersion"
                                        } else {
                                            stringResource(R.string.watch_ver_not_installed)
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (installedWatchVersion != null) GreenActive else TextMuted
                                    )
                                }
                            }

                            // 1-Tap Download from GitHub & Push to Watch via ADB
                            val watchRelease = latestWatchRelease
                            if (watchRelease?.watchApkUrl != null) {
                                val hasWatchUpdate = installedWatchVersion != null &&
                                    UpdateChecker.isNewerVersion(watchRelease.versionName, installedWatchVersion!!)
                                if (hasWatchUpdate) {
                                    Text(
                                        text = "¡Nueva versión disponible para el reloj: v${watchRelease.versionName}!",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = UltraOrange
                                    )
                                }
                                Button(
                                    enabled = !isInstallingWatchApk,
                                    onClick = {
                                        val port = watchPort.toIntOrNull()
                                        if (watchIp.isBlank() || port == null) {
                                            Toast.makeText(context, "Conecta primero el reloj (IP y Puerto)", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        isInstallingWatchApk = true
                                        watchApkProgress = 0.01f
                                        watchApkStatusMsg = "Descargando Ultra Remapper v${watchRelease.versionName}…"
                                        watchApkSuccess = false

                                        UpdateChecker.downloadApkToFile(
                                            context = context,
                                            apkUrl = watchRelease.watchApkUrl,
                                            targetFileName = "ultra-remapper-watch-v${watchRelease.versionName}.apk",
                                            onProgress = { frac, status ->
                                                watchApkProgress = frac * 0.45f
                                                watchApkStatusMsg = status
                                            }
                                        ) { downloadedFile ->
                                            if (downloadedFile == null) {
                                                isInstallingWatchApk = false
                                                watchApkStatusMsg = "Error al descargar el APK desde GitHub."
                                                return@downloadApkToFile
                                            }
                                            scope.launch {
                                                val (ok, msg) = adbHelper.installApkOnWatch(watchIp, port, downloadedFile) { frac, status ->
                                                    scope.launch {
                                                        watchApkProgress = 0.45f + (frac * 0.55f)
                                                        watchApkStatusMsg = status
                                                    }
                                                }
                                                watchApkSuccess = ok
                                                watchApkStatusMsg = msg
                                                isInstallingWatchApk = false
                                                if (ok) refreshAllWatchStates()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = UltraOrange,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_install_watch_github, watchRelease.versionName),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Local APK Picker button
                            OutlinedButton(
                                enabled = !isInstallingWatchApk,
                                onClick = {
                                    apkPickerLauncher.launch("application/vnd.android.package-archive")
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.btn_install_watch_local), fontSize = 12.sp)
                            }

                            if (isInstallingWatchApk || watchApkStatusMsg.isNotEmpty()) {
                                if (isInstallingWatchApk) {
                                    LinearProgressIndicator(
                                        progress = { watchApkProgress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = UltraOrange,
                                        trackColor = CardBorder
                                    )
                                }
                                Text(
                                    text = watchApkStatusMsg,
                                    fontSize = 12.sp,
                                    color = if (watchApkSuccess) GreenActive else if (isInstallingWatchApk) Color.White else RedRevoke,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // DEDICATED SAMSUNG PAY SECTION (STEP 3)
                item(key = "samsung_pay_section") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.hdr_samsung_pay),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = UltraOrange
                                )

                                isSamsungPayEnabled?.let { enabled ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (!enabled) GreenActive.copy(alpha = 0.16f) else UltraOrange.copy(alpha = 0.16f)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (!enabled) stringResource(R.string.samsung_pay_frozen_badge) else stringResource(R.string.samsung_pay_active_badge),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!enabled) GreenActive else UltraOrange
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = stringResource(R.string.samsung_pay_title),
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.samsung_pay_desc),
                                        fontSize = 12.sp,
                                        color = TextMuted,
                                        lineHeight = 16.sp
                                    )
                                }

                                if (isTogglingSamsungPay) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = UltraOrange,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Switch(
                                        checked = isSamsungPayEnabled ?: true,
                                        onCheckedChange = { targetEnabled ->
                                            val port = watchPort.toIntOrNull()
                                            if (watchIp.isBlank() || port == null) {
                                                Toast.makeText(context, "Conecta primero el reloj (IP y Puerto)", Toast.LENGTH_SHORT).show()
                                                return@Switch
                                            }
                                            scope.launch {
                                                isTogglingSamsungPay = true
                                                samsungPayStatusMsg = null
                                                val cmd = if (targetEnabled) {
                                                    "pm enable $SAMSUNG_PAY_PKG; pm unhide $SAMSUNG_PAY_PKG; cmd package install-existing $SAMSUNG_PAY_PKG"
                                                } else {
                                                    "pm disable-user --user 0 $SAMSUNG_PAY_PKG || pm disable-user $SAMSUNG_PAY_PKG || pm disable $SAMSUNG_PAY_PKG || pm hide $SAMSUNG_PAY_PKG"
                                                }
                                                val res = adbHelper.executeCommand(watchIp, port, cmd)
                                                val checkDisabled = adbHelper.executeCommand(watchIp, port, "pm list packages -d $SAMSUNG_PAY_PKG")
                                                val isNowDisabled = checkDisabled.contains(SAMSUNG_PAY_PKG)
                                                if (!res.startsWith("ERROR:")) {
                                                    isSamsungPayEnabled = !isNowDisabled
                                                    samsungPayStatusMsg = if (isNowDisabled) {
                                                        "Samsung Pay congelado. El botón inferior funcionará siempre sin interferencias."
                                                    } else {
                                                        "Samsung Pay reactivado."
                                                    }
                                                } else {
                                                    samsungPayStatusMsg = "Error al cambiar estado de Samsung Pay: $res"
                                                }
                                                isTogglingSamsungPay = false
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = UltraOrange
                                        )
                                    )
                                }
                            }

                            samsungPayStatusMsg?.let { msg ->
                                Text(
                                    text = msg,
                                    fontSize = 12.sp,
                                    color = if (msg.startsWith("Error")) RedRevoke else GreenActive,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // DEDICATED PERMISSIONS LIST SECTION (STEP 3)
                item(key = "permissions_section") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.hdr_permissions),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = UltraOrange
                                    )
                                    Text(
                                        text = stringResource(R.string.hdr_permissions_sub),
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }

                                Button(
                                    onClick = {
                                        val port = watchPort.toIntOrNull() ?: return@Button
                                        if (watchIp.isBlank()) return@Button
                                        scope.launch {
                                            isConnecting = true
                                            for (perm in WatchPermission.values()) {
                                                adbHelper.executeCommand(watchIp, port, perm.revokeCmd)
                                            }
                                            refreshAllWatchStates()
                                            isConnecting = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeutralSlate),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_revoke_all),
                                        color = RedRevoke,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            for (perm in WatchPermission.values()) {
                                val isGranted = permissionStates[perm]
                                val isLoadingThis = activeLoadingItem == perm.name

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NeutralSlate)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = perm.title,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 13.sp
                                                )
                                                if (isGranted != null) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(
                                                                if (isGranted) GreenActive.copy(alpha = 0.16f) else RedRevoke.copy(alpha = 0.16f)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = if (isGranted) stringResource(R.string.status_granted) else stringResource(R.string.status_missing),
                                                            fontSize = 10.sp,
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
                                        }

                                        if (isLoadingThis || (isCheckingPermissions && isGranted == null)) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                color = UltraOrange,
                                                strokeWidth = 2.5.dp
                                            )
                                        } else {
                                            Switch(
                                                checked = isGranted == true,
                                                onCheckedChange = { targetGrant ->
                                                    val port = watchPort.toIntOrNull()
                                                    if (watchIp.isBlank() || port == null) {
                                                        Toast.makeText(context, "Introduce IP y Puerto primero", Toast.LENGTH_SHORT).show()
                                                        return@Switch
                                                    }
                                                    scope.launch {
                                                        activeLoadingItem = perm.name
                                                        val cmd = if (targetGrant) perm.grantCmd else perm.revokeCmd
                                                        adbHelper.executeCommand(watchIp, port, cmd)
                                                        val checkRes = adbHelper.executeCommand(watchIp, port, perm.checkCmd)
                                                        permissionStates[perm] = checkRes.contains(perm.expectedGrantedOutput)
                                                        activeLoadingItem = null
                                                    }
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = GreenActive
                                                )
                                            )
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
                item(key = "dev_options") {
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

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
