package com.drdisagree.iconify.xposed.modules.launcher

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.drdisagree.iconify.data.common.Const.LAUNCHER3_PACKAGE
import com.drdisagree.iconify.data.common.Preferences.APP_DRAWER_THEMED_ICONS
import com.drdisagree.iconify.data.common.Preferences.FORCE_THEMED_ICONS
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.MonochromeIconFactory
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callStaticMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getAnyField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getExtraFieldSilently
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getStaticField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class ThemedIcons(context: Context) : ModPack(context) {

    private var forceThemedIcons: Boolean = false
    private var appDrawerThemedIcons: Boolean = false
    private var mIconDb: Any? = null
    private var mCache: Any? = null
    private var mModel: Any? = null

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            forceThemedIcons = getBoolean(FORCE_THEMED_ICONS, false)
            appDrawerThemedIcons = getBoolean(APP_DRAWER_THEMED_ICONS, false)
        }

        when (key.firstOrNull()) {
            in setOf(
                FORCE_THEMED_ICONS,
                APP_DRAWER_THEMED_ICONS
            ) -> reloadIcons()
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val baseIconCacheClass = findClass("com.android.launcher3.icons.cache.BaseIconCache")

        baseIconCacheClass
            .hookConstructor()
            .runAfter { param ->
                mIconDb = param.thisObject.getAnyField("mIconDb", "iconDb")
                mCache = param.thisObject.getAnyField("mCache", "cache")
            }

        val launcherAppStateClass = findClass("com.android.launcher3.LauncherAppState")

        launcherAppStateClass
            .hookConstructor()
            .runAfter { param ->
                mModel = param.thisObject.getField("mModel")
            }

        try {
            // Only for modified Launcher3
            if (mContext.packageName != LAUNCHER3_PACKAGE) throw Throwable()

            val launcherIconsClass = findClass("com.android.launcher3.icons.LauncherIcons")

            launcherIconsClass
                .hookMethod("getMonochromeDrawable")
                .throwError() // Available only in modified launcher3
                .runAfter { param ->
                    if (param.result == null && forceThemedIcons) {
                        val mIconBitmapSize = param.thisObject.getField("mIconBitmapSize") as Int

                        param.result = MonochromeIconFactory(mIconBitmapSize, true)
                            .wrap(mContext, param.args[0] as Drawable)
                    }
                }
        } catch (ignored: Throwable) {
            val baseIconFactoryClass = findClass("com.android.launcher3.icons.BaseIconFactory")

            baseIconFactoryClass
                .hookConstructor()
                .runAfter { param ->
                    val mIconBitmapSize = param.thisObject.getField("mIconBitmapSize") as Int
                    val monochromeIconFactory = MonochromeIconFactory(mIconBitmapSize, false)

                    AdaptiveIconDrawable::class.java
                        .hookMethod("getMonochrome")
                        .runAfter runAfter2@{ param2 ->
                            if (param2.result == null && forceThemedIcons) {
                                // If it's from com.android.launcher3.icons.IconProvider class and
                                // mentioned methods, monochrome is already included
                                Thread.currentThread().stackTrace.firstOrNull {
                                    it.className.contains("IconProvider") && it.methodName in listOf(
                                        "getIconWithOverrides",
                                        "getIcon"
                                    )
                                }?.let { return@runAfter2 }

                                var monochromeIcon = param2.thisObject
                                    .getExtraFieldSilently("mMonochromeIcon") as? Drawable

                                if (monochromeIcon == null) {
                                    monochromeIcon = monochromeIconFactory.wrap(
                                        mContext,
                                        param2.thisObject as Drawable
                                    )
                                    setAdditionalInstanceField(
                                        param2.thisObject,
                                        "mMonochromeIcon",
                                        monochromeIcon
                                    )
                                }

                                param2.result = monochromeIcon
                            }
                        }
                }
        }

        val bubbleTextViewClass = findClass("com.android.launcher3.BubbleTextView")
        val themesClass = findClass("com.android.launcher3.util.Themes")
        val themeManagerClass = findClass(
            "com.android.launcher3.graphics.ThemeManager",
            suppressError = true
        )

        bubbleTextViewClass
            .hookMethod("shouldUseTheme")
            .runAfter { param ->
                if (param.result == false && appDrawerThemedIcons) {
                    param.result = param.thisObject.getField("mDisplay") in setOf(
                        DISPLAY_ALL_APPS,
                        DISPLAY_SEARCH_RESULT,
                        DISPLAY_SEARCH_RESULT_SMALL,
                        DISPLAY_PREDICTION_ROW,
                        DISPLAY_SEARCH_RESULT_APP_ROW
                    ) && try {
                        themesClass.callStaticMethod(
                            "isThemedIconEnabled",
                            param.thisObject.callMethod("getContext")
                        )
                    } catch (ignored: Throwable) {
                        val themeManagerInstance = themeManagerClass
                            .getStaticField("INSTANCE")
                            .callMethod(
                                "get",
                                param.thisObject.callMethod("getContext")
                            )
                        themeManagerInstance.callMethod("isMonoThemeEnabled")
                    } as Boolean
                }
            }
    }

    private fun reloadIcons() {
        Handler(Looper.getMainLooper()).post {
            mCache.callMethod("clear")
            mIconDb.callMethod("clear")
            mModel.callMethod("forceReload")
        }
    }

    companion object {
        const val DISPLAY_ALL_APPS: Int = 1
        const val DISPLAY_SEARCH_RESULT: Int = 6
        const val DISPLAY_SEARCH_RESULT_SMALL: Int = 7
        const val DISPLAY_PREDICTION_ROW: Int = 8
        const val DISPLAY_SEARCH_RESULT_APP_ROW: Int = 9
    }
}