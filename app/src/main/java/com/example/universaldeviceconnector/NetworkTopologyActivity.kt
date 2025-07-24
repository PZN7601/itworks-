package com.example.universaldeviceconnector

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class NetworkTopologyActivity : AppCompatActivity() {
    
    private lateinit var topologyView: NetworkTopologyView
    private var devices: List<MainActivity.DeviceInfo> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_topology)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Network Topology"
        
        topologyView = findViewById(R.id.topologyView)
        
        // Get devices from intent
        @Suppress("DEPRECATION")
        devices = intent.getSerializableExtra("devices") as? List<MainActivity.DeviceInfo> ?: emptyList()
        
        setupTopologyView()
        loadDevices()
    }
    
    private fun setupTopologyView() {
        topologyView.setOnNodeClickListener { node ->
            showDeviceDetails(node)
        }
    }
    
    private fun loadDevices() {
        if (devices.isNotEmpty()) {
            topologyView.setDevices(devices)
            Toast.makeText(this, "Loaded ${devices.size} devices", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No devices to display", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showDeviceDetails(node: NetworkTopologyView.NetworkNode) {
        val device = node.device
        
        val details = StringBuilder().apply {
            append("Device Details\n\n")
            append("Name: ${device.name}\n")
            append("Address: ${device.address}\n")
            append("Vendor: ${device.vendor}\n")
            append("Category: ${device.category}\n")
            append("Compatibility: ${device.compatibility}\n")
            
            if (device.ports.isNotEmpty()) {
                append("Open Ports: ${device.ports.joinToString(", ")}\n")
            }
            
            if (device.services.isNotEmpty()) {
                append("Services: ${device.services.joinToString(", ")}\n")
            }
            
            if (node.isGateway) {
                append("\n🌐 Gateway Device")
            }
            
            append("\nLast Seen: ${getTimeAgo(device.lastSeen)}")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Device Information")
            .setMessage(details.toString())
            .setPositiveButton("Close", null)
            .setNeutralButton("Highlight") { _, _ ->
                topologyView.highlightDevice(device.address)
            }
            .show()
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
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.topology_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_reset_view -> {
                topologyView.resetView()
                Toast.makeText(this, "View reset", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_refresh -> {
                loadDevices()
                true
            }
            R.id.action_legend -> {
                showLegend()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showLegend() {
        val legendText = """
            Network Topology Legend
            
            📍 Node Types:
            • Router/Gateway: Red circle with gold border
            • Printer: Green circle
            • Camera: Blue circle
            • Computer: Purple circle
            • Phone/Mobile: Orange circle
            • IoT Device: Brown circle
            • Unknown: Gray circle
            
            🔗 Connection Types:
            • Solid line: Ethernet connection
            • Dashed green: WiFi connection
            • Dashed blue: Bluetooth connection
            • Dotted gray: Unknown connection
            
            🎮 Controls:
            • Tap: Select device
            • Double tap: Reset zoom
            • Pinch: Zoom in/out
            • Drag: Pan around
            
            💡 Features:
            • Devices automatically arrange based on network topology
            • Gateway devices are positioned centrally
            • Connection strength affects line opacity
            • Real-time physics simulation for optimal layout
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("How to Use")
            .setMessage(legendText)
            .setPositiveButton("Got it", null)
            .show()
    }
}
