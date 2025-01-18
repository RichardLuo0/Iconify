package com.drdisagree.iconify.xposed.modules.utils

import android.content.Context
import android.view.View
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.toolkit.XposedHook.Companion.findClass
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@Suppress("unused")
class MyConstraintSet(context: Context) : ModPack(context) {

    override fun updatePrefs(vararg key: String) {}

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        ConstraintSetClass = findClass("androidx.constraintlayout.widget.ConstraintSet")
    }

    companion object {
        private var ConstraintSetClass: Class<*>? = null

        // We use reflection otherwise we get ClassCastException
        val constraintSetInstance: Any?
            get() = ConstraintSetClass?.getDeclaredConstructor()?.newInstance()

        fun Any.clone(view: View) {
            callMethod(this, "clone", view)
        }

        fun Any.applyTo(view: View) {
            callMethod(this, "applyTo", view)
        }

        fun Any.connect(startID: Int, startSide: Int, endID: Int, endSide: Int) {
            callMethod(this, "connect", startID, startSide, endID, endSide)
        }

        fun Any.connect(startID: Int, startSide: Int, endID: Int, endSide: Int, margin: Int) {
            callMethod(this, "connect", startID, startSide, endID, endSide, margin)
        }

        fun Any.clear(viewId: Int, anchor: Int) {
            callMethod(this, "clear", viewId, anchor)
        }

        fun Any.setMargin(viewId: Int, anchor: Int, value: Int) {
            callMethod(this, "setMargin", viewId, anchor, value)
        }

        fun Any.createBarrier(id: Int, direction: Int, margin: Int, vararg referenced: Int) {
            val method = this::class.java.getDeclaredMethod(
                "createBarrier",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                IntArray::class.java
            )
            method.isAccessible = true
            method.invoke(this, id, direction, margin, referenced)
        }
    }
}