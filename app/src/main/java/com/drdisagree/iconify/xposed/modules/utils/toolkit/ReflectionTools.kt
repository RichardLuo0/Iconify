package com.drdisagree.iconify.xposed.modules.utils.toolkit

import android.util.ArraySet
import java.lang.reflect.Method
import java.util.regex.Pattern

fun isMethodAvailable(
    target: Any?,
    methodName: String,
    vararg parameterTypes: Class<*>
): Boolean {
    if (target == null) return false

    return try {
        target::class.java.getMethod(methodName, *parameterTypes)
        true
    } catch (ignored: NoSuchMethodException) {
        false
    }
}

fun isFieldAvailable(clazz: Class<*>, fieldName: String): Boolean {
    return try {
        clazz::class.java.getDeclaredField(fieldName)
        true
    } catch (ignored: NoSuchFieldException) {
        false
    }
}

fun findMethod(clazz: Class<*>, namePattern: String): Method? {
    val methods: Array<Method> = clazz.methods

    for (method in methods) {
        if (Pattern.matches(namePattern, method.name)) {
            return method
        }
    }

    return null
}

fun findMethods(clazz: Class<*>, namePattern: String): Set<Method> {
    val result: MutableSet<Method> = ArraySet()
    val methods: Array<Method> = clazz.methods

    for (method in methods) {
        if (Pattern.matches(namePattern, method.name)) {
            result.add(method)
        }
    }

    return result
}