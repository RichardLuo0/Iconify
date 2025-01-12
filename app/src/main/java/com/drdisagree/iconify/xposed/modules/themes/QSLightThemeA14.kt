package com.drdisagree.iconify.xposed.modules.themes


import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PorterDuff
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
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.getFloatField
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Arrays
import java.util.function.Consumer

@SuppressLint("DiscouragedApi")
class QSLightThemeA14(context: Context) : ModPack(context) {

    private var mBehindColors: Any? = null
    private var isDark: Boolean
    private var colorActive: Int? = null
    private var colorInactive: Int? = null
    private var unlockedScrimState: Any? = null
    private var qsTextAlwaysWhite = false
    private var qsTextFollowAccent = false
    private var mScrimBehindTint = Color.BLACK
    private var shadeCarrierGroupController: Any? = null
    private var mClockViewQSHeader: Any? = null
    private val modernShadeCarrierGroupMobileViews = ArrayList<Any>()
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
        val scrimControllerClass = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimController")
        val gradientColorsClass =
            findClass("com.android.internal.colorextraction.ColorExtractor\$GradientColors")!!
        val qsPanelControllerClass = findClass("$SYSTEMUI_PACKAGE.qs.QSPanelController")
        val scrimStateEnum = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimState")!!
        val qsIconViewImplClass = findClass("$SYSTEMUI_PACKAGE.qs.tileimpl.QSIconViewImpl")
        val centralSurfacesImplClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.phone.CentralSurfacesImpl",
            suppressError = true
        )
        val qsCustomizerClass = findClass("$SYSTEMUI_PACKAGE.qs.customize.QSCustomizer")
        val qsContainerImplClass = findClass("$SYSTEMUI_PACKAGE.qs.QSContainerImpl")
        val shadeCarrierClass = findClass("$SYSTEMUI_PACKAGE.shade.carrier.ShadeCarrier")
        val batteryStatusChipClass = findClass("$SYSTEMUI_PACKAGE.statusbar.BatteryStatusChip")
        val textButtonViewHolderClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.footer.ui.binder.TextButtonViewHolder",
            suppressError = true
        )
        val numberButtonViewHolderClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.footer.ui.binder.NumberButtonViewHolder",
            suppressError = true
        )
        val qsFooterViewClass = findClass("$SYSTEMUI_PACKAGE.qs.QSFooterView")
        val brightnessControllerClass =
            findClass("$SYSTEMUI_PACKAGE.settings.brightness.BrightnessController")
        val brightnessMirrorControllerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.policy.BrightnessMirrorController")
        val brightnessSliderControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.settings.brightness.BrightnessSliderController",
            suppressError = true
        )
        val quickStatusBarHeaderClass = findClass("$SYSTEMUI_PACKAGE.qs.QuickStatusBarHeader")
        val clockClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.policy.Clock",
            suppressError = true
        )
        val themeColorKtClass = findClass(
            "com.android.compose.theme.ColorKt",
            suppressError = true
        )
        val expandableControllerImplClass =
            findClass("com.android.compose.animation.ExpandableControllerImpl")
        val footerActionsViewModelClass =
            findClass("$SYSTEMUI_PACKAGE.qs.footer.ui.viewmodel.FooterActionsViewModel")
        val footerActionsViewBinderClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.footer.ui.binder.FooterActionsViewBinder",
            suppressError = true
        )
        val shadeHeaderControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.shade.ShadeHeaderController",
            "$SYSTEMUI_PACKAGE.shade.LargeScreenShadeHeaderController",
        )

        // Background color of android 14's charging chip. Fix for light QS theme situation
        val batteryStatusChipColorHook: XC_MethodHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!lightQSHeaderEnabled || isDark) return

                (getObjectField(param.thisObject, "roundedContainer") as LinearLayout)
                    .background.setTint(colorInactive!!)

                val colorPrimary: Int =
                    getColorAttrDefaultColor(mContext, android.R.attr.textColorPrimaryInverse)
                val textColorSecondary: Int =
                    getColorAttrDefaultColor(mContext, android.R.attr.textColorSecondaryInverse)

                callMethod(
                    getObjectField(param.thisObject, "batteryMeterView"),
                    "updateColors",
                    colorPrimary,
                    textColorSecondary,
                    colorPrimary
                )
            }
        }

        batteryStatusChipClass
            .hookConstructor()
            .run(batteryStatusChipColorHook)

        batteryStatusChipClass
            .hookMethod("onConfigurationChanged")
            .run(batteryStatusChipColorHook)

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

        shadeHeaderControllerClass
            .hookMethod("onInit")
            .runAfter { param ->
                try {
                    val mView = getObjectField(param.thisObject, "mView") as View
                    val iconManager = getObjectField(param.thisObject, "iconManager")
                    val batteryIcon = getObjectField(param.thisObject, "batteryIcon")
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

        // A14 ap11 onwards - modern implementation of mobile icons
        val shadeCarrierGroupControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.shade.carrier.ShadeCarrierGroupController",
            suppressError = true
        )
        val mobileIconBinderClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.pipeline.mobile.ui.binder.MobileIconBinder",
            suppressError = true
        )

        shadeCarrierGroupControllerClass
            .hookConstructor()
            .runAfter { param -> shadeCarrierGroupController = param.thisObject }

        mobileIconBinderClass
            .hookMethod("bind")
            .runAfter { param ->
                if (param.args[1].javaClass.name
                        .contains("ShadeCarrierGroupMobileIconViewModel")
                ) {
                    modernShadeCarrierGroupMobileViews.add(param.result)

                    if (!isDark && lightQSHeaderEnabled) {
                        val textColor = getColorAttrDefaultColor(
                            android.R.attr.textColorPrimary,
                            mContext
                        )
                        setMobileIconTint(param.result, textColor)
                    }
                }
            }

        qsContainerImplClass
            .hookMethod("updateResources")
            .suppressError()
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                try {
                    val view = (param.thisObject as ViewGroup).findViewById<ViewGroup>(
                        mContext.resources.getIdentifier(
                            "qs_footer_actions",
                            "id",
                            mContext.packageName
                        )
                    ).also {
                        it.background?.setTint(Color.TRANSPARENT)
                        it.elevation = 0f
                    }

                    // Settings button
                    view.findViewById<View>(
                        mContext.resources.getIdentifier(
                            "settings_button_container",
                            "id",
                            mContext.packageName
                        )
                    ).findViewById<ImageView>(
                        mContext.resources.getIdentifier(
                            "icon",
                            "id",
                            mContext.packageName
                        )
                    ).imageTintList = ColorStateList.valueOf(Color.BLACK)

                    // Power menu button
                    try {
                        view.findViewById<ImageView?>(
                            mContext.resources.getIdentifier(
                                "pm_lite",
                                "id",
                                mContext.packageName
                            )
                        )
                    } catch (ignored: ClassCastException) {
                        view.findViewById<ViewGroup?>(
                            mContext.resources.getIdentifier(
                                "pm_lite",
                                "id",
                                mContext.packageName
                            )
                        )
                    }?.apply {
                        if (this is ImageView) {
                            imageTintList = ColorStateList.valueOf(Color.WHITE)
                        } else if (this is ViewGroup) {
                            (getChildAt(0) as ImageView).setColorFilter(
                                Color.WHITE,
                                PorterDuff.Mode.SRC_IN
                            )
                        }
                    }
                } catch (ignored: Throwable) {
                    // it will fail on compose implementation
                }
            }

        // QS Customize panel
        qsCustomizerClass
            .hookConstructor()
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                val mainView = param.thisObject as ViewGroup

                for (i in 0 until mainView.childCount) {
                    mainView.getChildAt(i).setBackgroundColor(mScrimBehindTint)
                }
            }

        // Mobile signal icons - this is the legacy model. new model uses viewmodels
        shadeCarrierClass
            .hookMethod("updateState")
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                (getObjectField(param.thisObject, "mMobileSignal") as ImageView).imageTintList =
                    ColorStateList.valueOf(Color.BLACK)
            }

        // QS security footer count circle
        numberButtonViewHolderClass
            .hookConstructor()
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                (getObjectField(param.thisObject, "newDot") as ImageView)
                    .setColorFilter(Color.BLACK)

                (getObjectField(param.thisObject, "number") as TextView)
                    .setTextColor(Color.BLACK)
            }

        // QS security footer
        textButtonViewHolderClass
            .hookConstructor()
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                (getObjectField(param.thisObject, "chevron") as ImageView)
                    .setColorFilter(Color.BLACK)

                (getObjectField(param.thisObject, "icon") as ImageView)
                    .setColorFilter(Color.BLACK)

                (getObjectField(param.thisObject, "newDot") as ImageView)
                    .setColorFilter(Color.BLACK)

                (getObjectField(param.thisObject, "text") as TextView)
                    .setTextColor(Color.BLACK)
            }

        qsFooterViewClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                try {
                    (getObjectField(param.thisObject, "mBuildText") as TextView)
                        .setTextColor(Color.BLACK)
                } catch (ignored: Throwable) {
                }

                try {
                    (getObjectField(param.thisObject, "mEditButton") as ImageView)
                        .setColorFilter(Color.BLACK)
                } catch (ignored: Throwable) {
                }

                try {
                    setObjectField(
                        getObjectField(param.thisObject, "mPageIndicator"),
                        "mTint",
                        ColorStateList.valueOf(Color.BLACK)
                    )
                } catch (ignored: Throwable) {
                }
            }

        // Auto Brightness Icon Color
        brightnessControllerClass
            .hookMethod("updateIcon")
            .suppressError()
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                try {
                    (getObjectField(param.thisObject, "mIcon") as ImageView).imageTintList =
                        ColorStateList.valueOf(Color.BLACK)
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        brightnessSliderControllerClass
            .hookConstructor()
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                try {
                    (getObjectField(param.thisObject, "mIcon") as ImageView).imageTintList =
                        ColorStateList.valueOf(Color.BLACK)
                } catch (throwable: Throwable) {
                    try {
                        (getObjectField(
                            param.thisObject,
                            "mIconView"
                        ) as ImageView).imageTintList =
                            ColorStateList.valueOf(Color.BLACK)
                    } catch (ignored: Throwable) {
                    }
                }
            }

        brightnessMirrorControllerClass
            .hookMethod("updateIcon")
            .suppressError()
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                try {
                    val iconColor = if ((param.args?.get(0) ?: false) as Boolean) {
                        Color.WHITE
                    } else {
                        Color.BLACK
                    }
                    val mIcon = getObjectField(param.thisObject, "mIcon") as ImageView

                    mIcon.imageTintList = ColorStateList.valueOf(iconColor)
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
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

        // White QS Clock bug
        quickStatusBarHeaderClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                try {
                    mClockViewQSHeader = getObjectField(param.thisObject, "mClockView")
                } catch (ignored: Throwable) {
                }

                if (!isDark && lightQSHeaderEnabled && mClockViewQSHeader != null) {
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
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                (mClockViewQSHeader as? TextView)?.setTextColor(Color.WHITE)
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

                    val sideView = getObjectField(param.thisObject, "sideView") as ViewGroup

                    val customDrawable = sideView.findViewById<ImageView>(
                        mContext.resources.getIdentifier(
                            "customDrawable",
                            "id",
                            mContext.packageName
                        )
                    )
                    customDrawable.imageTintList = ColorStateList.valueOf(Color.WHITE)

                    val chevron = sideView.findViewById<ImageView>(
                        mContext.resources.getIdentifier(
                            "chevron",
                            "id",
                            mContext.packageName
                        )
                    )
                    chevron.imageTintList = ColorStateList.valueOf(Color.WHITE)
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

        qsContainerImplClass
            .hookMethod("updateResources")
            .suppressError()
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                try {
                    val res = mContext.resources

                    val view = (param.thisObject as ViewGroup).findViewById<ViewGroup>(
                        res.getIdentifier(
                            "qs_footer_actions",
                            "id",
                            mContext.packageName
                        )
                    )

                    try {
                        val pmButtonContainer = view.findViewById<ViewGroup>(
                            res.getIdentifier(
                                "pm_lite",
                                "id",
                                mContext.packageName
                            )
                        )

                        (pmButtonContainer.getChildAt(0) as ImageView).setColorFilter(
                            Color.BLACK,
                            PorterDuff.Mode.SRC_IN
                        )
                    } catch (ignored: Throwable) {
                        val pmButton = view.findViewById<ImageView>(
                            res.getIdentifier(
                                "pm_lite",
                                "id",
                                mContext.packageName
                            )
                        )

                        pmButton.imageTintList = ColorStateList.valueOf(Color.WHITE)
                    }
                } catch (ignored: Throwable) {
                }
            }

        // Compose implementation of QS Footer actions
        val graphicsColorKtClass = findClass(
            "androidx.compose.ui.graphics.ColorKt",
            suppressError = true
        )

        expandableControllerImplClass
            .hookConstructor()
            .runBefore { param ->
                if (!lightQSHeaderEnabled || isDark) return@runBefore

                param.args[1] = callStaticMethod(graphicsColorKtClass, "Color", Color.BLACK)
            }

        themeColorKtClass
            .hookMethod("colorAttr")
            .runBefore { param ->
                if (!lightQSHeaderEnabled || isDark) return@runBefore

                val code = param.args[0] as Int
                var result = 0

                if (code == PM_LITE_BACKGROUND_CODE) {
                    result = colorActive!!
                } else {
                    try {
                        when (mContext.resources.getResourceName(code).split("/")[1]) {
                            "underSurface", "onShadeActive", "shadeInactive" -> {
                                result = colorInactive!! // button backgrounds
                            }

                            "onShadeInactiveVariant" -> {
                                result = Color.BLACK // "number button" text
                            }
                        }
                    } catch (ignored: Throwable) {
                    }
                }

                if (result != 0) {
                    param.result = callStaticMethod(graphicsColorKtClass, "Color", result)
                }
            }

        footerActionsViewModelClass
            .hookConstructor()
            .runAfter { param ->
                if (!lightQSHeaderEnabled) return@runAfter

                if (!isDark) {
                    // Power button
                    val power = getObjectField(param.thisObject, "power")
                    setObjectField(power, "iconTint", Color.WHITE)
                    setObjectField(power, "backgroundColor", PM_LITE_BACKGROUND_CODE)

                    // Settings button
                    val settings = getObjectField(param.thisObject, "settings")
                    setObjectField(settings, "iconTint", Color.BLACK)
                }

                // We must use the classes defined in the apk. Using our own will fail.
                val stateFlowImplClass = findClass("kotlinx.coroutines.flow.StateFlowImpl")!!
                val readonlyStateFlowClass =
                    findClass("kotlinx.coroutines.flow.ReadonlyStateFlow")!!

                try {
                    val zeroAlphaFlow = stateFlowImplClass
                        .getConstructor(Any::class.java)
                        .newInstance(0f)

                    val readonlyStateFlowInstance = try {
                        readonlyStateFlowClass.constructors[0].newInstance(zeroAlphaFlow)
                    } catch (ignored: Throwable) {
                        readonlyStateFlowClass.constructors[0].newInstance(zeroAlphaFlow, null)
                    }

                    setObjectField(
                        param.thisObject,
                        "backgroundAlpha",
                        readonlyStateFlowInstance
                    )
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        footerActionsViewBinderClass
            .hookMethod("bind")
            .suppressError()
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                val view = param.args[0] as LinearLayout
                view.setBackgroundColor(Color.TRANSPARENT)
                view.elevation = 0f
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
                    val accentColor = getColorAttr(
                        mContext.resources.getIdentifier(
                            "colorAccent",
                            "attr",
                            "android"
                        ), mContext
                    ).defaultColor

                    callMethod(mBehindColors, "setMainColor", surfaceBackground)
                    callMethod(mBehindColors, "setSecondaryColor", accentColor)

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
                    val mClipsQsScrim = getObjectField(param.thisObject, "mClipsQsScrim") as Boolean

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

                        setObjectField(param.thisObject, "mBehindTint", Color.TRANSPARENT)
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
                                val mTintColor = getIntField(
                                    mScrimBehind,
                                    "mTintColor"
                                )

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

                                callMethod(
                                    mScrimBehind,
                                    "setViewAlpha",
                                    1f
                                )
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

            mScrimBehindTint = if (isDark) {
                mContext.resources.getColor(
                    mContext.resources.getIdentifier(
                        "android:color/system_neutral1_1000",
                        "color",
                        mContext.packageName
                    ), mContext.theme
                )
            } else {
                mContext.resources.getColor(
                    mContext.resources.getIdentifier(
                        "android:color/system_neutral1_100",
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
            try { // A14 ap11
                callMethod(iconManager, "setTint", textColorPrimary, textColorPrimary)

                modernShadeCarrierGroupMobileViews.forEach(Consumer { view: Any ->
                    setMobileIconTint(
                        view,
                        textColorPrimary
                    )
                })

                setModernSignalTextColor(textColorPrimary)
            } catch (ignored: Throwable) { // A14 older
                callMethod(iconManager, "setTint", textColorPrimary)
            }

            for (i in 1..3) {
                try {
                    (getObjectField(
                        mView.findViewById(
                            mContext.resources.getIdentifier(
                                "carrier$i",
                                "id",
                                mContext.packageName
                            )
                        ), "mCarrierText"
                    ) as TextView).setTextColor(textColorPrimary)

                    (getObjectField(
                        mView.findViewById(
                            mContext.resources.getIdentifier(
                                "carrier$i",
                                "id",
                                mContext.packageName
                            )
                        ), "mMobileSignal"
                    ) as ImageView).imageTintList = ColorStateList.valueOf(textColorPrimary)

                    (getObjectField(
                        mView.findViewById(
                            mContext.resources.getIdentifier(
                                "carrier$i",
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

    private fun setMobileIconTint(modernStatusBarViewBinding: Any, textColor: Int) {
        callMethod(
            modernStatusBarViewBinding,
            "onIconTintChanged",
            textColor,
            textColor
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun setModernSignalTextColor(textColor: Int) {
        val res: Resources = mContext.resources
        if (shadeCarrierGroupController == null) return

        val carrierGroups =
            getObjectField(shadeCarrierGroupController, "mCarrierGroups") as Array<View>?

        carrierGroups?.let {
            for (shadeCarrier in it) {
                try {
                    shadeCarrier.findViewById<View>(
                        res.getIdentifier(
                            "carrier_combo",
                            "id",
                            mContext.packageName
                        )
                    )?.findViewById<TextView>(
                        res.getIdentifier(
                            "mobile_carrier_text",
                            "id",
                            mContext.packageName
                        )
                    )?.setTextColor(textColor)
                } catch (ignored: Throwable) {
                }
            }
        }
    }

    companion object {
        private val TAG = "Iconify - ${QSLightThemeA14::class.java.simpleName}: "
        private var lightQSHeaderEnabled = false
        private var dualToneQSEnabled = false
        private const val PM_LITE_BACKGROUND_CODE = 1
    }
}