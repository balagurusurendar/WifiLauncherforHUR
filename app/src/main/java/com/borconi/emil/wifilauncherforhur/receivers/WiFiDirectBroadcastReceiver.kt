package com.borconi.emil.wifilauncherforhur.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.MacAddress
import android.net.Network
import android.net.wifi.WifiManager
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
import androidx.preference.Preference
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

    private val wifiManager: WifiManager

    private var customFrequencyFailed = false
    private val preference : SharedPreferences
    init {

        // Initialize ConnectivityManager
        this.connectivityManager =
            mService.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        this.wifiManager = mService.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        this.preference = PreferenceManager.getDefaultSharedPreferences(mService)

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

    private fun get2GhzFrequency(): Int {
        // Define the non-overlapping 2.4 GHz channels (1, 6, 11) and their frequencies in MHz
        val channelFrequencies = mapOf(
            1 to 2412, 6 to 2437, 11 to 2462
        )
        // Initialize a mutable map to count networks on each non-overlapping channel
        val channelCounts = channelFrequencies.keys.associateWith { 0 }.toMutableMap()

        try {
            // Getting scan results requires location permissions and enabled location services
            val scanResults = wifiManager.scanResults
            for (result in scanResults) {
                val channel = frequencyToChannel(result.frequency)
                // To account for overlap, we assign a network to the nearest non-overlapping channel.
                when (channel) {
                    in 1..3 -> channelCounts[1] = channelCounts[1]!! + 1 // Channels 1-3 are closest to 1
                    in 4..8 -> channelCounts[6] = channelCounts[6]!! + 1 // Channels 4-8 are closest to 6
                    in 9..13 -> channelCounts[11] = channelCounts[11]!! + 1 // Channels 9-13 are closest to 11
                }
            }
        } catch (e: SecurityException) {
            Log.e("WifiP2P", "Permission denied for getting scan results", e)
            // If permissions are missing or scan fails, return the default frequency for channel 6.
            return 2437
        }

        // Find the channel with the fewest networks, defaulting to channel 6 if the map is empty.
        val leastPopulatedChannel = channelCounts.minByOrNull { it.value }?.key ?: 6

        // Return the frequency in MHz for the least populated channel, defaulting to channel 6's frequency.
        return channelFrequencies[leastPopulatedChannel] ?: 2437
    }

    private fun get5GhzFrequency(): Int {
        // A selection of non-overlapping 20MHz channels in the 5GHz band.
        val channelFrequencies = mapOf(
            36 to 5180, 40 to 5200, 44 to 5220, 48 to 5240,
            149 to 5745, 153 to 5765, 157 to 5785, 161 to 5805
        )
        val channelCounts = channelFrequencies.keys.associateWith { 0 }.toMutableMap()

        try {
            val scanResults = wifiManager.scanResults
            for (result in scanResults) {
                val channel = frequencyToChannel(result.frequency)
                if (channelCounts.containsKey(channel)) {
                    channelCounts[channel] = channelCounts[channel]!! + 1
                }
            }
        } catch (e: SecurityException) {
            Log.e("WifiP2P", "Permission denied for getting 5GHz scan results", e)
            return 5180 // Default to channel 36
        }

        // Find the channel with the minimum number of networks, defaulting to channel 36
        val leastPopulatedChannel = channelCounts.minByOrNull { it.value }?.key ?: 36

        // Return the frequency in MHz for the least populated channel
        return channelFrequencies[leastPopulatedChannel] ?: 5180
    }


    /**
     * Converts a Wi-Fi frequency in MHz to its corresponding channel number.
     */
    private fun frequencyToChannel(freq: Int): Int {
        return when (freq) {
            in 2412..2483 -> (freq - 2412) / 5 + 1
            2484 -> 14 // Channel 14 is a special case
            in 5170..5825 -> (freq - 5000) / 5
            else -> -1 // Not a standard Wi-Fi frequency
        }
    }

    override fun onPeersAvailable(wifiP2pDeviceList: WifiP2pDeviceList) {
        for (device in wifiP2pDeviceList.getDeviceList()) {
            Log.d("WifiP2P", "Found device: " + device.deviceName)
            if (device.deviceName.lowercase(Locale.getDefault()).contains(lookfor)) {
                hurfound = true
                Log.d("WifiP2P", "Connecting to: " + device)
                val frequencySelected : Int = if (customFrequencyFailed){
                    0
                }else{
                    preference.getInt("wifi_frequency", 0)
                }
                val configBuilder = WifiP2pConfig.Builder()
                configBuilder.setDeviceAddress(MacAddress.fromString(device.deviceAddress))
                Log.d("WifiP2P", "Selected frequency: $frequencySelected")
                when(frequencySelected){
                    2 -> configBuilder.setGroupOperatingFrequency(get2GhzFrequency())
                    5 -> configBuilder.setGroupOperatingFrequency(get5GhzFrequency())
                }
                val config = configBuilder.build()
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
                        customFrequencyFailed = frequencySelected != 0
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
                connector.startAA(wifiP2pInfo.groupOwnerAddress.hostAddress)
            }
        }
    }

    fun stop(){
        stopDiscovery(false)
    }
}