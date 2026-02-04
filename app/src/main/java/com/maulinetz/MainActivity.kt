package com.maulinetz

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {
    private lateinit var etServerIp: EditText
    private lateinit var etPort: EditText
    private lateinit var etUser: EditText
    private lateinit var tvDeviceId: TextView
    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView
    
    private var isConnected = false
    private lateinit var deviceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etServerIp = findViewById(R.id.etServerIp)
        etPort = findViewById(R.id.etPort)
        etUser = findViewById(R.id.etUser)
        tvDeviceId = findViewById(R.id.tvDeviceId)
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)

        deviceId = generate32CharHexId()
        tvDeviceId.text = "ID del Dispositivo: $deviceId"

        btnConnect.setOnClickListener {
            if (isConnected) {
                disconnectVpn()
            } else {
                prepareVpn()
            }
        }
    }

    private fun generate32CharHexId(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "default_id"
        val bytes = MessageDigest.getInstance("MD5").digest(androidId.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun prepareVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 0)
        } else {
            onActivityResult(0, RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            connectVpn()
        }
    }

    private fun connectVpn() {
        val server = etServerIp.text.toString()
        val port = etPort.text.toString()
        val user = etUser.text.toString()

        if (server.isEmpty() || user.isEmpty()) {
            Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, HysteriaVpnService::class.java).apply {
            action = HysteriaVpnService.ACTION_CONNECT
            putExtra(HysteriaVpnService.EXTRA_SERVER, server)
            putExtra(HysteriaVpnService.EXTRA_PORT, port)
            putExtra(HysteriaVpnService.EXTRA_USER, user)
            putExtra(HysteriaVpnService.EXTRA_DEVICE_ID, deviceId)
        }
        startService(intent)
        
        isConnected = true
        btnConnect.text = "DESCONECTAR"
        tvStatus.text = "Estado: Conectado"
        toggleInputs(false)
    }

    private fun disconnectVpn() {
        val intent = Intent(this, HysteriaVpnService::class.java).apply {
            action = HysteriaVpnService.ACTION_DISCONNECT
        }
        startService(intent)
        
        isConnected = false
        btnConnect.text = "CONECTAR"
        tvStatus.text = "Estado: Desconectado"
        toggleInputs(true)
    }

    private fun toggleInputs(enabled: Boolean) {
        etServerIp.isEnabled = enabled
        etPort.isEnabled = enabled
        etUser.isEnabled = enabled
    }
}
