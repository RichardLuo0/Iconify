package com.drdisagree.iconify.xposed.modules

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.XResources.DimensionReplacement
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.drdisagree.iconify.R
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_CIRCLE
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_CUSTOM_LANDSCAPE
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_CUSTOM_RLANDSCAPE
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_DEFAULT
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_DEFAULT_LANDSCAPE
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_DEFAULT_RLANDSCAPE
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_DOTTED_CIRCLE
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_FILLED_CIRCLE
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYA
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYB
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYC
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYD
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYE
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYF
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYG
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYH
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYI
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYJ
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYK
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYL
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYM
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYN
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_BATTERYO
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_COLOROS
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_IOS_15
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_IOS_16
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_KIM
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_MIUI_PILL
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_SMILEY
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_STYLE_A
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_LANDSCAPE_STYLE_B
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_PORTRAIT_AIROO
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_PORTRAIT_CAPSULE
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_PORTRAIT_LORN
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_PORTRAIT_MX
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_PORTRAIT_ORIGAMI
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_RLANDSCAPE_COLOROS
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_RLANDSCAPE_STYLE_A
import com.drdisagree.iconify.common.Preferences.BATTERY_STYLE_RLANDSCAPE_STYLE_B
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_BLEND_COLOR
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_CHARGING_COLOR
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_CHARGING_ICON_MARGIN_LEFT
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_CHARGING_ICON_MARGIN_RIGHT
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_CHARGING_ICON_STYLE
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_CHARGING_ICON_SWITCH
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_CHARGING_ICON_WIDTH_HEIGHT
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_DIMENSION
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_FILL_ALPHA
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_FILL_COLOR
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_FILL_GRAD_COLOR
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_HEIGHT
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_HIDE_BATTERY
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_HIDE_PERCENTAGE
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_INSIDE_PERCENTAGE
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_LAYOUT_REVERSE
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_MARGIN_BOTTOM
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_MARGIN_LEFT
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_MARGIN_RIGHT
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_MARGIN_TOP
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_PERIMETER_ALPHA
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_POWERSAVE_FILL_COLOR
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_POWERSAVE_INDICATOR_COLOR
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_RAINBOW_FILL_COLOR
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_STYLE
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_SWAP_PERCENTAGE
import com.drdisagree.iconify.common.Preferences.CUSTOM_BATTERY_WIDTH
import com.drdisagree.iconify.common.Preferences.ICONIFY_CHARGING_ICON_TAG
import com.drdisagree.iconify.xposed.HookRes.Companion.modRes
import com.drdisagree.iconify.xposed.HookRes.Companion.resParams
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.batterystyles.BatteryDrawable
import com.drdisagree.iconify.xposed.modules.batterystyles.CircleBattery
import com.drdisagree.iconify.xposed.modules.batterystyles.CircleFilledBattery
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBattery
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryA
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryB
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryC
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryColorOS
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryD
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryE
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryF
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryG
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryH
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryI
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryJ
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryK
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryKim
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryL
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryM
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryMIUIPill
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryN
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryO
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatterySmiley
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryStyleA
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryStyleB
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryiOS15
import com.drdisagree.iconify.xposed.modules.batterystyles.LandscapeBatteryiOS16
import com.drdisagree.iconify.xposed.modules.batterystyles.PortraitBatteryAiroo
import com.drdisagree.iconify.xposed.modules.batterystyles.PortraitBatteryCapsule
import com.drdisagree.iconify.xposed.modules.batterystyles.PortraitBatteryLorn
import com.drdisagree.iconify.xposed.modules.batterystyles.PortraitBatteryMx
import com.drdisagree.iconify.xposed.modules.batterystyles.PortraitBatteryOrigami
import com.drdisagree.iconify.xposed.modules.batterystyles.RLandscapeBattery
import com.drdisagree.iconify.xposed.modules.batterystyles.RLandscapeBatteryColorOS
import com.drdisagree.iconify.xposed.modules.batterystyles.RLandscapeBatteryStyleA
import com.drdisagree.iconify.xposed.modules.batterystyles.RLandscapeBatteryStyleB
import com.drdisagree.iconify.xposed.modules.utils.SettingsLibUtils
import com.drdisagree.iconify.xposed.modules.utils.ViewHelper.toPx
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
import de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField
import de.robv.android.xposed.XposedHelpers.getBooleanField
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.XposedHelpers.setStaticIntField
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@Suppress("unused")
@SuppressLint("DiscouragedApi")
class BatteryStyleManager(context: Context) : ModPack(context) {

    private var defaultLandscapeBatteryEnabled = false
    private var frameColor = Color.WHITE
    private var batteryController: Any? = null
    private var batteryMeterViewParam: MethodHookParam? = null
    private var mBatteryLayoutReverse = false
    private var mScaledPerimeterAlpha = false
    private var mScaledFillAlpha = false
    private var mRainbowFillColor = false
    private var mCustomBlendColor = false
    private var mCustomChargingColor = Color.BLACK
    private var mCustomFillColor = Color.BLACK
    private var mCustomFillGradColor = Color.BLACK
    private var mCustomPowerSaveColor = Color.BLACK
    private var mCustomPowerSaveFillColor = Color.BLACK
    private var mSwapPercentage = false
    private val mChargingIconView: ImageView? = null
    private var mChargingIconSwitch = false
    private var mChargingIconStyle = 0
    private var mChargingIconML = 1
    private var mChargingIconMR = 0
    private var mChargingIconWH = 14
    private var mIsChargingImpl = false
    private var mIsCharging = false

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        var batteryStyle: Int

        Xprefs.apply {
            batteryStyle = getString(CUSTOM_BATTERY_STYLE, "0")!!.toInt()
            val hidePercentage: Boolean = getBoolean(CUSTOM_BATTERY_HIDE_PERCENTAGE, false)
            val defaultInsidePercentage = batteryStyle == BATTERY_STYLE_LANDSCAPE_IOS_16 ||
                    batteryStyle == BATTERY_STYLE_LANDSCAPE_BATTERYL ||
                    batteryStyle == BATTERY_STYLE_LANDSCAPE_BATTERYM
            val insidePercentage = defaultInsidePercentage ||
                    getBoolean(CUSTOM_BATTERY_INSIDE_PERCENTAGE, false)
            defaultLandscapeBatteryEnabled = batteryStyle == BATTERY_STYLE_DEFAULT_LANDSCAPE ||
                    batteryStyle == BATTERY_STYLE_DEFAULT_RLANDSCAPE
            customBatteryEnabled = batteryStyle != BATTERY_STYLE_DEFAULT &&
                    batteryStyle != BATTERY_STYLE_DEFAULT_LANDSCAPE &&
                    batteryStyle != BATTERY_STYLE_DEFAULT_RLANDSCAPE

            mBatteryRotation = if (defaultLandscapeBatteryEnabled) {
                if (batteryStyle == BATTERY_STYLE_DEFAULT_RLANDSCAPE) {
                    90
                } else {
                    270
                }
            } else {
                0
            }

            mHidePercentage = hidePercentage || insidePercentage
            mShowPercentInside = insidePercentage && (defaultInsidePercentage || !hidePercentage)
            mHideBattery = getBoolean(CUSTOM_BATTERY_HIDE_BATTERY, false)
            mBatteryLayoutReverse = getBoolean(CUSTOM_BATTERY_LAYOUT_REVERSE, false)
            mBatteryCustomDimension = getBoolean(CUSTOM_BATTERY_DIMENSION, false)
            mBatteryScaleWidth = getSliderInt(CUSTOM_BATTERY_WIDTH, 20)
            mBatteryScaleHeight = getSliderInt(CUSTOM_BATTERY_HEIGHT, 20)
            mScaledPerimeterAlpha = getBoolean(CUSTOM_BATTERY_PERIMETER_ALPHA, false)
            mScaledFillAlpha = getBoolean(CUSTOM_BATTERY_FILL_ALPHA, false)
            mRainbowFillColor = getBoolean(CUSTOM_BATTERY_RAINBOW_FILL_COLOR, false)
            mCustomBlendColor = getBoolean(CUSTOM_BATTERY_BLEND_COLOR, false)
            mCustomChargingColor = getInt(CUSTOM_BATTERY_CHARGING_COLOR, Color.BLACK)
            mCustomFillColor = getInt(CUSTOM_BATTERY_FILL_COLOR, Color.BLACK)
            mCustomFillGradColor = getInt(CUSTOM_BATTERY_FILL_GRAD_COLOR, Color.BLACK)
            mCustomPowerSaveColor = getInt(CUSTOM_BATTERY_POWERSAVE_INDICATOR_COLOR, Color.BLACK)
            mCustomPowerSaveFillColor = getInt(CUSTOM_BATTERY_POWERSAVE_FILL_COLOR, Color.BLACK)
            mSwapPercentage = getBoolean(CUSTOM_BATTERY_SWAP_PERCENTAGE, false)
            mChargingIconSwitch = getBoolean(CUSTOM_BATTERY_CHARGING_ICON_SWITCH, false)
            mChargingIconStyle = getString(CUSTOM_BATTERY_CHARGING_ICON_STYLE, "0")!!.toInt()
            mChargingIconML = getSliderInt(CUSTOM_BATTERY_CHARGING_ICON_MARGIN_LEFT, 1)
            mChargingIconMR = getSliderInt(CUSTOM_BATTERY_CHARGING_ICON_MARGIN_RIGHT, 0)
            mChargingIconWH = getSliderInt(CUSTOM_BATTERY_CHARGING_ICON_WIDTH_HEIGHT, 14)
            mBatteryMarginLeft = mContext.toPx(getSliderInt(CUSTOM_BATTERY_MARGIN_LEFT, 4))
            mBatteryMarginTop = mContext.toPx(getSliderInt(CUSTOM_BATTERY_MARGIN_TOP, 0))
            mBatteryMarginRight = mContext.toPx(getSliderInt(CUSTOM_BATTERY_MARGIN_RIGHT, 4))
            mBatteryMarginBottom = mContext.toPx(getSliderInt(CUSTOM_BATTERY_MARGIN_BOTTOM, 0))
        }

        if (mBatteryStyle != batteryStyle) {
            mBatteryStyle = batteryStyle

            for (view in batteryViews) {
                val mBatteryIconView = getObjectField(view, "mBatteryIconView") as ImageView?
                mBatteryIconView?.let {
                    updateBatteryRotation(it)
                    updateFlipper(it.parent)
                }

                val mBatteryPercentView = getObjectField(view, "mBatteryPercentView") as TextView?
                mBatteryPercentView?.visibility = if (mHidePercentage) View.GONE else View.VISIBLE

                val mCharging = isBatteryCharging(view)
                val mLevel = getObjectField(view, "mLevel") as Int

                if (customBatteryEnabled) {
                    val mBatteryDrawable = getNewBatteryDrawable(mContext)

                    if (mBatteryDrawable != null) {
                        if (mBatteryIconView != null) {
                            mBatteryIconView.setImageDrawable(mBatteryDrawable)
                            mBatteryIconView.setVisibility(if (mHideBattery) View.GONE else View.VISIBLE)
                        }

                        setAdditionalInstanceField(
                            view,
                            "mBatteryDrawable",
                            mBatteryDrawable
                        )

                        mBatteryDrawable.setBatteryLevel(mLevel)
                        mBatteryDrawable.setChargingEnabled(mCharging)

                        updateCustomizeBatteryDrawable(mBatteryDrawable)
                    }
                }
            }
        }

        refreshBatteryIcons()

        if (key.isNotEmpty()) {
            if (key[0] == CUSTOM_BATTERY_WIDTH ||
                key[0] == CUSTOM_BATTERY_HEIGHT
            ) {
                setDefaultBatteryDimens()
            }

            if (key[0] == CUSTOM_BATTERY_STYLE ||
                key[0] == CUSTOM_BATTERY_HIDE_PERCENTAGE ||
                key[0] == CUSTOM_BATTERY_LAYOUT_REVERSE ||
                key[0] == CUSTOM_BATTERY_DIMENSION ||
                key[0] == CUSTOM_BATTERY_WIDTH ||
                key[0] == CUSTOM_BATTERY_HEIGHT ||
                key[0] == CUSTOM_BATTERY_PERIMETER_ALPHA ||
                key[0] == CUSTOM_BATTERY_FILL_ALPHA ||
                key[0] == CUSTOM_BATTERY_RAINBOW_FILL_COLOR ||
                key[0] == CUSTOM_BATTERY_BLEND_COLOR ||
                key[0] == CUSTOM_BATTERY_CHARGING_COLOR ||
                key[0] == CUSTOM_BATTERY_FILL_COLOR ||
                key[0] == CUSTOM_BATTERY_FILL_GRAD_COLOR ||
                key[0] == CUSTOM_BATTERY_POWERSAVE_INDICATOR_COLOR ||
                key[0] == CUSTOM_BATTERY_POWERSAVE_FILL_COLOR ||
                key[0] == CUSTOM_BATTERY_SWAP_PERCENTAGE ||
                key[0] == CUSTOM_BATTERY_CHARGING_ICON_SWITCH ||
                key[0] == CUSTOM_BATTERY_CHARGING_ICON_STYLE ||
                key[0] == CUSTOM_BATTERY_CHARGING_ICON_MARGIN_LEFT ||
                key[0] == CUSTOM_BATTERY_CHARGING_ICON_MARGIN_RIGHT ||
                key[0] == CUSTOM_BATTERY_CHARGING_ICON_WIDTH_HEIGHT ||
                key[0] == CUSTOM_BATTERY_MARGIN_LEFT ||
                key[0] == CUSTOM_BATTERY_MARGIN_TOP ||
                key[0] == CUSTOM_BATTERY_MARGIN_RIGHT ||
                key[0] == CUSTOM_BATTERY_MARGIN_BOTTOM
            ) {
                if (batteryMeterViewParam != null) {
                    updateSettings(batteryMeterViewParam!!)
                }
            }
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val batteryControllerImplClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.policy.BatteryControllerImpl")
        val batteryMeterViewClass = findClass(
            "$SYSTEMUI_PACKAGE.battery.BatteryMeterView",
            "$SYSTEMUI_PACKAGE.BatteryMeterView"
        )

        batteryControllerImplClass
            .hookConstructor()
            .runAfter { param ->
                batteryController = param.thisObject
            }

        batteryControllerImplClass
            .hookMethod("fireBatteryUnknownStateChanged")
            .suppressError()
            .runAfter {
                if (!customBatteryEnabled) return@runAfter

                for (view in batteryViews) {
                    val mBatteryDrawable = getAdditionalInstanceField(
                        view,
                        "mBatteryDrawable"
                    ) as BatteryDrawable

                    callMethod(view, "setImageDrawable", mBatteryDrawable)
                }
            }

        val batteryDataRefreshHook: XC_MethodHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val mLevel = getIntField(param.thisObject, "mLevel")
                mIsChargingImpl = (getBooleanField(param.thisObject, "mPluggedIn")
                        || getBooleanField(param.thisObject, "mCharging")
                        || getBooleanField(param.thisObject, "mWirelessCharging"))
                val mPowerSave = getBooleanField(param.thisObject, "mPowerSave")

                if (!customBatteryEnabled) return

                refreshBatteryData(mLevel, mIsChargingImpl, mPowerSave)
                // refreshing twice to avoid a bug where the battery icon updates incorrectly
                refreshBatteryData(mLevel, mIsChargingImpl, mPowerSave)
            }
        }

        batteryControllerImplClass
            .hookMethodMatchPattern(".*fireBatteryLevelChanged.*")
            .run(batteryDataRefreshHook)

        batteryControllerImplClass
            .hookMethodMatchPattern(".*firePowerSaveChanged.*")
            .run(batteryDataRefreshHook)

        batteryControllerImplClass
            .hookMethod("onReceive")
            .run(batteryDataRefreshHook)

        val listener: OnAttachStateChangeListener = object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                batteryViews.add(v)

                Thread {
                    try {
                        if (batteryController != null) {
                            Thread.sleep(500)
                            callMethod(
                                batteryController,
                                "fireBatteryLevelChanged"
                            )
                        }
                    } catch (ignored: Throwable) {
                    }
                }.start()
            }

            override fun onViewDetachedFromWindow(v: View) {
                batteryViews.remove(v)
            }
        }

        batteryMeterViewClass
            .hookConstructor()
            .parameters(
                Context::class.java,
                AttributeSet::class.java,
                Int::class.javaPrimitiveType
            )
            .runAfter { param ->
                if (batteryMeterViewParam == null) {
                    batteryMeterViewParam = param
                }

                val styleableBatteryMeterView = intArrayOf(
                    mContext.resources.getIdentifier(
                        "frameColor",
                        "attr",
                        mContext.packageName
                    ),
                    mContext.resources.getIdentifier(
                        "textAppearance",
                        "attr",
                        mContext.packageName
                    )
                )
                val attrs = mContext.obtainStyledAttributes(
                    param.args[1] as AttributeSet,
                    styleableBatteryMeterView,
                    param.args[2] as Int,
                    0
                )

                frameColor = attrs.getColor(
                    mContext.resources.getIdentifier(
                        "BatteryMeterView_frameColor",
                        "styleable",
                        mContext.packageName
                    ),
                    mContext.getColor(
                        mContext.resources.getIdentifier(
                            "meter_background_color",
                            "color",
                            mContext.packageName
                        )
                    )
                )
                attrs.recycle()

                (param.thisObject as View).addOnAttachStateChangeListener(listener)

                val mBatteryIconView = initBatteryIfNull(
                    param,
                    getObjectField(
                        param.thisObject,
                        "mBatteryIconView"
                    ) as ImageView?
                )

                if (customBatteryEnabled || mBatteryStyle == BATTERY_STYLE_DEFAULT_LANDSCAPE || mBatteryStyle == BATTERY_STYLE_DEFAULT_RLANDSCAPE) {
                    updateBatteryRotation(mBatteryIconView)
                    updateFlipper(mBatteryIconView.parent)
                }

                if (!customBatteryEnabled) return@runAfter

                val mBatteryDrawable = getNewBatteryDrawable(mContext)

                if (mBatteryDrawable != null) {
                    setAdditionalInstanceField(
                        param.thisObject,
                        "mBatteryDrawable",
                        mBatteryDrawable
                    )

                    mBatteryIconView.setImageDrawable(mBatteryDrawable)

                    setObjectField(
                        param.thisObject,
                        "mBatteryIconView",
                        mBatteryIconView
                    )

                    mBatteryIconView.setVisibility(if (mHideBattery) View.GONE else View.VISIBLE)
                }

                val mCharging = isBatteryCharging(param.thisObject)
                updateChargingIconView(param.thisObject, mCharging)
                updateSettings(param)

                if (batteryController != null) {
                    callMethod(batteryController, "fireBatteryLevelChanged")
                }
            }

        batteryMeterViewClass
            .hookMethod("updateColors")
            .parameters(
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            .runAfter { param ->
                if (batteryMeterViewParam == null) {
                    batteryMeterViewParam = param
                }

                if (!customBatteryEnabled) return@runAfter

                val mBatteryDrawable = getAdditionalInstanceField(
                    param.thisObject,
                    "mBatteryDrawable"
                ) as BatteryDrawable?

                mBatteryDrawable?.setColors(
                    param.args[0] as Int,
                    param.args[1] as Int,
                    param.args[2] as Int
                )

                val mChargingIconView =
                    (param.thisObject as ViewGroup).findViewWithTag<ImageView>(
                        ICONIFY_CHARGING_ICON_TAG
                    )
                mChargingIconView?.setImageTintList(ColorStateList.valueOf(param.args[2] as Int))
            }

        val shadeHeaderControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.shade.ShadeHeaderController",
            "$SYSTEMUI_PACKAGE.shade.LargeScreenShadeHeaderController"
        )

        shadeHeaderControllerClass
            .hookMethod("onInit")
            .runAfter { param ->
                try {
                    val configurationControllerListener = getObjectField(
                        param.thisObject,
                        "configurationControllerListener"
                    )

                    configurationControllerListener.javaClass
                        .hookMethod("onConfigChanged")
                        .runAfter {
                            if (customBatteryEnabled) {
                                updateBatteryResources(param)
                            }
                        }

                    if (customBatteryEnabled) {
                        updateBatteryResources(param)
                    }
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }

        if (customBatteryEnabled) {
            batteryMeterViewClass
                .hookMethod("scaleBatteryMeterViews")
                .replace { refreshBatteryIcons() }

            batteryMeterViewClass
                .hookMethod("scaleBatteryMeterViewsLegacy")
                .suppressError()
                .replace { refreshBatteryIcons() }
        }

        batteryMeterViewClass
            .hookMethod("onBatteryLevelChanged")
            .runAfter { param ->
                if (batteryMeterViewParam == null) {
                    batteryMeterViewParam = param
                }

                mIsCharging = param.args[1] as Boolean
            }

        batteryMeterViewClass
            .hookMethod("setPercentShowMode")
            .runBefore { param ->
                if (batteryMeterViewParam == null) {
                    batteryMeterViewParam = param
                }

                if ((customBatteryEnabled || defaultLandscapeBatteryEnabled) && (mHidePercentage || mShowPercentInside)) {
                    param.result = 2
                }
            }

        batteryMeterViewClass
            .hookMethod("updateShowPercent")
            .runAfter { param ->
                if (batteryMeterViewParam == null) {
                    batteryMeterViewParam = param
                }

                val mBatteryPercentView = getObjectField(
                    param.thisObject,
                    "mBatteryPercentView"
                ) as TextView?

                mBatteryPercentView?.visibility = if (mHidePercentage) View.GONE else View.VISIBLE
            }

        removeBatteryMeterViewMethods(batteryMeterViewClass)
        setDefaultBatteryDimens()
    }

    private fun refreshBatteryData(mLevel: Int, mIsCharging: Boolean, mPowerSave: Boolean) {
        for (view in batteryViews) {
            try {
                view.post {
                    val mBatteryDrawable = getAdditionalInstanceField(
                        view,
                        "mBatteryDrawable"
                    ) as BatteryDrawable?

                    mBatteryDrawable?.let {
                        it.setBatteryLevel(mLevel)
                        it.setChargingEnabled(mIsCharging)
                        it.setPowerSavingEnabled(mPowerSave)
                        updateCustomizeBatteryDrawable(it)
                    }

                    val mBatteryPercentView =
                        getObjectField(view, "mBatteryPercentView") as TextView?

                    mBatteryPercentView?.visibility =
                        if (mHidePercentage) View.GONE else View.VISIBLE

                    scaleBatteryMeterViews(view)
                    updateChargingIconView(view, mIsCharging)
                }
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun updateBatteryResources(param: MethodHookParam) {
        try {
            val header = getObjectField(param.thisObject, "header") as View
            val textColorPrimary = SettingsLibUtils.getColorAttrDefaultColor(
                header.context,
                android.R.attr.textColorPrimary
            )
            val textColorPrimaryInverse = SettingsLibUtils.getColorAttrDefaultColor(
                header.context,
                android.R.attr.textColorPrimaryInverse
            )
            val textColorSecondary = SettingsLibUtils.getColorAttrDefaultColor(
                header.context,
                android.R.attr.textColorSecondary
            )
            val batteryIcon = try {
                getObjectField(
                    param.thisObject,
                    "batteryIcon"
                ) as LinearLayout
            } catch (throwable: Throwable) {
                getObjectField(
                    param.thisObject,
                    "batteryIcon"
                ) as FrameLayout
            }

            if (getObjectField(param.thisObject, "iconManager") != null) {
                try {
                    callMethod(
                        getObjectField(param.thisObject, "iconManager"),
                        "setTint",
                        textColorPrimary
                    )
                } catch (ignored: Throwable) {
                    callMethod(
                        getObjectField(param.thisObject, "iconManager"),
                        "setTint",
                        textColorPrimary,
                        textColorPrimaryInverse
                    )
                }
            }

            callMethod(
                batteryIcon,
                "updateColors",
                textColorPrimary,
                textColorSecondary,
                textColorPrimary
            )

            scaleBatteryMeterViews(batteryIcon)
        } catch (ignored: NoSuchFieldError) {
        } catch (throwable: Throwable) {
            log(TAG + throwable)
        }
    }

    private fun refreshBatteryIcons() {
        for (view in batteryViews) {
            val mBatteryIconView =
                getObjectField(view, "mBatteryIconView") as ImageView?

            if (mBatteryIconView != null) {
                updateBatteryRotation(mBatteryIconView)
                updateFlipper(mBatteryIconView.parent)
            }

            val mBatteryPercentView =
                getObjectField(view, "mBatteryPercentView") as TextView?
            mBatteryPercentView?.visibility = if (mHidePercentage) View.GONE else View.VISIBLE

            if (customBatteryEnabled) {
                scaleBatteryMeterViews(mBatteryIconView)

                mBatteryIconView?.setVisibility(if (mHideBattery) View.GONE else View.VISIBLE)

                try {
                    val mBatteryDrawable = getAdditionalInstanceField(
                        view,
                        "mBatteryDrawable"
                    ) as BatteryDrawable

                    mBatteryDrawable.setShowPercentEnabled(mShowPercentInside)
                    mBatteryDrawable.alpha = Math.round(BATTERY_ICON_OPACITY * 2.55f)
                    updateCustomizeBatteryDrawable(mBatteryDrawable)
                } catch (ignored: Throwable) {
                }
            }

            val mCharging = isBatteryCharging(view)
            updateChargingIconView(view, mCharging)
        }
    }

    private fun isBatteryCharging(thisObject: Any): Boolean {
        var mIsIncompatibleCharging = false

        val mCharging: Boolean = try {
            getObjectField(thisObject, "mCharging") as Boolean
        } catch (ignored: Throwable) {
            try {
                getObjectField(thisObject, "mPluggedIn") as Boolean
            } catch (ignored: Throwable) {
                mIsCharging
            }
        }

        try {
            mIsIncompatibleCharging =
                getObjectField(thisObject, "mIsIncompatibleCharging") as Boolean
        } catch (throwable: Throwable) {
            log(TAG + throwable)
        }

        return mCharging && !mIsIncompatibleCharging
    }

    private fun initBatteryIfNull(param: MethodHookParam, batteryIconView: ImageView?): ImageView {
        var mBatteryIconView: ImageView? = batteryIconView

        if (mBatteryIconView == null) {
            mBatteryIconView = ImageView(mContext)
            try {
                mBatteryIconView.setImageDrawable(
                    getObjectField(
                        param.thisObject,
                        "mAccessorizedDrawable"
                    ) as Drawable
                )
            } catch (throwable: Throwable) {
                try {
                    mBatteryIconView.setImageDrawable(
                        getObjectField(
                            param.thisObject,
                            "mThemedDrawable"
                        ) as Drawable
                    )
                } catch (throwable1: Throwable) {
                    mBatteryIconView.setImageDrawable(
                        getObjectField(
                            param.thisObject,
                            "mDrawable"
                        ) as Drawable
                    )
                }
            }

            val typedValue = TypedValue()
            mContext.resources.getValue(
                mContext.resources.getIdentifier(
                    "status_bar_icon_scale_factor",
                    "dimen",
                    mContext.packageName
                ), typedValue, true
            )

            val iconScaleFactor = typedValue.float
            val batteryWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                mBatteryScaleWidth.toFloat(),
                mBatteryIconView.context.resources.displayMetrics
            ).toInt()
            val batteryHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                mBatteryScaleHeight.toFloat(),
                mBatteryIconView.context.resources.displayMetrics
            ).toInt()
            val mlp = MarginLayoutParams(
                (batteryWidth * iconScaleFactor).toInt(),
                (batteryHeight * iconScaleFactor).toInt()
            )

            mlp.setMargins(
                0,
                0,
                0,
                mContext.resources.getDimensionPixelOffset(
                    mContext.resources.getIdentifier(
                        "battery_margin_bottom",
                        "dimen",
                        mContext.packageName
                    )
                )
            )

            setObjectField(param.thisObject, "mBatteryIconView", mBatteryIconView)
            callMethod(param.thisObject, "addView", mBatteryIconView, mlp)

            mBatteryIconView.setVisibility(if (mHideBattery) View.GONE else View.VISIBLE)
        }

        return mBatteryIconView
    }

    private fun getNewBatteryDrawable(context: Context): BatteryDrawable? {
        val mBatteryDrawable = when (mBatteryStyle) {
            BATTERY_STYLE_CUSTOM_RLANDSCAPE -> RLandscapeBattery(context, frameColor)
            BATTERY_STYLE_CUSTOM_LANDSCAPE -> LandscapeBattery(context, frameColor)
            BATTERY_STYLE_PORTRAIT_CAPSULE -> PortraitBatteryCapsule(context, frameColor)
            BATTERY_STYLE_PORTRAIT_LORN -> PortraitBatteryLorn(context, frameColor)
            BATTERY_STYLE_PORTRAIT_MX -> PortraitBatteryMx(context, frameColor)
            BATTERY_STYLE_PORTRAIT_AIROO -> PortraitBatteryAiroo(context, frameColor)
            BATTERY_STYLE_RLANDSCAPE_STYLE_A -> RLandscapeBatteryStyleA(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_STYLE_A -> LandscapeBatteryStyleA(context, frameColor)
            BATTERY_STYLE_RLANDSCAPE_STYLE_B -> RLandscapeBatteryStyleB(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_STYLE_B -> LandscapeBatteryStyleB(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_IOS_15 -> LandscapeBatteryiOS15(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_IOS_16 -> LandscapeBatteryiOS16(context, frameColor)
            BATTERY_STYLE_PORTRAIT_ORIGAMI -> PortraitBatteryOrigami(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_SMILEY -> LandscapeBatterySmiley(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_MIUI_PILL -> LandscapeBatteryMIUIPill(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_COLOROS -> LandscapeBatteryColorOS(context, frameColor)
            BATTERY_STYLE_RLANDSCAPE_COLOROS -> RLandscapeBatteryColorOS(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYA -> LandscapeBatteryA(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYB -> LandscapeBatteryB(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYC -> LandscapeBatteryC(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYD -> LandscapeBatteryD(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYE -> LandscapeBatteryE(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYF -> LandscapeBatteryF(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYG -> LandscapeBatteryG(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYH -> LandscapeBatteryH(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYI -> LandscapeBatteryI(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYJ -> LandscapeBatteryJ(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYK -> LandscapeBatteryK(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYL -> LandscapeBatteryL(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYM -> LandscapeBatteryM(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYN -> LandscapeBatteryN(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_BATTERYO -> LandscapeBatteryO(context, frameColor)
            BATTERY_STYLE_CIRCLE, BATTERY_STYLE_DOTTED_CIRCLE -> CircleBattery(context, frameColor)
            BATTERY_STYLE_FILLED_CIRCLE -> CircleFilledBattery(context, frameColor)
            BATTERY_STYLE_LANDSCAPE_KIM -> LandscapeBatteryKim(context, frameColor)
            else -> null
        }

        mBatteryDrawable?.apply {
            setShowPercentEnabled(mShowPercentInside)
            alpha = Math.round(BATTERY_ICON_OPACITY * 2.55f)
        }

        return mBatteryDrawable
    }

    private fun setDefaultBatteryDimens() {
        val resParam: InitPackageResourcesParam = resParams[SYSTEMUI_PACKAGE] ?: return

        if (defaultLandscapeBatteryEnabled) {
            resParam.res.setReplacement(
                SYSTEMUI_PACKAGE, "dimen", "status_bar_battery_icon_width", DimensionReplacement(
                    mBatteryScaleWidth.toFloat(), TypedValue.COMPLEX_UNIT_DIP
                )
            )

            resParam.res.setReplacement(
                SYSTEMUI_PACKAGE, "dimen", "status_bar_battery_icon_height", DimensionReplacement(
                    mBatteryScaleHeight.toFloat(), TypedValue.COMPLEX_UNIT_DIP
                )
            )

            resParam.res.setReplacement(
                SYSTEMUI_PACKAGE,
                "dimen",
                "signal_cluster_battery_padding",
                DimensionReplacement(4f, TypedValue.COMPLEX_UNIT_DIP)
            )
        } else if (customBatteryEnabled) {
            resParam.res.setReplacement(
                SYSTEMUI_PACKAGE,
                "dimen",
                "signal_cluster_battery_padding",
                DimensionReplacement(3f, TypedValue.COMPLEX_UNIT_DIP)
            )
        }
    }

    private fun removeBatteryMeterViewMethods(batteryMeterViewClass: Class<*>?) {
        if (customBatteryEnabled) {
            batteryMeterViewClass
                .hookMethod(
                    "updateDrawable",
                    "updateBatteryStyle",
                    "updateSettings",
                    "updateVisibility"
                )
                .suppressError()
                .replace { }

            val batteryMeterViewExClass = findClass(
                "com.nothing.systemui.battery.BatteryMeterViewEx",
                suppressError = true
            )

            batteryMeterViewExClass?.let { batteryMeterViewEx ->
                batteryMeterViewEx
                    .hookMethod("refreshByBatteryStateEx")
                    .replace { }

                batteryMeterViewEx
                    .hookMethod("addBatteryImageView")
                    .replace { param ->
                        val context = param.args[0] as Context
                        val batteryMeterView = param.args[1] as ViewGroup
                        val batteryIconView = param.args[2] as ImageView
                        val batteryWidth = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            mBatteryScaleWidth.toFloat(),
                            context.resources.displayMetrics
                        ).toInt()
                        val batteryHeight = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            mBatteryScaleHeight.toFloat(),
                            context.resources.displayMetrics
                        ).toInt()

                        setStaticIntField(
                            batteryMeterViewEx,
                            "sTempMax",
                            context.resources.getInteger(
                                context.resources.getIdentifier(
                                    "config_wire_charging_temp_max",
                                    "integer",
                                    context.packageName
                                )
                            )
                        )
                        setStaticIntField(
                            batteryMeterViewEx,
                            "sVoltMax",
                            context.resources.getInteger(
                                context.resources.getIdentifier(
                                    "config_wire_charging_voltage_max",
                                    "integer",
                                    context.packageName
                                )
                            )
                        )

                        batteryMeterView.addView(
                            batteryIconView,
                            ViewGroup.LayoutParams(batteryWidth, batteryHeight)
                        )
                    }

                batteryMeterViewEx
                    .hookMethod("updateView")
                    .replace { refreshBatteryIcons() }
            }
        }
    }

    private fun updateChargingIconView() {
        for (view in batteryViews) {
            updateChargingIconView(view, mIsChargingImpl)
        }
    }

    private fun updateChargingIconView(thisObject: Any, mCharging: Boolean = mIsChargingImpl) {
        var mChargingIconView =
            (thisObject as ViewGroup).findViewWithTag<ImageView>(ICONIFY_CHARGING_ICON_TAG)

        if (mChargingIconView == null) {
            mChargingIconView = ImageView(mContext)
            mChargingIconView.tag = ICONIFY_CHARGING_ICON_TAG
            thisObject.addView(mChargingIconView, 1)
        }

        val drawable = when (mChargingIconStyle) {
            0 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_bold,
                mContext.theme
            )

            1 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_asus,
                mContext.theme
            )

            2 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_buddy,
                mContext.theme
            )

            3 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_evplug,
                mContext.theme
            )

            4 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_idc,
                mContext.theme
            )

            5 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_ios,
                mContext.theme
            )

            6 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_koplak,
                mContext.theme
            )

            7 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_miui,
                mContext.theme
            )

            8 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_mmk,
                mContext.theme
            )

            9 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_moto,
                mContext.theme
            )

            10 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_nokia,
                mContext.theme
            )

            11 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_plug,
                mContext.theme
            )

            12 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_powercable,
                mContext.theme
            )

            13 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_powercord,
                mContext.theme
            )

            14 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_powerstation,
                mContext.theme
            )

            15 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_realme,
                mContext.theme
            )

            16 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_soak,
                mContext.theme
            )

            17 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_stres,
                mContext.theme
            )

            18 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_strip,
                mContext.theme
            )

            19 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_usbcable,
                mContext.theme
            )

            20 -> ResourcesCompat.getDrawable(
                modRes,
                R.drawable.ic_charging_xiaomi,
                mContext.theme
            )

            else -> null
        }

        if (drawable != null && drawable !== mChargingIconView.getDrawable()) {
            mChargingIconView.setImageDrawable(drawable)
        }

        val left: Int = mContext.toPx(mChargingIconML)
        val right: Int = mContext.toPx(mChargingIconMR)
        val size: Int = mContext.toPx(mChargingIconWH)
        val lp = if (thisObject is LinearLayout) {
            LinearLayout.LayoutParams(size, size)
        } else {
            FrameLayout.LayoutParams(size, size)
        }

        lp.setMargins(
            left,
            0,
            right,
            mContext.resources.getDimensionPixelSize(
                mContext.resources.getIdentifier(
                    "battery_margin_bottom",
                    "dimen",
                    mContext.packageName
                )
            )
        )

        mChargingIconView.apply {
            setLayoutParams(lp)
            setVisibility(if (mCharging && mChargingIconSwitch) View.VISIBLE else View.GONE)
        }
    }

    private fun updateSettings(param: MethodHookParam) {
        updateCustomizeBatteryDrawable(param.thisObject)
        updateChargingIconView(param.thisObject)
        updateBatteryRotation(param.thisObject)
        updateFlipper(param.thisObject)
        updateChargingIconView()
    }

    private fun updateFlipper(thisObject: Any) {
        val batteryView = if (thisObject is LinearLayout) {
            thisObject.orientation = LinearLayout.HORIZONTAL
            thisObject.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            thisObject
        } else {
            thisObject as View
        }

        batteryView.layoutDirection = if (mSwapPercentage) {
            View.LAYOUT_DIRECTION_RTL
        } else {
            View.LAYOUT_DIRECTION_LTR
        }
    }

    private fun updateBatteryRotation(thisObject: Any) {
        val mBatteryIconView = getObjectField(thisObject, "mBatteryIconView") as View
        updateBatteryRotation(mBatteryIconView)
    }

    private fun updateBatteryRotation(mBatteryIconView: View) {
        mBatteryIconView.rotation = if (!defaultLandscapeBatteryEnabled && mBatteryLayoutReverse) {
            180
        } else {
            mBatteryRotation
        }.toFloat()
    }

    private fun updateCustomizeBatteryDrawable(thisObject: Any) {
        if (!customBatteryEnabled) return

        val mBatteryDrawable = getAdditionalInstanceField(
            thisObject,
            "mBatteryDrawable"
        ) as BatteryDrawable

        updateCustomizeBatteryDrawable(mBatteryDrawable)
    }

    private fun updateCustomizeBatteryDrawable(mBatteryDrawable: BatteryDrawable) {
        if (!customBatteryEnabled) return

        mBatteryDrawable.customizeBatteryDrawable(
            mBatteryLayoutReverse,
            mScaledPerimeterAlpha,
            mScaledFillAlpha,
            mCustomBlendColor,
            mRainbowFillColor,
            mCustomFillColor,
            mCustomFillGradColor,
            mCustomChargingColor,
            mCustomPowerSaveColor,
            mCustomPowerSaveFillColor,
            mChargingIconSwitch
        )
    }

    private fun scaleBatteryMeterViews(thisObject: Any?) {
        if (thisObject == null) return

        if (thisObject is ImageView) {
            scaleBatteryMeterViews(thisObject)
        } else {
            val mBatteryIconView = getObjectField(thisObject, "mBatteryIconView") as ImageView?
            mBatteryIconView?.let { scaleBatteryMeterViews(it) }
        }
    }

    private fun scaleBatteryMeterViews(mBatteryIconView: ImageView) {
        try {
            val context = mBatteryIconView.context
            val res = context.resources
            val typedValue = TypedValue()

            res.getValue(
                res.getIdentifier(
                    "status_bar_icon_scale_factor",
                    "dimen",
                    context.packageName
                ), typedValue, true
            )

            val iconScaleFactor = typedValue.float
            val batteryWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                mBatteryScaleWidth.toFloat(),
                mBatteryIconView.context.resources.displayMetrics
            ).toInt()
            val batteryHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                mBatteryScaleHeight.toFloat(),
                mBatteryIconView.context.resources.displayMetrics
            ).toInt()

            val scaledLayoutParams = try {
                mBatteryIconView.layoutParams as LinearLayout.LayoutParams
            } catch (throwable: Throwable) {
                mBatteryIconView.layoutParams as FrameLayout.LayoutParams
            }
            scaledLayoutParams.width = (batteryWidth * iconScaleFactor).toInt()
            scaledLayoutParams.height = (batteryHeight * iconScaleFactor).toInt()

            if (mBatteryCustomDimension) {
                scaledLayoutParams.setMargins(
                    mBatteryMarginLeft,
                    mBatteryMarginTop,
                    mBatteryMarginRight,
                    mBatteryMarginBottom
                )
            } else {
                scaledLayoutParams.setMargins(
                    0,
                    0,
                    0,
                    context.resources.getDimensionPixelOffset(
                        context.resources.getIdentifier(
                            "battery_margin_bottom",
                            "dimen",
                            context.packageName
                        )
                    )
                )
            }

            mBatteryIconView.setLayoutParams(scaledLayoutParams)
            mBatteryIconView.setVisibility(if (mHideBattery) View.GONE else View.VISIBLE)
        } catch (throwable: Throwable) {
            log(TAG + throwable)
        }
    }

    companion object {
        private val TAG = "Iconify - ${BatteryStyleManager::class.java.simpleName}: "
        private val batteryViews = ArrayList<View>()
        private var mBatteryStyle = 0
        private var mShowPercentInside = false
        private var mHidePercentage = false
        private var mHideBattery = false
        private var mBatteryRotation = 0
        private var customBatteryEnabled = false
        private var mBatteryScaleWidth = 20
        private var mBatteryScaleHeight = 20
        private var mBatteryCustomDimension = false
        private var mBatteryMarginLeft = 0
        private var mBatteryMarginTop = 0
        private var mBatteryMarginRight = 0
        private var mBatteryMarginBottom = 0
        private const val BATTERY_ICON_OPACITY = 100
    }
}