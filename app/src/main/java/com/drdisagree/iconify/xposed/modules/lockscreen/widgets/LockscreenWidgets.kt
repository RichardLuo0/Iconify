package com.drdisagree.iconify.xposed.modules.lockscreen.widgets

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.view.ViewGroup
import android.widget.LinearLayout
import com.drdisagree.iconify.common.Const.ACTION_WEATHER_INFLATED
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BIG_ACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BIG_ICON_ACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BIG_ICON_INACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BIG_INACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_BOTTOM_MARGIN
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_CUSTOM_COLOR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CIRCULAR_COLOR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CUSTOM_COLOR_SWITCH
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_DEVICE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_LINEAR_COLOR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_DEVICE_WIDGET_TEXT_COLOR
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_ENABLED
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_EXTRAS
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SCALE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SMALL_ACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SMALL_ICON_ACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SMALL_ICON_INACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_SMALL_INACTIVE
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_WIDGETS_TOP_MARGIN
import com.drdisagree.iconify.common.Preferences.LSCLOCK_SWITCH
import com.drdisagree.iconify.common.Preferences.WEATHER_SWITCH
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.setMargins
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.modules.extras.views.LockscreenWidgetsView
import com.drdisagree.iconify.xposed.modules.lockscreen.Lockscreen.Companion.isComposeLockscreen
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.callbacks.XC_LoadPackage

class LockscreenWidgets(context: Context) : ModPack(context) {

    // Parent
    private var mStatusViewContainer: ViewGroup? = null
    private var mStatusArea: ViewGroup? = null

    // Widgets Container
    private lateinit var mWidgetsContainer: LinearLayout

    // Activity Starter
    private var mActivityStarter: Any? = null

    // Ls custom clock
    private var customLockscreenClock = false

    // Ls weather
    private var lsWeather = false
    private var lsWeatherInflated = false

    // Widgets Prefs
    // Lockscreen Widgets
    private var mWidgetsEnabled: Boolean = false
    private var mDeviceWidgetEnabled = false
    private var mDeviceCustomColor = false
    private var mDeviceLinearColor = Color.WHITE
    private var mDeviceCircularColor = Color.WHITE
    private var mDeviceTextColor = Color.WHITE
    private var mWidgetsCustomColor = false
    private var mBigInactiveColor = Color.BLACK
    private var mBigActiveColor = Color.WHITE
    private var mSmallInactiveColor = Color.BLACK
    private var mSmallActiveColor = Color.WHITE
    private var mBigIconActiveColor = Color.WHITE
    private var mBigIconInactiveColor = Color.BLACK
    private var mSmallIconActiveColor = Color.WHITE
    private var mSmallIconInactiveColor = Color.BLACK
    private var mDeviceName = ""
    private var mMainWidgets: String = ""
    private var mExtraWidgets: String = ""
    private var mTopMargin = 0
    private var mBottomMargin = 0
    private var mWidgetsScale = 1.0f

    private var mBroadcastRegistered = false
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && intent.action != null) {
                if (intent.action == ACTION_WEATHER_INFLATED && mWidgetsEnabled) {
                    lsWeatherInflated = true
                    placeWidgets()
                }
            }
        }
    }

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized || isComposeLockscreen) return

        Xprefs.apply {
            // Ls custom clock
            customLockscreenClock = getBoolean(LSCLOCK_SWITCH, false)

            // Ls weather
            lsWeather = getBoolean(WEATHER_SWITCH, false)

            // Widgets
            mWidgetsEnabled = getBoolean(LOCKSCREEN_WIDGETS_ENABLED, false)
            mDeviceWidgetEnabled = getBoolean(LOCKSCREEN_WIDGETS_DEVICE_WIDGET, false)
            mMainWidgets = getString(LOCKSCREEN_WIDGETS, "")!!
            mExtraWidgets = getString(LOCKSCREEN_WIDGETS_EXTRAS, "")!!
            mDeviceCustomColor =
                getBoolean(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CUSTOM_COLOR_SWITCH, false)
            mDeviceLinearColor = getInt(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_LINEAR_COLOR, Color.WHITE)
            mDeviceCircularColor =
                getInt(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CIRCULAR_COLOR, Color.WHITE)
            mDeviceTextColor = getInt(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_TEXT_COLOR, Color.WHITE)
            mDeviceName = getString(LOCKSCREEN_WIDGETS_DEVICE_WIDGET_DEVICE, "")!!
            mWidgetsCustomColor = getBoolean(LOCKSCREEN_WIDGETS_CUSTOM_COLOR, false)
            mBigInactiveColor = getInt(LOCKSCREEN_WIDGETS_BIG_INACTIVE, Color.BLACK)
            mBigActiveColor = getInt(LOCKSCREEN_WIDGETS_BIG_ACTIVE, Color.WHITE)
            mSmallInactiveColor = getInt(LOCKSCREEN_WIDGETS_SMALL_INACTIVE, Color.BLACK)
            mSmallActiveColor = getInt(LOCKSCREEN_WIDGETS_SMALL_ACTIVE, Color.WHITE)
            mBigIconActiveColor = getInt(LOCKSCREEN_WIDGETS_BIG_ICON_ACTIVE, Color.BLACK)
            mBigIconInactiveColor = getInt(LOCKSCREEN_WIDGETS_BIG_ICON_INACTIVE, Color.WHITE)
            mSmallIconActiveColor = getInt(LOCKSCREEN_WIDGETS_SMALL_ICON_ACTIVE, Color.BLACK)
            mSmallIconInactiveColor = getInt(LOCKSCREEN_WIDGETS_SMALL_ICON_INACTIVE, Color.WHITE)
            mTopMargin = getSliderInt(LOCKSCREEN_WIDGETS_TOP_MARGIN, 0)
            mBottomMargin = getSliderInt(LOCKSCREEN_WIDGETS_BOTTOM_MARGIN, 0)
            mWidgetsScale = getSliderFloat(LOCKSCREEN_WIDGETS_SCALE, 1.0f)
        }

        when (key.firstOrNull()) {
            in setOf(
                LOCKSCREEN_WIDGETS_ENABLED,
                LOCKSCREEN_WIDGETS_DEVICE_WIDGET,
                LOCKSCREEN_WIDGETS,
                LOCKSCREEN_WIDGETS_EXTRAS
            ) -> updateLockscreenWidgets()

            in setOf(
                LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CUSTOM_COLOR_SWITCH,
                LOCKSCREEN_WIDGETS_DEVICE_WIDGET_LINEAR_COLOR,
                LOCKSCREEN_WIDGETS_DEVICE_WIDGET_CIRCULAR_COLOR,
                LOCKSCREEN_WIDGETS_DEVICE_WIDGET_TEXT_COLOR,
                LOCKSCREEN_WIDGETS_DEVICE_WIDGET_DEVICE
            ) -> updateLsDeviceWidget()

            in setOf(
                LOCKSCREEN_WIDGETS_CUSTOM_COLOR,
                LOCKSCREEN_WIDGETS_BIG_ACTIVE,
                LOCKSCREEN_WIDGETS_BIG_INACTIVE,
                LOCKSCREEN_WIDGETS_SMALL_ACTIVE,
                LOCKSCREEN_WIDGETS_SMALL_INACTIVE,
                LOCKSCREEN_WIDGETS_BIG_ICON_ACTIVE,
                LOCKSCREEN_WIDGETS_BIG_ICON_INACTIVE,
                LOCKSCREEN_WIDGETS_SMALL_ICON_ACTIVE,
                LOCKSCREEN_WIDGETS_SMALL_ICON_INACTIVE
            ) -> updateLockscreenWidgetsColors()

            in setOf(
                LOCKSCREEN_WIDGETS_TOP_MARGIN,
                LOCKSCREEN_WIDGETS_BOTTOM_MARGIN
            ) -> updateMargins()

            LOCKSCREEN_WIDGETS_SCALE -> updateLockscreenWidgetsScale()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun handleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (isComposeLockscreen) return

        // Receiver to handle weather inflated
        if (!mBroadcastRegistered) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(ACTION_WEATHER_INFLATED)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mContext.registerReceiver(
                    mReceiver,
                    intentFilter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                mContext.registerReceiver(
                    mReceiver,
                    intentFilter
                )
            }

            mBroadcastRegistered = true
        }

        mWidgetsContainer = LinearLayout(mContext)
        mWidgetsContainer.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        LaunchableImageView = findClass("com.android.systemui.animation.view.LaunchableImageView")

        LaunchableLinearLayout =
            findClass("com.android.systemui.animation.view.LaunchableLinearLayout")

        val keyguardQuickAffordanceInteractor =
            findClass("com.android.systemui.keyguard.domain.interactor.KeyguardQuickAffordanceInteractor")

        keyguardQuickAffordanceInteractor
            .hookConstructor()
            .runAfter { param ->
                try {
                    mActivityStarter = param.thisObject.getField("activityStarter")
                } catch (t: Throwable) {
                    log(this@LockscreenWidgets, "Failed to get ActivityStarter")
                }
                setActivityStarter()
            }

        val keyguardStatusViewClass = findClass("com.android.keyguard.KeyguardStatusView")

        keyguardStatusViewClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                if (!mWidgetsEnabled) return@runAfter

                try {
                    mStatusViewContainer = param.thisObject.getField(
                        "mStatusViewContainer"
                    ) as ViewGroup
                } catch (t: Throwable) {
                    log(this@LockscreenWidgets, "Failed to get mStatusViewContainer")
                }

                placeWidgets()
            }

        val keyguardClockSwitch = findClass("com.android.keyguard.KeyguardClockSwitch")

        keyguardClockSwitch
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                if (!mWidgetsEnabled) return@runAfter

                try {
                    mStatusArea = param.thisObject.getField("mStatusArea") as ViewGroup
                } catch (t: Throwable) {
                    log(this@LockscreenWidgets, "Failed to get mStatusArea")
                }

                placeWidgets()
            }

        keyguardClockSwitch
            .hookMethod("updateClockViews")
            .runAfter { param ->
                if (!mWidgetsEnabled) return@runAfter

                updateLockscreenWidgetsOnClock(param.args[0] as Boolean)
            }

        val dozeScrimControllerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.DozeScrimController")

        dozeScrimControllerClass
            .hookMethod("onDozingChanged")
            .runAfter { param -> updateDozingState(param.args[0] as Boolean) }
    }

    private fun placeWidgets() {
        if (!mWidgetsEnabled || isComposeLockscreen) return
        if (mStatusViewContainer == null || mStatusArea == null) return
        if (lsWeather && !lsWeatherInflated) return
        try {
            val lsWidgets = LockscreenWidgetsView.getInstance(mContext, mActivityStarter)
            (lsWidgets.parent as ViewGroup?)?.removeView(lsWidgets)
            (mWidgetsContainer.parent as ViewGroup?)?.removeView(mWidgetsContainer)

            mWidgetsContainer.addView(lsWidgets)

            if (customLockscreenClock) {
                mStatusViewContainer?.addView(mWidgetsContainer)
            } else {
                // Put widgets view inside the status area
                // But before notifications
                mStatusArea?.addView(mWidgetsContainer, mStatusArea!!.childCount - 1)
            }
            updateLockscreenWidgets()
            updateLsDeviceWidget()
            updateLockscreenWidgetsColors()
            updateMargins()
            updateLockscreenWidgetsScale()
        } catch (ignored: Throwable) {
        }
    }

    private fun updateLockscreenWidgets() {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setOptions(mWidgetsEnabled, mDeviceWidgetEnabled, mMainWidgets, mExtraWidgets)
    }

    private fun updateLockscreenWidgetsOnClock(isLargeClock: Boolean) {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setIsLargeClock(if (customLockscreenClock) false else isLargeClock)
    }

    private fun updateLsDeviceWidget() {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setDeviceWidgetOptions(
            mDeviceCustomColor,
            mDeviceLinearColor,
            mDeviceCircularColor,
            mDeviceTextColor,
            mDeviceName
        )
    }

    private fun updateLockscreenWidgetsColors() {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setCustomColors(
            mWidgetsCustomColor,
            mBigInactiveColor, mBigActiveColor,
            mSmallInactiveColor, mSmallActiveColor,
            mBigIconInactiveColor, mBigIconActiveColor,
            mSmallIconInactiveColor, mSmallIconActiveColor
        )
    }

    private fun updateMargins() {
        setMargins(
            mWidgetsContainer,
            mContext,
            0,
            mTopMargin,
            0,
            mBottomMargin
        )
    }

    private fun updateLockscreenWidgetsScale() {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setScale(mWidgetsScale)
    }

    private fun updateDozingState(isDozing: Boolean) {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setDozingState(isDozing)
    }

    private fun setActivityStarter() {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        if (mActivityStarter != null) lsWidgets.setActivityStarter(mActivityStarter)
    }

    companion object {
        var LaunchableImageView: Class<*>? = null
        var LaunchableLinearLayout: Class<*>? = null
    }
}