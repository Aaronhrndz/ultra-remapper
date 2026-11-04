package com.aaronhrndz.ultraremapper.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.aaronhrndz.ultraremapper.HapticFeedbackHelper
import com.aaronhrndz.ultraremapper.adb.AdbMdnsDiscovery
import com.aaronhrndz.ultraremapper.adb.LocalAdbClient
import kotlin.concurrent.thread

private val SlateCard = Color(0xFF1F232A)
private val UltraOrange = Color(0xFFFF5500)
private val GreenOk = Color(0xFF4CAF50)
private val RedAlert = Color(0xFFF44336)

private fun isWirelessAdbEnabledOnSystem(context: Context): Boolean {
    return try {
        val wifiAdb = Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0)
        val usbAdb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
        wifiAdb == 1 || usbAdb == 1
    } catch (_: Exception) {
        false
    }
}

private fun openWirelessDebuggingSettings(context: Context) {
    val devIntents = listOf(
        Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"),
        Intent().setClassName(
            "com.google.android.apps.wearable.settings",
            "com.samsung.android.clockwork.settings.developer.wirelessadb.WirelessDebuggingActivity"
        ),
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent().setClassName(
            "com.google.android.apps.wearable.settings",
            "com.samsung.android.clockwork.settings.developer.StDevOptionsActivity"
        ),
        Intent("com.google.android.clockwork.settings.DEVELOPER_OPTIONS")
    )
    for (intent in devIntents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }
}

@Composable
fun AdbWizardScreen(
    onPairingSuccess: (LocalAdbClient) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    var currentStep by remember { mutableStateOf(1) }
    var isAdbActive by remember { mutableStateOf(isWirelessAdbEnabledOnSystem(context)) }
    var pinText by remember { mutableStateOf("") }
    var detectedPairingPort by remember { mutableStateOf<Int?>(null) }
    var detectedConnectPort by remember { mutableStateOf<Int?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Re-check system ADB status when returning from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAdbActive = isWirelessAdbEnabledOnSystem(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ONLY start mDNS discovery when entering Step 2 (so ZERO permission popup on Step 1!)
    DisposableEffect(currentStep) {
        var discovery: AdbMdnsDiscovery? = null
        if (currentStep == 2) {
            discovery = AdbMdnsDiscovery(context)
            discovery.startDiscovery { isPairing, port ->
                if (isPairing) {
                    detectedPairingPort = port
                } else {
                    detectedConnectPort = port
                }
            }
        }
        onDispose {
            discovery?.stopDiscovery()
        }
    }

    val connectAction: (String) -> Unit = { pin ->
        keyboardController?.hide()
        isConnecting = true
        statusMessage = "Conectando..."
        val pairingPort = detectedPairingPort
        val connectPort = detectedConnectPort ?: LocalAdbClient.getLastPort(context)

        thread {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val client = LocalAdbClient("127.0.0.1", connectPort)
            var pairedOk = true

            // If PIN provided and pairing port detected, pair first
            if (pin.length == 6) {
                if (pairingPort != null) {
                    mainHandler.post { statusMessage = "Emparejando dispositivo..." }
                    pairedOk = client.pair(context, pairingPort, pin)
                }
            }

            if (!pairedOk) {
                mainHandler.post {
                    isConnecting = false
                    statusMessage = "Fallo al emparejar. Código incorrecto o expirado."
                }
                return@thread
            }

            mainHandler.post { statusMessage = "Estableciendo conexión ADB..." }
            val connected = client.connect(context)
            if (connected) {
                mainHandler.post {
                    HapticFeedbackHelper.playButtonHaptic(context)
                    onPairingSuccess(client)
                }
            } else {
                mainHandler.post {
                    isConnecting = false
                    statusMessage = "Fallo al conectar (puerto $connectPort). Abre Depuración inalámbrica para verificar puerto."
                }
            }
        }
    }

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 30.dp, bottom = 44.dp, start = 12.dp, end = 12.dp)
        ) {
            item(key = "wizard_step_header") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        text = if (currentStep == 1) "PASO 1 DE 2" else "PASO 2 DE 2",
                        color = UltraOrange,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (currentStep == 1) {
                // STEP 1: Pure Instructions + Direct Wireless Debugging Button + Gated Next
                item(key = "s1_instructions") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF181B22))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "1. Conecta el reloj al Wi-Fi.",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "2. Abre Depuración inalámbrica y actívala.",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "3. Toca 'Vincular dispositivo nuevo', apunta el código de emparejamiento de 6 dígitos y vuelve aquí.",
                            color = Color(0xFFD5DCE6),
                            fontSize = 11.sp
                        )
                    }
                }

                item(key = "s1_open_btn") {
                    Button(
                        onClick = {
                            HapticFeedbackHelper.playUiTap(context)
                            openWirelessDebuggingSettings(context)
                        },
                        label = {
                            Text(
                                text = "Abrir Depuración Inalámbrica",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UltraOrange,
                            contentColor = Color.White
                        ),
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .transformedHeight(this, transformationSpec)
                    )
                }

                // Direct connect option if already paired earlier
                item(key = "s1_direct_connect_btn") {
                    if (LocalAdbClient.isPaired(context)) {
                        Button(
                            enabled = !isConnecting,
                            onClick = {
                                currentStep = 2
                                connectAction("")
                            },
                            label = {
                                Text(
                                    text = "Conectar directo (ya vinculado)",
                                    color = Color(0xFF64B5F6),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E2838),
                                contentColor = Color.White
                            ),
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .transformedHeight(this, transformationSpec)
                        )
                    }
                }

                item(key = "s1_next_btn") {
                    Button(
                        enabled = isAdbActive,
                        onClick = {
                            if (isAdbActive) {
                                HapticFeedbackHelper.playUiTap(context)
                                currentStep = 2
                            }
                        },
                        label = {
                            Text(
                                text = if (isAdbActive) "Ya tengo el código ›" else "Activa Depuración primero",
                                color = if (isAdbActive) Color.White else Color(0xFF7A8392),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SlateCard,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF16191F),
                            disabledContentColor = Color(0xFF6A717E)
                        ),
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .transformedHeight(this, transformationSpec)
                    )
                }
            } else {
                // STEP 2: Native Number TextField + Connect
                item(key = "s2_open_btn") {
                    Button(
                        onClick = {
                            HapticFeedbackHelper.playUiTap(context)
                            openWirelessDebuggingSettings(context)
                        },
                        label = {
                            Text(
                                text = "Abrir Depuración Inalámbrica",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UltraOrange,
                            contentColor = Color.White
                        ),
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .transformedHeight(this, transformationSpec)
                    )
                }

                item(key = "s2_desc") {
                    val portInfo = when {
                        detectedPairingPort != null -> "Puerto detectado: $detectedPairingPort"
                        detectedConnectPort != null -> "Puerto ADB: $detectedConnectPort"
                        else -> "Introduce el código de 6 dígitos"
                    }
                    Text(
                        text = portInfo,
                        color = if (detectedPairingPort != null || detectedConnectPort != null) GreenOk else Color(0xFF9EA7B8),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )
                }

                // Native Number TextField
                item(key = "s2_input_box") {
                    BasicTextField(
                        value = pinText,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { it.isDigit() }.take(6)
                            pinText = filtered
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                if (pinText.length == 6) {
                                    connectAction(pinText)
                                }
                            }
                        ),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(UltraOrange),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        decorationBox = { innerTextField ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF181B22))
                                    .border(
                                        width = 1.5.dp,
                                        color = if (pinText.isNotEmpty()) UltraOrange else Color(0xFF383D47),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 12.dp)
                            ) {
                                if (pinText.isEmpty()) {
                                    Text(
                                        text = "Toca para escribir PIN",
                                        color = Color(0xFF7A8392),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                item(key = "s2_connect_btn") {
                    Button(
                        enabled = !isConnecting && pinText.length == 6,
                        onClick = {
                            connectAction(pinText)
                        },
                        label = {
                            Text(
                                text = if (isConnecting) "Conectando..." else "Vincular y Conectar",
                                color = if (pinText.length == 6 && !isConnecting) Color.White else Color(0xFF7A8392),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UltraOrange,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF16191F),
                            disabledContentColor = Color(0xFF6A717E)
                        ),
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .transformedHeight(this, transformationSpec)
                    )
                }

                // Direct connect option if already paired earlier
                item(key = "s2_direct_connect_btn") {
                    Button(
                        enabled = !isConnecting,
                        onClick = {
                            connectAction("")
                        },
                        label = {
                            Text(
                                text = "Conectar sin código (ya vinculado)",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
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

                statusMessage?.let { msg ->
                    item(key = "s2_status") {
                        Text(
                            text = msg,
                            color = if (msg.contains("Fallo")) RedAlert else GreenOk,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
