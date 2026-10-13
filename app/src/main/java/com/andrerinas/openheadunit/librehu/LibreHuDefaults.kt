package com.andrerinas.openheadunit.librehu

import android.content.Context
import android.net.Uri
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings

/**
 * LibreHU fork: settings for a head unit running LibreHU (launcher or LibreHU-service installed), applied once per
 * [VERSION] and only to the settings nobody chose yet, so a user's choice or an imported backup always stays.
 *
 * - USB: start on plug-in and connect when a single phone is there (the head unit is the only screen, there is
 *   nothing else to pick).
 * - Fixed landscape screen.
 * - Track, artist and cover from Android Auto in the system media session: the launcher's player and the steering
 *   wheel keys see what plays.
 *
 * Video (resolution, codec, renderer, density) stays with the onboarding's own detection of the screen and decoders;
 * light / dark and accent come from the launcher ([LibreHuTheme]); the night mode sent to the phone can come from
 * the headlights (LibreHU-service, Android Auto page).
 */
object LibreHuDefaults {
    private const val VERSION = 1
    private val PROVIDERS = listOf(Uri.parse("content://org.librehu.launcher.theme"), Uri.parse("content://org.librehu.service.vehicle"))

    fun apply(context: Context) {
        val settings = Settings(context)
        if (settings.librehuPresetVersion >= VERSION) return
        val pm = context.packageManager
        if (PROVIDERS.none { pm.resolveContentProvider(it.authority!!, 0) != null }) return
        val applied = mutableListOf<String>()

        fun preset(
            key: String,
            set: () -> Unit,
        ) {
            if (settings.isSet(key)) return
            set()
            applied += key
        }
        preset("auto-start-on-usb") { settings.autoStartOnUsb = true }
        preset("auto-connect-single-usb") { settings.autoConnectSingleUsbDevice = true }
        preset(Settings.KEY_SCREEN_ORIENTATION) { settings.screenOrientation = Settings.ScreenOrientation.LANDSCAPE }
        preset(Settings.KEY_SYNC_MEDIA_SESSION_AA_METADATA) { settings.syncMediaSessionWithAaMetadata = true }
        settings.librehuPresetVersion = VERSION
        AppLog.i("LibreHuDefaults: preset $VERSION, set ${applied.ifEmpty { listOf("nothing") }.joinToString()}")
    }
}
