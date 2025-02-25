package com.drdisagree.iconify.xposed.modules.extras.views

import android.view.View
import android.view.animation.TranslateAnimation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AodBurnInProtection(private val view: View) {

    private var isMovementEnabled: Boolean = false
    private var originalX: Float = 0f
    private var originalY: Float = 0f
    private var movementJob: Job? = null

    init {
        originalX = view.x
        originalY = view.y
    }

    fun setMovementEnabled(enabled: Boolean) {
        if (enabled != isMovementEnabled) {
            isMovementEnabled = enabled

            if (isMovementEnabled) {
                startMovement()
            } else {
                stopMovement()
            }
        }
    }

    private fun startMovement() {
        if (movementJob?.isActive == true) return

        originalX = view.x
        originalY = view.y

        movementJob = CoroutineScope(Dispatchers.Main).launch {
            while (isMovementEnabled) {
                moveViewSlightly()
                delay(45 * 60 * 1000L) // Delay for 45 minutes
            }
        }
    }

    private fun stopMovement() {
        movementJob?.cancel()
        resetViewPosition()
    }

    private fun moveViewSlightly() {
        val offsetX = (6..10).random().toFloat() * if (Math.random() > 0.5) 1 else -1
        val offsetY = (6..10).random().toFloat() * if (Math.random() > 0.5) 1 else -1

        val animation = TranslateAnimation(view.x, offsetX, view.y, offsetY)
        animation.duration = 1000 // 1 second duration for the movement
        animation.fillAfter = true // Keep the final position after the animation finishes

        view.startAnimation(animation)
    }

    private fun resetViewPosition() {
        view.animate().x(originalX).y(originalY).setDuration(300).start()
    }

    companion object {
        private val activeMovements = mutableMapOf<View, AodBurnInProtection>()

        fun registerForView(view: View): AodBurnInProtection {
            return activeMovements.getOrPut(view) {
                AodBurnInProtection(view).also {
                    it.startMovement()
                }
            }
        }

        fun unregisterForView(view: View) {
            activeMovements[view]?.apply {
                stopMovement()
            }
            activeMovements.remove(view)
        }
    }
}