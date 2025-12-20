package com.borconi.emil.wifilauncherforhur.connectivity

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Parcel
import android.util.Log
import androidx.core.app.NotificationCompat
import com.borconi.emil.wifilauncherforhur.R
import com.borconi.emil.wifilauncherforhur.services.WifiService
import kotlinx.coroutines.CoroutineScope
import org.apache.commons.net.util.SubnetUtils
import org.mockito.Mockito
import java.net.NetworkInterface

open class Connector(
    var notificationManager: NotificationManager,
    var notification: NotificationCompat.Builder,
    var context: WifiService
) {
    var network: Network? = null

    open suspend fun start(){
    }

    fun getAAIntent(ip: String?): Intent? {
        if (network == null) {
            if (ip!=null){
                network = fakeNetwork()
            }
        }
        if (network == null){
            val connectivity =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            network = connectivity.activeNetwork
        }

        if (network == null) {
            updateNotification(R.string.no_network)
            return null
        }

        val androidAutoWirelessIntent = Intent()
        var wifiinfo: WifiInfo? = null
        try {
            val cl = Class.forName("android.net.wifi.WifiInfo")
            wifiinfo = cl.newInstance() as WifiInfo
        } catch (e: Exception) {
            Log.d("Connector", "WifiInfo Creation error", e)
        }

        androidAutoWirelessIntent.setClassName(
            PACKAGE_NAME_ANDROID_AUTO_WIRELESS,
            CLASS_NAME_ANDROID_AUTO_WIRELESS
        )
        androidAutoWirelessIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        androidAutoWirelessIntent
            .putExtra(PARAM_HOST_ADDRESS_EXTRA_NAME, ip)
            .putExtra(PARAM_SERVICE_PORT_EXTRA_NAME, 5288)
            .putExtra("wifi_info", wifiinfo)
            .putExtra("PARAM_SERVICE_WIFI_NETWORK", network)
            .putExtra("WIFI_Q_ENABLED", true)

        return androidAutoWirelessIntent
    }


    private fun fakeNetwork() : Network?{
        var fakeNetwork: Network? = null
        try {
            fakeNetwork = Mockito.mock(
                Network::class.java,
                Mockito.withSettings().useConstructor(0)
            )
        } catch (e: Exception) {
        }

        if (fakeNetwork != null) {
            val p = Parcel.obtain()
            fakeNetwork.writeToParcel(p, 0)
            p.setDataPosition(0)
            return Network.CREATOR.createFromParcel(p)
        }
        return null
    }

    val isWiFiConnected: Boolean
        get() {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val nw = connectivityManager.getActiveNetwork()
            if (nw == null) return false
            val actNw = connectivityManager.getNetworkCapabilities(nw)
            return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
        }

    fun startAA(hostIpAddress: String?) {

        if (!WifiService.connected.get()) context.startActivity(
            getAAIntent(
                hostIpAddress
            )
        )
    }

    fun updateNotification(content: Int) {
        if (!WifiService.connected.get()){
            notification.setContentText(context.getString(content))
            notificationManager.notify(WifiService.NOTIFICATION_ID, notification.build())
        }
    }

    fun resetNotification(){
        updateNotification(R.string.service_wifi_looking_text)
    }

    open suspend fun stop() {
    }

    open fun removeIntentReceivers(){
    }

    open suspend fun maintainConnectionAlone(){
    }

    companion object {
        private const val PACKAGE_NAME_ANDROID_AUTO_WIRELESS =
            "com.google.android.projection.gearhead"
        private const val CLASS_NAME_ANDROID_AUTO_WIRELESS =
            "com.google.android.apps.auto.wireless.setup.service.impl.WirelessStartupActivity"

        private const val PARAM_HOST_ADDRESS_EXTRA_NAME = "PARAM_HOST_ADDRESS"
        private const val PARAM_SERVICE_PORT_EXTRA_NAME = "PARAM_SERVICE_PORT"
    }
}
