package com.drdisagree.iconify.xposed.modules

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.XResources
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.FIXED_STATUS_ICONS_SIDEMARGIN
import com.drdisagree.iconify.common.Preferences.FIXED_STATUS_ICONS_SWITCH
import com.drdisagree.iconify.common.Preferences.FIXED_STATUS_ICONS_TOPMARGIN
import com.drdisagree.iconify.common.Preferences.HEADER_CLOCK_SWITCH
import com.drdisagree.iconify.common.Preferences.HIDE_DATA_DISABLED_ICON
import com.drdisagree.iconify.common.Preferences.HIDE_STATUS_ICONS_SWITCH
import com.drdisagree.iconify.common.Preferences.QSPANEL_HIDE_CARRIER
import com.drdisagree.iconify.xposed.HookRes.Companion.resParams
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookLayout
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
class Miscellaneous(context: Context) : ModPack(context) {

    private var hideQsCarrierGroup = false
    private var hideStatusIcons = false
    private var fixedStatusIcons = false
    private var hideDataDisabledIcon = false
    private var sideMarginStatusIcons = 0
    private var topMarginStatusIcons = 8
    private var statusIcons: LinearLayout? = null
    private var statusIconContainer: LinearLayout? = null
    private var mobileSignalControllerParam: Any? = null
    private var showHeaderClockA14 = false

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            hideQsCarrierGroup = getBoolean(QSPANEL_HIDE_CARRIER, false)
            hideStatusIcons = getBoolean(HIDE_STATUS_ICONS_SWITCH, false)
            fixedStatusIcons = getBoolean(FIXED_STATUS_ICONS_SWITCH, false)
            topMarginStatusIcons = getSliderInt(FIXED_STATUS_ICONS_TOPMARGIN, 8)
            sideMarginStatusIcons = getSliderInt(FIXED_STATUS_ICONS_SIDEMARGIN, 0)
            hideDataDisabledIcon = getBoolean(HIDE_DATA_DISABLED_ICON, false)
            showHeaderClockA14 = getBoolean(HEADER_CLOCK_SWITCH, false) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        }

        if (key.isNotEmpty()) {
            key[0].let {
                if (it == QSPANEL_HIDE_CARRIER) {
                    hideQSCarrierGroup()
                }

                if (it == HIDE_STATUS_ICONS_SWITCH) {
                    hideStatusIcons()
                }

                if (it == FIXED_STATUS_ICONS_SWITCH ||
                    it == HIDE_STATUS_ICONS_SWITCH ||
                    it == FIXED_STATUS_ICONS_TOPMARGIN ||
                    it == FIXED_STATUS_ICONS_SIDEMARGIN
                ) {
                    fixedStatusIconsA12()
                }

                if (it == HIDE_DATA_DISABLED_ICON &&
                    mobileSignalControllerParam != null
                ) {
                    callMethod(mobileSignalControllerParam, "updateTelephony")
                }
            }
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        hideElements()
        hideQSCarrierGroup()
        hideStatusIcons()
        fixedStatusIconsA12()
        hideDataDisabledIcon()
    }

    private fun hideElements() {
        val quickStatusBarHeader = findClass("$SYSTEMUI_PACKAGE.qs.QuickStatusBarHeader")

        quickStatusBarHeader
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                if (hideStatusIcons) {
                    try {
                        (getObjectField(
                            param.thisObject,
                            "mDateView"
                        ) as View).apply {
                            layoutParams.height = 0
                            layoutParams.width = 0
                            visibility = View.INVISIBLE
                        }
                    } catch (ignored: Throwable) {
                    }

                    try {
                        (getObjectField(
                            param.thisObject,
                            "mClockDateView"
                        ) as TextView).apply {
                            visibility = View.INVISIBLE
                            setTextAppearance(0)
                            setTextColor(0)
                        }
                    } catch (ignored: Throwable) {
                    }

                    try {
                        (getObjectField(
                            param.thisObject,
                            "mClockView"
                        ) as TextView).apply {
                            visibility = View.INVISIBLE
                            setTextAppearance(0)
                            setTextColor(0)
                        }
                    } catch (ignored: Throwable) {
                    }
                }

                if (hideStatusIcons || hideQsCarrierGroup) {
                    try {
                        val mQSCarriers = getObjectField(
                            param.thisObject,
                            "mQSCarriers"
                        ) as View

                        mQSCarriers.visibility = View.INVISIBLE
                    } catch (ignored: Throwable) {
                    }
                }
            }

        val shadeHeaderControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.shade.LargeScreenShadeHeaderController",
            "$SYSTEMUI_PACKAGE.shade.ShadeHeaderController"
        )

        shadeHeaderControllerClass
            .hookMethod("onInit")
            .runAfter { param ->
                if (hideStatusIcons) {
                    try {
                        val iconContainer = getObjectField(
                            param.thisObject,
                            "iconContainer"
                        ) as LinearLayout

                        (iconContainer.parent as ViewGroup).removeView(iconContainer)
                    } catch (ignored: Throwable) {
                    }
                    try {
                        val batteryIcon = getObjectField(
                            param.thisObject,
                            "batteryIcon"
                        ) as LinearLayout

                        (batteryIcon.parent as ViewGroup).removeView(batteryIcon)
                    } catch (ignored: Throwable) {
                    }
                }

                if (hideStatusIcons || hideQsCarrierGroup) {
                    try {
                        val qsCarrierGroup = getObjectField(
                            param.thisObject,
                            "qsCarrierGroup"
                        ) as LinearLayout

                        (qsCarrierGroup.parent as ViewGroup).removeView(qsCarrierGroup)
                    } catch (ignored: Throwable) {
                    }

                    try {
                        val mShadeCarrierGroup = getObjectField(
                            param.thisObject,
                            "mShadeCarrierGroup"
                        ) as LinearLayout

                        (mShadeCarrierGroup.parent as ViewGroup).removeView(mShadeCarrierGroup)
                    } catch (ignored: Throwable) {
                    }
                }
            }
    }

    private fun hideDataDisabledIcon() {
        val mobileSignalController =
            findClass("$SYSTEMUI_PACKAGE.statusbar.connectivity.MobileSignalController")
        val alwaysShowDataRatIcon = booleanArrayOf(false)
        val mDataDisabledIcon = booleanArrayOf(false)

        mobileSignalController
            .hookMethod("updateTelephony")
            .runBefore { param ->
                if (mobileSignalControllerParam == null) {
                    mobileSignalControllerParam = param.thisObject
                }

                if (!hideDataDisabledIcon) return@runBefore

                alwaysShowDataRatIcon[0] = getObjectField(
                    getObjectField(
                        param.thisObject,
                        "mConfig"
                    ), "alwaysShowDataRatIcon"
                ) as Boolean

                setObjectField(
                    getObjectField(param.thisObject, "mConfig"),
                    "alwaysShowDataRatIcon",
                    false
                )

                try {
                    mDataDisabledIcon[0] = getObjectField(
                        param.thisObject,
                        "mDataDisabledIcon"
                    ) as Boolean

                    setObjectField(
                        param.thisObject,
                        "mDataDisabledIcon",
                        false
                    )
                } catch (ignored: Throwable) {
                }
            }
            .runAfter { param ->
                if (mobileSignalControllerParam == null) {
                    mobileSignalControllerParam = param.thisObject
                }

                if (!hideDataDisabledIcon) return@runAfter

                setObjectField(
                    getObjectField(param.thisObject, "mConfig"),
                    "alwaysShowDataRatIcon",
                    alwaysShowDataRatIcon[0]
                )

                try {
                    setObjectField(
                        param.thisObject,
                        "mDataDisabledIcon",
                        mDataDisabledIcon[0]
                    )
                } catch (ignored: Throwable) {
                }
            }
    }

    private fun hideQSCarrierGroup() {
        val xResources: XResources = resParams[SYSTEMUI_PACKAGE]?.res ?: return

        xResources
            .hookLayout()
            .packageName(SYSTEMUI_PACKAGE)
            .resource("layout", "quick_qs_status_icons")
            .suppressError()
            .run { liparam ->
                if (!hideQsCarrierGroup || showHeaderClockA14) return@run

                liparam.view.findViewById<LinearLayout>(
                    liparam.res.getIdentifier(
                        "carrier_group",
                        "id",
                        mContext.packageName
                    )
                ).apply {
                    layoutParams.height = 0
                    layoutParams.width = 0
                    minimumWidth = 0
                    visibility = View.INVISIBLE
                }
            }
    }

    private fun hideStatusIcons() {
        val xResources: XResources = resParams[SYSTEMUI_PACKAGE]?.res ?: return

        xResources
            .hookLayout()
            .packageName(SYSTEMUI_PACKAGE)
            .resource("layout", "quick_qs_status_icons")
            .suppressError()
            .run { liparam ->
                if (!hideStatusIcons) return@run

                try {
                    liparam.view.findViewById<TextView>(
                        liparam.res.getIdentifier(
                            "clock",
                            "id",
                            mContext.packageName
                        )
                    ).apply {
                        layoutParams.height = 0
                        layoutParams.width = 0
                        setTextAppearance(0)
                        setTextColor(0)
                    }
                } catch (ignored: Throwable) {
                }

                try {
                    liparam.view.findViewById<TextView>(
                        liparam.res.getIdentifier(
                            "date_clock",
                            "id",
                            mContext.packageName
                        )
                    ).apply {
                        layoutParams.height = 0
                        layoutParams.width = 0
                        setTextAppearance(0)
                        setTextColor(0)
                    }
                } catch (ignored: Throwable) {
                }

                if (!showHeaderClockA14) {
                    try {
                        liparam.view.findViewById<LinearLayout>(
                            liparam.res.getIdentifier(
                                "carrier_group",
                                "id",
                                mContext.packageName
                            )
                        ).apply {
                            layoutParams.height = 0
                            layoutParams.width = 0
                            minimumWidth = 0
                            visibility = View.INVISIBLE
                        }
                    } catch (ignored: Throwable) {
                    }

                    try {
                        liparam.view.findViewById<LinearLayout>(
                            liparam.res.getIdentifier(
                                "statusIcons",
                                "id",
                                mContext.packageName
                            )
                        ).apply {
                            layoutParams.height = 0
                            layoutParams.width = 0
                        }
                    } catch (ignored: Throwable) {
                    }

                    try {
                        liparam.view.findViewById<LinearLayout>(
                            liparam.res.getIdentifier(
                                "batteryRemainingIcon",
                                "id",
                                mContext.packageName
                            )
                        ).apply {
                            layoutParams.height = 0
                            layoutParams.width = 0
                        }
                    } catch (ignored: Throwable) {
                    }
                }

                try {
                    liparam.view.findViewById<FrameLayout>(
                        liparam.res.getIdentifier(
                            "rightLayout",
                            "id",
                            mContext.packageName
                        )
                    ).apply {
                        layoutParams.height = 0
                        layoutParams.width = 0
                        visibility = View.INVISIBLE
                    }
                } catch (ignored: Throwable) {
                }

                // Ricedroid date
                try {
                    liparam.view.findViewById<TextView>(
                        liparam.res.getIdentifier(
                            "date",
                            "id",
                            mContext.packageName
                        )
                    ).apply {
                        layoutParams.height = 0
                        layoutParams.width = 0
                        setTextAppearance(0)
                        setTextColor(0)
                    }
                } catch (ignored: Throwable) {
                }

                // Nusantara clock
                try {
                    liparam.view.findViewById<TextView>(
                        liparam.res.getIdentifier(
                            "jr_clock",
                            "id",
                            mContext.packageName
                        )
                    ).apply {
                        layoutParams.height = 0
                        layoutParams.width = 0
                        setTextAppearance(0)
                        setTextColor(0)
                    }
                } catch (ignored: Throwable) {
                }

                // Nusantara date
                try {
                    val jrDateContainer =
                        liparam.view.findViewById<LinearLayout>(
                            liparam.res.getIdentifier(
                                "jr_date_container",
                                "id",
                                mContext.packageName
                            )
                        )
                    (jrDateContainer.getChildAt(0) as TextView).apply {
                        layoutParams.height = 0
                        layoutParams.width = 0
                        setTextAppearance(0)
                        setTextColor(0)
                    }
                } catch (ignored: Throwable) {
                }
            }

        xResources
            .hookLayout()
            .packageName(SYSTEMUI_PACKAGE)
            .resource("layout", "quick_status_bar_header_date_privacy")
            .suppressError()
            .run { liparam ->
                if (!hideStatusIcons) return@run

                try {
                    liparam.view.findViewById<TextView>(
                        liparam.res.getIdentifier(
                            "date",
                            "id",
                            mContext.packageName
                        )
                    ).apply {
                        setTextAppearance(0)
                        layoutParams.height = 0
                        layoutParams.width = 0
                        setTextAppearance(0)
                        setTextColor(0)
                    }
                } catch (ignored: Throwable) {
                }
            }
    }

    private fun fixedStatusIconsA12() {
        if (Build.VERSION.SDK_INT >= 33) return

        val xResources: XResources = resParams[SYSTEMUI_PACKAGE]?.res ?: return

        xResources
            .hookLayout()
            .packageName(SYSTEMUI_PACKAGE)
            .resource("layout", "quick_qs_status_icons")
            .suppressError()
            .run { liparam ->
                if (!fixedStatusIcons || hideStatusIcons) return@run

                try {
                    statusIcons = liparam.view.findViewById(
                        liparam.res.getIdentifier(
                            "statusIcons",
                            "id",
                            mContext.packageName
                        )
                    )

                    if (statusIcons != null) {
                        statusIconContainer = statusIcons!!.parent as LinearLayout
                        statusIcons!!.layoutParams.height = 0
                        statusIcons!!.layoutParams.width = 0
                        statusIcons!!.visibility = View.GONE
                        statusIcons!!.requestLayout()
                    }

                    val batteryRemainingIcon = liparam.view.findViewById<LinearLayout>(
                        liparam.res.getIdentifier(
                            "batteryRemainingIcon",
                            "id",
                            mContext.packageName
                        )
                    )

                    batteryRemainingIcon?.let {
                        (it.layoutParams as LinearLayout.LayoutParams)
                            .weight = 0f
                        it.layoutParams.height = 0
                        it.layoutParams.width = 0
                        it.visibility = View.GONE
                        it.requestLayout()
                    }
                } catch (ignored: Throwable) {
                }
            }

        xResources
            .hookLayout()
            .packageName(SYSTEMUI_PACKAGE)
            .resource("layout", "quick_status_bar_header_date_privacy")
            .suppressError()
            .run { liparam ->
                if (!fixedStatusIcons || hideStatusIcons) return@run

                try {
                    val privacyContainer =
                        liparam.view.findViewById<FrameLayout>(
                            liparam.res.getIdentifier(
                                "privacy_container",
                                "id",
                                mContext.packageName
                            )
                        )

                    if (statusIconContainer != null && statusIconContainer!!.parent != null && statusIcons != null) {
                        try {
                            (statusIconContainer!!.parent as FrameLayout).removeView(
                                statusIconContainer
                            )
                        } catch (ignored: Throwable) {
                            (statusIconContainer!!.parent as LinearLayout).removeView(
                                statusIconContainer
                            )
                        }

                        val statusIcons =
                            statusIconContainer!!.getChildAt(0) as LinearLayout
                        statusIcons.let {
                            it.layoutParams.height = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                28f,
                                mContext.resources.displayMetrics
                            ).toInt()
                            it.layoutParams.width =
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            it.visibility = View.VISIBLE
                            it.requestLayout()
                        }

                        val batteryRemainingIcon =
                            (statusIconContainer!!.getChildAt(1) as LinearLayout)
                        batteryRemainingIcon.let {
                            (it.layoutParams as LinearLayout.LayoutParams).weight =
                                1f
                            it.layoutParams.height =
                                TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP,
                                    28f,
                                    mContext.resources.displayMetrics
                                ).toInt()
                            it.layoutParams.width = 0
                            it.visibility = View.VISIBLE
                            it.requestLayout()
                        }

                        statusIconContainer!!.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                28f,
                                mContext.resources.displayMetrics
                            ).toInt(),
                            Gravity.END
                        )
                        statusIconContainer!!.gravity = Gravity.CENTER
                        (statusIconContainer!!.layoutParams as FrameLayout.LayoutParams).setMargins(
                            0, TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                topMarginStatusIcons.toFloat(),
                                mContext.resources.displayMetrics
                            ).toInt(), 0, 0
                        )
                        (statusIconContainer!!.layoutParams as FrameLayout.LayoutParams).marginEnd =
                            TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                sideMarginStatusIcons.toFloat(),
                                mContext.resources.displayMetrics
                            ).toInt()
                        statusIconContainer!!.requestLayout()

                        privacyContainer.addView(statusIconContainer)
                    }
                } catch (ignored: Throwable) {
                }
            }
    }

    companion object {
        private val TAG = "Iconify - ${Miscellaneous::class.java.simpleName}: "
    }
}
