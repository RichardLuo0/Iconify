package com.drdisagree.iconify.xposed.modules

import android.annotation.SuppressLint
import android.app.Notification
import android.app.WallpaperColors
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import com.drdisagree.iconify.common.Const.FRAMEWORK_PACKAGE
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.COLORED_NOTIFICATION_ALTERNATIVE_SWITCH
import com.drdisagree.iconify.common.Preferences.COLORED_NOTIFICATION_ICON_SWITCH
import com.drdisagree.iconify.common.Preferences.COLORED_NOTIFICATION_VIEW_SWITCH
import com.drdisagree.iconify.utils.color.monet.quantize.QuantizerCelebi
import com.drdisagree.iconify.utils.color.monet.score.Score
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.utils.toolkit.getAnyField
import com.drdisagree.iconify.xposed.modules.utils.toolkit.getFieldSilently
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.utils.toolkit.isMethodAvailable
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.newInstance
import de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
@Suppress("deprecation", "UNCHECKED_CAST")
class ColorizeNotification(context: Context) : ModPack(context) {

    private var coloredNotificationIcon = false
    private var coloredNotificationView = false
    private var coloredNotificationAlternativeColor = false
    private var titleResId = 0
    private var subTextResId = 0
    private var schemeStyle: Any = "TONAL_SPOT"

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            coloredNotificationIcon = getBoolean(COLORED_NOTIFICATION_ICON_SWITCH, false)
            coloredNotificationView = getBoolean(COLORED_NOTIFICATION_VIEW_SWITCH, false)
            coloredNotificationAlternativeColor =
                getBoolean(COLORED_NOTIFICATION_ALTERNATIVE_SWITCH, false)
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val colorSchemeClass = findClass("$SYSTEMUI_PACKAGE.monet.ColorScheme")
        val monetStyleClass = findClass("$SYSTEMUI_PACKAGE.monet.Style")!!
        val expandableNotificationRowClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.ExpandableNotificationRow")
        val notificationBackgroundViewClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.NotificationBackgroundView")
        val notificationViewWrapperClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.wrapper.NotificationViewWrapper")
        val notificationContentViewClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.NotificationContentView")
        val notificationContentInflaterClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.NotificationContentInflater")
        val notificationHeaderViewWrapperClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper")

        val styles: Array<out Any> = monetStyleClass.getEnumConstants()!!
        for (style in styles) {
            if (style.toString().contains("CONTENT")) {
                schemeStyle = style
                break
            }
        }

        findClass("android.app.Notification\$Builder")
            .hookConstructor()
            .parameters(
                Context::class.java,
                Notification::class.java
            )
            .runAfter { param ->
                if (!coloredNotificationView) return@runAfter

                val mN = param.args[1] as? Notification ?: return@runAfter

                if (getAdditionalInstanceField(mN, "mPrimaryTextColor") != null) {
                    val builder: Any = param.thisObject

                    val mParams = getObjectField(builder, "mParams")
                    callMethod(builder, "getColors", mParams)

                    val mColors = getObjectField(builder, "mColors")

                    listOf(
                        "mProtectionColor",
                        "mPrimaryTextColor",
                        "mSecondaryTextColor",
                        "mPrimaryAccentColor",
                        "mSecondaryAccentColor",
                        "mTertiaryAccentColor",
                        "mOnTertiaryAccentTextColor",
                        "mTertiaryFixedDimAccentColor",
                        "mOnTertiaryFixedAccentTextColor"
                    ).forEach { fieldName ->
                        setObjectField(
                            mColors,
                            fieldName,
                            getAdditionalInstanceField(mN, fieldName)
                        )
                    }
                }
            }

        expandableNotificationRowClass
            .hookMethod("onNotificationUpdated")
            .runAfter { param ->
                if (!coloredNotificationView) return@runAfter

                val mEntry = getFieldSilently(param.thisObject, "mEntry") ?: return@runAfter

                val mSbn = getObjectField(mEntry, "mSbn")
                val notification = callMethod(mSbn, "getNotification") as Notification

                val overflowColor = getAdditionalInstanceField(notification, "mSecondaryTextColor")
                if (overflowColor != null) {
                    setObjectField(param.thisObject, "mNotificationColor", overflowColor)
                }

                val mNotifyBackgroundColor = getAdditionalInstanceField(
                    notification,
                    "mNotifyBackgroundColor"
                )
                if (mNotifyBackgroundColor != null) {
                    var bgColor = mNotifyBackgroundColor as Int
                    val mCurrentBackgroundTint = try {
                        callMethod(param.thisObject, "getCurrentBackgroundTint")
                    } catch (ignore: Throwable) {
                        getObjectField(param.thisObject, "mCurrentBackgroundTint")
                    } as Int

                    if (mCurrentBackgroundTint != bgColor) {
                        bgColor = Color.argb(
                            255,
                            Color.red(bgColor),
                            Color.green(bgColor),
                            Color.blue(bgColor)
                        )
                        callMethod(param.thisObject, "setBackgroundTintColor", bgColor)

                        try {
                            setObjectField(param.thisObject, "mCurrentBackgroundTint", bgColor)
                        } catch (ignored: Throwable) {
                        }

                        val notificationBackgroundView = getObjectField(
                            param.thisObject,
                            "mBackgroundNormal"
                        ) as View

                        try {
                            val bgDrawable = getObjectField(
                                notificationBackgroundView,
                                "mBackground"
                            ) as Drawable
                            DrawableCompat.setTint(bgDrawable, bgColor)
                        } catch (ignored: Throwable) {
                        }

                        try {
                            setObjectField(notificationBackgroundView, "mTintColor", bgColor)
                        } catch (ignored: Throwable) {
                        }

                        Handler(Looper.getMainLooper()).post {
                            notificationBackgroundView.invalidate()
                        }
                    }
                }
            }

        notificationBackgroundViewClass
            .hookMethod("setTint")
            .parameters(Int::class.javaPrimitiveType)
            .runBefore { param ->
                if (!coloredNotificationView) return@runBefore

                if (param.args[0] as Int == 0) {
                    param.result = null
                }
            }

        notificationViewWrapperClass
            .hookMethod("getCustomBackgroundColor")
            .runBefore { param ->
                if (!coloredNotificationView) return@runBefore

                param.result = getObjectField(param.thisObject, "mBackgroundColor")
            }

        notificationContentViewClass
            .hookMethod("updateAllSingleLineViews")
            .runAfter { param ->
                if (!coloredNotificationView) return@runAfter

                if (param.thisObject == null) return@runAfter

                val mEntry =
                    getFieldSilently(param.thisObject, "mNotificationEntry") ?: return@runAfter
                val singleLineView =
                    getFieldSilently(param.thisObject, "mSingleLineView") ?: return@runAfter

                val mSbn = getObjectField(mEntry, "mSbn")
                val mN = callMethod(mSbn, "getNotification") as Notification

                if (getAdditionalInstanceField(mN, "mSecondaryTextColor") != null) {
                    val hybridNotificationView = singleLineView as LinearLayout

                    val mTitleView =
                        getObjectField(hybridNotificationView, "mTitleView") as TextView
                    mTitleView.setTextColor(
                        getAdditionalInstanceField(
                            mN,
                            "mPrimaryTextColor"
                        ) as Int
                    )

                    val mTextView = getObjectField(hybridNotificationView, "mTextView") as TextView
                    mTextView.setTextColor(
                        getAdditionalInstanceField(
                            mN,
                            "mSecondaryTextColor"
                        ) as Int
                    )
                }
            }

        fun initializeNotificationColors(
            mN: Notification,
            builder: Notification.Builder,
            packageContext: Context = mContext
        ) {
            if (callMethod(mN, "isColorized") as Boolean) return
            if (callMethod(mN, "isMediaNotification") as Boolean) return

            val applicationInfo =
                mN.extras.getParcelable<ApplicationInfo>("android.appInfo")
                    ?: return
            val pkgName = applicationInfo.packageName

            if (pkgName == FRAMEWORK_PACKAGE) return

            val fallbackColor = mContext.resources.getColor(
                mContext.resources.getIdentifier(
                    "android:color/system_accent1_600",
                    "color",
                    mContext.packageName
                ), mContext.theme
            )

            val packageManager = packageContext.packageManager
            val notifyIcon = try {
                packageManager.getApplicationIcon(pkgName)
            } catch (ignored: Throwable) {
                ColorDrawable(fallbackColor)
            }

            callMethod(builder, "makeNotificationGroupHeader")

            val wallpaperColors: WallpaperColors?
            var primaryColor: Int?

            if (!coloredNotificationAlternativeColor) { // Use WallpaperColors to get the primary color
                wallpaperColors = WallpaperColors.fromDrawable(notifyIcon)
                primaryColor = wallpaperColors.primaryColor.toArgb()
            } else { // Use Monet Score and Quantizer to get the primary color
                val bitmap = notifyIcon.drawableToBitmap()
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                primaryColor = Score.score(QuantizerCelebi.quantize(pixels, 25)).firstOrNull()
                    ?: fallbackColor
                wallpaperColors = WallpaperColors.fromDrawable(ColorDrawable(primaryColor))
            }

            if (Color.luminance(primaryColor) > 0.9) {
                wallpaperColors.secondaryColor?.let {
                    primaryColor = it.toArgb()
                }
            }

            val darkTheme = packageContext.resources.configuration.isNightModeActive
            val colorScheme = try {
                newInstance(
                    colorSchemeClass,
                    primaryColor,
                    darkTheme,
                    schemeStyle
                )
            } catch (ignored: NoSuchMethodError) {
                newInstance(
                    colorSchemeClass,
                    wallpaperColors,
                    darkTheme,
                    schemeStyle
                )
            }

            val paletteAccent1 = getAnyField(colorScheme, "accent1", "mAccent1")
            val paletteAccent2 = getAnyField(colorScheme, "accent2", "mAccent2")
            val paletteAccent3 = getAnyField(colorScheme, "accent3", "mAccent3")
            val paletteNeutral1 = getAnyField(colorScheme, "neutral1", "mNeutral1")
            val paletteNeutral2 = getAnyField(colorScheme, "neutral2", "mNeutral2")

            val accent1 = getObjectField(paletteAccent1, "allShades") as List<Int>
            val accent2 = getObjectField(paletteAccent2, "allShades") as List<Int>
            val accent3 = getObjectField(paletteAccent3, "allShades") as List<Int>
            val neutral1 = getObjectField(paletteNeutral1, "allShades") as List<Int>
            val neutral2 = getObjectField(paletteNeutral2, "allShades") as List<Int>

            val bgColor = accent1[if (darkTheme) 10 else 2]
            val bgColorInverse = (if (darkTheme) 0xFFFFFFFF else 0xFF000000).toInt()
            val mParams = getObjectField(builder, "mParams")

            callMethod(mParams, "reset")
            callMethod(builder, "getColors", mParams)

            val mColors = getObjectField(builder, "mColors")
            val mProtectionColor = ColorUtils.blendARGB(bgColor, bgColorInverse, 0.15f)
            val mPrimaryTextColor = neutral1[if (darkTheme) 1 else 11]
            val mSecondaryTextColor = neutral2[if (darkTheme) 2 else 10]
            val mPrimaryAccentColor = accent1[if (darkTheme) 3 else 8]
            val mSecondaryAccentColor = accent2[if (darkTheme) 3 else 8]
            val mTertiaryAccentColor = accent3[if (darkTheme) 3 else 8]
            val mOnTertiaryAccentTextColor = accent3[if (darkTheme) 10 else 1]
            val mTertiaryFixedDimAccentColor = accent3[4]
            val mOnTertiaryFixedAccentTextColor = accent3[10]

            mapOf(
                "mBackgroundColor" to bgColor,
                "mProtectionColor" to mProtectionColor,
                "mPrimaryTextColor" to mPrimaryTextColor,
                "mSecondaryTextColor" to mSecondaryTextColor,
                "mPrimaryAccentColor" to mPrimaryAccentColor,
                "mSecondaryAccentColor" to mSecondaryAccentColor,
                "mTertiaryAccentColor" to mTertiaryAccentColor,
                "mOnTertiaryAccentTextColor" to mOnTertiaryAccentTextColor,
                "mTertiaryFixedDimAccentColor" to mTertiaryFixedDimAccentColor,
                "mOnTertiaryFixedAccentTextColor" to mOnTertiaryFixedAccentTextColor
            ).forEach { (fieldName, value) ->
                setObjectField(mColors, fieldName, value)

                if (fieldName == "mBackgroundColor") {
                    setAdditionalInstanceField(mN, "mNotifyBackgroundColor", value)
                } else {
                    setAdditionalInstanceField(mN, fieldName, value)
                }
            }
        }

        fun setNotificationTextColor(
            notification: Notification,
            inflationProgress: Any,
            packageContext: Context = mContext
        ) {
            if (titleResId == 0) {
                titleResId = packageContext.resources.getIdentifier(
                    "notification_title",
                    "id",
                    SYSTEMUI_PACKAGE
                )
                subTextResId = packageContext.resources.getIdentifier(
                    "notification_text",
                    "id",
                    SYSTEMUI_PACKAGE
                )
            }

            listOf(
                "newPublicView",
                "newContentView",
                "newExpandedView",
                "newHeadsUpView"
            ).forEach { contentType ->
                val baseContent = getObjectField(inflationProgress, contentType) as? RemoteViews

                if (baseContent != null && titleResId != 0 &&
                    getAdditionalInstanceField(notification, "mPrimaryTextColor") != null
                ) {
                    baseContent.setTextColor(
                        titleResId,
                        getAdditionalInstanceField(
                            notification,
                            "mPrimaryTextColor"
                        ) as Int
                    )
                    baseContent.setTextColor(
                        subTextResId,
                        getAdditionalInstanceField(
                            notification,
                            "mSecondaryTextColor"
                        ) as Int
                    )
                }
            }
        }

        if (isMethodAvailable(notificationContentInflaterClass, "createRemoteViews")) {
            notificationContentInflaterClass
                .hookMethod("createRemoteViews")
                .runBefore { param ->
                    if (!coloredNotificationView) return@runBefore

                    val builder = param.args[1] as Notification.Builder
                    val mN = getObjectField(builder, "mN") as Notification
                    val mContext = param.args[5] as Context

                    initializeNotificationColors(mN, builder, mContext)
                }
                .runAfter { param ->
                    if (!coloredNotificationView) return@runAfter

                    val builder = param.args[1] as Notification.Builder
                    val notification: Notification = builder.notification

                    if (callMethod(
                            notification,
                            "isMediaNotification"
                        ) as Boolean
                    ) return@runAfter

                    val inflationProgress: Any = param.result
                    val mContext = param.args[5] as Context

                    setNotificationTextColor(notification, inflationProgress, mContext)
                }
        } else {
            findClass("android.app.Notification\$Builder")
                .hookMethod("recoverBuilder")
                .runAfter { param ->
                    if (!coloredNotificationView) return@runAfter

                    val builder = param.result as Notification.Builder
                    val mN = getObjectField(builder, "mN") as Notification

                    initializeNotificationColors(mN, builder)
                }
        }

        notificationHeaderViewWrapperClass
            .hookMethod("onContentUpdated")
            .runAfter { param ->
                if (!coloredNotificationIcon) return@runAfter

                val row = param.args[0]
                val notifyEntries = try {
                    callMethod(row, "getEntry")
                } catch (ignored: Throwable) {
                    getObjectField(row, "mEntry")
                }
                val notifySbn = try {
                    callMethod(notifyEntries, "getSbn")
                } catch (ignored: Throwable) {
                    getObjectField(notifyEntries, "mSbn")
                }
                val notification = callMethod(notifySbn, "getNotification") as Notification
                val pkgName = callMethod(notifySbn, "getPackageName") as? String ?: return@runAfter
                val appIcon: Drawable = try {
                    mContext.packageManager.getApplicationIcon(pkgName)
                } catch (ignored: Throwable) {
                    return@runAfter
                }
                val mIcon = getFieldSilently(param.thisObject, "mIcon") as ImageView
                val mWorkProfileImage =
                    getFieldSilently(param.thisObject, "mWorkProfileImage") as? ImageView
                val mImageTransformStateIconTag = mContext.resources.getIdentifier(
                    "image_icon_tag",
                    "id",
                    SYSTEMUI_PACKAGE
                )

                if (mWorkProfileImage != null) {
                    mIcon.setImageDrawable(appIcon);
                    mWorkProfileImage.setImageIcon(notification.smallIcon);
                    // The work profile image is always the same
                    // Lets just set the icon tag for it not to animate
                    mWorkProfileImage.setTag(
                        mImageTransformStateIconTag,
                        notification.smallIcon
                    )
                }
                mIcon.setTag(mImageTransformStateIconTag, notification.smallIcon);
            }
    }

    private fun Drawable.drawableToBitmap(): Bitmap {
        return if (this is BitmapDrawable && bitmap != null) {
            bitmap
        } else {
            val bitmap = Bitmap.createBitmap(
                intrinsicWidth.coerceAtLeast(1),
                intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            bitmap
        }
    }
}