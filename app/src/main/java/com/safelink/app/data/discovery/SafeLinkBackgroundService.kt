package com.safelink.app.data.discovery

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import com.safelink.app.MainActivity
import com.safelink.app.R
import java.util.UUID

@SuppressLint("MissingPermission")
@com.google.accompanist.permissions.ExperimentalPermissionsApi
class SafeLinkBackgroundService : Service() {

    private val CHANNEL_ID = "SafeLinkForegroundServiceChannel"
    private val NOTIFICATION_ID = 1
    
    private val ALERT_CHANNEL_ID = "SafeLinkAlertChannel"
    private val ALERT_NOTIFICATION_ID = 2
    
    private var isScanning = false
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val scanner = bluetoothAdapter?.bluetoothLeScanner
    private val safeLinkServiceUuid = ParcelUuid(UUID.fromString("a07498ca-1088-4361-9c3a-23d9a101fcc4"))

    private var lastFoundTime = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeLink Background Scan")
            .setContentText("Scanning for nearby devices...")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        
        startBleScan()

        // If the service is killed, restart it
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBleScan()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startBleScan() {
        if (scanner == null || isScanning) return

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(safeLinkServiceUuid)
            .build()

        // Low power scan is essential for background services to not kill the battery
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        try {
            scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
        } catch (e: Exception) {
            // Permissions missing or BLE off
        }
    }

    private fun stopBleScan() {
        if (!isScanning) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {}
        isScanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.scanRecord?.let {
                val now = System.currentTimeMillis()
                // Only trigger the "Found!" notification once every 60 seconds at most
                if (now - lastFoundTime > 60000) {
                    lastFoundTime = now
                    updateNotification("SafeLink Connected", "Your device is nearby and ready!")
                }
            }
        }
    }

    private fun updateNotification(title: String, text: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Low Priority channel for the persistent background service (no popup, no sound)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SafeLink Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            
            // High Priority channel for the "Fast Pair" popup alerts (forces Heads-Up display)
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "SafeLink Device Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when you are near your SafeLink devices"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(alertChannel)
        }
    }
}
