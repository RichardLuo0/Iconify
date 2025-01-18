package com.drdisagree.iconify.xposed.modules

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewGroup.TEXT_ALIGNMENT_CENTER
import android.view.ViewTreeObserver.OnDrawListener
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.CUSTOM_QS_MARGIN
import com.drdisagree.iconify.common.Preferences.FIX_NOTIFICATION_COLOR
import com.drdisagree.iconify.common.Preferences.FIX_NOTIFICATION_FOOTER_BUTTON_COLOR
import com.drdisagree.iconify.common.Preferences.FIX_QS_TILE_COLOR
import com.drdisagree.iconify.common.Preferences.HEADER_CLOCK_SWITCH
import com.drdisagree.iconify.common.Preferences.HIDE_QSLABEL_SWITCH
import com.drdisagree.iconify.common.Preferences.HIDE_QS_FOOTER_BUTTONS
import com.drdisagree.iconify.common.Preferences.HIDE_QS_ON_LOCKSCREEN
import com.drdisagree.iconify.common.Preferences.HIDE_QS_SILENT_TEXT
import com.drdisagree.iconify.common.Preferences.QQS_TOPMARGIN
import com.drdisagree.iconify.common.Preferences.QS_TEXT_ALWAYS_WHITE
import com.drdisagree.iconify.common.Preferences.QS_TEXT_FOLLOW_ACCENT
import com.drdisagree.iconify.common.Preferences.QS_TOPMARGIN
import com.drdisagree.iconify.common.Preferences.VERTICAL_QSTILE_SWITCH
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.ViewHelper.toPx
import com.drdisagree.iconify.xposed.modules.utils.isPixelVariant
import com.drdisagree.iconify.xposed.modules.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookMethodMatchPattern
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
class QuickSettings(context: Context) : ModPack(context) {

    private var fixQsTileColor = true
    private var fixNotificationColor = true
    private var fixNotificationFooterButtonsColor = true
    private var qsTextAlwaysWhite = false
    private var qsTextFollowAccent = false
    private var qsTextAccentColor = Color.BLUE
    private var hideQsOnLockscreen = false
    private var hideSilentText = false
    private var hideFooterButtons = false
    private var qqsTopMargin = 100
    private var qsTopMargin = 100
    private var mParam: Any? = null
    private var mFooterButtonsContainer: ViewGroup? = null
    private var mFooterButtonsOnDrawListener: OnDrawListener? = null
    private var mSilentTextContainer: ViewGroup? = null
    private var mSilentTextOnDrawListener: OnDrawListener? = null
    private var mKeyguardStateController: Any? = null
    private val isAtLeastAndroid14 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    private var isVerticalQSTileActive = false
    private var isHideLabelActive = false
    private var customQsMarginsEnabled = false
    private var qsTilePrimaryTextSize: Float? = null
    private var qsTileSecondaryTextSize: Float? = null
    private var showHeaderClock = false

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            isVerticalQSTileActive = getBoolean(VERTICAL_QSTILE_SWITCH, false)
            isHideLabelActive = getBoolean(HIDE_QSLABEL_SWITCH, false)
            customQsMarginsEnabled = getBoolean(CUSTOM_QS_MARGIN, false)
            qqsTopMargin = getSliderInt(QQS_TOPMARGIN, 100)
            qsTopMargin = getSliderInt(QS_TOPMARGIN, 100)
            fixQsTileColor = isAtLeastAndroid14 &&
                    getBoolean(FIX_QS_TILE_COLOR, false)
            fixNotificationColor = isAtLeastAndroid14 &&
                    getBoolean(FIX_NOTIFICATION_COLOR, false)
            fixNotificationFooterButtonsColor = isAtLeastAndroid14 &&
                    getBoolean(FIX_NOTIFICATION_FOOTER_BUTTON_COLOR, false)
            qsTextAlwaysWhite = getBoolean(QS_TEXT_ALWAYS_WHITE, false)
            qsTextFollowAccent = getBoolean(QS_TEXT_FOLLOW_ACCENT, false)
            hideQsOnLockscreen = getBoolean(HIDE_QS_ON_LOCKSCREEN, false)
            hideSilentText = getBoolean(HIDE_QS_SILENT_TEXT, false)
            hideFooterButtons = getBoolean(HIDE_QS_FOOTER_BUTTONS, false)
            showHeaderClock = getBoolean(HEADER_CLOCK_SWITCH, false)
        }

        triggerQsElementVisibility()
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        initQsAccentColor()
        setVerticalTiles()
        setQsMargin()
        fixQsTileAndLabelColorA14()
        fixNotificationColorA14()
        manageQsElementVisibility()
        disableQsOnSecureLockScreen()
    }

    private fun setVerticalTiles() {
        val qsTileViewImplClass = findClass("$SYSTEMUI_PACKAGE.qs.tileimpl.QSTileViewImpl")
        val fontSizeUtils = findClass("$SYSTEMUI_PACKAGE.FontSizeUtils")

        qsTileViewImplClass
            .hookConstructor()
            .runAfter { param ->
                if (!isVerticalQSTileActive) return@runAfter

                mParam = param.thisObject

                try {
                    (mParam as LinearLayout).gravity = Gravity.CENTER
                    (mParam as LinearLayout).orientation = LinearLayout.VERTICAL

                    (getObjectField(
                        mParam,
                        "label"
                    ) as TextView).textAlignment = TEXT_ALIGNMENT_CENTER

                    (getObjectField(
                        mParam,
                        "secondaryLabel"
                    ) as TextView).textAlignment = TEXT_ALIGNMENT_CENTER

                    (getObjectField(
                        mParam,
                        "labelContainer"
                    ) as LinearLayout).layoutParams = MarginLayoutParams(
                        MarginLayoutParams.MATCH_PARENT, MarginLayoutParams.WRAP_CONTENT
                    )

                    (getObjectField(
                        mParam,
                        "sideView"
                    ) as View).visibility = View.GONE

                    (mParam as LinearLayout).removeView(
                        getObjectField(
                            mParam,
                            "labelContainer"
                        ) as LinearLayout
                    )

                    if (!isHideLabelActive) {
                        (getObjectField(
                            mParam,
                            "labelContainer"
                        ) as LinearLayout).gravity = Gravity.CENTER_HORIZONTAL

                        (mParam as LinearLayout).addView(
                            getObjectField(
                                mParam,
                                "labelContainer"
                            ) as LinearLayout
                        )
                    }

                    fixTileLayout(mParam as LinearLayout, mParam)

                    if (qsTilePrimaryTextSize == null || qsTileSecondaryTextSize == null) {
                        try {
                            callStaticMethod(
                                fontSizeUtils,
                                "updateFontSize",
                                mContext.resources.getIdentifier(
                                    "qs_tile_text_size",
                                    "dimen",
                                    mContext.packageName
                                ),
                                getObjectField(mParam, "label")
                            )

                            callStaticMethod(
                                fontSizeUtils,
                                "updateFontSize",
                                mContext.resources.getIdentifier(
                                    "qs_tile_text_size",
                                    "dimen",
                                    mContext.packageName
                                ),
                                getObjectField(mParam, "secondaryLabel")
                            )
                        } catch (ignored: Throwable) {
                        }

                        qsTilePrimaryTextSize = (getObjectField(
                            mParam,
                            "label"
                        ) as TextView).textSize

                        qsTileSecondaryTextSize = (getObjectField(
                            mParam,
                            "secondaryLabel"
                        ) as TextView).textSize
                    }
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        qsTileViewImplClass
            .hookMethod("onConfigurationChanged")
            .runAfter { param ->
                if (!isVerticalQSTileActive) return@runAfter

                fixTileLayout(param.thisObject as LinearLayout, mParam)
            }
    }

    private fun setQsMargin() {
        Resources::class.java
            .hookMethod("getDimensionPixelSize")
            .suppressError()
            .runBefore { param ->
                if (!customQsMarginsEnabled) return@runBefore

                val qqsHeaderResNames = arrayOf(
                    "qs_header_system_icons_area_height",
                    "qqs_layout_margin_top",
                    "qs_header_row_min_height",
                    "large_screen_shade_header_min_height"
                )

                qqsHeaderResNames.forEach { resName ->
                    try {
                        val resId = mContext.resources.getIdentifier(
                            resName,
                            "dimen",
                            mContext.packageName
                        )

                        if (param.args[0] == resId) {
                            param.result = mContext.toPx(qqsTopMargin)
                        }
                    } catch (ignored: Throwable) {
                    }
                }

                val qsHeaderResNames = arrayOf(
                    "qs_panel_padding_top",
                    "qs_panel_padding_top_combined_headers",
                    "qs_header_height"
                )

                qsHeaderResNames.forEach { resName ->
                    try {
                        val resId = mContext.resources.getIdentifier(
                            resName,
                            "dimen",
                            mContext.packageName
                        )

                        if (param.args[0] == resId) {
                            if (showHeaderClock && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                val isLandscape =
                                    mContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                                param.result = if (isLandscape) 0 else mContext.toPx(qsTopMargin)
                            } else {
                                param.result = mContext.toPx(qsTopMargin)
                            }
                        }
                    } catch (ignored: Throwable) {
                    }
                }
            }

        val quickStatusBarHeader = findClass("$SYSTEMUI_PACKAGE.qs.QuickStatusBarHeader")

        quickStatusBarHeader
            .hookMethod("updateResources")
            .runAfter { param ->
                if (!customQsMarginsEnabled) return@runAfter

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        val res = mContext.resources

                        val qqsLP = callMethod(
                            getObjectField(
                                param.thisObject,
                                "mHeaderQsPanel"
                            ), "getLayoutParams"
                        ) as MarginLayoutParams
                        qqsLP.topMargin = mContext.resources.getDimensionPixelSize(
                            res.getIdentifier(
                                "qqs_layout_margin_top",
                                "dimen",
                                mContext.packageName
                            )
                        )

                        callMethod(
                            getObjectField(
                                param.thisObject,
                                "mHeaderQsPanel"
                            ), "setLayoutParams", qqsLP
                        )
                    } catch (throwable: Throwable) {
                        log(TAG + throwable)
                    }
                }
            }
    }

    private fun fixQsTileAndLabelColorA14() {
        if (!isAtLeastAndroid14) return

        val qsTileViewImplClass = findClass("$SYSTEMUI_PACKAGE.qs.tileimpl.QSTileViewImpl")!!

        if (fixQsTileColor && qsTileViewImplClass.declaredMethods.find { it.name == "setStateLayer" } != null) {
            qsTileViewImplClass
                .hookMethod("setStateLayer")
                .replace { param ->
                    val currentState = getObjectField(
                        param.thisObject,
                        "currentState"
                    ) as Int

                    val ld: LayerDrawable = (getObjectField(
                        param.thisObject,
                        "backgroundDrawable"
                    ) as LayerDrawable).mutate() as LayerDrawable

                    when (currentState) {
                        Tile.STATE_ACTIVE -> ld.setTint(Color.WHITE)

                        Tile.STATE_INACTIVE -> ld.setTint(Color.TRANSPARENT)

                        else -> ld.setTint(Color.TRANSPARENT)
                    }
                }
        }

        val removeQsTileTint: XC_MethodHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!fixQsTileColor) return

                try {
                    setObjectField(
                        param.thisObject,
                        "colorActive",
                        Color.WHITE
                    )
                    setObjectField(
                        param.thisObject,
                        "colorInactive",
                        Color.TRANSPARENT
                    )
                    setObjectField(
                        param.thisObject,
                        "colorUnavailable",
                        Color.TRANSPARENT
                    )
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }
        }

        qsTileViewImplClass
            .hookConstructor()
            .run(removeQsTileTint)

        qsTileViewImplClass
            .hookMethod("updateResources")
            .suppressError()
            .run(removeQsTileTint)

        qsTileViewImplClass
            .hookConstructor()
            .runAfter { initQsAccentColor() }

        qsTileViewImplClass
            .hookConstructor()
            .runAfter { param ->
                if (!qsTextAlwaysWhite && !qsTextFollowAccent) return@runAfter

                @ColorInt val color: Int = qsIconLabelColor
                @ColorInt val colorAlpha =
                    color and 0xFFFFFF or (Math.round(Color.alpha(color) * 0.8f) shl 24)

                setObjectField(
                    param.thisObject,
                    "colorLabelActive",
                    color
                )
                setObjectField(
                    param.thisObject,
                    "colorSecondaryLabelActive",
                    colorAlpha
                )
            }

        qsTileViewImplClass
            .hookMethod("getLabelColorForState")
            .runBefore { param ->
                if (isQsIconLabelStateActive(param, 0)) {
                    param.result = qsIconLabelColor
                }
            }

        qsTileViewImplClass
            .hookMethod("getSecondaryLabelColorForState")
            .runBefore { param ->
                if (isQsIconLabelStateActive(param, 0)) {
                    @ColorInt val color: Int = qsIconLabelColor
                    @ColorInt val colorAlpha =
                        color and 0xFFFFFF or (Math.round(Color.alpha(color) * 0.8f) shl 24)
                    param.result = colorAlpha
                }
            }

        val qsIconViewImplClass = findClass("$SYSTEMUI_PACKAGE.qs.tileimpl.QSIconViewImpl")

        qsIconViewImplClass
            .hookMethod("getIconColorForState", "getColor")
            .runBefore { param ->
                val stateIndex = if (param.args.size > 1) 1 else 0

                if (isQsIconLabelStateActive(param, stateIndex)) {
                    param.result = qsIconLabelColor
                }
            }

        qsIconViewImplClass
            .hookMethod("updateIcon")
            .runAfter { param ->
                val stateIndex = if (param.args.size > 1) 1 else 0

                if (isQsIconLabelStateActive(param, stateIndex)) {
                    val mIcon = param.args[0] as ImageView
                    mIcon.imageTintList = ColorStateList.valueOf(qsIconLabelColor)
                }
            }

        val qsContainerImplClass = findClass("$SYSTEMUI_PACKAGE.qs.QSContainerImpl")

        qsContainerImplClass
            .hookMethod("updateResources")
            .suppressError()
            .runAfter { param ->
                if (!qsTextAlwaysWhite && !qsTextFollowAccent) return@runAfter

                try {
                    val res = mContext.resources
                    val view = (param.thisObject as ViewGroup).findViewById<ViewGroup>(
                        res.getIdentifier(
                            "qs_footer_actions",
                            "id",
                            mContext.packageName
                        )
                    )

                    @ColorInt val color: Int = qsIconLabelColor

                    try {
                        val pmButtonContainer = view.findViewById<ViewGroup>(
                            res.getIdentifier(
                                "pm_lite",
                                "id",
                                mContext.packageName
                            )
                        )
                        (pmButtonContainer.getChildAt(0) as ImageView).setColorFilter(
                            color,
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
                        pmButton.imageTintList = ColorStateList.valueOf(color)
                    }
                } catch (ignored: Throwable) {
                }
            }

        // Compose implementation of QS Footer actions
        val footerActionsButtonViewModelClass =
            findClass("$SYSTEMUI_PACKAGE.qs.footer.ui.viewmodel.FooterActionsButtonViewModel")

        footerActionsButtonViewModelClass
            .hookConstructor()
            .runBefore { param ->
                if (!qsTextAlwaysWhite && !qsTextFollowAccent) return@runBefore

                if (mContext.resources.getResourceName((param.args[0] as Int))
                        .split("/".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1] == "pm_lite"
                ) {
                    param.args[2] = qsIconLabelColor
                }
            }

        // Auto brightness icon color
        val brightnessControllerClass =
            findClass("$SYSTEMUI_PACKAGE.settings.brightness.BrightnessController")
        val brightnessMirrorControllerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.policy.BrightnessMirrorController")
        val brightnessSliderControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.settings.brightness.BrightnessSliderController",
            suppressError = true
        )

        brightnessControllerClass
            .hookConstructor()
            .runAfter { initQsAccentColor() }

        brightnessControllerClass
            .hookMethod("updateIcon")
            .suppressError()
            .runAfter { param ->
                if (!qsTextAlwaysWhite && !qsTextFollowAccent) return@runAfter

                @ColorInt val color: Int = qsIconLabelColor
                try {
                    (getObjectField(param.thisObject, "mIcon") as ImageView).imageTintList =
                        ColorStateList.valueOf(color)
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        brightnessSliderControllerClass
            .hookConstructor()
            .runAfter { param ->
                if (!qsTextAlwaysWhite && !qsTextFollowAccent) return@runAfter

                initQsAccentColor()

                @ColorInt val color: Int = qsIconLabelColor

                try {
                    (getObjectField(param.thisObject, "mIcon") as ImageView).imageTintList =
                        ColorStateList.valueOf(color)
                } catch (throwable: Throwable) {
                    try {
                        (getObjectField(
                            param.thisObject,
                            "mIconView"
                        ) as ImageView).imageTintList =
                            ColorStateList.valueOf(color)
                    } catch (ignored: Throwable) {
                    }
                }
            }

        brightnessMirrorControllerClass
            .hookMethod("updateIcon")
            .suppressError()
            .runAfter { param ->
                if (!qsTextAlwaysWhite && !qsTextFollowAccent) return@runAfter

                @ColorInt val color: Int = qsIconLabelColor

                try {
                    (getObjectField(param.thisObject, "mIcon") as ImageView).imageTintList =
                        ColorStateList.valueOf(color)
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }
    }

    private fun fixNotificationColorA14() {
        if (!isAtLeastAndroid14) return

        val activatableNotificationViewClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.ActivatableNotificationView")
        val notificationBackgroundViewClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.NotificationBackgroundView")
        val footerViewClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.notification.footer.ui.view.FooterView",
            "$SYSTEMUI_PACKAGE.statusbar.notification.row.FooterView"
        )

        val removeNotificationTint: XC_MethodHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!fixNotificationColor) return

                val notificationBackgroundView = getObjectField(
                    param.thisObject,
                    "mBackgroundNormal"
                ) as View

                try {
                    setObjectField(
                        param.thisObject,
                        "mCurrentBackgroundTint",
                        param.args[0] as Int
                    )
                } catch (ignored: Throwable) {
                }

                try {
                    setObjectField(notificationBackgroundView, "mTintColor", 0)
                } catch (ignored: Throwable) {
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                if (!fixNotificationColor) return

                val notificationBackgroundView = getObjectField(
                    param.thisObject,
                    "mBackgroundNormal"
                ) as View

                try {
                    callMethod(notificationBackgroundView, "setColorFilter", 0)
                } catch (ignored: Throwable) {
                }

                try {
                    (getObjectField(
                        notificationBackgroundView,
                        "mBackground"
                    ) as Drawable).colorFilter = null
                } catch (ignored: Throwable) {
                }

                try {
                    setObjectField(notificationBackgroundView, "mTintColor", 0)
                } catch (ignored: Throwable) {
                }

                Handler(Looper.getMainLooper()).post {
                    notificationBackgroundView.invalidate()
                }
            }
        }

        activatableNotificationViewClass
            .hookMethod("setBackgroundTintColor")
            .run(removeNotificationTint)

        activatableNotificationViewClass
            .hookMethod("updateBackgroundTint")
            .run(removeNotificationTint)

        activatableNotificationViewClass
            .hookMethod("calculateBgColor")
            .runBefore { param ->
                if (!fixNotificationColor) return@runBefore

                try {
                    param.result = getObjectField(
                        param.thisObject,
                        "mCurrentBackgroundTint"
                    )
                } catch (ignored: Throwable) {
                }
            }

        notificationBackgroundViewClass
            .hookMethodMatchPattern("setCustomBackground.*")
            .runBefore { param ->
                if (!fixNotificationColor) return@runBefore

                setObjectField(param.thisObject, "mTintColor", 0)
            }

        footerViewClass
            .hookMethodMatchPattern("updateColors.*")
            .runAfter { param ->
                if (!fixNotificationFooterButtonsColor) return@runAfter

                try {
                    val mManageButton = try {
                        getObjectField(param.thisObject, "mManageButton")
                    } catch (ignored: Throwable) {
                        getObjectField(param.thisObject, "mManageOrHistoryButton")
                    } as Button
                    val mClearAllButton = try {
                        getObjectField(param.thisObject, "mClearAllButton")
                    } catch (ignored: Throwable) {
                        getObjectField(param.thisObject, "mDismissButton")
                    } as Button

                    mManageButton.background?.colorFilter = null
                    mClearAllButton.background?.colorFilter = null

                    Handler(Looper.getMainLooper()).post {
                        mManageButton.invalidate()
                        mClearAllButton.invalidate()
                    }
                } catch (ignored: Throwable) {
                }
            }
    }

    private fun manageQsElementVisibility() {
        val footerViewClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.notification.footer.ui.view.FooterView",
            "$SYSTEMUI_PACKAGE.statusbar.notification.row.FooterView"
        )

        footerViewClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                val view = param.thisObject as View

                val resId1 = mContext.resources.getIdentifier(
                    "manage_text",
                    "id",
                    mContext.packageName
                )

                val resId2 = mContext.resources.getIdentifier(
                    "dismiss_text",
                    "id",
                    mContext.packageName
                )

                if (resId1 != 0) {
                    mFooterButtonsContainer =
                        view.findViewById<View>(resId1).parent as ViewGroup
                } else if (resId2 != 0) {
                    mFooterButtonsContainer =
                        view.findViewById<View>(resId2).parent as ViewGroup
                }

                triggerQsElementVisibility()
            }

        val sectionHeaderViewClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.stack.SectionHeaderView")

        sectionHeaderViewClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                mSilentTextContainer = param.thisObject as ViewGroup

                triggerQsElementVisibility()
            }
    }

    private fun disableQsOnSecureLockScreen() {
        val remoteInputQuickSettingsDisablerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.policy.RemoteInputQuickSettingsDisabler")
        val phoneStatusBarPolicyClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.PhoneStatusBarPolicy")

        phoneStatusBarPolicyClass
            .hookConstructor()
            .runAfter { param ->
                mKeyguardStateController = getObjectField(
                    param.thisObject,
                    "mKeyguardStateController"
                )

                if (mKeyguardStateController == null) {
                    log(TAG + "mKeyguardStateController is null")
                }
            }

        remoteInputQuickSettingsDisablerClass
            .hookMethod("adjustDisableFlags")
            .runBefore { param ->
                if (!hideQsOnLockscreen || mKeyguardStateController == null) return@runBefore

                val isUnlocked = !(getObjectField(
                    mKeyguardStateController,
                    "mShowing"
                ) as Boolean) || getObjectField(
                    mKeyguardStateController,
                    "mCanDismissLockScreen"
                ) as Boolean

                /*
                 * Location: frameworks/base/core/java/android/app/StatusBarManager.java
                 * public static final int DISABLE2_QUICK_SETTINGS = 1;
                 */
                param.result = if (hideQsOnLockscreen && !isUnlocked) {
                    param.args[0] as Int or 1 // DISABLE2_QUICK_SETTINGS
                } else {
                    param.args[0]
                }
            }
    }

    private fun isQsIconLabelStateActive(param: MethodHookParam?, stateIndex: Int): Boolean {
        if (param?.args == null) return false

        if (!qsTextAlwaysWhite && !qsTextFollowAccent) return false

        val isActiveState: Boolean = try {
            getObjectField(
                param.args[stateIndex],
                "state"
            ) as Int == Tile.STATE_ACTIVE
        } catch (throwable: Throwable) {
            try {
                param.args[stateIndex] as Int == Tile.STATE_ACTIVE
            } catch (throwable1: Throwable) {
                try {
                    param.args[stateIndex] as Boolean
                } catch (throwable2: Throwable) {
                    log(TAG + throwable2)
                    false
                }
            }
        }

        return isActiveState
    }

    @get:ColorInt
    private val qsIconLabelColor: Int
        get() {
            return when {
                qsTextAlwaysWhite -> Color.WHITE
                qsTextFollowAccent -> qsTextAccentColor
                else -> Color.WHITE
            }
        }

    private fun initQsAccentColor() {
        qsTextAccentColor = mContext.resources.getColor(
            mContext.resources.getIdentifier(
                if (isPixelVariant) {
                    "android:color/holo_green_light"
                } else {
                    "android:color/holo_blue_light"
                },
                "color",
                mContext.packageName
            ), mContext.theme
        )
    }

    private fun triggerQsElementVisibility() {
        if (mFooterButtonsContainer != null) {
            if (mFooterButtonsOnDrawListener == null) {
                mFooterButtonsOnDrawListener =
                    OnDrawListener { mFooterButtonsContainer!!.visibility = View.INVISIBLE }
            }

            try {
                if (hideFooterButtons) {
                    mFooterButtonsContainer!!.visibility = View.INVISIBLE
                    mFooterButtonsContainer!!.viewTreeObserver
                        .addOnDrawListener(mFooterButtonsOnDrawListener)
                } else {
                    mFooterButtonsContainer!!.viewTreeObserver
                        .removeOnDrawListener(mFooterButtonsOnDrawListener)
                    mFooterButtonsContainer!!.visibility = View.VISIBLE
                }
            } catch (ignored: Throwable) {
            }
        }

        if (mSilentTextContainer != null) {
            if (mSilentTextOnDrawListener == null) {
                mSilentTextOnDrawListener =
                    OnDrawListener { mSilentTextContainer!!.visibility = View.GONE }
            }

            try {
                if (hideSilentText) {
                    mSilentTextContainer!!.visibility = View.GONE
                    mSilentTextContainer!!.viewTreeObserver
                        .addOnDrawListener(mSilentTextOnDrawListener)
                } else {
                    mSilentTextContainer!!.viewTreeObserver
                        .removeOnDrawListener(mSilentTextOnDrawListener)
                    mSilentTextContainer!!.visibility = View.VISIBLE
                }
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun fixTileLayout(tile: LinearLayout, param: Any?) {
        val mRes = mContext.resources
        val padding = mRes.getDimensionPixelSize(
            mRes.getIdentifier(
                "qs_tile_padding",
                "dimen",
                mContext.packageName
            )
        )

        tile.apply {
            setPadding(padding, padding, padding, padding)
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
        }

        if (!isHideLabelActive) {
            try {
                ((getObjectField(
                    tile,
                    "labelContainer"
                ) as LinearLayout).layoutParams as MarginLayoutParams).marginStart = 0

                ((getObjectField(
                    tile,
                    "labelContainer"
                ) as LinearLayout).layoutParams as MarginLayoutParams).topMargin = mContext.toPx(2)
            } catch (throwable: Throwable) {
                log(TAG + throwable)
            }
        }

        if (param != null) {
            (getObjectField(
                param,
                "label"
            ) as TextView).textAlignment = TEXT_ALIGNMENT_CENTER

            (getObjectField(
                param,
                "secondaryLabel"
            ) as TextView).textAlignment = TEXT_ALIGNMENT_CENTER
        }
    }

    companion object {
        private val TAG = "Iconify - ${QuickSettings::class.java.simpleName}: "
    }
}