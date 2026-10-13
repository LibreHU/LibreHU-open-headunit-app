package com.andrerinas.openheadunit.librehu

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Surface
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.aap.protocol.messages.TouchEvent
import com.andrerinas.openheadunit.aap.protocol.messages.VideoFocusEvent
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.HeadUnitScreenConfig
import com.andrerinas.openheadunit.utils.Settings
import kotlin.math.roundToInt

/**
 * LibreHU fork: Android Auto's picture in a card of LibreHU Launcher (experimental).
 *
 * The launcher binds to this service (guarded by the signature permission `<applicationId>.permission.CONFIGURE`)
 * and sends, through a [Messenger]:
 * - [MSG_ATTACH] with its card's [Surface] (`surface` in the data bundle): the decoder renders there, scaled to the
 *   card, and video focus is taken back so the phone sends a key frame;
 * - [MSG_DETACH]: the card is gone; the decoder lets go of it, and video focus is released unless the projection
 *   screen took the picture back in the meantime;
 * - [MSG_TOUCH] with `action` (MotionEvent action), `x` and `y` (0..1 of the card): a one-finger touch.
 *
 * The projection screen keeps priority: when it comes back to the front its own surface replaces the card's, and a
 * late detach of the card then changes nothing. Off in Settings > Car and keys (LibreHU), the service refuses.
 */
class LibreHuVideoService : Service() {
    private var cardSurface: Surface? = null

    private val handler =
        object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    MSG_ATTACH -> attach(msg.data)
                    MSG_DETACH -> detach("card_detached")
                    MSG_TOUCH -> touch(msg.data)
                }
            }
        }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent): IBinder? {
        if (!Settings(this).librehuVideoCard) {
            AppLog.i("LibreHuVideoService: launcher card refused (switched off)")
            return null
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        detach("card_unbound")
        return false
    }

    override fun onDestroy() {
        detach("service_destroyed")
        super.onDestroy()
    }

    private fun attach(data: Bundle) {
        data.classLoader = Surface::class.java.classLoader
        @Suppress("DEPRECATION")
        val surface = data.getParcelable<Surface>("surface") ?: return
        if (!surface.isValid) return
        val component = App.provide(this)
        AppLog.i("LibreHuVideoService: picture to the launcher card")
        cardSurface = surface
        component.videoDecoder.setSurface(surface)
        if (component.commManager.isConnected) component.commManager.retakeVideoFocusForKeyframe()
    }

    private fun detach(reason: String) {
        val surface = cardSurface ?: return
        cardSurface = null
        val component = App.provide(this)
        // False when the projection screen already took the picture back: nothing to release then.
        if (component.videoDecoder.detachSurfaceIfCurrent(surface, "librehu_$reason")) {
            AppLog.i("LibreHuVideoService: card gone ($reason), video focus released")
            if (component.commManager.isConnected) component.commManager.send(VideoFocusEvent(gain = false, unsolicited = false))
        }
        runCatching { surface.release() }
    }

    private fun touch(data: Bundle) {
        val surface = cardSurface ?: return
        val component = App.provide(this)
        if (!component.videoDecoder.isCurrentSurface(surface) || !component.commManager.isConnected) return
        val action =
            when (data.getInt("action", -1)) {
                MotionEvent.ACTION_DOWN -> com.andrerinas.openheadunit.aap.protocol.proto.Input.TouchEvent.PointerAction.TOUCH_ACTION_DOWN
                MotionEvent.ACTION_MOVE -> com.andrerinas.openheadunit.aap.protocol.proto.Input.TouchEvent.PointerAction.TOUCH_ACTION_MOVE
                MotionEvent.ACTION_UP -> com.andrerinas.openheadunit.aap.protocol.proto.Input.TouchEvent.PointerAction.TOUCH_ACTION_UP
                MotionEvent.ACTION_CANCEL -> com.andrerinas.openheadunit.aap.protocol.proto.Input.TouchEvent.PointerAction.TOUCH_ACTION_CANCEL
                else -> return
            }
        val w = HeadUnitScreenConfig.getNegotiatedWidth()
        val h = HeadUnitScreenConfig.getNegotiatedHeight()
        if (w <= 0 || h <= 0) return
        // The card shows the whole video frame stretched (MediaCodec scale to fit).
        val x = (data.getFloat("x").coerceIn(0f, 1f) * w).roundToInt()
        val y = (data.getFloat("y").coerceIn(0f, 1f) * h).roundToInt()
        component.commManager.send(TouchEvent(SystemClock.elapsedRealtime(), action, 0, listOf(Triple(0, x, y))))
    }

    companion object {
        const val MSG_ATTACH = 1
        const val MSG_DETACH = 2
        const val MSG_TOUCH = 3
    }
}
