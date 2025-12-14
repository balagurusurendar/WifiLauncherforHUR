package com.borconi.emil.wifilauncherforhur.connectivity

import android.app.NotificationManager
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.DiscoveryListener
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class NDSConnector(
    notificationManager: NotificationManager,
    notification: NotificationCompat.Builder,
    context: Context
) : Connector(notificationManager, notification, context), DiscoveryListener, NsdManager.ResolveListener {
    private val mNsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var discoveryStarted = false
    private var found = false

    init {
        startDiscover()
    }

    private fun startDiscover(){
        if (discoveryStarted || found){
            return
        }
        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, this)
        discoveryStarted = true;
    }

    private fun stopDiscover(){
        if (!discoveryStarted){
            return
        }
        mNsdManager.stopServiceDiscovery(this)
        discoveryStarted = false
    }

    override fun stop() {
        found = true
        stopDiscover()
    }

    override fun onDiscoveryStarted(serviceType: String?) {
        Log.d(TAG, "Service discovery started")

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(this::stopDiscover, 10000)
    }

    override fun onDiscoveryStopped(serviceType: String?) {
        Log.d(TAG, "Discovery stopped: $serviceType")
        if (!found){
            startDiscover()
        }
    }

    override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
        Log.d(TAG, "Service found: $serviceInfo")
        if (!found){
            mNsdManager.resolveService(serviceInfo, this);
        }
    }

    override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
        Log.d(TAG, "Service Lost Starting Rediscover")
        found = false
        startDiscover()
    }

    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
        Log.d(TAG, "Start Discovery Failed Restarting")
        discoveryStarted = false
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(this::startDiscover, 10000)
    }

    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
        Log.d(TAG, "Stop Discovery Failed")
    }

    override fun onResolveFailed(
        serviceInfo: NsdServiceInfo?,
        errorCode: Int
    ) {
        Log.d(TAG, "Resolve Failed with error code $errorCode")
    }

    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
        Log.d(TAG, "Service resolved: $serviceInfo")
        if (serviceInfo!=null){
            val hostIpAddress = serviceInfo.hostAddresses[0].hostAddress
            Log.d(TAG, "Host IP address: $hostIpAddress")
            this.network = serviceInfo.network
            startAA(hostIpAddress)
            found = true
            stopDiscover()
        }
    }

    companion object {
        private const val TAG = "NetworkServiceDiscovery"
        const val SERVICE_TYPE: String = "_aawireless._tcp."
    }
}
