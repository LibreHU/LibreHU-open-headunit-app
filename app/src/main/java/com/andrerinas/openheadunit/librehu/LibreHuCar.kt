package com.andrerinas.openheadunit.librehu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.aap.protocol.messages.SensorEvent
import com.andrerinas.openheadunit.aap.protocol.proto.Sensors
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * LibreHU fork: the car as LibreHU-service sees it, sent to the phone as Android Auto sensors, and the steering wheel /
 * front panel keys LibreHU-service forwards while a session runs.
 *
 * Both come as broadcasts that only the apps signed with the LibreHU key can send (the receiver requires the signature
 * permission `<applicationId>.permission.CONFIGURE`, which LibreHU-service holds):
 * - [ACTION_CAR]: any of `handbrake`, `reverse`, `headlight` (booleans), `turn` (`left`, `right`, `none`),
 *   `speed_kmh`, `rpm`, `fuel_pct`, `ambient_c` (doubles). Missing extras keep the last value.
 * - [ACTION_KEY]: `keycode` (an Android KeyEvent code) and `event` (`down`, `up`, or `click` by default);
 *   KEYCODE_VOICE_ASSIST toggles the phone's voice assistant.
 *
 * Each sensor has its own switch (Settings > LibreHU), the parking brake included: switched off it is neither
 * announced nor sent, and the phone relies on its own estimate. Announcing happens when a phone connects (service
 * discovery), so a sensor switched on takes effect at the next connection; switched off, sending stops at once.
 */
object LibreHuCar {
    const val ACTION_CAR = "org.librehu.headunit.action.CAR"
    const val ACTION_KEY = "org.librehu.headunit.action.KEY"

    /** What the switches cover, in the order of the settings screen. */
    enum class Sensor(
        val key: String,
        val type: Sensors.SensorType,
        val default: Boolean,
    ) {
        PARKING_BRAKE("librehu-sensor-parking-brake", Sensors.SensorType.PARKING_BRAKE, true),
        SPEED("librehu-sensor-speed", Sensors.SensorType.CAR_SPEED, true),
        RPM("librehu-sensor-rpm", Sensors.SensorType.RPM, true),
        FUEL("librehu-sensor-fuel", Sensors.SensorType.FUEL_LEVEL, true),
        ENVIRONMENT("librehu-sensor-environment", Sensors.SensorType.ENVIRONMENT, true),
        LIGHTS("librehu-sensor-lights", Sensors.SensorType.LIGHT, true),

        /** Only reverse is known (GPIO): R in reverse, D otherwise, so off by default. */
        GEAR("librehu-sensor-gear", Sensors.SensorType.GEAR, false),
    }

    /** Last values from LibreHU-service; null = never received. */
    data class State(
        val handbrake: Boolean? = null,
        val reverse: Boolean? = null,
        val headlight: Boolean? = null,
        val turn: String? = null,
        val speedKmh: Double? = null,
        val rpm: Double? = null,
        val fuelPct: Double? = null,
        val ambientC: Double? = null,
        val updated: Long = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Broadcast inside this app when the headlights change: the "Car Headlight Signal" night mode and theme follow. */
    const val ACTION_HEADLIGHT_CHANGED = "org.librehu.headunit.internal.HEADLIGHT_CHANGED"

    /** Headlights reported by LibreHU-service, null until then: the car signal of this head unit. */
    val headlight: Boolean? get() = _state.value.headlight

    /** The headlights alone (LibreHU-service's night command), when the car data is not sent. */
    fun setHeadlight(
        context: Context,
        on: Boolean,
    ) {
        val old = _state.value
        if (old.headlight == on) return
        _state.value = old.copy(headlight = on, updated = System.currentTimeMillis())
        headlightChanged(context)
    }

    private fun headlightChanged(context: Context) {
        runCatching { context.sendBroadcast(Intent(ACTION_HEADLIGHT_CHANGED).setPackage(context.packageName)) }
    }

    private var registered = false

    fun enabled(
        settings: Settings,
        sensor: Sensor,
    ): Boolean = settings.librehuSensor(sensor.key, sensor.default)

    /** LibreHU-service installed (it is the only sender). */
    fun available(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(SERVICE_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }

    /** Sensors to announce to the phone (service discovery). */
    fun announced(settings: Settings): List<Sensors.SensorType> = Sensor.entries.filter { enabled(settings, it) }.map { it.type }

    fun start(context: Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        val filter =
            IntentFilter().apply {
                addAction(ACTION_CAR)
                addAction(ACTION_KEY)
            }
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    when (intent.action) {
                        ACTION_CAR -> onCar(app, intent)
                        // Ordered broadcast: LibreHU-service keeps the key's usual action unless it is taken.
                        ACTION_KEY -> {
                            val taken = onKey(app, intent)
                            if (isOrderedBroadcast) resultCode = if (taken) KEY_TAKEN else 0
                        }
                    }
                }
            },
            filter,
            "${app.packageName}.permission.CONFIGURE",
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /** The phone asked for [type] (sensor start request): send the last value at once. */
    fun prime(
        context: Context,
        type: Sensors.SensorType,
    ) {
        val settings = Settings(context)
        val sensor = Sensor.entries.firstOrNull { it.type == type } ?: return
        if (!enabled(settings, sensor)) return
        event(sensor, _state.value)?.let { App.provide(context).commManager.send(it) }
    }

    private fun onCar(
        context: Context,
        intent: Intent,
    ) {
        val old = _state.value

        fun bool(key: String): Boolean? = if (intent.hasExtra(key)) intent.getBooleanExtra(key, false) else null

        fun num(key: String): Double? = if (intent.hasExtra(key)) intent.getDoubleExtra(key, 0.0).takeUnless { it.isNaN() } else null
        val new =
            State(
                handbrake = bool("handbrake") ?: old.handbrake,
                reverse = bool("reverse") ?: old.reverse,
                headlight = bool("headlight") ?: old.headlight,
                turn = intent.getStringExtra("turn") ?: old.turn,
                speedKmh = num("speed_kmh") ?: old.speedKmh,
                rpm = num("rpm") ?: old.rpm,
                fuelPct = num("fuel_pct") ?: old.fuelPct,
                ambientC = num("ambient_c") ?: old.ambientC,
                updated = System.currentTimeMillis(),
            )
        _state.value = new
        if (old.headlight != new.headlight) headlightChanged(context)
        val comm = App.provide(context).commManager
        if (!comm.isConnected) return
        val settings = Settings(context)
        for (sensor in Sensor.entries) {
            if (!enabled(settings, sensor) || !changed(sensor, old, new)) continue
            event(sensor, new)?.let { comm.send(it) }
        }
    }

    private fun changed(
        sensor: Sensor,
        a: State,
        b: State,
    ): Boolean =
        when (sensor) {
            Sensor.PARKING_BRAKE -> a.handbrake != b.handbrake
            // Speed and engine speed are sent with each reading (about once a second), like a car would.
            Sensor.SPEED -> b.speedKmh != null
            Sensor.RPM -> b.rpm != null
            Sensor.FUEL -> a.fuelPct?.roundToInt() != b.fuelPct?.roundToInt()
            Sensor.ENVIRONMENT -> a.ambientC?.roundToInt() != b.ambientC?.roundToInt()
            Sensor.LIGHTS -> a.headlight != b.headlight || a.turn != b.turn
            Sensor.GEAR -> a.reverse != b.reverse
        }

    private fun event(
        sensor: Sensor,
        s: State,
    ): SensorEvent? {
        val batch = Sensors.SensorBatch.newBuilder()
        when (sensor) {
            Sensor.PARKING_BRAKE -> {
                val v = s.handbrake ?: return null
                batch.addParkingbrakeData(Sensors.SensorBatch.ParkingBrakeData.newBuilder().setIsEngaged(v))
            }
            Sensor.SPEED -> {
                val v = s.speedKmh ?: return null
                // speed_e3: metres per second x 1000.
                batch.addSpeedData(Sensors.SensorBatch.SpeedData.newBuilder().setSpeedE3((v / 3.6 * 1000).roundToInt()))
            }
            Sensor.RPM -> {
                val v = s.rpm ?: return null
                batch.addRpm(Sensors.SensorBatch.RpmData.newBuilder().setRpm(v.roundToInt()))
            }
            Sensor.FUEL -> {
                val v = s.fuelPct ?: return null
                batch.addFuelData(
                    Sensors.SensorBatch.FuelData
                        .newBuilder()
                        .setFuellevel(v.roundToInt().coerceIn(0, 100))
                        .setLowfuel(v < LOW_FUEL_PCT),
                )
            }
            Sensor.ENVIRONMENT -> {
                val v = s.ambientC ?: return null
                batch.addEnviormentData(Sensors.SensorBatch.EnvironmentData.newBuilder().setTemperatureE3((v * 1000).roundToInt()))
            }
            Sensor.LIGHTS -> {
                if (s.headlight == null && s.turn == null) return null
                val light = Sensors.SensorBatch.LightData.newBuilder()
                s.headlight?.let {
                    light.setHeadlight(
                        if (it) Sensors.SensorBatch.LightData.HeadLightState.HEAD_LIGHT_STATE_ON else Sensors.SensorBatch.LightData.HeadLightState.HEAD_LIGHT_STATE_OFF,
                    )
                }
                s.turn?.let {
                    light.setTurnIndicator(
                        when (it) {
                            "left" -> Sensors.SensorBatch.LightData.TurnIndicatorState.TURN_INDICATOR_LEFT
                            "right" -> Sensors.SensorBatch.LightData.TurnIndicatorState.TURN_INDICATOR_RIGHT
                            else -> Sensors.SensorBatch.LightData.TurnIndicatorState.TURN_INDICATOR_NONE
                        },
                    )
                }
                batch.addLightData(light)
            }
            Sensor.GEAR -> {
                val v = s.reverse ?: return null
                val gear = if (v) Sensors.SensorBatch.GearData.GEAR.REVERSE else Sensors.SensorBatch.GearData.GEAR.DRIVE
                batch.addGearData(Sensors.SensorBatch.GearData.newBuilder().setGear(gear))
            }
        }
        return SensorEvent(sensor.type.number, batch.build())
    }

    /** True when the key went to the phone. */
    private fun onKey(
        context: Context,
        intent: Intent,
    ): Boolean {
        val settings = Settings(context)
        if (!settings.librehuCarKeys) return false
        val code = intent.getIntExtra("keycode", KeyEvent.KEYCODE_UNKNOWN)
        if (code == KeyEvent.KEYCODE_UNKNOWN) return false
        val comm = App.provide(context).commManager
        if (!comm.isConnected) return false
        val event = intent.getStringExtra("event") ?: "click"
        AppLog.i("LibreHuCar: key $code $event")
        if (code == KeyEvent.KEYCODE_VOICE_ASSIST) {
            if (event != "up") comm.sendToggleVoiceAssistant()
            return true
        }
        when (event) {
            "down" -> comm.sendKey(code, true, null, "librehu")
            "up" -> comm.sendKey(code, false, null, "librehu")
            else -> {
                comm.sendKey(code, true, null, "librehu")
                comm.sendKey(code, false, null, "librehu")
            }
        }
        return true
    }

    private const val LOW_FUEL_PCT = 12.0
    const val SERVICE_PACKAGE = "org.librehu.service"

    /** Sent to LibreHU-service: the phone's battery from Android Auto (`level` %, `remaining_s`, `critical`). */
    const val ACTION_PHONE_BATTERY = "org.librehu.headunit.PHONE_BATTERY"

    fun reportBattery(
        context: Context,
        level: Int,
        remainingS: Int,
        critical: Boolean,
    ) {
        runCatching {
            context.applicationContext.sendBroadcast(
                Intent(ACTION_PHONE_BATTERY)
                    .setPackage(SERVICE_PACKAGE)
                    .putExtra("level", level)
                    .putExtra("remaining_s", remainingS)
                    .putExtra("critical", critical),
            )
        }
    }
    private const val KEY_TAKEN = 1
}
