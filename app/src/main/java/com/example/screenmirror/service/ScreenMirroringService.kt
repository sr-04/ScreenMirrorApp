package com.example.screenmirror.service

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
import com.example.screenmirror.utils.Constants
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class ScreenMirroringService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(Constants.NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
                startScreenCapture(resultCode, data)
            }
            ACTION_STOP -> stopScreenCapture()
        }
        return START_NOT_STICKY
    }

    private fun startScreenCapture(resultCode: Int, data: Intent?) {
        val metrics = DisplayMetrics()
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)

        setupVirtualDisplay(metrics)
        startServer()
    }

    private fun setupVirtualDisplay(metrics: DisplayMetrics) {
        imageReader = ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun startServer() {
        isRunning = true
        thread {
            try {
                serverSocket = ServerSocket(Constants.PORT)
                while (isRunning) {
                    val client = serverSocket?.accept()
                    handleClient(client)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(client: Socket?) {
        thread {
            try {
                val outputStream = DataOutputStream(client?.getOutputStream())
                while (isRunning) {
                    val image = imageReader?.acquireLatestImage()
                    image?.use { img ->
                        val plane = img.planes[0]
                        val buffer = plane.buffer
                        outputStream.writeInt(img.width)
                        outputStream.writeInt(img.height)
                        outputStream.write(buffer.array())
                    }
                    Thread.sleep(Constants.FRAME_DELAY)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopScreenCapture() {
        isRunning = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        serverSocket?.close()
        stopForeground(true)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val channelId = "screen_mirror_channel"
        val channelName = "Screen Mirroring Service"

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle("Screen Mirroring")
            .setContentText("Screen is being shared")
            .build()
    }

    companion object {
        const val ACTION_START = "start_screen_mirror"
        const val ACTION_STOP = "stop_screen_mirror"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
    }
}