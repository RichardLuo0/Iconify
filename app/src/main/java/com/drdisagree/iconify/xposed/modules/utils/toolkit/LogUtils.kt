package com.drdisagree.iconify.xposed.modules.utils.toolkit


import android.content.Context
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.findClassIfExists

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
        log("Class: null not found")
        return
    }
    dumpClass(classObj)
}

private fun dumpClass(className: String, classLoader: ClassLoader?) {
    val ourClass = findClassIfExists(className, classLoader)
    if (ourClass == null) {
        log("Class: $className not found")
        return
    }
    dumpClass(ourClass)
}

private fun dumpClass(ourClass: Class<*>) {
    val ms = ourClass.declaredMethods
    log("\n\nClass: ${ourClass.name}")
    log("extends: ${ourClass.superclass.name}")

    log("Subclasses:")
    val scs = ourClass.classes
    for (c in scs) {
        log(c.name)
    }

    log("Methods:")
    val cons = ourClass.declaredConstructors
    for (m in cons) {
        log(m.name + " - " + " - " + m.parameterCount)
        val cs = m.parameterTypes
        for (c in cs) {
            log("\t\t" + c.typeName)
        }
    }
    for (m in ms) {
        log(m.name + " - " + m.returnType + " - " + m.parameterCount)
        val cs = m.parameterTypes
        for (c in cs) {
            log("\t\t" + c.typeName)
        }
    }

    log("Fields:")
    val fs = ourClass.declaredFields
    for (f in fs) {
        log("\t\t" + f.name + "-" + f.type.name)
    }
    log("End dump\n\n")
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
    log(logMessage)
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