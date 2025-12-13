package com.borconi.emil.wifilauncherforhur.connectivity

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.borconi.emil.wifilauncherforhur.R
import com.borconi.emil.wifilauncherforhur.receivers.WiFiDirectBroadcastReceiver
import com.borconi.emil.wifilauncherforhur.services.WifiService

class WiFiP2PConnector(
    notificationManager: NotificationManager,
    notification: NotificationCompat.Builder,
    context: Context
) : Connector(notificationManager, notification, context) {
    private var wifip2preceiver: WiFiDirectBroadcastReceiver? = null
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var isWifiP2pStarted = false // Flag to prevent multiple initializations

    private var listeningForWifi = false;
    // Receiver to listen for Wi-Fi state changes
    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)) {
                WifiManager.WIFI_STATE_ENABLED -> {
                    Log.d(TAG, "Wi-Fi has been enabled. Starting P2P setup.")
                    // Unregister this receiver as it's no longer needed
                    context.unregisterReceiver(this)
                    // Now that Wi-Fi is on, initialize Wi-Fi P2P
                    initializeWifiP2p()
                }
                WifiManager.WIFI_STATE_DISABLED -> {
                    Log.d(TAG, "Wi-Fi has been disabled.")
                    channel = null;
                    isWifiP2pStarted = false;
                    start()
                }
            }
        }
    }

    private val  onChannelDisconnected = WifiP2pManager.ChannelListener {
        Log.d(TAG, "Channel disconnected. Starting P2P setup.")
        channel = null
        isWifiP2pStarted = false;
        initializeWifiP2p()
    }

    init {
        start()
    }

    fun start() {
        // Get the WifiManager service to check the current Wi-Fi state
        val wifiManager = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        if (wifiManager?.isWifiEnabled == true) {
            // If Wi-Fi is already on, proceed directly
            Log.d(TAG, "Wi-Fi is already enabled. Initializing P2P.")
            initializeWifiP2p()
        } else {
            if (listeningForWifi)
                return
            listeningForWifi = true
            // If Wi-Fi is off, register a receiver to wait for it to be turned on
            Log.d(TAG, "Wi-Fi is disabled. Registering receiver to listen for state changes.")
            val filter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
            context.registerReceiver(wifiStateReceiver, filter)
            notification.setContentText(context.getString(R.string.wifi_not_enabled_waiting))
            notificationManager.notify(WifiService.NOTIFICATION_ID, notification.build())
        }
    }

    private fun initializeWifiP2p() {
        // Prevent this from running more than once
        if (isWifiP2pStarted) {
            Log.d(TAG, "Wi-Fi P2P already started.")
            return
        }

        manager = context?.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

        if (manager != null && channel == null) { // Also check if channel is not already initialized
            channel = manager!!.initialize(context, context?.mainLooper, onChannelDisconnected)
            if (channel != null) {
                startWifip2pReceiver(manager, channel)
                isWifiP2pStarted = true // Set the flag
            } else {
                Log.e(TAG, "Failed to initialize Wi-Fi P2P channel.")
            }
        } else {
            if (manager == null) Log.e(TAG, "Could not get WifiP2pManager service.")
        }
    }


    private fun startWifip2pReceiver(manager: WifiP2pManager?, channel: WifiP2pManager.Channel?) {
        wifip2preceiver = WiFiDirectBroadcastReceiver(manager, channel, context, this)
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        context?.registerReceiver(wifip2preceiver, intentFilter)
    }

    override fun stop() {
        try {
            if (wifip2preceiver!=null){
                wifip2preceiver!!.stop()
                try {
                    // Unregister all receivers to prevent leaks
                    context.unregisterReceiver(wifip2preceiver)
                }catch (e: IllegalArgumentException){}
                wifip2preceiver = null
            }
            // It's good practice to try/catch unregistering the wifiStateReceiver,
            // as it might not have been registered if Wi-Fi was already on.
            try {
                context.unregisterReceiver(wifiStateReceiver)
            } catch (e: IllegalArgumentException) {
                // Receiver was not registered, which is fine.
            }

            if (manager != null && channel != null) {
                manager!!.cancelConnect(channel, null)
                manager!!.removeGroup(channel, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during stop: ${e.message}")
        } finally {
            // Reset state
            isWifiP2pStarted = false
            listeningForWifi = false;
            manager = null
            channel = null
        }
    }

    companion object {
        private const val TAG = "WiFi-P2P"
    }
}
