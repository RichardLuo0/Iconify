package com.drdisagree.iconify.xposed.modules.quicksettings

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.drdisagree.iconify.common.Const.FRAMEWORK_PACKAGE
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.ICONIFY_QS_HEADER_CONTAINER_SHADE_TAG
import com.drdisagree.iconify.common.Preferences.ICONIFY_QS_HEADER_CONTAINER_TAG
import com.drdisagree.iconify.common.Preferences.OP_QS_HEADER_BLUR_LEVEL
import com.drdisagree.iconify.common.Preferences.OP_QS_HEADER_EXPANSION_Y
import com.drdisagree.iconify.common.Preferences.OP_QS_HEADER_GAP_EXPANDED
import com.drdisagree.iconify.common.Preferences.OP_QS_HEADER_HIDE_STOCK_MEDIA
import com.drdisagree.iconify.common.Preferences.OP_QS_HEADER_SWITCH
import com.drdisagree.iconify.common.Preferences.OP_QS_HEADER_TOP_MARGIN
import com.drdisagree.iconify.common.Preferences.OP_QS_HEADER_VIBRATE
import com.drdisagree.iconify.common.Preferences.QS_TEXT_ALWAYS_WHITE
import com.drdisagree.iconify.common.Preferences.QS_TEXT_FOLLOW_ACCENT
import com.drdisagree.iconify.utils.color.monet.quantize.QuantizerCelebi
import com.drdisagree.iconify.utils.color.monet.score.Score
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.callbacks.ControllersProvider
import com.drdisagree.iconify.xposed.modules.extras.utils.ActivityLauncherUtils
import com.drdisagree.iconify.xposed.modules.extras.utils.SettingsLibUtils.Companion.getColorAttrDefaultColor
import com.drdisagree.iconify.xposed.modules.extras.utils.TouchAnimator
import com.drdisagree.iconify.xposed.modules.extras.utils.VibrationUtils
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.applyBlur
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.toPx
import com.drdisagree.iconify.xposed.modules.extras.utils.isQsTileOverlayEnabled
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getFieldSilently
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.isMethodAvailable
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setField
import com.drdisagree.iconify.xposed.modules.extras.views.MediaPlayerPagerAdapter
import com.drdisagree.iconify.xposed.modules.extras.views.OpQsHeaderView
import com.drdisagree.iconify.xposed.modules.extras.views.OpQsMediaPlayerView
import com.drdisagree.iconify.xposed.modules.extras.views.OpQsMediaPlayerView.Companion.opMediaDefaultBackground
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.reflect.Method
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.properties.Delegates
import kotlin.random.Random

@Suppress("DiscouragedApi")
class OpQsHeader(context: Context) : ModPack(context) {

    // Preferences
    private var showOpQsHeaderView = false
    private var vibrateOnClick = false
    private var hideStockMediaPlayer = false
    private var mediaBlurLevel = 10f
    private var topMarginValue = 0
    private var expansionAmount = 0
    private var qsTextAlwaysWhite = false
    private var qsTextFollowAccent = false
    private var expandedQsGap = 0

    // Views
    private var mQsHeaderContainer: LinearLayout = LinearLayout(mContext)
    private var mQsHeaderContainerShade: LinearLayout = LinearLayout(mContext).apply {
        tag = ICONIFY_QS_HEADER_CONTAINER_SHADE_TAG
    }
    private var mQsPanelView: ViewGroup? = null
    private var mQuickStatusBarHeader: FrameLayout? = null
    private var mQQSContainerAnimator: TouchAnimator? = null
    private lateinit var mHeaderQsPanel: LinearLayout
    private var mOpQsHeaderView: OpQsHeaderView? = null

    // Colors
    private var colorActive: Int? = null
    private var colorInactive: Int? = null
    private var colorLabelActive: Int? = null
    private var colorLabelInactive: Int? = null
    private var colorAccent by Delegates.notNull<Int>()
    private var colorPrimary by Delegates.notNull<Int>()

    // Media data
    private val mActiveMediaControllers = mutableListOf<Pair<String, MediaController>>()
    private val mPrevMediaPlayingState = mutableMapOf<String, Boolean>()
    private val mMediaControllerMetadataMap = mutableMapOf<String, MediaMetadata?>()
    private val mPrevMediaControllerMetadataMap = mutableMapOf<String, MediaMetadata?>()
    private val mPrevMediaArtworkMap = mutableMapOf<String, Bitmap?>()
    private val mPrevMediaProcessedArtworkMap = mutableMapOf<String, Bitmap?>()
    private val mMediaPlayerViews = mutableListOf<Pair<String?, OpQsMediaPlayerView>>().also {
        it.add(null to OpQsMediaPlayerView(mContext))
    }
    private var mMediaPlayerAdapter: MediaPlayerPagerAdapter? = null

    // Tile and media state
    private var mInternetEnabled = false
    private var mBluetoothEnabled = false

    // Misc
    private var mHandler: Handler = Handler(Looper.getMainLooper())
    private val artworkExtractorScope = CoroutineScope(Dispatchers.IO + Job())
    private var mMediaUpdater = CoroutineScope(Dispatchers.Main)
    private var mMediaUpdaterJob: Job? = null
    private var mActivityStarter: Any? = null
    private var mMediaOutputDialogFactory: Any? = null
    private var mNotificationMediaManager: Any? = null
    private var mBluetoothController: Any? = null
    private var qsTileViewImplInstance: Any? = null
    private lateinit var mConnectivityManager: ConnectivityManager
    private lateinit var mTelephonyManager: TelephonyManager
    private lateinit var mWifiManager: WifiManager
    private lateinit var mBluetoothManager: BluetoothManager
    private lateinit var mMediaSessionManager: MediaSessionManager
    private lateinit var mSubscriptionManager: SubscriptionManager
    private lateinit var mActivityLauncherUtils: ActivityLauncherUtils

    // Resources
    private var qqsTileHeight by Delegates.notNull<Int>()
    private var qsTileMarginVertical by Delegates.notNull<Int>()
    private var qsTileCornerRadius by Delegates.notNull<Float>()
    private lateinit var opMediaBackgroundDrawable: Drawable
    private var previousBlurLevel = 10f

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            showOpQsHeaderView = getBoolean(OP_QS_HEADER_SWITCH, false)
            vibrateOnClick = getBoolean(OP_QS_HEADER_VIBRATE, false)
            hideStockMediaPlayer = getBoolean(OP_QS_HEADER_HIDE_STOCK_MEDIA, false)
            mediaBlurLevel = getSliderInt(OP_QS_HEADER_BLUR_LEVEL, 10).toFloat()
            topMarginValue = getSliderInt(OP_QS_HEADER_TOP_MARGIN, 0)
            expansionAmount = getSliderInt(OP_QS_HEADER_EXPANSION_Y, 0)
            expandedQsGap = getSliderInt(OP_QS_HEADER_GAP_EXPANDED, 0)
            qsTextAlwaysWhite = getBoolean(QS_TEXT_ALWAYS_WHITE, false)
            qsTextFollowAccent = getBoolean(QS_TEXT_FOLLOW_ACCENT, false)
        }

        when (key.firstOrNull()) {
            in setOf(
                OP_QS_HEADER_VIBRATE,
                OP_QS_HEADER_BLUR_LEVEL
            ) -> updateMediaPlayers(force = true)
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val qsPanelClass = findClass("$SYSTEMUI_PACKAGE.qs.QSPanel")!!
        val quickQsPanelClass = findClass("$SYSTEMUI_PACKAGE.qs.QuickQSPanel")!!
        val qsImplClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.QSImpl",
            "$SYSTEMUI_PACKAGE.qs.QSFragment"
        )
        val qsContainerImplClass = findClass("$SYSTEMUI_PACKAGE.qs.QSContainerImpl")
        val qsTileViewImplClass = findClass("$SYSTEMUI_PACKAGE.qs.tileimpl.QSTileViewImpl")
        val tileLayoutClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.TileLayout",
            "$SYSTEMUI_PACKAGE.qs.PagedTileLayout"
        )
        val qsPanelControllerBaseClass = findClass("$SYSTEMUI_PACKAGE.qs.QSPanelControllerBase")
        val qsSecurityFooterUtilsClass = findClass("$SYSTEMUI_PACKAGE.qs.QSSecurityFooterUtils")
        val quickStatusBarHeaderClass = findClass("$SYSTEMUI_PACKAGE.qs.QuickStatusBarHeader")
        val shadeHeaderControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.shade.ShadeHeaderController",
            "$SYSTEMUI_PACKAGE.shade.LargeScreenShadeHeaderController",
        )
        val dependencyClass = findClass("$SYSTEMUI_PACKAGE.Dependency")
        val activityStarterClass = findClass("$SYSTEMUI_PACKAGE.plugins.ActivityStarter")
        val bluetoothControllerImplClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.policy.BluetoothControllerImpl")
        val notificationMediaManagerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.NotificationMediaManager")
        val mediaControlPanelClass = findClass(
            "$SYSTEMUI_PACKAGE.media.controls.ui.controller.MediaControlPanel",
            "$SYSTEMUI_PACKAGE.media.controls.ui.MediaControlPanel"
        )
        val volumeDialogImplClass = findClass(
            "$SYSTEMUI_PACKAGE.volume.VolumeDialogImpl"
        )
        launchableImageView = findClass("$SYSTEMUI_PACKAGE.animation.view.LaunchableImageView")
        launchableLinearLayout =
            findClass("$SYSTEMUI_PACKAGE.animation.view.LaunchableLinearLayout")

        initResources()

        quickStatusBarHeaderClass
            .hookConstructor()
            .runAfter {
                try {
                    mActivityStarter =
                        callStaticMethod(dependencyClass, "get", activityStarterClass)
                    mActivityLauncherUtils = ActivityLauncherUtils(mContext, mActivityStarter)
                } catch (ignored: Throwable) {
                }
            }

        qsSecurityFooterUtilsClass
            .hookConstructor()
            .runAfter { param ->
                mActivityStarter = param.thisObject.getField("mActivityStarter")
                mActivityLauncherUtils = ActivityLauncherUtils(mContext, mActivityStarter)
            }

        bluetoothControllerImplClass
            .hookConstructor()
            .runAfter { param -> mBluetoothController = param.thisObject }

        notificationMediaManagerClass
            .hookConstructor()
            .runAfter { param -> mNotificationMediaManager = param.thisObject }

        val getMediaOutputDialog = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (mMediaOutputDialogFactory == null) {
                    mMediaOutputDialogFactory = try {
                        param.thisObject.getField("mMediaOutputDialogFactory")
                    } catch (ignored: Throwable) {
                        param.thisObject.getField("mMediaOutputDialogManager")
                    }
                }
            }
        }

        mediaControlPanelClass
            .hookConstructor()
            .run(getMediaOutputDialog)

        volumeDialogImplClass
            .hookConstructor()
            .run(getMediaOutputDialog)

        qsTileViewImplClass
            .hookMethod("init")
            .runBefore { param -> qsTileViewImplInstance = param.thisObject }

        tileLayoutClass
            .hookConstructor()
            .runAfter {
                if (!showOpQsHeaderView) return@runAfter

                updateOpHeaderView()
            }

        // Update colors when device theme changes
        shadeHeaderControllerClass
            .hookMethod("onInit")
            .runAfter { param ->
                if (!showOpQsHeaderView) return@runAfter

                val configurationControllerListener = param.thisObject.getField(
                    "configurationControllerListener"
                )

                configurationControllerListener.javaClass
                    .hookMethod(
                        "onConfigChanged",
                        "onDensityOrFontScaleChanged",
                        "onUiModeChanged",
                        "onThemeChanged"
                    )
                    .runAfter {
                        if (showOpQsHeaderView && qsTileViewImplInstance != null) {
                            updateOpHeaderView()
                        }
                    }
            }

        quickStatusBarHeaderClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                if (!showOpQsHeaderView) return@runAfter

                mQuickStatusBarHeader = param.thisObject as FrameLayout

                mHeaderQsPanel = (param.thisObject as FrameLayout).findViewById(
                    mContext.resources.getIdentifier(
                        "quick_qs_panel",
                        "id",
                        SYSTEMUI_PACKAGE
                    )
                )

                mQsHeaderContainer.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                }

                mQsHeaderContainerShade.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                }

                (mQsHeaderContainer.parent as? ViewGroup)?.removeView(mQsHeaderContainer)
                val headerImageAvailable = mQuickStatusBarHeader!!.findViewWithTag<ViewGroup?>(
                    ICONIFY_QS_HEADER_CONTAINER_TAG
                )
                mQuickStatusBarHeader!!.addView(
                    mQsHeaderContainer,
                    if (headerImageAvailable == null) {
                        -1
                    } else {
                        mQuickStatusBarHeader!!.indexOfChild(headerImageAvailable) + 1
                    }
                )

                val relativeLayout = RelativeLayout(mContext).apply {
                    layoutParams = RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.TOP
                    }
                    clipChildren = false
                    clipToPadding = false

                    (mOpQsHeaderView?.parent as? ViewGroup)?.removeView(mOpQsHeaderView)
                    mOpQsHeaderView = OpQsHeaderView(mContext).apply {
                        setOnAttachListener {
                            ControllersProvider.getInstance().apply {
                                registerWifiCallback(mWifiCallback)
                                registerMobileDataCallback(mMobileDataCallback)
                                registerBluetoothCallback(mBluetoothCallback)
                            }
                        }
                        setOnDetachListener {
                            ControllersProvider.getInstance().apply {
                                unRegisterWifiCallback(mWifiCallback)
                                unRegisterMobileDataCallback(mMobileDataCallback)
                                unRegisterBluetoothCallback(mBluetoothCallback)
                            }
                        }
                        setOnClickListeners(
                            onClickListener = mOnClickListener,
                            onLongClickListener = mOnLongClickListener
                        )
                        mMediaPlayerAdapter = MediaPlayerPagerAdapter(mMediaPlayerViews)
                        mediaPlayerContainer.adapter = mMediaPlayerAdapter
                    }

                    mQsHeaderContainer.addView(mOpQsHeaderView)
                    updateOpHeaderView()

                    (mQsHeaderContainer.parent as? ViewGroup)?.removeView(mQsHeaderContainer)
                    addView(mQsHeaderContainer)

                    (mHeaderQsPanel.parent as? ViewGroup)?.removeView(mHeaderQsPanel)
                    addView(mHeaderQsPanel)

                    (mHeaderQsPanel.layoutParams as RelativeLayout.LayoutParams).apply {
                        addRule(RelativeLayout.BELOW, mOpQsHeaderView!!.id)
                    }
                }

                mQuickStatusBarHeader!!.addView(
                    relativeLayout,
                    mQuickStatusBarHeader!!.childCount
                )

                buildHeaderViewExpansion()
            }

        // Move view to different parent when rotation changes
        quickStatusBarHeaderClass
            .hookMethod("updateResources")
            .runAfter { param ->
                mQuickStatusBarHeader = param.thisObject as FrameLayout

                mHeaderQsPanel = (param.thisObject as FrameLayout).findViewById(
                    mContext.resources.getIdentifier(
                        "quick_qs_panel",
                        "id",
                        SYSTEMUI_PACKAGE
                    )
                )

                if (!showOpQsHeaderView) return@runAfter

                buildHeaderViewExpansion()

                if (isLandscape) {
                    if (mQsHeaderContainer.parent != mQsHeaderContainerShade) {
                        (mQsHeaderContainer.parent as? ViewGroup)?.removeView(mQsHeaderContainer)
                        mQsHeaderContainerShade.addView(mQsHeaderContainer, -1)
                    }
                    mQsHeaderContainerShade.visibility = View.VISIBLE
                } else {
                    if (mQsHeaderContainer.parent != mQuickStatusBarHeader) {
                        val headerImageAvailable =
                            mQuickStatusBarHeader!!.findViewWithTag<ViewGroup?>(
                                ICONIFY_QS_HEADER_CONTAINER_TAG
                            )
                        (mQsHeaderContainer.parent as? ViewGroup)?.removeView(mQsHeaderContainer)
                        mQuickStatusBarHeader?.addView(
                            mQsHeaderContainer,
                            if (headerImageAvailable == null) {
                                0
                            } else {
                                mQuickStatusBarHeader!!.indexOfChild(headerImageAvailable) + 1
                            }
                        )
                    }
                    mQsHeaderContainerShade.visibility = View.GONE
                }
            }

        val updateQsTopMargin = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!showOpQsHeaderView) return

                val mQsPanel = try {
                    param.thisObject.getField("mQSPanel")
                } catch (ignored: Throwable) {
                    (param.thisObject as FrameLayout).findViewById(
                        mContext.resources.getIdentifier(
                            "quick_settings_panel",
                            "id",
                            SYSTEMUI_PACKAGE
                        )
                    )
                } as LinearLayout

                (mQsPanel.layoutParams as MarginLayoutParams).topMargin =
                    if (isLandscape) {
                        0
                    } else {
                        (qqsTileHeight * 2) + (qsTileMarginVertical * 2) +
                                mContext.toPx(topMarginValue + expansionAmount + expandedQsGap)
                    }
            }
        }

        // Update qs top margin
        qsContainerImplClass
            .hookMethod(
                "onFinishInflate",
                "updateResources"
            )
            .run(updateQsTopMargin)

        // Hide stock media player
        val reAttachMediaHostAvailable = qsPanelClass.declaredMethods.any {
            it.name == "reAttachMediaHost"
        }

        if (showOpQsHeaderView && hideStockMediaPlayer && reAttachMediaHostAvailable) {
            qsPanelClass
                .hookMethod("reAttachMediaHost")
                .replace { }

            // Ensure stock media player is hidden
            qsImplClass
                .hookMethod("onComponentCreated")
                .runAfter { param ->
                    if (!showOpQsHeaderView) return@runAfter

                    try {
                        val mQSPanelController = param.thisObject.getField("mQSPanelController")

                        val listener = Runnable {
                            val mediaHost = mQSPanelController.callMethod("getMediaHost")
                            val hostView = mediaHost.callMethod("getHostView")

                            hostView.callMethod("setAlpha", 0.0f)

                            try {
                                mQSPanelController.callMethod("requestAnimatorUpdate")
                            } catch (ignored: Throwable) {
                                val mQSAnimator = param.thisObject.getField("mQSAnimator")
                                mQSAnimator.callMethod("requestAnimatorUpdate")
                            }
                        }

                        mQSPanelController.callMethod(
                            "setUsingHorizontalLayoutChangeListener",
                            listener
                        )
                    } catch (ignored: Throwable) {
                    }
                }
        } else if (hideStockMediaPlayer) { // If reAttachMediaHost is not available, we need to hook switchTileLayout()
            qsPanelControllerBaseClass
                .hookMethod("switchTileLayout")
                .runBefore { param ->
                    if (!showOpQsHeaderView) return@runBefore

                    val force = param.args[0] as Boolean
                    val horizontal = param.thisObject.callMethod(
                        "shouldUseHorizontalLayout"
                    ) as Boolean
                    val mUsingHorizontalLayout = param.thisObject.getField(
                        "mUsingHorizontalLayout"
                    ) as Boolean

                    if (horizontal != mUsingHorizontalLayout || force) {
                        val mQSLogger = param.thisObject.getField("mQSLogger")
                        val qsPanel = param.thisObject.getField("mView")
                        val qsPanelTag = qsPanel.callMethod("getDumpableTag")

                        try {
                            mQSLogger.callMethod(
                                "logSwitchTileLayout",
                                horizontal,
                                mUsingHorizontalLayout,
                                force,
                                qsPanelTag
                            )
                        } catch (ignored: Throwable) {
                            mQSLogger.callMethod(
                                "logSwitchTileLayout",
                                qsPanelTag,
                                horizontal,
                                mUsingHorizontalLayout,
                                force
                            )
                        }

                        param.thisObject.setField(
                            "mUsingHorizontalLayout",
                            horizontal
                        )

                        val mUsingHorizontalLayout2 = qsPanel.getField(
                            "mUsingHorizontalLayout"
                        ) as Boolean

                        if (horizontal != mUsingHorizontalLayout2 || force) {
                            qsPanel.setField(
                                "mUsingHorizontalLayout",
                                horizontal
                            )

                            val mHorizontalContentContainer = qsPanel.getField(
                                "mHorizontalContentContainer"
                            ) as LinearLayout

                            val newParent: ViewGroup = if (horizontal) {
                                mHorizontalContentContainer
                            } else {
                                qsPanel as ViewGroup
                            }
                            val mTileLayout = qsPanel.getField("mTileLayout")
                            val index = if (!horizontal) {
                                qsPanel.getField("mMovableContentStartIndex")
                            } else {
                                0
                            } as Int

                            callStaticMethod(
                                qsPanelClass,
                                "switchToParent",
                                mTileLayout,
                                newParent,
                                index,
                                qsPanelTag
                            )

                            val mFooter = qsPanel.getFieldSilently("mFooter") as? View

                            if (mFooter != null) {
                                callStaticMethod(
                                    qsPanelClass,
                                    "switchToParent",
                                    mFooter,
                                    newParent,
                                    index + 1,
                                    qsPanelTag
                                )
                            }

                            mTileLayout.callMethod(
                                "setMinRows",
                                if (horizontal) 2 else 1
                            )
                            mTileLayout.callMethod(
                                "setMaxColumns",
                                if (horizontal) 2 else 4
                            )

                            val mHorizontalLinearLayout = qsPanel.getFieldSilently(
                                "mHorizontalLinearLayout"
                            ) as? LinearLayout

                            if (mHorizontalLinearLayout != null &&
                                quickQsPanelClass.isInstance(qsPanel)
                            ) {
                                val mMediaTotalBottomMargin =
                                    qsPanel.getField("mMediaTotalBottomMargin") as Int
                                val mQsPanelBottomPadding = (qsPanel as LinearLayout).paddingBottom

                                val layoutParams =
                                    mHorizontalLinearLayout.layoutParams as LinearLayout.LayoutParams
                                layoutParams.bottomMargin =
                                    (mMediaTotalBottomMargin - mQsPanelBottomPadding)
                                        .coerceAtLeast(0)
                                mHorizontalLinearLayout.layoutParams = layoutParams
                            }

                            qsPanel.callMethod("updatePadding")

                            mHorizontalLinearLayout?.visibility = if (!horizontal) {
                                View.GONE
                            } else {
                                View.VISIBLE
                            }
                        }

                        (param.thisObject.getFieldSilently(
                            "mUsingHorizontalLayoutChangedListener"
                        ) as? Runnable)?.run()
                    }

                    param.result = true
                }
        }

        val hasSwitchAllContentToParent = qsPanelClass.declaredMethods.any {
            it.name == "switchAllContentToParent"
        }
        val hasSwitchToParentMethod = qsPanelClass.declaredMethods.any { method ->
            method.name == "switchToParent" &&
                    method.parameterTypes.contentEquals(
                        arrayOf(View::class.java, ViewGroup::class.java, Int::class.java)
                    )
        }

        if (hasSwitchAllContentToParent && hasSwitchToParentMethod) {
            qsPanelClass
                .hookMethod("switchAllContentToParent")
                .runBefore { param ->
                    if (!showOpQsHeaderView) return@runBefore

                    val parent = param.args[0] as ViewGroup
                    val mMovableContentStartIndex = param.thisObject.getField(
                        "mMovableContentStartIndex"
                    ) as Int
                    val index = if (parent === param.thisObject) mMovableContentStartIndex else 0
                    val targetParentId = mContext.resources.getIdentifier(
                        "quick_settings_panel",
                        "id",
                        SYSTEMUI_PACKAGE
                    )

                    if (parent.id == targetParentId) {
                        val checkExistingView =
                            parent.findViewWithTag<ViewGroup?>(ICONIFY_QS_HEADER_CONTAINER_SHADE_TAG)
                        if (checkExistingView != null) {
                            mQsHeaderContainerShade = checkExistingView as LinearLayout
                            if (parent.indexOfChild(mQsHeaderContainerShade) == index) {
                                return@runBefore
                            }
                        }

                        param.thisObject.callMethod(
                            "switchToParent",
                            mQsHeaderContainerShade,
                            parent,
                            index
                        )
                    }
                }

            if (showOpQsHeaderView) {
                qsPanelClass
                    .hookMethod("switchToParent")
                    .parameters(
                        View::class.java,
                        ViewGroup::class.java,
                        Int::class.java,
                        String::class.java
                    )
                    .replace { param ->
                        val view = param.args[0] as View
                        val newParent = param.args[1] as? ViewGroup
                        val tempIndex = param.args[2] as Int

                        if (newParent == null) {
                            return@replace
                        }

                        val index = if (view.tag == ICONIFY_QS_HEADER_CONTAINER_SHADE_TAG) {
                            tempIndex
                        } else {
                            tempIndex + 1
                        }

                        val currentParent = view.parent as? ViewGroup

                        if (currentParent != newParent) {
                            currentParent?.removeView(view)
                            newParent.addView(view, index.coerceAtMost(newParent.childCount))
                        } else if (newParent.indexOfChild(view) == index) {
                            return@replace
                        } else {
                            newParent.removeView(view)
                            newParent.addView(view, index.coerceAtMost(newParent.childCount))
                        }
                    }
            }
        } else { // Some ROMs don't have this method switchAllContentToParent()
            qsPanelControllerBaseClass
                .hookMethod("onInit")
                .runBefore { param ->
                    mQsPanelView = param.thisObject.getField(
                        "mView"
                    ) as ViewGroup
                }

            qsPanelClass
                .hookMethod("switchToParent")
                .parameters(
                    View::class.java,
                    ViewGroup::class.java,
                    Int::class.java,
                    String::class.java
                )
                .runBefore { param ->
                    if (!showOpQsHeaderView ||
                        mQsPanelView == null ||
                        (param.args[1] as? ViewGroup) == null
                    ) return@runBefore

                    val parent = param.args[1] as ViewGroup
                    val mMovableContentStartIndex = mQsPanelView.getField(
                        "mMovableContentStartIndex"
                    ) as Int
                    val index = if (parent === mQsPanelView) mMovableContentStartIndex else 0
                    val targetParentId = mContext.resources.getIdentifier(
                        "quick_settings_panel",
                        "id",
                        SYSTEMUI_PACKAGE
                    )

                    if (parent.id == targetParentId) {
                        val mQsHeaderContainerShadeParent =
                            mQsHeaderContainerShade.parent as? ViewGroup
                        if (mQsHeaderContainerShadeParent != parent ||
                            mQsHeaderContainerShadeParent.indexOfChild(mQsHeaderContainerShade) != index
                        ) {
                            mQsHeaderContainerShadeParent?.removeView(mQsHeaderContainerShade)
                            parent.addView(mQsHeaderContainerShade, index)
                        }
                    }

                    param.args[2] = ((param.args[2] as Int) + 1).coerceAtMost(parent.childCount)
                }
        }

        qsImplClass
            .hookMethod("setQsExpansion")
            .runAfter { param ->
                if (!showOpQsHeaderView) return@runAfter

                val onKeyguard = param.thisObject.callMethod(
                    "isKeyguardState"
                ) as Boolean
                val mShowCollapsedOnKeyguard = param.thisObject.getField(
                    "mShowCollapsedOnKeyguard"
                ) as Boolean

                val onKeyguardAndExpanded = onKeyguard && !mShowCollapsedOnKeyguard
                val expansion = param.args[0] as Float

                setExpansion(onKeyguardAndExpanded, expansion)
            }

        hookResources()
    }

    private fun hookResources() {
        Resources::class.java
            .hookMethod("getBoolean")
            .suppressError()
            .runBefore { param ->
                if (!showOpQsHeaderView) return@runBefore

                val resId1 = mContext.resources.getIdentifier(
                    "config_use_split_notification_shade",
                    "bool",
                    SYSTEMUI_PACKAGE
                )

                val resId2 = mContext.resources.getIdentifier(
                    "config_skinnyNotifsInLandscape",
                    "bool",
                    SYSTEMUI_PACKAGE
                )

                if (param.args[0] == resId1) {
                    param.result = isLandscape
                } else if (param.args[0] == resId2) {
                    param.result = false
                }
            }

        Resources::class.java
            .hookMethod("getInteger")
            .runBefore { param ->
                if (!showOpQsHeaderView) return@runBefore

                val resId1 = mContext.resources.getIdentifier(
                    "quick_settings_max_rows",
                    "integer",
                    SYSTEMUI_PACKAGE
                )

                if (param.args[0] == resId1) {
                    param.result = 3
                }
            }

        Resources::class.java
            .hookMethod("getDimensionPixelSize")
            .suppressError()
            .runBefore { param ->
                if (!showOpQsHeaderView) return@runBefore

                val res1 = mContext.resources.getIdentifier(
                    "qs_brightness_margin_top",
                    "dimen",
                    SYSTEMUI_PACKAGE
                )

                if (res1 != 0 && param.args[0] == res1) {
                    param.result = 0
                }
            }
    }

    private fun updateOpHeaderView() {
        if (mOpQsHeaderView == null) return

        initResources()
        startMediaUpdater()
        updateInternetState()
        updateBluetoothState()
        updateMediaPlayers(force = true)
    }

    private fun buildHeaderViewExpansion() {
        if (!showOpQsHeaderView ||
            mOpQsHeaderView == null ||
            !::mHeaderQsPanel.isInitialized
        ) return

        val resources = mContext.resources
        val largeScreenHeaderActive = resources.getBoolean(
            resources.getIdentifier(
                "config_use_large_screen_shade_header",
                "bool",
                SYSTEMUI_PACKAGE
            )
        )
        val derivedTopMargin = if (isLandscape) 0 else topMarginValue

        val params = mQsHeaderContainer.layoutParams as MarginLayoutParams
        val qqsHeaderResId = if (largeScreenHeaderActive) resources.getIdentifier(
            "qqs_layout_margin_top",
            "dimen",
            SYSTEMUI_PACKAGE
        )
        else resources.getIdentifier(
            "large_screen_shade_header_min_height",
            "dimen",
            SYSTEMUI_PACKAGE
        )
        val topMargin = resources.getDimensionPixelSize(qqsHeaderResId)
        params.topMargin = topMargin + mContext.toPx(derivedTopMargin)
        mQsHeaderContainer.layoutParams = params

        (mHeaderQsPanel.layoutParams as MarginLayoutParams).topMargin = topMargin +
                (qqsTileHeight * 2) + (qsTileMarginVertical * 2) + mContext.toPx(derivedTopMargin)

        val qsHeaderHeight = resources.getDimensionPixelOffset(
            resources.getIdentifier(
                "qs_header_height",
                "dimen",
                SYSTEMUI_PACKAGE
            )
        ) - resources.getDimensionPixelOffset(qqsHeaderResId)

        val mQQSExpansionY = if (isLandscape) {
            0
        } else {
            qsHeaderHeight + 16 - topMargin + expansionAmount
        }

        val builderP: TouchAnimator.Builder = TouchAnimator.Builder()
            .addFloat(
                mQsHeaderContainer,
                "translationY",
                0F,
                mContext.toPx(mQQSExpansionY).toFloat()
            )

        mQQSContainerAnimator = builderP.build()
    }

    private fun setExpansion(forceExpanded: Boolean, expansionFraction: Float) {
        val keyguardExpansionFraction = if (forceExpanded) 1f else expansionFraction
        mQQSContainerAnimator?.setPosition(keyguardExpansionFraction)

        mQsHeaderContainer.alpha = if (forceExpanded) expansionFraction else 1f
    }

    private val mWifiCallback: ControllersProvider.OnWifiChanged =
        object : ControllersProvider.OnWifiChanged {
            override fun onWifiChanged(mWifiIndicators: Any?) {
                updateInternetState()
            }
        }

    private val mMobileDataCallback: ControllersProvider.OnMobileDataChanged =
        object : ControllersProvider.OnMobileDataChanged {
            override fun setMobileDataIndicators(mMobileDataIndicators: Any?) {
                updateInternetState()
            }

            override fun setNoSims(show: Boolean, simDetected: Boolean) {
                updateInternetState()
            }

            override fun setIsAirplaneMode(mIconState: Any?) {
                updateInternetState()
            }
        }

    private val mBluetoothCallback: ControllersProvider.OnBluetoothChanged =
        object : ControllersProvider.OnBluetoothChanged {
            override fun onBluetoothChanged(enabled: Boolean) {
                updateBluetoothState(enabled)
            }
        }

    private val mMediaCallback: MediaController.Callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            updateMediaControllers()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            updateMediaControllers()
        }
    }

    private fun startMediaUpdater() {
        mMediaUpdaterJob?.cancel()

        mMediaUpdaterJob = mMediaUpdater.launch {
            while (isActive) {
                updateMediaControllers()
                delay(1000)
            }
        }
    }

    private fun stopMediaUpdater() {
        mMediaUpdaterJob?.cancel()
    }

    private fun toggleInternetState(v: View) {
        mHandler.post {
            if (!ControllersProvider.showInternetDialog(v)) {
                mActivityLauncherUtils.launchApp(Intent(Settings.ACTION_WIFI_SETTINGS), true)
            }
        }

        mHandler.postDelayed({
            updateInternetState()
        }, 250)
    }

    private fun updateInternetState() {
        val isWiFiConnected = isWiFiConnected
        val isMobileDataConnected = isMobileDataConnected
        mInternetEnabled = isWiFiConnected || isMobileDataConnected

        val internetLabel: CharSequence = mContext.getString(
            mContext.resources.getIdentifier(
                "quick_settings_internet_label",
                "string",
                SYSTEMUI_PACKAGE
            )
        )
        val noInternetIconDrawable = ContextCompat.getDrawable(
            mContext,
            mContext.resources.getIdentifier(
                "ic_qs_no_internet_available",
                "drawable",
                SYSTEMUI_PACKAGE
            )
        )!!
        colorLabelInactive?.let { DrawableCompat.setTint(noInternetIconDrawable, it) }

        if (mInternetEnabled) {
            if (isWiFiConnected) {
                val wifiInfo = getWiFiInfo()
                val wifiIconResId = when (wifiInfo.signalStrengthLevel) {
                    4 -> "ic_wifi_signal_4"
                    3 -> "ic_wifi_signal_3"
                    2 -> "ic_wifi_signal_2"
                    1 -> "ic_wifi_signal_1"
                    else -> "ic_wifi_signal_0"
                }
                val wifiIconDrawable = ContextCompat.getDrawable(
                    mContext,
                    mContext.resources.getIdentifier(
                        wifiIconResId,
                        "drawable",
                        FRAMEWORK_PACKAGE
                    )
                )!!
                colorLabelActive?.let { DrawableCompat.setTint(wifiIconDrawable, it) }

                mOpQsHeaderView?.setInternetText(wifiInfo.ssid)
                mOpQsHeaderView?.setInternetIcon(wifiIconDrawable)
            } else {
                val carrierInfo = getConnectedCarrierInfo()
                val maxBars = 4

                val mobileDataIconDrawable = ContextCompat.getDrawable(
                    mContext,
                    mContext.resources.getIdentifier(
                        "ic_signal_cellular_${carrierInfo.signalStrengthLevel}_${maxBars}_bar",
                        "drawable",
                        FRAMEWORK_PACKAGE
                    )
                )!!
                colorLabelActive?.let { DrawableCompat.setTint(mobileDataIconDrawable, it) }

                if (carrierInfo.networkType == null) {
                    mOpQsHeaderView?.setInternetText(carrierInfo.name)
                } else {
                    mOpQsHeaderView?.setInternetText(
                        String.format(
                            "%s, %s",
                            carrierInfo.name,
                            carrierInfo.networkType
                        )
                    )
                }
                mOpQsHeaderView?.setInternetIcon(mobileDataIconDrawable)
            }
        } else {
            mOpQsHeaderView?.setInternetText(internetLabel)
            mOpQsHeaderView?.setInternetIcon(noInternetIconDrawable)
        }

        updateInternetTileColors()
    }

    private val isWiFiConnected: Boolean
        get() {
            val network: Network? = mConnectivityManager.activeNetwork
            return if (network != null) {
                val capabilities = mConnectivityManager.getNetworkCapabilities(network)
                capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                false
            }
        }

    data class WiFiInfo(
        val ssid: String,
        val signalStrengthLevel: Int
    )

    @Suppress("deprecation")
    private fun getWiFiInfo(): WiFiInfo {
        val internetLabel = mContext.getString(
            mContext.resources.getIdentifier(
                "quick_settings_internet_label",
                "string",
                SYSTEMUI_PACKAGE
            )
        )
        val defaultInfo = WiFiInfo(internetLabel, 0)

        val wifiInfo = mWifiManager.connectionInfo ?: return defaultInfo

        val ssid = if (wifiInfo.hiddenSSID || wifiInfo.ssid == WifiManager.UNKNOWN_SSID) {
            internetLabel
        } else {
            wifiInfo.ssid?.replace("\"", "") ?: internetLabel
        }
        val signalStrengthLevel = WifiManager.calculateSignalLevel(wifiInfo.rssi, 5).coerceIn(0, 4)

        return WiFiInfo(ssid, signalStrengthLevel)
    }

    private val isMobileDataConnected: Boolean
        get() {
            val network: Network? = mConnectivityManager.activeNetwork
            if (network != null) {
                val capabilities = mConnectivityManager.getNetworkCapabilities(network)
                return capabilities != null &&
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            return false
        }

    data class CarrierInfo(
        val name: String,
        val signalStrengthLevel: Int?,
        val networkType: String?
    )

    @Suppress("deprecation")
    @SuppressLint("MissingPermission")
    fun getConnectedCarrierInfo(): CarrierInfo {
        val internetLabel = mContext.getString(
            mContext.resources.getIdentifier(
                "quick_settings_internet_label",
                "string",
                SYSTEMUI_PACKAGE
            )
        )
        val defaultInfo = CarrierInfo(internetLabel, null, null)

        val activeSubscriptionInfoList = mSubscriptionManager.activeSubscriptionInfoList
        if (activeSubscriptionInfoList.isNullOrEmpty()) {
            return defaultInfo
        }

        val network: Network = mConnectivityManager.activeNetwork ?: return defaultInfo
        val networkCapabilities =
            mConnectivityManager.getNetworkCapabilities(network) ?: return defaultInfo

        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            for (subscriptionInfo in activeSubscriptionInfoList) {
                val subId = subscriptionInfo.subscriptionId
                val subTelephonyManager = mTelephonyManager.createForSubscriptionId(subId)

                if (subTelephonyManager.simState == TelephonyManager.SIM_STATE_READY &&
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                ) {
                    val carrierName = subscriptionInfo.carrierName.toString()
                    val signalStrength = subTelephonyManager.signalStrength
                    val signalStrengthLevel = signalStrength?.level?.coerceIn(0, 4) ?: 0

                    val networkType = when (subTelephonyManager.networkType) {
                        TelephonyManager.NETWORK_TYPE_NR -> "5G"
                        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                        TelephonyManager.NETWORK_TYPE_HSDPA,
                        TelephonyManager.NETWORK_TYPE_HSPA,
                        TelephonyManager.NETWORK_TYPE_HSUPA,
                        TelephonyManager.NETWORK_TYPE_UMTS -> "3G"

                        TelephonyManager.NETWORK_TYPE_EDGE,
                        TelephonyManager.NETWORK_TYPE_GPRS -> "2G"

                        TelephonyManager.NETWORK_TYPE_CDMA,
                        TelephonyManager.NETWORK_TYPE_EVDO_0,
                        TelephonyManager.NETWORK_TYPE_EVDO_A,
                        TelephonyManager.NETWORK_TYPE_EVDO_B,
                        TelephonyManager.NETWORK_TYPE_1xRTT -> "CDMA"

                        TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
                        else -> null
                    }

                    return CarrierInfo(carrierName, signalStrengthLevel, networkType)
                }
            }
        }

        return defaultInfo
    }

    private val isBluetoothEnabled: Boolean
        get() {
            return mBluetoothManager.adapter != null && mBluetoothManager.adapter.isEnabled
        }

    @SuppressLint("MissingPermission")
    private fun getBluetoothConnectedDevice(): String {
        val bluetoothLabel = mContext.resources.getString(
            mContext.resources.getIdentifier(
                "quick_settings_bluetooth_label",
                "string",
                SYSTEMUI_PACKAGE
            )
        )

        val bluetoothAdapter = mBluetoothManager.adapter

        if (bluetoothAdapter != null) {
            val bondedDevices = bluetoothAdapter.bondedDevices

            for (device in bondedDevices) {
                if (isBluetoothDeviceConnected(device)) {
                    return device.name.ifEmpty { bluetoothLabel }
                }
            }
        }

        return bluetoothLabel
    }

    private fun isBluetoothDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            val m: Method = device.javaClass.getMethod("isConnected")
            m.invoke(device) as Boolean
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }

    private fun toggleBluetoothState(v: View) {
        mHandler.post {
            if (!ControllersProvider.showBluetoothDialog(mContext, v)) {
                mBluetoothController.callMethod("setBluetoothEnabled", !isBluetoothEnabled)
            }
        }

        mHandler.postDelayed({
            updateBluetoothState()
        }, 250)
    }

    private fun updateBluetoothState(enabled: Boolean = isBluetoothEnabled) {
        mBluetoothEnabled = enabled

        val connectedIconResId = mContext.resources.getIdentifier(
            "ic_bluetooth_connected",
            "drawable",
            SYSTEMUI_PACKAGE
        )
        val connectedIcon = if (connectedIconResId != 0) {
            ContextCompat.getDrawable(mContext, connectedIconResId)
        } else {
            ContextCompat.getDrawable(
                appContext,
                appContext.resources.getIdentifier(
                    "ic_bluetooth_connected",
                    "drawable",
                    appContext.packageName
                )
            )
        }!!
        colorLabelActive?.let { DrawableCompat.setTint(connectedIcon, it) }

        val disconnectedIconResId = mContext.resources.getIdentifier(
            "ic_qs_bluetooth",
            "drawable",
            FRAMEWORK_PACKAGE
        )
        val disconnectedIcon = if (disconnectedIconResId != 0) {
            ContextCompat.getDrawable(mContext, disconnectedIconResId)
        } else {
            ContextCompat.getDrawable(
                appContext,
                appContext.resources.getIdentifier(
                    "ic_bluetooth_disconnected",
                    "drawable",
                    appContext.packageName
                )
            )
        }!!
        colorLabelInactive?.let { DrawableCompat.setTint(disconnectedIcon, it) }

        if (enabled) {
            val defaultLabel = mContext.resources.getString(
                mContext.resources.getIdentifier(
                    "quick_settings_bluetooth_label",
                    "string",
                    SYSTEMUI_PACKAGE
                )
            )
            val bluetoothLabel = getBluetoothConnectedDevice()
            val deviceConnected = bluetoothLabel != defaultLabel

            mOpQsHeaderView?.setBlueToothText(bluetoothLabel)
            mOpQsHeaderView?.setBlueToothIcon(if (deviceConnected) connectedIcon else disconnectedIcon)
        } else {
            mOpQsHeaderView?.setBlueToothText(
                mContext.resources.getIdentifier(
                    "quick_settings_bluetooth_label",
                    "string",
                    SYSTEMUI_PACKAGE
                )
            )
            mOpQsHeaderView?.setBlueToothIcon(disconnectedIcon)
        }

        updateBluetoothTileColors()
    }

    private enum class MediaAction {
        TOGGLE_PLAYBACK,
        PLAY_PREVIOUS,
        PLAY_NEXT
    }

    private fun performMediaAction(packageName: String, action: MediaAction) {
        val controller = mActiveMediaControllers.find { it.first == packageName }?.second ?: return

        when (action) {
            MediaAction.TOGGLE_PLAYBACK -> {
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    controller.transportControls.pause()
                } else {
                    controller.transportControls.play()
                }
            }

            MediaAction.PLAY_PREVIOUS -> {
                controller.transportControls.skipToPrevious()
            }

            MediaAction.PLAY_NEXT -> {
                controller.transportControls.skipToNext()
            }
        }

        updateMediaPlayers()
    }

    private fun updateMediaControllers() {
        val currentControllers = mMediaSessionManager.getActiveSessions(null)
            .map { controller -> controller.packageName to controller }
            .toMutableList()

        val currentPackageNames = currentControllers.map { it.first }.toSet()

        mActiveMediaControllers.removeAll { (packageName, controller) ->
            if (!currentPackageNames.contains(packageName)) {
                controller.unregisterCallback(mMediaCallback)

                mMediaControllerMetadataMap.remove(packageName)
                mPrevMediaControllerMetadataMap.remove(packageName)
                mPrevMediaPlayingState.remove(packageName)
                mPrevMediaArtworkMap.remove(packageName)
                mPrevMediaProcessedArtworkMap.remove(packageName)
                removeMediaPlayerView(packageName)

                true
            } else {
                false
            }
        }

        currentControllers.forEach { (packageName, controller) ->
            val existingController = mActiveMediaControllers.find { it.first == packageName }

            if (existingController == null) {
                controller.registerCallback(mMediaCallback)
                mMediaControllerMetadataMap[packageName] = controller.metadata
                mActiveMediaControllers.add(packageName to controller)
            } else if (existingController.second != controller) {
                val oldController = existingController.second

                oldController.unregisterCallback(mMediaCallback)
                controller.registerCallback(mMediaCallback)
                mMediaControllerMetadataMap[packageName] = controller.metadata

                mActiveMediaControllers.remove(existingController)
                mActiveMediaControllers.add(packageName to controller)
            }
        }

        updateMediaPlayers()
    }

    private fun updateMediaPlayers(force: Boolean = false) {
        updateMediaPlayer(null, null, force)

        mActiveMediaControllers.forEach { (packageName, controller) ->
            updateMediaPlayer(packageName, controller, force)
        }
    }

    private fun updateMediaPlayer(
        packageName: String?,
        controller: MediaController?,
        force: Boolean = false
    ) {
        if (mOpQsHeaderView == null || !::opMediaBackgroundDrawable.isInitialized) return

        val mMediaPlayer = getOrCreateMediaPlayer(packageName) ?: return
        val mInactiveBackground = opMediaBackgroundDrawable.constantState?.newDrawable()?.mutate()
            ?.apply {
                if (isQsTileOverlayEnabled) {
                    setTint(Color.TRANSPARENT)
                } else {
                    colorInactive?.let { setTint(it) }
                }
            }

        mMediaPlayer.apply {
            if (packageName == null ||
                mediaPlayerBackground.drawable == null ||
                mediaPlayerBackground.drawable == opMediaDefaultBackground
            ) {
                setMediaPlayerBackground(mInactiveBackground)
                colorLabelActive?.let {
                    setMediaAppIconColor(
                        backgroundColor = colorAccent,
                        iconColor = it
                    )
                }
                colorLabelInactive?.let { setMediaPlayerItemsColor(it) }
            }
        }

        if (packageName == null || controller == null) return

        artworkExtractorScope.launch {
            val mMediaMetadata = controller.metadata
            val mPreviousMediaMetadata = mPrevMediaControllerMetadataMap[packageName]

            val (areBitmapsEqual, areMetadataEqual) = areDataEqual(
                mPreviousMediaMetadata,
                mMediaMetadata
            )
            val isSamePlayingState =
                controller.playbackState?.state == PlaybackState.STATE_PLAYING ==
                        mPrevMediaPlayingState[packageName]

            val requireUpdate = !areBitmapsEqual || !areMetadataEqual || !isSamePlayingState

            if (!requireUpdate && !force) return@launch

            val mMediaArtwork = mMediaMetadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: mMediaMetadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            val processedArtwork = processArtwork(mMediaArtwork, mMediaPlayer.mediaPlayerBackground)
            val dominantColor: Int? = extractDominantColor(processedArtwork)
            val filteredArtwork: Bitmap? = processedArtwork?.let {
                applyColorFilterToBitmap(it, dominantColor)
                it.applyBlur(mContext, mediaBlurLevel)
            }
            val newArtworkDrawable = when {
                filteredArtwork != null -> BitmapDrawable(mContext.resources, filteredArtwork)
                else -> mInactiveBackground
            }

            val finalArtworkDrawable: Drawable? = when {
                mPrevMediaArtworkMap[packageName] == null && filteredArtwork != null -> {
                    TransitionDrawable(
                        arrayOf(
                            mInactiveBackground,
                            newArtworkDrawable
                        )
                    )
                }

                mPrevMediaArtworkMap[packageName] != null && filteredArtwork != null -> {
                    TransitionDrawable(
                        arrayOf(
                            BitmapDrawable(
                                mContext.resources,
                                mPrevMediaProcessedArtworkMap[packageName]
                            ),
                            newArtworkDrawable
                        )
                    )
                }

                mPrevMediaArtworkMap[packageName] != null && filteredArtwork == null -> {
                    TransitionDrawable(
                        arrayOf(
                            BitmapDrawable(
                                mContext.resources,
                                mPrevMediaProcessedArtworkMap[packageName]
                            ),
                            newArtworkDrawable
                        )
                    )
                }

                else -> {
                    mInactiveBackground
                }
            }

            val mMediaTitle = mMediaMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val mMediaArtist = mMediaMetadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val mIsMediaPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING

            val appIconDrawable = runCatching {
                controller.packageName?.let { packageName ->
                    mContext.packageManager.getApplicationIcon(packageName)
                }?.toCircularDrawable()
            }.getOrNull()

            withContext(Dispatchers.Main) {
                mMediaPlayer.apply {
                    setMediaTitle(
                        mMediaTitle
                            ?: appContext.getString(
                                appContext.resources.getIdentifier(
                                    "media_player_not_playing",
                                    "string",
                                    appContext.packageName
                                )
                            )
                    )
                    setMediaArtist(mMediaArtist)
                    setMediaPlayingIcon(mIsMediaPlaying)
                }

                val requireIconTint = when {
                    appIconDrawable != null && mMediaTitle != null -> {
                        mMediaPlayer.setMediaAppIconDrawable(appIconDrawable)
                        false
                    }

                    else -> {
                        mMediaPlayer.resetMediaAppIcon()
                        true
                    }
                }

                withContext(Dispatchers.IO) {
                    previousBlurLevel = mediaBlurLevel
                    mPrevMediaPlayingState[packageName] = mIsMediaPlaying
                    mPrevMediaControllerMetadataMap[packageName] = mMediaMetadata
                    mPrevMediaArtworkMap[packageName] = mMediaArtwork
                    mPrevMediaProcessedArtworkMap[packageName] = filteredArtwork
                }

                val onDominantColor: Int?

                withContext(Dispatchers.IO) {
                    val bitmap = filteredArtwork ?: mMediaArtwork
                    val scaledBitmap = bitmap?.let { scaleBitmap(it, 0.1f) }
                    val mostUsedColor = scaledBitmap?.let { getMostUsedColor(it) } ?: dominantColor
                    onDominantColor = getContrastingTextColor(mostUsedColor)
                }

                if (requireIconTint) {
                    mMediaPlayer.setMediaAppIconColor(
                        backgroundColor = dominantColor ?: colorActive ?: colorAccent,
                        iconColor = onDominantColor ?: colorLabelActive ?: colorPrimary
                    )
                } else {
                    mMediaPlayer.resetMediaAppIconColor(
                        backgroundColor = dominantColor ?: colorActive ?: colorAccent
                    )
                }

                if (processedArtwork == null || onDominantColor == null) {
                    mMediaPlayer.setMediaPlayerItemsColor(colorLabelInactive)
                } else {
                    mMediaPlayer.setMediaPlayerItemsColor(onDominantColor)
                }

                mMediaPlayer.post {
                    mMediaPlayer.setMediaPlayerBackground(finalArtworkDrawable)
                    if (finalArtworkDrawable is TransitionDrawable) {
                        finalArtworkDrawable.isCrossFadeEnabled = true
                        finalArtworkDrawable.startTransition(250)
                    }
                }
            }
        }
    }

    private fun getOrCreateMediaPlayer(packageName: String?): OpQsMediaPlayerView? {
        if (!mMediaPlayerViews.any { it.first == packageName }) {
            val mediaPlayerView = OpQsMediaPlayerView(mContext)

            if (packageName != null) {
                mediaPlayerView.setOnClickListeners { v ->
                    when (v) {
                        mediaPlayerView.mediaPlayerPrevBtn -> {
                            performMediaAction(packageName, MediaAction.PLAY_PREVIOUS)
                        }

                        mediaPlayerView.mediaPlayerPlayPauseBtn -> {
                            performMediaAction(packageName, MediaAction.TOGGLE_PLAYBACK)
                        }

                        mediaPlayerView.mediaPlayerNextBtn -> {
                            performMediaAction(packageName, MediaAction.PLAY_NEXT)
                        }

                        mediaPlayerView.mediaPlayerBackground -> {
                            launchMediaPlayer(packageName)
                        }

                        mediaPlayerView.mediaOutputSwitcher -> {
                            launchMediaOutputSwitcher(packageName, v)
                        }
                    }
                }
            }

            addMediaPlayerView(packageName, mediaPlayerView)
        }

        return mMediaPlayerViews.find { it.first == packageName }?.second
    }

    private fun addMediaPlayerView(packageName: String?, view: OpQsMediaPlayerView) {
        if (packageName == null && mMediaPlayerViews.isNotEmpty()) return

        val position = mMediaPlayerViews.indexOfFirst { it.first == packageName }
        if (position != -1) return

        mMediaPlayerViews.removeAll { it.first == null }
        mMediaPlayerAdapter?.notifyDataSetChanged()

        mMediaPlayerViews.add(0, packageName to view)
        mMediaPlayerAdapter?.notifyDataSetChanged()

        mOpQsHeaderView?.mediaPlayerContainer?.post {
            mOpQsHeaderView?.mediaPlayerContainer?.setCurrentItem(0, false)
        }

        updateAdapter()
    }

    private fun removeMediaPlayerView(packageName: String) {
        val position = mMediaPlayerViews.indexOfFirst { it.first == packageName }
        if (position == -1) return

        mMediaPlayerViews.removeAt(position)
        mMediaPlayerAdapter?.notifyDataSetChanged()

        if (mMediaPlayerViews.isEmpty()) {
            mMediaPlayerViews.add(null to OpQsMediaPlayerView(mContext))
            mMediaPlayerAdapter?.notifyDataSetChanged()
        }

        updateAdapter()
    }

    private fun updateAdapter() {
        mMediaPlayerAdapter = MediaPlayerPagerAdapter(mMediaPlayerViews)
        mOpQsHeaderView?.mediaPlayerContainer?.adapter = mMediaPlayerAdapter
    }

    private fun updateInternetTileColors() {
        if (mInternetEnabled) {
            mOpQsHeaderView?.setInternetTileColor(
                tileColor = colorActive,
                labelColor = colorLabelActive
            )
        } else {
            mOpQsHeaderView?.setInternetTileColor(
                tileColor = colorInactive,
                labelColor = colorLabelInactive
            )
        }
    }

    private fun updateBluetoothTileColors() {
        if (mBluetoothEnabled) {
            mOpQsHeaderView?.setBluetoothTileColor(
                tileColor = colorActive,
                labelColor = colorLabelActive
            )
        } else {
            mOpQsHeaderView?.setBluetoothTileColor(
                tileColor = colorInactive,
                labelColor = colorLabelInactive
            )
        }
    }

    private suspend fun processArtwork(
        bitmap: Bitmap?,
        mMediaAlbumArtBg: ImageView
    ): Bitmap? {
        return withContext(Dispatchers.IO) {
            if (bitmap == null) return@withContext null

            val width = mMediaAlbumArtBg.width
            val height = mMediaAlbumArtBg.height

            getScaledRoundedBitmap(bitmap, width, height)
        }
    }

    private fun getScaledRoundedBitmap(
        bitmap: Bitmap,
        width: Int,
        height: Int
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null

        val widthScale = width.toFloat() / bitmap.width
        val heightScale = height.toFloat() / bitmap.height
        val scaleFactor = maxOf(widthScale, heightScale)

        val scaledWidth = (bitmap.width * scaleFactor).toInt()
        val scaledHeight = (bitmap.height * scaleFactor).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val xOffset = (scaledWidth - width) / 2
        val yOffset = (scaledHeight - height) / 2

        val validWidth =
            if (xOffset + width > scaledBitmap.width) scaledBitmap.width - xOffset else width
        val validHeight =
            if (yOffset + height > scaledBitmap.height) scaledBitmap.height - yOffset else height

        val croppedBitmap = Bitmap.createBitmap(
            scaledBitmap,
            xOffset,
            yOffset,
            validWidth,
            validHeight
        )

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val paint = Paint().apply {
            isAntiAlias = true
            shader = BitmapShader(croppedBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())

        Canvas(output).drawRoundRect(rect, qsTileCornerRadius, qsTileCornerRadius, paint)

        return output
    }

    private fun applyColorFilterToBitmap(bitmap: Bitmap, color: Int?): Bitmap {
        val colorFilteredBitmap = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            bitmap.config ?: Bitmap.Config.ARGB_8888
        )

        val paint = Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(
                0f, 0f, bitmap.width.toFloat(), 0f, // Horizontal gradient
                intArrayOf(
                    ColorUtils.blendARGB(color ?: Color.BLACK, Color.TRANSPARENT, 0.4f),
                    ColorUtils.blendARGB(color ?: Color.BLACK, Color.TRANSPARENT, 0.6f),
                    ColorUtils.blendARGB(color ?: Color.BLACK, Color.TRANSPARENT, 0.8f),
                    ColorUtils.blendARGB(color ?: Color.BLACK, Color.TRANSPARENT, 0.6f),
                    ColorUtils.blendARGB(color ?: Color.BLACK, Color.TRANSPARENT, 0.4f)
                ),
                floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f), // Positions for the colors
                Shader.TileMode.CLAMP
            )
        }

        Canvas(colorFilteredBitmap).apply {
            drawBitmap(bitmap, 0f, 0f, null)
            drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        }

        return colorFilteredBitmap
    }

    private suspend fun extractDominantColor(bitmap: Bitmap?): Int? =
        suspendCancellableCoroutine { cont ->
            if (bitmap == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            Palette.from(bitmap).generate { palette ->
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val fallbackColor = Score.score(QuantizerCelebi.quantize(pixels, 128)).firstOrNull()
                val dominantColor = palette?.getDominantColor(fallbackColor ?: Color.BLACK)
                cont.resume(dominantColor)
            }
        }

    private fun Bitmap.toCircularBitmap(): Bitmap {
        val width = this.width
        val height = this.height
        val diameter = width.coerceAtMost(height)
        val output = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888)

        val paint = Paint()
        paint.isAntiAlias = true

        val canvas = Canvas(output)
        val rect = Rect(0, 0, diameter, diameter)
        val rectF = RectF(rect)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawOval(rectF, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val left = (width - diameter) / 2
        val top = (height - diameter) / 2
        canvas.drawBitmap(this, Rect(left, top, left + diameter, top + diameter), rect, paint)

        return output
    }

    private fun Drawable.toCircularDrawable(): Drawable {
        val bitmap = this.toBitmap()
        val circularBitmap = bitmap.toCircularBitmap()
        return BitmapDrawable(mContext.resources, circularBitmap)
    }

    @Suppress("SameParameterValue")
    private fun scaleBitmap(bitmap: Bitmap, scaleFactor: Float): Bitmap {
        val width = (bitmap.width * scaleFactor).toInt()
        val height = (bitmap.height * scaleFactor).toInt()
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private data class ColorRGB(val r: Int, val g: Int, val b: Int) {
        fun toInt() = (r shl 16) or (g shl 8) or b
    }

    private fun colorDistance(c1: ColorRGB, c2: ColorRGB): Double {
        return sqrt(
            ((c1.r - c2.r).toDouble().pow(2)) +
                    ((c1.g - c2.g).toDouble().pow(2)) +
                    ((c1.b - c2.b).toDouble().pow(2))
        )
    }

    private fun getMostUsedColor(bitmap: Bitmap, k: Int = 5, iterations: Int = 10): Int {
        val width = bitmap.width
        val height = bitmap.height
        val colors = mutableListOf<ColorRGB>()

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val color = ColorRGB(
                    r = (pixel shr 16) and 0xFF,
                    g = (pixel shr 8) and 0xFF,
                    b = pixel and 0xFF
                )
                colors.add(color)
            }
        }

        val centroids = List(k) {
            ColorRGB(
                r = Random.nextInt(256),
                g = Random.nextInt(256),
                b = Random.nextInt(256)
            )
        }.toMutableList()

        repeat(iterations) {
            val clusters = Array(k) { mutableListOf<ColorRGB>() }

            for (color in colors) {
                val distances = centroids.map { centroid -> colorDistance(color, centroid) }
                val closestCentroidIndex = distances.indexOf(distances.minOrNull())
                clusters[closestCentroidIndex].add(color)
            }

            for (i in centroids.indices) {
                val clusterColors = clusters[i]
                if (clusterColors.isNotEmpty()) {
                    val r = clusterColors.map { it.r }.average().toInt()
                    val g = clusterColors.map { it.g }.average().toInt()
                    val b = clusterColors.map { it.b }.average().toInt()
                    centroids[i] = ColorRGB(r, g, b)
                }
            }
        }

        val dominantColor = centroids.maxByOrNull { centroid ->
            colors.count { color ->
                colorDistance(color, centroid) < 50
            }
        } ?: ColorRGB(0, 0, 0)

        return dominantColor.toInt()
    }

    private fun getContrastingTextColor(color: Int?): Int? {
        if (color == null) return null

        val luminance = (0.299 * Color.red(color) +
                0.587 * Color.green(color) +
                0.114 * Color.blue(color)) / 255

        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }

    private val mOnClickListener = View.OnClickListener { v ->
        if (v === mOpQsHeaderView?.internetTile) {
            toggleInternetState(v)
            vibrate()
        } else if (v === mOpQsHeaderView?.bluetoothTile) {
            toggleBluetoothState(v)
            vibrate()
        }
    }

    private val mOnLongClickListener = OnLongClickListener { v ->
        if (v === mOpQsHeaderView?.internetTile) {
            mActivityLauncherUtils.launchApp(Intent(Settings.ACTION_WIFI_SETTINGS), true)
            vibrate()
            return@OnLongClickListener true
        } else if (v === mOpQsHeaderView?.bluetoothTile) {
            mActivityLauncherUtils.launchApp(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), true)
            vibrate()
            return@OnLongClickListener true
        } else {
            return@OnLongClickListener false
        }
    }

    private fun launchMediaOutputSwitcher(packageName: String?, v: View) {
        if (packageName == null) return

        if (mMediaOutputDialogFactory != null) {
            if (isMethodAvailable(
                    mMediaOutputDialogFactory,
                    "create",
                    String::class.java,
                    Boolean::class.java,
                    View::class.java
                )
            ) {
                mMediaOutputDialogFactory.callMethod("create", packageName, true, v)
            } else if (isMethodAvailable(
                    mMediaOutputDialogFactory,
                    "create",
                    View::class.java,
                    String::class.java,
                    Boolean::class.java,
                    Boolean::class.java
                )
            ) {
                mMediaOutputDialogFactory.callMethod("create", v, packageName, true, true)
            } else if (isMethodAvailable(
                    mMediaOutputDialogFactory,
                    "createAndShow",
                    String::class.java,
                    Boolean::class.java,
                    View::class.java,
                    UserHandle::class.java,
                    MediaSession.Token::class.java
                )
            ) {
                mMediaOutputDialogFactory.callMethod(
                    "createAndShow",
                    packageName,
                    true,
                    v,
                    null,
                    null
                )
            } else {
                log(this@OpQsHeader, "No method available to create MediaOutputDialog")
            }
        } else {
            log(this@OpQsHeader, "MediaOutputDialogFactory is not available")
        }
    }

    private fun launchMediaPlayer(packageName: String?) {
        if (packageName == null) return

        mActivityLauncherUtils.launchApp(
            Intent(
                mContext.packageManager.getLaunchIntentForPackage(packageName)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(packageName)
            },
            true
        )
        vibrate()
    }

    private fun areDataEqual(
        metadata1: MediaMetadata?,
        metadata2: MediaMetadata?
    ): Pair<Boolean, Boolean> {
        val bitmap1 = metadata1?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata1?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val bitmap2 = metadata2?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata2?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        return areBitmapsEqual(bitmap1, bitmap2) to
                (metadata1?.getString(MediaMetadata.METADATA_KEY_TITLE) ==
                        metadata2?.getString(MediaMetadata.METADATA_KEY_TITLE) &&
                        metadata1?.getString(MediaMetadata.METADATA_KEY_ARTIST) ==
                        metadata2?.getString(MediaMetadata.METADATA_KEY_ARTIST))
    }

    private fun areBitmapsEqual(bitmap1: Bitmap?, bitmap2: Bitmap?): Boolean {
        if (bitmap1 == null && bitmap2 == null) {
            return true
        }
        if (bitmap1 == null || bitmap2 == null) {
            return false
        }
        if (bitmap1.width != bitmap2.width || bitmap1.height != bitmap2.height) {
            return false
        }

        val buffer1 = ByteBuffer.allocate(bitmap1.byteCount)
        val buffer2 = ByteBuffer.allocate(bitmap2.byteCount)

        bitmap1.copyPixelsToBuffer(buffer1)
        bitmap2.copyPixelsToBuffer(buffer2)

        return buffer1.array().contentEquals(buffer2.array())
    }

    private fun vibrate() {
        if (vibrateOnClick) {
            VibrationUtils.triggerVibration(mContext, 2)
        }
    }

    private fun initResources() {
        mContext.apply {
            colorAccent = getColorAttrDefaultColor(
                this,
                android.R.attr.colorAccent
            )
            colorPrimary = getColorAttrDefaultColor(
                this,
                android.R.attr.colorPrimary
            )
            qsTileCornerRadius = resources.getDimensionPixelSize(
                resources.getIdentifier(
                    "qs_corner_radius",
                    "dimen",
                    SYSTEMUI_PACKAGE
                )
            ).toFloat()
            qqsTileHeight = resources.getDimensionPixelSize(
                resources.getIdentifier(
                    "qs_quick_tile_size",
                    "dimen",
                    SYSTEMUI_PACKAGE
                )
            )
            qsTileMarginVertical = resources.getDimensionPixelSize(
                resources.getIdentifier(
                    "qs_tile_margin_vertical",
                    "dimen",
                    SYSTEMUI_PACKAGE
                )
            )
        }

        qsTileViewImplInstance?.let { thisObject ->
            val qsTileOverlayEnabled = isQsTileOverlayEnabled

            colorActive = if (qsTileOverlayEnabled) Color.WHITE
            else thisObject.getField(
                "colorActive"
            ) as Int
            colorInactive = if (qsTileOverlayEnabled) Color.TRANSPARENT
            else thisObject.getField(
                "colorInactive"
            ) as Int
            colorLabelActive = if (qsTextAlwaysWhite) Color.WHITE
            else if (qsTextFollowAccent) colorAccent
            else thisObject.getField(
                "colorLabelActive"
            ) as Int
            colorLabelInactive = thisObject.getField(
                "colorLabelInactive"
            ) as Int
        }

        opMediaBackgroundDrawable = if (colorInactive != null && colorInactive != 0) {
            GradientDrawable().apply {
                setColor(colorInactive!!)
                shape = GradientDrawable.RECTANGLE
                cornerRadius = qsTileCornerRadius
            }
        } else {
            ContextCompat.getDrawable(
                mContext,
                mContext.resources.getIdentifier(
                    "qs_tile_background_shape",
                    "drawable",
                    SYSTEMUI_PACKAGE
                )
            )!!
        }

        mContext.apply {
            mWifiManager = getSystemService(WifiManager::class.java)
            mTelephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            mMediaSessionManager = mContext.getSystemService(MediaSessionManager::class.java)
            mConnectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            mSubscriptionManager =
                getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        }

        mInternetEnabled = isWiFiConnected || isMobileDataConnected
        mBluetoothEnabled = isBluetoothEnabled
    }

    private val isLandscape: Boolean
        get() = mContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    companion object {
        var launchableImageView: Class<*>? = null
        var launchableLinearLayout: Class<*>? = null
    }
}