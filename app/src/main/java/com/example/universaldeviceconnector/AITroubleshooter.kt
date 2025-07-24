package com.example.universaldeviceconnector

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class AITroubleshooter(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // AI Knowledge Base
    private val troubleshootingRules = mapOf(
        "printer_offline" to listOf(
            "Check power cable connection",
            "Verify network connectivity",
            "Restart print spooler service",
            "Update printer drivers",
            "Check for paper jams"
        ),
        "router_slow" to listOf(
            "Check bandwidth usage",
            "Restart router",
            "Update firmware",
            "Check for interference",
            "Optimize channel settings"
        ),
        "camera_no_feed" to listOf(
            "Check power supply",
            "Verify network connection",
            "Check camera settings",
            "Restart camera service",
            "Update firmware"
        ),
        "device_unreachable" to listOf(
            "Ping device to test connectivity",
            "Check network cables",
            "Verify IP configuration",
            "Check firewall settings",
            "Restart network services"
        )
    )
    
    private val commonIssues = mapOf(
        "high_latency" to "Network latency above 100ms detected",
        "port_closed" to "Expected service port is closed",
        "authentication_failed" to "Device authentication failed",
        "service_down" to "Critical service not responding",
        "low_resources" to "Device resources running low"
    )
    
    fun analyzeDeviceIssues(device: MainActivity.DeviceInfo): AIAnalysisResult {
        val issues = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        val severity = determineSeverity(device)
        
        // Analyze based on device category
        when (device.category.lowercase()) {
            "printer" -> analyzePrinterIssues(device, issues, recommendations)
            "router" -> analyzeRouterIssues(device, issues, recommendations)
            "camera" -> analyzeCameraIssues(device, issues, recommendations)
            else -> analyzeGenericIssues(device, issues, recommendations)
        }
        
        // Check common network issues
        analyzeNetworkIssues(device, issues, recommendations)
        
        return AIAnalysisResult(
            deviceName = device.name,
            deviceAddress = device.address,
            issues = issues,
            recommendations = recommendations,
            severity = severity,
            confidence = calculateConfidence(issues),
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun analyzePrinterIssues(device: MainActivity.DeviceInfo, issues: MutableList<String>, recommendations: MutableList<String>) {
        if (!device.ports.contains(631) && !device.ports.contains(515)) {
            issues.add("Printer service ports not accessible")
            recommendations.addAll(troubleshootingRules["printer_offline"] ?: emptyList())
        }
        
        if (device.healthStatus == "Unknown") {
            issues.add("Unable to determine printer status")
            recommendations.add("Check SNMP configuration")
            recommendations.add("Verify printer web interface")
        }
    }
    
    private fun analyzeRouterIssues(device: MainActivity.DeviceInfo, issues: MutableList<String>, recommendations: MutableList<String>) {
        if (!device.ports.contains(80) && !device.ports.contains(443)) {
            issues.add("Router web interface not accessible")
            recommendations.add("Check router configuration")
            recommendations.add("Verify admin credentials")
        }
        
        if (!device.ports.contains(53)) {
            issues.add("DNS service not detected")
            recommendations.add("Check DNS configuration")
            recommendations.add("Verify DNS forwarding settings")
        }
    }
    
    private fun analyzeCameraIssues(device: MainActivity.DeviceInfo, issues: MutableList<String>, recommendations: MutableList<String>) {
        if (!device.ports.contains(80) && !device.ports.contains(554)) {
            issues.add("Camera streaming ports not accessible")
            recommendations.addAll(troubleshootingRules["camera_no_feed"] ?: emptyList())
        }
        
        if (device.vendor.lowercase().contains("unknown")) {
            issues.add("Camera vendor not identified")
            recommendations.add("Check camera documentation")
            recommendations.add("Verify camera model and firmware")
        }
    }
    
    private fun analyzeGenericIssues(device: MainActivity.DeviceInfo, issues: MutableList<String>, recommendations: MutableList<String>) {
        if (device.ports.isEmpty()) {
            issues.add("No open ports detected")
            recommendations.addAll(troubleshootingRules["device_unreachable"] ?: emptyList())
        }
        
        if (device.vendor == "Unknown") {
            issues.add("Device vendor not identified")
            recommendations.add("Perform MAC address lookup")
            recommendations.add("Check device documentation")
        }
    }
    
    private fun analyzeNetworkIssues(device: MainActivity.DeviceInfo, issues: MutableList<String>, recommendations: MutableList<String>) {
        val lastSeenHours = (System.currentTimeMillis() - device.lastSeen) / (1000 * 60 * 60)
        
        if (lastSeenHours > 24) {
            issues.add("Device not seen for ${lastSeenHours} hours")
            recommendations.add("Check device power status")
            recommendations.add("Verify network connectivity")
        }
        
        if (!device.isActive) {
            issues.add("Device appears to be inactive")
            recommendations.add("Attempt to ping device")
            recommendations.add("Check device status indicators")
        }
    }
    
    private fun determineSeverity(device: MainActivity.DeviceInfo): String {
        return when {
            device.category.lowercase() in listOf("router", "firewall") -> "HIGH"
            device.category.lowercase() in listOf("server", "database") -> "HIGH"
            device.category.lowercase() in listOf("printer", "camera") -> "MEDIUM"
            else -> "LOW"
        }
    }
    
    private fun calculateConfidence(issues: List<String>): Int {
        return when {
            issues.isEmpty() -> 95
            issues.size <= 2 -> 85
            issues.size <= 4 -> 70
            else -> 60
        }
    }
    
    fun generateAutomatedFix(device: MainActivity.DeviceInfo, issue: String): AutomatedFix {
        val commands = mutableListOf<String>()
        val description = StringBuilder()
        
        when (issue.lowercase()) {
            "printer service ports not accessible" -> {
                commands.add("ping ${device.address}")
                commands.add("telnet ${device.address} 631")
                commands.add("snmpwalk -v2c -c public ${device.address}")
                description.append("Automated printer connectivity test and service verification")
            }
            "router web interface not accessible" -> {
                commands.add("ping ${device.address}")
                commands.add("curl -I http://${device.address}")
                commands.add("nmap -p 80,443 ${device.address}")
                description.append("Automated router interface accessibility check")
            }
            "device not seen for" -> {
                commands.add("ping -c 4 ${device.address}")
                commands.add("arping ${device.address}")
                commands.add("nmap -sn ${device.address}")
                description.append("Automated device reachability test")
            }
            else -> {
                commands.add("ping ${device.address}")
                description.append("Basic connectivity test")
            }
        }
        
        return AutomatedFix(
            issue = issue,
            description = description.toString(),
            commands = commands,
            estimatedTime = commands.size * 5, // 5 seconds per command
            riskLevel = "LOW"
        )
    }
    
    suspend fun executeAutomatedFix(fix: AutomatedFix, device: MainActivity.DeviceInfo): FixResult {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<String>()
            var success = true
            
            try {
                fix.commands.forEach { command ->
                    delay(1000) // Simulate command execution time
                    val result = simulateCommandExecution(command, device)
                    results.add("$command: $result")
                    
                    if (result.contains("failed", ignoreCase = true)) {
                        success = false
                    }
                }
                
                FixResult(
                    success = success,
                    results = results,
                    message = if (success) "Automated fix completed successfully" else "Some commands failed",
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                FixResult(
                    success = false,
                    results = results,
                    message = "Fix execution failed: ${e.message}",
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }
    
    private fun simulateCommandExecution(command: String, device: MainActivity.DeviceInfo): String {
        return when {
            command.startsWith("ping") -> {
                if (device.isActive) "PING successful - 4 packets transmitted, 4 received"
                else "PING failed - Host unreachable"
            }
            command.startsWith("telnet") -> {
                val port = command.split(" ").lastOrNull()?.toIntOrNull()
                if (port != null && device.ports.contains(port)) {
                    "Connection successful to port $port"
                } else {
                    "Connection failed - Port closed or filtered"
                }
            }
            command.startsWith("curl") -> {
                if (device.ports.contains(80) || device.ports.contains(443)) {
                    "HTTP/1.1 200 OK - Web interface accessible"
                } else {
                    "Connection failed - Web interface not available"
                }
            }
            command.startsWith("nmap") -> {
                "Nmap scan completed - ${device.ports.size} open ports found"
            }
            command.startsWith("snmpwalk") -> {
                if (device.ports.contains(161)) {
                    "SNMP walk successful - Device responding"
                } else {
                    "SNMP failed - Service not available"
                }
            }
            else -> "Command executed successfully"
        }
    }
    
    fun generateNetworkInsights(devices: List<MainActivity.DeviceInfo>): JSONObject {
        val insights = JSONObject()
        
        // Network topology analysis
        val subnets = devices.groupBy { getSubnet(it.address) }
        insights.put("subnet_count", subnets.size)
        insights.put("total_devices", devices.size)
        
        // Device category distribution
        val categoryDistribution = devices.groupBy { it.category }.mapValues { it.value.size }
        insights.put("device_categories", JSONObject(categoryDistribution))
        
        // Health analysis
        val healthyDevices = devices.count { it.healthStatus != "Unknown" && it.isActive }
        val healthPercentage = if (devices.isNotEmpty()) (healthyDevices * 100) / devices.size else 0
        insights.put("network_health_percentage", healthPercentage)
        
        // Security analysis
        val unsecureDevices = devices.count { device ->
            device.ports.any { it in listOf(23, 21, 80) } && !device.ports.contains(443)
        }
        insights.put("security_risk_devices", unsecureDevices)
        
        // Recommendations
        val recommendations = JSONArray()
        if (healthPercentage < 80) {
            recommendations.put("Consider investigating devices with unknown health status")
        }
        if (unsecureDevices > 0) {
            recommendations.put("$unsecureDevices devices may have security vulnerabilities")
        }
        if (subnets.size > 5) {
            recommendations.put("Large number of subnets detected - consider network segmentation review")
        }
        
        insights.put("ai_recommendations", recommendations)
        insights.put("analysis_timestamp", System.currentTimeMillis())
        
        return insights
    }
    
    private fun getSubnet(ipAddress: String): String {
        return try {
            val parts = ipAddress.split(".")
            if (parts.size >= 3) {
                "${parts[0]}.${parts[1]}.${parts[2]}"
            } else {
                "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    data class AIAnalysisResult(
        val deviceName: String,
        val deviceAddress: String,
        val issues: List<String>,
        val recommendations: List<String>,
        val severity: String,
        val confidence: Int,
        val timestamp: Long
    )
    
    data class AutomatedFix(
        val issue: String,
        val description: String,
        val commands: List<String>,
        val estimatedTime: Int,
        val riskLevel: String
    )
    
    data class FixResult(
        val success: Boolean,
        val results: List<String>,
        val message: String,
        val timestamp: Long
    )
}
