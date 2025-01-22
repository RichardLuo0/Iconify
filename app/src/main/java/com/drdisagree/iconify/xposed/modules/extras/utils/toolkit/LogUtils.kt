package com.drdisagree.iconify.xposed.modules.extras.utils.toolkit


import android.content.Context
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.findClassIfExists

fun log(message: String?) {
    XposedBridge.log(message)
}

fun log(tag: String, message: Any?) {
    XposedBridge.log(
        "Iconify - $tag: $message"
    )
}

fun <T : Any> log(clazz: T, message: Any?) {
    XposedBridge.log(
        "Iconify - ${
            clazz.javaClass.simpleName.replace(
                "\$Companion",
                ""
            )
        }: $message"
    )
}

fun <T : Any> log(clazz: T, throwable: Throwable?) {
    XposedBridge.log(
        "Iconify - ${
            clazz.javaClass.simpleName.replace(
                "\$Companion",
                ""
            )
        }: $throwable"
    )
}

fun <T : Any> log(clazz: T, exception: Exception?) {
    XposedBridge.log(
        "Iconify - ${
            clazz.javaClass.simpleName.replace(
                "\$Companion",
                ""
            )
        }: $exception"
    )
}

fun findAndDumpClass(className: String, classLoader: ClassLoader?): Class<*> {
    dumpClass(className, classLoader)
    return findClass(className, classLoader)
}

fun findAndDumpClassIfExists(className: String, classLoader: ClassLoader?): Class<*> {
    dumpClass(className, classLoader)
    return findClassIfExists(className, classLoader)
}

fun dumpClassObj(classObj: Class<*>?) {
    if (classObj == null) {
        XposedBridge.log("Class: null not found")
        return
    }
    dumpClass(classObj)
}

private fun dumpClass(className: String, classLoader: ClassLoader?) {
    val ourClass = findClassIfExists(className, classLoader)
    if (ourClass == null) {
        XposedBridge.log("Class: $className not found")
        return
    }
    dumpClass(ourClass)
}

private fun dumpClass(ourClass: Class<*>) {
    val ms = ourClass.declaredMethods
    XposedBridge.log("\n\nClass: ${ourClass.name}")
    XposedBridge.log("extends: ${ourClass.superclass.name}")

    XposedBridge.log("Subclasses:")
    val scs = ourClass.classes
    for (c in scs) {
        XposedBridge.log(c.name)
    }

    XposedBridge.log("Methods:")
    val cons = ourClass.declaredConstructors
    for (m in cons) {
        XposedBridge.log(m.name + " - " + " - " + m.parameterCount)
        val cs = m.parameterTypes
        for (c in cs) {
            XposedBridge.log("\t\t" + c.typeName)
        }
    }
    for (m in ms) {
        XposedBridge.log(m.name + " - " + m.returnType + " - " + m.parameterCount)
        val cs = m.parameterTypes
        for (c in cs) {
            XposedBridge.log("\t\t" + c.typeName)
        }
    }

    XposedBridge.log("Fields:")
    val fs = ourClass.declaredFields
    for (f in fs) {
        XposedBridge.log("\t\t" + f.name + "-" + f.type.name)
    }
    XposedBridge.log("End dump\n\n")
}

fun dumpChildViews(context: Context, view: View) {
    if (view is ViewGroup) {
        logViewInfo(context, view, 0)
        dumpChildViewsRecursive(context, view, 0)
    } else {
        logViewInfo(context, view, 0)
    }
}

private fun logViewInfo(context: Context, view: View, indentationLevel: Int) {
    val indentation = repeatString("\t", indentationLevel)
    val viewName = view.javaClass.simpleName
    val superclassName = view.javaClass.superclass?.simpleName ?: "None"
    val backgroundDrawable = view.background
    val childCount = if (view is ViewGroup) view.childCount else 0
    var resourceIdName = "none"
    try {
        val viewId = view.id
        resourceIdName = context.resources.getResourceName(viewId)
    } catch (ignored: Throwable) {
    }
    var logMessage = "$indentation$viewName (Extends: $superclassName) - ID: $resourceIdName"
    if (childCount > 0) {
        logMessage += " - ChildCount: $childCount"
    }
    if (backgroundDrawable != null) {
        logMessage += " - Background: ${backgroundDrawable.javaClass.simpleName}"
    }
    XposedBridge.log(logMessage)
}

private fun dumpChildViewsRecursive(
    context: Context,
    viewGroup: ViewGroup,
    indentationLevel: Int
) {
    for (i in 0 until viewGroup.childCount) {
        val childView = viewGroup.getChildAt(i)
        logViewInfo(context, childView, indentationLevel + 1)
        if (childView is ViewGroup) {
            dumpChildViewsRecursive(context, childView, indentationLevel + 1)
        }
    }
}

@Suppress("SameParameterValue")
private fun repeatString(str: String, times: Int): String {
    val result = StringBuilder()
    for (i in 0 until times) {
        result.append(str)
    }
    return result.toString()
}