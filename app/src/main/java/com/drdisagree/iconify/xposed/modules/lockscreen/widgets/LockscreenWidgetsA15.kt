package com.drdisagree.iconify.xposed.modules.lockscreen.widgets

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.drdisagree.iconify.common.Const.ACTION_LS_CLOCK_INFLATED
import com.drdisagree.iconify.common.Const.ACTION_WEATHER_INFLATED
import com.drdisagree.iconify.common.Const.DISABLE_DYNAMIC_CLOCK_COMMAND
import com.drdisagree.iconify.common.Const.ENABLE_DYNAMIC_CLOCK_COMMAND
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.ICONIFY_LOCKSCREEN_WIDGET_TAG
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
import com.drdisagree.iconify.xposed.HookEntry.Companion.enqueueProxyCommand
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.MyConstraintSet.Companion.applyTo
import com.drdisagree.iconify.xposed.modules.extras.utils.MyConstraintSet.Companion.clear
import com.drdisagree.iconify.xposed.modules.extras.utils.MyConstraintSet.Companion.clone
import com.drdisagree.iconify.xposed.modules.extras.utils.MyConstraintSet.Companion.connect
import com.drdisagree.iconify.xposed.modules.extras.utils.MyConstraintSet.Companion.constraintSetInstance
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.assignIdsToViews
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.getLsItemsContainer
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

class LockscreenWidgetsA15(context: Context) : ModPack(context) {

    // Parent
    private var mLockscreenRootView: ViewGroup? = null
    private var mLsItemsContainer: LinearLayout? = null

    // Widgets Container
    private lateinit var mWidgetsContainer: LinearLayout

    // Activity Starter
    private var mActivityStarter: Any? = null

    // Ls custom clock
    private var mLockscreenClockEnabled = false
    private var mLockscreenClockInflated = false

    // Ls weather
    private var mWeatherEnabled = false
    private var mWeatherInflated = false

    // Widgets Prefs
    // Lockscreen Widgets
    private var mWidgetsEnabled = false
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
    private var mMainWidgets = ""
    private var mExtraWidgets = ""
    private var mTopMargin = 0
    private var mBottomMargin = 0
    private var mWidgetsScale = 1.0f

    private var mBroadcastRegistered = false
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && intent.action != null && mWidgetsEnabled) {
                if (intent.action == ACTION_WEATHER_INFLATED) {
                    mWeatherInflated = true
                    placeWidgetsView()
                } else if (intent.action == ACTION_LS_CLOCK_INFLATED) {
                    mLockscreenClockInflated = true
                    if (!mWeatherEnabled) {
                        placeWidgetsView()
                    }
                }
            }
        }
    }

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized || !isComposeLockscreen) return

        Xprefs.apply {
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

            // Ls custom clock
            mLockscreenClockEnabled = getBoolean(LSCLOCK_SWITCH, false)

            // Ls weather
            mWeatherEnabled = getBoolean(WEATHER_SWITCH, false)
        }

        when (key.firstOrNull()) {
            LOCKSCREEN_WIDGETS_ENABLED -> {
                resetDynamicClock()
                updateLockscreenWidgets()
            }

            in setOf(
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "DiscouragedApi")
    override fun handleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (!isComposeLockscreen) return

        // Receiver to handle weather inflated
        if (!mBroadcastRegistered) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(ACTION_WEATHER_INFLATED)
            intentFilter.addAction(ACTION_LS_CLOCK_INFLATED)

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

        mWidgetsContainer = LinearLayout(mContext).apply {
            id = View.generateViewId()
            tag = ICONIFY_LOCKSCREEN_WIDGET_TAG
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val keyguardQuickAffordanceInteractorClass =
            findClass("$SYSTEMUI_PACKAGE.keyguard.domain.interactor.KeyguardQuickAffordanceInteractor")

        keyguardQuickAffordanceInteractorClass
            .hookConstructor()
            .runAfter { param ->
                try {
                    mActivityStarter = param.thisObject.getField("activityStarter")
                } catch (t: Throwable) {
                    log(this@LockscreenWidgetsA15, "Failed to get ActivityStarter")
                }
                setActivityStarter()
            }

        val keyguardClockSwitchClass = findClass("com.android.keyguard.KeyguardClockSwitch")

        keyguardClockSwitchClass
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

        val aodBurnInLayerClass =
            findClass("$SYSTEMUI_PACKAGE.keyguard.ui.view.layout.sections.AodBurnInLayer")

        aodBurnInLayerClass
            .hookConstructor()
            .runAfter { param ->
                if (!mWidgetsEnabled) return@runAfter

                val entryV = param.thisObject as View

                entryV.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!mWidgetsEnabled) return@postDelayed

                            val rootView = entryV.parent as ViewGroup
                            mLockscreenRootView = rootView

                            if (mLockscreenClockEnabled || mWeatherEnabled) {
                                mLsItemsContainer = rootView.getLsItemsContainer()

                                (mWidgetsContainer.parent as? ViewGroup)
                                    ?.removeView(mWidgetsContainer)

                                // Add widgets view at the end
                                mLsItemsContainer!!.addView(
                                    mWidgetsContainer,
                                    mLsItemsContainer!!.childCount - 1
                                )
                            } else {
                                mLockscreenRootView!!.addView(mWidgetsContainer)
                            }

                            applyLayoutConstraints(mLsItemsContainer ?: mWidgetsContainer)

                            placeWidgetsView()
                        }, 1000)
                    }

                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }

        val defaultNotificationStackScrollLayoutSectionClass =
            findClass("$SYSTEMUI_PACKAGE.keyguard.ui.view.layout.sections.DefaultNotificationStackScrollLayoutSection")

        val notificationContainerId = mContext.resources.getIdentifier(
            "nssl_placeholder",
            "id",
            mContext.packageName
        )

        defaultNotificationStackScrollLayoutSectionClass
            .hookMethod("applyConstraints")
            .runAfter { param ->
                if (!mWidgetsEnabled) return@runAfter

                val constraintSet = param.args[0]

                constraintSet.clear(
                    notificationContainerId,
                    ConstraintSet.TOP
                )
                constraintSet.connect(
                    notificationContainerId,
                    ConstraintSet.TOP,
                    (mLsItemsContainer ?: mWidgetsContainer).id,
                    ConstraintSet.BOTTOM
                )
            }

        val smartspaceSectionClass =
            findClass("$SYSTEMUI_PACKAGE.keyguard.ui.view.layout.sections.SmartspaceSection")

        smartspaceSectionClass
            .hookMethod("applyConstraints")
            .runAfter { param ->
                if (!mWidgetsEnabled) return@runAfter

                val constraintSet = param.args[0]

                val dateSmartSpaceViewId = mContext.resources.getIdentifier(
                    "date_smartspace_view",
                    "id",
                    mContext.packageName
                )

                // Connect widget view to bottom of date smartspace
                if (!mLockscreenClockEnabled && mWeatherEnabled && mLsItemsContainer != null) {
                    constraintSet.clear(
                        mLsItemsContainer!!.id,
                        ConstraintSet.TOP
                    )
                    constraintSet.connect(
                        mLsItemsContainer!!.id,
                        ConstraintSet.TOP,
                        dateSmartSpaceViewId,
                        ConstraintSet.BOTTOM
                    )
                } else if (mLockscreenClockEnabled && mLsItemsContainer != null) {
                    constraintSet.clear(
                        mLsItemsContainer!!.id,
                        ConstraintSet.TOP
                    )
                    constraintSet.connect(
                        mLsItemsContainer!!.id,
                        ConstraintSet.TOP,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.TOP
                    )
                } else if (!mLockscreenClockEnabled && !mWeatherEnabled) {
                    constraintSet.clear(
                        mWidgetsContainer.id,
                        ConstraintSet.TOP
                    )
                    constraintSet.connect(
                        mWidgetsContainer.id,
                        ConstraintSet.TOP,
                        dateSmartSpaceViewId,
                        ConstraintSet.BOTTOM
                    )
                }
            }
    }

    private fun placeWidgetsView() {
        if (!isComposeLockscreen) return
        if (!mWidgetsEnabled || mLockscreenRootView == null) return
        if (mLockscreenClockEnabled && !mLockscreenClockInflated) return
        if (mWeatherEnabled && !mWeatherInflated) return

        try {
            val lsWidgets = LockscreenWidgetsView.getInstance(mContext, mActivityStarter)
            (lsWidgets.parent as? ViewGroup)?.removeView(lsWidgets)
            mWidgetsContainer.addView(lsWidgets)

            updateLockscreenWidgets()
            updateLsDeviceWidget()
            updateLockscreenWidgetsColors()
            updateMargins()
            updateLockscreenWidgetsScale()
            applyLayoutConstraints(mLsItemsContainer ?: mWidgetsContainer)
        } catch (ignored: Throwable) {
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun applyLayoutConstraints(widgetView: ViewGroup) {
        mLockscreenRootView.assignIdsToViews()

        widgetView.getChildAt(0)?.layoutParams?.width = LinearLayout.LayoutParams.MATCH_PARENT

        val notificationContainerId = mContext.resources.getIdentifier(
            "nssl_placeholder",
            "id",
            mContext.packageName
        )
        val aodNotificationIconContainerId = mContext.resources.getIdentifier(
            "aod_notification_icon_container",
            "id",
            mContext.packageName
        )

        constraintSetInstance?.also { constraintSet ->
            constraintSet.clone(mLockscreenRootView!!)

            // Connect widget view to parent
            constraintSet.connect(
                widgetView.id,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            constraintSet.connect(
                widgetView.id,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )

            if ((widgetView == mWidgetsContainer && !mLockscreenClockEnabled && !mWeatherEnabled) ||
                (widgetView == mLsItemsContainer && !mLockscreenClockEnabled && mWeatherEnabled)
            ) {
                val dateSmartspaceViewId = mContext.resources.getIdentifier(
                    "date_smartspace_view",
                    "id",
                    mContext.packageName
                )
                // If no custom clock or widgets enabled, or only widgets enabled
                // then connect widget view to bottom of date smartspace
                constraintSet.connect(
                    widgetView.id,
                    ConstraintSet.TOP,
                    dateSmartspaceViewId,
                    ConstraintSet.BOTTOM
                )
            } else if (widgetView == mLsItemsContainer && mLockscreenClockEnabled) {
                // If custom clock enabled, then connect whole container to top of parent
                constraintSet.connect(
                    widgetView.id,
                    ConstraintSet.TOP,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.TOP
                )
            }

            // Connect notification container below widget
            if (notificationContainerId != 0) {
                constraintSet.clear(
                    notificationContainerId,
                    ConstraintSet.TOP
                )
                constraintSet.connect(
                    notificationContainerId,
                    ConstraintSet.TOP,
                    widgetView.id,
                    ConstraintSet.BOTTOM
                )
            }

            // Connect aod notification icon container below widget
            if (aodNotificationIconContainerId != 0) {
                constraintSet.clear(
                    aodNotificationIconContainerId,
                    ConstraintSet.TOP
                )
                constraintSet.connect(
                    aodNotificationIconContainerId,
                    ConstraintSet.TOP,
                    widgetView.id,
                    ConstraintSet.BOTTOM
                )
            }

            constraintSet.applyTo(mLockscreenRootView!!)
        }
    }

    private fun updateLockscreenWidgets() {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setOptions(mWidgetsEnabled, mDeviceWidgetEnabled, mMainWidgets, mExtraWidgets)
    }

    private fun updateLockscreenWidgetsOnClock(isLargeClock: Boolean) {
        val lsWidgets = LockscreenWidgetsView.getInstance() ?: return
        lsWidgets.setIsLargeClock(if (mLockscreenClockEnabled) false else isLargeClock)
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
            mBigInactiveColor,
            mBigActiveColor,
            mSmallInactiveColor,
            mSmallActiveColor,
            mBigIconInactiveColor,
            mBigIconActiveColor,
            mSmallIconInactiveColor,
            mSmallIconActiveColor
        )
    }

    private fun updateMargins() {
        val childView = mWidgetsContainer.getChildAt(0) as? LinearLayout

        mWidgetsContainer.layoutParams.width = if (mLsItemsContainer == null) {
            0
        } else {
            LinearLayout.LayoutParams.MATCH_PARENT
        }

        mWidgetsContainer.gravity = Gravity.CENTER_HORIZONTAL
        childView?.gravity = Gravity.CENTER_HORIZONTAL

        if (childView != null) {
            setMargins(
                childView,
                mContext,
                0,
                mTopMargin,
                0,
                mBottomMargin
            )
        }

        applyLayoutConstraints(mLsItemsContainer ?: mWidgetsContainer)
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

    private fun resetDynamicClock() {
        enqueueProxyCommand { proxy ->
            proxy.runCommand(
                if (mWidgetsEnabled) DISABLE_DYNAMIC_CLOCK_COMMAND else ENABLE_DYNAMIC_CLOCK_COMMAND
            )
        }
    }
}