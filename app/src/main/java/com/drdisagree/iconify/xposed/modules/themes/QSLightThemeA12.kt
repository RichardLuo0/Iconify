package com.drdisagree.iconify.xposed.modules.themes

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.DUALTONE_QSPANEL
import com.drdisagree.iconify.common.Preferences.LIGHT_QSPANEL
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.utils.SystemUtils
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
class QSLightThemeA12(context: Context) : ModPack(context) {

    private var mBehindColors: Any? = null
    private var isDark: Boolean
    private val qsLightThemeOverlay = "IconifyComponentQSLT.overlay"
    private val qsDualToneOverlay = "IconifyComponentQSDT.overlay"

    init {
        isDark = SystemUtils.isDarkMode
    }

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            lightQSHeaderEnabled = getBoolean(LIGHT_QSPANEL, false)
            dualToneQSEnabled = lightQSHeaderEnabled && getBoolean(DUALTONE_QSPANEL, false)
        }

        if (key.isNotEmpty()) {
            key[0].let {
                if (it == LIGHT_QSPANEL || it == DUALTONE_QSPANEL) {
                    applyOverlays(true)
                }
            }
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val utilsClass = findClass("com.android.settingslib.Utils")
        val ongoingPrivacyChipClass = findClass("$SYSTEMUI_PACKAGE.privacy.OngoingPrivacyChip")
        val fragmentHostManagerClass = findClass("$SYSTEMUI_PACKAGE.fragments.FragmentHostManager")
        val scrimControllerClass = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimController")
        val gradientColorsClass =
            findClass("com.android.internal.colorextraction.ColorExtractor.GradientColors")!!
        val statusbarClass = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.StatusBar")
        val interestingConfigChangesClass =
            findClass("com.android.settingslib.applications.InterestingConfigChanges")!!
        val scrimStateEnum = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimState")!!

        try {
            mBehindColors = gradientColorsClass.getDeclaredConstructor().newInstance()
        } catch (throwable: Throwable) {
            log(TAG + throwable)
        }

        scrimControllerClass
            .hookMethod("onUiModeChanged")
            .runAfter {
                try {
                    mBehindColors = gradientColorsClass.getDeclaredConstructor().newInstance()
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        scrimControllerClass
            .hookMethod("updateScrims")
            .runAfter { param ->
                if (!dualToneQSEnabled) return@runAfter

                try {
                    val mScrimBehind = getObjectField(param.thisObject, "mScrimBehind")
                    val mBlankScreen = getObjectField(param.thisObject, "mBlankScreen") as Boolean
                    val alpha = callMethod(mScrimBehind, "getViewAlpha") as Float
                    val animateBehindScrim = alpha != 0f && !mBlankScreen

                    callMethod(
                        mScrimBehind,
                        "setColors",
                        mBehindColors,
                        animateBehindScrim
                    )
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        scrimControllerClass
            .hookMethod("updateThemeColors")
            .runAfter {
                if (!dualToneQSEnabled) return@runAfter

                try {
                    @SuppressLint("DiscouragedApi") val states = callStaticMethod(
                        utilsClass,
                        "getColorAttr",
                        mContext,
                        mContext.resources.getIdentifier(
                            "android:attr/colorSurfaceHeader",
                            "attr",
                            mContext.packageName
                        )
                    ) as ColorStateList

                    val surfaceBackground = states.defaultColor
                    val accentStates = callStaticMethod(
                        utilsClass,
                        "getColorAccent",
                        mContext
                    ) as ColorStateList
                    val accent = accentStates.defaultColor

                    callMethod(mBehindColors, "setMainColor", surfaceBackground)
                    callMethod(mBehindColors, "setSecondaryColor", accent)

                    val contrast = ColorUtils.calculateContrast(
                        callMethod(
                            mBehindColors,
                            "getMainColor"
                        ) as Int, Color.WHITE
                    )

                    callMethod(mBehindColors, "setSupportsDarkText", contrast > 4.5)
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        ongoingPrivacyChipClass
            .hookMethod("updateResources")
            .runAfter { param ->
                if (!lightQSHeaderEnabled) return@runAfter

                try {
                    val iconColor = mContext.resources.getColor(
                        mContext.resources.getIdentifier(
                            "android:color/system_neutral1_900",
                            "color",
                            mContext.packageName
                        ), mContext.theme
                    )

                    setObjectField(param.thisObject, "iconColor", iconColor)
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        scrimControllerClass
            .hookMethod("applyStateToAlpha", "applyState")
            .runAfter { param ->
                if (!lightQSHeaderEnabled) return@runAfter

                try {
                    val mClipsQsScrim =
                        getObjectField(param.thisObject, "mClipsQsScrim") as Boolean

                    if (mClipsQsScrim) {
                        setObjectField(param.thisObject, "mBehindTint", Color.TRANSPARENT)
                    }
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        val constants: Array<out Any> = scrimStateEnum.enumConstants ?: arrayOf()
        constants.forEach { constant ->
            when (constant.toString()) {
                "KEYGUARD" -> constant.javaClass
                    .hookMethod("prepare")
                    .parameters(scrimStateEnum)
                    .runAfter { param ->
                        if (!lightQSHeaderEnabled) return@runAfter

                        val mClipQsScrim = getObjectField(
                            param.thisObject,
                            "mClipQsScrim"
                        ) as Boolean

                        if (mClipQsScrim) {
                            callMethod(
                                param.thisObject,
                                "updateScrimColor",
                                getObjectField(param.thisObject, "mScrimBehind"),
                                1f,
                                Color.TRANSPARENT
                            )
                        }
                    }

                "BOUNCER" -> constant.javaClass
                    .hookMethod("prepare")
                    .parameters(scrimStateEnum)
                    .runAfter { param ->
                        if (!lightQSHeaderEnabled) return@runAfter

                        setObjectField(
                            param.thisObject,
                            "mBehindTint",
                            Color.TRANSPARENT
                        )
                    }

                "SHADE_LOCKED" -> {
                    constant.javaClass
                        .hookMethod("prepare")
                        .runAfter { param ->
                            if (!lightQSHeaderEnabled) return@runAfter

                            setObjectField(
                                param.thisObject,
                                "mBehindTint",
                                Color.TRANSPARENT
                            )

                            val mClipQsScrim = getObjectField(
                                param.thisObject,
                                "mClipQsScrim"
                            ) as Boolean

                            if (mClipQsScrim) {
                                callMethod(
                                    param.thisObject,
                                    "updateScrimColor",
                                    getObjectField(param.thisObject, "mScrimBehind"),
                                    1f,
                                    Color.TRANSPARENT
                                )
                            }
                        }

                    constant.javaClass
                        .hookMethod("getBehindTint")
                        .suppressError()
                        .runAfter { param ->
                            if (!lightQSHeaderEnabled) return@runAfter

                            param.result = Color.TRANSPARENT
                        }
                }

                "UNLOCKED" -> constant.javaClass
                    .hookMethod("prepare")
                    .parameters(scrimStateEnum)
                    .runAfter { param ->
                        if (!lightQSHeaderEnabled) return@runAfter

                        setObjectField(
                            param.thisObject,
                            "mBehindTint",
                            Color.TRANSPARENT
                        )

                        callMethod(
                            param.thisObject,
                            "updateScrimColor",
                            getObjectField(param.thisObject, "mScrimBehind"),
                            1f,
                            Color.TRANSPARENT
                        )
                    }
            }
        }

        fragmentHostManagerClass
            .hookConstructor()
            .runBefore { param ->
                if (!lightQSHeaderEnabled) return@runBefore

                try {
                    setObjectField(
                        param.thisObject,
                        "mConfigChanges",
                        interestingConfigChangesClass.getDeclaredConstructor(
                            Int::class.javaPrimitiveType
                        ).newInstance(0x40000000 or 0x0004 or 0x0100 or -0x80000000 or 0x0200)
                    )
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        statusbarClass
            .hookConstructor()
            .runAfter { param ->
                try {
                    getObjectField(param.thisObject, "mOnColorsChangedListener").javaClass
                        .hookMethod("onColorsChanged")
                        .runAfter { applyOverlays(true) }
                } catch (ignored: Throwable) {
                }
            }
    }

    @Suppress("SameParameterValue")
    private fun applyOverlays(force: Boolean) {
        val isCurrentlyDark: Boolean = SystemUtils.isDarkMode
        if (isCurrentlyDark == isDark && !force) return

        isDark = isCurrentlyDark

        Utils.disableOverlays(qsLightThemeOverlay, qsDualToneOverlay)

        try {
            Thread.sleep(50)
        } catch (ignored: Throwable) {
        }

        if (lightQSHeaderEnabled) {
            if (!isCurrentlyDark) {
                Utils.enableOverlay(qsLightThemeOverlay)
            }
            if (dualToneQSEnabled) Utils.enableOverlay(qsDualToneOverlay)
        }
    }

    companion object {
        private val TAG = "Iconify - ${QSLightThemeA12::class.java.simpleName}: "
        private var lightQSHeaderEnabled = false
        private var dualToneQSEnabled = false
    }
}