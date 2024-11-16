package com.example.screenmirror

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.screenmirror.databinding.ActivityMainBinding  // This import should work now
import com.example.screenmirror.service.ScreenMirroringService
import com.example.screenmirror.utils.NetworkUtils

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var mediaProjectionManager: MediaProjectionManager? = null

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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        setupUI()
        displayIPAddress()
    }

    private fun setupUI() {
        binding.startButton.setOnClickListener {
            startForResult.launch(mediaProjectionManager?.createScreenCaptureIntent())
        }

        binding.stopButton.setOnClickListener {
            stopScreenMirroring()
        }

        binding.receiveButton.setOnClickListener {
            startActivity(Intent(this, ReceiverActivity::class.java))
        }
    }

    private fun displayIPAddress() {
        val ipAddress = NetworkUtils.getLocalIpAddress()
        binding.ipAddressText.text = "Your IP Address: $ipAddress"
    }

    private fun startScreenMirroring(resultCode: Int, data: Intent?) {
        val serviceIntent = Intent(this, ScreenMirroringService::class.java).apply {
            action = ScreenMirroringService.ACTION_START
            putExtra(ScreenMirroringService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenMirroringService.EXTRA_DATA, data)
        }
        startService(serviceIntent)

        binding.startButton.isEnabled = false
        binding.stopButton.isEnabled = true
        binding.statusText.text = "Status: Connected"
    }

    private fun stopScreenMirroring() {
        val serviceIntent = Intent(this, ScreenMirroringService::class.java).apply {
            action = ScreenMirroringService.ACTION_STOP
        }
        startService(serviceIntent)

        binding.startButton.isEnabled = true
        binding.stopButton.isEnabled = false
        binding.statusText.text = "Status: Not Connected"
    }
}