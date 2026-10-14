// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// DeviceTilt and the floating bar's quantized gravity light, adapted to visible View owners.
// miuix source revisions and local changes: app/src/main/assets/open-source-licenses.txt.
package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import com.forbidad4tieba.hook.core.XposedCompat
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

/** One rotation sensor subscription, shared by the currently visible glass surfaces. */
internal object LiquidGlassLightSource : SensorEventListener {
    private val listeners = LinkedHashSet<View>()
    private val rotation = FloatArray(9)
    private var manager: SensorManager? = null
    private var unavailable = false
    private var initialized = false
    private var gravityX = 0f
    private var gravityY = 0f
    var angle = LiquidGlassStyle.DEFAULT_LIGHT_ANGLE
        private set

    fun register(view: View) {
        if (unavailable || !listeners.add(view)) return
        if (manager != null) return
        try {
            val next = view.context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = next?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: next?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (next == null || sensor == null || !next.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)) {
                unavailable = true
                listeners.clear()
                XposedCompat.log("[LiquidGlass] rotation sensor unavailable; using the fixed light angle")
                return
            }
            initialized = false
            manager = next
        } catch (failure: RuntimeException) {
            unavailable = true
            listeners.clear()
            XposedCompat.log("[LiquidGlass] rotation sensor unavailable: ${failure.message}")
        }
    }

    fun unregister(view: View) {
        if (!listeners.remove(view) || listeners.isNotEmpty()) return
        manager?.unregisterListener(this)
        manager = null
        initialized = false
        angle = LiquidGlassStyle.DEFAULT_LIGHT_ANGLE
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (listeners.isEmpty()) return
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val x = -rotation[6]
        val y = -rotation[7]
        if (!initialized) {
            gravityX = x
            gravityY = y
            initialized = true
        } else {
            gravityX += (x - gravityX) * 0.15f
            gravityY += (y - gravityY) * 0.15f
        }
        val next = gravityAngle(gravityX, gravityY)
        if (angle != next) {
            angle = next
            listeners.forEach { it.postInvalidateOnAnimation() }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun gravityAngle(x: Float, y: Float): Float {
        val step = (3.0 * PI / 180.0).toFloat()
        return if (x * x + y * y > 0.01f) {
            (atan2(y, x) / step).roundToInt() * step
        } else LiquidGlassStyle.DEFAULT_LIGHT_ANGLE
    }
}
