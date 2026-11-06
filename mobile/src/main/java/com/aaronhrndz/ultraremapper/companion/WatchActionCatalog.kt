package com.aaronhrndz.ultraremapper.companion

/**
 * Shared definitions of watch gestures and actions so the Mobile Companion
 * can display and configure the exact same options as the watch app.
 */
object WatchActionCatalog {

    val topGestures = listOf(
        "top_double" to "Doble pulsación",
        "top_long" to "Mantener pulsado"
    )

    val actionGestures = listOf(
        "single" to "Una pulsación",
        "double" to "Doble pulsación",
        "long" to "Pulsación larga"
    )

    val bottomGestures = listOf(
        "bottom_single" to "Una pulsación",
        "bottom_double_tap" to "Doble pulsación",
        "bottom_triple_tap" to "Triple pulsación",
        "bottom_long" to "Pulsación larga"
    )

    val gestureList = listOf(
        "toctoc" to "Toc toc (Gesto de muñeca)"
    )

    val allGestures = topGestures + actionGestures + bottomGestures + gestureList

    val shealthActions = listOf(
        "shealth_workout" to "Mis entrenamientos",
        "shealth_route" to "Modo Ruta (Samsung Health)"
    )

    val samsungActions = listOf(
        "recents" to "Abrir multitarea (Recientes)",
        "stopwatch" to "Cronómetro",
        "water_lock" to "Bloqueo de Agua",
        "assistant" to "Asistente Digital"
    )

    val devActions = listOf(
        "wireless_debugging" to "Depuración Inalámbrica (ADB)",
        "adb_mode" to "Mantener Pantalla Activa (Reloj despierto)"
    )

    val utilityActions = listOf(
        "flashlight" to "Linterna",
        "red_flashlight" to "Linterna RGBW",
        "dnd_toggle" to "No Molestar",
        "save_parking" to "Guardar Parking",
        "nav_parking" to "Volver al Coche (Maps a pie)",
        "water_eject" to "Expulsar Agua (Altavoz)",
        "lte_toggle" to "LTE y Datos Móviles"
    )

    val homeAssistantActions = listOf(
        "ha_assist" to "Home Assistant Assist (Voz)"
    )

    val mediaActions = listOf(
        "play_pause" to "Reproducir / Pausa",
        "next" to "Pista Siguiente",
        "previous" to "Pista Anterior",
        "vol_up" to "Subir Volumen",
        "vol_down" to "Bajar Volumen",
        "open_media_player" to "Abrir Controlador Multimedia"
    )

    private val allStaticActions = (shealthActions + samsungActions + devActions + utilityActions + homeAssistantActions + mediaActions).toMap()

    fun getActionLabel(action: String, installedApps: List<WatchAppEntry>): String {
        if (action == "none" || action.isBlank()) return "Nada"
        if (action.startsWith("app:")) {
            val pkg = action.removePrefix("app:")
            return installedApps.firstOrNull { it.pkg == pkg }?.label ?: pkg
        }
        return allStaticActions[action] ?: action
    }

    fun getGestureTitle(gesture: String): String {
        return allGestures.firstOrNull { it.first == gesture }?.second ?: gesture
    }
}
