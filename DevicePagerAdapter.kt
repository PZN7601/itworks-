package com.example.universaldeviceconnector

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.viewpager.widget.PagerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class DevicePagerAdapter(
    private val activity: MainActivity,
    private val mainScope: CoroutineScope,
    private val ioScope: CoroutineScope
) : PagerAdapter() {
    private val tabTitles = arrayOf("Bluetooth", "WiFi", "Network", "Serial")
    private lateinit var bluetoothView: View
    private lateinit var wifiView: View
    private lateinit var networkView: View
    private lateinit var serialView: View

    // Bluetooth UI elements
    private lateinit var btListView: ListView
    private lateinit var btAdapter: ArrayAdapter<String>
    private val btDevices = mutableListOf<BluetoothDevice>()
    private val btDisplayList = mutableListOf<String>()

    // WiFi UI elements
    private lateinit var wifiListView: ListView
    private lateinit var wifiAdapter: ArrayAdapter<String>
    private val wifiScanResults = mutableListOf<ScanResult>()
    private val wifiDisplayList = mutableListOf<String>()

    // Network UI elements
    private lateinit var networkListView: ListView
    private lateinit var networkAdapter: ArrayAdapter<String>
    private val networkDevices = mutableListOf<MainActivity.DeviceInfo>()
    private val networkDisplayList = mutableListOf<String>()

    // Serial UI elements
    private lateinit var serialListView: ListView
    private lateinit var serialAdapter: ArrayAdapter<String>
    private val serialDisplayList = mutableListOf<String>()

    override fun instantiateItem(container: android.view.ViewGroup, position: Int): Any {
        val inflater = LayoutInflater.from(container.context)
        val view = when (position) {
            0 -> {
                bluetoothView = inflater.inflate(R.layout.device_list_layout, container, false)
                setupBluetoothView()
                bluetoothView
            }
            1 -> {
                wifiView = inflater.inflate(R.layout.device_list_layout, container, false)
                setupWifiView()
                wifiView
            }
            2 -> {
                networkView = inflater.inflate(R.layout.device_list_layout, container, false)
                setupNetworkView()
                networkView
            }
            3 -> {
                serialView = inflater.inflate(R.layout.device_list_layout, container, false)
                setupSerialView()
                serialView
            }
            else -> View(container.context)
        }
        container.addView(view)
        return view
    }

    override fun getCount(): Int = tabTitles.size

    override fun isViewFromObject(view: View, `object`: Any): Boolean = view == `object`

    override fun getPageTitle(position: Int): CharSequence = tabTitles[position]

    override fun destroyItem(container: android.view.ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    private fun setupBluetoothView() {
        btListView = bluetoothView.findViewById(R.id.listView)
        btAdapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, btDisplayList)
        btListView.adapter = btAdapter
        val refreshBtn = bluetoothView.findViewById<Button>(R.id.refreshButton)
        refreshBtn.setOnClickListener { startBluetoothScan() }
        val diagnoseBtn = bluetoothView.findViewById<Button>(R.id.diagnoseButton)
        diagnoseBtn.setOnClickListener {
            val pos = btListView.checkedItemPosition
            if (pos == ListView.INVALID_POSITION) {
                Toast.makeText(activity, "Select a Bluetooth device first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val device = btDevices[pos]
            runBluetoothDiagnosis(device)
        }
        btListView.choiceMode = ListView.CHOICE_MODE_SINGLE
    }

    private fun setupWifiView() {
        wifiListView = wifiView.findViewById(R.id.listView)
        wifiAdapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, wifiDisplayList)
        wifiListView.adapter = wifiAdapter
        val refreshBtn = wifiView.findViewById<Button>(R.id.refreshButton)
        refreshBtn.setOnClickListener { startWifiScan() }
        val diagnoseBtn = wifiView.findViewById<Button>(R.id.diagnoseButton)
        diagnoseBtn.setOnClickListener {
            val pos = wifiListView.checkedItemPosition
            if (pos == ListView.INVALID_POSITION) {
                Toast.makeText(activity, "Select a WiFi network first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val result = wifiScanResults[pos]
            runWifiDiagnosis(result)
        }
        wifiListView.choiceMode = ListView.CHOICE_MODE_SINGLE
    }

    private fun setupNetworkView() {
        networkListView = networkView.findViewById(R.id.listView)
        networkAdapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, networkDisplayList)
        networkListView.adapter = networkAdapter
        val refreshBtn = networkView.findViewById<Button>(R.id.refreshButton)
        refreshBtn.setOnClickListener { startNetworkScan() }
        val diagnoseBtn = networkView.findViewById<Button>(R.id.diagnoseButton)
        diagnoseBtn.setOnClickListener {
            val pos = networkListView.checkedItemPosition
            if (pos == ListView.INVALID_POSITION) {
                Toast.makeText(activity, "Select a network device first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val device = networkDevices[pos]
            runNetworkDiagnosis(device)
        }
        networkListView.choiceMode = ListView.CHOICE_MODE_SINGLE
    }

    private fun setupSerialView() {
        serialListView = serialView.findViewById(R.id.listView)
        serialAdapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, serialDisplayList)
        serialListView.adapter = serialAdapter
        val refreshBtn = serialView.findViewById<Button>(R.id.refreshButton)
        refreshBtn.setOnClickListener { listSerialDevices() }
        val diagnoseBtn = serialView.findViewById<Button>(R.id.diagnoseButton)
        diagnoseBtn.setOnClickListener {
            Toast.makeText(activity, "Serial device diagnosis coming soon", Toast.LENGTH_SHORT).show()
            activity.logEvent("Serial diagnosis requested (feature in development)")
        }
        serialListView.choiceMode = ListView.CHOICE_MODE_SINGLE
    }

    fun startBluetoothScan() {
        if (!activity.bluetoothAdapter.isEnabled) {
            Toast.makeText(activity, "Bluetooth is disabled", Toast.LENGTH_LONG).show()
            return
        }
        
        Toast.makeText(activity, "Scanning Bluetooth devices...", Toast.LENGTH_SHORT).show()
        activity.logEvent("Starting Bluetooth device scan")
        btDevices.clear()
        btDisplayList.clear()
        btAdapter.notifyDataSetChanged()
        
        if (activity.bluetoothAdapter.isDiscovering) {
            activity.bluetoothAdapter.cancelDiscovery()
        }
        
        activity.bluetoothAdapter.startDiscovery()
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        activity.registerReceiver(btReceiver, filter)
        
        // Add bonded devices immediately
        activity.bluetoothAdapter.bondedDevices.forEach { device ->
            addBluetoothDevice(device, true)
        }
    }

    private fun addBluetoothDevice(device: BluetoothDevice, isPaired: Boolean = false) {
        if (!btDevices.contains(device)) {
            btDevices.add(device)
            
            ioScope.launch {
                val cacheKey = device.name ?: device.address
                val cachedDevice = activity.deviceCache[cacheKey]
                
                val vendor = cachedDevice?.vendor ?: activity.lookupOnline(device.address.replace(":", ""))
                val category = cachedDevice?.category ?: activity.categorizeDevice(device.name ?: "", vendor)
                val action = activity.suggestAction(device.name ?: "Unknown", category)
                val compat = cachedDevice?.compatibility ?: activity.checkCompatibility(device.name ?: "", category)
                
                val deviceInfo = MainActivity.DeviceInfo(
                    name = device.name ?: "Unknown Device",
                    address = device.address,
                    vendor = vendor,
                    category = category,
                    compatibility = compat,
                    isActive = isPaired
                )
                
                activity.deviceCache[cacheKey] = deviceInfo
                activity.saveCache()
                
                val pairedStatus = if (isPaired) " [PAIRED]" else ""
                val displayText = "${device.name ?: "Unknown"}$pairedStatus\n" +
                        "${device.address} - $vendor\n" +
                        "Category: $category | Action: $action"
                
                mainScope.launch {
                    btDisplayList.add(displayText)
                    btAdapter.notifyDataSetChanged()
                }
                
                activity.logDebug("Added Bluetooth device: ${device.name} [${device.address}] - $category")
            }
        }
    }

    val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let { addBluetoothDevice(it, false) }
            }
        }
    }

    fun startWifiScan() {
        if (!activity.wifiManager.isWifiEnabled) {
            Toast.makeText(activity, "WiFi is disabled", Toast.LENGTH_LONG).show()
            return
        }
        
        Toast.makeText(activity, "Scanning WiFi networks...", Toast.LENGTH_SHORT).show()
        activity.logEvent("Starting WiFi network scan")
        
        wifiScanResults.clear()
        wifiDisplayList.clear()
        wifiAdapter.notifyDataSetChanged()
        
        val success = activity.wifiManager.startScan()
        if (!success) {
            Toast.makeText(activity, "WiFi scan failed", Toast.LENGTH_SHORT).show()
            activity.logEvent("WiFi scan failed to start")
            return
        }
        
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        activity.registerReceiver(wifiReceiver, filter)
    }

    val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                try {
                    activity.unregisterReceiver(this)
                } catch (e: Exception) {
                    // Already unregistered
                }
                
                val results = activity.wifiManager.scanResults
                wifiScanResults.addAll(results)
                wifiDisplayList.clear()
                
                results.forEach { result ->
                    val security = when {
                        result.capabilities.contains("WPA3") -> "WPA3"
                        result.capabilities.contains("WPA2") -> "WPA2"
                        result.capabilities.contains("WPA") -> "WPA"
                        result.capabilities.contains("WEP") -> "WEP"
                        else -> "Open"
                    }
                    
                    val signalStrength = when {
                        result.level > -50 -> "Excellent"
                        result.level > -60 -> "Good"
                        result.level > -70 -> "Fair"
                        else -> "Weak"
                    }
                    
                    val displayText = "${result.SSID}\n" +
                        "Signal: ${result.level}dBm ($signalStrength)\n" +
                        "Security: $security | Freq: ${result.frequency}MHz"
                    
                    wifiDisplayList.add(displayText)
                }
                
                wifiAdapter.notifyDataSetChanged()
                activity.logEvent("WiFi scan completed: ${results.size} networks found")
            }
        }
    }

    fun startNetworkScan() {
        Toast.makeText(activity, "Scanning network devices...", Toast.LENGTH_SHORT).show()
        activity.logEvent("Starting network device scan")
        
        networkDevices.clear()
        networkDisplayList.clear()
        networkAdapter.notifyDataSetChanged()
        
        ioScope.launch {
            // Scan common network ranges
            MainActivity.COMMON_NETWORK_RANGES.forEach { range ->
                scanNetworkRange(range)
            }
            
            mainScope.launch {
                Toast.makeText(activity, "Network scan completed", Toast.LENGTH_SHORT).show()
                activity.logEvent("Network scan completed: ${networkDevices.size} devices found")
            }
        }
    }

    private suspend fun scanNetworkRange(cidr: String) {
        try {
            val (baseIp, maskBits) = cidr.split("/")
            val ipParts = baseIp.split(".").map { it.toInt() }
            val maxHosts = (1 shl (32 - maskBits.toInt())) - 2
            
            // Limit scan to reasonable range
            val scanLimit = minOf(maxHosts, 254)
            
            for (i in 1..scanLimit) {
                val testIp = "${ipParts[0]}.${ipParts[1]}.${ipParts[2]}.$i"
                
                // Try to connect to a common port
                val reachable = withContext(Dispatchers.IO) {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(testIp, 80), 300)
                        socket.close()
                        true
                    } catch (e: Exception) {
                        try {
                            val socket = Socket()
                            socket.connect(InetSocketAddress(testIp, 443), 300)
                            socket.close()
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }
                }
                
                if (reachable) {
                    activity.logDebug("Found reachable host: $testIp")
                    
                    ioScope.launch {
                        // Get open ports
                        val ports = activity.performPortScan(testIp)
                        
                        // Try to determine device type from open ports
                        val category = when {
                            ports.contains(21) -> "file server"
                            ports.contains(22) -> "network device"
                            ports.contains(23) -> "network device"
                            ports.contains(80) -> "web server"
                            ports.contains(443) -> "web server"
                            ports.contains(515) -> "printer"
                            ports.contains(631) -> "printer"
                            else -> "unknown device"
                        }
                        
                        val deviceInfo = MainActivity.DeviceInfo(
                            name = "$category at $testIp",
                            address = testIp,
                            category = category,
                            ports = ports,
                            services = ports.map { "Port $it" }
                        )
                        
                        networkDevices.add(deviceInfo)
                        
                        val displayText = "$testIp\n" +
                                "Type: $category\n" +
                                "Open ports: ${ports.joinToString(", ")}"
                        
                        mainScope.launch {
                            networkDisplayList.add(displayText)
                            networkAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            activity.logDebug("Error scanning range $cidr: ${e.message}")
        }
    }

    fun listSerialDevices() {
        Toast.makeText(activity, "Scanning serial devices...", Toast.LENGTH_SHORT).show()
        activity.logEvent("Scanning for serial devices")
        
        serialDisplayList.clear()
        serialAdapter.notifyDataSetChanged()
        
        ioScope.launch {
            // Look for common serial device paths
            val serialPaths = listOf(
                "/dev/ttyUSB0", "/dev/ttyUSB1", "/dev/ttyACM0",
                "/dev/ttyS0", "/dev/ttyS1", "/dev/ttyS2"
            )
            
            serialPaths.forEach { path ->
                val file = java.io.File(path)
                if (file.exists()) {
                    activity.logDebug("Found potential serial device: $path")
                    
                    mainScope.launch {
                        serialDisplayList.add("$path\nSerial Device")
                        serialAdapter.notifyDataSetChanged()
                    }
                }
            }
            
            // Also check USB devices
            try {
                val usbManager = activity.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
                val deviceList = usbManager.deviceList
                
                deviceList.forEach { (name, device) ->
                    activity.logDebug("Found USB device: $name")
                    
                    mainScope.launch {
                        serialDisplayList.add("USB Device: $name\nVendorID: ${device.vendorId}, ProductID: ${device.productId}")
                        serialAdapter.notifyDataSetChanged()
                    }
                }
                
                if (deviceList.isEmpty() && serialDisplayList.isEmpty()) {
                    mainScope.launch {
                        serialDisplayList.add("No serial or USB devices found")
                        serialAdapter.notifyDataSetChanged()
                    }
                }
            } catch (e: Exception) {
                activity.logDebug("Error listing USB devices: ${e.message}")
                
                if (serialDisplayList.isEmpty()) {
                    mainScope.launch {
                        serialDisplayList.add("Error scanning for devices")
                        serialAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun runBluetoothDiagnosis(device: BluetoothDevice) {
        Toast.makeText(activity, "Running diagnosis on ${device.name}", Toast.LENGTH_SHORT).show()
        activity.logEvent("Running Bluetooth diagnosis on device: ${device.name}")
        
        val results = StringBuilder()
        results.append("DIAGNOSIS REPORT\n")
        results.append("Device: ${device.name ?: "Unknown"}\n")
        results.append("Address: ${device.address}\n")
        results.append("Type: ${getBtDeviceType(device)}\n")
        
        val paired = activity.bluetoothAdapter.bondedDevices.contains(device)
        results.append("Paired: ${if (paired) "Yes" else "No"}\n")
        
        val cacheKey = device.name ?: device.address
        val cachedDevice = activity.deviceCache[cacheKey]
        
        if (cachedDevice != null) {
            results.append("Vendor: ${cachedDevice.vendor}\n")
            results.append("Category: ${cachedDevice.category}\n")
            results.append("Compatibility: ${cachedDevice.compatibility}\n")
            results.append("Last seen: ${getTimeAgo(cachedDevice.lastSeen)}\n")
        }
        
        // Show diagnosis
        AlertDialog.Builder(activity)
            .setTitle("Bluetooth Device Diagnosis")
            .setMessage(results.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun getBtDeviceType(device: BluetoothDevice): String {
        return when (device.bluetoothClass.majorDeviceClass) {
            android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio/Video Device"
            android.bluetooth.BluetoothClass.Device.Major.COMPUTER -> "Computer"
            android.bluetooth.BluetoothClass.Device.Major.HEALTH -> "Health Device"
            android.bluetooth.BluetoothClass.Device.Major.IMAGING -> "Imaging Device"
            android.bluetooth.BluetoothClass.Device.Major.MISC -> "Miscellaneous"
            android.bluetooth.BluetoothClass.Device.Major.NETWORKING -> "Networking Device"
            android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral Device"
            android.bluetooth.BluetoothClass.Device.Major.PHONE -> "Phone"
            android.bluetooth.BluetoothClass.Device.Major.TOY -> "Toy"
            android.bluetooth.BluetoothClass.Device.Major.WEARABLE -> "Wearable Device"
            else -> "Unknown Type"
        }
    }

    private fun runWifiDiagnosis(result: ScanResult) {
        Toast.makeText(activity, "Running diagnosis on ${result.SSID}", Toast.LENGTH_SHORT).show()
        activity.logEvent("Running WiFi diagnosis on network: ${result.SSID}")
        
        val results = StringBuilder()
        results.append("DIAGNOSIS REPORT\n")
        results.append("Network: ${result.SSID}\n")
        results.append("BSSID: ${result.BSSID}\n")
        
        val signalStrength = when {
            result.level > -50 -> "Excellent"
            result.level > -60 -> "Good"
            result.level > -70 -> "Fair"
            else -> "Weak"
        }
        
        results.append("Signal: ${result.level}dBm ($signalStrength)\n")
        
        val security = when {
            result.capabilities.contains("WPA3") -> "WPA3 (High Security)"
            result.capabilities.contains("WPA2") -> "WPA2 (Good Security)"
            result.capabilities.contains("WPA") -> "WPA (Moderate Security)"
            result.capabilities.contains("WEP") -> "WEP (Low Security - Vulnerable)"
            else -> "Open (No Security)"
        }
        
        results.append("Security: $security\n")
        results.append("Frequency: ${result.frequency}MHz\n")
        
        if (result.frequency > 5000) {
            results.append("Band: 5GHz (Faster, shorter range)\n")
        } else {
            results.append("Band: 2.4GHz (Better range, more interference)\n")
        }
        
        // Show diagnosis
        AlertDialog.Builder(activity)
            .setTitle("WiFi Network Diagnosis")
            .setMessage(results.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun runNetworkDiagnosis(device: MainActivity.DeviceInfo) {
        Toast.makeText(activity, "Running diagnosis on ${device.address}", Toast.LENGTH_SHORT).show()
        activity.logEvent("Running network diagnosis on device: ${device.address}")
        
        ioScope.launch {
            val results = StringBuilder()
            results.append("DIAGNOSIS REPORT\n")
            results.append("Device: ${device.name}\n")
            results.append("IP Address: ${device.address}\n")
            results.append("Category: ${device.category}\n")
            
            // Add port information
            results.append("Open Ports: ${device.ports.joinToString(", ")}\n")
            
            // Try to get hostname
            var hostname = "Unknown"
            try {
                val inetAddress = java.net.InetAddress.getByName(device.address)
                hostname = inetAddress.hostName
                if (hostname != device.address) {
                    results.append("Hostname: $hostname\n")
                }
            } catch (e: Exception) {
                // Could not resolve hostname
            }
            
            // Check common services
            val serviceMap = mapOf(
                21 to "FTP",
                22 to "SSH",
                23 to "Telnet",
                25 to "SMTP",
                53 to "DNS",
                80 to "HTTP",
                443 to "HTTPS",
                515 to "LPD (Printer)",
                631 to "IPP (Printer)",
                3389 to "RDP",
                8080 to "HTTP-Alt",
                8443 to "HTTPS-Alt"
            )
            
            val detectedServices = mutableListOf<String>()
            device.ports.forEach { port ->
                val service = serviceMap[port] ?: "Unknown Service"
                detectedServices.add("$service (Port $port)")
            }
            
            if (detectedServices.isNotEmpty()) {
                results.append("Detected Services:\n")
                detectedServices.forEach { service ->
                    results.append("- $service\n")
                }
            }
            
            // Show diagnosis on UI thread
            mainScope.launch {
                AlertDialog.Builder(activity)
                    .setTitle("Network Device Diagnosis")
                    .setMessage(results.toString())
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000} minutes ago"
            diff < 86400000 -> "${diff / 3600000} hours ago"
            else -> "${diff / 86400000} days ago"
        }
    }
}
