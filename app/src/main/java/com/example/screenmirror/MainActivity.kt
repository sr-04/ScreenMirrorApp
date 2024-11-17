package com.example.screenmirror

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.screenmirror.service.ScreenMirroringService
import com.example.screenmirror.utils.NetworkUtils

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var receiveButton: Button
    private lateinit var ipAddressText: TextView
    private var mediaProjectionManager: MediaProjectionManager? = null
    private val PERMISSION_REQUEST_CODE = 1234

    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startScreenMirroring(result.resultCode, result.data)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()
        initializeViews()
        setupUI()
        displayIPAddress()

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                notGrantedPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                displayIPAddress()
            } else {
                Toast.makeText(this, "Permissions required for screen mirroring", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        receiveButton = findViewById(R.id.receiveButton)
        ipAddressText = findViewById(R.id.ipAddressText)
    }

    private fun setupUI() {
        startButton.setOnClickListener {
            requestScreenCapture()
        }

        stopButton.setOnClickListener {
            stopScreenCapture()
        }

        receiveButton.setOnClickListener {
            startActivity(Intent(this, ReceiverActivity::class.java))
        }

        stopButton.isEnabled = false
    }

    private fun displayIPAddress() {
        val ipAddress = NetworkUtils.getLocalIpAddress()
        if (ipAddress != null) {
            ipAddressText.text = "Your IP Address: $ipAddress"
        } else {
            ipAddressText.text = "Could not get IP address. Make sure Wi-Fi is connected."
        }
    }

    private fun requestScreenCapture() {
        try {
            mediaProjectionManager?.let {
                startForResult.launch(it.createScreenCaptureIntent())
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error requesting screen capture: ${e.message}",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun startScreenMirroring(resultCode: Int, data: Intent?) {
        try {
            val serviceIntent = Intent(this, ScreenMirroringService::class.java).apply {
                action = ScreenMirroringService.ACTION_START
                putExtra(ScreenMirroringService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenMirroringService.EXTRA_DATA, data)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            startButton.isEnabled = false
            stopButton.isEnabled = true
            statusText.text = "Status: Connected"
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting screen mirroring: ${e.message}",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopScreenCapture() {
        try {
            val serviceIntent = Intent(this, ScreenMirroringService::class.java).apply {
                action = ScreenMirroringService.ACTION_STOP
            }
            startService(serviceIntent)

            startButton.isEnabled = true
            stopButton.isEnabled = false
            statusText.text = "Status: Not Connected"
        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping screen mirroring: ${e.message}",
                Toast.LENGTH_SHORT).show()
        }
    }
}