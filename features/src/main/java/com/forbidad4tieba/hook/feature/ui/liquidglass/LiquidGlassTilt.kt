// Gravity projection/smoothing adapted from compose-miuix-ui 0.9.3 (Apache-2.0).
// Quantization and light directions follow KernelSU's FloatingBottomBar.
package com.forbidad4tieba.hook.feature.ui.liquidglass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import com.forbidad4tieba.hook.core.XposedCompat
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

/** One listener shared by both glass layers; only active while the bar is on screen. */
internal class LiquidGlassTilt(
    context: Context,
    private val displayRotation: () -> Int,
    private val onAngleChanged: (Float) -> Unit,
) : SensorEventListener {
    internal companion object {
        val REST_ANGLE = (-PI / 2).toFloat()
        private val ANGLE_STEP = (3.0 * PI / 180.0).toFloat()

        fun quantizedAngle(x: Float, y: Float): Float {
            if (!x.isFinite() || !y.isFinite() || x * x + y * y <= 0.01f) return REST_ANGLE
            return (atan2(y, x) / ANGLE_STEP).roundToInt() * ANGLE_STEP
        }
    }

    private val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotation = FloatArray(9)
    private var registered = false
    private var failed = false
    private var initialized = false
    private var smoothX = 0f
    private var smoothY = 0f
    private var angle = REST_ANGLE

    fun setActive(active: Boolean) {
        if (!active) {
            if (registered) manager?.unregisterListener(this)
            registered = false
            initialized = false
        } else if (!registered && !failed && sensor != null && manager != null) {
            try {
                registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
                if (!registered) {
                    failed = true
                    XposedCompat.log("[LiquidGlassTilt] rotation sensor registration rejected")
                }
            } catch (t: RuntimeException) {
                failed = true
                XposedCompat.log("[LiquidGlassTilt] rotation sensor unavailable: ${t.message}")
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!registered) return
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val x = -rotation[6]
        val y = -rotation[7]
        val screenX: Float
        val screenY: Float
        when (displayRotation()) {
            Surface.ROTATION_90 -> { screenX = y; screenY = -x }
            Surface.ROTATION_180 -> { screenX = -x; screenY = -y }
            Surface.ROTATION_270 -> { screenX = -y; screenY = x }
            else -> { screenX = x; screenY = y }
        }
        if (!initialized) {
            smoothX = screenX
            smoothY = screenY
            initialized = true
        } else {
            smoothX += (screenX - smoothX) * 0.15f
            smoothY += (screenY - smoothY) * 0.15f
        }
        val next = quantizedAngle(smoothX, smoothY)
        if (angle == next) return
        angle = next
        onAngleChanged(next)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
