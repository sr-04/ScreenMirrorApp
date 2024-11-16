package com.example.screenmirror

import android.graphics.Bitmap
import android.os.Bundle
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.screenmirror.utils.Constants
import java.io.DataInputStream
import java.net.Socket
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class ReceiverActivity : AppCompatActivity() {
    private lateinit var surfaceView: SurfaceView
    private lateinit var ipAddressInput: EditText
    private lateinit var connectButton: Button
    private var client: Socket? = null
    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiver)

        // Initialize views
        surfaceView = findViewById(R.id.surfaceView)
        ipAddressInput = findViewById(R.id.ipAddressInput)
        connectButton = findViewById(R.id.connectButton)

        setupUI()
    }

    private fun setupUI() {
        connectButton.setOnClickListener {
            val ipAddress = ipAddressInput.text.toString()
            if (ipAddress.isNotEmpty()) {
                connectToServer(ipAddress)
            } else {
                Toast.makeText(this, "Please enter IP address", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectToServer(serverIp: String) {
        isRunning = true
        thread {
            try {
                client = Socket(serverIp, Constants.PORT)
                val inputStream = DataInputStream(client?.getInputStream())

                while (isRunning) {
                    val width = inputStream.readInt()
                    val height = inputStream.readInt()
                    val bufferSize = width * height * 4
                    val buffer = ByteArray(bufferSize)
                    inputStream.readFully(buffer)

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(buffer))

                    displayFrame(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Connection error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun displayFrame(bitmap: Bitmap) {
        runOnUiThread {
            val canvas = surfaceView.holder.lockCanvas()
            if (canvas != null) {
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                surfaceView.holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        client?.close()
    }
}