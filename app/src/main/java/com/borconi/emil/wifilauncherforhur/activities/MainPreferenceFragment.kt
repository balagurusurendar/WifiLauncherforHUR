package com.borconi.emil.wifilauncherforhur.activities

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.borconi.emil.wifilauncherforhur.BuildConfig
import com.borconi.emil.wifilauncherforhur.EmptyListPreference
import com.borconi.emil.wifilauncherforhur.R
import com.borconi.emil.wifilauncherforhur.services.WifiService
import com.borconi.emil.wifilauncherforhur.services.WifiService.Companion.isRunning
import java.util.stream.Collectors

class MainPreferenceFragment : PreferenceFragmentCompat() {
    private var alertDialogOpen = false
    private var bluetoothDevicesPreference: Preference? = null


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    private fun updatepref(preference: Preference?, newValue: Any?): Boolean {
        if (newValue == null) return false

        val preferenceScreen = getPreferenceScreen()
        val option = newValue.toString().toInt()
        val wifivalue = preferenceScreen.findPreference<EditTextPreference?>("hur_p2p_name")
        val wififrequencyOption = preferenceScreen.findPreference<ListPreference?>("wifi_frequency")
        if (option == 2) {
            wifivalue!!.isVisible = true
            wififrequencyOption!!.isVisible = true
            wifivalue.setSummary(R.string.hur_p2p_name_desc)
            wifivalue.setTitle(R.string.hur_p2p_name)
        } else {
            wifivalue!!.isVisible = false
            wififrequencyOption!!.isVisible = false
        }

        if (isRunning) {
            activity!!.stopService(Intent(context, WifiService::class.java))
            Thread {
                try {
                    Thread.sleep(500)
                    activity!!.startService(Intent(context, WifiService::class.java))
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                }
            }.start()
        }
        return true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = super.onCreateView(inflater, container, savedInstanceState) as LinearLayout


        val preferenceScreen = getPreferenceScreen()
        bluetoothDevicesPreference =
            preferenceScreen.findPreference("selected_bluetooth_devices")

        preferenceScreen.findPreference<Preference?>("connection_mode")!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference, newValue -> updatepref(preference, newValue) }

        val p = preferenceScreen.findPreference<ListPreference?>("connection_mode")
        updatepref(p, p!!.value)

        bluetoothDevicesPreference!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { preference: Preference? ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) if (ActivityCompat.checkSelfPermission(
                        context!!,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val uri = Uri.fromParts("package", context!!.getPackageName(), null)
                    intent.setData(uri)
                    startActivity(intent)
                    return@OnPreferenceClickListener false
                } else tryPopulateBluetoothDevices(preference as EmptyListPreference)
                true
            }

        if (bluetoothDevicesPreference != null) {
            bluetoothDevicesPreference!!.setOnPreferenceChangeListener { preference: Preference?, newValue: Any? ->
                val bluetoothDevicesPref = preference as EmptyListPreference
                bluetoothDevicesPref.setValues(newValue as MutableSet<String?>?)
                setBluetoothDevicesSummary(bluetoothDevicesPref)
                true
            }
        }


        val startServiceManuallyPreference =
            preferenceScreen.findPreference<Preference?>("start_service_manually")
        startServiceManuallyPreference?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { _: Preference? ->
                val context = getContext()
                val wifiServiceIntent = Intent(context, WifiService::class.java)

                context?.startForegroundService(wifiServiceIntent)
                true
            }


        if (!alertDialogOpen && !PreferenceManager.getDefaultSharedPreferences(context!!)
                .getBoolean("nowarning", false)
        ) {
            val builder =
                AlertDialog.Builder(getActivity()!!, R.style.Base_Theme_MaterialComponents_Dialog)

            builder.setTitle(getActivity()!!.getResources().getString(R.string.major_title))
            builder.setMessage(getActivity()!!.getResources().getString(R.string.major_desc))
            builder.setPositiveButton(
                getString(R.string.save)
            ) { dialog: DialogInterface?, id: Int ->
                PreferenceManager.getDefaultSharedPreferences(context!!).edit()
                    .putBoolean("nowarning", true).apply()
                dialog!!.dismiss()
            }
            builder.setNegativeButton(
                getString(R.string.close),
                (DialogInterface.OnClickListener { dialogInterface: DialogInterface?, i: Int -> dialogInterface!!.dismiss() })
            )
            builder.setOnDismissListener { dialog: DialogInterface? ->
                alertDialogOpen = false
            }
            alertDialogOpen = true
            builder.show()
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestPermissionLauncher.launch(
            arrayOf<String>(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) requestPermissionLauncher.launch(
            arrayOf<String>(Manifest.permission.BLUETOOTH_CONNECT)
        )

        return v
    }


    fun requestDrawOverlays(msg: Int) {
        if (!alertDialogOpen) {
            val builder =
                AlertDialog.Builder(activity!!, R.style.Base_Theme_MaterialComponents_Dialog)

            builder.setTitle(
                getActivity()!!.getResources().getString(R.string.alert_permission_denied_title)
            )
            builder.setMessage(activity!!.resources.getString(msg))
            builder.setPositiveButton(
                "OK"
            ) { dialog: DialogInterface?, id: Int ->
                val intent: Intent?
                if (msg == R.string.alert_need_draw_over_other_apps) {
                    intent =
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(Uri.parse("package:" + activity!!.packageName))
                    startActivity(intent)
                } else if (msg == R.string.System_settings_desc) {
                    intent =
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData(Uri.parse("package:" + activity!!.packageName))
                    startActivity(intent)
                } else if (msg == R.string.battery_optimization_title) {
                    intent =
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(
                            Uri.parse("package:" + activity!!.packageName)
                        )
                    builder.setTitle(getString(R.string.battery_optimization_title))
                    startActivity(intent)
                } else if (msg == R.string.locations_needed) requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
                else requestPermissionLauncher.launch(arrayOf<String>(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                dialog!!.dismiss()
            }
            builder.setOnDismissListener { dialog: DialogInterface? ->
                alertDialogOpen = false
            }
            alertDialogOpen = true
            builder.show()
        }
    }


    override fun onResume() {
        super.onResume()
        val preferenceScreen = getPreferenceScreen()

        val bluetoothDevices =
            preferenceScreen.findPreference<MultiSelectListPreference?>("selected_bluetooth_devices")

        tryPopulateBluetoothDevices((bluetoothDevicesPreference as EmptyListPreference?)!!)
        setBluetoothDevicesSummary(bluetoothDevices)
        updatePermissionsStatusPreference()
    }


    protected fun updatePermissionsStatusPreference() {
        val preferenceScreen = getPreferenceScreen()
        val permissionsStatusPreference =
            preferenceScreen.findPreference<Preference?>("permissions_status")
        if (permissionsStatusPreference != null) {
            if (getPermission(false)) {
                permissionsStatusPreference.title = getString(R.string.status_all_permissions_granted)
                permissionsStatusPreference.setIcon(R.drawable.ic_green_done_24)
            } else {
                permissionsStatusPreference.title = getString(R.string.status_denied_permissions)
                permissionsStatusPreference.setIcon(R.drawable.ic_red_report_problem_24)
                permissionsStatusPreference.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener { preference: Preference? ->
                        getPermission(true)
                        true
                    }
                getPermission(true)
            }
        }
    }


    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), { permission: Map<String, Boolean> ->
        permission.entries.forEach { entry ->
            run {
                if (!entry.value) {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(activity as Activity, entry.key)) {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                            )
                        )
                    } else {
                        if (entry.key === Manifest.permission.BLUETOOTH_CONNECT)
                            setBluetoothDevicesSummary(null)
                    }
                } else {
                    tryPopulateBluetoothDevices(bluetoothDevicesPreference as EmptyListPreference)
                }
            }
        }
    })

    private fun getPermission(show: Boolean): Boolean {
        val packageName = context!!.packageName
        val pm = context!!.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Settings.canDrawOverlays(context) && Settings.System.canWrite(context)
            && pm.isIgnoringBatteryOptimizations(packageName) //&& ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            && ((Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                activity!!,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                activity!!,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED)
                    ) || ContextCompat.checkSelfPermission(
                activity!!,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED)
        ) return true
        else if (!show) return false
        else if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                activity!!,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                requestDrawOverlays(R.string.locations_needed)
            } else requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return false
        } else if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                activity!!,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                requestDrawOverlays(R.string.background_location_needed)
            } else requestPermissionLauncher.launch(arrayOf<String>(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            return false
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                activity!!,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(arrayOf<String>(Manifest.permission.NEARBY_WIFI_DEVICES))
            return false
        } else if (!Settings.canDrawOverlays(context)) {
            requestDrawOverlays(R.string.alert_need_draw_over_other_apps)
            return false
        } else if (!Settings.System.canWrite(context)) {
            requestDrawOverlays(R.string.System_settings_desc)
            return false
        } else {
            requestDrawOverlays(R.string.disable_optimization)

            return false
        }
    }


    protected fun tryPopulateBluetoothDevices(preference: MultiSelectListPreference) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter != null && !adapter.isEnabled) {
            val intentOpenBluetoothSettings = Intent()
            intentOpenBluetoothSettings.setAction(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intentOpenBluetoothSettings, REQUEST_ENABLE_BT)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) if (ActivityCompat.checkSelfPermission(
                context!!,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //  startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + BuildConfig.APPLICATION_ID)));
            return
        }
        if (adapter!!.getBondedDevices().isEmpty()) {
            val entries = arrayOfNulls<String>(1)
            val entryValues = arrayOfNulls<String>(1)
            entries[0] = "NO DEVICES"
            entryValues[0] = null
        }
        val entries = arrayOfNulls<String>(adapter.getBondedDevices().size)
        val entryValues = arrayOfNulls<String>(adapter.getBondedDevices().size)
        var i = 0
        for (dev in adapter.getBondedDevices()) {
            if (dev.getName() == null || "".equals(dev.getName(), ignoreCase = true)) entries[i] =
                "UNKNOWN"
            else entries[i] = dev.getName()
            entryValues[i] = dev.getAddress()
            i++
        }



        preference.entries = entries
        preference.entryValues = entryValues
    }

    protected fun setBluetoothDevicesSummary(bluetoothDevices: MultiSelectListPreference?) {
        val adapter = BluetoothAdapter.getDefaultAdapter()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) if (ActivityCompat.checkSelfPermission(
                context!!,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothDevices!!.setSummary(R.string.nobt)
            return
        }

        if (adapter == null || !adapter.isEnabled) {
            bluetoothDevices!!.setSummary(R.string.settings_bluetooth_selected_bluetooth_devices_turn_on)
            return
        }
        if (bluetoothDevices != null) {
            val values = bluetoothDevices.values.stream()
                .filter { v: String? -> !v.equals("", ignoreCase = true) }
                .collect(Collectors.toSet())

            if (values.isNotEmpty()) {
                bluetoothDevices.setSummary(
                    values.stream()
                        .map<CharSequence?> { v: String? ->
                            val indexOfValue = bluetoothDevices.findIndexOfValue(v)
                            if (indexOfValue >= 0) {
                                return@map bluetoothDevices.entries[indexOfValue]
                            }
                            getString(R.string.settings_bluetooth_selected_bluetooth_devices_forgotten_device)
                        }.collect(Collectors.joining(", "))
                )
            } else {
                bluetoothDevices.setSummary(R.string.settings_bluetooth_selected_bluetooth_devices_description)
            }
        }
    }


    override fun onStop() {
        super.onStop()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            val preferenceScreen = getPreferenceScreen()
            val bluetoothDevices =
                preferenceScreen.findPreference<MultiSelectListPreference?>("selected_bluetooth_devices")
            tryPopulateBluetoothDevices(bluetoothDevices!!)
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 90
    }
}