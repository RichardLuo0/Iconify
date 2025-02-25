package com.drdisagree.iconify.xposed.modules.launcher

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import com.drdisagree.iconify.data.common.Preferences.DOUBLE_TAP_TO_SLEEP
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.VibrationUtils
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.utils.SystemUtils.Companion.sleepDevice
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlin.math.hypot

class GestureMod(context: Context) : ModPack(context) {

    private var doubleTapToSleep = false
    private var firstTapTime: Long = 0
    private var firstTapX: Float = 0f
    private var firstTapY: Float = 0f
    private var isFirstTapRunning = false
    private var isFirstTapComplete = false

    override fun updatePrefs(vararg key: String) {
        doubleTapToSleep = Xprefs.getBoolean(DOUBLE_TAP_TO_SLEEP, false)
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val workspaceTouchListenerClass =
            findClass("com.android.launcher3.touch.WorkspaceTouchListener")

        workspaceTouchListenerClass
            .hookMethod("onTouch")
            .runAfter { param ->
                if (!doubleTapToSleep) return@runAfter

                val event = param.args[1] as MotionEvent

                // Sequence to detect: ↓↑↓
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val currentTime = SystemClock.uptimeMillis()
                        val totalTapDuration = currentTime - firstTapTime

                        if (totalTapDuration > DOUBLE_TAP_TIMEOUT) {
                            isFirstTapRunning = false
                            isFirstTapComplete = false
                        }

                        if (!isFirstTapRunning) {
                            // First event (ACTION_DOWN)
                            firstTapTime = currentTime
                            firstTapX = event.x
                            firstTapY = event.y
                            isFirstTapRunning = true
                        } else if (isFirstTapComplete) {
                            // Third event (ACTION_DOWN)
                            val distance = hypot(
                                (event.x - firstTapX).toDouble(),
                                (event.y - firstTapY).toDouble()
                            )

                            if (distance <= TAP_DISTANCE_THRESHOLD) {
                                if (doubleTapToSleep) {
                                    VibrationUtils.triggerVibration(mContext, 2)
                                    sleepDevice()
                                }
                            }

                            isFirstTapRunning = false
                            isFirstTapComplete = false
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        // Second event (ACTION_UP)
                        if (isFirstTapRunning && !isFirstTapComplete) {
                            isFirstTapComplete = true
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val distance = hypot(
                            (event.x - firstTapX).toDouble(),
                            (event.y - firstTapY).toDouble()
                        )

                        if (isFirstTapRunning && distance > TAP_DISTANCE_THRESHOLD) {
                            // If the user moves, cancel double-tap detection
                            isFirstTapRunning = false
                            isFirstTapComplete = false
                        }
                    }
                }
            }
    }

    companion object {
        private const val DOUBLE_TAP_TIMEOUT = 400L
        private const val TAP_DISTANCE_THRESHOLD = 50f
    }
}
