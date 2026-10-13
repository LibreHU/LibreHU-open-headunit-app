package com.andrerinas.openheadunit.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.andrerinas.openheadunit.utils.AppLog
import org.json.JSONObject

/**
 * LibreHU fork: [AutomationReceiver] for the apps signed with the LibreHU key (LibreHU-service, LibreHU Launcher).
 * The manifest guards it with the signature permission `<applicationId>.permission.CONFIGURE`, so the configuring
 * commands (ACTION_SET_SETTINGS, ACTION_GET_SETTINGS…) are accepted without the user's "Allow external configuration"
 * switch, which stays the gate for every other app.
 */
class LibreHuAutomationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        AppLog.i("LibreHuAutomationReceiver: $action")

        val effects = AutomationCommandPolicy.effectsFor(
            action,
            IntentExtras(intent),
            AutomationCommandPolicy.State(externalConfigAllowed = true)
        )

        val reply = JSONObject().put("action", action ?: "")
        val ok = try {
            AutomationEffectRunner.run(context, effects, reply)
        } catch (e: Exception) {
            reply.put("error", e.message ?: e.javaClass.simpleName)
            AppLog.e("LibreHuAutomationReceiver: $action failed: ${e.message}", e)
            false
        }
        reply.put("ok", ok)
        if (isOrderedBroadcast) {
            resultCode = if (ok) 0 else 1
            resultData = reply.toString()
        }
    }
}
