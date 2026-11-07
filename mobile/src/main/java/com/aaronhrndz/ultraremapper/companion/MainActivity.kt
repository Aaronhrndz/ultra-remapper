package com.aaronhrndz.ultraremapper.companion

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private val UltraOrange = Color(0xFFFF5500)
private val DarkBg = Color(0xFF0E1014)
private val CardBg = Color(0xFF181B22)
private val SlateButton = Color(0xFF242934)
private val CardBorder = Color(0xFF282E3A)
private val GreenOk = Color(0xFF4CAF50)
private val RedAlert = Color(0xFFF44336)
private val RedBannerBg = Color(0xFF8E1B1B)
private val TextMuted = Color(0xFF9EA7B8)

private const val SAMSUNG_PAY_PKG = "com.samsung.android.samsungpay.gear"
private const val WATCH_APP_PKG = "com.aaronhrndz.ultraremapper"

enum class WatchPermission(
    val key: String,
    val title: String,
    val description: String,
    val grantCmd: String,
    val revokeCmd: String,
    val checkCmd: String,
    val expectedGrantedOutput: String
) {
    OVERLAY(
        key = "OVERLAY",
        title = "Overlay",
        description = "Superposición en segundo plano y linterna",
        grantCmd = "appops set com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW allow",
        revokeCmd = "appops set com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW ignore",
        checkCmd = "appops get com.aaronhrndz.ultraremapper SYSTEM_ALERT_WINDOW",
        expectedGrantedOutput = "allow"
    ),
    WRITE_SECURE(
        key = "WRITE_SECURE",
        title = "Secure Settings",
        description = "Ajustes del sistema, LTE, No Molestar y rutas",
        grantCmd = "pm grant com.aaronhrndz.ultraremapper android.permission.WRITE_SECURE_SETTINGS",
        revokeCmd = "pm revoke com.aaronhrndz.ultraremapper android.permission.WRITE_SECURE_SETTINGS",
        checkCmd = "dumpsys package com.aaronhrndz.ultraremapper | grep WRITE_SECURE_SETTINGS",
        expectedGrantedOutput = "granted=true"
    ),
    ACTION_ROUTING(
        key = "ACTION_ROUTING",
        title = "Botón de Acción",
        description = "Ruta de pulsación física del botón central",
        grantCmd = "settings put global setting_action_button_press com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.ActionButtonService",
        revokeCmd = "settings put global setting_action_button_press com.samsung.android.wear.shealth/com.samsung.android.wear.shealth.service.ActionButtonService",
        checkCmd = "settings get global setting_action_button_press",
        expectedGrantedOutput = "com.aaronhrndz.ultraremapper"
    ),
    MEDIA_BRIDGE(
        key = "MEDIA_BRIDGE",
        title = "Notificaciones",
        description = "Control multimedia y detección de ejercicio",
        grantCmd = "cmd notification allow_listener com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.MediaControlService",
        revokeCmd = "cmd notification disallow_listener com.aaronhrndz.ultraremapper/com.aaronhrndz.ultraremapper.MediaControlService",
        checkCmd = "settings get secure enabled_notification_listeners",
        expectedGrantedOutput = "com.aaronhrndz.ultraremapper"
    ),
    BATTERY_WHITELIST(
        key = "BATTERY_WHITELIST",
        title = "Sin restricción batería",
        description = "Evita cierre en reposo (Doze)",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionHomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val adbHelper = remember { AdbHelper.getInstance(context) }
    val adbClient = remember { AdbClient(context) }

    // Bluetooth / DataLayer Sync State
    var isBtSynced by remember { mutableStateOf(false) }
    var btWatchName by remember { mutableStateOf<String?>(null) }
    var installedWatchVersion by remember { mutableStateOf<String?>(null) }
    var isMasterEnabled by remember { mutableStateOf(true) }
    var isHapticEnabled by remember { mutableStateOf(true) }
    val gestureMappings = remember { mutableStateMapOf<String, String>() }
    val permissionStates = remember { mutableStateMapOf<WatchPermission, Boolean?>() }
    var a11yEnabled by remember { mutableStateOf(true) }
    var isAssistantConfigured by remember { mutableStateOf(true) }
    var isTocTocConfigured by remember { mutableStateOf(true) }
    var haInstalledOnWatch by remember { mutableStateOf(false) }
    var isSamsungPayEnabled by remember { mutableStateOf<Boolean?>(null) }
    var watchInstalledApps by remember { mutableStateOf<List<WatchAppEntry>>(emptyList()) }

    // ADB Connection State
    var isAdbConnected by remember { mutableStateOf(adbHelper.isConnected) }
    var isAdbPaired by remember { mutableStateOf(adbHelper.isPaired()) }
    var watchIp by remember { mutableStateOf(adbHelper.getLastHost()) }
    var pairPort by remember { mutableStateOf("") }
    var watchPort by remember { mutableStateOf(adbHelper.getLastPort()?.toString() ?: "") }
    var pairingCode by remember { mutableStateOf("") }
    var mdnsPairDetected by remember { mutableStateOf(false) }
    var mdnsConnectDetected by remember { mutableStateOf(false) }

    var isAdbConnecting by remember { mutableStateOf(false) }
    var isPairing by remember { mutableStateOf(false) }
    var adbStatusMsg by remember { mutableStateOf<String?>(null) }
    var isAdbStatusOk by remember { mutableStateOf(false) }

    // Collapsible Sections (Watch-style)
    var isTopExpanded by remember { mutableStateOf(false) }
    var isActionExpanded by remember { mutableStateOf(false) }
    var isBottomExpanded by remember { mutableStateOf(false) }
    var isGesturesExpanded by remember { mutableStateOf(false) }
    var isAdbExpanded by remember { mutableStateOf(!isAdbPaired && !isBtSynced) }
    var isPermsExpanded by remember { mutableStateOf(false) }

    // Loading spinners per item
    var activeLoadingItem by remember { mutableStateOf<String?>(null) }
    var samsungPayError by remember { mutableStateOf<String?>(null) }

    // OTA Updates (Mobile Companion + Watch APK)
    var isBetaEnabled by remember { mutableStateOf(UpdateChecker.isBetaChannel(context)) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var devOptionsUnlocked by remember { mutableStateOf(isBetaEnabled) }
    var companionUpdate by remember { mutableStateOf<ReleaseUpdate?>(null) }
    var latestWatchRelease by remember { mutableStateOf<ReleaseUpdate?>(null) }
    var companionUpdateProgress by remember { mutableFloatStateOf(0f) }
    var companionUpdateStatus by remember { mutableStateOf("") }

    var isInstallingWatchApk by remember { mutableStateOf(false) }
    var watchApkProgress by remember { mutableFloatStateOf(0f) }
    var watchApkStatusMsg by remember { mutableStateOf("") }
    var watchApkSuccess by remember { mutableStateOf(false) }

    // Action Picker Bottom Sheet state
    var pickingGesture by remember { mutableStateOf<String?>(null) }
    var pickingAppSublist by remember { mutableStateOf(false) }

    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.2.3"
        } catch (_: Exception) {
            "2.2.3"
        }
    }

    // Apply incoming Bluetooth / DataLayer state
    val dataLayerBridge = remember {
        WatchDataLayerBridge(context) { state, nodeName ->
            isBtSynced = true
            if (!nodeName.isNullOrBlank()) btWatchName = nodeName
            if (state.versionName.isNotBlank()) installedWatchVersion = state.versionName
            isMasterEnabled = state.masterEnabled
            isHapticEnabled = state.hapticEnabled
            for ((k, v) in state.mappings) {
                gestureMappings[k] = v
            }
            for (perm in WatchPermission.values()) {
                state.permissions[perm.key]?.let { permissionStates[perm] = it }
            }
            state.permissions["ACCESSIBILITY"]?.let { a11yEnabled = it }
            state.permissions["IS_ASSISTANT"]?.let { isAssistantConfigured = it }
            state.permissions["IS_TOCTOC"]?.let { isTocTocConfigured = it }
            state.permissions["HA_INSTALLED"]?.let { haInstalledOnWatch = it }
            state.permissions["SAMSUNG_PAY_ENABLED"]?.let { isSamsungPayEnabled = it }
            if (state.installedApps.isNotEmpty()) {
                watchInstalledApps = state.installedApps
            }
        }
    }

    DisposableEffect(Unit) {
        dataLayerBridge.start()
        onDispose {
            dataLayerBridge.stop()
        }
    }

    // Check GitHub Releases
    LaunchedEffect(isBetaEnabled) {
        UpdateChecker.checkAll(context) { compUpd, latestRel ->
            companionUpdate = compUpd
            latestWatchRelease = latestRel
        }
    }

    /**
     * Reads complete state from Watch over ADB (used when ADB connects or when Watch app is older/not installed).
     * Runs in a single batched shell call to be fast and avoid socket contention.
     */
    suspend fun fetchFullWatchStateViaAdb(host: String, port: Int): Boolean {
        val ok = adbHelper.ensureConnected(host, port)
        isAdbConnected = ok
        if (!ok) return false
        isAdbPaired = true

        // 1. Check installed version
        val verOut = adbHelper.executeCommand(host, port, "dumpsys package $WATCH_APP_PKG | grep versionName")
        val verMatch = Regex("versionName=([^\\s]+)").find(verOut)
        installedWatchVersion = verMatch?.groupValues?.getOrNull(1)

        // 2. Check all 5 permissions
        for (perm in WatchPermission.values()) {
            val out = adbHelper.executeCommand(host, port, perm.checkCmd)
            permissionStates[perm] = out.contains(perm.expectedGrantedOutput)
        }

        // 3. Check Accessibility, Assistant, TocToc
        val a11yOut = adbHelper.executeCommand(host, port, "settings get secure enabled_accessibility_services")
        a11yEnabled = a11yOut.contains(WATCH_APP_PKG)

        val assistOut = adbHelper.executeCommand(host, port, "settings get secure voice_interaction_service; settings get secure assistant")
        isAssistantConfigured = assistOut.contains(WATCH_APP_PKG)

        val toctocOut = adbHelper.executeCommand(host, port, "settings get global setting_gesture_launch_app")
        isTocTocConfigured = toctocOut.contains(WATCH_APP_PKG)

        // 4. Reliable Samsung Pay enabled/frozen check
        val spayDump = adbHelper.executeCommand(
            host,
            port,
            "pm list packages -d $SAMSUNG_PAY_PKG; dumpsys package $SAMSUNG_PAY_PKG | grep -E 'enabled=[234]|enabledSetting=[234]'"
        )
        val isSpayDisabled = spayDump.contains("package:$SAMSUNG_PAY_PKG") ||
            spayDump.contains("enabled=2") ||
            spayDump.contains("enabled=3") ||
            spayDump.contains("enabled=4") ||
            spayDump.contains("enabledSetting=2") ||
            spayDump.contains("enabledSetting=3") ||
            spayDump.contains("enabledSetting=4")
        isSamsungPayEnabled = !isSpayDisabled

        // 5. Read shared_prefs mappings if Bluetooth hasn't synced them yet
        if (!isBtSynced && installedWatchVersion != null) {
            val prefsXml = adbHelper.executeCommand(
                host,
                port,
                "run-as $WATCH_APP_PKG cat shared_prefs/mapping.xml 2>/dev/null"
            )
            if (prefsXml.contains("<map>")) {
                Regex("<string name=\"([^\"]+)\">([^<]+)</string>").findAll(prefsXml).forEach { m ->
                    gestureMappings[m.groupValues[1]] = m.groupValues[2]
                }
                Regex("<boolean name=\"remapper_master_enabled\" value=\"(true|false)\"").find(prefsXml)?.let {
                    isMasterEnabled = it.groupValues[1].toBoolean()
                }
                Regex("<boolean name=\"remapper_haptic_feedback_enabled\" value=\"(true|false)\"").find(prefsXml)?.let {
                    isHapticEnabled = it.groupValues[1].toBoolean()
                }
            }
        }

        // Also ask watch to push DataLayer state over Bluetooth
        dataLayerBridge.requestSyncFromWatch()
        return true
    }

    // Start mDNS discovery ONLY when ADB collapsible is expanded AND not yet connected
    DisposableEffect(isAdbExpanded, isAdbConnected) {
        if (isAdbExpanded && !isAdbConnected) {
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

    // Auto-connect & auto-fetch when mDNS detects watch connect port and we are already paired
    LaunchedEffect(mdnsConnectDetected, isAdbPaired) {
        val port = watchPort.toIntOrNull()
        if (isAdbPaired && !isAdbConnected && !isAdbConnecting && watchIp.isNotBlank() && port != null) {
            isAdbConnecting = true
            val ok = fetchFullWatchStateViaAdb(watchIp, port)
            isAdbConnecting = false
            if (ok) {
                adbStatusMsg = "Conectado ($watchIp:$port)"
                isAdbStatusOk = true
                isAdbExpanded = false // Auto-collapse ADB card once connected!
            }
        }
    }

    // Helper to update a button/gesture mapping (via Bluetooth DataLayer + ADB fallback)
    fun updateWatchMapping(gesture: String, action: String) {
        gestureMappings[gesture] = action
        val cmd = JSONObject().apply {
            put("type", "set_mapping")
            put("gesture", gesture)
            put("action", action)
        }
        dataLayerBridge.sendCommandToWatch(cmd)
    }

    fun updateMasterSwitch(enabled: Boolean) {
        isMasterEnabled = enabled
        val cmd = JSONObject().apply {
            put("type", "set_master")
            put("enabled", enabled)
        }
        dataLayerBridge.sendCommandToWatch(cmd)
    }

    fun updateHapticSwitch(enabled: Boolean) {
        isHapticEnabled = enabled
        val cmd = JSONObject().apply {
            put("type", "set_haptic")
            put("enabled", enabled)
        }
        dataLayerBridge.sendCommandToWatch(cmd)
    }

    // Diagnostic calculation (exact match with Watch Home screen)
    val allConfiguredActions = WatchActionCatalog.allGestures.map { gestureMappings[it.first] ?: "none" }.toSet()
    val bottomConfigured = WatchActionCatalog.bottomGestures.any { (gestureMappings[it.first] ?: "none") != "none" }
    val needsA11y = bottomConfigured || allConfiguredActions.contains("flashlight")
    val needsSecureSettings = allConfiguredActions.any { it in setOf("lte_toggle", "adb_mode") } ||
        (gestureMappings["toctoc"] ?: "none") != "none" ||
        (gestureMappings["top_double"] ?: "none") != "none" ||
        (gestureMappings["top_long"] ?: "none") != "none"
    val needsMediaBridge = a11yEnabled || allConfiguredActions.any { act ->
        act == "flashlight" || WatchActionCatalog.mediaActions.any { it.first == act }
    }
    val needsOverlay = allConfiguredActions.contains("red_flashlight") || allConfiguredActions.contains("flashlight")
    val topLongAct = gestureMappings["top_long"] ?: "none"
    val needsAssistant = isMasterEnabled && topLongAct != "none" && topLongAct != "assistant"
    val needsTocToc = isMasterEnabled && (gestureMappings["toctoc"] ?: "none") != "none"
    val needsSamsungPayFreeze = isMasterEnabled &&
        (gestureMappings["bottom_long"] ?: "none") != "none" &&
        (isSamsungPayEnabled == true)

    val watchConnectedAny = isBtSynced || isAdbConnected

    Scaffold(
        containerColor = DarkBg
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp, start = 16.dp, end = 16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. HEADER + CONNECTION PILL
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ultra Remapper",
                                fontSize = 21.sp,
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
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = UltraOrange
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Bluetooth Sync Pill
                            StatusPill(
                                label = if (isBtSynced) {
                                    "BT: ${btWatchName ?: "Sincronizado"}"
                                } else {
                                    "BT: Esperando reloj"
                                },
                                active = isBtSynced,
                                activeColor = GreenOk
                            )
                            // ADB Sync Pill
                            StatusPill(
                                label = if (isAdbConnected) {
                                    "ADB: Conectado"
                                } else if (isAdbPaired) {
                                    "ADB: Vinculado"
                                } else {
                                    "ADB: No vinculado"
                                },
                                active = isAdbConnected,
                                activeColor = UltraOrange
                            )
                        }
                    }

                    // Version Tag (7 taps -> Dev options)
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
                                        if (devOptionsUnlocked) "Opciones de desarrollador activadas" else "Opciones de desarrollador ocultas",
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

            // 2. COMPANION APP SELF-UPDATE BANNER (if available)
            companionUpdate?.let { upd ->
                item(key = "companion_update_banner") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, UltraOrange, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF261812))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Actualización de App Móvil disponible: v${upd.versionName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = UltraOrange
                            )
                            if (companionUpdateStatus.isNotEmpty()) {
                                LinearProgressIndicator(
                                    progress = { companionUpdateProgress },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                    color = UltraOrange,
                                    trackColor = CardBorder
                                )
                                Text(companionUpdateStatus, fontSize = 12.sp, color = Color.White)
                            } else {
                                Button(
                                    onClick = {
                                        UpdateChecker.downloadAndInstallCompanion(context, upd) { frac, status ->
                                            companionUpdateProgress = frac
                                            companionUpdateStatus = status
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = UltraOrange),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Actualizar App Móvil", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // 3. WATCH APP INSTALL / UPDATE BANNER (Clean, no local APK button)
            val watchRel = latestWatchRelease
            val watchNeedsInstall = (isAdbConnected && installedWatchVersion == null)
            val watchHasUpdate = (installedWatchVersion != null && watchRel != null &&
                UpdateChecker.isNewerVersion(watchRel.versionName, installedWatchVersion!!))

            if (watchNeedsInstall || watchHasUpdate || isInstallingWatchApk || watchApkStatusMsg.isNotEmpty()) {
                item(key = "watch_apk_banner") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, UltraOrange.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (installedWatchVersion == null) {
                                        "Instalar Ultra Remapper en el Reloj"
                                    } else {
                                        "Actualización para el Reloj: v${watchRel?.versionName ?: ""}"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = UltraOrange
                                )
                                Text(
                                    text = if (installedWatchVersion != null) "Instalada: v$installedWatchVersion" else "No instalada",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            if (watchRel?.watchApkUrl != null && !isInstallingWatchApk) {
                                Button(
                                    onClick = {
                                        val port = watchPort.toIntOrNull()
                                        if (!isAdbConnected || watchIp.isBlank() || port == null) {
                                            isAdbExpanded = true
                                            Toast.makeText(context, "Conecta primero por ADB al reloj", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        isInstallingWatchApk = true
                                        watchApkProgress = 0.02f
                                        watchApkStatusMsg = "Descargando v${watchRel.versionName}…"
                                        watchApkSuccess = false

                                        UpdateChecker.downloadApkToFile(
                                            context = context,
                                            apkUrl = watchRel.watchApkUrl,
                                            targetFileName = "ultra-remapper-watch-v${watchRel.versionName}.apk",
                                            onProgress = { frac, status ->
                                                watchApkProgress = frac * 0.45f
                                                watchApkStatusMsg = status
                                            }
                                        ) { file ->
                                            if (file == null) {
                                                isInstallingWatchApk = false
                                                watchApkStatusMsg = "Error al descargar el APK de GitHub."
                                                return@downloadApkToFile
                                            }
                                            scope.launch {
                                                val (ok, msg) = adbHelper.installApkOnWatch(watchIp, port, file) { frac, status ->
                                                    scope.launch {
                                                        watchApkProgress = 0.45f + (frac * 0.55f)
                                                        watchApkStatusMsg = status
                                                    }
                                                }
                                                watchApkSuccess = ok
                                                watchApkStatusMsg = msg
                                                isInstallingWatchApk = false
                                                if (ok) fetchFullWatchStateViaAdb(watchIp, port)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = UltraOrange),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (installedWatchVersion == null) {
                                            "Descargar e Instalar v${watchRel.versionName} en el Reloj"
                                        } else {
                                            "Actualizar Reloj a v${watchRel.versionName}"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            if (isInstallingWatchApk || watchApkStatusMsg.isNotEmpty()) {
                                if (isInstallingWatchApk) {
                                    LinearProgressIndicator(
                                        progress = { watchApkProgress },
                                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                        color = UltraOrange,
                                        trackColor = CardBorder
                                    )
                                }
                                Text(
                                    text = watchApkStatusMsg,
                                    fontSize = 12.sp,
                                    color = if (watchApkSuccess) GreenOk else if (isInstallingWatchApk) Color.White else RedAlert,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 4. MISSING PERMISSIONS / DIAGNOSTIC BANNERS (Same as Watch Home)
            if (watchConnectedAny) {
                val missingRoute = permissionStates[WatchPermission.ACTION_ROUTING] == false
                val missingOverlay = permissionStates[WatchPermission.OVERLAY] == false && needsOverlay
                val missingMedia = permissionStates[WatchPermission.MEDIA_BRIDGE] == false && needsMediaBridge
                val missingSecure = permissionStates[WatchPermission.WRITE_SECURE] == false && needsSecureSettings
                val missingA11y = !a11yEnabled && needsA11y
                val missingAssistant = !isAssistantConfigured && needsAssistant
                val missingTocToc = !isTocTocConfigured && needsTocToc

                if (missingRoute || missingOverlay || missingMedia || missingSecure || missingA11y || missingAssistant || missingTocToc || needsSamsungPayFreeze) {
                    item(key = "diag_banners") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Acciones necesarias",
                                color = RedAlert,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            if (missingRoute) {
                                DiagBanner(
                                    title = "Enrutado de botón inactivo",
                                    subtitle = "Toca para activar Ruta del Botón de Acción",
                                    badge = "ADB"
                                ) {
                                    isPermsExpanded = true
                                }
                            }
                            if (missingOverlay) {
                                DiagBanner(
                                    title = "Falta permiso de superposición",
                                    subtitle = "Necesario para abrir apps y linterna RGBW",
                                    badge = "ADB"
                                ) {
                                    isPermsExpanded = true
                                }
                            }
                            if (missingSecure) {
                                DiagBanner(
                                    title = "Falta Secure Settings",
                                    subtitle = "Necesario para LTE, Toc toc y doble toque superior",
                                    badge = "ADB"
                                ) {
                                    isPermsExpanded = true
                                }
                            }
                            if (missingMedia) {
                                DiagBanner(
                                    title = "Control multimedia inactivo",
                                    subtitle = "Activar acceso a notificaciones en Permisos",
                                    badge = "ADB"
                                ) {
                                    isPermsExpanded = true
                                }
                            }
                            if (missingA11y) {
                                DiagBanner(
                                    title = "Activar Accesibilidad en el reloj",
                                    subtitle = "Toca para abrir Ajustes de Accesibilidad en el reloj",
                                    badge = "RELOJ"
                                ) {
                                    dataLayerBridge.sendCommandToWatch(JSONObject().put("type", "open_a11y"))
                                    Toast.makeText(context, "Abriendo Accesibilidad en el reloj…", Toast.LENGTH_SHORT).show()
                                }
                            }
                            if (missingAssistant) {
                                DiagBanner(
                                    title = "Elegir como Asistente Digital",
                                    subtitle = "Toca para abrir selector de Asistente en el reloj",
                                    badge = "RELOJ"
                                ) {
                                    dataLayerBridge.sendCommandToWatch(JSONObject().put("type", "open_assistant"))
                                    Toast.makeText(context, "Selecciona Ultra Remapper en el reloj", Toast.LENGTH_SHORT).show()
                                }
                            }
                            if (missingTocToc) {
                                DiagBanner(
                                    title = "Configurar gesto Toc toc",
                                    subtitle = "Toca para abrir ajustes de Botones y Gestos en el reloj",
                                    badge = "RELOJ"
                                ) {
                                    dataLayerBridge.sendCommandToWatch(JSONObject().put("type", "open_toctoc"))
                                    Toast.makeText(context, "Elige Ultra Remapper en Toc toc del reloj", Toast.LENGTH_SHORT).show()
                                }
                            }
                            if (needsSamsungPayFreeze) {
                                DiagBanner(
                                    title = "Conflicto con Samsung Pay",
                                    subtitle = "Congela Samsung Pay abajo para que el botón inferior no falle",
                                    badge = "ADB"
                                ) {
                                    scope.launch { listState.animateScrollToItem(8) }
                                }
                            }
                        }
                    }
                }
            }

            // 5. BUTTON & GESTURE CONFIGURATION (Mirrors Watch UI Collapsibles)
            item(key = "hdr_remap_config") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Botones y Gestos del Reloj",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (!watchConnectedAny) {
                        Text(
                            text = "Conecta el reloj para configurar",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Top Button Collapsible
            item(key = "card_top_button") {
                WatchSectionCollapsible(
                    iconRes = R.drawable.ic_watch_top_button,
                    title = "Botón Superior",
                    subtitle = WatchActionCatalog.topGestures.count { (gestureMappings[it.first] ?: "none") != "none" }
                        .let { if (it > 0) "$it configurados" else "Sin asignar" },
                    isExpanded = isTopExpanded,
                    onToggle = { isTopExpanded = !isTopExpanded }
                ) {
                    WatchActionCatalog.topGestures.forEach { (gesture, name) ->
                        val act = gestureMappings[gesture] ?: "none"
                        GestureMappingRow(
                            gestureName = name,
                            actionLabel = WatchActionCatalog.getActionLabel(act, watchInstalledApps),
                            isConfigured = act != "none",
                            enabled = watchConnectedAny
                        ) {
                            pickingAppSublist = false
                            pickingGesture = gesture
                        }
                    }
                }
            }

            // Center / Action Button Collapsible
            item(key = "card_action_button") {
                WatchSectionCollapsible(
                    iconRes = R.drawable.ic_watch_quick_button,
                    title = "Botón Central (Acción)",
                    subtitle = WatchActionCatalog.actionGestures.count { (gestureMappings[it.first] ?: "none") != "none" }
                        .let { if (it > 0) "$it configurados" else "Sin asignar" },
                    isExpanded = isActionExpanded,
                    onToggle = { isActionExpanded = !isActionExpanded }
                ) {
                    Text(
                        text = "Con Samsung Health en pantalla la app se pausa para permitir los controles nativos.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    WatchActionCatalog.actionGestures.forEach { (gesture, name) ->
                        val act = gestureMappings[gesture] ?: "none"
                        GestureMappingRow(
                            gestureName = name,
                            actionLabel = WatchActionCatalog.getActionLabel(act, watchInstalledApps),
                            isConfigured = act != "none",
                            enabled = watchConnectedAny
                        ) {
                            pickingAppSublist = false
                            pickingGesture = gesture
                        }
                    }
                }
            }

            // Bottom Button Collapsible
            item(key = "card_bottom_button") {
                WatchSectionCollapsible(
                    iconRes = R.drawable.ic_watch_bottom_button,
                    title = "Botón Inferior",
                    subtitle = WatchActionCatalog.bottomGestures.count { (gestureMappings[it.first] ?: "none") != "none" }
                        .let { if (it > 0) "$it configurados" else "Sin asignar" },
                    isExpanded = isBottomExpanded,
                    onToggle = { isBottomExpanded = !isBottomExpanded }
                ) {
                    WatchActionCatalog.bottomGestures.forEach { (gesture, name) ->
                        val act = gestureMappings[gesture] ?: "none"
                        GestureMappingRow(
                            gestureName = name,
                            actionLabel = WatchActionCatalog.getActionLabel(act, watchInstalledApps),
                            isConfigured = act != "none",
                            enabled = watchConnectedAny
                        ) {
                            pickingAppSublist = false
                            pickingGesture = gesture
                        }
                    }
                }
            }

            // Gestures (Toc toc) Collapsible
            item(key = "card_gestures") {
                WatchSectionCollapsible(
                    iconRes = R.drawable.ic_wrist_gesture,
                    title = "Gestos",
                    subtitle = WatchActionCatalog.gestureList.count { (gestureMappings[it.first] ?: "none") != "none" }
                        .let { if (it > 0) "Toc toc activo" else "Sin asignar" },
                    isExpanded = isGesturesExpanded,
                    onToggle = { isGesturesExpanded = !isGesturesExpanded }
                ) {
                    WatchActionCatalog.gestureList.forEach { (gesture, name) ->
                        val act = gestureMappings[gesture] ?: "none"
                        GestureMappingRow(
                            gestureName = name,
                            actionLabel = WatchActionCatalog.getActionLabel(act, watchInstalledApps),
                            isConfigured = act != "none",
                            enabled = watchConnectedAny
                        ) {
                            pickingAppSublist = false
                            pickingGesture = gesture
                        }
                    }
                }
            }

            // 6. SETTINGS & ADB SECTION HEADER (Mirrors Watch SettingsScreen)
            item(key = "hdr_settings_section") {
                Text(
                    text = "Ajustes y Sistema del Reloj",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Master Remapper & Haptic Switches
            item(key = "master_haptic_card") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WatchToggleRow(
                        title = "Remapear botones",
                        subtitle = if (isMasterEnabled) "Activo en el reloj" else "Pausado",
                        checked = isMasterEnabled,
                        enabled = watchConnectedAny,
                        onToggle = { updateMasterSwitch(!isMasterEnabled) }
                    )
                    WatchToggleRow(
                        title = "Vibración háptica",
                        subtitle = "Respuesta táctil al pulsar botones",
                        checked = isHapticEnabled,
                        enabled = watchConnectedAny,
                        onToggle = { updateHapticSwitch(!isHapticEnabled) }
                    )
                }
            }

            // 7. COLLAPSIBLE ADB CONNECTION CARD (Auto-closes when connected!)
            item(key = "adb_collapsible") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardBg)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isAdbExpanded = !isAdbExpanded }
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ajustes ADB Inalámbrico",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val statusText = when {
                                isAdbConnecting -> "ADB: Conectando al reloj…"
                                isAdbConnected -> "ADB: Conectado ($watchIp:$watchPort)"
                                isAdbPaired -> "ADB: Vinculado (Toca para conectar)"
                                else -> "ADB: No emparejado (Necesario para conceder permisos)"
                            }
                            Text(
                                text = statusText,
                                color = when {
                                    isAdbConnecting -> Color(0xFFFFB74D)
                                    isAdbConnected -> GreenOk
                                    isAdbPaired -> Color(0xFF64B5F6)
                                    else -> TextMuted
                                },
                                fontSize = 12.sp
                            )
                        }
                        CollapseChevron(isExpanded = isAdbExpanded)
                    }

                    AnimatedVisibility(visible = isAdbExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "1. Conecta reloj y móvil al mismo Wi-Fi.\n2. En el reloj abre Opciones de desarrollador › Depuración inalámbrica.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )

                            if (mdnsPairDetected || mdnsConnectDetected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF132A1C))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (mdnsPairDetected) {
                                            "✓ Reloj detectado en modo vinculación ($watchIp:$pairPort)"
                                        } else {
                                            "✓ Reloj detectado en red ($watchIp:$watchPort)"
                                        },
                                        fontSize = 12.sp,
                                        color = GreenOk,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // IP & Connection Port
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = watchIp,
                                    onValueChange = { watchIp = it },
                                    label = { Text("IP del Reloj", fontSize = 11.sp) },
                                    placeholder = { Text("192.168.1.X") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.6f),
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
                                    label = { Text("Puerto ADB", fontSize = 11.sp) },
                                    placeholder = { Text("42071") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = UltraOrange,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }

                            // Optional 6-digit Pairing row (shown if not paired yet or user wants to pair)
                            if (!isAdbPaired || mdnsPairDetected) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = pairingCode,
                                        onValueChange = { pairingCode = it.filter { c -> c.isDigit() }.take(6) },
                                        label = { Text("PIN 6 dígitos", fontSize = 11.sp) },
                                        placeholder = { Text("000000") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        modifier = Modifier.weight(1.2f),
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
                                        label = { Text("Puerto PIN", fontSize = 11.sp) },
                                        placeholder = { Text("38123") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = UltraOrange,
                                            unfocusedBorderColor = CardBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                }

                                Button(
                                    enabled = !isPairing && pairingCode.length == 6,
                                    onClick = {
                                        val pPort = pairPort.toIntOrNull()
                                        if (watchIp.isBlank() || pPort == null) {
                                            Toast.makeText(context, "Indica IP y Puerto de vinculación", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        scope.launch {
                                            isPairing = true
                                            adbStatusMsg = "Vinculando con el reloj…"
                                            val (ok, msg) = adbHelper.pairDevice(watchIp, pPort, pairingCode)
                                            isPairing = false
                                            adbStatusMsg = msg
                                            isAdbStatusOk = ok
                                            if (ok) {
                                                isAdbPaired = true
                                                pairingCode = ""
                                                // Auto-connect right after pairing if connect port is known
                                                val cPort = watchPort.toIntOrNull()
                                                if (cPort != null) {
                                                    isAdbConnecting = true
                                                    val connOk = fetchFullWatchStateViaAdb(watchIp, cPort)
                                                    isAdbConnecting = false
                                                    if (connOk) {
                                                        isAdbExpanded = false
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = UltraOrange),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isPairing) {
                                        SwitchLoadingSpinner()
                                    } else {
                                        Text("Vincular Reloj con PIN", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }

                            // Connect / Refresh & Unpair buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    enabled = !isAdbConnecting,
                                    onClick = {
                                        val port = watchPort.toIntOrNull()
                                        if (watchIp.isBlank() || port == null) {
                                            Toast.makeText(context, "Introduce IP y Puerto ADB del reloj", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        scope.launch {
                                            isAdbConnecting = true
                                            adbStatusMsg = "Conectando y leyendo estado del reloj…"
                                            val ok = fetchFullWatchStateViaAdb(watchIp, port)
                                            isAdbConnecting = false
                                            if (ok) {
                                                adbStatusMsg = "Conectado ($watchIp:$port)"
                                                isAdbStatusOk = true
                                                delay(350)
                                                isAdbExpanded = false // Close ADB collapsible automatically!
                                            } else {
                                                adbStatusMsg = "No se pudo conectar a $watchIp:$port. Verifica que Depuración Inalámbrica esté activa."
                                                isAdbStatusOk = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isAdbConnected) SlateButton else UltraOrange),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.4f)
                                ) {
                                    if (isAdbConnecting) {
                                        SwitchLoadingSpinner()
                                    } else {
                                        Text(
                                            text = if (isAdbConnected) "Refrescar ADB" else "Conectar ADB",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                if (isAdbPaired) {
                                    OutlinedButton(
                                        onClick = {
                                            adbHelper.unpair()
                                            isAdbPaired = false
                                            isAdbConnected = false
                                            adbStatusMsg = "Vinculación ADB borrada."
                                            isAdbStatusOk = false
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAlert),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Borrar ADB", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            adbStatusMsg?.let { msg ->
                                Text(
                                    text = msg,
                                    fontSize = 12.sp,
                                    color = if (isAdbStatusOk) GreenOk else UltraOrange,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 8. COLLAPSIBLE PERMISSIONS CARD (Mirrors Watch Permissions Menu)
            item(key = "permissions_collapsible") {
                val grantedCount = WatchPermission.values().count { permissionStates[it] == true }
                val totalPerms = WatchPermission.values().size

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardBg)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isPermsExpanded = !isPermsExpanded }
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permisos",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (permissionStates.isEmpty()) {
                                    "Conecta el reloj para ver permisos"
                                } else {
                                    "$grantedCount de $totalPerms activos"
                                },
                                color = if (grantedCount == totalPerms && totalPerms > 0) GreenOk else TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        CollapseChevron(isExpanded = isPermsExpanded)
                    }

                    AnimatedVisibility(visible = isPermsExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WatchPermission.values().forEach { perm ->
                                val isGranted = permissionStates[perm] == true
                                val isThisLoading = activeLoadingItem == perm.key

                                WatchToggleRow(
                                    title = perm.title,
                                    subtitle = perm.description,
                                    checked = isGranted,
                                    isLoading = isThisLoading,
                                    enabled = true,
                                    onToggle = {
                                        val port = watchPort.toIntOrNull()
                                        if (watchIp.isBlank() || port == null) {
                                            isAdbExpanded = true
                                            Toast.makeText(context, "Conecta primero por ADB al reloj", Toast.LENGTH_SHORT).show()
                                            return@WatchToggleRow
                                        }
                                        val targetState = !isGranted
                                        activeLoadingItem = perm.key
                                        scope.launch {
                                            try {
                                                val cmd = if (targetState) perm.grantCmd else perm.revokeCmd
                                                val res = adbHelper.executeCommand(watchIp, port, cmd)
                                                if (res.startsWith("ERROR:")) {
                                                    isAdbConnected = false
                                                    isAdbExpanded = true
                                                    Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val checkOut = adbHelper.executeCommand(watchIp, port, perm.checkCmd)
                                                    permissionStates[perm] = checkOut.contains(perm.expectedGrantedOutput)
                                                    dataLayerBridge.requestSyncFromWatch()
                                                }
                                            } finally {
                                                activeLoadingItem = null
                                            }
                                        }
                                    }
                                )
                            }

                            // Subtle secondary link to grant missing permissions if any are missing
                            if (grantedCount < totalPerms) {
                                OutlinedButton(
                                    enabled = activeLoadingItem == null,
                                    onClick = {
                                        val port = watchPort.toIntOrNull()
                                        if (watchIp.isBlank() || port == null) {
                                            isAdbExpanded = true
                                            Toast.makeText(context, "Conecta primero por ADB al reloj", Toast.LENGTH_SHORT).show()
                                            return@OutlinedButton
                                        }
                                        scope.launch {
                                            activeLoadingItem = "ALL_PERMS"
                                            try {
                                                for (perm in WatchPermission.values()) {
                                                    adbHelper.executeCommand(watchIp, port, perm.grantCmd)
                                                }
                                                fetchFullWatchStateViaAdb(watchIp, port)
                                            } finally {
                                                activeLoadingItem = null
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = UltraOrange),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (activeLoadingItem == "ALL_PERMS") {
                                        SwitchLoadingSpinner(color = UltraOrange)
                                    } else {
                                        Text("Activar permisos faltantes", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 9. SAMSUNG PAY FREEZE ROW (Mirrors Watch SettingsScreen)
            item(key = "samsung_pay_row") {
                val isPayLoading = activeLoadingItem == "SAMSUNG_PAY"
                val payEnabled = isSamsungPayEnabled ?: true

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardBg)
                        .padding(12.dp)
                ) {
                    WatchToggleRow(
                        title = "Samsung Pay",
                        subtitle = when (isSamsungPayEnabled) {
                            true -> "Activo (Desactívalo para evitar conflictos con botón inferior)"
                            false -> "Congelado (El botón inferior funciona al 100%)"
                            null -> "Conecta el reloj para ver estado"
                        },
                        subtitleColor = if (isSamsungPayEnabled == false) UltraOrange else TextMuted,
                        checked = payEnabled,
                        isLoading = isPayLoading,
                        enabled = true,
                        onToggle = {
                            val port = watchPort.toIntOrNull()
                            if (watchIp.isBlank() || port == null) {
                                isAdbExpanded = true
                                Toast.makeText(context, "Conecta primero por ADB al reloj", Toast.LENGTH_SHORT).show()
                                return@WatchToggleRow
                            }
                            val targetEnabled = !payEnabled
                            activeLoadingItem = "SAMSUNG_PAY"
                            samsungPayError = null
                            scope.launch {
                                try {
                                    val cmd = if (targetEnabled) {
                                        "pm enable $SAMSUNG_PAY_PKG; pm unhide $SAMSUNG_PAY_PKG; cmd package install-existing $SAMSUNG_PAY_PKG"
                                    } else {
                                        "pm disable-user --user 0 $SAMSUNG_PAY_PKG || pm disable-user $SAMSUNG_PAY_PKG || pm disable $SAMSUNG_PAY_PKG || pm hide $SAMSUNG_PAY_PKG"
                                    }
                                    val res = adbHelper.executeCommand(watchIp, port, cmd)
                                    if (res.startsWith("ERROR:")) {
                                        isAdbConnected = false
                                        isAdbExpanded = true
                                        samsungPayError = res
                                    } else {
                                        fetchFullWatchStateViaAdb(watchIp, port)
                                    }
                                } finally {
                                    activeLoadingItem = null
                                }
                            }
                        }
                    )

                    samsungPayError?.let { err ->
                        Text(
                            text = err,
                            color = RedAlert,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                        )
                    }
                }
            }

            // 10. DEVELOPER OPTIONS (If unlocked with 7 taps)
            if (devOptionsUnlocked) {
                item(key = "dev_options") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(CardBg)
                            .padding(12.dp)
                    ) {
                        WatchToggleRow(
                            title = "Canal Pre-release",
                            subtitle = "Recibir versiones preliminares desde GitHub",
                            checked = isBetaEnabled,
                            enabled = true,
                            onToggle = {
                                val next = !isBetaEnabled
                                isBetaEnabled = next
                                UpdateChecker.setBetaChannel(context, next)
                            }
                        )
                    }
                }
            }
        }
    }

    // MODAL BOTTOM SHEET FOR PICKING BUTTON / GESTURE ACTION (Same categories as Watch)
    val activeGesture = pickingGesture
    if (activeGesture != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val currentAct = gestureMappings[activeGesture] ?: "none"

        ModalBottomSheet(
            onDismissRequest = { pickingGesture = null },
            sheetState = sheetState,
            containerColor = CardBg,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = if (pickingAppSublist) {
                        "Seleccionar App del Reloj"
                    } else {
                        WatchActionCatalog.getGestureTitle(activeGesture)
                    },
                    color = UltraOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (pickingAppSublist) {
                        item(key = "back_to_actions") {
                            OutlinedButton(
                                onClick = { pickingAppSublist = false },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("‹ Volver a acciones")
                            }
                        }
                        if (watchInstalledApps.isEmpty()) {
                            item(key = "no_apps_msg") {
                                Text(
                                    text = "Abre Ultra Remapper en el reloj para sincronizar la lista de aplicaciones instaladas por Bluetooth.",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        } else {
                            items(watchInstalledApps, key = { it.pkg }) { app ->
                                val actKey = "app:${app.pkg}"
                                ActionRadioRow(
                                    label = app.label,
                                    subtitle = app.pkg,
                                    selected = currentAct == actKey
                                ) {
                                    updateWatchMapping(activeGesture, actKey)
                                    pickingGesture = null
                                }
                            }
                        }
                    } else {
                        // Open App Row
                        item(key = "pick_open_app") {
                            val isApp = currentAct.startsWith("app:")
                            ActionRadioRow(
                                label = "Abrir App…",
                                subtitle = if (isApp) {
                                    WatchActionCatalog.getActionLabel(currentAct, watchInstalledApps)
                                } else {
                                    "Elegir entre ${watchInstalledApps.size} apps del reloj ›"
                                },
                                selected = isApp
                            ) {
                                pickingAppSublist = true
                            }
                        }

                        // None Row
                        item(key = "pick_none") {
                            ActionRadioRow(
                                label = "Nada",
                                subtitle = null,
                                selected = currentAct == "none"
                            ) {
                                updateWatchMapping(activeGesture, "none")
                                pickingGesture = null
                            }
                        }

                        // Categories
                        val sections = listOf(
                            "Samsung Health" to WatchActionCatalog.shealthActions,
                            "Funciones Samsung" to WatchActionCatalog.samsungActions,
                            "Desarrollo (ADB)" to WatchActionCatalog.devActions,
                            "Utilidades" to WatchActionCatalog.utilityActions,
                            "Home Assistant" to WatchActionCatalog.homeAssistantActions,
                            "Multimedia y Volumen" to WatchActionCatalog.mediaActions
                        )

                        for ((secTitle, itemsList) in sections) {
                            item(key = "hdr_$secTitle") {
                                Text(
                                    text = secTitle,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                                )
                            }
                            items(itemsList, key = { it.first }) { (actKey, actLabel) ->
                                ActionRadioRow(
                                    label = actLabel,
                                    subtitle = null,
                                    selected = currentAct == actKey
                                ) {
                                    updateWatchMapping(activeGesture, actKey)
                                    pickingGesture = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    active: Boolean,
    activeColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CardBg)
            .border(1.dp, if (active) activeColor.copy(alpha = 0.45f) else CardBorder, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) activeColor else TextMuted)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (active) Color.White else TextMuted,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DiagBanner(
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RedBannerBg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WatchSectionCollapsible(
    iconRes: Int,
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggle() }
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        color = if (subtitle.contains("configurados") || subtitle.contains("activo")) UltraOrange else TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
            CollapseChevron(isExpanded = isExpanded)
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun GestureMappingRow(
    gestureName: String,
    actionLabel: String,
    isConfigured: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SlateButton)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gestureName,
                color = if (enabled) Color.White else TextMuted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = actionLabel,
                color = if (isConfigured) UltraOrange else TextMuted,
                fontSize = 12.sp,
                fontWeight = if (isConfigured) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        ForwardChevron()
    }
}

@Composable
private fun WatchToggleRow(
    title: String,
    subtitle: String,
    subtitleColor: Color = TextMuted,
    checked: Boolean,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SlateButton)
            .clickable(enabled = enabled && !isLoading) { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        if (isLoading) {
            SwitchLoadingSpinner()
        } else {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(20.dp)
                    .clip(CircleShape)
                    .background(if (checked) UltraOrange else Color(0xFF383D47))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun ActionRadioRow(
    label: String,
    subtitle: String?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SlateButton)
            .clickable { onSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = UltraOrange,
                unselectedColor = TextMuted
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = if (selected) UltraOrange else TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CollapseChevron(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF8A93A2)
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        if (isExpanded) {
            drawLine(color, Offset(w * 0.2f, h * 0.65f), Offset(w * 0.5f, h * 0.35f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(w * 0.5f, h * 0.35f), Offset(w * 0.8f, h * 0.65f), strokeWidth, StrokeCap.Round)
        } else {
            drawLine(color, Offset(w * 0.2f, h * 0.35f), Offset(w * 0.5f, h * 0.65f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(w * 0.5f, h * 0.65f), Offset(w * 0.8f, h * 0.35f), strokeWidth, StrokeCap.Round)
        }
    }
}

@Composable
private fun ForwardChevron(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF8A93A2)
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.2.dp.toPx()
        drawLine(color, Offset(w * 0.35f, h * 0.2f), Offset(w * 0.7f, h * 0.5f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(w * 0.7f, h * 0.5f), Offset(w * 0.35f, h * 0.8f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun SwitchLoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SwitchSpinner")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing)
        ),
        label = "SpinnerAngle"
    )
    Canvas(modifier = modifier.size(16.dp)) {
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
