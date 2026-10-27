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
 * - Decoder low-latency request on (version 3): "vdec-lowlatency" on the head unit's MediaTek decoder, which Android 9
 *   has no official key for; Moonlight sets it by default, and [com.andrerinas.openheadunit.decoder.video.DecoderConfigLadder]
 *   drops it again if the decoder refuses it. LibreHU-service shows the switch on its Android Auto page.
 *
 * - Bluetooth pairing assistance on (version 4): Android Auto's PairingRequest over USB is answered and a new phone
 *   is paired from the head unit, the comparison number sent to Android Auto (AapBluetoothControl).
 *
 * Video (resolution, codec, renderer, density) stays with the onboarding's own detection of the screen and decoders;
 * light / dark and accent come from the launcher ([LibreHuTheme]), else from the headlights ("Car Headlight Signal",
 * fed by LibreHU-service: [LibreHuCar.headlight]); the night mode sent to the phone follows the headlights the same way
 * when LibreHU-service asks for it (Android Auto page, on by default).
 */
object LibreHuDefaults {
    private const val VERSION = 4
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
        // Version 2: the app's own light / dark from the headlights when it does not follow the launcher.
        preset("app-theme") { settings.appTheme = Settings.AppTheme.CAR_SIGNAL }
        // Version 3: the decoder's low-latency mode.
        preset("debug-video-low-latency") { settings.debugVideoLowLatency = true }
        // Version 4: Android Auto's Bluetooth pairing answered, a new phone plugged in over USB paired with this unit
        // by itself (AapBluetoothControl), so unplugging it carries on wirelessly.
        preset("bluetooth-pairing-assist") { settings.bluetoothPairingAssist = true }
        settings.librehuPresetVersion = VERSION
        AppLog.i("LibreHuDefaults: preset $VERSION, set ${applied.ifEmpty { listOf("nothing") }.joinToString()}")
    }
}
