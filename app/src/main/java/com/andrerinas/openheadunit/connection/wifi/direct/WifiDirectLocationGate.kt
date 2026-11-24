package com.andrerinas.openheadunit.connection.wifi.direct

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.App

/**
 * LibreHU fork: whether location stops this app from creating a WiFi Direct group.
 *
 * From Android 10 to 12 the platform creates a group only for a caller holding fine location
 * (WifiPermissionsUtil.checkCanAccessWifiDirect), and the location app-op it notes there is allowed
 * in the background only with location allowed all the time: this app's foreground service is not a
 * location one (AppOpsManager.resolveFirstUnrestrictedUidState), and Native AA starts at boot with no
 * screen of ours visible. Refused, createGroup only answers ERROR ("UID … has no location permission"
 * in the system log), which the ladder retried every minute and a half for hours on a clean install,
 * waking the phone over Bluetooth each time for a network that could not exist. From Android 13
 * NEARBY_WIFI_DEVICES replaces location here, and before 10 nothing asks for it.
 */
object WifiDirectLocationGate {
    /** The permission still missing, or null when location does not stop the group. */
    fun missing(sdk: Int, fineGranted: Boolean, backgroundGranted: Boolean, inForeground: Boolean): String? = when {
        sdk < Build.VERSION_CODES.Q || sdk > Build.VERSION_CODES.S_V2 -> null
        !fineGranted -> Manifest.permission.ACCESS_FINE_LOCATION
        !inForeground && !backgroundGranted -> Manifest.permission.ACCESS_BACKGROUND_LOCATION
        else -> null
    }

    fun missing(context: Context): String? {
        val sdk = Build.VERSION.SDK_INT
        if (sdk < Build.VERSION_CODES.Q || sdk > Build.VERSION_CODES.S_V2) return null
        return missing(
            sdk = sdk,
            fineGranted = granted(context, Manifest.permission.ACCESS_FINE_LOCATION),
            backgroundGranted = granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            inForeground = App.hasStartedActivity,
        )
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
