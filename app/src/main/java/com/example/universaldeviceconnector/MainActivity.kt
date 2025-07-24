package com.example.universaldeviceconnector

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "UDCApp"
        private const val APP_AUTHORIZED_USER = "admin"
        private const val SECURE_PASS_HASH = "098f6bcd4621d373cade4e832627b4f6" // "test"
        private const val APP_MODE = "production" // or "experimental"
        private const val KILL_SWITCH_FILE = "kill.flag"
        
        val SUPPORTED_CATEGORIES = listOf(
            "vending machine", "atm", "vehicle", "router", "printer", "iot sensor",
            "appliance", "camera", "speaker", "smart display", "hvac", "cnc",
            "label printer", "usb device", "serial device", "pos terminal",
            "kiosk", "scanner", "medical device", "industrial controller"
        )
        
        private const val ONLINE_LOOKUP_API = "https://api.macvendors.com/"
        
        val COMMON_NETWORK_RANGES = listOf(
            "192.168.1.0/24", "192.168.0.0/24", "10.0.0.0/24", "172.16.0.0/24"
        )
        
        val COMMON_PORTS = listOf(22, 23, 80, 443, 21, 25, 53, 110, 143, 993, 995, 515, 631, 3389, 8080)
        
        val DEVICE_SIGNATURES = mapOf(
            "printer" to listOf("hp", "canon", "epson", "brother", "lexmark", "xerox"),
            "router" to listOf("linksys", "netgear", "cisco", "asus", "tp-link", "d-link"),
            "camera" to listOf("axis", "hikvision", "dahua", "bosch", "sony", "panasonic"),
            "pos" to listOf("verifone", "ingenico", "pax", "square", "clover"),
            "atm" to listOf("ncr", "diebold", "wincor", "hitachi", "fujitsu"),
            "iot" to listOf("arduino", "raspberry", "esp8266", "esp32", "particle")
        )
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var wifiManager: WifiManager
    val deviceCache = mutableMapOf<String, DeviceInfo>()
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainScope = CoroutineScope(Dispatchers.Main)
    
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager
    private lateinit var adapter: DevicePagerAdapter
    
    private val logFileName = "log_${APP_MODE}.log"
    private val debugFileName = "debug_${APP_MODE}.log"
    private val cacheFileName = "device_cache.json"
    private val networkDevicesFile = "network_devices.json"

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var granted = true
        permissions.entries.forEach {
            if (!it.value) granted = false
        }
        if (granted) {
            startAllScans()
        } else {
            Toast.makeText(this, "Required permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    data class DeviceInfo(
        val name: String,
        val address: String,
        val vendor: String = "Unknown",
        val category: String = "Unknown",
        val ports: List<Int> = emptyList(),
        val services: List<String> = emptyList(),
        val compatibility: String = "Unknown",
        val lastSeen: Long = System.currentTimeMillis(),
        val isActive: Boolean = false
    ) : java.io.Serializable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (checkKillSwitch()) {
            corruptApp()
            return
        }
        
        loadCache()
        loadNetworkDevices()
        
        setContentView(R.layout.activity_main)
        
        // Initialize Bluetooth
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        
        adapter = DevicePagerAdapter(this, mainScope, ioScope)
        viewPager.adapter = adapter
        tabLayout.setupWithViewPager(viewPager)
        
        authenticateUser()
    }

    private fun checkKillSwitch(): Boolean {
        val file = File(filesDir, KILL_SWITCH_FILE)
        if (file.exists()) {
            logEvent("Kill switch detected - terminating application")
            return true
        }
        return false
    }

    private fun corruptApp() {
        logEvent("Kill switch triggered. Corrupting app data.")
        try {
            val filesToDelete = listOf(
                File(filesDir, logFileName),
                File(filesDir, debugFileName),
                File(filesDir, cacheFileName),
                File(filesDir, networkDevicesFile)
            )
            filesToDelete.forEach {
                if (it.exists()) {
                    it.delete()
                    logEvent("Deleted: ${it.name}")
                }
            }
            deviceCache.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error corrupting app", e)
        }
        finishAffinity()
    }

    private fun authenticateUser() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        
        AlertDialog.Builder(this)
            .setTitle("Authentication Required")
            .setMessage("Enter password for user \"$APP_AUTHORIZED_USER\":")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                val enteredPw = input.text.toString()
                if (md5(enteredPw) == SECURE_PASS_HASH) {
                    logEvent("User authenticated successfully")
                    checkPermissionsAndStartScans()
                } else if (APP_MODE == "experimental") {
                    AlertDialog.Builder(this)
                        .setTitle("Bypass Permission")
                        .setMessage("Verbal permission granted? (yes)")
                        .setCancelable(false)
                        .setPositiveButton("Yes") { _, _ ->
                            logEvent("Bypass granted (experimental mode)")
                            checkPermissionsAndStartScans()
                        }
                        .setNegativeButton("No") { _, _ ->
                            logEvent("Access denied - bypass rejected")
                            finish()
                        }
                        .show()
                } else {
                    logEvent("Authentication failed for user: $APP_AUTHORIZED_USER")
                    Toast.makeText(this, "Access denied", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .show()
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun loadCache() {
        try {
            val cacheFile = File(filesDir, cacheFileName)
            if (cacheFile.exists()) {
                val jsonText = cacheFile.readText()
                val jsonObj = JSONObject(jsonText)
                jsonObj.keys().forEach { key ->
                    val deviceJson = jsonObj.getJSONObject(key)
                    val device = DeviceInfo(
                        name = deviceJson.optString("name", "Unknown"),
                        address = deviceJson.optString("address", ""),
                        vendor = deviceJson.optString("vendor", "Unknown"),
                        category = deviceJson.optString("category", "Unknown"),
                        compatibility = deviceJson.optString("compatibility", "Unknown"),
                        lastSeen = deviceJson.optLong("lastSeen", System.currentTimeMillis())
                    )
                    deviceCache[key] = device
                }
                logDebug("Cache loaded with ${deviceCache.size} entries")
            }
        } catch (e: Exception) {
            logDebug("Cache load failed: ${e.message}")
        }
    }

    private fun loadNetworkDevices() {
        try {
            val networkFile = File(filesDir, networkDevicesFile)
            if (networkFile.exists()) {
                val jsonText = networkFile.readText()
                val jsonObj = JSONObject(jsonText)
                logDebug("Network devices cache loaded")
            }
        } catch (e: Exception) {
            logDebug("Network devices load failed: ${e.message}")
        }
    }

    fun saveCache() {
        try {
            val jsonObj = JSONObject()
            deviceCache.entries.forEach { (key, device) ->
                val deviceJson = JSONObject().apply {
                    put("name", device.name)
                    put("address", device.address)
                    put("vendor", device.vendor)
                    put("category", device.category)
                    put("compatibility", device.compatibility)
                    put("lastSeen", device.lastSeen)
                }
                jsonObj.put(key, deviceJson)
            }
            val cacheFile = File(filesDir, cacheFileName)
            cacheFile.writeText(jsonObj.toString(2))
            logDebug("Cache saved with ${deviceCache.size} entries")
        } catch (e: Exception) {
            logDebug("Cache save failed: ${e.message}")
        }
    }

    private fun checkPermissionsAndStartScans() {
        val permissionsNeeded = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH)
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsNeeded.add(Manifest.permission.ACCESS_WIFI_STATE)
        permissionsNeeded.add(Manifest.permission.CHANGE_WIFI_STATE)
        permissionsNeeded.add(Manifest.permission.INTERNET)
        
        val missingPermissions = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            startAllScans()
        }
    }

    private fun startAllScans() {
        adapter.startBluetoothScan()
        adapter.startWifiScan()
        adapter.startNetworkScan()
        adapter.listSerialDevices()
    }

    suspend fun lookupOnline(macAddress: String): String {
        return try {
            val request = Request.Builder()
                .url("$ONLINE_LOOKUP_API$macAddress")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string() ?: "Unknown"
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            logDebug("Online lookup failed for $macAddress: ${e.message}")
            "Unknown"
        }
    }

    fun categorizeDevice(name: String, vendor: String): String {
        val searchText = "$name $vendor".lowercase(Locale.getDefault())
        
        DEVICE_SIGNATURES.entries.forEach { (category, keywords) ->
            if (keywords.any { keyword -> searchText.contains(keyword) }) {
                return category
            }
        }
        
        return when {
            searchText.contains("print") -> "printer"
            searchText.contains("router") || searchText.contains("access point") -> "router"
            searchText.contains("camera") || searchText.contains("webcam") -> "camera"
            searchText.contains("speaker") || searchText.contains("audio") -> "speaker"
            searchText.contains("tv") || searchText.contains("display") -> "smart display"
            else -> "unknown"
        }
    }

    fun suggestAction(name: String, category: String): String {
        return when (category.lowercase()) {
            "printer" -> "Check print queue, test page"
            "router" -> "Check connectivity, admin panel"
            "camera" -> "Verify stream, check settings"
            "pos", "atm" -> "Check transaction logs, network"
            "iot" -> "Verify sensor data, connectivity"
            "speaker" -> "Test audio output, pairing"
            else -> "Basic connectivity test"
        }
    }

    fun checkCompatibility(name: String, category: String): String {
        val searchText = name.lowercase(Locale.getDefault())
        
        return when (category) {
            "printer" -> when {
                searchText.contains("hp") || searchText.contains("canon") -> "High compatibility"
                searchText.contains("brother") || searchText.contains("epson") -> "Medium compatibility"
                else -> "Unknown compatibility"
            }
            "router" -> "Standard network protocols"
            "camera" -> "RTSP/HTTP streaming"
            "pos" -> "EMV/NFC protocols"
            else -> "Standard protocols"
        }
    }

    suspend fun performPortScan(ipAddress: String): List<Int> {
        val openPorts = mutableListOf<Int>()
        
        COMMON_PORTS.forEach { port ->
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ipAddress, port), 1000)
                openPorts.add(port)
                socket.close()
                logDebug("Open port found: $ipAddress:$port")
            } catch (e: Exception) {
                // Port is closed or filtered
            }
        }
        
        return openPorts
    }

    fun logEvent(text: String) {
        try {
            val logsDir = getExternalFilesDir(null) ?: filesDir
            val logFile = File(logsDir, logFileName)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            logFile.appendText("[$timestamp] $text\n")
            Log.i(TAG, text)
        } catch (e: Exception) {
            Log.e(TAG, "Logging failed", e)
        }
    }

    fun logDebug(text: String) {
        try {
            val logsDir = getExternalFilesDir(null) ?: filesDir
            val debugFile = File(logsDir, debugFileName)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            debugFile.appendText("[$timestamp] $text\n")
            Log.d(TAG, text)
        } catch (e: Exception) {
            Log.e(TAG, "Debug logging failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
        mainScope.cancel()
        
        try {
            unregisterReceiver(adapter.btReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
        
        try {
            unregisterReceiver(adapter.wifiReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
        
        saveCache()
        logEvent("Application terminated")
    }
}
