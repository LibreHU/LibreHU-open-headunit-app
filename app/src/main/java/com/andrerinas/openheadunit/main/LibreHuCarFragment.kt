package com.andrerinas.openheadunit.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.librehu.LibreHuCar
import com.andrerinas.openheadunit.main.settings.SettingItem
import com.andrerinas.openheadunit.main.settings.SettingsAdapter
import com.andrerinas.openheadunit.utils.Settings
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * LibreHU fork: what LibreHU-service passes to Android Auto. The keys, each car sensor (the parking brake can stop
 * being sent), the head unit's GPS, and the last values received. Changes apply at once (sensors switched on: at
 * the next connection, see [LibreHuCar]).
 */
class LibreHuCarFragment : Fragment() {
    private lateinit var settings: Settings
    private lateinit var adapter: SettingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_librehu_car, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        settings = App.provide(requireContext()).settings
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            try {
                if (!findNavController().navigateUp()) requireActivity().finish()
            } catch (_: Exception) {
                requireActivity().finish()
            }
        }
        adapter = SettingsAdapter()
        view.findViewById<RecyclerView>(R.id.recycler_view).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@LibreHuCarFragment.adapter
        }
        updateList()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LibreHuCar.state.collect { updateList() }
            }
        }
    }

    private fun sensorRow(
        sensor: LibreHuCar.Sensor,
        name: Int,
        description: Int,
    ) = SettingItem.ToggleSettingEntry(
        stableId = "librehu_${sensor.name}",
        nameResId = name,
        descriptionResId = description,
        isChecked = LibreHuCar.enabled(settings, sensor),
        onCheckedChanged = { on ->
            settings.setLibrehuSensor(sensor.key, on)
            updateList()
        },
    )

    private fun updateList() {
        if (!isAdded) return
        val items = mutableListOf<SettingItem>()

        items.add(SettingItem.CategoryHeader("librehuKeysHeader", R.string.librehu_car_keys_header))
        items.add(
            SettingItem.ToggleSettingEntry(
                stableId = "librehuCarKeys",
                nameResId = R.string.librehu_car_keys,
                descriptionResId = R.string.librehu_car_keys_description,
                isChecked = settings.librehuCarKeys,
                onCheckedChanged = { on ->
                    settings.librehuCarKeys = on
                    updateList()
                },
            ),
        )

        items.add(SettingItem.CategoryHeader("librehuSensorsHeader", R.string.librehu_sensors_header))
        items.add(SettingItem.InfoBanner("librehuSensorsInfo", R.string.librehu_sensors_info))
        items.add(sensorRow(LibreHuCar.Sensor.PARKING_BRAKE, R.string.librehu_sensor_parking_brake, R.string.librehu_sensor_parking_brake_description))
        items.add(sensorRow(LibreHuCar.Sensor.SPEED, R.string.librehu_sensor_speed, R.string.librehu_sensor_speed_description))
        items.add(sensorRow(LibreHuCar.Sensor.RPM, R.string.librehu_sensor_rpm, R.string.librehu_sensor_obd_description))
        items.add(sensorRow(LibreHuCar.Sensor.FUEL, R.string.librehu_sensor_fuel, R.string.librehu_sensor_obd_description))
        items.add(sensorRow(LibreHuCar.Sensor.ENVIRONMENT, R.string.librehu_sensor_environment, R.string.librehu_sensor_obd_description))
        items.add(sensorRow(LibreHuCar.Sensor.LIGHTS, R.string.librehu_sensor_lights, R.string.librehu_sensor_lights_description))
        items.add(sensorRow(LibreHuCar.Sensor.GEAR, R.string.librehu_sensor_gear, R.string.librehu_sensor_gear_description))

        items.add(SettingItem.CategoryHeader("librehuGpsHeader", R.string.librehu_gps_header))
        items.add(
            SettingItem.ToggleSettingEntry(
                stableId = "librehuGps",
                nameResId = R.string.librehu_gps,
                descriptionResId = R.string.librehu_gps_description,
                isChecked = settings.useGpsForNavigation,
                onCheckedChanged = { on ->
                    settings.useGpsForNavigation = on
                    updateList()
                },
            ),
        )

        items.add(SettingItem.CategoryHeader("librehuLiveHeader", R.string.librehu_live_header))
        items.add(SettingItem.InfoBanner("librehuLive", R.string.librehu_live_none, live(LibreHuCar.state.value)))

        adapter.submitList(items)
    }

    /** The last values from LibreHU-service, one line each; null before the first message. */
    private fun live(s: LibreHuCar.State): String? {
        if (s.updated == 0L) return null
        fun yes(v: Boolean?) = getString(if (v == true) R.string.librehu_yes else R.string.librehu_no)
        val dash = "–"
        val lines =
            listOf(
                getString(R.string.librehu_sensor_parking_brake) + " : " + (s.handbrake?.let { yes(it) } ?: dash),
                getString(R.string.librehu_live_reverse) + " : " + (s.reverse?.let { yes(it) } ?: dash),
                getString(R.string.librehu_live_headlights) + " : " + (s.headlight?.let { yes(it) } ?: dash),
                getString(R.string.librehu_sensor_speed) + " : " + (s.speedKmh?.let { String.format(Locale.ROOT, "%.0f km/h", it) } ?: dash),
                getString(R.string.librehu_sensor_rpm) + " : " + (s.rpm?.let { String.format(Locale.ROOT, "%.0f rpm", it) } ?: dash),
                getString(R.string.librehu_sensor_fuel) + " : " + (s.fuelPct?.let { String.format(Locale.ROOT, "%.0f %%", it) } ?: dash),
                getString(R.string.librehu_sensor_environment) + " : " + (s.ambientC?.let { String.format(Locale.ROOT, "%.0f °C", it) } ?: dash),
            )
        return lines.joinToString("\n")
    }
}
