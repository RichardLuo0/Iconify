package com.drdisagree.iconify.xposed.modules

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WALLPAPER_BLUR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WALLPAPER_BLUR_RADIUS
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.ViewHelper.applyBlur
import com.drdisagree.iconify.xposed.modules.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class Lockscreen(context: Context) : ModPack(context) {

    private var wallpaperBlurEnabled = false
    private var wallpaperBlurRadius = 25

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            wallpaperBlurEnabled = getBoolean(LOCKSCREEN_WALLPAPER_BLUR, false)
            wallpaperBlurRadius = getSliderInt(LOCKSCREEN_WALLPAPER_BLUR_RADIUS, 25)
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        blurredWallpaper()
    }

    private fun blurredWallpaper() {
        val canvasEngineClass =
            findClass("$SYSTEMUI_PACKAGE.wallpapers.ImageWallpaper\$CanvasEngine")

        canvasEngineClass
            .hookMethod("drawFrameOnCanvas")
            .parameters(Bitmap::class.java)
            .runBefore { param ->
                val canvasEngine = param.thisObject
                val isLockscreenWallpaper = isLockScreenWallpaper(canvasEngine)

                if (wallpaperBlurEnabled && wallpaperBlurRadius > 0 && isLockscreenWallpaper) {
                    val bitmap = param.args[0] as Bitmap
                    val displayContext = callMethod(
                        canvasEngine,
                        "getDisplayContext"
                    ) as Context

                    param.args[0] = bitmap.applyBlur(displayContext, wallpaperBlurRadius.toFloat())
                }
            }
    }

    private fun isLockScreenWallpaper(canvasEngine: Any): Boolean {
        return callMethod(canvasEngine, "getWallpaperFlags") as Int == WallpaperManager.FLAG_LOCK
    }

    companion object {
        private val TAG = "Iconify - ${Lockscreen::class.java.simpleName}: "
    }
}