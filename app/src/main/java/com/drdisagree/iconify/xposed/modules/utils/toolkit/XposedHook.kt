package com.drdisagree.iconify.xposed.modules.utils.toolkit

import android.content.res.XResources
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedBridge.hookMethod
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.findAndHookConstructor
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LayoutInflated
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method
import java.util.regex.Pattern

class XposedHook {
    companion object {
        val TAG = "Iconify - ${XposedHook::class.java.simpleName}: "
        lateinit var loadPackageParam: XC_LoadPackage.LoadPackageParam

        fun init(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
            this.loadPackageParam = loadPackageParam
        }

        fun findClass(vararg classNames: String): Class<*>? {
            if (::loadPackageParam.isInitialized.not()) {
                throw IllegalStateException("XposedHook.init() must be called before XposedHook.findClass()")
            }

            for (className in classNames) {
                val clazz = XposedHelpers.findClassIfExists(className, loadPackageParam.classLoader)
                if (clazz != null) return clazz
            }

            if (classNames.size == 1) {
                XposedBridge.log(TAG + "Class not found: ${classNames[0]}")
            } else {
                XposedBridge.log(TAG + "None of the classes were found: ${classNames.joinToString()}")
            }

            return null
        }
    }
}

fun Class<*>?.hookMethod(vararg methodNames: String): MethodHookHelper {
    return MethodHookHelper(this, methodNames)
}

fun Class<*>?.hookConstructor(): MethodHookHelper {
    return MethodHookHelper(this)
}

fun Class<*>?.hookMethodMatchPattern(methodNamePattern: String): MethodHookHelper {
    return MethodHookHelper(this, arrayOf(methodNamePattern), true)
}

class MethodHookHelper(
    private val clazz: Class<*>?,
    private val methodNames: Array<out String>? = null,
    private val isPattern: Boolean = false
) {

    private var parameterTypes: Array<Any?>? = null
    private var printError: Boolean = true

    @Suppress("UNCHECKED_CAST")
    fun parameters(vararg parameterTypes: Any?): MethodHookHelper {
        this.parameterTypes = parameterTypes as Array<Any?>?
        return this
    }

    fun run(callback: XC_MethodHook): MethodHookHelper {
        if (clazz == null) return this

        if (methodNames.isNullOrEmpty()) { // hooking constructor
            hookConstructor(callback)
        } else { // hooking method
            for (methodName in methodNames) {
                if (isPattern) {
                    val pattern = Pattern.compile(methodName)
                    clazz.declaredMethods.forEach { method ->
                        if (pattern.matcher(method.name).matches()) {
                            hookMethod(method, callback)
                        }
                    }
                } else {
                    clazz.declaredMethods.find { it.name == methodName }?.let { method ->
                        hookMethod(method, callback)
                    } ?: run {
                        if (printError && methodNames!!.size == 1) {
                            XposedBridge.log(XposedHook.TAG + "Method not found: $methodName")
                        }
                    }
                }
            }
        }

        return this
    }

    fun runBefore(callback: (XC_MethodHook.MethodHookParam) -> Unit): MethodHookHelper {
        if (clazz == null) return this

        if (methodNames.isNullOrEmpty()) { // hooking constructor
            hookConstructorBefore(callback)
        } else { // hooking method
            for (methodName in methodNames) {
                if (isPattern) {
                    val pattern = Pattern.compile(methodName)
                    clazz.declaredMethods.forEach { method ->
                        if (pattern.matcher(method.name).matches()) {
                            hookMethodBefore(method, callback)
                        }
                    }
                } else {
                    clazz.declaredMethods.find { it.name == methodName }?.let { method ->
                        hookMethodBefore(method, callback)
                    } ?: run {
                        if (printError && methodNames!!.size == 1) {
                            XposedBridge.log(XposedHook.TAG + "Method not found: $methodName")
                        }
                    }
                }
            }
        }

        return this
    }

    fun runAfter(callback: (XC_MethodHook.MethodHookParam) -> Unit): MethodHookHelper {
        if (clazz == null) return this

        if (methodNames.isNullOrEmpty()) { // hooking constructor
            hookConstructorAfter(callback)
        } else { // hooking method
            for (methodName in methodNames) {
                if (isPattern) {
                    val pattern = Pattern.compile(methodName)
                    clazz.declaredMethods.forEach { method ->
                        if (pattern.matcher(method.name).matches()) {
                            hookMethodAfter(method, callback)
                        }
                    }
                } else {
                    clazz.declaredMethods.find { it.name == methodName }?.let { method ->
                        hookMethodAfter(method, callback)
                    } ?: run {
                        if (printError && methodNames!!.size == 1) {
                            XposedBridge.log(XposedHook.TAG + "Method not found: $methodName")
                        }
                    }
                }
            }
        }

        return this
    }

    fun replace(callback: (XC_MethodHook.MethodHookParam) -> Unit): MethodHookHelper {
        if (clazz == null || methodNames.isNullOrEmpty()) return this

        for (methodName in methodNames) {
            if (isPattern) {
                val pattern = Pattern.compile(methodName)
                clazz.declaredMethods.forEach { method ->
                    if (pattern.matcher(method.name).matches()) {
                        hookMethodReplace(method, callback)
                    }
                }
            } else {
                clazz.declaredMethods.find { it.name == methodName }?.let { method ->
                    hookMethodReplace(method, callback)
                } ?: run {
                    if (printError && methodNames!!.size == 1) {
                        XposedBridge.log(XposedHook.TAG + "Method not found: $methodName")
                    }
                }
            }
        }

        return this
    }

    private fun hookConstructor(callback: XC_MethodHook): MethodHookHelper {
        if (clazz == null) return this

        if (parameterTypes.isNullOrEmpty()) {
            hookAllConstructors(clazz, callback)
        } else {
            findAndHookConstructor(
                clazz,
                *parameterTypes!!,
                callback
            )
        }

        return this
    }

    private fun hookConstructorBefore(callback: (XC_MethodHook.MethodHookParam) -> Unit): MethodHookHelper {
        if (clazz == null) return this

        if (parameterTypes.isNullOrEmpty()) {
            hookAllConstructors(clazz, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    callback(param)
                }
            })
        } else {
            findAndHookConstructor(
                clazz,
                *parameterTypes!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        callback(param)
                    }
                }
            )
        }

        return this
    }

    private fun hookConstructorAfter(callback: (XC_MethodHook.MethodHookParam) -> Unit): MethodHookHelper {
        if (clazz == null) return this

        if (parameterTypes.isNullOrEmpty()) {
            hookAllConstructors(clazz, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    callback(param)
                }
            })
        } else {
            findAndHookConstructor(
                clazz,
                *parameterTypes!!,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        callback(param)
                    }
                }
            )
        }

        return this
    }

    private fun hookMethodBefore(
        method: Method,
        callback: (XC_MethodHook.MethodHookParam) -> Unit
    ) {
        if (parameterTypes.isNullOrEmpty()) {
            hookAllMethods(clazz, method.name, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    callback(param)
                }
            })
        } else {
            findAndHookMethod(
                clazz,
                method.name,
                *parameterTypes!!,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        callback(param)
                    }
                }
            )
        }
    }

    private fun hookMethodAfter(
        method: Method,
        callback: (XC_MethodHook.MethodHookParam) -> Unit
    ) {
        if (parameterTypes.isNullOrEmpty()) {
            hookAllMethods(clazz, method.name, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    callback(param)
                }
            })
        } else {
            findAndHookMethod(
                clazz,
                method.name,
                *parameterTypes!!,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        callback(param)
                    }
                }
            )
        }
    }

    private fun hookMethodReplace(
        method: Method,
        callback: (XC_MethodHook.MethodHookParam) -> Unit
    ) {
        if (parameterTypes.isNullOrEmpty()) {
            hookAllMethods(clazz, method.name, object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    callback(param)
                    return null
                }
            })
        } else {
            findAndHookMethod(
                clazz,
                method.name,
                *parameterTypes!!,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        callback(param)
                        return null
                    }
                }
            )
        }
    }

    /*
     * Call before running any hook
     */
    fun suppressError(): MethodHookHelper {
        printError = false
        return this
    }
}

fun XResources.hookLayout(): LayoutHookHelper {
    return LayoutHookHelper(this)
}

class LayoutHookHelper(private val xResources: XResources) {

    private var packageName: String? = null
    private var resourceType: String? = null
    private var resourceName: String? = null
    private var printError: Boolean = true

    fun packageName(packageName: String): LayoutHookHelper {
        this.packageName = packageName
        return this
    }

    fun resource(resourceType: String, resourceName: String): LayoutHookHelper {
        this.resourceType = resourceType
        this.resourceName = resourceName
        return this
    }

    fun run(callback: (XC_LayoutInflated.LayoutInflatedParam) -> Unit): LayoutHookHelper {
        if (packageName == null || resourceType == null || resourceName == null) {
            throw IllegalArgumentException("packageName, resourceType and resourceName must be set")
        }

        try {
            xResources.hookLayout(
                packageName,
                resourceType,
                resourceName,
                object : XC_LayoutInflated() {
                    override fun handleLayoutInflated(param: LayoutInflatedParam) {
                        callback(param)
                    }
                }
            )
        } catch (throwable: Throwable) {
            if (printError) {
                XposedBridge.log(XposedHook.TAG + throwable)
            }
        }

        return this
    }

    /*
     * Call before running any hook
     */
    fun suppressError(): LayoutHookHelper {
        printError = false
        return this
    }
}