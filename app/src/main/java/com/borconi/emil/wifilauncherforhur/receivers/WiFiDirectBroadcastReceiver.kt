package com.borconi.emil.wifilauncherforhur.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import com.borconi.emil.wifilauncherforhur.connectivity.WiFiP2PConnector
import org.mockito.Mockito
import java.util.Locale

@SuppressLint("MissingPermission")
class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager?,
    private val channel: WifiP2pManager.Channel?,
    mService: Context,
    private val connector: WiFiP2PConnector
) : BroadcastReceiver(), PeerListListener {
    private val lookfor: String = PreferenceManager.getDefaultSharedPreferences(mService)
        .getString("hur_p2p_name", "HUR7")!!.lowercase(Locale.getDefault())

    private var discoveryStarted = false
    private var hurfound = false

    // Add these new variables
    private val connectivityManager: ConnectivityManager
    private var networkCallback: NetworkCallback? = null

    init {

        // Initialize ConnectivityManager
        this.connectivityManager =
            mService.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager


        Log.d("WiFi-P2P", "Look for Device with following name:" + lookfor)
    }

    override fun onReceive(context: Context?, intent: Intent) {
        val action = intent.getAction()



        Log.d("WifiP2P", "Action is: " + action)
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION == action) {
            if (intent.getIntExtra(
                    WifiP2pManager.EXTRA_WIFI_STATE,
                    WifiP2pManager.WIFI_P2P_STATE_ENABLED
                ) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
            ) {
                Log.d("WifiP2P", "Wifi p2p is enabled: " + intent)
                startDiscovery()
                //Wifi P2P is enabled.
            }
            // Check to see if Wi-Fi is enabled and notify appropriate activity
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == action) {
            if (manager != null) {
                if (hurfound) return
                manager.requestPeers(channel, this)
            }
            // Call WifiP2pManager.requestPeers() to get a list of current peers
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION == action) {
            // This is the important part
            val p2pInfo =
                intent.getParcelableExtra<WifiP2pInfo?>(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
            if (p2pInfo !=null){
                val groupInfo = intent.getParcelableExtra<WifiP2pGroup>(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
                connectToAA(p2pInfo, groupInfo);
            }
        }
    }


    private fun startDiscovery() {
        if (discoveryStarted || hurfound) {
            return
        }
        manager!!.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                discoveryStarted = true;
                Log.d("WifiP2P", "P2P discovery started")
                loopDiscovery()
            }

            override fun onFailure(i: Int) {
                Log.e("WifiP2P", "P2P FAILED to start")
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed(Runnable { startDiscovery() }, 2000)
            }
        })
    }

    private fun stopDiscovery(restart: Boolean) {
        if (!discoveryStarted){
            return
        }
        manager!!.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                discoveryStarted = false
                Log.d("WifiP2P", "Discovery stopped")
                if (restart) {
                    Log.d("WifiP2P", "Restarting discovery")
                    startDiscovery()
                }
            }

            override fun onFailure(i: Int) {
                Log.e("WifiP2P", "Discovery NOT stopped!")
            }
        })
    }

    private fun loopDiscovery() {
        if (discoveryStarted) {
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed(Runnable { stopDiscovery(!hurfound) }, 10000)
        }
    }

    override fun onPeersAvailable(wifiP2pDeviceList: WifiP2pDeviceList) {
        for (device in wifiP2pDeviceList.getDeviceList()) {
            Log.d("WifiP2P", "Found device: " + device.deviceName)
            if (device.deviceName.lowercase(Locale.getDefault()).contains(lookfor)) {
                hurfound = true
                Log.d("WifiP2P", "Connecting to: " + device)
                val config = WifiP2pConfig()
                config.deviceAddress = device.deviceAddress
                config.groupOwnerIntent = 0
                manager!!.connect(channel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.d("WifiP2P", "Connected to: " + device)
                    }

                    override fun onFailure(reason: Int) {
                        Log.d(
                            "WifiP2P",
                            "Failed to connect to: " + device + " with reason: " + reason
                        )
                        hurfound = false
                        startDiscovery()
                    }
                })
                break
            }
        }
    }

    fun connectToAA(wifiP2pInfo: WifiP2pInfo, groupInfo : WifiP2pGroup?) {
        if (wifiP2pInfo.groupFormed){
            if (wifiP2pInfo.isGroupOwner) {
                Log.d("WifiP2P", "me assigned as group owner so removing group & restart")
                manager!!.removeGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onFailure(reason: Int) {
                        Log.d("WifiP2P", "Failed to remove group:")
                    }

                    override fun onSuccess() {
                        Log.d("WifiP2P", "Group removed. starting discovery again")
                        hurfound = false
                        startDiscovery()
                    }
                })
            } else {
                if (groupInfo!=null){
                    var fakeNetwork: Network? = null
                    try {
                        fakeNetwork = Mockito.mock(
                            Network::class.java,
                            Mockito.withSettings().useConstructor(groupInfo.networkId)
                        )
                    } catch (e: Exception) {
                    }

                    if (fakeNetwork != null) {
                        val p = Parcel.obtain()
                        fakeNetwork.writeToParcel(p, 0)
                        p.setDataPosition(0)
                        connector.network = Network.CREATOR.createFromParcel(p)
                    }
                }
                connector.startAA(wifiP2pInfo.groupOwnerAddress.hostAddress, false)
            }
        }
    }

    fun stop(){
        stopDiscovery(false)
    }
}