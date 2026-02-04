package com.maulinetz

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class HysteriaVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var hysteriaProcess: Process? = null
    private val isRunning = AtomicBoolean(false)
    private var reconnectThread: Thread? = null

    companion object {
        const val TAG = "HysteriaVpnService"
        const val ACTION_CONNECT = "com.maulinetz.CONNECT"
        const val ACTION_DISCONNECT = "com.maulinetz.DISCONNECT"
        const val EXTRA_SERVER = "server"
        const val EXTRA_PORT = "port"
        const val EXTRA_USER = "user"
        const val EXTRA_DEVICE_ID = "device_id"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val server = intent.getStringExtra(EXTRA_SERVER) ?: ""
                val port = intent.getStringExtra(EXTRA_PORT) ?: "8443"
                val user = intent.getStringExtra(EXTRA_USER) ?: ""
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: ""
                startVpn(server, port, user, deviceId)
            }
            ACTION_DISCONNECT -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(server: String, port: String, user: String, deviceId: String) {
        if (isRunning.get()) return
        isRunning.set(true)

        Thread {
            try {
                val binaryFile = extractBinary()
                if (binaryFile == null) {
                    Log.e(TAG, "Failed to extract binary")
                    stopSelf()
                    return@Thread
                }

                val configFile = createConfigFile(server, port, user, deviceId)
                
                setupVpnInterface()
                runHysteria(binaryFile, configFile)
                
                startMonitoring()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting VPN", e)
                stopVpn()
            }
        }.start()
    }

    private fun extractBinary(): File? {
        val abi = if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) "arm64-v8a" else "armeabi-v7a"
        val assetPath = "lib/$abi/hysteria"
        val outFile = File(filesDir, "hysteria_bin")
        
        try {
            assets.open(assetPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile.setExecutable(true)
            return outFile
        } catch (e: IOException) {
            Log.e(TAG, "Binary extraction failed", e)
            return null
        }
    }

    private fun createConfigFile(server: String, port: String, user: String, deviceId: String): File {
        val configFile = File(filesDir, "client.yaml")
        val configContent = """
            server: $server:$port
            auth: $user:$deviceId
            transport: udp
            tls:
              sni: $server
              insecure: true
            socks5:
              listen: 127.0.0.1:1080
            bandwidth:
              up: 100 mbps
              down: 500 mbps
            fastOpen: true
            lazy: true
        """.trimIndent()
        configFile.writeText(configContent)
        return configFile
    }

    private fun setupVpnInterface() {
        val builder = Builder()
            .setSession("Maulinet Z")
            .setMtu(1350)
            .addAddress("10.0.0.2", 24)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)
            .addDisallowedApplication(packageName)

        vpnInterface = builder.establish()
    }

    private fun runHysteria(binary: File, config: File) {
        val pb = ProcessBuilder(binary.absolutePath, "client", "-c", config.absolutePath)
        pb.redirectErrorStream(true)
        hysteriaProcess = pb.start()
        
        Thread {
            hysteriaProcess?.inputStream?.bufferedReader()?.use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.d(TAG, "Hysteria: $line")
                }
            }
        }.start()
    }

    private fun startMonitoring() {
        reconnectThread = Thread {
            while (isRunning.get()) {
                try {
                    hysteriaProcess?.exitValue()
                    // If we get here, the process has exited
                    Log.w(TAG, "Hysteria process died, reconnecting in 2s...")
                    Thread.sleep(2000)
                    if (isRunning.get()) {
                        val binaryFile = File(filesDir, "hysteria_bin")
                        val configFile = File(filesDir, "client.yaml")
                        runHysteria(binaryFile, configFile)
                    }
                } catch (e: IllegalThreadStateException) {
                    // Process is still running
                    Thread.sleep(5000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
        reconnectThread?.start()
    }

    private fun stopVpn() {
        isRunning.set(false)
        reconnectThread?.interrupt()
        hysteriaProcess?.destroy()
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
