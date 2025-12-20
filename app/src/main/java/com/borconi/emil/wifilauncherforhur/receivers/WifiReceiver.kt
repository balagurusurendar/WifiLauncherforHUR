package com.borconi.emil.wifilauncherforhur.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.borconi.emil.wifilauncherforhur.services.WifiService

/**
 * BroadcastReceiver to handle our explicit intents from notification actions
 */
class WifiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) return

        when (intent.action) {
            ACTION_WIFI_LAUNCHER_EXIT -> {
                WifiService.mustexit = true
                val startWifiServiceIntent = Intent(context, WifiService::class.java)
                context.stopService(startWifiServiceIntent)
            }
        }
    }

    companion object {
        const val ACTION_WIFI_LAUNCHER_EXIT: String =
            "com.borconi.emil.wifilauncherforhur.action.EXIT"
    }
}
