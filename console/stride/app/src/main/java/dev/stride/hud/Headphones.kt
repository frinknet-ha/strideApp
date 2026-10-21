package dev.stride.hud

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/** Public discovery and pairing APIs. Android owns pairing confirmation and A2DP routing. */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class Headphones(private val activity: Activity, private val moving: () -> Boolean) {
    companion object { const val PERMISSION = 7623 }
    private val main = Handler(Looper.getMainLooper())
    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private var profile: BluetoothProfile? = null
    private val found = linkedMapOf<String, BluetoothDevice>()
    private var scanning = false
    private var closed = false
    private var registered = false
    private var message = "Put headphones in pairing mode, then tap Find headphones."
    @Volatile private var cached = "{\"message\":\"Loading Bluetooth…\",\"devices\":[]}"
    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(id: Int, proxy: BluetoothProfile) {
            if (closed) { adapter?.closeProfileProxy(id, proxy); return }
            profile = proxy; refresh()
        }
        override fun onServiceDisconnected(id: Int) { profile = null; refresh() }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (closed) return
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> if (device != null && isAudio(device)) found[device.address] = device
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    scanning = false; message = "Search complete. Tap Pair beside your headphones."
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    if (device != null && isAudio(device)) found[device.address] = device
                    message = when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                        BluetoothDevice.BOND_BONDED -> "Paired. Waiting for Android to connect headphone audio."
                        BluetoothDevice.BOND_BONDING -> "Confirm the Android pairing prompt if one appears."
                        else -> "Pairing did not complete. Put headphones in pairing mode and try again."
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> message = if (adapter?.isEnabled == true) "Bluetooth is on. Find headphones to pair." else "Bluetooth is off."
            }
            refresh()
        }
    }
    init { main.post {
        if (!closed) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            }
            activity.registerReceiver(receiver, filter); registered = true
            try { adapter?.getProfileProxy(activity, listener, BluetoothProfile.A2DP) } catch (_: Exception) {}
            refresh()
        }
    } }
    private fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    private fun permitted() = permissions().all { activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    private fun idle(): Boolean {
        if (moving()) { message = "Pause the workout before pairing headphones."; refresh(); return false }
        return true
    }
    private fun isAudio(device: BluetoothDevice): Boolean = try {
        device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
            (device.bluetoothClass?.majorDeviceClass in listOf(null, 0, BluetoothClass.Device.Major.UNCATEGORIZED) && !device.name.isNullOrBlank())
    } catch (_: SecurityException) { false }
    private fun refresh() {
        if (closed) return
        try {
            val on = adapter?.isEnabled == true
            if (on) adapter?.bondedDevices?.filter(::isAudio)?.forEach { found[it.address] = it }
            val connected = profile?.connectedDevices?.map { it.address }?.toSet() ?: emptySet()
            val devices = JSONArray()
            found.values.forEach { device ->
                devices.put(JSONObject().put("address", device.address).put("name", device.name ?: "Headphones")
                    .put("paired", device.bondState == BluetoothDevice.BOND_BONDED)
                    .put("pairing", device.bondState == BluetoothDevice.BOND_BONDING)
                    .put("connected", device.address in connected))
            }
            cached = JSONObject().put("enabled", on).put("available", adapter != null)
                .put("scanning", scanning).put("message", if (connected.isNotEmpty()) "Headphone audio connected." else message)
                .put("devices", devices).toString()
        } catch (_: SecurityException) {
            cached = JSONObject().put("enabled", true).put("available", adapter != null).put("scanning", false)
                .put("message", "Allow Bluetooth discovery permission to find headphones.").put("devices", JSONArray()).toString()
        }
    }
    fun status(): String = cached
    fun enable() {
        if (!idle()) return
        try { activity.startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 7624) }
        catch (_: Exception) { message = "Android could not enable Bluetooth. Use Android Bluetooth settings."; refresh() }
    }
    fun scan() {
        if (closed || !idle()) return
        if (!permitted()) { activity.requestPermissions(permissions(), PERMISSION); return }
        if (adapter?.isEnabled != true) { message = "Turn Bluetooth on before searching."; refresh(); return }
        try {
            if (scanning) adapter.cancelDiscovery()
            found.entries.removeAll { it.value.bondState == BluetoothDevice.BOND_NONE }
            scanning = adapter.startDiscovery()
            message = if (scanning) "Searching nearby headphones… Keep them in pairing mode." else "Search could not start. Check Bluetooth and Android location services."
            refresh()
            main.postDelayed({ if (!closed && scanning) { adapter.cancelDiscovery(); scanning = false; refresh() } }, 20_000)
        } catch (_: Exception) { message = "Search unavailable. Check Android Bluetooth permissions and location services."; refresh() }
    }
    fun permissionResult(results: IntArray) {
        if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) scan()
        else { message = "Discovery permission declined. Android Bluetooth settings remain available."; refresh() }
    }
    fun pair(address: String) {
        if (closed || !idle()) return
        val device = found[address] ?: return
        try {
            if (scanning) { adapter?.cancelDiscovery(); scanning = false }
            message = when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> "Already paired. Turn the headphones off and on to reconnect; use Android Bluetooth settings to connect audio if needed."
                BluetoothDevice.BOND_BONDING -> "Pairing in progress. Confirm the Android prompt."
                else -> if (device.createBond()) "Pairing… Confirm the Android prompt if shown." else "Pairing could not start. Try pairing mode again."
            }
        } catch (_: Exception) { message = "Pairing failed. Try Android Bluetooth settings." }
        refresh()
    }
    fun close() {
        closed = true; main.removeCallbacksAndMessages(null)
        try { if (scanning) adapter?.cancelDiscovery() } catch (_: Exception) {}
        try { if (registered) activity.unregisterReceiver(receiver) } catch (_: Exception) {}
        profile?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }; profile = null
    }
}
