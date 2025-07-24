package com.example.universaldeviceconnector

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class NetworkTopologyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class NetworkNode(
        val device: MainActivity.DeviceInfo,
        var x: Float = 0f,
        var y: Float = 0f,
        var vx: Float = 0f,
        var vy: Float = 0f,
        val radius: Float = 40f,
        var isSelected: Boolean = false,
        var isGateway: Boolean = false
    )

    data class NetworkConnection(
        val from: NetworkNode,
        val to: NetworkNode,
        val strength: Float = 1f,
        val connectionType: ConnectionType = ConnectionType.ETHERNET
    )

    enum class ConnectionType {
        ETHERNET, WIFI, BLUETOOTH, UNKNOWN
    }

    private val nodes = mutableListOf<NetworkNode>()
    private val connections = mutableListOf<NetworkConnection>()
    
    // Paint objects for drawing
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val connectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gatewayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Colors for different device types
    private val deviceColors = mapOf(
        "router" to Color.parseColor("#FF5722"),
        "printer" to Color.parseColor("#4CAF50"),
        "camera" to Color.parseColor("#2196F3"),
        "computer" to Color.parseColor("#9C27B0"),
        "phone" to Color.parseColor("#FF9800"),
        "iot" to Color.parseColor("#795548"),
        "unknown" to Color.parseColor("#607D8B")
    )
    
    // Gesture detection
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    
    // Zoom and pan
    private var scaleFactor = 1f
    private var panX = 0f
    private var panY = 0f
    
    // Animation
    private var isAnimating = false
    private val animationRunnable = object : Runnable {
        override fun run() {
            if (isAnimating) {
                updatePhysics()
                invalidate()
                postDelayed(this, 16) // ~60 FPS
            }
        }
    }
    
    // Physics simulation parameters
    private val repulsionForce = 5000f
    private val attractionForce = 0.1f
    private val damping = 0.9f
    private val centeringForce = 0.001f
    
    private var selectedNode: NetworkNode? = null
    private var onNodeClickListener: ((NetworkNode) -> Unit)? = null

    init {
        setupPaints()
        
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
        
        startAnimation()
    }

    private fun setupPaints() {
        nodePaint.style = Paint.Style.FILL
        
        connectionPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#E0E0E0")
        }
        
        textPaint.apply {
            color = Color.BLACK
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        
        selectedPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.parseColor("#FF4081")
        }
        
        gatewayPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.parseColor("#FFC107")
        }
    }

    fun setDevices(devices: List<MainActivity.DeviceInfo>) {
        nodes.clear()
        connections.clear()
        
        // Create nodes
        devices.forEachIndexed { index, device ->
            val node = NetworkNode(
                device = device,
                x = Random.nextFloat() * width,
                y = Random.nextFloat() * height,
                isGateway = isGatewayDevice(device)
            )
            nodes.add(node)
        }
        
        // Create connections based on network proximity
        createConnections()
        
        // Position nodes in a better initial layout
        arrangeInitialLayout()
        
        invalidate()
    }

    private fun isGatewayDevice(device: MainActivity.DeviceInfo): Boolean {
        return device.category.lowercase().contains("router") ||
                device.name.lowercase().contains("gateway") ||
                device.ports.contains(53) // DNS port
    }

    private fun createConnections() {
        // Connect devices on the same subnet
        val subnets = mutableMapOf<String, MutableList<NetworkNode>>()
        
        nodes.forEach { node ->
            val subnet = getSubnet(node.device.address)
            if (subnet.isNotEmpty()) {
                subnets.getOrPut(subnet) { mutableListOf() }.add(node)
            }
        }
        
        // Create connections within subnets
        subnets.values.forEach { subnetNodes ->
            if (subnetNodes.size > 1) {
                // Find gateway in subnet
                val gateway = subnetNodes.find { it.isGateway }
                
                if (gateway != null) {
                    // Connect all devices to gateway
                    subnetNodes.forEach { node ->
                        if (node != gateway) {
                            connections.add(
                                NetworkConnection(
                                    from = gateway,
                                    to = node,
                                    connectionType = ConnectionType.ETHERNET
                                )
                            )
                        }
                    }
                } else {
                    // Create mesh connections
                    for (i in subnetNodes.indices) {
                        for (j in i + 1 until subnetNodes.size) {
                            connections.add(
                                NetworkConnection(
                                    from = subnetNodes[i],
                                    to = subnetNodes[j],
                                    connectionType = ConnectionType.ETHERNET
                                )
                            )
                        }
                    }
                }
            }
        }
        
        // Add some wireless connections for mobile devices
        addWirelessConnections()
    }

    private fun getSubnet(ipAddress: String): String {
        return try {
            val parts = ipAddress.split(".")
            if (parts.size >= 3) {
                "${parts[0]}.${parts[1]}.${parts[2]}"
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun addWirelessConnections() {
        val mobileDevices = nodes.filter { 
            it.device.category.lowercase().contains("phone") ||
            it.device.category.lowercase().contains("tablet") ||
            it.device.category.lowercase().contains("laptop")
        }
        
        val accessPoints = nodes.filter { 
            it.device.category.lowercase().contains("router") ||
            it.device.name.lowercase().contains("ap")
        }
        
        mobileDevices.forEach { mobile ->
            val nearestAP = accessPoints.minByOrNull { ap ->
                distance(mobile.x, mobile.y, ap.x, ap.y)
            }
            
            nearestAP?.let { ap ->
                connections.add(
                    NetworkConnection(
                        from = ap,
                        to = mobile,
                        connectionType = ConnectionType.WIFI,
                        strength = 0.7f
                    )
                )
            }
        }
    }

    private fun arrangeInitialLayout() {
        if (nodes.isEmpty()) return
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        // Place gateway devices in the center
        val gateways = nodes.filter { it.isGateway }
        val regularNodes = nodes.filter { !it.isGateway }
        
        // Arrange gateways in center
        gateways.forEachIndexed { index, gateway ->
            val angle = (2 * PI * index / gateways.size).toFloat()
            val radius = 100f
            gateway.x = centerX + cos(angle) * radius
            gateway.y = centerY + sin(angle) * radius
        }
        
        // Arrange other devices in concentric circles
        val devicesPerRing = 8
        var currentRing = 1
        var deviceIndex = 0
        
        regularNodes.forEach { node ->
            val ringRadius = 200f + (currentRing * 150f)
            val angleStep = 2 * PI / devicesPerRing
            val angle = (angleStep * deviceIndex).toFloat()
            
            node.x = centerX + cos(angle) * ringRadius
            node.y = centerY + sin(angle) * ringRadius
            
            deviceIndex++
            if (deviceIndex >= devicesPerRing) {
                deviceIndex = 0
                currentRing++
            }
        }
    }

    private fun startAnimation() {
        isAnimating = true
        post(animationRunnable)
    }

    private fun stopAnimation() {
        isAnimating = false
        removeCallbacks(animationRunnable)
    }

    private fun updatePhysics() {
        if (nodes.size < 2) return
        
        // Reset forces
        nodes.forEach { node ->
            node.vx = 0f
            node.vy = 0f
        }
        
        // Apply repulsion between all nodes
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val node1 = nodes[i]
                val node2 = nodes[j]
                
                val dx = node2.x - node1.x
                val dy = node2.y - node1.y
                val distance = sqrt(dx * dx + dy * dy)
                
                if (distance > 0) {
                    val force = repulsionForce / (distance * distance)
                    val fx = (dx / distance) * force
                    val fy = (dy / distance) * force
                    
                    node1.vx -= fx
                    node1.vy -= fy
                    node2.vx += fx
                    node2.vy += fy
                }
            }
        }
        
        // Apply attraction for connected nodes
        connections.forEach { connection ->
            val dx = connection.to.x - connection.from.x
            val dy = connection.to.y - connection.from.y
            val distance = sqrt(dx * dx + dy * dy)
            
            if (distance > 0) {
                val force = attractionForce * distance * connection.strength
                val fx = (dx / distance) * force
                val fy = (dy / distance) * force
                
                connection.from.vx += fx
                connection.from.vy += fy
                connection.to.vx -= fx
                connection.to.vy -= fy
            }
        }
        
        // Apply centering force
        val centerX = width / 2f
        val centerY = height / 2f
        
        nodes.forEach { node ->
            val dx = centerX - node.x
            val dy = centerY - node.y
            
            node.vx += dx * centeringForce
            node.vy += dy * centeringForce
        }
        
        // Update positions
        nodes.forEach { node ->
            node.vx *= damping
            node.vy *= damping
            
            node.x += node.vx
            node.y += node.vy
            
            // Keep nodes within bounds
            node.x = node.x.coerceIn(node.radius, width - node.radius)
            node.y = node.y.coerceIn(node.radius, height - node.radius)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scaleFactor, scaleFactor)
        
        // Draw connections
        connections.forEach { connection ->
            drawConnection(canvas, connection)
        }
        
        // Draw nodes
        nodes.forEach { node ->
            drawNode(canvas, node)
        }
        
        canvas.restore()
        
        // Draw legend
        drawLegend(canvas)
    }

    private fun drawConnection(canvas: Canvas, connection: NetworkConnection) {
        val paint = Paint(connectionPaint).apply {
            when (connection.connectionType) {
                ConnectionType.WIFI -> {
                    color = Color.parseColor("#4CAF50")
                    pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
                }
                ConnectionType.BLUETOOTH -> {
                    color = Color.parseColor("#2196F3")
                    pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
                }
                ConnectionType.ETHERNET -> {
                    color = Color.parseColor("#757575")
                    pathEffect = null
                }
                ConnectionType.UNKNOWN -> {
                    color = Color.parseColor("#BDBDBD")
                    pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f)
                }
            }
            alpha = (255 * connection.strength).toInt()
        }
        
        canvas.drawLine(
            connection.from.x, connection.from.y,
            connection.to.x, connection.to.y,
            paint
        )
        
        // Draw connection type indicator
        val midX = (connection.from.x + connection.to.x) / 2
        val midY = (connection.from.y + connection.to.y) / 2
        
        when (connection.connectionType) {
            ConnectionType.WIFI -> {
                drawWifiIcon(canvas, midX, midY)
            }
            ConnectionType.BLUETOOTH -> {
                drawBluetoothIcon(canvas, midX, midY)
            }
            else -> {}
        }
    }

    private fun drawNode(canvas: Canvas, node: NetworkNode) {
        val color = deviceColors[node.device.category.lowercase()] ?: deviceColors["unknown"]!!
        
        // Draw node circle
        nodePaint.color = color
        canvas.drawCircle(node.x, node.y, node.radius, nodePaint)
        
        // Draw gateway indicator
        if (node.isGateway) {
            canvas.drawCircle(node.x, node.y, node.radius + 5f, gatewayPaint)
        }
        
        // Draw selection indicator
        if (node.isSelected) {
            canvas.drawCircle(node.x, node.y, node.radius + 10f, selectedPaint)
        }
        
        // Draw device icon
        drawDeviceIcon(canvas, node)
        
        // Draw device name
        val deviceName = node.device.name.take(10) + if (node.device.name.length > 10) "..." else ""
        canvas.drawText(
            deviceName,
            node.x,
            node.y + node.radius + 30f,
            textPaint
        )
        
        // Draw IP address
        textPaint.textSize = 18f
        canvas.drawText(
            node.device.address,
            node.x,
            node.y + node.radius + 50f,
            textPaint
        )
        textPaint.textSize = 24f
    }

    private fun drawDeviceIcon(canvas: Canvas, node: NetworkNode) {
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        
        val icon = when (node.device.category.lowercase()) {
            "router" -> "R"
            "printer" -> "P"
            "camera" -> "C"
            "computer" -> "PC"
            "phone" -> "📱"
            "iot" -> "IoT"
            else -> "?"
        }
        
        canvas.drawText(icon, node.x, node.y + 7f, iconPaint)
    }

    private fun drawWifiIcon(canvas: Canvas, x: Float, y: Float) {
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4CAF50")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        
        // Draw wifi waves
        canvas.drawArc(x - 8f, y - 8f, x + 8f, y + 8f, -45f, 90f, false, iconPaint)
        canvas.drawArc(x - 5f, y - 5f, x + 5f, y + 5f, -45f, 90f, false, iconPaint)
    }

    private fun drawBluetoothIcon(canvas: Canvas, x: Float, y: Float) {
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2196F3")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        
        // Draw simplified bluetooth symbol
        canvas.drawLine(x, y - 8f, x, y + 8f, iconPaint)
        canvas.drawLine(x - 4f, y - 4f, x + 4f, y + 4f, iconPaint)
        canvas.drawLine(x - 4f, y + 4f, x + 4f, y - 4f, iconPaint)
    }

    private fun drawLegend(canvas: Canvas) {
        val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 16f
        }
        
        val legendBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5F5F5")
            alpha = 200
        }
        
        val legendX = 20f
        val legendY = 20f
        val legendWidth = 200f
        val legendHeight = 150f
        
        // Draw legend background
        canvas.drawRoundRect(
            legendX, legendY, legendX + legendWidth, legendY + legendHeight,
            10f, 10f, legendBackground
        )
        
        // Draw legend items
        var yOffset = legendY + 25f
        
        canvas.drawText("Network Topology", legendX + 10f, yOffset, legendPaint)
        yOffset += 25f
        
        // Connection types
        canvas.drawLine(legendX + 10f, yOffset, legendX + 30f, yOffset, connectionPaint)
        canvas.drawText("Ethernet", legendX + 40f, yOffset + 5f, legendPaint)
        yOffset += 20f
        
        val wifiPaint = Paint(connectionPaint).apply {
            color = Color.parseColor("#4CAF50")
            pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        }
        canvas.drawLine(legendX + 10f, yOffset, legendX + 30f, yOffset, wifiPaint)
        canvas.drawText("WiFi", legendX + 40f, yOffset + 5f, legendPaint)
        yOffset += 20f
        
        val btPaint = Paint(connectionPaint).apply {
            color = Color.parseColor("#2196F3")
            pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
        }
        canvas.drawLine(legendX + 10f, yOffset, legendX + 30f, yOffset, btPaint)
        canvas.drawText("Bluetooth", legendX + 40f, yOffset + 5f, legendPaint)
        yOffset += 20f
        
        // Gateway indicator
        canvas.drawCircle(legendX + 20f, yOffset, 8f, gatewayPaint)
        canvas.drawText("Gateway", legendX + 40f, yOffset + 5f, legendPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f)
            invalidate()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            panX -= distanceX
            panY -= distanceY
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val adjustedX = (e.x - panX) / scaleFactor
            val adjustedY = (e.y - panY) / scaleFactor
            
            val clickedNode = nodes.find { node ->
                distance(adjustedX, adjustedY, node.x, node.y) <= node.radius
            }
            
            // Clear previous selection
            nodes.forEach { it.isSelected = false }
            
            clickedNode?.let { node ->
                node.isSelected = true
                selectedNode = node
                onNodeClickListener?.invoke(node)
                invalidate()
            }
            
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Reset zoom and pan
            scaleFactor = 1f
            panX = 0f
            panY = 0f
            invalidate()
            return true
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    fun setOnNodeClickListener(listener: (NetworkNode) -> Unit) {
        onNodeClickListener = listener
    }

    fun highlightDevice(deviceAddress: String) {
        nodes.forEach { it.isSelected = false }
        nodes.find { it.device.address == deviceAddress }?.let { node ->
            node.isSelected = true
            selectedNode = node
            invalidate()
        }
    }

    fun resetView() {
        scaleFactor = 1f
        panX = 0f
        panY = 0f
        nodes.forEach { it.isSelected = false }
        selectedNode = null
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
