package com.drdisagree.iconify.xposed.modules.themes

import android.service.quicksettings.Tile
import com.drdisagree.iconify.xposed.HookEntry.Companion.enqueueProxyCommand
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.getObjectField

object Utils {

    fun getTileState(param: XC_MethodHook.MethodHookParam): Pair<Boolean, Boolean> {
        var isDisabledState: Boolean
        var isActiveState: Boolean
        val idx = if (param.args.size > 1) 1 else 0

        try {
            isDisabledState = try {
                getObjectField(
                    param.args[idx],
                    "disabledByPolicy"
                ) as Boolean ||
                        getObjectField(
                            param.args[idx],
                            "state"
                        ) as Int == Tile.STATE_UNAVAILABLE
            } catch (throwable: Throwable) {
                getObjectField(
                    param.args[idx],
                    "state"
                ) as Int == Tile.STATE_UNAVAILABLE
            }

            isActiveState = try {
                getObjectField(
                    param.args[idx],
                    "state"
                ) as Int == Tile.STATE_ACTIVE
            } catch (throwable: Throwable) {
                try {
                    param.args[idx] as Int == Tile.STATE_ACTIVE
                } catch (throwable1: Throwable) {
                    try {
                        param.args[idx] as Boolean
                    } catch (throwable2: Throwable) {
                        false
                    }
                }
            }
        } catch (ignored: Throwable) {
            isDisabledState = param.args[idx] == Tile.STATE_UNAVAILABLE
            isActiveState = param.args[idx] == Tile.STATE_ACTIVE
        }

        return Pair(isDisabledState, isActiveState)
    }

    fun enableOverlay(pkgName: String) {
        enqueueProxyCommand { proxy ->
            proxy.runCommand("cmd overlay enable --user current $pkgName")
            proxy.runCommand("cmd overlay set-priority $pkgName highest")
        }
    }

    fun enableOverlay(pkgName: String, priority: String) {
        enqueueProxyCommand { proxy ->
            proxy.runCommand("cmd overlay enable --user current $pkgName")
            proxy.runCommand("cmd overlay set-priority $pkgName $priority")
        }
    }

    fun enableOverlays(vararg pkgNames: String?) {
        val command = StringBuilder()

        for (pkgName in pkgNames) {
            command.append("cmd overlay enable --user current ").append(pkgName)
                .append("; cmd overlay set-priority ").append(pkgName).append(" highest; ")
        }

        enqueueProxyCommand { proxy ->
            proxy.runCommand(command.toString().trim { it <= ' ' })
        }
    }

    fun disableOverlay(pkgName: String) {
        enqueueProxyCommand { proxy ->
            proxy.runCommand("cmd overlay disable --user current $pkgName")
        }
    }

    fun disableOverlays(vararg pkgNames: String?) {
        val command = StringBuilder()

        for (pkgName in pkgNames) {
            command.append("cmd overlay disable --user current ").append(pkgName).append("; ")
        }

        enqueueProxyCommand { proxy ->
            proxy.runCommand(command.toString().trim { it <= ' ' })
        }
    }
}