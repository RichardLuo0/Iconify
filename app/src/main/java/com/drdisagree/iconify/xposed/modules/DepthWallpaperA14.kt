package com.drdisagree.iconify.xposed.modules

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.drdisagree.iconify.IExtractSubjectCallback
import com.drdisagree.iconify.R
import com.drdisagree.iconify.common.Const.ACTION_EXTRACT_FAILURE
import com.drdisagree.iconify.common.Const.ACTION_EXTRACT_SUBJECT
import com.drdisagree.iconify.common.Const.ACTION_EXTRACT_SUCCESS
import com.drdisagree.iconify.common.Const.AI_PLUGIN_PACKAGE
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.CUSTOM_DEPTH_WALLPAPER_SWITCH
import com.drdisagree.iconify.common.Preferences.DEPTH_WALLPAPER_AI_MODE
import com.drdisagree.iconify.common.Preferences.DEPTH_WALLPAPER_CHANGED
import com.drdisagree.iconify.common.Preferences.DEPTH_WALLPAPER_FOREGROUND_ALPHA
import com.drdisagree.iconify.common.Preferences.DEPTH_WALLPAPER_ON_AOD
import com.drdisagree.iconify.common.Preferences.DEPTH_WALLPAPER_SWITCH
import com.drdisagree.iconify.common.Preferences.LOCKSCREEN_SHADE_SWITCH
import com.drdisagree.iconify.xposed.HookEntry.Companion.enqueueProxyCommand
import com.drdisagree.iconify.xposed.HookRes.Companion.modRes
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getFloatField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

@SuppressLint("DiscouragedApi")
class DepthWallpaperA14(context: Context) : ModPack(context) {

    private var showDepthWallpaper = false
    private var showCustomImages = false
    private var foregroundAlpha = 1.0f
    private var mScrimController: Any? = null
    private var mForegroundDimmingOverlay: Drawable? = null
    private lateinit var mWallpaperForeground: FrameLayout
    private lateinit var mWallpaperBackground: FrameLayout
    private lateinit var mWallpaperBitmapContainer: FrameLayout
    private lateinit var mWallpaperDimmingOverlay: FrameLayout
    private var mWallpaperForegroundCacheValid = false
    private var mLayersCreated = false
    private var showOnAOD = true
    private var keepLockScreenShade = true
    private var mAiMode = 0
    private var foregroundPath = Environment.getExternalStorageDirectory()
        .toString() + "/.iconify_files/depth_wallpaper_fg.png"
    private var backgroundPath = Environment.getExternalStorageDirectory()
        .toString() + "/.iconify_files/depth_wallpaper_bg.png"
    private var mPluginReceiverRegistered = false
    private lateinit var mPluginReceiver: BroadcastReceiver
    private var wallpaperProcessorThread: Thread? = null

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            showDepthWallpaper = getBoolean(DEPTH_WALLPAPER_SWITCH, false)
            showCustomImages = getBoolean(CUSTOM_DEPTH_WALLPAPER_SWITCH, false)
            foregroundAlpha = getSliderInt(DEPTH_WALLPAPER_FOREGROUND_ALPHA, 80) / 100.0f
            showOnAOD = getBoolean(DEPTH_WALLPAPER_ON_AOD, true)
            keepLockScreenShade = getBoolean(LOCKSCREEN_SHADE_SWITCH, true)
            mAiMode = getString(DEPTH_WALLPAPER_AI_MODE, "0")!!.toInt()
        }

        if (key.isNotEmpty()) {
            key[0].let {
                if (it == DEPTH_WALLPAPER_SWITCH ||
                    it == DEPTH_WALLPAPER_CHANGED ||
                    it == CUSTOM_DEPTH_WALLPAPER_SWITCH
                ) {
                    if (it == DEPTH_WALLPAPER_CHANGED) {
                        mWallpaperForegroundCacheValid = false
                    }

                    if (it == CUSTOM_DEPTH_WALLPAPER_SWITCH && !showCustomImages) {
                        invalidateCache()
                    }

                    setCustomDepthWallpaper()
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "NewApi")
    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        mPluginReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != null) {
                    when (intent.action) {
                        ACTION_EXTRACT_SUCCESS -> {
                            mWallpaperForegroundCacheValid = false
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mContext,
                                    modRes.getString(R.string.depth_wallpaper_subject_extraction_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        ACTION_EXTRACT_FAILURE -> {
                            mWallpaperForegroundCacheValid = false
                            log("Subject extraction failed\n${intent.getStringExtra("error")}")

                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mContext,
                                    modRes.getString(R.string.depth_wallpaper_subject_extraction_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }

        if (!mPluginReceiverRegistered) {
            IntentFilter().apply {
                addAction(ACTION_EXTRACT_SUCCESS)
                addAction(ACTION_EXTRACT_FAILURE)
            }.also { intentFilter ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mContext.registerReceiver(
                        mPluginReceiver,
                        intentFilter,
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    mContext.registerReceiver(mPluginReceiver, intentFilter)
                }
            }
            mPluginReceiverRegistered = true
        }

        val qsImplClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.QSImpl",
            "$SYSTEMUI_PACKAGE.qs.QSFragment"
        )
        val canvasEngineClass =
            findClass("$SYSTEMUI_PACKAGE.wallpapers.ImageWallpaper\$CanvasEngine")
        val centralSurfacesImplClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.CentralSurfacesImpl")
        val scrimControllerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimController")
        val scrimViewClass = findClass("$SYSTEMUI_PACKAGE.scrim.ScrimView")
        val aodBurnInLayerClass =
            findClass("$SYSTEMUI_PACKAGE.keyguard.ui.view.layout.sections.AodBurnInLayer")
        val keyguardBottomAreaViewClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.phone.KeyguardBottomAreaView")

        aodBurnInLayerClass
            .hookConstructor()
            .runAfter { param ->
                if (!showDepthWallpaper) return@runAfter

                val entryV = param.thisObject as View

                entryV.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            val rootView = entryV.parent as ViewGroup

                            if (!mLayersCreated) {
                                createLayers()
                            }

                            reAddView(
                                rootView,
                                mWallpaperForeground,
                                0
                            )
                            reAddView(
                                rootView,
                                rootView.findViewById(
                                    mContext.resources.getIdentifier(
                                        "lockscreen_clock_view_large",
                                        "id",
                                        mContext.packageName
                                    )
                                ),
                                0
                            )
                            reAddView(
                                rootView,
                                rootView.findViewById(
                                    mContext.resources.getIdentifier(
                                        "lockscreen_clock_view",
                                        "id",
                                        mContext.packageName
                                    )
                                ),
                                0
                            )
                        }, 1000)
                    }

                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }

        scrimViewClass
            .hookMethod("setViewAlpha")
            .runBefore { param ->
                if (!mLayersCreated) return@runBefore

                if (showOnAOD && getObjectField(
                        mScrimController,
                        "mState"
                    ).toString() != "KEYGUARD"
                ) {
                    mWallpaperForeground.post { mWallpaperForeground.alpha = foregroundAlpha }
                } else if (getObjectField(
                        mScrimController,
                        "mNotificationsScrim"
                    ) == param.thisObject
                ) { // instead of using the mScrimName since older ones don't have that field
                    val mScrimBehindAlphaKeyguard = getFloatField(
                        mScrimController,
                        "mScrimBehindAlphaKeyguard"
                    )

                    var notificationAlpha = param.args[0] as Float

                    if (notificationAlpha < mScrimBehindAlphaKeyguard) {
                        notificationAlpha = 0f
                    }

                    val foregroundAlpha = if (notificationAlpha > mScrimBehindAlphaKeyguard) {
                        (1f - notificationAlpha) / (1f - mScrimBehindAlphaKeyguard)
                    } else {
                        1f
                    }

                    mWallpaperForeground.post { mWallpaperForeground.alpha = foregroundAlpha }
                }
            }

        centralSurfacesImplClass
            .hookMethod("start")
            .runAfter {
                val scrimBehind = getObjectField(mScrimController, "mScrimBehind") as View
                val rootView = scrimBehind.parent as ViewGroup

                val targetView = rootView.findViewById<ViewGroup>(
                    mContext.resources.getIdentifier(
                        "notification_container_parent",
                        "id",
                        mContext.packageName
                    )
                )

                if (!mLayersCreated) {
                    createLayers()
                }

                reAddView(rootView, mWallpaperBackground, 0)
                reAddView(targetView, mWallpaperForeground, 1)
            }

        centralSurfacesImplClass
            .hookMethod("onStartedWakingUp")
            .suppressError()
            .runAfter { setDepthWallpaper() }

        canvasEngineClass
            .hookMethod("onSurfaceDestroyed")
            .runAfter { param ->
                // lockscreen wallpaper changed
                if (showDepthWallpaper && !showCustomImages && isLockScreenWallpaper(param.thisObject)) {
                    invalidateCache()
                }
            }

        canvasEngineClass
            .hookMethod("onCreate")
            .runAfter { param ->
                if (callMethod(
                        getObjectField(
                            param.thisObject,
                            "mWallpaperManager"
                        ),
                        "getWallpaperInfo",
                        WallpaperManager.FLAG_LOCK
                    ) != null && !showCustomImages
                ) { // it's live wallpaper. we can't use that
                    invalidateCache()
                }
            }

        canvasEngineClass
            .hookMethod("drawFrameOnCanvas")
            .runAfter { param ->
                wallpaperProcessorThread?.interrupt()

                if (showDepthWallpaper && !showCustomImages && isLockScreenWallpaper(param.thisObject)) {
                    wallpaperProcessorThread = Thread {
                        val wallpaperBitmap = Bitmap.createBitmap((param.args[0] as Bitmap))
                        val cacheIsValid: Boolean = assertCache(wallpaperBitmap)

                        val displayBounds = (callMethod(
                            param.thisObject,
                            "getDisplayContext"
                        ) as Context)
                            .getSystemService(
                                WindowManager::class.java
                            )
                            .currentWindowMetrics
                            .bounds

                        val ratioW = 1f * displayBounds.width() / wallpaperBitmap.width
                        val ratioH = 1f * displayBounds.height() / wallpaperBitmap.height

                        val desiredHeight = Math.round(
                            max(
                                ratioH.toDouble(),
                                ratioW.toDouble()
                            ) * wallpaperBitmap.height
                        ).toInt()
                        val desiredWidth = Math.round(
                            max(
                                ratioH.toDouble(),
                                ratioW.toDouble()
                            ) * wallpaperBitmap.width
                        ).toInt()

                        val xPixelShift = (desiredWidth - displayBounds.width()) / 2
                        val yPixelShift = (desiredHeight - displayBounds.height()) / 2

                        val scaledWallpaperBitmap = Bitmap.createScaledBitmap(
                            wallpaperBitmap,
                            desiredWidth,
                            desiredHeight,
                            true
                        ).let {
                            Bitmap.createBitmap(
                                it,
                                xPixelShift,
                                yPixelShift,
                                displayBounds.width(),
                                displayBounds.height()
                            )
                        }.let {
                            Bitmap.createBitmap(it)
                        }

                        try {
                            val file = File(backgroundPath)
                            file.parentFile?.mkdirs()
                            val out = FileOutputStream(file)
                            scaledWallpaperBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            out.flush()
                            out.close()
                        } catch (throwable: IOException) {
                            log(TAG + throwable)
                        }

                        if (!mLayersCreated) {
                            createLayers()
                        }

                        mWallpaperBackground.post {
                            mWallpaperBitmapContainer.background = BitmapDrawable(
                                mContext.resources,
                                scaledWallpaperBitmap
                            )
                            if (mScrimController != null) {
                                mWallpaperDimmingOverlay.setBackgroundColor(Color.BLACK)
                                mWallpaperDimmingOverlay.alpha = getFloatField(
                                    mScrimController,
                                    "mScrimBehindAlphaKeyguard"
                                )
                            }
                        }

                        if (!cacheIsValid) {
                            val callback = object : IExtractSubjectCallback.Stub() {
                                override fun onStart(message: String) {
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show()
                                    }
                                }

                                override fun onResult(success: Boolean, message: String) {
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            if (mAiMode == 0) {
                                enqueueProxyCommand { proxy ->
                                    proxy.extractSubject(
                                        scaledWallpaperBitmap,
                                        foregroundPath,
                                        callback
                                    )
                                }
                            } else {
                                sendPluginIntent()
                            }
                        }

                        wallpaperProcessorThread = null
                    }

                    wallpaperProcessorThread?.start()
                }
            }

        scrimControllerClass
            .hookConstructor()
            .runAfter { param -> mScrimController = param.thisObject }

        scrimControllerClass
            .hookMethod("applyAndDispatchState")
            .runAfter { setDepthWallpaper() }

        qsImplClass
            .hookMethod("setQsExpansion")
            .runAfter { param ->
                if (callMethod(param.thisObject, "isKeyguardState") as Boolean) {
                    setDepthWallpaper()
                }
            }

        /*
         * Custom depth wallpaper images
         */
        keyguardBottomAreaViewClass
            .hookMethod("onConfigurationChanged")
            .runAfter { setCustomDepthWallpaper() }

        setCustomDepthWallpaper()
    }

    private fun sendPluginIntent() {
        try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    mContext,
                    modRes.getString(R.string.depth_wallpaper_subject_extraction_started),
                    Toast.LENGTH_LONG
                ).show()
            }

            mContext.sendBroadcast(
                Intent(ACTION_EXTRACT_SUBJECT).apply {
                    setComponent(
                        ComponentName(
                            AI_PLUGIN_PACKAGE,
                            "$AI_PLUGIN_PACKAGE.receivers.SubjectExtractionReceiver"
                        )
                    )
                    putExtra("sourcePath", backgroundPath)
                    putExtra("destinationPath", foregroundPath)
                    setPackage(mContext.packageName)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
            )
        } catch (_: Throwable) {
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun assertCache(wallpaperBitmap: Bitmap): Boolean {
        var cacheIsValid = false

        try {
            val wallpaperCacheFile = File(backgroundPath)

            val compressedBitmap = ByteArrayOutputStream()
            wallpaperBitmap.compress(Bitmap.CompressFormat.JPEG, 100, compressedBitmap)
            if (wallpaperCacheFile.exists()) {
                val cacheStream = FileInputStream(wallpaperCacheFile)

                if (cacheStream.readAllBytes().contentEquals(compressedBitmap.toByteArray())) {
                    cacheIsValid = true
                } else {
                    val newCacheStream = FileOutputStream(wallpaperCacheFile)
                    compressedBitmap.writeTo(newCacheStream)
                    newCacheStream.close()
                }
                cacheStream.close()
            }
            compressedBitmap.close()
        } catch (ignored: Throwable) {
        }

        if (!cacheIsValid) {
            invalidateCache()
        }

        return cacheIsValid
    }

    private fun createLayers() {
        mWallpaperForeground = FrameLayout(mContext)
        mWallpaperBackground = FrameLayout(mContext)
        mWallpaperDimmingOverlay = FrameLayout(mContext)
        mWallpaperBitmapContainer = FrameLayout(mContext)

        val layoutParams = FrameLayout.LayoutParams(
            MATCH_PARENT,
            MATCH_PARENT
        )

        mWallpaperDimmingOverlay.setBackgroundColor(
            if (File(backgroundPath).exists()) {
                Color.BLACK
            } else {
                Color.TRANSPARENT
            }
        )
        mWallpaperDimmingOverlay.alpha = 0F

        mWallpaperDimmingOverlay.layoutParams = layoutParams
        mWallpaperBitmapContainer.layoutParams = layoutParams

        mWallpaperBackground.addView(mWallpaperBitmapContainer)
        mWallpaperBackground.addView(mWallpaperDimmingOverlay)

        mWallpaperForeground.layoutParams = layoutParams
        mWallpaperBackground.layoutParams = layoutParams

        mWallpaperForeground.id = View.generateViewId()

        mLayersCreated = true
    }

    private fun isLockScreenWallpaper(canvasEngine: Any): Boolean {
        return ((getWallpaperFlag(canvasEngine) and WallpaperManager.FLAG_LOCK) == WallpaperManager.FLAG_LOCK)
    }

    private fun setDepthWallpaper() {
        if (mScrimController == null) return

        val state = getObjectField(mScrimController, "mState").toString()
        val showForeground = (showDepthWallpaper &&
                (state == "KEYGUARD" || (showOnAOD && (state == "AOD" || state == "PULSING"))))

        if (showForeground) {
            if ((!mWallpaperForegroundCacheValid || mWallpaperForeground.background == null) &&
                File(foregroundPath).exists()
            ) {
                try {
                    FileInputStream(foregroundPath).use { inputStream ->
                        val bitmapDrawable = BitmapDrawable.createFromStream(
                            inputStream,
                            ""
                        )
                        bitmapDrawable!!.alpha = 255

                        mForegroundDimmingOverlay = bitmapDrawable.constantState!!
                            .newDrawable().mutate()
                        mForegroundDimmingOverlay!!.setTint(Color.BLACK)

                        mWallpaperForeground.background = LayerDrawable(
                            arrayOf(
                                bitmapDrawable,
                                mForegroundDimmingOverlay
                            )
                        )
                        mWallpaperForegroundCacheValid = true
                    }
                } catch (ignored: Throwable) {
                }
            }

            if (mWallpaperForegroundCacheValid && mWallpaperForeground.background != null) {
                mWallpaperForeground.background.alpha = (foregroundAlpha * 255).toInt()

                if (state != "KEYGUARD") { // AOD
                    mForegroundDimmingOverlay!!.alpha = 192
                } else {
                    // this is the dimmed wallpaper coverage
                    mForegroundDimmingOverlay!!.alpha = if (keepLockScreenShade) Math.round(
                        getFloatField(
                            mScrimController,
                            "mScrimBehindAlphaKeyguard"
                        ) * 240 // A tad bit lower than max. show it a bit lighter than other stuff
                    ) else 0

                    mWallpaperDimmingOverlay.alpha = getFloatField(
                        mScrimController,
                        "mScrimBehindAlphaKeyguard"
                    )
                }

                mWallpaperBackground.visibility = View.VISIBLE
                mWallpaperForeground.visibility = View.VISIBLE
            }
        } else if (mLayersCreated) {
            mWallpaperForeground.visibility = View.GONE

            if (state == "UNLOCKED") {
                mWallpaperBackground.visibility = View.GONE
            }
        }
    }

    private fun getWallpaperFlag(canvasEngine: Any): Int {
        return callMethod(canvasEngine, "getWallpaperFlags") as Int
    }

    private fun invalidateCache() { // invalidate lock screen wallpaper subject cache
        mWallpaperForegroundCacheValid = false

        if (mLayersCreated) {
            mWallpaperForeground.post {
                mWallpaperForeground.visibility = View.GONE
                mWallpaperForeground.background = null
                mWallpaperBackground.visibility = View.GONE
                mWallpaperBitmapContainer.background = null
            }
        }

        try {
            if (File(foregroundPath).exists()) {
                File(foregroundPath).delete()
            }
        } catch (ignored: Throwable) {
        }
    }

    /*
     * Custom depth wallpaper images
     */
    private fun setCustomDepthWallpaper() {
        if (!showDepthWallpaper || !showCustomImages) return

        if (!mLayersCreated) {
            createLayers()
        }

        try {
            val mainHandler = Handler(Looper.getMainLooper())
            val executor = Executors.newSingleThreadScheduledExecutor()

            executor.scheduleAtFixedRate({
                val androidDir =
                    File(Environment.getExternalStorageDirectory().toString() + "/Android")

                if (androidDir.isDirectory) {
                    mainHandler.post {
                        try {
                            if (File(backgroundPath).exists()) {
                                FileInputStream(backgroundPath).use { inputStream ->
                                    val bitmapDrawable = BitmapDrawable.createFromStream(
                                        inputStream,
                                        ""
                                    )!!.apply {
                                        alpha = 255
                                    }

                                    mWallpaperBackground.post {
                                        mWallpaperBitmapContainer.background = bitmapDrawable

                                        if (mScrimController != null) {
                                            mWallpaperDimmingOverlay.setBackgroundColor(Color.BLACK)
                                            mWallpaperDimmingOverlay.alpha = getFloatField(
                                                mScrimController,
                                                "mScrimBehindAlphaKeyguard"
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (ignored: Throwable) {
                        }

                        // this sets the dimmed foreground wallpaper
                        setDepthWallpaper()
                    }

                    executor.shutdown()
                    executor.shutdownNow()
                }
            }, 0, 5, TimeUnit.SECONDS)
        } catch (ignored: Throwable) {
        }
    }

    private fun reAddView(parentView: ViewGroup, childView: View?, index: Int) {
        childView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(childView)
            parentView.addView(view, index)
        }
    }

    companion object {
        private val TAG = "Iconify - ${DepthWallpaperA14::class.java.simpleName}: "
    }
}