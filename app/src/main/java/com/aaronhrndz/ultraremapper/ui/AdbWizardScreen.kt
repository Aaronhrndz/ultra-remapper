package com.aaronhrndz.ultraremapper.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.aaronhrndz.ultraremapper.adb.AdbMdnsDiscovery
import com.aaronhrndz.ultraremapper.adb.LocalAdbClient
import kotlin.concurrent.thread

private val SlateCard = Color(0xFF1E222A)
private val UltraOrange = Color(0xFFFF6D00)
private val GreenOk = Color(0xFF4CAF50)

@Composable
fun AdbWizardScreen(
    onPairingSuccess: (LocalAdbClient) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    var currentStep by remember { mutableStateOf(1) }
    var pinText by remember { mutableStateOf("") }
    var detectedPairingPort by remember { mutableStateOf<Int?>(null) }
    var detectedConnectPort by remember { mutableStateOf<Int?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Start local mDNS discovery in background
    DisposableEffect(Unit) {
        val discovery = AdbMdnsDiscovery(context)
        discovery.startDiscovery { isPairing, port ->
            if (isPairing) {
                detectedPairingPort = port
            } else {
                detectedConnectPort = port
            }
        }
        onDispose {
            discovery.stopDiscovery()
        }
    }

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 32.dp, bottom = 40.dp, start = 14.dp, end = 14.dp)
        ) {
            item(key = "wizard_step_header") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(
                        text = "PASO $currentStep DE 3",
                        color = UltraOrange,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            when (currentStep) {
                1 -> {
                    // STEP 1: Activar Depuración Inalámbrica
                    item(key = "s1_info") {
                        Text(
                            text = "1. Conecta el reloj a Wi-Fi.\n2. Activa 'Depuración ADB' y 'Depuración inalámbrica' en Opciones de desarrollador.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }

                    item(key = "s1_open_btn") {
                        Button(
                            onClick = {
                                val intents = listOf(
                                    Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"),
                                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                )
                                for (intent in intents) {
                                    try {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                        break
                                    } catch (_: Exception) {}
                                }
                            },
                            label = { Text("Abrir Opciones Desarrollador", textAlign = TextAlign.Center) },
                            colors = ButtonDefaults.buttonColors(containerColor = UltraOrange),
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec)
                        )
                    }

                    item(key = "s1_next_btn") {
                        Button(
                            onClick = { currentStep = 2 },
                            label = { Text("Ya está activado ›") },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).transformedHeight(this, transformationSpec)
                        )
                    }
                }

                2 -> {
                    // STEP 2: Instrucción gráfica de vinculación
                    item(key = "s2_info") {
                        Text(
                            text = "Dentro de 'Depuración inalámbrica', pulsa en 'Vincular nuevo dispositivo' y mira el código de 6 cifras.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }

                    item(key = "s2_graphic") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF14171E))
                                .border(1.dp, UltraOrange.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = "PIN: [ 1 2 3 4 5 6 ]",
                                color = UltraOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    item(key = "s2_next_btn") {
                        Button(
                            onClick = { currentStep = 3 },
                            label = { Text("Tengo el código ›") },
                            colors = ButtonDefaults.buttonColors(containerColor = UltraOrange),
                            transformation = SurfaceTransformation(transformationSpec),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).transformedHeight(this, transformationSpec)
                        )
                    }
                }

                3 -> {
                    // STEP 3: Entrada del PIN de 6 dígitos + Emparejamiento
                    item(key = "s3_pin_display") {
                        val displayPin = if (pinText.isEmpty()) "______" else pinText.padEnd(6, '_')
                        Text(
                            text = displayPin.map { "$it " }.joinToString(""),
                            color = UltraOrange,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }

                    if (isConnecting) {
                        item(key = "s3_spinner") {
                            Text(
                                text = "Vinculando con el reloj...",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
                            )
                        }
                    } else {
                        // Numeric Keypad rows (1-9, C, 0, OK)
                        val numpad = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("C", "0", "OK")
                        )

                        for (row in numpad) {
                            item(key = "row_${row.joinToString("")}") {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    for (key in row) {
                                        Button(
                                            onClick = {
                                                when (key) {
                                                    "C" -> if (pinText.isNotEmpty()) pinText = pinText.dropLast(1)
                                                    "OK" -> {
                                                        if (pinText.length == 6) {
                                                            isConnecting = true
                                                            statusMessage = "Conectando..."
                                                            val port = detectedConnectPort ?: 5555
                                                            thread {
                                                                val client = LocalAdbClient("127.0.0.1", port)
                                                                val ok = client.connect(context)
                                                                if (ok) {
                                                                    onPairingSuccess(client)
                                                                } else {
                                                                    isConnecting = false
                                                                    statusMessage = "Fallo de conexión. Revisa ADB."
                                                                }
                                                            }
                                                        }
                                                    }
                                                    else -> if (pinText.length < 6) pinText += key
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (key == "OK") GreenOk else if (key == "C") Color(0xFF3A2022) else SlateCard
                                            ),
                                            modifier = Modifier.weight(1f).height(40.dp)
                                        ) {
                                            Text(text = key, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    statusMessage?.let { msg ->
                        item(key = "s3_status") {
                            Text(
                                text = msg,
                                color = if (msg.contains("Fallo")) Color(0xFFF44336) else GreenOk,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
