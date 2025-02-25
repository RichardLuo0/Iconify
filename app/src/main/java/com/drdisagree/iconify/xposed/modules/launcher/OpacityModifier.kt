package com.drdisagree.iconify.xposed.modules.launcher

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.drdisagree.iconify.data.common.Preferences.APP_DRAWER_BACKGROUND_OPACITY
import com.drdisagree.iconify.data.common.Preferences.DISABLE_RECENTS_BLUR
import com.drdisagree.iconify.data.common.Preferences.RECENTS_BACKGROUND_OPACITY
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethodSilently
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setFieldSilently
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class OpacityModifier(context: Context) : ModPack(context) {

    private var baseDepthControllerInstance: Any? = null
    private var appDrawerBackgroundOpacity: Int = 100
    private var recentsBackgroundOpacity: Int = 100
    private var disableRecentsBlur: Boolean = false
    private var originalBlurValue: Int = -1

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            appDrawerBackgroundOpacity =
                getSliderInt(APP_DRAWER_BACKGROUND_OPACITY, 100) * 255 / 100
            recentsBackgroundOpacity = getSliderInt(RECENTS_BACKGROUND_OPACITY, 100) * 255 / 100
            disableRecentsBlur = getBoolean(DISABLE_RECENTS_BLUR, false)
        }

        when (key.firstOrNull()) {
            in setOf(DISABLE_RECENTS_BLUR) -> reloadDepthAndBlur()
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val allAppsStateClass = findClass("com.android.launcher3.uioverrides.states.AllAppsState")
        val overviewStateClass = findClass("com.android.launcher3.uioverrides.states.OverviewState")
        val quickSwitchStateClass =
            findClass("com.android.launcher3.uioverrides.states.QuickSwitchState")
        val recentsStateClass = findClass("com.android.quickstep.fallback.RecentsState")
        val hintStateClass = findClass("com.android.launcher3.states.HintState")
        val activityAllAppsContainerViewClass =
            findClass("com.android.launcher3.allapps.ActivityAllAppsContainerView")

        activityAllAppsContainerViewClass
            .hookMethod("updateHeaderScroll")
            .runAfter { param ->
                if (appDrawerBackgroundOpacity != 255) {
                    param.thisObject.setFieldSilently("mHeaderColor", Color.TRANSPARENT)
                    param.thisObject.callMethodSilently("invalidateHeader")
                }
            }

        allAppsStateClass
            .hookMethod("getWorkspaceScrimColor")
            .runAfter { param ->
                param.result = ColorUtils.setAlphaComponent(
                    param.result as Int,
                    appDrawerBackgroundOpacity
                )
            }

        overviewStateClass
            .hookMethod("getWorkspaceScrimColor")
            .runAfter { param ->
                param.result = ColorUtils.setAlphaComponent(
                    param.result as Int,
                    recentsBackgroundOpacity
                )
            }

        quickSwitchStateClass
            .hookMethod("getWorkspaceScrimColor")
            .runAfter { param ->
                val launcher = param.args[0]
                val deviceProfile = launcher.callMethod("getDeviceProfile")
                val isTaskbarPresentInApps =
                    deviceProfile.callMethodSilently("isTaskbarPresentInApps") as? Boolean
                        ?: deviceProfile.getField("isTaskbarPresentInApps") as Boolean
                val currentResult = param.result as Int

                if (currentResult != Color.TRANSPARENT && !isTaskbarPresentInApps) {
                    param.result = ColorUtils.setAlphaComponent(
                        param.result as Int,
                        recentsBackgroundOpacity
                    )
                }
            }

        recentsStateClass
            .hookMethod("getScrimColor")
            .runAfter { param ->
                param.result = ColorUtils.setAlphaComponent(
                    param.result as Int,
                    recentsBackgroundOpacity
                )
            }

        hintStateClass
            .hookMethod("getWorkspaceScrimColor")
            .runAfter { param ->
                param.result = ColorUtils.setAlphaComponent(
                    param.result as Int,
                    recentsBackgroundOpacity
                )
            }

        val baseDepthControllerClass = findClass("com.android.quickstep.util.BaseDepthController")

        baseDepthControllerClass
            .hookConstructor()
            .runAfter { param ->
                baseDepthControllerInstance = param.thisObject

                if (originalBlurValue == -1) {
                    val mMaxBlurRadius = param.thisObject.getField("mMaxBlurRadius")

                    originalBlurValue = if (mMaxBlurRadius is Float) {
                        mMaxBlurRadius.toInt()
                    } else {
                        mMaxBlurRadius as Int
                    }
                }

                reloadDepthAndBlur()
            }
    }

    private fun reloadDepthAndBlur() {
        if (baseDepthControllerInstance == null || originalBlurValue == -1) return

        if (disableRecentsBlur) {
            baseDepthControllerInstance.setField("mMaxBlurRadius", 0)
        } else {
            baseDepthControllerInstance.setField("mMaxBlurRadius", originalBlurValue)
        }
    }
}