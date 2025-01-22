package com.drdisagree.iconify.xposed.modules.lockscreen

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.res.XResources
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.HIDE_LOCKSCREEN_LOCK_ICON
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WALLPAPER_BLUR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WALLPAPER_BLUR_RADIUS
import com.drdisagree.iconify.xposed.HookRes.Companion.resParams
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.TimeUtils.isSecurityPatchAfter
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.applyBlur
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.hideView
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookLayout
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Calendar

class Lockscreen(context: Context) : ModPack(context) {

    private var wallpaperBlurEnabled = false
    private var wallpaperBlurRadius = 25
    private var hideLockscreenLockIcon = false

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            wallpaperBlurEnabled = getBoolean(LOCKSCREEN_WALLPAPER_BLUR, false)
            wallpaperBlurRadius = getSliderInt(LOCKSCREEN_WALLPAPER_BLUR_RADIUS, 25)
            hideLockscreenLockIcon = getBoolean(HIDE_LOCKSCREEN_LOCK_ICON, false)
        }

        when (key.firstOrNull()) {
            HIDE_LOCKSCREEN_LOCK_ICON -> hideLockscreenLockIcon()
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        blurredWallpaper()
        hideLockscreenLockIcon()
    }

    private fun blurredWallpaper() {
        val canvasEngineClass =
            findClass("$SYSTEMUI_PACKAGE.wallpapers.ImageWallpaper\$CanvasEngine")

        canvasEngineClass
            .hookMethod("drawFrameOnCanvas")
            .parameters(Bitmap::class.java)
            .runBefore { param ->
                val canvasEngine = param.thisObject
                val isLockscreenWallpaper = canvasEngine.callMethod(
                    "getWallpaperFlags"
                ) as Int == WallpaperManager.FLAG_LOCK

                if (wallpaperBlurEnabled && wallpaperBlurRadius > 0 && isLockscreenWallpaper) {
                    val bitmap = param.args[0] as Bitmap
                    val displayContext = canvasEngine.callMethod("getDisplayContext") as Context

                    param.args[0] = bitmap.applyBlur(displayContext, wallpaperBlurRadius.toFloat())
                }
            }
    }

    @SuppressLint("DiscouragedApi")
    private fun hideLockscreenLockIcon() {
        if (!isComposeLockscreen) {
            val xResources: XResources = resParams[SYSTEMUI_PACKAGE]?.res ?: return

            xResources
                .hookLayout()
                .packageName(SYSTEMUI_PACKAGE)
                .resource("layout", "status_bar_expanded")
                .suppressError()
                .run { liparam ->
                    liparam.view.findViewById<View>(
                        liparam.res.getIdentifier(
                            "lock_icon_view",
                            "id",
                            mContext.packageName
                        )
                    ).apply {
                        if (!hideLockscreenLockIcon) return@apply

                        layoutParams.height = 0
                        layoutParams.width = 0
                        visibility = View.GONE
                        viewTreeObserver.addOnDrawListener {
                            visibility = View.GONE
                        }
                        requestLayout()
                    }
                }
        } else {
            val aodBurnInLayerClass =
                findClass("$SYSTEMUI_PACKAGE.keyguard.ui.view.layout.sections.AodBurnInLayer")

            aodBurnInLayerClass
                .hookConstructor()
                .runAfter { param ->
                    if (!hideLockscreenLockIcon) return@runAfter

                    val entryV = param.thisObject as View

                    entryV.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (!hideLockscreenLockIcon) return@postDelayed

                                val rootView = entryV.parent as ViewGroup

                                listOf(
                                    "device_entry_icon_bg",
                                    "device_entry_icon_fg"
                                ).map { resourceName ->
                                    val resourceId = mContext.resources.getIdentifier(
                                        resourceName,
                                        "id",
                                        mContext.packageName
                                    )
                                    if (resourceId != -1) {
                                        rootView.findViewById<View?>(resourceId)
                                    } else {
                                        null
                                    }
                                }.forEach { view ->
                                    view.hideView()
                                }
                            }, 1000)
                        }

                        override fun onViewDetachedFromWindow(v: View) {}
                    })
                }
        }
    }

    companion object {
        val isComposeLockscreen = findClass(
            "$SYSTEMUI_PACKAGE.keyguard.ui.view.layout.sections.AodBurnInLayer",
            suppressError = true
        ) != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && isSecurityPatchAfter(
            Calendar.getInstance().apply { set(2024, Calendar.NOVEMBER, 30) }
        )
    }
}