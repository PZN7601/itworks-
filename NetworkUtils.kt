package com.example.universaldeviceconnector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

object NetworkUtils {
    suspend fun isReachable(ipAddress: String, timeoutMillis: Int = 500): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                InetAddress.getByName(ipAddress).isReachable(timeoutMillis)
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun isPortOpen(ipAddress: String, port: Int, timeoutMillis: Int = 500): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ipAddress, port), timeoutMillis)
                    true
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun getMacAddress(ipAddress: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val runtime = Runtime.getRuntime()
                val process = runtime.exec("ping -c 1 $ipAddress")
                if (process.waitFor() == 0) {
                    // Now execute ARP to get the MAC address
                    val arpProcess = runtime.exec("arp -a $ipAddress")
                    BufferedReader(InputStreamReader(arpProcess.inputStream)).use { reader ->
                        val output = reader.readLine()
                        // Parse MAC address from output
                        val macPattern = "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})".toRegex()
                        val macMatcher = macPattern.find(output ?: "")
                        macMatcher?.value
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    suspend fun getNetworkInfo(ipAddress: String): Map<String, String> {
        val info = mutableMapOf<String, String>()
        
        withContext(Dispatchers.IO) {
            try {
                // Try to get hostname
                try {
                    val inetAddress = InetAddress.getByName(ipAddress)
                    info["hostname"] = inetAddress.hostName
                } catch (e: Exception) {
                    info["hostname"] = "Unknown"
                }
                
                // Check common ports
                val commonPorts = listOf(21, 22, 23, 80, 443, 445, 515, 631, 3389, 8080)
                val openPorts = mutableListOf<Int>()
                
                for (port in commonPorts) {
                    if (isPortOpen(ipAddress, port, 300)) {
                        openPorts.add(port)
                    }
                }
                
                info["openPorts"] = openPorts.joinToString(", ")
                
                // Try to get MAC address
                val mac = getMacAddress(ipAddress)
                if (mac != null) {
                    info["macAddress"] = mac
                }
            } catch (e: Exception) {
                info["error"] = e.message ?: "Unknown error"
            }
        }
        
        return info
    }
}
