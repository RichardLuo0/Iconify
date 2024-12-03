package com.drdisagree.iconify.xposed.modules.themes

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.service.quicksettings.Tile
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.drdisagree.iconify.R
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.FLUID_NOTIF_TRANSPARENCY
import com.drdisagree.iconify.common.Preferences.FLUID_POWERMENU_TRANSPARENCY
import com.drdisagree.iconify.common.Preferences.FLUID_QSPANEL
import com.drdisagree.iconify.xposed.HookRes
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.RoundedCornerProgressDrawable
import com.drdisagree.iconify.xposed.modules.utils.SettingsLibUtils
import com.drdisagree.iconify.xposed.modules.utils.ViewHelper.toPx
import com.drdisagree.iconify.xposed.utils.SystemUtils
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlin.math.max
import kotlin.math.min

@SuppressLint("DiscouragedApi")
class QSFluidThemeA14(context: Context) : ModPack(context) {

    private var wasDark: Boolean = SystemUtils.isDarkMode
    private var mSlider: SeekBar? = null
    var colorActive = mContext.resources.getColor(
        mContext.resources.getIdentifier(
            "android:color/system_accent1_400",
            "color",
            mContext.packageName
        ), mContext.theme
    )
    var colorInactive = SettingsLibUtils.getColorAttrDefaultColor(
        mContext,
        mContext.resources.getIdentifier(
            "offStateColor",
            "attr",
            mContext.packageName
        )
    )
    var colorActiveAlpha = changeAlpha(colorActive, ACTIVE_ALPHA)
    var colorInactiveAlpha = changeAlpha(colorInactive, INACTIVE_ALPHA)

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            fluidQsThemeEnabled = getBoolean(FLUID_QSPANEL, false)
            fluidNotificationEnabled = fluidQsThemeEnabled &&
                    getBoolean(FLUID_NOTIF_TRANSPARENCY, false)
            fluidPowerMenuEnabled = fluidQsThemeEnabled &&
                    getBoolean(FLUID_POWERMENU_TRANSPARENCY, false)
        }

        initResources()
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val qsPanelClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.QSPanel",
            loadPackageParam.classLoader
        )
        val qsTileViewImplClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.tileimpl.QSTileViewImpl",
            loadPackageParam.classLoader
        )
        val qsIconViewImplClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.tileimpl.QSIconViewImpl",
            loadPackageParam.classLoader
        )
        var footerViewClass = findClassIfExists(
            "$SYSTEMUI_PACKAGE.statusbar.notification.footer.ui.view.FooterView",
            loadPackageParam.classLoader
        )
        if (footerViewClass == null) {
            footerViewClass = findClass(
                "$SYSTEMUI_PACKAGE.statusbar.notification.row.FooterView",
                loadPackageParam.classLoader
            )
        }
        val centralSurfacesImplClass = findClassIfExists(
            "$SYSTEMUI_PACKAGE.statusbar.phone.CentralSurfacesImpl",
            loadPackageParam.classLoader
        )
        val notificationExpandButtonClass = findClassIfExists(
            "com.android.internal.widget.NotificationExpandButton",
            loadPackageParam.classLoader
        )
        val brightnessSliderViewClass = findClass(
            "$SYSTEMUI_PACKAGE.settings.brightness.BrightnessSliderView",
            loadPackageParam.classLoader
        )
        val brightnessControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.settings.brightness.BrightnessController",
            loadPackageParam.classLoader
        )
        val brightnessMirrorControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.policy.BrightnessMirrorController",
            loadPackageParam.classLoader
        )
        val brightnessSliderControllerClass = findClassIfExists(
            "$SYSTEMUI_PACKAGE.settings.brightness.BrightnessSliderController",
            loadPackageParam.classLoader
        )
        val activatableNotificationViewClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.notification.row.ActivatableNotificationView",
            loadPackageParam.classLoader
        )
        val themeColorKtClass = findClassIfExists(
            "com.android.compose.theme.ColorKt",
            loadPackageParam.classLoader
        )
        val footerActionsViewModelClass = findClassIfExists(
            "$SYSTEMUI_PACKAGE.qs.footer.ui.viewmodel.FooterActionsViewModel",
            loadPackageParam.classLoader
        )
        val footerActionsViewBinderClass = findClassIfExists(
            "$SYSTEMUI_PACKAGE.qs.footer.ui.binder.FooterActionsViewBinder",
            loadPackageParam.classLoader
        )

        // Initialize resources and colors
        hookAllMethods(qsTileViewImplClass, "init", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                initResources()
            }
        })

        if (centralSurfacesImplClass != null) {
            hookAllConstructors(centralSurfacesImplClass, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    initResources()
                }
            })

            hookAllMethods(centralSurfacesImplClass, "updateTheme", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    initResources()
                }
            })
        }

        hookAllConstructors(qsTileViewImplClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val tempColorInactive = SettingsLibUtils.getColorAttrDefaultColor(
                    mContext,
                    mContext.resources.getIdentifier(
                        "offStateColor",
                        "attr",
                        mContext.packageName
                    )
                )

                colorInactive = if (tempColorInactive != 0) tempColorInactive
                else SettingsLibUtils.getColorAttrDefaultColor(
                    mContext,
                    mContext.resources.getIdentifier(
                        "shadeInactive",
                        "attr",
                        mContext.packageName
                    )
                )
                colorInactiveAlpha = changeAlpha(colorInactive, INACTIVE_ALPHA)
            }
        })

        hookAllMethods(qsTileViewImplClass, "getBackgroundColorForState", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!fluidQsThemeEnabled) return

                try {
                    if (param.args[0] as Int == Tile.STATE_ACTIVE) {
                        param.result = colorActiveAlpha
                    } else {
                        val inactiveColor = param.result as Int?

                        inactiveColor?.let {
                            colorInactive = it
                            colorInactiveAlpha = changeAlpha(it, INACTIVE_ALPHA)

                            if (param.args[0] as Int == Tile.STATE_INACTIVE) {
                                param.result = changeAlpha(it, INACTIVE_ALPHA)
                            } else if (param.args[0] as Int == Tile.STATE_UNAVAILABLE) {
                                param.result = changeAlpha(it, UNAVAILABLE_ALPHA)
                            }
                        }
                    }
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }
        })

        // QS icon color
        hookAllMethods(qsIconViewImplClass, "getIconColorForState", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!fluidQsThemeEnabled) return

                try {
                    if (getObjectField(
                            param.args[1],
                            "state"
                        ) as Int == Tile.STATE_ACTIVE
                    ) {
                        param.result = colorActive
                    }
                } catch (ignored: Throwable) {
                }
            }
        })

        hookAllMethods(qsIconViewImplClass, "updateIcon", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!fluidQsThemeEnabled) return
                try {
                    if (param.args[0] is ImageView &&
                        getIntField(param.args[1], "state") == Tile.STATE_ACTIVE
                    ) {
                        (param.args[0] as ImageView).imageTintList = ColorStateList.valueOf(
                            colorActive
                        )
                    }
                } catch (ignored: Throwable) {
                }
            }
        })

        hookAllMethods(qsIconViewImplClass, "setIcon", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!fluidQsThemeEnabled) return

                try {
                    if (param.args[0] is ImageView &&
                        getIntField(param.args[1], "state") == Tile.STATE_ACTIVE
                    ) {
                        setObjectField(param.thisObject, "mTint", colorActive)
                    }
                } catch (ignored: Throwable) {
                }
            }
        })

        try {
            val qsContainerImplClass = findClass(
                "$SYSTEMUI_PACKAGE.qs.QSContainerImpl",
                loadPackageParam.classLoader
            )

            hookAllMethods(qsContainerImplClass, "updateResources", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!fluidQsThemeEnabled) return

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

                    // Security footer
                    view.let {
                        it.getChildAt(0)?.apply {
                            background?.setTint(colorInactiveAlpha)
                            background?.alpha = (INACTIVE_ALPHA * 255).toInt()
                        }
                        it.getChildAt(1)?.apply {
                            background?.setTint(colorInactiveAlpha)
                            background?.alpha = (INACTIVE_ALPHA * 255).toInt()
                        }
                    }

                    // Settings button
                    view.findViewById<View?>(
                        mContext.resources.getIdentifier(
                            "settings_button_container",
                            "id",
                            mContext.packageName
                        )
                    )?.apply {
                        background.setTint(colorInactiveAlpha)
                    }

                    // Multi user switch
                    view.findViewById<View?>(
                        mContext.resources.getIdentifier(
                            "multi_user_switch",
                            "id",
                            mContext.packageName
                        )
                    )?.apply {
                        background.setTint(colorInactiveAlpha)
                    }

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
                        background.setTint(colorActive)
                        background.alpha = (ACTIVE_ALPHA * 255).toInt()

                        if (this is ImageView) {
                            imageTintList = ColorStateList.valueOf(colorActive)
                        } else if (this is ViewGroup) {
                            (getChildAt(0) as ImageView).setColorFilter(
                                colorActive,
                                PorterDuff.Mode.SRC_IN
                            )
                        }
                    }
                }
            })
        } catch (ignored: Throwable) {
        }

        try { // Compose implementation of QS Footer actions
            val graphicsColorKtClass = findClass(
                "androidx.compose.ui.graphics.ColorKt",
                loadPackageParam.classLoader
            )

            hookAllMethods(themeColorKtClass, "colorAttr", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!fluidQsThemeEnabled) return

                    val code = param.args[0] as Int
                    var result = 0

                    when (code) {
                        PM_LITE_BACKGROUND_CODE -> {
                            result = colorActiveAlpha
                        }

                        else -> {
                            try {
                                when (mContext.resources.getResourceName(code).split("/")[1]) {
                                    "underSurface", "onShadeActive", "shadeInactive" -> {
                                        result = colorInactiveAlpha // button backgrounds
                                    }
                                }
                            } catch (ignored: Throwable) {
                            }
                        }
                    }

                    if (result != 0) {
                        param.result = callStaticMethod(graphicsColorKtClass, "Color", result)
                    }
                }
            })

            hookAllConstructors(footerActionsViewModelClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!fluidQsThemeEnabled) return

                    // Power button
                    val power = getObjectField(param.thisObject, "power")
                    setObjectField(power, "iconTint", colorActive)
                    setObjectField(power, "backgroundColor", PM_LITE_BACKGROUND_CODE)

                    // We must use the classes defined in the apk. Using our own will fail.
                    val stateFlowImplClass = findClass(
                        "kotlinx.coroutines.flow.StateFlowImpl",
                        loadPackageParam.classLoader
                    )
                    val readonlyStateFlowClass = findClass(
                        "kotlinx.coroutines.flow.ReadonlyStateFlow",
                        loadPackageParam.classLoader
                    )

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
            })

            hookAllMethods(footerActionsViewBinderClass, "bindButton", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!fluidQsThemeEnabled) return

                    val view = getObjectField(param.args[0], "view") as View
                    view.background?.alpha = (INACTIVE_ALPHA * 255).toInt()
                }
            })

            hookAllMethods(footerActionsViewBinderClass, "bind", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!fluidQsThemeEnabled) return

                    val view = param.args[0] as LinearLayout
                    view.setBackgroundColor(Color.TRANSPARENT)
                    view.elevation = 0f
                }
            })
        } catch (ignored: Throwable) {
        }

        // Brightness slider and auto brightness color
        hookAllMethods(brightnessSliderViewClass, "onFinishInflate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                mSlider = getObjectField(param.thisObject, "mSlider") as SeekBar

                try {
                    if (mSlider != null && fluidQsThemeEnabled) {
                        mSlider!!.progressDrawable = createBrightnessDrawable(mContext)

                        val progress = mSlider!!.progressDrawable as LayerDrawable
                        val progressSlider = progress
                            .findDrawableByLayerId(android.R.id.progress) as DrawableWrapper

                        try {
                            val actualProgressSlider = progressSlider.drawable as LayerDrawable?
                            val mBrightnessIcon = actualProgressSlider!!.findDrawableByLayerId(
                                mContext.resources.getIdentifier(
                                    "slider_icon",
                                    "id",
                                    mContext.packageName
                                )
                            )

                            mBrightnessIcon.setTintList(ColorStateList.valueOf(Color.TRANSPARENT))
                            mBrightnessIcon.alpha = 0
                        } catch (ignored: Throwable) {
                        }
                    }
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }
        })

        hookAllMethods(brightnessControllerClass, "updateIcon", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!fluidQsThemeEnabled) return

                try {
                    (getObjectField(
                        param.thisObject,
                        "mIcon"
                    ) as ImageView).imageTintList = ColorStateList.valueOf(
                        colorActive
                    )

                    (getObjectField(
                        param.thisObject,
                        "mIcon"
                    ) as ImageView).backgroundTintList = ColorStateList.valueOf(
                        colorActiveAlpha
                    )
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }
        })

        if (brightnessSliderControllerClass != null) {
            hookAllConstructors(brightnessSliderControllerClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!fluidQsThemeEnabled) return

                    try {
                        (getObjectField(
                            param.thisObject,
                            "mIcon"
                        ) as ImageView).imageTintList = ColorStateList.valueOf(
                            colorActive
                        )

                        (getObjectField(
                            param.thisObject,
                            "mIcon"
                        ) as ImageView).backgroundTintList = ColorStateList.valueOf(
                            colorActiveAlpha
                        )
                    } catch (throwable: Throwable) {
                        try {
                            (getObjectField(
                                param.thisObject,
                                "mIconView"
                            ) as ImageView).imageTintList = ColorStateList.valueOf(
                                colorActive
                            )

                            (getObjectField(
                                param.thisObject,
                                "mIconView"
                            ) as ImageView).backgroundTintList = ColorStateList.valueOf(
                                colorActiveAlpha
                            )
                        } catch (ignored: Throwable) {
                        }
                    }
                }
            })
        }

        hookAllMethods(
            brightnessMirrorControllerClass,
            "updateIcon",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!fluidQsThemeEnabled) return

                    try {
                        (getObjectField(
                            param.thisObject,
                            "mIcon"
                        ) as ImageView).imageTintList = ColorStateList.valueOf(
                            colorActive
                        )

                        (getObjectField(
                            param.thisObject,
                            "mIcon"
                        ) as ImageView).backgroundTintList = ColorStateList.valueOf(
                            colorActiveAlpha
                        )
                    } catch (throwable: Throwable) {
                        log(TAG + throwable)
                    }
                }
            })

        hookAllMethods(
            brightnessMirrorControllerClass,
            "updateResources",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!fluidQsThemeEnabled) return

                    try {
                        val mBrightnessMirror = getObjectField(
                            param.thisObject,
                            "mBrightnessMirror"
                        ) as FrameLayout
                        mBrightnessMirror.background.alpha = (INACTIVE_ALPHA * 255).toInt()
                    } catch (throwable: Throwable) {
                        log(TAG + throwable)
                    }
                }
            })

        hookAllMethods(qsPanelClass, "updateResources", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!fluidQsThemeEnabled) return

                try {
                    (getObjectField(param.thisObject, "mAutoBrightnessView") as View)
                        .background.setTint(colorActiveAlpha)
                } catch (ignored: Throwable) {
                }
            }
        })

        // QS tile primary label color
        hookAllMethods(qsTileViewImplClass, "getLabelColorForState", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!fluidQsThemeEnabled) return

                try {
                    if (param.args[0] as Int == Tile.STATE_ACTIVE) {
                        param.result = colorActive
                    }
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }
        })

        // QS tile secondary label color
        hookAllMethods(
            qsTileViewImplClass,
            "getSecondaryLabelColorForState",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!fluidQsThemeEnabled) return

                    try {
                        if (param.args[0] as Int == Tile.STATE_ACTIVE) {
                            param.result = colorActive
                        }
                    } catch (throwable: Throwable) {
                        log(TAG + throwable)
                    }
                }
            }
        )

        hookAllConstructors(qsTileViewImplClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!fluidQsThemeEnabled) return

                colorInactive = changeAlpha(
                    getObjectField(
                        param.thisObject,
                        "colorInactive"
                    ) as Int, 1.0f
                )
                colorInactiveAlpha = changeAlpha(colorInactive, INACTIVE_ALPHA)

                initResources()

                // For LineageOS based roms
                try {
                    setObjectField(
                        param.thisObject,
                        "colorActive",
                        colorActiveAlpha
                    )

                    setObjectField(
                        param.thisObject,
                        "colorInactive",
                        changeAlpha(
                            getObjectField(param.thisObject, "colorInactive") as Int,
                            INACTIVE_ALPHA
                        )
                    )

                    setObjectField(
                        param.thisObject,
                        "colorUnavailable",
                        changeAlpha(
                            getObjectField(param.thisObject, "colorInactive") as Int,
                            UNAVAILABLE_ALPHA
                        )
                    )

                    setObjectField(
                        param.thisObject,
                        "colorLabelActive",
                        colorActive
                    )

                    setObjectField(
                        param.thisObject,
                        "colorSecondaryLabelActive",
                        colorActive
                    )
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }

                try {
                    if (mSlider != null) {
                        mSlider!!.progressDrawable = createBrightnessDrawable(mContext)

                        val progress = mSlider!!.progressDrawable as LayerDrawable
                        val progressSlider =
                            progress.findDrawableByLayerId(android.R.id.progress) as DrawableWrapper

                        try {
                            val actualProgressSlider = progressSlider.drawable as LayerDrawable?
                            val mBrightnessIcon = actualProgressSlider!!.findDrawableByLayerId(
                                mContext.resources.getIdentifier(
                                    "slider_icon",
                                    "id",
                                    mContext.packageName
                                )
                            )

                            mBrightnessIcon.setTintList(ColorStateList.valueOf(Color.TRANSPARENT))
                            mBrightnessIcon.alpha = 0
                        } catch (ignored: Throwable) {
                        }
                    }
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }
        })

        hookAllMethods(qsTileViewImplClass, "updateResources", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!fluidQsThemeEnabled) return

                colorInactive = changeAlpha(
                    getObjectField(
                        param.thisObject,
                        "colorInactive"
                    ) as Int, 1.0f
                )
                colorInactiveAlpha = changeAlpha(colorInactive, INACTIVE_ALPHA)

                initResources()

                try {
                    setObjectField(
                        param.thisObject,
                        "colorActive",
                        colorActiveAlpha
                    )

                    setObjectField(
                        param.thisObject,
                        "colorInactive",
                        changeAlpha(
                            getObjectField(param.thisObject, "colorInactive") as Int,
                            INACTIVE_ALPHA
                        )
                    )

                    setObjectField(
                        param.thisObject,
                        "colorUnavailable",
                        changeAlpha(
                            getObjectField(param.thisObject, "colorInactive") as Int,
                            UNAVAILABLE_ALPHA
                        )
                    )

                    setObjectField(
                        param.thisObject,
                        "colorLabelActive",
                        colorActive
                    )

                    setObjectField(
                        param.thisObject,
                        "colorSecondaryLabelActive",
                        colorActive
                    )
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }
        })

        // Notifications
        hookAllMethods(
            activatableNotificationViewClass,
            "onFinishInflate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!fluidQsThemeEnabled || !fluidNotificationEnabled) return

                    val mBackgroundNormal =
                        getObjectField(param.thisObject, "mBackgroundNormal") as View?
                    mBackgroundNormal?.alpha = INACTIVE_ALPHA
                }
            })

        // Notification expand/collapse pill
        if (notificationExpandButtonClass != null) {
            hookAllMethods(
                notificationExpandButtonClass,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!fluidQsThemeEnabled || !fluidNotificationEnabled) return

                        val mPillView = (param.thisObject as ViewGroup).findViewById<View?>(
                            mContext.resources.getIdentifier(
                                "expand_button_pill",
                                "id",
                                mContext.packageName
                            )
                        )
                        mPillView?.background?.alpha = (INACTIVE_ALPHA * 255).toInt()
                    }
                })
        }

        // Notification footer buttons
        val updateNotificationFooterButtons: XC_MethodHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!fluidQsThemeEnabled || !fluidNotificationEnabled) return

                try {
                    val mManageButton: Button = try {
                        getObjectField(param.thisObject, "mManageButton")
                    } catch (ignored: Throwable) {
                        getObjectField(param.thisObject, "mManageOrHistoryButton")
                    } as Button
                    val mClearAllButton: Button = try {
                        getObjectField(param.thisObject, "mClearAllButton")
                    } catch (ignored: Throwable) {
                        getObjectField(param.thisObject, "mDismissButton")
                    } as Button

                    mManageButton.background?.alpha = (INACTIVE_ALPHA * 255).toInt()
                    mClearAllButton.background?.alpha = (INACTIVE_ALPHA * 255).toInt()
                } catch (throwable: Throwable) {
                    log(TAG + throwable)
                }
            }
        }

        hookAllMethods(footerViewClass, "onFinishInflate", updateNotificationFooterButtons)

        try {
            hookAllMethods(footerViewClass, "updateColors", updateNotificationFooterButtons)
        } catch (ignored: Throwable) {
        }

        for (i in 1..3) {
            try {
                hookAllMethods(
                    footerViewClass,
                    "updateColors$${i}",
                    updateNotificationFooterButtons
                )
            } catch (ignored: Throwable) {
            }
        }

        // Power menu
        try {
            val globalActionsDialogLiteSinglePressActionClass = findClass(
                "$SYSTEMUI_PACKAGE.globalactions.GlobalActionsDialogLite\$SinglePressAction",
                loadPackageParam.classLoader
            )
            val globalActionsLayoutLiteClass = findClass(
                "$SYSTEMUI_PACKAGE.globalactions.GlobalActionsLayoutLite",
                loadPackageParam.classLoader
            )

            // Layout background
            hookAllMethods(globalActionsLayoutLiteClass, "onLayout", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!fluidPowerMenuEnabled) return

                    (param.thisObject as View).findViewById<View>(android.R.id.list)
                        .background.alpha = (INACTIVE_ALPHA * 255).toInt()
                }
            })

            // Button Color
            hookAllMethods(
                globalActionsDialogLiteSinglePressActionClass,
                "create",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!fluidPowerMenuEnabled) return

                        val itemView = param.result as View
                        val iconView = itemView.findViewById<ImageView>(android.R.id.icon)
                        iconView.background.alpha = (INACTIVE_ALPHA * 255).toInt()
                    }
                })
        } catch (ignored: Throwable) {
        }
    }

    private fun initResources() {
        val isDark: Boolean = SystemUtils.isDarkMode

        if (isDark != wasDark) {
            wasDark = isDark
        }

        colorActive = ContextCompat.getColor(mContext, android.R.color.system_accent1_400)
        colorActiveAlpha = changeAlpha(colorActive, ACTIVE_ALPHA)
    }

    private fun changeAlpha(color: Int, alpha: Float): Int {
        return changeAlpha(color, (alpha * 255).toInt())
    }

    private fun changeAlpha(color: Int, alpha: Int): Int {
        val alphaInRange = max(0.0, min(alpha.toDouble(), 255.0)).toInt()

        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        return Color.argb(alphaInRange, red, green, blue)
    }

    private fun createBrightnessDrawable(context: Context): LayerDrawable {
        val res = context.resources
        val cornerRadius = res.getDimensionPixelSize(
            res.getIdentifier(
                "rounded_slider_corner_radius",
                "dimen",
                context.packageName
            )
        )
        val height = res.getDimensionPixelSize(
            res.getIdentifier(
                "rounded_slider_height",
                "dimen",
                context.packageName
            )
        )
        val startPadding = context.toPx(15)
        val endPadding = context.toPx(15)

        // Create the background shape
        val radiusF = FloatArray(8)
        for (i in 0..7) {
            radiusF[i] = cornerRadius.toFloat()
        }

        val backgroundShape = ShapeDrawable(RoundRectShape(radiusF, null, null))
        backgroundShape.intrinsicHeight = height
        backgroundShape.alpha = (BRIGHTNESS_BAR_BACKGROUND_ALPHA * 255).toInt()
        backgroundShape.setTint(colorInactive)

        // Create the progress drawable
        var progressDrawable: RoundedCornerProgressDrawable? = null
        try {
            progressDrawable = RoundedCornerProgressDrawable(
                createBrightnessForegroundDrawable(context)
            )
            progressDrawable.alpha = (BRIGHTNESS_BAR_FOREGROUND_ALPHA * 255).toInt()
            progressDrawable.setTint(colorActive)
        } catch (ignored: Throwable) {
        }

        // Create the start and end drawables
        val startDrawable = ResourcesCompat.getDrawable(
            HookRes.modRes,
            R.drawable.ic_brightness_low,
            context.theme
        )
        val endDrawable = ResourcesCompat.getDrawable(
            HookRes.modRes,
            R.drawable.ic_brightness_full,
            context.theme
        )

        if (startDrawable != null && endDrawable != null) {
            startDrawable.setTint(colorActive)
            endDrawable.setTint(colorActive)
        }

        // Create the layer drawable
        val layers = arrayOf(backgroundShape, progressDrawable, startDrawable, endDrawable)
        val layerDrawable = LayerDrawable(layers)
        layerDrawable.setId(0, android.R.id.background)
        layerDrawable.setId(1, android.R.id.progress)
        layerDrawable.setLayerGravity(2, Gravity.START or Gravity.CENTER_VERTICAL)
        layerDrawable.setLayerGravity(3, Gravity.END or Gravity.CENTER_VERTICAL)
        layerDrawable.setLayerInsetStart(2, startPadding)
        layerDrawable.setLayerInsetEnd(3, endPadding)

        return layerDrawable
    }

    private fun createBrightnessForegroundDrawable(context: Context): LayerDrawable {
        val res = context.resources
        val rectangleDrawable = GradientDrawable()
        val cornerRadius = context.resources.getDimensionPixelSize(
            res.getIdentifier(
                "rounded_slider_corner_radius",
                "dimen",
                context.packageName
            )
        )

        rectangleDrawable.cornerRadius = cornerRadius.toFloat()
        rectangleDrawable.setColor(colorActive)

        val layerDrawable = LayerDrawable(arrayOf<Drawable>(rectangleDrawable))
        layerDrawable.setLayerGravity(0, Gravity.FILL_HORIZONTAL or Gravity.CENTER)

        val height = context.toPx(48)
        layerDrawable.setLayerSize(0, layerDrawable.getLayerWidth(0), height)

        return layerDrawable
    }

    companion object {
        private val TAG = "Iconify - ${QSFluidThemeA14::class.java.simpleName}: "
        private const val ACTIVE_ALPHA = 0.2f
        private const val INACTIVE_ALPHA = 0.4f
        private const val UNAVAILABLE_ALPHA = 0.3f
        private const val BRIGHTNESS_BAR_BACKGROUND_ALPHA = 0.3f
        private const val BRIGHTNESS_BAR_FOREGROUND_ALPHA = 0.2f
        private const val PM_LITE_BACKGROUND_CODE = 1
        private var fluidQsThemeEnabled = false
        private var fluidNotificationEnabled = false
        private var fluidPowerMenuEnabled = false
    }
}