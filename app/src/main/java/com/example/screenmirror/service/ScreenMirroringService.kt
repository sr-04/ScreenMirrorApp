package com.example.screenmirror.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.example.screenmirror.utils.Constants
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket

class ScreenMirroringService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(Constants.NOTIFICATION_ID, createNotification())
    }

    @SuppressLint("NewApi")
    private fun createNotificationChannel() {
        val channelId = "screen_mirror_channel"
        val channelName = "Screen Mirroring Service"
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Screen mirroring service notification"
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    @SuppressLint("NewApi")
    private fun createNotification(): Notification {
        val channelId = "screen_mirror_channel"
        return Notification.Builder(this, channelId)
            .setContentTitle("Screen Mirroring")
            .setContentText("Screen is being shared")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d("ScreenMirror", "Starting screen capture service")
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
                startScreenCapture(resultCode, data)
            }
            ACTION_STOP -> {
                Log.d("ScreenMirror", "Stopping screen capture service")
                stopScreenCapture()
            }
        }
        return START_NOT_STICKY
    }

    private fun startScreenCapture(resultCode: Int, data: Intent?) {
        try {
            val metrics = DisplayMetrics()
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)

            Log.d("ScreenMirror", "Screen size: ${metrics.widthPixels}x${metrics.heightPixels}")

            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)

            imageReader = ImageReader.newInstance(
                metrics.widthPixels,
                metrics.heightPixels,
                PixelFormat.RGBA_8888,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    image?.use { img ->
                        Log.d("ScreenMirror", "New frame captured: ${img.width}x${img.height}")
                    }
                }, null)
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )

            Log.d("ScreenMirror", "Virtual display created")
            startServer()
        } catch (e: Exception) {
            Log.e("ScreenMirror", "Error in startScreenCapture: ${e.message}")
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun startServer() {
        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(Constants.PORT)
                Log.d("ScreenMirror", "Server started on port ${Constants.PORT}")

                while (isRunning) {
                    val client = serverSocket?.accept()
                    Log.d("ScreenMirror", "Client connected from: ${client?.inetAddress}")
                    handleClient(client)
                }
            } catch (e: Exception) {
                Log.e("ScreenMirror", "Error in server: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }

    private fun handleClient(client: Socket?) {
        Thread {
            try {
                val outputStream = DataOutputStream(client?.getOutputStream())
                while (isRunning) {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        try {
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val pixelStride = plane.pixelStride
                            val rowStride = plane.rowStride
                            val rowPadding = rowStride - pixelStride * image.width

                            // Create byte array of correct size
                            val dataSize = buffer.remaining()
                            val bytes = ByteArray(dataSize)
                            buffer.get(bytes)

                            try {
                                // Send frame data
                                outputStream.writeInt(image.width)
                                outputStream.writeInt(image.height)
                                outputStream.writeInt(dataSize)
                                outputStream.write(bytes)
                                outputStream.flush()
                            } catch (e: Exception) {
                                Log.e("ScreenMirror", "Error sending frame: ${e.message}")
                                break
                            }
                        } finally {
                            image.close()
                        }
                        Thread.sleep(Constants.FRAME_DELAY)
                    }
                }
            } catch (e: Exception) {
                Log.e("ScreenMirror", "Error handling client: ${e.message}")
                e.printStackTrace()
            } finally {
                try {
                    client?.close()
                } catch (e: Exception) {
                    Log.e("ScreenMirror", "Error closing client: ${e.message}")
                }
            }
        }.start()
    }

    private fun stopScreenCapture() {
        isRunning = false
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
            serverSocket?.close()
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            Log.e("ScreenMirror", "Error stopping capture: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopScreenCapture()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "start_screen_mirror"
        const val ACTION_STOP = "stop_screen_mirror"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
    }
}