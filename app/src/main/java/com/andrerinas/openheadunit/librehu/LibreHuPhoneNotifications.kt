package com.andrerinas.openheadunit.librehu

import android.content.Context
import android.content.Intent
import com.andrerinas.openheadunit.aap.AapMessage
import com.andrerinas.openheadunit.aap.protocol.Channel
import com.andrerinas.openheadunit.aap.protocol.proto.Control
import com.andrerinas.openheadunit.utils.AppLog

/**
 * LibreHU fork (experimental): the phone's notification service (GenericNotification) and phone status service,
 * announced only when "Phone notifications to LibreHU" is on ([com.andrerinas.openheadunit.utils.Settings.librehuPhoneNotifications]).
 *
 * Their messages are not documented (names only, from a reverse-engineered firmware): each one is logged by its shape
 * (type, size, protobuf field numbers with their numbers or lengths, never the text), so the format can be worked out
 * from a real phone, and told to LibreHU-service for the front LED blink: every notification message, and a phone
 * status that starts showing an incoming call. `org.librehu.action.PROJECTION_NOTIFICATION` (`source` = android_auto,
 * `kind` = message / call), taken by the service from holders of the VEHICLE signature permission only; nothing of
 * the notification goes with it.
 */
internal class LibreHuPhoneNotifications(context: Context) {
    private val app = context.applicationContext

    /** The last phone status showed an incoming call. */
    private var incoming = false

    /** Messages logged in this session: the shape is enough a few times, not for every message. */
    private var logged = 0

    fun handles(channel: Int) = channel == Channel.ID_NOTI || channel == Channel.ID_PHONE

    fun process(message: AapMessage) {
        val payload = message.data.copyOfRange(message.dataOffset, message.size.coerceAtMost(message.data.size))
        val name = if (message.channel == Channel.ID_NOTI) "notification" else "phone status"
        if (logged < MAX_LOGGED) {
            logged++
            AppLog.i("LibreHU %s: type=0x%04x size=%d fields=%s", name, message.type, payload.size, shape(payload))
        }
        when (message.channel) {
            Channel.ID_NOTI -> report("message")
            Channel.ID_PHONE -> {
                val now =
                    runCatching {
                        Control.Service.PhoneStatusService.parseFrom(payload).callsList
                            .any { it.state == Control.Service.PhoneStatus_State.Incoming }
                    }.getOrDefault(false)
                if (now && !incoming) report("call")
                incoming = now
            }
        }
    }

    private fun report(kind: String) {
        runCatching {
            app.sendBroadcast(
                Intent(ACTION)
                    .setPackage(LibreHuCar.SERVICE_PACKAGE)
                    .putExtra("source", "android_auto")
                    .putExtra("kind", kind),
            )
        }.onFailure { AppLog.e("LibreHU phone notification: ${it.message}") }
    }

    companion object {
        const val ACTION = "org.librehu.action.PROJECTION_NOTIFICATION"
        private const val MAX_LOGGED = 40

        /**
         * The protobuf fields of [bytes], without their text: `1:v=4` (varint), `2:len=12` (bytes, string or
         * message), `3:f64`, `4:f32`; `?` where it stops making sense as protobuf.
         */
        fun shape(bytes: ByteArray): String {
            val out = StringBuilder()
            var i = 0
            fun varint(): Long? {
                var shift = 0
                var result = 0L
                while (i < bytes.size && shift < 64) {
                    val b = bytes[i++].toInt() and 0xff
                    result = result or ((b and 0x7f).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
                return null
            }
            var fields = 0
            while (i < bytes.size && fields < 32) {
                val key = varint() ?: return out.append(" ?").toString().trim()
                val field = key ushr 3
                if (field == 0L) return out.append(" ?").toString().trim()
                when ((key and 7).toInt()) {
                    0 -> out.append(" $field:v=${varint() ?: return out.append(" ?").toString().trim()}")
                    1 -> { out.append(" $field:f64"); i += 8 }
                    2 -> {
                        val len = varint() ?: return out.append(" ?").toString().trim()
                        if (len < 0 || len > bytes.size - i) return out.append(" $field:len=$len?").toString().trim()
                        out.append(" $field:len=$len")
                        i += len.toInt()
                    }
                    5 -> { out.append(" $field:f32"); i += 4 }
                    else -> return out.append(" ?").toString().trim()
                }
                fields++
            }
            return out.toString().trim().ifEmpty { "-" }
        }
    }
}
