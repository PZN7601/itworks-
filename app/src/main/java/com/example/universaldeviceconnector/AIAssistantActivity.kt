package com.example.universaldeviceconnector

import android.os.Bundle
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class AIAssistantActivity : AppCompatActivity() {
    
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var quickActionsLayout: LinearLayout
    
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    
    private lateinit var aiTroubleshooter: AITroubleshooter
    private lateinit var remoteManager: RemoteManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var devices: List<MainActivity.DeviceInfo> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_assistant)
        
        setupToolbar()
        initializeViews()
        setupRecyclerView()
        setupQuickActions()
        
        aiTroubleshooter = AITroubleshooter(this)
        remoteManager = RemoteManager(this)
        
        // Get devices from intent
        @Suppress("DEPRECATION")
        devices = intent.getSerializableExtra("devices") as? List<MainActivity.DeviceInfo> ?: emptyList()
        
        // Welcome message
        addMessage(ChatMessage("AI Assistant", "Hello! I'm your AI troubleshooting assistant. I can help you diagnose issues, suggest fixes, and even execute automated repairs. How can I help you today?", false))
    }
    
    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Troubleshooting Assistant"
        supportActionBar?.subtitle = "Powered by Local AI Engine"
    }
    
    private fun initializeViews() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        quickActionsLayout = findViewById(R.id.quickActionsLayout)
        
        sendButton.setOnClickListener {
            sendMessage()
        }
        
        messageInput.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(chatMessages)
        chatRecyclerView.adapter = chatAdapter
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
    }
    
    private fun setupQuickActions() {
        val quickActions = listOf(
            "Analyze all devices" to { analyzeAllDevices() },
            "Check network health" to { checkNetworkHealth() },
            "Find issues" to { findIssues() },
            "Suggest optimizations" to { suggestOptimizations() },
            "Run diagnostics" to { runDiagnostics() }
        )
        
        quickActions.forEach { (text, action) ->
            val button = Button(this).apply {
                this.text = text
                setBackgroundResource(R.drawable.quick_action_button)
                setTextColor(getColor(R.color.colorPrimary))
                setOnClickListener { action() }
            }
            quickActionsLayout.addView(button)
        }
    }
    
    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isNotEmpty()) {
            addMessage(ChatMessage("You", message, true))
            messageInput.text.clear()
            processUserMessage(message)
        }
    }
    
    private fun addMessage(message: ChatMessage) {
        chatMessages.add(message)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        chatRecyclerView.scrollToPosition(chatMessages.size - 1)
    }
    
    private fun processUserMessage(message: String) {
        scope.launch {
            try {
                // Show typing indicator
                addMessage(ChatMessage("AI Assistant", "🤔 Analyzing your request...", false))
                
                delay(1000) // Simulate processing time
                
                val response = generateAIResponse(message)
                
                // Remove typing indicator and add real response
                chatMessages.removeLastOrNull()
                chatAdapter.notifyItemRemoved(chatMessages.size)
                
                addMessage(ChatMessage("AI Assistant", response, false))
                
            } catch (e: Exception) {
                addMessage(ChatMessage("AI Assistant", "Sorry, I encountered an error: ${e.message}", false))
            }
        }
    }
    
    private suspend fun generateAIResponse(userMessage: String): String {
        return withContext(Dispatchers.IO) {
            val message = userMessage.lowercase()
            
            when {
                message.contains("analyze") || message.contains("check") -> {
                    if (message.contains("all") || message.contains("network")) {
                        analyzeNetworkAndGenerateResponse()
                    } else {
                        "I can analyze your network devices. Would you like me to check all devices or a specific one?"
                    }
                }
                
                message.contains("fix") || message.contains("repair") -> {
                    generateFixSuggestions()
                }
                
                message.contains("issue") || message.contains("problem") -> {
                    findAndReportIssues()
                }
                
                message.contains("optimize") || message.contains("improve") -> {
                    generateOptimizationSuggestions()
                }
                
                message.contains("help") -> {
                    """
                    I can help you with:
                    
                    🔍 **Device Analysis**: Analyze individual devices or your entire network
                    🛠️ **Issue Detection**: Find problems and suggest solutions
                    ⚡ **Automated Fixes**: Execute repairs automatically (with your permission)
                    📊 **Network Optimization**: Suggest improvements for better performance
                    🔧 **Remote Management**: Help with remote device access and control
                    
                    Just ask me something like:
                    • "Analyze all my devices"
                    • "Fix the printer issues"
                    • "Check network health"
                    • "Optimize my network"
                    """.trimIndent()
                }
                
                message.contains("printer") -> {
                    analyzePrinterDevices()
                }
                
                message.contains("router") -> {
                    analyzeRouterDevices()
                }
                
                message.contains("camera") -> {
                    analyzeCameraDevices()
                }
                
                else -> {
                    "I understand you're asking about: \"$userMessage\"\n\nCould you be more specific? I can help with device analysis, troubleshooting, fixes, and optimizations. Try asking something like 'analyze my network' or 'find issues'."
                }
            }
        }
    }
    
    private fun analyzeNetworkAndGenerateResponse(): String {
        val insights = aiTroubleshooter.generateNetworkInsights(devices)
        
        return """
        📊 **Network Analysis Complete**
        
        **Overview:**
        • Total Devices: ${insights.getInt("total_devices")}
        • Subnets: ${insights.getInt("subnet_count")}
        • Network Health: ${insights.getInt("network_health_percentage")}%
        
        **Device Categories:**
        ${formatDeviceCategories(insights.getJSONObject("device_categories"))}
        
        **Security Status:**
        • Devices with security risks: ${insights.getInt("security_risk_devices")}
        
        **AI Recommendations:**
        ${formatRecommendations(insights.getJSONArray("ai_recommendations"))}
        
        Would you like me to analyze specific devices or suggest automated fixes?
        """.trimIndent()
    }
    
    private fun generateFixSuggestions(): String {
        val issueDevices = devices.filter { device ->
            !device.isActive || device.healthStatus == "Unknown" || device.ports.isEmpty()
        }
        
        if (issueDevices.isEmpty()) {
            return "🎉 Great news! I didn't find any obvious issues that need fixing. Your network appears to be healthy!"
        }
        
        val suggestions = StringBuilder("🔧 **Fix Suggestions:**\n\n")
        
        issueDevices.take(5).forEach { device ->
            val analysis = aiTroubleshooter.analyzeDeviceIssues(device)
            suggestions.append("**${device.name}** (${device.address}):\n")
            analysis.issues.forEach { issue ->
                suggestions.append("• Issue: $issue\n")
            }
            analysis.recommendations.take(2).forEach { rec ->
                suggestions.append("  → $rec\n")
            }
            suggestions.append("\n")
        }
        
        suggestions.append("Would you like me to execute automated fixes for any of these devices?")
        
        return suggestions.toString()
    }
    
    private fun findAndReportIssues(): String {
        val issues = mutableListOf<String>()
        
        devices.forEach { device ->
            val analysis = aiTroubleshooter.analyzeDeviceIssues(device)
            if (analysis.issues.isNotEmpty()) {
                issues.add("${device.name}: ${analysis.issues.joinToString(", ")}")
            }
        }
        
        return if (issues.isEmpty()) {
            "✅ **No Critical Issues Found**\n\nYour network devices appear to be functioning normally. All devices are responding and services are accessible."
        } else {
            "⚠️ **Issues Detected:**\n\n${issues.joinToString("\n\n")}\n\nWould you like me to suggest fixes for these issues?"
        }
    }
    
    private fun generateOptimizationSuggestions(): String {
        return """
        🚀 **Network Optimization Suggestions:**
        
        **Performance:**
        • Consider upgrading devices with high latency
        • Implement QoS policies for critical devices
        • Monitor bandwidth usage patterns
        
        **Security:**
        • Enable HTTPS on web interfaces where possible
        • Disable unnecessary services (Telnet, HTTP)
        • Implement network segmentation
        
        **Management:**
        • Set up SNMP monitoring for critical devices
        • Configure automated backups
        • Implement centralized logging
        
        **Reliability:**
        • Check for firmware updates
        • Monitor device health metrics
        • Set up redundancy for critical services
        
        Would you like me to help implement any of these optimizations?
        """.trimIndent()
    }
    
    private fun analyzePrinterDevices(): String {
        val printers = devices.filter { it.category.lowercase().contains("printer") }
        
        if (printers.isEmpty()) {
            return "I didn't find any printers in your network. Would you like me to scan for print services?"
        }
        
        val analysis = StringBuilder("🖨️ **Printer Analysis:**\n\n")
        
        printers.forEach { printer ->
            val deviceAnalysis = aiTroubleshooter.analyzeDeviceIssues(printer)
            analysis.append("**${printer.name}**\n")
            analysis.append("• Address: ${printer.address}\n")
            analysis.append("• Status: ${if (printer.isActive) "Online" else "Offline"}\n")
            analysis.append("• Print Services: ${if (printer.ports.contains(631)) "IPP ✓" else ""}${if (printer.ports.contains(515)) " LPD ✓" else ""}\n")
            
            if (deviceAnalysis.issues.isNotEmpty()) {
                analysis.append("• Issues: ${deviceAnalysis.issues.joinToString(", ")}\n")
            }
            analysis.append("\n")
        }
        
        return analysis.toString()
    }
    
    private fun analyzeRouterDevices(): String {
        val routers = devices.filter { it.category.lowercase().contains("router") }
        
        if (routers.isEmpty()) {
            return "I didn't find any routers in your network scan. This might be because the router is the device you're scanning from."
        }
        
        val analysis = StringBuilder("🌐 **Router Analysis:**\n\n")
        
        routers.forEach { router ->
            val deviceAnalysis = aiTroubleshooter.analyzeDeviceIssues(router)
            analysis.append("**${router.name}**\n")
            analysis.append("• Address: ${router.address}\n")
            analysis.append("• Web Interface: ${if (router.ports.contains(80) || router.ports.contains(443)) "Available" else "Not accessible"}\n")
            analysis.append("• DNS Service: ${if (router.ports.contains(53)) "Running" else "Not detected"}\n")
            
            if (deviceAnalysis.issues.isNotEmpty()) {
                analysis.append("• Issues: ${deviceAnalysis.issues.joinToString(", ")}\n")
            }
            analysis.append("\n")
        }
        
        return analysis.toString()
    }
    
    private fun analyzeCameraDevices(): String {
        val cameras = devices.filter { it.category.lowercase().contains("camera") }
        
        if (cameras.isEmpty()) {
            return "I didn't find any cameras in your network. Would you like me to scan for video streaming services?"
        }
        
        val analysis = StringBuilder("📹 **Camera Analysis:**\n\n")
        
        cameras.forEach { camera ->
            val deviceAnalysis = aiTroubleshooter.analyzeDeviceIssues(camera)
            analysis.append("**${camera.name}**\n")
            analysis.append("• Address: ${camera.address}\n")
            analysis.append("• Web Interface: ${if (camera.ports.contains(80)) "HTTP" else ""}${if (camera.ports.contains(443)) " HTTPS" else ""}\n")
            analysis.append("• Streaming: ${if (camera.ports.contains(554)) "RTSP ✓" else "Standard HTTP"}\n")
            
            if (deviceAnalysis.issues.isNotEmpty()) {
                analysis.append("• Issues: ${deviceAnalysis.issues.joinToString(", ")}\n")
            }
            analysis.append("\n")
        }
        
        return analysis.toString()
    }
    
    private fun formatDeviceCategories(categories: org.json.JSONObject): String {
        val formatted = StringBuilder()
        categories.keys().forEach { key ->
            formatted.append("• ${key.capitalize()}: ${categories.getInt(key)}\n")
        }
        return formatted.toString()
    }
    
    private fun formatRecommendations(recommendations: org.json.JSONArray): String {
        val formatted = StringBuilder()
        for (i in 0 until recommendations.length()) {
            formatted.append("• ${recommendations.getString(i)}\n")
        }
        return formatted.toString()
    }
    
    // Quick action methods
    private fun analyzeAllDevices() {
        addMessage(ChatMessage("You", "Analyze all devices", true))
        processUserMessage("analyze all devices")
    }
    
    private fun checkNetworkHealth() {
        addMessage(ChatMessage("You", "Check network health", true))
        processUserMessage("check network health")
    }
    
    private fun findIssues() {
        addMessage(ChatMessage("You", "Find issues", true))
        processUserMessage("find issues")
    }
    
    private fun suggestOptimizations() {
        addMessage(ChatMessage("You", "Suggest optimizations", true))
        processUserMessage("suggest optimizations")
    }
    
    private fun runDiagnostics() {
        addMessage(ChatMessage("You", "Run diagnostics", true))
        processUserMessage("run comprehensive diagnostics")
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
    
    data class ChatMessage(
        val sender: String,
        val message: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )
}
