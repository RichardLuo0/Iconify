package com.drdisagree.iconify.xposed.modules.themes

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.service.quicksettings.Tile
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.SettingsLibUtils.Companion.getColorAttr
import com.drdisagree.iconify.xposed.modules.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookMethodMatchPattern
import com.drdisagree.iconify.xposed.utils.SystemUtils
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getFloatField
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
class QSBlackThemeA13(context: Context) : ModPack(context) {

    private var mBehindColors: Any? = null
    private var isDark: Boolean
    private var colorText: Int? = null
    private var colorTextAlpha: Int? = null
    private var mClockViewQSHeader: Any? = null
    private var qsTextAlwaysWhite = false
    private var qsTextFollowAccent = false

    init {
        isDark = SystemUtils.isDarkMode
    }

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            blackQSHeaderEnabled = getBoolean(Preferences.BLACK_QSPANEL, false)
            qsTextAlwaysWhite = getBoolean(Preferences.QS_TEXT_ALWAYS_WHITE, false)
            qsTextFollowAccent = getBoolean(Preferences.QS_TEXT_FOLLOW_ACCENT, false)
        }

        initColors(true)
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val qsTileViewImplClass = findClass("$SYSTEMUI_PACKAGE.qs.tileimpl.QSTileViewImpl")
        val fragmentHostManagerClass = findClass("$SYSTEMUI_PACKAGE.fragments.FragmentHostManager")
        val scrimControllerClass = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimController")
        val gradientColorsClass =
            findClass("com.android.internal.colorextraction.ColorExtractor\$GradientColors")!!
        val qsPanelControllerClass = findClass("$SYSTEMUI_PACKAGE.qs.QSPanelController")
        val interestingConfigChangesClass =
            findClass("com.android.settingslib.applications.InterestingConfigChanges")!!
        val scrimStateEnum = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimState")!!
        val qsIconViewImplClass = findClass("$SYSTEMUI_PACKAGE.qs.tileimpl.QSIconViewImpl")
        val centralSurfacesImplClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.phone.CentralSurfacesImpl",
            suppressError = true
        )
        val clockClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.policy.Clock",
            suppressError = true
        )
        val quickStatusBarHeaderClass = findClass("$SYSTEMUI_PACKAGE.qs.QuickStatusBarHeader")
        val brightnessControllerClass =
            findClass("$SYSTEMUI_PACKAGE.settings.brightness.BrightnessController")
        val brightnessMirrorControllerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.policy.BrightnessMirrorController")
        val brightnessSliderControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.settings.brightness.BrightnessSliderController",
            suppressError = true
        )

        qsPanelControllerClass
            .hookConstructor()
            .runAfter { calculateColors() }

        try { // QPR1
            val qsFgsManagerFooterClass = findClass(
                "$SYSTEMUI_PACKAGE.qs.QSFgsManagerFooter",
                throwException = true
            )

            qsFgsManagerFooterClass
                .hookConstructor()
                .runAfter { param ->
                    if (!isDark && blackQSHeaderEnabled) {
                        try {
                            (getObjectField(param.thisObject, "mNumberContainer") as View)
                                .background.setTint(colorText!!)
                        } catch (throwable: Throwable) {
                            log(TAG + throwable)
                        }
                    }
                }
        } catch (throwable: Throwable) { // QPR2&3
            // QPR3
            val shadeHeaderControllerClass = findClass(
                "$SYSTEMUI_PACKAGE.shade.ShadeHeaderController",
                "$SYSTEMUI_PACKAGE.shade.LargeScreenShadeHeaderController",
                suppressError = true
            )
            val qsContainerImplClass = findClass("$SYSTEMUI_PACKAGE.qs.QSContainerImpl")

            shadeHeaderControllerClass
                .hookMethod("onInit")
                .runAfter { param ->
                    try {
                        val mView =
                            getObjectField(param.thisObject, "mView") as View
                        val iconManager =
                            getObjectField(param.thisObject, "iconManager")
                        val batteryIcon =
                            getObjectField(param.thisObject, "batteryIcon")
                        val configurationControllerListener = getObjectField(
                            param.thisObject,
                            "configurationControllerListener"
                        )

                        configurationControllerListener.javaClass
                            .hookMethod(
                                "onConfigChanged",
                                "onDensityOrFontScaleChanged",
                                "onUiModeChanged",
                                "onThemeChanged"
                            )
                            .runAfter { setHeaderComponentsColor(mView, iconManager, batteryIcon) }

                        setHeaderComponentsColor(mView, iconManager, batteryIcon)
                    } catch (throwable: Throwable) {
                        log(TAG + throwable)
                    }
                }

            qsContainerImplClass
                .hookMethod("updateResources")
                .runAfter { param ->
                    if (!blackQSHeaderEnabled) return@runAfter

                    try {
                        val res = mContext.resources
                        val view = param.thisObject as ViewGroup

                        val settingsButtonContainer = view.findViewById<View>(
                            res.getIdentifier(
                                "settings_button_container",
                                "id",
                                mContext.packageName
                            )
                        )

                        val icon = settingsButtonContainer.findViewById<ImageView>(
                            res.getIdentifier(
                                "icon",
                                "id",
                                mContext.packageName
                            )
                        )

                        icon.imageTintList = ColorStateList.valueOf(Color.WHITE)
                    } catch (throwable: Throwable) {
                        log(TAG + throwable)
                    }
                }
        }

        // QS tile primary label color
        qsTileViewImplClass
            .hookMethod("getLabelColorForState")
            .runBefore { param ->
                if (!blackQSHeaderEnabled) return@runBefore

                try {
                    if (param.args[0] as Int == Tile.STATE_ACTIVE) {
                        param.result = colorText
                    }
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        // QS tile secondary label color
        qsTileViewImplClass
            .hookMethod("getSecondaryLabelColorForState")
            .runBefore { param ->
                if (!blackQSHeaderEnabled) return@runBefore

                try {
                    if (param.args[0] as Int == Tile.STATE_ACTIVE) {
                        param.result = colorText
                    }
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        // Auto Brightness Icon Color
        brightnessControllerClass
            .hookMethod("updateIcon")
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    (getObjectField(param.thisObject, "mIcon") as ImageView).imageTintList =
                        ColorStateList.valueOf(colorText!!)
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        brightnessSliderControllerClass
            .hookConstructor()
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    (getObjectField(param.thisObject, "mIcon") as ImageView).imageTintList =
                        ColorStateList.valueOf(colorText!!)
                } catch (throwable: Throwable) {
                    try {
                        (getObjectField(
                            param.thisObject,
                            "mIconView"
                        ) as ImageView).imageTintList =
                            ColorStateList.valueOf(colorText!!)
                    } catch (throwable1: Throwable) {
                        log(TAG + throwable1)
                    }
                }
            }

        brightnessMirrorControllerClass
            .hookMethod("updateIcon")
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    (getObjectField(param.thisObject, "mIcon") as ImageView).imageTintList =
                        ColorStateList.valueOf(colorText!!)
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        qsIconViewImplClass
            .hookMethod("setIcon")
            .runBefore { param ->
                if (!blackQSHeaderEnabled) return@runBefore

                try {
                    if (param.args[0] is ImageView &&
                        getIntField(param.args[1], "state") == Tile.STATE_ACTIVE
                    ) {
                        setObjectField(param.thisObject, "mTint", colorText)
                    }
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        // White QS Clock bug
        quickStatusBarHeaderClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                try {
                    mClockViewQSHeader =
                        getObjectField(param.thisObject, "mClockView")
                } catch (ignored: Throwable) {
                }

                if (blackQSHeaderEnabled && mClockViewQSHeader != null) {
                    try {
                        (mClockViewQSHeader as TextView).setTextColor(Color.WHITE)
                    } catch (throwable: Throwable) {
                        log(TAG + throwable)
                    }
                }
            }

        // White QS Clock bug
        clockClass
            .hookMethod("onColorsChanged")
            .suppressError()
            .runAfter {
                if (!blackQSHeaderEnabled) return@runAfter

                (mClockViewQSHeader as? TextView)?.setTextColor(Color.WHITE)
            }

        centralSurfacesImplClass
            .hookConstructor()
            .runAfter { initColors(true) }

        centralSurfacesImplClass
            .hookMethod("updateTheme")
            .runAfter { initColors(false) }

        qsTileViewImplClass
            .hookConstructor()
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    if (!qsTextAlwaysWhite && !qsTextFollowAccent) {
                        setObjectField(
                            param.thisObject,
                            "colorLabelActive",
                            colorText
                        )

                        setObjectField(
                            param.thisObject,
                            "colorSecondaryLabelActive",
                            colorTextAlpha
                        )
                    }

                    setObjectField(
                        param.thisObject,
                        "colorLabelInactive",
                        Color.WHITE
                    )

                    setObjectField(
                        param.thisObject,
                        "colorSecondaryLabelInactive",
                        -0x7f000001
                    )

                    val sideView =
                        getObjectField(param.thisObject, "sideView") as ViewGroup

                    val customDrawable = sideView.findViewById<ImageView>(
                        mContext.resources.getIdentifier(
                            "customDrawable",
                            "id",
                            mContext.packageName
                        )
                    )
                    customDrawable.imageTintList = ColorStateList.valueOf(colorText!!)

                    val chevron = sideView.findViewById<ImageView>(
                        mContext.resources.getIdentifier(
                            "chevron",
                            "id",
                            mContext.packageName
                        )
                    )
                    chevron.imageTintList = ColorStateList.valueOf(colorText!!)
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        qsIconViewImplClass
            .hookMethod("getIconColorForState", "getColor")
            .runBefore { param ->
                if (!blackQSHeaderEnabled) return@runBefore

                val (isDisabledState: Boolean, isActiveState: Boolean) = Utils.getTileState(param)

                if (isDisabledState) {
                    param.result = -0x7f000001
                } else if (isActiveState && !qsTextAlwaysWhite && !qsTextFollowAccent) {
                    param.result = colorText
                } else if (!isActiveState) {
                    param.result = Color.WHITE
                }
            }

        qsIconViewImplClass
            .hookMethod("updateIcon")
            .runAfter { param ->
                if (!blackQSHeaderEnabled || qsTextAlwaysWhite || qsTextFollowAccent) return@runAfter

                val (isDisabledState: Boolean,
                    isActiveState: Boolean) = Utils.getTileState(param)

                if (blackQSHeaderEnabled) {
                    val mIcon = param.args[0] as ImageView

                    if (isDisabledState) {
                        mIcon.imageTintList = ColorStateList.valueOf(-0x7f000001)
                    } else if (isActiveState && !qsTextAlwaysWhite && !qsTextFollowAccent) {
                        mIcon.imageTintList = ColorStateList.valueOf(colorText!!)
                    } else if (!isActiveState) {
                        mIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
                    }
                }
            }

        try {
            mBehindColors = gradientColorsClass.getDeclaredConstructor().newInstance()
        } catch (throwable: Throwable) {
            log(TAG + throwable)
        }

        scrimControllerClass
            .hookMethod("updateScrims")
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    val mScrimBehind = getObjectField(param.thisObject, "mScrimBehind")
                    val mBlankScreen = getObjectField(param.thisObject, "mBlankScreen") as Boolean
                    val alpha = getFloatField(mScrimBehind, "mViewAlpha")
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
                calculateColors()

                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    val states: ColorStateList = getColorAttr(
                        mContext.resources.getIdentifier(
                            "android:attr/colorBackgroundFloating",
                            "attr",
                            mContext.packageName
                        ), mContext
                    )
                    val surfaceBackground = states.defaultColor
                    val accentStates: ColorStateList = getColorAttr(
                        mContext.resources.getIdentifier(
                            "colorAccent",
                            "attr",
                            "android"
                        ), mContext
                    )
                    val accent = accentStates.defaultColor

                    callMethod(mBehindColors, "setMainColor", surfaceBackground)
                    callMethod(mBehindColors, "setSecondaryColor", accent)

                    val contrast = ColorUtils.calculateContrast(
                        callMethod(
                            mBehindColors,
                            "getMainColor"
                        ) as Int, Color.WHITE
                    )

                    callMethod(
                        mBehindColors,
                        "setSupportsDarkText",
                        contrast > 4.5
                    )
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        scrimControllerClass
            .hookMethodMatchPattern("applyState.*")
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    val mClipsQsScrim =
                        getObjectField(param.thisObject, "mClipsQsScrim") as Boolean
                    if (mClipsQsScrim) {
                        setObjectField(param.thisObject, "mBehindTint", Color.BLACK)
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
                    .runAfter { param ->
                        if (!blackQSHeaderEnabled) return@runAfter

                        val mClipQsScrim = getObjectField(
                            param.thisObject,
                            "mClipQsScrim"
                        ) as Boolean

                        if (mClipQsScrim) {
                            val mScrimBehind = getObjectField(
                                param.thisObject,
                                "mScrimBehind"
                            )
                            val mTintColor = getIntField(mScrimBehind, "mTintColor")

                            if (mTintColor != Color.BLACK) {
                                setObjectField(
                                    mScrimBehind,
                                    "mTintColor",
                                    Color.BLACK
                                )

                                callMethod(
                                    mScrimBehind,
                                    "updateColorWithTint",
                                    false
                                )
                            }

                            callMethod(mScrimBehind, "setViewAlpha", 1f)
                        }
                    }

                "BOUNCER" -> constant.javaClass
                    .hookMethod("prepare")
                    .runAfter { param ->
                        if (!blackQSHeaderEnabled) return@runAfter

                        setObjectField(param.thisObject, "mBehindTint", Color.BLACK)
                    }

                "SHADE_LOCKED" -> {
                    constant.javaClass
                        .hookMethod("prepare")
                        .runAfter { param ->
                            if (!blackQSHeaderEnabled) return@runAfter

                            setObjectField(param.thisObject, "mBehindTint", Color.BLACK)

                            val mClipQsScrim = getObjectField(
                                param.thisObject,
                                "mClipQsScrim"
                            ) as Boolean

                            if (mClipQsScrim) {
                                val mScrimBehind = getObjectField(
                                    param.thisObject,
                                    "mScrimBehind"
                                )
                                val mTintColor = getIntField(mScrimBehind, "mTintColor")

                                if (mTintColor != Color.BLACK) {
                                    setObjectField(
                                        mScrimBehind,
                                        "mTintColor",
                                        Color.BLACK
                                    )

                                    callMethod(
                                        mScrimBehind,
                                        "updateColorWithTint",
                                        false
                                    )
                                }

                                callMethod(mScrimBehind, "setViewAlpha", 1f)
                            }
                        }

                    constant.javaClass
                        .hookMethod("getBehindTint")
                        .suppressError()
                        .runBefore { param ->
                            if (!blackQSHeaderEnabled) return@runBefore

                            param.result = Color.BLACK
                        }
                }

                "UNLOCKED" -> constant.javaClass
                    .hookMethod("prepare")
                    .runAfter { param ->
                        if (!blackQSHeaderEnabled) return@runAfter

                        setObjectField(
                            param.thisObject,
                            "mBehindTint",
                            Color.BLACK
                        )

                        val mScrimBehind = getObjectField(param.thisObject, "mScrimBehind")
                        val mTintColor = getIntField(mScrimBehind, "mTintColor")

                        if (mTintColor != Color.BLACK) {
                            setObjectField(
                                mScrimBehind,
                                "mTintColor",
                                Color.BLACK
                            )

                            callMethod(
                                mScrimBehind,
                                "updateColorWithTint",
                                false
                            )
                        }

                        callMethod(mScrimBehind, "setViewAlpha", 1f)
                    }
            }
        }

        fragmentHostManagerClass
            .hookConstructor()
            .runBefore { param ->
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
    }

    private fun initColors(force: Boolean) {
        val isDark: Boolean = SystemUtils.isDarkMode
        if (isDark == this.isDark && !force) return

        this.isDark = isDark

        calculateColors()
    }

    private fun calculateColors() {
        try {
            colorText = mContext.resources.getColor(
                mContext.resources.getIdentifier(
                    "android:color/system_neutral1_900",
                    "color",
                    mContext.packageName
                ), mContext.theme
            )

            colorTextAlpha = colorText!! and 0xFFFFFF or (Math.round(
                Color.alpha(
                    colorText!!
                ) * 0.8f
            ) shl 24)
        } catch (throwable: Throwable) {
            log(TAG + throwable)
        }
    }

    private fun setHeaderComponentsColor(mView: View, iconManager: Any, batteryIcon: Any) {
        if (!blackQSHeaderEnabled) return

        val textColor = Color.WHITE

        try {
            (mView.findViewById<View>(
                mContext.resources.getIdentifier(
                    "clock",
                    "id",
                    mContext.packageName
                )
            ) as TextView).setTextColor(textColor)

            (mView.findViewById<View>(
                mContext.resources.getIdentifier(
                    "date",
                    "id",
                    mContext.packageName
                )
            ) as TextView).setTextColor(textColor)
        } catch (ignored: Throwable) {
        }

        try {
            callMethod(iconManager, "setTint", textColor)

            for (i in 1..3) {
                val id = String.format("carrier%s", i)

                try {
                    (getObjectField(
                        mView.findViewById(
                            mContext.resources.getIdentifier(
                                id,
                                "id",
                                mContext.packageName
                            )
                        ), "mCarrierText"
                    ) as TextView).setTextColor(textColor)

                    (getObjectField(
                        mView.findViewById(
                            mContext.resources.getIdentifier(
                                id,
                                "id",
                                mContext.packageName
                            )
                        ), "mMobileSignal"
                    ) as ImageView).imageTintList = ColorStateList.valueOf(textColor)

                    (getObjectField(
                        mView.findViewById(
                            mContext.resources.getIdentifier(
                                id,
                                "id",
                                mContext.packageName
                            )
                        ), "mMobileRoaming"
                    ) as ImageView).imageTintList = ColorStateList.valueOf(textColor)
                } catch (ignored: Throwable) {
                }
            }

            callMethod(batteryIcon, "updateColors", textColor, textColor, textColor)
        } catch (throwable: Throwable) {
            log(TAG + throwable)
        }
    }

    companion object {
        private val TAG = "Iconify - ${QSBlackThemeA13::class.java.simpleName}: "
        private var blackQSHeaderEnabled = false
    }
}