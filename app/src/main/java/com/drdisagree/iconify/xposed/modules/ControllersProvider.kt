package com.drdisagree.iconify.xposed.modules

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.view.View
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.utils.Helpers.isMethodAvailable
import com.drdisagree.iconify.xposed.modules.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.utils.toolkit.hookMethod
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage

class ControllersProvider(context: Context) : ModPack(context) {

    private var mBluetoothEnabled = false

    private var mAccessPointController: Any? = null
    private var mInternetDialogManager: Any? = null
    private var mInternetDialogFactory: Any? = null
    private var mBluetoothTileDialogViewModel: Any? = null

    private var mCellularTile: Any? = null
    private var mBluetoothTile: Any? = null

    private val mMobileDataChangedListeners = ArrayList<OnMobileDataChanged>()
    private val mWifiChangedListeners = ArrayList<OnWifiChanged>()
    private val mBluetoothChangedListeners = ArrayList<OnBluetoothChanged>()
    private val mTorchModeChangedListeners = ArrayList<OnTorchModeChanged>()
    private val mHotspotChangedListeners = ArrayList<OnHotspotChanged>()
    private val mDozeChangedListeners = ArrayList<OnDozingChanged>()

    private var mExpandableClass: Class<*>? = null

    override fun updatePrefs(vararg key: String) {}

    override fun handleLoadPackage(loadPackageParam: XC_LoadPackage.LoadPackageParam) {

        instance = this

        // Network Callbacks
        val callbackHandler =
            findClass("$SYSTEMUI_PACKAGE.statusbar.connectivity.CallbackHandler")
        mExpandableClass = findClass("$SYSTEMUI_PACKAGE.animation.Expandable")

        callbackHandler
            .hookMethod("setMobileDataIndicators")
            .runAfter { param -> onSetMobileDataIndicators(param.args[0]) }

        callbackHandler
            .hookMethod("setIsAirplaneMode")
            .runAfter { param -> onSetIsAirplaneMode(param.args[0]) }

        callbackHandler
            .hookMethod("setNoSims")
            .runAfter { param -> onSetNoSims(param.args[0] as Boolean, param.args[1] as Boolean) }

        callbackHandler
            .hookMethod("setWifiIndicators")
            .runAfter { param -> onWifiChanged(param.args[0]) }

        // Internet Tile - for opening Internet Dialog
        findClass("$SYSTEMUI_PACKAGE.qs.tiles.InternetTile")
            .hookConstructor()
            .runAfter { param -> mCellularTile = param.thisObject }

        // Stole also Internet Dialog Manager in case no tile is available
        findClass("$SYSTEMUI_PACKAGE.statusbar.connectivity.NetworkControllerImpl")
            .hookConstructor()
            .runAfter { param ->
                try {
                    mAccessPointController = getObjectField(param.thisObject, "mAccessPoints")
                } catch (ignored: Throwable) {
                }
                try {
                    mInternetDialogManager =
                        getObjectField(param.thisObject, "mInternetDialogManager")
                } catch (ignored: Throwable) {
                }
                try {
                    mInternetDialogFactory =
                        getObjectField(param.thisObject, "mInternetDialogFactory")
                } catch (ignored: Throwable) {
                }
            }

        // Bluetooth Controller
        val bluetoothControllerImpl =
            findClass("$SYSTEMUI_PACKAGE.statusbar.policy.BluetoothControllerImpl")

        bluetoothControllerImpl
            .hookConstructor()
            .runAfter { param -> mBluetoothController = param.thisObject }

        bluetoothControllerImpl
            .hookMethod("onBluetoothStateChanged")
            .runAfter { param ->
                mBluetoothEnabled = (param.args[0] == 12 || param.args[0] == 11)
                onBluetoothChanged(mBluetoothEnabled)
            }

        bluetoothControllerImpl
            .hookMethod("onConnectionStateChanged")
            .runAfter { onBluetoothChanged(mBluetoothEnabled) }

        bluetoothControllerImpl
            .hookMethod("onAclConnectionStateChanged")
            .runAfter { onBluetoothChanged(mBluetoothEnabled) }

        // Get Bluetooth Tile for Dialog
        findClass("$SYSTEMUI_PACKAGE.qs.tiles.BluetoothTile")
            .hookConstructor()
            .runAfter { param ->
                mBluetoothTile = param.thisObject
                try {
                    mBluetoothTileDialogViewModel =
                        getObjectField(param.thisObject, "mDialogViewModel")
                } catch (ignored: Throwable) {
                }
            }

        // Stole FlashLight Callback
        findClass("$SYSTEMUI_PACKAGE.statusbar.policy.FlashlightControllerImpl")
            .hookConstructor()
            .runAfter { param ->
                getObjectField(param.thisObject, "mTorchCallback").javaClass
                    .hookMethod("onTorchModeChanged")
                    .parameters(String::class.java, Boolean::class.javaPrimitiveType)
                    .runAfter { param2 -> onTorchModeChanged(param2.args[1] as Boolean) }
            }

        // Get Hotspot Callback
        findClass("$SYSTEMUI_PACKAGE.statusbar.policy.HotspotControllerImpl")
            .hookMethod("fireHotspotChangedCallback")
            .runAfter { param ->
                val enabled = getIntField(param.thisObject, "mHotspotState") == 13
                val devices = getIntField(param.thisObject, "mNumConnectedDevices")
                onHotspotChanged(enabled, devices)
            }

        // Hotspot Tile - for setting Hotspot
        findClass("$SYSTEMUI_PACKAGE.qs.tiles.HotspotTile")
            .hookConstructor()
            .runAfter { param ->
                mHotspotTile = param.thisObject
                mHotspotController = getObjectField(param.thisObject, "mHotspotController")
            }

        // Home Controls Tile - for ControlsActivity
        findClass("$SYSTEMUI_PACKAGE.qs.tiles.DeviceControlsTile")
            .hookConstructor()
            .runAfter { param -> mDeviceControlsTile = param.thisObject }

        // Wallet Tile - for opening wallet
        findClass("$SYSTEMUI_PACKAGE.qs.tiles.QuickAccessWalletTile")
            .hookConstructor()
            .runAfter { param -> mWalletTile = param.thisObject }

        // Doze Callback
        findClass("$SYSTEMUI_PACKAGE.statusbar.phone.DozeScrimController")
            .hookMethod("onDozingChanged")
            .runAfter { param -> onDozingChanged(param.args[0] as Boolean) }
    }

    /**
     * Callbacks for Mobile Data
     */
    interface OnMobileDataChanged {
        fun setMobileDataIndicators(mMobileDataIndicators: Any?)
        fun setNoSims(show: Boolean, simDetected: Boolean)
        fun setIsAirplaneMode(mIconState: Any?)
    }

    /**
     * Callback for WiFi
     */
    interface OnWifiChanged {
        fun onWifiChanged(mWifiIndicators: Any?)
    }

    /**
     * Callback for Bluetooth
     */
    interface OnBluetoothChanged {
        fun onBluetoothChanged(enabled: Boolean)
    }

    /**
     * Callback for FlashLight
     */
    interface OnTorchModeChanged {
        fun onTorchModeChanged(enabled: Boolean)
    }

    /**
     * Callback for Hotspot
     */
    interface OnHotspotChanged {
        fun onHotspotChanged(enabled: Boolean, connectedDevices: Int)
    }

    /**
     * Callback for Doze
     */
    interface OnDozingChanged {
        fun onDozingChanged(dozing: Boolean)
    }

    fun registerMobileDataCallback(callback: OnMobileDataChanged) {
        instance.mMobileDataChangedListeners.add(callback)
    }

    /** @noinspection unused
     */
    fun unRegisterMobileDataCallback(callback: OnMobileDataChanged?) {
        instance.mMobileDataChangedListeners.remove(callback)
    }

    fun registerWifiCallback(callback: OnWifiChanged) {
        instance.mWifiChangedListeners.add(callback)
    }

    /** @noinspection unused
     */
    fun unRegisterWifiCallback(callback: OnWifiChanged?) {
        instance.mWifiChangedListeners.remove(callback)
    }

    fun registerBluetoothCallback(callback: OnBluetoothChanged) {
        instance.mBluetoothChangedListeners.add(callback)
    }

    /** @noinspection unused
     */
    fun unRegisterBluetoothCallback(callback: OnBluetoothChanged?) {
        instance.mBluetoothChangedListeners.remove(callback)
    }

    fun registerTorchModeCallback(callback: OnTorchModeChanged) {
        instance.mTorchModeChangedListeners.add(callback)
    }

    /** @noinspection unused
     */
    fun unRegisterTorchModeCallback(callback: OnTorchModeChanged?) {
        instance.mTorchModeChangedListeners.remove(callback)
    }

    fun registerDozingCallback(callback: OnDozingChanged) {
        instance.mDozeChangedListeners.add(callback)
    }

    /** @noinspection unused */
    fun unRegisterDozingCallback(callback: OnDozingChanged?) {
        instance.mDozeChangedListeners.remove(callback)
    }

    fun registerHotspotCallback(callback: OnHotspotChanged) {
        instance.mHotspotChangedListeners.add(callback)
    }

    /** @noinspection unused */
    fun unRegisterHotspotCallback(callback: OnHotspotChanged?) {
        instance.mHotspotChangedListeners.remove(callback)
    }

    private fun onSetMobileDataIndicators(mMobileDataIndicators: Any) {
        for (callback in mMobileDataChangedListeners) {
            try {
                callback.setMobileDataIndicators(mMobileDataIndicators)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun onSetIsAirplaneMode(mMobileDataIndicators: Any) {
        for (callback in mMobileDataChangedListeners) {
            try {
                callback.setIsAirplaneMode(mMobileDataIndicators)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun onSetNoSims(show: Boolean, simDetected: Boolean) {
        for (callback in mMobileDataChangedListeners) {
            try {
                callback.setNoSims(show, simDetected)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun onWifiChanged(wifiIndicators: Any) {
        for (callback in mWifiChangedListeners) {
            try {
                callback.onWifiChanged(wifiIndicators)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun onBluetoothChanged(enabled: Boolean) {
        for (callback in mBluetoothChangedListeners) {
            try {
                callback.onBluetoothChanged(enabled)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun onTorchModeChanged(enabled: Boolean) {
        for (callback in mTorchModeChangedListeners) {
            try {
                callback.onTorchModeChanged(enabled)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun onHotspotChanged(enabled: Boolean, connectedDevices: Int) {
        for (callback in mHotspotChangedListeners) {
            try {
                callback.onHotspotChanged(enabled, connectedDevices)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun onDozingChanged(isDozing: Boolean) {
        for (callback in mDozeChangedListeners) {
            try {
                callback.onDozingChanged(isDozing)
            } catch (ignored: Throwable) {
            }
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private lateinit var instance: ControllersProvider

        private val TAG: String = "Iconify - ${ControllersProvider::class.java.simpleName}: "

        var mBluetoothController: Any? = null
        var mHotspotController: Any? = null

        var mHotspotTile: Any? = null
        var mDeviceControlsTile: Any? = null
        var mWalletTile: Any? = null

        fun getInstance(): ControllersProvider {
            return instance
        }

        fun showInternetDialog(view: View): Boolean {
            if (::instance.isInitialized.not()) {
                log(TAG + "Instance is null")
                return false
            }

            val expandableClassAvailable = instance.mExpandableClass != null

            instance.mAccessPointController?.let { accessPointController ->
                when {
                    isMethodAvailable(
                        instance.mInternetDialogManager,
                        "create",
                        Boolean::class.java,
                        Boolean::class.java,
                        Boolean::class.java,
                        View::class.java
                    ) -> {
                        callMethod(
                            instance.mInternetDialogManager,
                            "create",
                            true,
                            callMethod(accessPointController, "canConfigMobileData"),
                            callMethod(accessPointController, "canConfigWifi"),
                            view
                        )
                        return true
                    }

                    isMethodAvailable(
                        instance.mInternetDialogManager,
                        "create",
                        View::class.java,
                        Boolean::class.java,
                        Boolean::class.java
                    ) -> {
                        callMethod(
                            instance.mInternetDialogManager,
                            "create",
                            view,
                            callMethod(accessPointController, "canConfigMobileData"),
                            callMethod(accessPointController, "canConfigWifi")
                        )
                        return true
                    }

                    isMethodAvailable(
                        instance.mInternetDialogFactory,
                        "create",
                        Boolean::class.java,
                        Boolean::class.java,
                        View::class.java
                    ) -> {
                        callMethod(
                            instance.mInternetDialogFactory,
                            "create",
                            callMethod(accessPointController, "canConfigMobileData"),
                            callMethod(accessPointController, "canConfigWifi"),
                            view
                        )
                        return true
                    }

                    expandableClassAvailable && isMethodAvailable(
                        instance.mInternetDialogManager,
                        "create",
                        Boolean::class.java,
                        Boolean::class.java,
                        instance.mExpandableClass!!
                    ) -> {
                        callMethod(
                            instance.mInternetDialogManager,
                            "create",
                            callMethod(accessPointController, "canConfigMobileData"),
                            callMethod(accessPointController, "canConfigWifi"),
                            null
                        )
                        return true
                    }

                    expandableClassAvailable && isMethodAvailable(
                        instance.mInternetDialogManager,
                        "create",
                        Boolean::class.java,
                        Boolean::class.java,
                        Boolean::class.java,
                        instance.mExpandableClass!!
                    ) -> {
                        callMethod(
                            instance.mInternetDialogManager,
                            "create",
                            true,
                            callMethod(accessPointController, "canConfigMobileData"),
                            callMethod(accessPointController, "canConfigWifi"),
                            null
                        )
                        return true
                    }

                    else -> {
                        log(TAG + "No internet dialog available")
                        return false
                    }
                }
            } ?: run {
                instance.mCellularTile?.let { cellularTile ->
                    when {
                        isMethodAvailable(
                            cellularTile,
                            "handleClick",
                            View::class.java
                        ) -> {
                            callMethod(cellularTile, "handleClick", view)
                            return true
                        }

                        else -> {
                            log(TAG + "No internet tile available")
                            return false
                        }
                    }
                }
            }

            return false
        }

        fun showBluetoothDialog(context: Context, view: View): Boolean {
            if (::instance.isInitialized.not()) {
                log(TAG + "Instance is null")
                return false
            }

            val expandableClassAvailable = instance.mExpandableClass != null

            when {
                isMethodAvailable(
                    instance.mBluetoothTileDialogViewModel,
                    "showDialog",
                    Context::class.java,
                    View::class.java
                ) -> {
                    callMethod(
                        instance.mBluetoothTileDialogViewModel,
                        "showDialog",
                        context,
                        view
                    )
                    return true
                }

                isMethodAvailable(
                    instance.mBluetoothTileDialogViewModel,
                    "showDialog",
                    Context::class.java,
                    View::class.java,
                    Boolean::class.java
                ) -> {
                    val isAutoOn = Settings.System.getInt(
                        context.contentResolver,
                        "qs_bt_auto_on", 0
                    ) == 1
                    callMethod(
                        instance.mBluetoothTileDialogViewModel,
                        "showDialog",
                        context,
                        view,
                        isAutoOn
                    )
                    return true
                }

                expandableClassAvailable && isMethodAvailable(
                    instance.mBluetoothTileDialogViewModel,
                    "showDialog",
                    instance.mExpandableClass!!
                ) -> {
                    // it's calling wrong callMethod() so we have to call it manually
                    return try {
                        instance.mBluetoothTileDialogViewModel!!::class.java.getMethod(
                            "showDialog",
                            instance.mExpandableClass!!
                        ).invoke(instance.mBluetoothTileDialogViewModel!!, null)
                        true
                    } catch (e: Exception) {
                        log(TAG + e)
                        false
                    }
                }

                isMethodAvailable(
                    instance.mBluetoothTile,
                    "handleClick",
                    View::class.java
                ) -> {
                    callMethod(instance.mBluetoothTile, "handleClick", view)
                    return true
                }

                expandableClassAvailable && isMethodAvailable(
                    instance.mBluetoothTile,
                    "handleClick",
                    instance.mExpandableClass!!
                ) -> {
                    callMethod(instance.mBluetoothTile, "handleClick", null)
                    return true
                }

                else -> {
                    log(TAG + "No bluetooth dialog available")
                    return false
                }
            }
        }
    }
}