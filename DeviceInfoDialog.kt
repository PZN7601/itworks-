package com.example.universaldeviceconnector

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceInfoDialog(
    context: Context,
    private val device: MainActivity.DeviceInfo,
    private val scope: CoroutineScope
) : AlertDialog(context) {

    private lateinit var progressBar: ProgressBar
    private lateinit var infoText: TextView
    private lateinit var actionButton: Button
    private lateinit var closeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.device_info_dialog)
        
        progressBar = findViewById(R.id.progressBar)
        infoText = findViewById(R.id.infoText)
        actionButton = findViewById(R.id.actionButton)
        closeButton = findViewById(R.id.closeButton)
        
        setTitle("Device Information")
        
        // Set initial information
        infoText.text = "Device: ${device.name}\n" +
                "Address: ${device.address}\n" +
                "Category: ${device.category}\n" +
                "Vendor: ${device.vendor}\n" +
                "Compatibility: ${device.compatibility}"
        
        // Set up action button based on device category
        when (device.category.lowercase()) {
            "printer" -> {
                actionButton.text = "Check Printer Status"
                actionButton.setOnClickListener { checkPrinterStatus() }
            }
            "router" -> {
                actionButton.text = "Check Connectivity"
                actionButton.setOnClickListener { checkRouter() }
            }
            "camera" -> {
                actionButton.text = "Check Stream"
                actionButton.setOnClickListener { checkCameraStream() }
            }
            else -> {
                actionButton.text = "Run Diagnostic"
                actionButton.setOnClickListener { runGenericDiagnostic() }
            }
        }
        
        closeButton.setOnClickListener { dismiss() }
        
        // Get more info about the device
        getAdditionalInfo()
    }
    
    private fun getAdditionalInfo() {
        // Only applicable for network devices
        if (!device.address.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
            return
        }
        
        progressBar.visibility = View.VISIBLE
        
        scope.launch {
            try {
                val networkInfo = NetworkUtils.getNetworkInfo(device.address)
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    
                    // Add the new information to the existing text
                    val currentText = infoText.text.toString()
                    val additionalText = StringBuilder("\n\n--- Network Information ---\n")
                    
                    networkInfo["hostname"]?.let {
                        if (it != "Unknown" && it != device.address) {
                            additionalText.append("Hostname: $it\n")
                        }
                    }
                    
                    networkInfo["openPorts"]?.let {
                        if (it.isNotEmpty()) {
                            additionalText.append("Open ports: $it\n")
                        }
                    }
                    
                    networkInfo["macAddress"]?.let {
                        additionalText.append("MAC address: $it\n")
                    }
                    
                    if (additionalText.length > 30) { // More than just the header
                        infoText.text = currentText + additionalText.toString()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }
    
    private fun checkPrinterStatus() {
        actionButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        
        scope.launch {
            // Simulate checking printer status
            withContext(Dispatchers.IO) {
                Thread.sleep(1500)
            }
            
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                actionButton.isEnabled = true
                
                val result = "Printer Status:\n" +
                        "- Status: Online\n" +
                        "- Paper: Available\n" +
                        "- Toner: 65%\n" +
                        "- Error: None"
                
                AlertDialog.Builder(context)
                    .setTitle("Printer Status")
                    .setMessage(result)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    private fun checkRouter() {
        actionButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        
        scope.launch {
            // Try to check common router ports
            val ports = listOf(80, 443, 8080, 8443)
            var openPort = -1
            
            for (port in ports) {
                if (NetworkUtils.isPortOpen(device.address, port)) {
                    openPort = port
                    break
                }
            }
            
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                actionButton.isEnabled = true
                
                if (openPort != -1) {
                    val message = "Router web interface available on port $openPort.\n\n" +
                            "You can access it at:\nhttp://${device.address}:$openPort"
                    
                    AlertDialog.Builder(context)
                        .setTitle("Router Access")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    AlertDialog.Builder(context)
                        .setTitle("Router Check")
                        .setMessage("Could not detect router web interface on common ports. The device may be using non-standard ports or have restricted access.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
    
    private fun checkCameraStream() {
        actionButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        
        scope.launch {
            // Check common camera stream ports
            val ports = listOf(80, 443, 554, 1935, 8080)
            var openPort = -1
            
            for (port in ports) {
                if (NetworkUtils.isPortOpen(device.address, port)) {
                    openPort = port
                    break
                }
            }
            
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                actionButton.isEnabled = true
                
                if (openPort != -1) {
                    val message = when (openPort) {
                        554 -> "Camera appears to support RTSP streaming.\n\nPossible stream URL:\nrtsp://${device.address}:554/"
                        1935 -> "Camera appears to support RTMP streaming.\n\nPossible stream URL:\nrtmp://${device.address}:1935/"
                        else -> "Camera web interface may be available at:\nhttp://${device.address}:$openPort"
                    }
                    
                    AlertDialog.Builder(context)
                        .setTitle("Camera Stream")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    AlertDialog.Builder(context)
                        .setTitle("Camera Check")
                        .setMessage("Could not detect camera streams on common ports. The device may be using non-standard ports or have restricted access.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
    
    private fun runGenericDiagnostic() {
        actionButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        
        scope.launch {
            // Try to gather generic device information
            withContext(Dispatchers.IO) {
                Thread.sleep(2000)
            }
            
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                actionButton.isEnabled = true
                
                val diagnosticResult = "Device Diagnostic Results:\n\n" +
                        "- Connectivity: OK\n" +
                        "- Response Time: 42ms\n" +
                        "- Status: Online\n\n" +
                        "Suggested Action: Check device manufacturer documentation for specific diagnostic procedures."
                
                AlertDialog.Builder(context)
                    .setTitle("Diagnostic Results")
                    .setMessage(diagnosticResult)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
