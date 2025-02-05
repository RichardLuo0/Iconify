package com.drdisagree.iconify.xposed.modules.statusbar

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.drdisagree.iconify.common.Const.FRAMEWORK_PACKAGE
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.COLORED_STATUSBAR_ICON
import com.drdisagree.iconify.common.Preferences.HIDE_LOCKSCREEN_CARRIER
import com.drdisagree.iconify.common.Preferences.HIDE_LOCKSCREEN_STATUSBAR
import com.drdisagree.iconify.common.Preferences.SB_CLOCK_SIZE
import com.drdisagree.iconify.common.Preferences.SB_CLOCK_SIZE_SWITCH
import com.drdisagree.iconify.common.Preferences.STATUSBAR_SWAP_WIFI_CELLULAR
import com.drdisagree.iconify.xposed.HookRes.Companion.resParams
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.StatusBarClock.getCenterClockView
import com.drdisagree.iconify.xposed.modules.extras.utils.StatusBarClock.getLeftClockView
import com.drdisagree.iconify.xposed.modules.extras.utils.StatusBarClock.getRightClockView
import com.drdisagree.iconify.xposed.modules.extras.utils.StatusBarClock.setClockGravity
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getFieldSilently
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setField
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LayoutInflated
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
class Statusbar(context: Context) : ModPack(context) {

    private var mColoredStatusbarIcon = false
    private var sbClockSizeSwitch = false
    private var sbClockSize = 14
    private var mClockView: TextView? = null
    private var mCenterClockView: TextView? = null
    private var mRightClockView: TextView? = null
    private var mLeftClockSize = 14
    private var mCenterClockSize = 14
    private var mRightClockSize = 14
    private var hideLockscreenCarrier = false
    private var hideLockscreenStatusbar = false
    private var swapWifiAndCellularIcon = false

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            mColoredStatusbarIcon = getBoolean(COLORED_STATUSBAR_ICON, false)
            sbClockSizeSwitch = getBoolean(SB_CLOCK_SIZE_SWITCH, false)
            sbClockSize = getSliderInt(SB_CLOCK_SIZE, 14)
            hideLockscreenCarrier = getBoolean(HIDE_LOCKSCREEN_CARRIER, false)
            hideLockscreenStatusbar = getBoolean(HIDE_LOCKSCREEN_STATUSBAR, false)
            swapWifiAndCellularIcon = getBoolean(STATUSBAR_SWAP_WIFI_CELLULAR, false)
        }

        when (key.firstOrNull()) {
            in setOf(
                SB_CLOCK_SIZE_SWITCH,
                SB_CLOCK_SIZE
            ) -> setClockSize()

            in setOf(
                HIDE_LOCKSCREEN_CARRIER,
                HIDE_LOCKSCREEN_STATUSBAR
            ) -> hideLockscreenCarrierOrStatusbar()
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        setColoredNotificationIcons()
        hideLockscreenCarrierOrStatusbar()
        applyClockSize()
        swapWifiAndCellularIcon()
    }

    private fun setColoredNotificationIcons() {
        if (!mColoredStatusbarIcon) return

        val notificationIconContainerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.NotificationIconContainer")
        val iconStateClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.NotificationIconContainer\$IconState")
        val legacyNotificationIconAreaControllerImplClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.LegacyNotificationIconAreaControllerImpl")
        val drawableSizeClass = findClass("$SYSTEMUI_PACKAGE.util.drawable.DrawableSize")
        val scalingDrawableWrapperClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.ScalingDrawableWrapper")!!
        val statusBarIconViewClass = findClass("$SYSTEMUI_PACKAGE.statusbar.StatusBarIconView")

        @Suppress("UNCHECKED_CAST")
        notificationIconContainerClass
            .hookMethod("applyIconStates")
            .runAfter { param ->
                val mIconStates: HashMap<View, Any> = param.thisObject.getField(
                    "mIconStates"
                ) as HashMap<View, Any>

                for (icon in mIconStates.keys) {
                    removeTintForStatusbarIcon(icon)
                }
            }

        iconStateClass
            .hookMethod(
                "initFrom",
                "applyToView"
            )
            .runAfter { param -> removeTintForStatusbarIcon(param) }

        statusBarIconViewClass
            .hookMethod("updateIconColor")
            .replace { }

        legacyNotificationIconAreaControllerImplClass
            .hookMethod("updateTintForIcon")
            .runAfter { param ->
                removeTintForStatusbarIcon(param)

                try {
                    val view = param.args[0] as View
                    view.callMethod("setStaticDrawableColor", 0) // StatusBarIconView.NO_COLOR
                    view.callMethod("setDecorColor", Color.WHITE)
                } catch (ignored: Throwable) {
                }
            }

        try {
            statusBarIconViewClass
                .hookMethod("getIcon")
                .parameters(
                    Context::class.java,
                    Context::class.java,
                    "com.android.internal.statusbar.StatusBarIcon"
                )
                .runBefore { param ->
                    val sysuiContext = param.args[0] as Context
                    val context = param.args[1] as Context
                    val statusBarIcon = param.args[2]

                    setNotificationIcon(
                        statusBarIcon,
                        context,
                        sysuiContext,
                        drawableSizeClass,
                        param,
                        scalingDrawableWrapperClass
                    )
                }
        } catch (ignored: Throwable) {
            statusBarIconViewClass
                .hookMethod("getIcon")
                .parameters("com.android.internal.statusbar.StatusBarIcon")
                .runBefore { param ->
                    val sysuiContext = mContext
                    var context: Context? = null
                    val statusBarIcon = param.args[0]
                    val statusBarNotification = param.thisObject.getFieldSilently("mNotification")

                    if (statusBarNotification != null) {
                        context = statusBarNotification.callMethod(
                            "getPackageContext",
                            mContext
                        ) as Context?
                    }

                    if (context == null) {
                        context = mContext
                    }

                    setNotificationIcon(
                        statusBarIcon,
                        context,
                        sysuiContext,
                        drawableSizeClass,
                        param,
                        scalingDrawableWrapperClass
                    )
                }
        }
    }

    private fun removeTintForStatusbarIcon(param: XC_MethodHook.MethodHookParam) {
        val icon = param.args[0] as View
        removeTintForStatusbarIcon(icon)
    }

    private fun removeTintForStatusbarIcon(icon: View) {
        try {
            val pkgName = icon
                .getField("mIcon")
                .getField("pkg") as String

            if (!pkgName.contains("systemui")) {
                icon.setField("mCurrentSetColor", 0) // StatusBarIconView.NO_COLOR
                icon.callMethod("updateIconColor")
            }
        } catch (ignored: Throwable) {
            log(this@Statusbar, ignored)
        }
    }

    private fun setNotificationIcon(
        statusBarIcon: Any?,
        context: Context,
        sysuiContext: Context,
        drawableSize: Class<*>?,
        param: XC_MethodHook.MethodHookParam,
        scalingDrawableWrapper: Class<*>
    ) {
        var icon: Drawable
        val res = sysuiContext.resources
        val pkgName = statusBarIcon.getField("pkg") as String

        if (listOf("com.android", "systemui").any { pkgName.contains(it) }) {
            return
        }

        try {
            icon = context.packageManager.getApplicationIcon(pkgName)
        } catch (e: Throwable) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isLowRamDevice = callStaticMethod(
                ActivityManager::class.java,
                "isLowRamDeviceStatic"
            ) as Boolean

            val maxIconSize = res.getDimensionPixelSize(
                res.getIdentifier(
                    if (isLowRamDevice) {
                        "notification_small_icon_size_low_ram"
                    } else {
                        "notification_small_icon_size"
                    },
                    "dimen",
                    FRAMEWORK_PACKAGE
                )
            )

            if (drawableSize != null) {
                icon = callStaticMethod(
                    drawableSize,
                    "downscaleToSize",
                    res,
                    icon,
                    maxIconSize,
                    maxIconSize
                ) as Drawable
            }
        }

        val typedValue = TypedValue()
        res.getValue(
            res.getIdentifier(
                "status_bar_icon_scale_factor",
                "dimen",
                SYSTEMUI_PACKAGE
            ),
            typedValue,
            true
        )
        val scaleFactor = typedValue.float

        if (scaleFactor == 1f) {
            param.result = icon
        } else {
            param.result = scalingDrawableWrapper.getConstructor(
                Drawable::class.java,
                Float::class.javaPrimitiveType
            ).newInstance(icon, scaleFactor)
        }
    }

    private fun hideLockscreenCarrierOrStatusbar() {
        val resParam: InitPackageResourcesParam = resParams[SYSTEMUI_PACKAGE] ?: return

        try {
            resParam.res.hookLayout(
                SYSTEMUI_PACKAGE,
                "layout",
                "keyguard_status_bar",
                object : XC_LayoutInflated() {
                    override fun handleLayoutInflated(liparam: LayoutInflatedParam) {
                        if (hideLockscreenCarrier) {
                            try {
                                liparam.view.findViewById<TextView>(
                                    liparam.res.getIdentifier(
                                        "keyguard_carrier_text",
                                        "id",
                                        mContext.packageName
                                    )
                                ).apply {
                                    layoutParams.height = 0
                                    visibility = View.INVISIBLE
                                    requestLayout()
                                }
                            } catch (ignored: Throwable) {
                            }
                        }

                        if (hideLockscreenStatusbar) {
                            try {
                                liparam.view.findViewById<LinearLayout>(
                                    liparam.res.getIdentifier(
                                        "status_icon_area",
                                        "id",
                                        mContext.packageName
                                    )
                                ).apply {
                                    layoutParams.height = 0
                                    visibility = View.INVISIBLE
                                    requestLayout()
                                }
                            } catch (ignored: Throwable) {
                            }

                            try {
                                liparam.view.findViewById<TextView>(
                                    liparam.res.getIdentifier(
                                        "keyguard_carrier_text",
                                        "id",
                                        mContext.packageName
                                    )
                                ).apply {
                                    layoutParams.height = 0
                                    visibility = View.INVISIBLE
                                    requestLayout()
                                }
                            } catch (ignored: Throwable) {
                            }
                        }
                    }
                })
        } catch (ignored: Throwable) {
        }
    }

    private fun applyClockSize() {
        val collapsedStatusBarFragment = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.phone.CollapsedStatusBarFragment",
            "$SYSTEMUI_PACKAGE.statusbar.phone.fragment.CollapsedStatusBarFragment"
        )

        collapsedStatusBarFragment
            .hookMethod("onViewCreated")
            .parameters(
                View::class.java,
                Bundle::class.java
            )
            .runAfter { param ->
                mClockView = getLeftClockView(mContext, param) as? TextView
                mCenterClockView = getCenterClockView(mContext, param) as? TextView
                mRightClockView = getRightClockView(mContext, param) as? TextView

                mLeftClockSize = mClockView?.textSize?.toInt() ?: 14
                mCenterClockSize = mCenterClockView?.textSize?.toInt() ?: 14
                mRightClockSize = mRightClockView?.textSize?.toInt() ?: 14

                setClockSize()

                val textChangeListener = object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }

                    override fun afterTextChanged(s: Editable) {
                        setClockSize()
                    }
                }

                mClockView?.addTextChangedListener(textChangeListener)
                mCenterClockView?.addTextChangedListener(textChangeListener)
                mRightClockView?.addTextChangedListener(textChangeListener)
            }
    }

    private fun swapWifiAndCellularIcon() {
        val statusBarIconListClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.phone.StatusBarIconList",
            "$SYSTEMUI_PACKAGE.statusbar.phone.ui.StatusBarIconList"
        )
        val iconManagerClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.phone.StatusBarIconController\$IconManager",
            "$SYSTEMUI_PACKAGE.statusbar.phone.ui.IconManager"
        )
        val darkIconManagerClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.phone.StatusBarIconController\$DarkIconManager",
            "$SYSTEMUI_PACKAGE.statusbar.phone.ui.DarkIconManager"
        )
        val statusBarIconViewClass = findClass("$SYSTEMUI_PACKAGE.statusbar.StatusBarIconView")

        fun swapStatusbarIconSlots(list: List<*>, param: MethodHookParam, fieldName: String) {
            val wifiIndex = list.indexOfFirst { (it.getField("mName") as String) == "wifi" }
            val mobileIndex = list.indexOfFirst { (it.getField("mName") as String) == "mobile" }

            if (wifiIndex != -1 && mobileIndex != -1 && mobileIndex > wifiIndex) {
                val mutableList = list.toMutableList()
                mutableList[wifiIndex] = mutableList[mobileIndex].also {
                    mutableList[mobileIndex] = mutableList[wifiIndex]
                }
                param.thisObject.setField(fieldName, mutableList)
            }
        }

        statusBarIconListClass
            .hookConstructor()
            .runAfter { param ->
                if (!swapWifiAndCellularIcon) return@runAfter

                swapStatusbarIconSlots(
                    param.thisObject.getField("mSlots") as ArrayList<*>,
                    param,
                    "mSlots"
                )
                swapStatusbarIconSlots(
                    param.thisObject.getField("mViewOnlySlots") as List<*>,
                    param,
                    "mViewOnlySlots"
                )
            }

        iconManagerClass
            .hookMethod(
                "addIcon",
                "addHolder",
                "addNewWifiIcon",
                "addNewMobileIcon",
                "addNetworkTraffic",
                "addBluetoothIcon",
                "addBindableIcon"
            )
            .runBefore { param ->
                if (!swapWifiAndCellularIcon) return@runBefore

                // addBindableIcon has index parameter at 1
                val intIdx = if (param.args[0] is Int) 0 else 1

                val index = param.args[intIdx] as Int
                val mGroup = param.thisObject.getField("mGroup") as ViewGroup

                param.args[intIdx] = index.coerceIn(0, mGroup.childCount)
            }

        fun handleSetIcon(param: MethodHookParam, applyDark: Boolean = false) {
            if (!swapWifiAndCellularIcon) return

            val viewIndex = param.args[0] as Int
            val icon = param.args[1]
            val mGroup = param.thisObject.getField("mGroup") as ViewGroup
            val view = mGroup.getChildAt(viewIndex)

            if (view.javaClass.simpleName != statusBarIconViewClass?.simpleName) {
                try {
                    val layoutParams: ViewGroup.LayoutParams = view.layoutParams
                    val onCreateLayoutParams: LinearLayout.LayoutParams =
                        param.thisObject.callMethod(
                            "onCreateLayoutParams",
                            icon.getField("shape")
                        ) as LinearLayout.LayoutParams

                    if (onCreateLayoutParams.width != layoutParams.width ||
                        onCreateLayoutParams.height != layoutParams.height
                    ) {
                        view.setLayoutParams(onCreateLayoutParams)
                    }
                } catch (ignored: Throwable) {
                    // icon.shape does not exist in older versions
                }

                if (applyDark) {
                    val mDarkIconDispatcher = param.thisObject.getField("mDarkIconDispatcher")
                    mDarkIconDispatcher.callMethod("applyDark", view)
                }

                param.result = null
            }
        }

        iconManagerClass
            .hookMethod("onSetIcon")
            .runBefore { param -> handleSetIcon(param, false) }

        darkIconManagerClass
            .hookMethod("onSetIcon")
            .runBefore { param -> handleSetIcon(param, true) }
    }

    @SuppressLint("RtlHardcoded")
    private fun setClockSize() {
        val leftClockSize = if (sbClockSizeSwitch) sbClockSize else mLeftClockSize
        val centerClockSize = if (sbClockSizeSwitch) sbClockSize else mCenterClockSize
        val rightClockSize = if (sbClockSizeSwitch) sbClockSize else mRightClockSize
        val unit = if (sbClockSizeSwitch) TypedValue.COMPLEX_UNIT_SP else TypedValue.COMPLEX_UNIT_PX

        mClockView?.let {
            it.setTextSize(unit, leftClockSize.toFloat())

            if (sbClockSizeSwitch) {
                setClockGravity(it, Gravity.LEFT or Gravity.CENTER)
            }
        }

        mCenterClockView?.let {
            it.setTextSize(unit, centerClockSize.toFloat())

            if (sbClockSizeSwitch) {
                setClockGravity(it, Gravity.CENTER)
            }
        }

        mRightClockView?.let {
            it.setTextSize(unit, rightClockSize.toFloat())

            if (sbClockSizeSwitch) {
                setClockGravity(it, Gravity.RIGHT or Gravity.CENTER)
            }
        }
    }
}