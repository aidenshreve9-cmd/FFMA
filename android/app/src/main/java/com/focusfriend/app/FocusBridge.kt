package com.focusfriend.app

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * The page's window.FFAndroid. Methods run on a WebView background thread.
 * Only the app's own bundled page is loaded, so only it can call these.
 */
class FocusBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun dndState(): String = if (DndController.hasAccess(activity)) "granted" else "denied"

    @JavascriptInterface
    fun openDndAccess() {
        activity.runOnUiThread { activity.openDndAccessSettings() }
    }

    /** policyJson is the page's interruptionPolicy(); endAt is the session's end time in ms. */
    @JavascriptInterface
    fun begin(policyJson: String, endAt: Double): Boolean {
        val alarms = try { JSONObject(policyJson).optBoolean("alarms", true) } catch (e: Exception) { true }
        return DndController.begin(activity, alarms, endAt.toLong())
    }

    @JavascriptInterface
    fun end() {
        DndController.restore(activity)
    }

    @JavascriptInterface
    fun keepScreenOn(on: Boolean) {
        activity.runOnUiThread { activity.setKeepScreenOn(on) }
    }
}
