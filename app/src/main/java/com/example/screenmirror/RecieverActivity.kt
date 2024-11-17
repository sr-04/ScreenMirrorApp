package com.example.screenmirror

import android.graphics.Bitmap
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.screenmirror.utils.Constants
import java.io.DataInputStream
import java.net.Socket
import java.nio.ByteBuffer
import android.util.Log
import kotlin.concurrent.thread

class ReceiverActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private lateinit var surfaceView: SurfaceView
    private lateinit var ipAddressInput: EditText
    private lateinit var connectButton: Button
    private var client: Socket? = null
    private var isRunning = false
    private var surfaceCreated = false
    private var surfaceHolder: SurfaceHolder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiver)
        initializeViews()
        setupUI()
    }

    private fun initializeViews() {
        try {
            surfaceView = findViewById(R.id.surfaceView)
            ipAddressInput = findViewById(R.id.ipAddressInput)
            connectButton = findViewById(R.id.connectButton)

            surfaceHolder = surfaceView.holder
            surfaceHolder?.addCallback(this)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error initializing views", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupUI() {
        connectButton.setOnClickListener {
            val ipAddress = ipAddressInput.text.toString()
            if (ipAddress.isNotEmpty()) {
                connectButton.isEnabled = false
                connectButton.text = "Connecting..."
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

                runOnUiThread {
                    Toast.makeText(this, "Connected to server", Toast.LENGTH_SHORT).show()
                    connectButton.text = "Connected"
                }

                while (isRunning && !isFinishing) {
                    try {
                        val width = inputStream.readInt()
                        val height = inputStream.readInt()
                        val dataSize = inputStream.readInt()
                        val buffer = ByteArray(dataSize)

                        inputStream.readFully(buffer)

                        Log.d("ScreenMirror", "Received frame: ${width}x${height}, size: ${dataSize}")

                        val bitmap = Bitmap.createBitmap(
                            width, height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(buffer))

                        if (surfaceCreated) {
                            displayFrame(bitmap)
                        }

                        bitmap.recycle()
                    } catch (e: Exception) {
                        Log.e("ScreenMirror", "Error receiving frame: ${e.message}")
                        e.printStackTrace()
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("ScreenMirror", "Connection error: ${e.message}")
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Connection error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    connectButton.isEnabled = true
                    connectButton.text = "Connect"
                }
            }
        }
    }

    private fun displayFrame(bitmap: Bitmap) {
        if (!surfaceCreated || surfaceHolder == null) return

        try {
            surfaceHolder?.let { holder ->
                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    val scale = kotlin.math.min(
                        surfaceView.width.toFloat() / bitmap.width,
                        surfaceView.height.toFloat() / bitmap.height
                    )

                    val scaledWidth = (bitmap.width * scale).toInt()
                    val scaledHeight = (bitmap.height * scale).toInt()

                    val left = (surfaceView.width - scaledWidth) / 2f
                    val top = (surfaceView.height - scaledHeight) / 2f

                    canvas.drawColor(android.graphics.Color.BLACK)
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        android.graphics.RectF(left, top, left + scaledWidth, top + scaledHeight),
                        null
                    )

                    holder.unlockCanvasAndPost(canvas)
                    Log.d("ScreenMirror", "Frame displayed")
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenMirror", "Error displaying frame: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceCreated = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Surface dimensions changed
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceCreated = false
    }

    override fun onDestroy() {
        isRunning = false
        surfaceCreated = false
        try {
            client?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}