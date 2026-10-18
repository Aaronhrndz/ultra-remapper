package com.bennybar.ultraremapper

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import kotlin.math.PI
import kotlin.math.sin

object UtilityActions {

    private const val PREF_PARKING = "parking_prefs"
    private const val KEY_HAS_PARKING = "has_parking"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_PREV_TIMEOUT = "prev_screen_timeout"

    @Volatile
    private var adbEasyModeActive = false
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var wifiHighPerfLock: WifiManager.WifiLock? = null

    @Volatile
    private var waterEjectRunning = false

    fun isAdbEasyModeEnabled(): Boolean = adbEasyModeActive

    // ------------------------------------------------------------------------
    // 1. PARKING SAVE & NAVIGATE TO CAR (GOOGLE MAPS WALKING)
    // ------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    fun saveParking(context: Context) {
        val appCtx = context.applicationContext
        val lm = appCtx.getSystemService(LocationManager::class.java)
        if (lm == null) {
            Toast.makeText(appCtx, appCtx.getString(R.string.toast_parking_no_gps), Toast.LENGTH_SHORT).show()
            return
        }

        // Try best recent location first (under 2 minutes old)
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var bestLocation: Location? = null
        val now = System.currentTimeMillis()
        for (p in providers) {
            try {
                val loc = lm.getLastKnownLocation(p)
                if (loc != null && (now - loc.time) < 120_000L) {
                    if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                        bestLocation = loc
                    }
                }
            } catch (_: SecurityException) {
                Toast.makeText(appCtx, appCtx.getString(R.string.toast_parking_perm_needed), Toast.LENGTH_LONG).show()
                return
            } catch (_: Exception) {
            }
        }

        if (bestLocation != null) {
            persistParking(appCtx, bestLocation.latitude, bestLocation.longitude)
            return
        }

        // Otherwise request fresh location fix
        Toast.makeText(appCtx, appCtx.getString(R.string.toast_parking_acquiring), Toast.LENGTH_SHORT).show()
        try {
            val provider = lm.getBestProvider(Criteria().apply { accuracy = Criteria.ACCURACY_FINE }, true)
                ?: LocationManager.GPS_PROVIDER
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lm.removeUpdates(this)
                    persistParking(appCtx, location.latitude, location.longitude)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            // Fallback after 8 seconds if indoors: use any available lastKnownLocation
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    lm.removeUpdates(listener)
                } catch (_: Exception) {}
            }, 8000L)
        } catch (_: SecurityException) {
            Toast.makeText(appCtx, appCtx.getString(R.string.toast_parking_perm_needed), Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(appCtx, appCtx.getString(R.string.toast_parking_no_gps), Toast.LENGTH_SHORT).show()
        }
    }

    private fun persistParking(context: Context, lat: Double, lon: Double) {
        context.getSharedPreferences(PREF_PARKING, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_PARKING, true)
            .putString(KEY_LAT, lat.toString())
            .putString(KEY_LON, lon.toString())
            .apply()
        vibrateDouble(context)
        Toast.makeText(context, context.getString(R.string.toast_parking_saved), Toast.LENGTH_SHORT).show()
    }

    fun navigateToParking(context: Context, backToHome: Boolean) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREF_PARKING, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_PARKING, false)) {
            Toast.makeText(appCtx, appCtx.getString(R.string.toast_parking_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val lat = prefs.getString(KEY_LAT, null) ?: return
        val lon = prefs.getString(KEY_LON, null) ?: return

        // Launch Google Maps Wear OS directly in walking navigation mode
        val navUri = Uri.parse("google.navigation:q=$lat,$lon&mode=w")
        val mapsIntent = Intent(Intent.ACTION_VIEW, navUri)
            .setPackage("com.google.android.apps.maps")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (backToHome) addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME) }
        try {
            appCtx.startActivity(mapsIntent)
            return
        } catch (_: Exception) {
        }

        // Fallback to geo: URI (opens OruxMaps / Google Maps / any installed map app)
        val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Parking)")
        val geoIntent = Intent(Intent.ACTION_VIEW, geoUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appCtx.startActivity(geoIntent)
        } catch (_: Exception) {
            Toast.makeText(appCtx, appCtx.getString(R.string.toast_maps_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------------
    // 2. ADB SIDELOAD / KEEP-AWAKE EASY MODE TOGGLE
    // ------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    fun toggleAdbEasyMode(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREF_PARKING, Context.MODE_PRIVATE)
        val pm = appCtx.getSystemService(PowerManager::class.java)
        val wm = appCtx.applicationContext.getSystemService(WifiManager::class.java)

        if (!adbEasyModeActive) {
            adbEasyModeActive = true
            // 1. Acquire Screen Bright WakeLock so watch stays unlocked (no PIN re-prompt) & awake for 30 min
            try {
                screenWakeLock?.let { if (it.isHeld) it.release() }
                screenWakeLock = pm?.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "UltraRemapper:AdbEasyWakeLock"
                )?.apply {
                    acquire(30 * 60 * 1000L)
                }
            } catch (_: Exception) {
            }

            // 2. Acquire High-Perf WifiLock so wireless ADB never drops
            try {
                wifiHighPerfLock?.let { if (it.isHeld) it.release() }
                wifiHighPerfLock = wm?.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "UltraRemapper:AdbWifiLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            } catch (_: Exception) {
            }

            // 3. If WRITE_SETTINGS / WRITE_SECURE_SETTINGS granted, extend timeout & enable adb_wifi_enabled
            try {
                val curTimeout = Settings.System.getInt(appCtx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 15000)
                if (curTimeout < 1_800_000) {
                    prefs.edit().putInt(KEY_PREV_TIMEOUT, curTimeout).apply()
                }
                Settings.System.putInt(appCtx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 1_800_000)
            } catch (_: Exception) {
            }
            try {
                Settings.Global.putInt(appCtx.contentResolver, "adb_wifi_enabled", 1)
                Settings.Global.putInt(appCtx.contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 7)
                Settings.Global.putInt(appCtx.contentResolver, "cw_disable_wifimediator", 1)
            } catch (_: Exception) {
            }

            vibrateDouble(appCtx)
            Toast.makeText(appCtx, appCtx.getString(R.string.toast_adb_mode_on), Toast.LENGTH_SHORT).show()
        } else {
            adbEasyModeActive = false
            try {
                screenWakeLock?.let { if (it.isHeld) it.release() }
                screenWakeLock = null
            } catch (_: Exception) {
            }
            try {
                wifiHighPerfLock?.let { if (it.isHeld) it.release() }
                wifiHighPerfLock = null
            } catch (_: Exception) {
            }
            try {
                val prev = prefs.getInt(KEY_PREV_TIMEOUT, 15000)
                Settings.System.putInt(appCtx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, prev)
            } catch (_: Exception) {
            }
            try {
                Settings.Global.putInt(appCtx.contentResolver, "cw_disable_wifimediator", 0)
            } catch (_: Exception) {
            }

            vibrateSingle(appCtx)
            Toast.makeText(appCtx, appCtx.getString(R.string.toast_adb_mode_off), Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------------
    // 3. TOUCH SENSITIVITY / GLOVE MODE TOGGLE
    // ------------------------------------------------------------------------

    fun toggleTouchSensitivity(context: Context) {
        val appCtx = context.applicationContext
        val current = try {
            Settings.System.getInt(appCtx.contentResolver, "setting_touch_sensitivity_on", 0)
        } catch (_: Exception) {
            0
        }
        val target = if (current == 1) 0 else 1
        try {
            Settings.System.putInt(appCtx.contentResolver, "setting_touch_sensitivity_on", target)
            if (target == 1) {
                vibrateDouble(appCtx)
                Toast.makeText(appCtx, appCtx.getString(R.string.toast_glove_mode_on), Toast.LENGTH_SHORT).show()
            } else {
                vibrateSingle(appCtx)
                Toast.makeText(appCtx, appCtx.getString(R.string.toast_glove_mode_off), Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(appCtx, appCtx.getString(R.string.toast_write_secure_needed), Toast.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------------------
    // 4. LTE & MOBILE DATA TOGGLE
    // ------------------------------------------------------------------------

    fun toggleLteData(context: Context) {
        val appCtx = context.applicationContext
        val currentCell = try {
            Settings.Global.getInt(appCtx.contentResolver, "cell_on", 0)
        } catch (_: Exception) {
            0
        }
        val targetOn = currentCell == 0
        try {
            Settings.Global.putInt(appCtx.contentResolver, "cell_on", if (targetOn) 1 else 0)
            Settings.Global.putInt(appCtx.contentResolver, "mobile_data", if (targetOn) 1 else 0)
            if (targetOn) {
                vibrateDouble(appCtx)
                Toast.makeText(appCtx, appCtx.getString(R.string.toast_lte_on), Toast.LENGTH_SHORT).show()
            } else {
                vibrateSingle(appCtx)
                Toast.makeText(appCtx, appCtx.getString(R.string.toast_lte_off), Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) {
            Toast.makeText(appCtx, appCtx.getString(R.string.toast_write_secure_needed), Toast.LENGTH_LONG).show()
            try {
                appCtx.startActivity(
                    Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------------
    // 4. WATER EJECT SPEAKER (165 Hz Sine Wave + Vibrate + Water Lock)
    // ------------------------------------------------------------------------

    fun ejectWater(context: Context) {
        val appCtx = context.applicationContext
        if (waterEjectRunning) return
        waterEjectRunning = true

        Toast.makeText(appCtx, appCtx.getString(R.string.toast_water_eject), Toast.LENGTH_SHORT).show()

        Thread {
            val audio = appCtx.getSystemService(AudioManager::class.java)
            val prevVol = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 10
            val maxVol = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            try {
                audio?.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
                val sampleRate = 44100
                val durationSec = 4
                val numSamples = sampleRate * durationSec
                val samples = ShortArray(numSamples)
                val freq = 165.0
                for (i in 0 until numSamples) {
                    // Pulse envelope every 0.5s for stronger water displacement
                    val env = if ((i / (sampleRate / 2)) % 2 == 0) 1.0 else 0.35
                    samples[i] = (sin(2.0 * PI * i * freq / sampleRate) * Short.MAX_VALUE * env).toInt().toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size)
                track.play()

                val vib = appCtx.getSystemService(Vibrator::class.java)
                vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 150, 600, 150, 600, 150, 600), -1))

                Thread.sleep(durationSec * 1000L)
                track.stop()
                track.release()
            } catch (_: Exception) {
            } finally {
                try {
                    audio?.setStreamVolume(AudioManager.STREAM_MUSIC, prevVol, 0)
                } catch (_: Exception) {
                }
                waterEjectRunning = false
            }
        }.start()
    }

    private fun vibrateSingle(context: Context) {
        try {
            context.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    private fun vibrateDouble(context: Context) {
        try {
            context.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 50, 60), -1))
        } catch (_: Exception) {
        }
    }
}
