package com.andrerinas.openheadunit.librehu

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings

/**
 * LibreHU fork: the car as LibreHU-service describes it (`content://org.librehu.service.vehicle/info`, signature
 * permission `org.librehu.permission.VEHICLE`, held by the apps signed with the LibreHU key) goes to Open Headunit's
 * vehicle settings, at start and on every change: display name (the car's name), make, model, year, driver's side,
 * and the head unit's Bluetooth address, which Android hides from apps. Empty values leave Open Headunit's own.
 */
object LibreHuVehicleSync {
    private val URI: Uri = Uri.parse("content://org.librehu.service.vehicle/info")
    private var observing = false

    fun start(context: Context) {
        val app = context.applicationContext
        if (app.packageManager.resolveContentProvider(URI.authority!!, 0) == null) return
        Thread({ sync(app) }, "librehu-vehicle").start()
        if (observing) return
        observing = true
        runCatching {
            app.contentResolver.registerContentObserver(
                URI,
                false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        Thread({ sync(app) }, "librehu-vehicle").start()
                    }
                },
            )
        }.onFailure { AppLog.w("LibreHuVehicleSync: observer: ${it.message}") }
    }

    fun sync(context: Context) {
        try {
            context.contentResolver.query(URI, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return

                fun col(name: String): String = c.getColumnIndex(name).takeIf { it >= 0 }?.let { c.getString(it) }.orEmpty().trim()
                val settings = Settings(context)
                col("name").takeIf { it.isNotEmpty() }?.let { settings.vehicleDisplayName = it }
                col("make").takeIf { it.isNotEmpty() }?.let { settings.vehicleMake = it }
                col("model").takeIf { it.isNotEmpty() }?.let { settings.vehicleModel = it }
                col("year").takeIf { it.isNotEmpty() }?.let { settings.vehicleYear = it }
                col("driver_side").takeIf { it.isNotEmpty() }?.let { settings.rightHandDrive = it == "right" }
                col("bt_mac").takeIf { Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}").matches(it) }?.let { settings.bluetoothAddress = it }
                AppLog.i("LibreHuVehicleSync: vehicle settings from LibreHU-service")
            }
        } catch (e: Exception) {
            AppLog.w("LibreHuVehicleSync: ${e.message}")
        }
    }
}
