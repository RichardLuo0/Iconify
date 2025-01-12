package com.drdisagree.iconify.xposed.modules.themes


import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.service.quicksettings.Tile
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.DUALTONE_QSPANEL
import com.drdisagree.iconify.common.Preferences.LIGHT_QSPANEL
import com.drdisagree.iconify.common.Preferences.QS_TEXT_ALWAYS_WHITE
import com.drdisagree.iconify.common.Preferences.QS_TEXT_FOLLOW_ACCENT
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.SettingsLibUtils.Companion.getColorAttr
import com.drdisagree.iconify.xposed.modules.utils.SettingsLibUtils.Companion.getColorAttrDefaultColor
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
import java.util.Arrays

@SuppressLint("DiscouragedApi")
class QSLightThemeA13(context: Context) : ModPack(context) {

    private var mBehindColors: Any? = null
    private var isDark: Boolean
    private var colorActive: Int? = null
    private var colorInactive: Int? = null
    private var mClockViewQSHeader: Any? = null
    private var unlockedScrimState: Any? = null
    private var qsTextAlwaysWhite = false
    private var qsTextFollowAccent = false
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
            qsTextAlwaysWhite = getBoolean(QS_TEXT_ALWAYS_WHITE, false)
            qsTextFollowAccent = getBoolean(QS_TEXT_FOLLOW_ACCENT, false)
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

        val batteryStatusChipClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.BatteryStatusChip",
            suppressError = true
        )

        // Background color of android 14's charging chip. Fix for light QS theme situation
        batteryStatusChipClass
            .hookMethod("updateResources")
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                (getObjectField(param.thisObject, "roundedContainer") as LinearLayout)
                    .background.setTint(colorActive!!)

                val colorPrimary: Int = getColorAttrDefaultColor(
                    mContext,
                    android.R.attr.textColorPrimaryInverse
                )
                val textColorSecondary: Int = getColorAttrDefaultColor(
                    mContext,
                    android.R.attr.textColorSecondaryInverse
                )

                callMethod(
                    getObjectField(param.thisObject, "batteryMeterView"),
                    "updateColors",
                    colorPrimary,
                    textColorSecondary,
                    colorPrimary
                )
            }

        unlockedScrimState = scrimStateEnum.enumConstants?.let {
            Arrays.stream(it)
                .filter { c: Any -> c.toString() == "UNLOCKED" }
                .findFirst().get()
        }

        unlockedScrimState?.javaClass
            ?.hookMethod("prepare")
            ?.runAfter {
                if (!lightQSHeaderEnabled) return@runAfter

                setObjectField(unlockedScrimState, "mBehindTint", Color.TRANSPARENT)
            }

        qsPanelControllerClass
            .hookConstructor()
            .runAfter { calculateColors() }

        try { // 13QPR1
            val qsFgsManagerFooterClass = findClass(
                "$SYSTEMUI_PACKAGE.qs.QSFgsManagerFooter",
                throwException = true
            )
            val footerActionsControllerClass =
                findClass("$SYSTEMUI_PACKAGE.qs.FooterActionsController")

            qsFgsManagerFooterClass
                .hookConstructor()
                .runAfter { param ->
                    if (!lightQSHeaderEnabled || isDark) return@runAfter

                    try {
                        (getObjectField(param.thisObject, "mNumberContainer") as View)
                            .background.setTint(colorInactive!!)
                    } catch (throwable: Throwable) {
                        log(TAG + throwable)
                    }
                }

            footerActionsControllerClass
                .hookConstructor()
                .runAfter { param ->
                    if (!lightQSHeaderEnabled || isDark) return@runAfter

                    try {
                        val res = mContext.resources
                        val view = param.args[0] as ViewGroup

                        view.findViewById<View>(
                            res.getIdentifier(
                                "multi_user_switch",
                                "id",
                                mContext.packageName
                            )
                        ).background.setTint(
                            colorInactive!!
                        )

                        val settingsButtonContainer = view.findViewById<View>(
                            res.getIdentifier(
                                "settings_button_container",
                                "id",
                                mContext.packageName
                            )
                        )

                        settingsButtonContainer.background.setTint(colorInactive!!)
                    } catch (throwable: Throwable) {
                        log(TAG + throwable)
                    }
                }

            // White QS Clock bug - doesn't seem applicable on 13QPR3 and 14
            quickStatusBarHeaderClass
                .hookMethod("onFinishInflate")
                .runAfter { param ->
                    val thisView = param.thisObject as View
                    val res = mContext.resources

                    mClockViewQSHeader = try {
                        getObjectField(param.thisObject, "mClockView")
                    } catch (ignored: Throwable) {
                        thisView.findViewById(
                            res.getIdentifier(
                                "clock",
                                "id",
                                mContext.packageName
                            )
                        )
                    }

                    if (lightQSHeaderEnabled && !isDark && mClockViewQSHeader != null) {
                        try {
                            (mClockViewQSHeader as TextView).setTextColor(Color.BLACK)
                        } catch (throwable: Throwable) {
                            log(TAG + throwable)
                        }
                    }
                }

            // White QS Clock bug - doesn't seem applicable on 13QPR3 and 14
            clockClass
                .hookMethod("onColorsChanged")
                .runAfter {
                    val isLight = isDark // reverse logic

                    if (lightQSHeaderEnabled && isLight && mClockViewQSHeader != null) {
                        try {
                            (mClockViewQSHeader as TextView).setTextColor(Color.BLACK)
                        } catch (throwable: Throwable) {
                            log(TAG + throwable)
                        }
                    }
                }
        } catch (throwable: Throwable) { // 13QPR2&3
            // 13QPR3
            val shadeHeaderControllerClass = findClass(
                "$SYSTEMUI_PACKAGE.shade.ShadeHeaderController",
                "$SYSTEMUI_PACKAGE.shade.LargeScreenShadeHeaderController"
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
                    if (!lightQSHeaderEnabled || isDark) return@runAfter

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
                        settingsButtonContainer.background.setTint(colorInactive!!)

                        val settingsIcon =
                            settingsButtonContainer.findViewById<ImageView>(
                                res.getIdentifier(
                                    "icon",
                                    "id",
                                    mContext.packageName
                                )
                            )
                        settingsIcon.imageTintList = ColorStateList.valueOf(Color.BLACK)

                        val pmButtonContainer = view.findViewById<View>(
                            res.getIdentifier(
                                "pm_lite",
                                "id",
                                mContext.packageName
                            )
                        )

                        val pmIcon = pmButtonContainer.findViewById<ImageView>(
                            res.getIdentifier(
                                "icon",
                                "id",
                                mContext.packageName
                            )
                        )
                        pmIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
                    } catch (throwable: Throwable) {
                        log(TAG + throwable)
                    }
                }
        }

        qsIconViewImplClass
            .hookMethod("setIcon")
            .runBefore { param ->
                if (!lightQSHeaderEnabled || isDark) return@runBefore

                try {
                    if (param.args[0] is ImageView &&
                        getIntField(param.args[1], "state") == Tile.STATE_ACTIVE
                    ) {
                        setObjectField(param.thisObject, "mTint", colorInactive)
                    }
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        centralSurfacesImplClass
            .hookConstructor()
            .runAfter { applyOverlays(true) }

        centralSurfacesImplClass
            .hookMethod("updateTheme")
            .runAfter { applyOverlays(false) }

        qsTileViewImplClass
            .hookConstructor()
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                try {
                    if (!qsTextAlwaysWhite && !qsTextFollowAccent) {
                        setObjectField(param.thisObject, "colorLabelActive", Color.WHITE)
                        setObjectField(param.thisObject, "colorSecondaryLabelActive", -0x7f000001)
                    }

                    setObjectField(param.thisObject, "colorLabelInactive", Color.BLACK)
                    setObjectField(param.thisObject, "colorSecondaryLabelInactive", -0x80000000)
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        qsIconViewImplClass
            .hookMethod("getIconColorForState", "getColor")
            .runBefore { param ->
                if (!lightQSHeaderEnabled || isDark) return@runBefore

                val (isDisabledState: Boolean, isActiveState: Boolean) = Utils.getTileState(param)

                if (isDisabledState) {
                    param.result = -0x80000000
                } else {
                    if (isActiveState && !qsTextAlwaysWhite && !qsTextFollowAccent) {
                        param.result = Color.WHITE
                    } else if (!isActiveState) {
                        param.result = Color.BLACK
                    }
                }
            }

        qsIconViewImplClass
            .hookMethod("updateIcon")
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                val (isDisabledState: Boolean, isActiveState: Boolean) = Utils.getTileState(param)

                val mIcon = param.args[0] as ImageView

                if (isDisabledState) {
                    param.result = -0x80000000
                } else {
                    if (isActiveState && !qsTextAlwaysWhite && !qsTextFollowAccent) {
                        mIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
                    } else if (!isActiveState) {
                        mIcon.imageTintList = ColorStateList.valueOf(Color.BLACK)
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
                if (!dualToneQSEnabled) return@runAfter

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

                if (!dualToneQSEnabled) return@runAfter

                try {
                    val states: ColorStateList = getColorAttr(
                        mContext.resources.getIdentifier(
                            "android:attr/colorSurfaceHeader",
                            "attr",
                            mContext.packageName
                        ), mContext
                    )
                    val surfaceBackground = states.defaultColor
                    val accentStates: ColorStateList =
                        getColorAttr(
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

                    callMethod(mBehindColors, "setSupportsDarkText", contrast > 4.5)
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        scrimControllerClass
            .hookMethodMatchPattern("applyState.*")
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
                    .runAfter { param ->
                        if (!lightQSHeaderEnabled) return@runAfter

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

                            if (mTintColor != Color.TRANSPARENT) {
                                setObjectField(
                                    mScrimBehind,
                                    "mTintColor",
                                    Color.TRANSPARENT
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
                                val mScrimBehind = getObjectField(
                                    param.thisObject,
                                    "mScrimBehind"
                                )
                                val mTintColor = getIntField(mScrimBehind, "mTintColor")

                                if (mTintColor != Color.TRANSPARENT) {
                                    setObjectField(
                                        mScrimBehind,
                                        "mTintColor",
                                        Color.TRANSPARENT
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
                            if (!lightQSHeaderEnabled) return@runBefore

                            param.result = Color.TRANSPARENT
                        }
                }

                "UNLOCKED" -> constant.javaClass
                    .hookMethod("prepare")
                    .runAfter { param ->
                        if (!lightQSHeaderEnabled) return@runAfter

                        setObjectField(
                            param.thisObject,
                            "mBehindTint",
                            Color.TRANSPARENT
                        )

                        val mScrimBehind = getObjectField(param.thisObject, "mScrimBehind")
                        val mTintColor = getIntField(mScrimBehind, "mTintColor")

                        if (mTintColor != Color.TRANSPARENT) {
                            setObjectField(
                                mScrimBehind,
                                "mTintColor",
                                Color.TRANSPARENT
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

    private fun applyOverlays(force: Boolean) {
        val isCurrentlyDark: Boolean = SystemUtils.isDarkMode
        if (isCurrentlyDark == isDark && !force) return

        isDark = isCurrentlyDark

        calculateColors()

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

    private fun calculateColors() {
        if (!lightQSHeaderEnabled) return

        try {
            if (unlockedScrimState != null) {
                setObjectField(unlockedScrimState, "mBehindTint", Color.TRANSPARENT)
            }

            if (!isDark) {
                colorActive = mContext.resources.getColor(
                    mContext.resources.getIdentifier(
                        "android:color/system_accent1_600",
                        "color",
                        mContext.packageName
                    ), mContext.theme
                )
                colorInactive = mContext.resources.getColor(
                    mContext.resources.getIdentifier(
                        "android:color/system_neutral1_10",
                        "color",
                        mContext.packageName
                    ), mContext.theme
                )
            }
        } catch (throwable: Throwable) {
            log(TAG + throwable)
        }
    }

    private fun setHeaderComponentsColor(mView: View, iconManager: Any, batteryIcon: Any) {
        if (!lightQSHeaderEnabled) return

        val textColorPrimary: Int =
            getColorAttrDefaultColor(android.R.attr.textColorPrimary, mContext)
        val textColorSecondary: Int =
            getColorAttrDefaultColor(android.R.attr.textColorSecondary, mContext)

        try {
            (mView.findViewById<View>(
                mContext.resources.getIdentifier(
                    "clock",
                    "id",
                    mContext.packageName
                )
            ) as TextView).setTextColor(textColorPrimary)

            (mView.findViewById<View>(
                mContext.resources.getIdentifier(
                    "date",
                    "id",
                    mContext.packageName
                )
            ) as TextView).setTextColor(textColorPrimary)
        } catch (ignored: Throwable) {
        }

        try {
            callMethod(iconManager, "setTint", textColorPrimary)

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
                    ) as TextView).setTextColor(textColorPrimary)

                    (getObjectField(
                        mView.findViewById(
                            mContext.resources.getIdentifier(
                                id,
                                "id",
                                mContext.packageName
                            )
                        ), "mMobileSignal"
                    ) as ImageView).imageTintList = ColorStateList.valueOf(textColorPrimary)

                    (getObjectField(
                        mView.findViewById(
                            mContext.resources.getIdentifier(
                                id,
                                "id",
                                mContext.packageName
                            )
                        ), "mMobileRoaming"
                    ) as ImageView).imageTintList = ColorStateList.valueOf(textColorPrimary)
                } catch (ignored: Throwable) {
                }
            }

            callMethod(
                batteryIcon,
                "updateColors",
                textColorPrimary,
                textColorSecondary,
                textColorPrimary
            )
        } catch (throwable: Throwable) {
            log(TAG + throwable)
        }
    }

    companion object {
        private val TAG = "Iconify - ${QSLightThemeA13::class.java.simpleName}: "
        private var lightQSHeaderEnabled = false
        private var dualToneQSEnabled = false
    }
}