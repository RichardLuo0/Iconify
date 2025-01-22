package com.drdisagree.iconify.xposed.modules.extras.utils.toolkit

import android.content.res.XResources
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedBridge.hookMethod
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.findAndHookConstructor
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.getStaticObjectField
import de.robv.android.xposed.callbacks.XC_LayoutInflated
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method
import java.util.regex.Pattern

class XposedHook {
    companion object {
        lateinit var loadPackageParam: XC_LoadPackage.LoadPackageParam

        fun init(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
            Companion.loadPackageParam =
                loadPackageParam
        }

        fun findClass(
            vararg classNames: String,
            suppressError: Boolean = false,
            throwException: Boolean = false
        ): Class<*>? {
            if (Companion::loadPackageParam.isInitialized.not()) {
                throw IllegalStateException("XposedHook.init() must be called before XposedHook.findClass()")
            }

            for (className in classNames) {
                val clazz = XposedHelpers.findClassIfExists(
                    className,
                    loadPackageParam.classLoader
                )
                if (clazz != null) return clazz
            }

            if (throwException) {
                if (classNames.size == 1) {
                    throw Throwable("Class not found: ${classNames[0]}")
                } else {
                    throw Throwable("None of the classes were found: ${classNames.joinToString()}")
                }
            } else if (!suppressError) {
                if (classNames.size == 1) {
                    log(XposedHook, "Class not found: ${classNames[0]}")
                } else {
                    log(XposedHook, "None of the classes were found: ${classNames.joinToString()}")
                }
            }

            return null
        }
    }
}

fun Class<*>?.hookMethod(vararg methodNames: String): MethodHookHelper {
    return MethodHookHelper(
        this,
        methodNames
    )
}

fun Class<*>?.hookConstructor(): MethodHookHelper {
    return MethodHookHelper(this)
}

fun Class<*>?.hookMethodMatchPattern(methodNamePattern: String): MethodHookHelper {
    return MethodHookHelper(
        this,
        arrayOf(methodNamePattern),
        true
    )
}

class MethodHookHelper(
    private val clazz: Class<*>?,
    private val methodNames: Array<out String>? = null,
    private val isPattern: Boolean = false,
    private val method: Method? = null
) {

    constructor(
        clazz: Class<*>?,
        methodNames: Array<out String>? = null,
        isPattern: Boolean = false
    ) : this(
        clazz,
        methodNames,
        isPattern,
        null
    )

    constructor(
        method: Method
    ) : this(
        null,
        null,
        false,
        method
    )

    private var parameterTypes: Array<Any?>? = null
    private var printError: Boolean = true

    @Suppress("UNCHECKED_CAST")
    fun parameters(vararg parameterTypes: Any?): MethodHookHelper {
        this.parameterTypes = parameterTypes as Array<Any?>?
        return this
    }

    fun run(callback: XC_MethodHook): MethodHookHelper {
        if (method != null) { // hooking directly via Method instance
            hookMethod(method, callback)
        } else if (methodNames.isNullOrEmpty()) { // hooking constructor
            hookConstructor(callback)
        } else { // hooking method
            methodNames.forEach { methodName ->
                if (isPattern) {
                    val pattern = Pattern.compile(methodName)
                    clazz?.declaredMethods?.forEach { method ->
                        if (pattern.matcher(method.name).matches()) {
                            hookMethod(method, callback)
                        }
                    }
                } else {
                    clazz?.declaredMethods?.find { it.name == methodName }?.let { method ->
                        hookMethod(method, callback)
                    } ?: run {
                        if (printError && methodNames!!.size == 1) {
                            log(
                                XposedHook,
                                "Method not found: $methodName${if (clazz?.simpleName != null) " in ${clazz.simpleName}" else ""}"
                            )
                        }
                    }
                }
            }
        }

        return this
    }

    fun runBefore(callback: (XC_MethodHook.MethodHookParam) -> Unit): MethodHookHelper {
        if (method != null) { // hooking directly via Method instance
            hookMethodBefore(method, callback)
        } else if (methodNames.isNullOrEmpty()) { // hooking constructor
            hookConstructorBefore(callback)
        } else { // hooking method
            methodNames.forEach { methodName ->
                if (isPattern) {
                    val pattern = Pattern.compile(methodName)
                    clazz?.declaredMethods?.forEach { method ->
                        if (pattern.matcher(method.name).matches()) {
                            hookMethodBefore(method, callback)
                        }
                    }
                } else {
                    clazz?.declaredMethods?.find { it.name == methodName }?.let { method ->
                        hookMethodBefore(method, callback)
                    } ?: run {
                        if (printError && methodNames!!.size == 1) {
                            log(
                                XposedHook,
                                "Method not found: $methodName${if (clazz?.simpleName != null) " in ${clazz.simpleName}" else ""}"
                            )
                        }
                    }
                }
            }
        }

        return this
    }

    fun runAfter(callback: (XC_MethodHook.MethodHookParam) -> Unit): MethodHookHelper {
        if (method != null) { // hooking directly via Method instance
            hookMethodAfter(method, callback)
        } else if (methodNames.isNullOrEmpty()) { // hooking constructor
            hookConstructorAfter(callback)
        } else { // hooking method
            methodNames.forEach { methodName ->
                if (isPattern) {
                    val pattern = Pattern.compile(methodName)
                    clazz?.declaredMethods?.forEach { method ->
                        if (pattern.matcher(method.name).matches()) {
                            hookMethodAfter(method, callback)
                        }
                    }
                } else {
                    clazz?.declaredMethods?.find { it.name == methodName }?.let { method ->
                        hookMethodAfter(method, callback)
                    } ?: run {
                        if (printError && methodNames!!.size == 1) {
                            log(
                                XposedHook,
                                "Method not found: $methodName${if (clazz?.simpleName != null) " in ${clazz.simpleName}" else ""}"
                            )
                        }
                    }
                }
            }
        }

        return this
    }

    fun replace(callback: (XC_MethodHook.MethodHookParam) -> Unit): MethodHookHelper {
        if (method != null) { // hooking directly via Method instance
            hookMethodReplace(method, callback)
        } else {
            methodNames?.forEach { methodName ->
                if (isPattern) {
                    val pattern = Pattern.compile(methodName)
                    clazz?.declaredMethods?.forEach { method ->
                        if (pattern.matcher(method.name).matches()) {
                            hookMethodReplace(method, callback)
                        }
                    }
                } else {
                    clazz?.declaredMethods?.find { it.name == methodName }?.let { method ->
                        hookMethodReplace(method, callback)
                    } ?: run {
                        if (printError && methodNames!!.size == 1) {
                            log(
                                XposedHook,
                                "Method not found: $methodName${if (clazz?.simpleName != null) " in ${clazz.simpleName}" else ""}"
                            )
                        }
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
        if (clazz == null) {
            hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    callback(param)
                }
            })
        } else {
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
    }

    private fun hookMethodAfter(
        method: Method,
        callback: (XC_MethodHook.MethodHookParam) -> Unit
    ) {
        if (clazz == null) {
            hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    callback(param)
                }
            })
        } else {
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
    }

    private fun hookMethodReplace(
        method: Method,
        callback: (XC_MethodHook.MethodHookParam) -> Unit
    ) {
        if (clazz == null) {
            hookMethod(method, object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    callback(param)
                    return null
                }
            })
        } else {
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
    }

    /*
     * Call before running any hook
     */
    fun suppressError(): MethodHookHelper {
        printError = false
        return this
    }
}

fun Method.run(callback: XC_MethodHook): MethodHookHelper {
    return MethodHookHelper(this)
        .run(callback)
}

fun Method.runBefore(callback: (XC_MethodHook.MethodHookParam) -> Unit): MethodHookHelper {
    return MethodHookHelper(this)
        .runBefore(callback)
}

fun Method.runAfter(callback: (XC_MethodHook.MethodHookParam) -> Unit): MethodHookHelper {
    return MethodHookHelper(this)
        .runAfter(callback)
}

fun Method.replace(callback: (XC_MethodHook.MethodHookParam) -> Unit): MethodHookHelper {
    return MethodHookHelper(this)
        .replace(callback)
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

    fun resource(
        resourceType: String,
        resourceName: String
    ): LayoutHookHelper {
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
                log(XposedHook, throwable)
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

fun Any?.callMethod(methodName: String): Any? {
    if (this == null) return null

    return XposedHelpers.callMethod(this, methodName)
}

fun Any?.callMethod(methodName: String, vararg args: Any?): Any? {
    if (this == null) return null

    return XposedHelpers.callMethod(this, methodName, *args)
}

fun Any?.callMethodSilently(methodName: String): Any? {
    if (this == null) return null

    return try {
        XposedHelpers.callMethod(this, methodName)
    } catch (ignored: Throwable) {
        null
    }
}

fun Any?.callMethodSilently(methodName: String, vararg args: Any?): Any? {
    if (this == null) return null

    return try {
        XposedHelpers.callMethod(this, methodName, *args)
    } catch (ignored: Throwable) {
        null
    }
}

fun Class<*>?.callStaticMethod(methodName: String): Any? {
    if (this == null) return null

    return XposedHelpers.callStaticMethod(this, methodName)
}

fun Class<*>?.callStaticMethod(methodName: String, vararg args: Any?): Any? {
    if (this == null) return null

    return XposedHelpers.callStaticMethod(this, methodName, *args)
}

fun Class<*>?.callStaticMethodSilently(methodName: String): Any? {
    if (this == null) return null

    return try {
        XposedHelpers.callStaticMethod(this, methodName)
    } catch (ignored: Throwable) {
        null
    }
}

fun Class<*>?.callStaticMethodSilently(methodName: String, vararg args: Any?): Any? {
    if (this == null) return null

    return try {
        XposedHelpers.callStaticMethod(this, methodName, *args)
    } catch (ignored: Throwable) {
        null
    }
}

fun Any?.getField(fieldName: String): Any {
    if (this == null) throw NoSuchFieldError("Field not found: $fieldName, object is null")

    return XposedHelpers.getObjectField(this, fieldName)
}

fun Any?.getFieldSilently(fieldName: String): Any? {
    if (this == null) return null

    return try {
        XposedHelpers.getObjectField(this, fieldName)
    } catch (ignored: Throwable) {
        null
    }
}

fun Any?.setField(fieldName: String, value: Any?) {
    XposedHelpers.setObjectField(this, fieldName, value)
}

fun Any?.setFieldSilently(fieldName: String, value: Any?) {
    try {
        XposedHelpers.setObjectField(this, fieldName, value)
    } catch (ignored: Throwable) {
    }
}

fun Class<*>?.getStaticField(fieldName: String): Any {
    if (this == null) throw NoSuchFieldError("Field not found: $fieldName, class is null")

    return getStaticObjectField(this, fieldName)
}

fun Class<*>?.getStaticFieldSilently(fieldName: String): Any? {
    if (this == null) return null

    return try {
        getStaticObjectField(this, fieldName)
    } catch (ignored: Throwable) {
        null
    }
}

fun Class<*>?.setStaticField(fieldName: String, value: Any?) {
    XposedHelpers.setStaticObjectField(this, fieldName, value)
}

fun Class<*>?.setStaticFieldSilently(fieldName: String, value: Any?) {
    try {
        XposedHelpers.setStaticObjectField(this, fieldName, value)
    } catch (ignored: Throwable) {
    }
}

fun Any?.getAnyField(vararg fieldNames: String): Any? {
    fieldNames.forEach { fieldName ->
        try {
            return XposedHelpers.getObjectField(this, fieldName)
        } catch (ignored: Throwable) {
        }
    }

    throw NoSuchFieldError("Field not found: ${fieldNames.joinToString()}")
}

fun Any?.setAnyField(value: Any?, vararg fieldNames: String) {
    fieldNames.forEach { fieldName ->
        try {
            return XposedHelpers.setObjectField(this, fieldName, value)
        } catch (ignored: Throwable) {
        }
    }

    throw NoSuchFieldError("Field not found: ${fieldNames.joinToString()}")
}

fun Class<*>?.getAnyStaticField(vararg fieldNames: String): Any? {
    fieldNames.forEach { fieldName ->
        try {
            return getStaticObjectField(this, fieldName)
        } catch (ignored: Throwable) {
        }
    }

    throw NoSuchFieldError("Field not found: ${fieldNames.joinToString()}")
}

fun Any?.getExtraField(fieldName: String): Any {
    return XposedHelpers.getAdditionalInstanceField(this, fieldName)
}

fun Any?.getExtraFieldSilently(fieldName: String): Any? {
    return try {
        XposedHelpers.getAdditionalInstanceField(this, fieldName)
    } catch (ignored: Throwable) {
        null
    }
}

fun Any?.setExtraField(fieldName: String, value: Any?) {
    XposedHelpers.setAdditionalInstanceField(this, fieldName, value)
}