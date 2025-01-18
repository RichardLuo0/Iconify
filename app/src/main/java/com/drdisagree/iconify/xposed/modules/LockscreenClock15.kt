package com.drdisagree.iconify.xposed.modules

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextClock
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.drdisagree.iconify.BuildConfig
import com.drdisagree.iconify.R
import com.drdisagree.iconify.common.Const.RESET_LOCKSCREEN_CLOCK_COMMAND
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.DEPTH_WALLPAPER_SWITCH
import com.drdisagree.iconify.common.Preferences.ICONIFY_LOCKSCREEN_CLOCK_TAG
import com.drdisagree.iconify.common.Preferences.LSCLOCK_BOTTOMMARGIN
import com.drdisagree.iconify.common.Preferences.LSCLOCK_COLOR_CODE_ACCENT1
import com.drdisagree.iconify.common.Preferences.LSCLOCK_COLOR_CODE_ACCENT2
import com.drdisagree.iconify.common.Preferences.LSCLOCK_COLOR_CODE_ACCENT3
import com.drdisagree.iconify.common.Preferences.LSCLOCK_COLOR_CODE_TEXT1
import com.drdisagree.iconify.common.Preferences.LSCLOCK_COLOR_CODE_TEXT2
import com.drdisagree.iconify.common.Preferences.LSCLOCK_COLOR_SWITCH
import com.drdisagree.iconify.common.Preferences.LSCLOCK_DEVICENAME
import com.drdisagree.iconify.common.Preferences.LSCLOCK_FONT_LINEHEIGHT
import com.drdisagree.iconify.common.Preferences.LSCLOCK_FONT_SWITCH
import com.drdisagree.iconify.common.Preferences.LSCLOCK_FONT_TEXT_SCALING
import com.drdisagree.iconify.common.Preferences.LSCLOCK_STYLE
import com.drdisagree.iconify.common.Preferences.LSCLOCK_SWITCH
import com.drdisagree.iconify.common.Preferences.LSCLOCK_TOPMARGIN
import com.drdisagree.iconify.common.Preferences.LSCLOCK_USERNAME
import com.drdisagree.iconify.common.Resources.LOCKSCREEN_CLOCK_LAYOUT
import com.drdisagree.iconify.utils.TextUtils
import com.drdisagree.iconify.xposed.HookEntry.Companion.enqueueProxyCommand
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.MyConstraintSet.Companion.applyTo
import com.drdisagree.iconify.xposed.modules.utils.MyConstraintSet.Companion.clear
import com.drdisagree.iconify.xposed.modules.utils.MyConstraintSet.Companion.clone
import com.drdisagree.iconify.xposed.modules.utils.MyConstraintSet.Companion.connect
import com.drdisagree.iconify.xposed.modules.utils.MyConstraintSet.Companion.constraintSetInstance
import com.drdisagree.iconify.xposed.modules.utils.TimeUtils
import com.drdisagree.iconify.xposed.modules.utils.TimeUtils.isSecurityPatchAfter
import com.drdisagree.iconify.xposed.modules.utils.ViewHelper.applyFontRecursively
import com.drdisagree.iconify.xposed.modules.utils.ViewHelper.applyTextMarginRecursively
import com.drdisagree.iconify.xposed.modules.utils.ViewHelper.applyTextScalingRecursively
import com.drdisagree.iconify.xposed.modules.utils.ViewHelper.findViewContainsTag
import com.drdisagree.iconify.xposed.modules.utils.ViewHelper.findViewWithTagAndChangeColor
import com.drdisagree.iconify.xposed.modules.utils.ViewHelper.toPx
import com.drdisagree.iconify.xposed.modules.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.views.ArcProgressWidget.generateBitmap
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.File
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SuppressLint("DiscouragedApi")
class LockscreenClock15(context: Context) : ModPack(context) {

    private var showLockscreenClock = false
    private var showDepthWallpaper = false // was used in android 13 and below
    private var mClockViewContainer: ViewGroup? = null
    private var mUserManager: UserManager? = null
    private var mAudioManager: AudioManager? = null
    private var mActivityManager: ActivityManager? = null
    private var appContext: Context? = null
    private var mBatteryStatusView: TextView? = null
    private var mBatteryLevelView: TextView? = null
    private var mVolumeLevelView: TextView? = null
    private var mBatteryProgress: ProgressBar? = null
    private var mVolumeProgress: ProgressBar? = null
    private var mBatteryStatus = 1
    private var mBatteryPercentage = 1
    private var mVolumeLevelArcProgress: ImageView? = null
    private var mRamUsageArcProgress: ImageView? = null
    private val mBatteryReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != null && intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)

                mBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 1)
                mBatteryPercentage = level * 100 / scale

                initBatteryStatus()
            }
        }
    }
    private val mVolumeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            initSoundManager()
        }
    }
    private var currentClockLayout: View? = null
    private var mAccentColor1 = 0
    private var mAccentColor2 = 0
    private var mAccentColor3 = 0
    private var mTextColor1 = 0
    private var mTextColor2 = 0
    private var clockStyle = 0
    private var topMargin = 100
    private var bottomMargin = 40
    private var textScaleFactor = 1f
    private var lineHeight = 0
    private var customColorEnabled = false
    private var customUserName = ""
    private var customDeviceName = ""
    private var customFontEnabled = false
    private var customTypeface: Typeface? = null
    private val customFontDirectory =
        "${Environment.getExternalStorageDirectory()}/.iconify_files/lsclock_font.ttf"

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized || !isComposeLockscreen) return

        Xprefs.apply {
            showLockscreenClock = getBoolean(LSCLOCK_SWITCH, false)
            showDepthWallpaper = getBoolean(DEPTH_WALLPAPER_SWITCH, false)
            clockStyle = getInt(LSCLOCK_STYLE, 0)
            topMargin = getSliderInt(LSCLOCK_TOPMARGIN, 100)
            bottomMargin = getSliderInt(LSCLOCK_BOTTOMMARGIN, 40)
            textScaleFactor = getSliderInt(LSCLOCK_FONT_TEXT_SCALING, 10) / 10.0f
            lineHeight = getSliderInt(LSCLOCK_FONT_LINEHEIGHT, 0)
            customColorEnabled = getBoolean(LSCLOCK_COLOR_SWITCH, false)
            customUserName = getString(LSCLOCK_USERNAME, "")!!
            customDeviceName = getString(LSCLOCK_DEVICENAME, "")!!
            customFontEnabled = getBoolean(LSCLOCK_FONT_SWITCH, false)
            if (customFontEnabled && File(customFontDirectory).exists()) {
                customTypeface = Typeface.createFromFile(File(customFontDirectory))
            }

            mAccentColor1 = getInt(
                LSCLOCK_COLOR_CODE_ACCENT1,
                mContext.resources.getColor(
                    mContext.resources.getIdentifier(
                        "android:color/system_accent1_300",
                        "color",
                        mContext.packageName
                    ), mContext.theme
                )
            )
            mAccentColor2 = getInt(
                LSCLOCK_COLOR_CODE_ACCENT2,
                mContext.resources.getColor(
                    mContext.resources.getIdentifier(
                        "android:color/system_accent2_300",
                        "color",
                        mContext.packageName
                    ), mContext.theme
                )
            )
            mAccentColor3 = getInt(
                LSCLOCK_COLOR_CODE_ACCENT3,
                mContext.resources.getColor(
                    mContext.resources.getIdentifier(
                        "android:color/system_accent3_300",
                        "color",
                        mContext.packageName
                    ), mContext.theme
                )
            )
            mTextColor1 = getInt(LSCLOCK_COLOR_CODE_TEXT1, Color.WHITE)
            mTextColor2 = getInt(LSCLOCK_COLOR_CODE_TEXT2, Color.BLACK)
        }

        resetStockClock()

        when (key.firstOrNull()) {
            in setOf(
                LSCLOCK_SWITCH,
                LSCLOCK_COLOR_SWITCH,
                LSCLOCK_COLOR_CODE_ACCENT1,
                LSCLOCK_COLOR_CODE_ACCENT2,
                LSCLOCK_COLOR_CODE_ACCENT3,
                LSCLOCK_COLOR_CODE_TEXT1,
                LSCLOCK_COLOR_CODE_TEXT2,
                LSCLOCK_STYLE,
                LSCLOCK_TOPMARGIN,
                LSCLOCK_BOTTOMMARGIN,
                LSCLOCK_FONT_LINEHEIGHT,
                LSCLOCK_FONT_SWITCH,
                LSCLOCK_FONT_TEXT_SCALING,
                LSCLOCK_USERNAME,
                LSCLOCK_DEVICENAME,
                DEPTH_WALLPAPER_SWITCH
            ) -> updateClockView()
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        if (!isComposeLockscreen) return

        initResources(mContext)

        val aodBurnInLayerClass =
            findClass("$SYSTEMUI_PACKAGE.keyguard.ui.view.layout.sections.AodBurnInLayer")

        aodBurnInLayerClass
            .hookConstructor()
            .runAfter { param ->
                if (!showLockscreenClock) return@runAfter

                val entryV = param.thisObject as View

                entryV.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!showLockscreenClock) return@postDelayed

                            val rootView = entryV.parent as ViewGroup
                            mClockViewContainer = rootView

                            // Hide stock clock
                            listOf(
                                "bc_smartspace_view",
                                "date_smartspace_view",
                                "lockscreen_clock_view",
                                "lockscreen_clock_view_large"
                            ).map { resourceName ->
                                rootView.findViewById<View?>(
                                    mContext.resources.getIdentifier(
                                        resourceName,
                                        "id",
                                        mContext.packageName
                                    )
                                )
                            }.forEach { view ->
                                view.hide()
                            }

                            registerClockUpdater()
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
            .runAfter { param2 ->
                val constraintSet = param2.args[0]

                currentClockLayout?.let { clockView ->
                    constraintSet.clear(
                        notificationContainerId,
                        ConstraintSet.TOP
                    )
                    constraintSet.connect(
                        notificationContainerId,
                        ConstraintSet.TOP,
                        clockView.id,
                        ConstraintSet.BOTTOM,
                        mContext.toPx(bottomMargin)
                    )
                }
            }

        val aodNotificationIconsSectionClass =
            findClass("$SYSTEMUI_PACKAGE.keyguard.ui.view.layout.sections.AodNotificationIconsSection")

        val aodNotificationContainerId = mContext.resources.getIdentifier(
            "aod_notification_icon_container",
            "id",
            mContext.packageName
        )

        aodNotificationIconsSectionClass
            .hookMethod("applyConstraints")
            .runAfter { param ->
                if (!showLockscreenClock) return@runAfter

                val constraintSet = param.args[0]

                currentClockLayout?.let { clockView ->
                    constraintSet.clear(
                        aodNotificationContainerId,
                        ConstraintSet.TOP
                    )
                    constraintSet.connect(
                        aodNotificationContainerId,
                        ConstraintSet.TOP,
                        clockView.id,
                        ConstraintSet.BOTTOM
                    )
                }
            }

        try {
            val executor = Executors.newSingleThreadScheduledExecutor()
            executor.scheduleAtFixedRate({
                val androidDir =
                    File(Environment.getExternalStorageDirectory().toString() + "/Android")

                if (androidDir.isDirectory) {
                    updateClockView()
                    executor.shutdown()
                    executor.shutdownNow()
                }
            }, 0, 5, TimeUnit.SECONDS)
        } catch (ignored: Throwable) {
        }
    }

    private fun initResources(context: Context) {
        try {
            appContext = context.createPackageContext(
                BuildConfig.APPLICATION_ID,
                Context.CONTEXT_IGNORE_SECURITY
            )
        } catch (ignored: PackageManager.NameNotFoundException) {
        }

        Handler(Looper.getMainLooper()).post {
            mUserManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        }
        mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mActivityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        try {
            context.registerReceiver(mBatteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (ignored: Exception) {
        }

        try {
            context.registerReceiver(
                mVolumeReceiver,
                IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            )
        } catch (ignored: Exception) {
        }
    }

    // Broadcast receiver for updating clock
    private fun registerClockUpdater() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }

        val timeChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Handler(Looper.getMainLooper()).post { updateClockView() }
            }
        }

        mContext.registerReceiver(timeChangedReceiver, filter)

        updateClockView()
    }

    private fun updateClockView() {
        if (mClockViewContainer == null) {
            currentClockLayout = null
            return
        }

        val currentTime = System.currentTimeMillis()
        val isClockAdded =
            mClockViewContainer!!.findViewWithTag<View?>(ICONIFY_LOCKSCREEN_CLOCK_TAG) != null

        if (isClockAdded && currentTime - lastUpdated < THRESHOLD_TIME) {
            return
        } else {
            lastUpdated = currentTime
        }

        // Remove existing clock view
        if (isClockAdded) {
            mClockViewContainer!!.removeView(
                mClockViewContainer!!.findViewWithTag(
                    ICONIFY_LOCKSCREEN_CLOCK_TAG
                )
            )
        }

        currentClockLayout = clockViewLayout

        currentClockLayout?.apply {
            tag = ICONIFY_LOCKSCREEN_CLOCK_TAG
            id = View.generateViewId()

            (parent as? ViewGroup)?.removeView(this)
            mClockViewContainer!!.addView(this, 0)
            layoutParams.width = 0

            modifyClockView(this)
            initSoundManager()
            initBatteryStatus()
        }
    }

    private val clockViewLayout: View?
        get() {
            if (appContext == null || !XprefsIsInitialized || !showLockscreenClock) return null

            return LayoutInflater.from(appContext).inflate(
                appContext!!.resources.getIdentifier(
                    LOCKSCREEN_CLOCK_LAYOUT + clockStyle,
                    "layout",
                    BuildConfig.APPLICATION_ID
                ),
                null
            )
        }

    private fun modifyClockView(clockView: View) {
        if (!XprefsIsInitialized) return

        applyLayoutConstraints(clockView)

        if (customColorEnabled) {
            findViewWithTagAndChangeColor(clockView, "accent1", mAccentColor1)
            findViewWithTagAndChangeColor(clockView, "accent2", mAccentColor2)
            findViewWithTagAndChangeColor(clockView, "accent3", mAccentColor3)
            findViewWithTagAndChangeColor(clockView, "text1", mTextColor1)
            findViewWithTagAndChangeColor(clockView, "text2", mTextColor2)
        }

        customTypeface?.also {
            applyFontRecursively(clockView, it)
        }

        applyTextMarginRecursively(mContext, clockView, lineHeight)

        if (clockStyle != 10) {
            TextUtils.convertTextViewsToTitleCase(clockView)
        }

        when (clockStyle) {
            5 -> {
                mBatteryStatusView = clockView.findViewContainsTag("battery_status") as TextView?
                mBatteryLevelView = clockView.findViewContainsTag("battery_percentage") as TextView?
                mVolumeLevelView = clockView.findViewContainsTag("volume_level") as TextView?
                mBatteryProgress =
                    clockView.findViewContainsTag("battery_progressbar") as ProgressBar?
                mVolumeProgress =
                    clockView.findViewContainsTag("volume_progressbar") as ProgressBar?
            }

            19 -> {
                mBatteryLevelView = clockView.findViewContainsTag("battery_percentage") as TextView?
                mBatteryProgress =
                    clockView.findViewContainsTag("battery_progressbar") as ProgressBar?
                mVolumeLevelArcProgress =
                    clockView.findViewContainsTag("volume_progress") as ImageView?
                mRamUsageArcProgress = clockView.findViewContainsTag("ram_usage_info") as ImageView?
            }

            22 -> {
                val hourView = clockView.findViewContainsTag("textHour") as TextView
                val minuteView = clockView.findViewContainsTag("textMinute") as TextView
                val tickIndicator = clockView.findViewContainsTag("tickIndicator") as TextClock

                TimeUtils.setCurrentTimeTextClock(mContext, tickIndicator, hourView, minuteView)
            }

            else -> {
                mBatteryStatusView = null
                mBatteryLevelView = null
                mVolumeLevelView = null
                mBatteryProgress = null
                mVolumeProgress = null
            }
        }

        val deviceName = clockView.findViewContainsTag("device_name") as TextView?
        deviceName?.text = customDeviceName.ifEmpty { Build.MODEL }

        val usernameView = clockView.findViewContainsTag("username") as TextView?
        usernameView?.text = customUserName.ifEmpty { userName }

        val imageView = clockView.findViewContainsTag("profile_picture") as ImageView?
        userImage?.let { imageView?.setImageDrawable(it) }

        if (textScaleFactor != 1f) {
            applyTextScalingRecursively(clockView, textScaleFactor)

            val reduceFactor = if (textScaleFactor > 1f) 0.91f else 1f

            mVolumeLevelArcProgress?.layoutParams?.apply {
                width = (width * textScaleFactor * reduceFactor).toInt()
                height = (height * textScaleFactor * reduceFactor).toInt()
            }

            mRamUsageArcProgress?.layoutParams?.apply {
                width = (width * textScaleFactor * reduceFactor).toInt()
                height = (height * textScaleFactor * reduceFactor).toInt()
            }

            (mBatteryProgress?.parent as ViewGroup?)?.apply {
                layoutParams?.apply {
                    width = (width * textScaleFactor * reduceFactor).toInt()
                }
                requestLayout()
            }
        }
    }

    private fun applyLayoutConstraints(clockView: View) {
        assignIdsToViews(mClockViewContainer!!)

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
            constraintSet.clone(mClockViewContainer!!)

            // Connect clock view to parent
            constraintSet.connect(
                clockView.id,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            constraintSet.connect(
                clockView.id,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            constraintSet.connect(
                clockView.id,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP,
                mContext.toPx(topMargin)
            )

            // Connect notification container below clock
            if (notificationContainerId != 0) {
                constraintSet.clear(notificationContainerId, ConstraintSet.TOP)
                constraintSet.connect(
                    notificationContainerId,
                    ConstraintSet.TOP,
                    clockView.id,
                    ConstraintSet.BOTTOM,
                    mContext.toPx(bottomMargin)
                )
            }

            // Connect aod notification icon container below clock
            if (aodNotificationIconContainerId != 0) {
                constraintSet.clear(aodNotificationIconContainerId, ConstraintSet.TOP)
                constraintSet.connect(
                    aodNotificationIconContainerId,
                    ConstraintSet.TOP,
                    clockView.id,
                    ConstraintSet.BOTTOM,
                    mContext.toPx(bottomMargin)
                )
            }

            constraintSet.applyTo(mClockViewContainer!!)
        }
    }

    private fun initBatteryStatus() {
        if (mBatteryStatusView != null) {
            when (mBatteryStatus) {
                BatteryManager.BATTERY_STATUS_CHARGING -> {
                    mBatteryStatusView!!.setText(R.string.battery_charging)
                }

                BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> {
                    mBatteryStatusView!!.setText(R.string.battery_discharging)
                }

                BatteryManager.BATTERY_STATUS_FULL -> {
                    mBatteryStatusView!!.setText(R.string.battery_full)
                }

                BatteryManager.BATTERY_STATUS_UNKNOWN -> {
                    mBatteryStatusView!!.setText(R.string.battery_level_percentage)
                }
            }
        }

        if (mBatteryProgress != null) {
            mBatteryProgress!!.progress = mBatteryPercentage
        }

        if (mBatteryLevelView != null) {
            mBatteryLevelView!!.text =
                appContext!!.resources.getString(R.string.percentage_text, mBatteryPercentage)
        }

        initRamUsage()
    }

    private fun initSoundManager() {
        val volLevel = mAudioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolLevel = mAudioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volPercent = (volLevel.toFloat() / maxVolLevel * 100).toInt()
        val textScaleFactor: Float =
            (Xprefs.getSliderInt(LSCLOCK_FONT_TEXT_SCALING, 10) / 10.0).toFloat()
        val reduceFactor = if (textScaleFactor > 1f) 0.91f else 1f

        mVolumeProgress?.progress = volPercent

        mVolumeLevelView?.text =
            appContext!!.resources.getString(R.string.percentage_text, volPercent)

        mVolumeLevelArcProgress?.setImageBitmap(
            generateBitmap(
                context = mContext,
                percentage = volPercent,
                textInside = appContext!!.resources.getString(
                    R.string.percentage_text,
                    volPercent
                ),
                textInsideSizePx = (40 * textScaleFactor * reduceFactor).toInt(),
                iconDrawable = ContextCompat.getDrawable(
                    appContext!!,
                    R.drawable.ic_volume_up
                ),
                iconSizePx = 38,
                typeface = if (customFontEnabled && File(customFontDirectory).exists()) {
                    Typeface.createFromFile(File(customFontDirectory))
                } else {
                    Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
            )
        )
    }

    private fun initRamUsage() {
        if (mActivityManager == null) return

        val memoryInfo = ActivityManager.MemoryInfo()
        mActivityManager!!.getMemoryInfo(memoryInfo)
        val usedMemory = memoryInfo.totalMem - memoryInfo.availMem
        val usedMemoryPercentage = (usedMemory * 100 / memoryInfo.totalMem).toInt()
        val textScaleFactor: Float =
            (Xprefs.getSliderInt(LSCLOCK_FONT_TEXT_SCALING, 10) / 10.0).toFloat()
        val reduceFactor = if (textScaleFactor > 1f) 0.91f else 1f

        mRamUsageArcProgress?.setImageBitmap(
            generateBitmap(
                context = mContext,
                percentage = usedMemoryPercentage,
                textInside = appContext!!.resources.getString(
                    R.string.percentage_text,
                    usedMemoryPercentage
                ),
                textInsideSizePx = (40 * textScaleFactor * reduceFactor).toInt(),
                textBottom = "RAM",
                textBottomSizePx = 28,
                typeface = if (customFontEnabled && File(customFontDirectory).exists()) {
                    Typeface.createFromFile(File(customFontDirectory))
                } else {
                    Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
            )
        )
    }

    private val userName: String
        @SuppressLint("MissingPermission")
        get() {
            if (mUserManager == null) {
                return "User"
            }

            val username = mUserManager!!.userName

            return if (username.isNotEmpty()) mUserManager!!.userName
            else appContext!!.resources.getString(R.string.default_user_name)
        }

    private val userImage: Drawable?
        get() = if (mUserManager == null) {
            ResourcesCompat.getDrawable(
                appContext!!.resources,
                R.drawable.default_avatar,
                appContext!!.theme
            )
        } else try {
            val getUserIconMethod = mUserManager!!.javaClass
                .getMethod("getUserIcon", Int::class.javaPrimitiveType)
            val userId = UserHandle::class.java.getDeclaredMethod("myUserId").invoke(null) as Int
            val bitmapUserIcon = getUserIconMethod.invoke(mUserManager, userId) as Bitmap

            BitmapDrawable(mContext.resources, bitmapUserIcon)
        } catch (throwable: Throwable) {
            if (throwable !is NullPointerException) {
                log(TAG + throwable)
            }

            ResourcesCompat.getDrawable(
                appContext!!.resources,
                R.drawable.default_avatar,
                appContext!!.theme
            )
        }

    private fun resetStockClock() {
        if (showLockscreenClock) {
            enqueueProxyCommand { proxy ->
                proxy.runCommand(RESET_LOCKSCREEN_CLOCK_COMMAND)
            }
        }
    }

    private fun View?.hide() {
        if (this == null) return

        viewTreeObserver?.addOnDrawListener {
            apply {
                layoutParams.height = 0
                layoutParams.width = 0
                visibility = View.INVISIBLE
            }
        }
    }

    private fun assignIdsToViews(container: ViewGroup) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)

            if (child is ViewGroup) {
                assignIdsToViews(child)
            }

            if (child.id == View.NO_ID) {
                child.id = View.generateViewId()
            }
        }
    }

    companion object {
        private val TAG = "Iconify - ${LockscreenClock15::class.java.simpleName}: "
        private var lastUpdated = System.currentTimeMillis()
        private const val THRESHOLD_TIME: Long = 500 // milliseconds

        val isComposeLockscreen = findClass(
            "$SYSTEMUI_PACKAGE.keyguard.ui.view.layout.sections.AodBurnInLayer",
            suppressError = true
        ) != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && isSecurityPatchAfter(
            Calendar.getInstance().apply { set(2024, Calendar.NOVEMBER, 30) }
        )
    }
}