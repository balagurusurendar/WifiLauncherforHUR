package com.borconi.emil.wifilauncherforhur.connectivity

import android.app.NotificationManager
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.DiscoveryListener
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import com.borconi.emil.wifilauncherforhur.services.WifiService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NDSConnector(
    notificationManager: NotificationManager,
    notification: NotificationCompat.Builder,
    context: WifiService
) : Connector(notificationManager, notification, context){
    private val mNsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var discoveryStarted = false
    private var found = false

    val discoveryListener = object : DiscoveryListener{
        override fun onDiscoveryStarted(serviceType: String?) {
            Log.d(TAG, "Service discovery started")
            context.serviceScope.launch {
                delay(10000)
                stopDiscover()
            }
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            Log.d(TAG, "Discovery stopped: $serviceType")
            context.serviceScope.launch {
                delay(2000)
                if (!found){
                    startDiscover()
                }
            }
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            Log.d(TAG, "Service found: $serviceInfo")
            if (serviceInfo!=null){
                startServiceResolution(serviceInfo)
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            Log.d(TAG, "Service Lost Starting Rediscover")
            found = false
            context.serviceScope.launch {
                delay(2000)
                if (!found){
                    startDiscover()
                }
            }
        }

        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.d(TAG, "Start Discovery Failed Restarting")
            discoveryStarted = false
            context.serviceScope.launch {
                delay(10000)
                if (!found){
                    startDiscover()
                }
            }
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.d(TAG, "Stop Discovery Failed")
        }

    }

    val resolveListener = object : NsdManager.ResolveListener{
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
                this@NDSConnector.network = serviceInfo.network
                context.serviceScope.launch {
                    startAA(hostIpAddress)
                }
                found = true
                stopServiceResolution()
                stopDiscover()
            }
        }
    }

    override suspend fun start() {
        startDiscover()
    }

    private fun startDiscover(){
        if (discoveryStarted || found){
            return
        }
        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        discoveryStarted = true;
    }

    private fun stopDiscover(){
        if (!discoveryStarted){
            return
        }
        mNsdManager.stopServiceDiscovery(discoveryListener)
        discoveryStarted = false
    }

    private fun startServiceResolution(serviceInfo : NsdServiceInfo){
        if (!found){
            mNsdManager.resolveService(serviceInfo,  resolveListener)
        }
    }

    private fun stopServiceResolution() {
        try {
            mNsdManager.stopServiceResolution(resolveListener)
        }catch (_ : Exception){
        }
    }

    override suspend fun stop() {
        found = true
        stopServiceResolution()
        stopDiscover()
    }

    override suspend fun maintainConnectionAlone() {
        stop()
    }

    companion object {
        private const val TAG = "NetworkServiceDiscovery"
        const val SERVICE_TYPE: String = "_aawireless._tcp."
    }
}
