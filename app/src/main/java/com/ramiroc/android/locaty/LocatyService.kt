/**
 * Copyright (c) 2020 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.ramiroc.android.locaty

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.IBinder
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlin.math.round
import androidx.localbroadcastmanager.content.LocalBroadcastManager



class LocatyService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var background = false
    private val notificationActivityRequestCode = 0
    private val notificationId = 1
    private val notificationStopRequestCode = 2



    override fun onCreate() {
        super.onCreate()
        // 1
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
// 2
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        }
// 3
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        }

        val notification = createNotification(getString(R.string.not_available), 0.0)
// 2
        startForeground(notificationId, notification)

    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            // 1
            background = it.getBooleanExtra(KEY_BACKGROUND, false)
        }
        return START_STICKY
    }


        private val accelerometerReading = FloatArray(3)
        private val magnetometerReading = FloatArray(3)
        private val rotationMatrix = FloatArray(9)
        private val orientationAngles = FloatArray(3)



        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }

        override fun onSensorChanged(event: SensorEvent?) {
            // 1
            if (event == null) {
                return
            }
            // 2
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                // 3
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }

            updateOrientationAngles()
        }

        fun updateOrientationAngles() {
            // 1
            SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
            // 2
            val orientation = SensorManager.getOrientation(rotationMatrix, orientationAngles)
            // 3
            val degrees = (Math.toDegrees(orientation.get(0).toDouble()) + 360.0) % 360.0
            // 4
            val angle = round(degrees * 100) / 100

            val direction = getDirection(degrees)

            // 1
            val intent = Intent()
            intent.putExtra(KEY_ANGLE, angle)
            intent.putExtra(KEY_DIRECTION, direction)
            intent.action = KEY_ON_SENSOR_CHANGED_ACTION
// 2
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

            if (background) {
                // 1
                val notification = createNotification(direction, angle)
                startForeground(notificationId, notification)
            } else {
                // 2
                stopForeground(true)
            }


        }

        private fun getDirection(angle: Double): String {
            var direction = ""

            if (angle >= 350 || angle <= 10)
                direction = "N"
            if (angle < 350 && angle > 280)
                direction = "NW"
            if (angle <= 280 && angle > 260)
                direction = "W"
            if (angle <= 260 && angle > 190)
                direction = "SW"
            if (angle <= 190 && angle > 170)
                direction = "S"
            if (angle <= 170 && angle > 100)
                direction = "SE"
            if (angle <= 100 && angle > 80)
                direction = "E"
            if (angle <= 80 && angle > 10)
                direction = "NE"

            return direction
        }

    private fun createNotification(direction: String, angle: Double): Notification {
        // 1
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                application.packageName,
                "Notifications", NotificationManager.IMPORTANCE_DEFAULT
            )

            // Configure the notification channel.
            notificationChannel.enableLights(false)
            notificationChannel.setSound(null, null)
            notificationChannel.enableVibration(false)
            notificationChannel.vibrationPattern = longArrayOf(0L)
            notificationChannel.setShowBadge(false)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val notificationBuilder = NotificationCompat.Builder(baseContext, application.packageName)
        // 2
        val contentIntent = PendingIntent.getActivity(
            this, notificationActivityRequestCode,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
        // 3
        val stopNotificationIntent = Intent(this, ActionListener::class.java)
        stopNotificationIntent.action = KEY_NOTIFICATION_STOP_ACTION
        stopNotificationIntent.putExtra(KEY_NOTIFICATION_ID, notificationId)
        val pendingStopNotificationIntent =
            PendingIntent.getBroadcast(this, notificationStopRequestCode, stopNotificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        notificationBuilder.setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentTitle(resources.getString(R.string.app_name))
            .setContentText("You're currently facing $direction at an angle of $angle°")
            .setWhen(System.currentTimeMillis())
            .setDefaults(0)
            .setVibrate(longArrayOf(0L))
            .setSound(null)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(contentIntent)
            .addAction(R.mipmap.ic_launcher_round, getString(R.string.stop_notifications), pendingStopNotificationIntent)


        return notificationBuilder.build()
    }




    override fun onBind(p0: Intent?): IBinder? {
            TODO("Not yet implemented")
        }

    companion object {
        val KEY_ANGLE = "angle"
        val KEY_DIRECTION = "direction"
        val KEY_BACKGROUND = "background"
        val KEY_NOTIFICATION_ID = "notificationId"
        val KEY_ON_SENSOR_CHANGED_ACTION = "com.ramiroc.android.locaty.ON_SENSOR_CHANGED"
        val KEY_NOTIFICATION_STOP_ACTION = "com.ramiroc.android.locaty.NOTIFICATION_STOP"
    }

    class ActionListener : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent != null && intent.action != null) {
                // 1
                if (intent.action.equals(KEY_NOTIFICATION_STOP_ACTION)) {
                    context?.let {
                        // 2
                        val notificationManager =
                            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val locatyIntent = Intent(context, LocatyService::class.java)
                        // 3
                        context.stopService(locatyIntent)
                        val notificationId = intent.getIntExtra(KEY_NOTIFICATION_ID, -1)
                        if (notificationId != -1) {
                            // 4
                            notificationManager.cancel(notificationId)
                        }
                    }
                }
            }
        }
    }





}