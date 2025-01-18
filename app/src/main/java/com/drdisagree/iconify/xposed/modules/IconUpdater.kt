package com.drdisagree.iconify.xposed.modules

import android.content.Context
import android.os.UserHandle
import com.drdisagree.iconify.BuildConfig
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookMethod
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class IconUpdater(context: Context) : ModPack(context) {

    private var launcherModel: Any? = null

    override fun updatePrefs(vararg key: String) {}

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val launcherModelClass = findClass("com.android.launcher3.LauncherModel")
        val baseDraggingActivityClass = findClass("com.android.launcher3.BaseDraggingActivity")

        launcherModelClass
            .hookConstructor()
            .runAfter { param -> launcherModel = param.thisObject }

        baseDraggingActivityClass
            .hookMethod("onResume")
            .suppressError()
            .runAfter {
                try {
                    if (launcherModel != null) {
                        callMethod(
                            launcherModel,
                            "onAppIconChanged",
                            BuildConfig.APPLICATION_ID,
                            UserHandle.getUserHandleForUid(0)
                        )
                    }
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }
    }

    companion object {
        private val TAG = "Iconify - ${IconUpdater::class.java.simpleName}: "
    }
}