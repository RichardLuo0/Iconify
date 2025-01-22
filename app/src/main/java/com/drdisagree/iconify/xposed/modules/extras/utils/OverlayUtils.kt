package com.drdisagree.iconify.xposed.modules.extras.utils

import com.topjohnwu.superuser.Shell

val isQsTileOverlayEnabled: Boolean
    get() {
        val output = Shell.cmd(
            "[[ $(cmd overlay list | grep -oE '\\[x\\] IconifyComponentQSS[N|P][0-9]+.overlay') ]] && echo 1 || echo 0"
        ).exec().out
        return output.isNotEmpty() && output[0] == "1"
    }

val isPixelVariant: Boolean
    get() {
        val output = Shell.cmd(
            "[[ $(cmd overlay list | grep -oE '\\[x\\] IconifyComponentQSSP[0-9]+.overlay') ]] && echo 1 || echo 0"
        ).exec().out
        return output.isNotEmpty() && output[0] == "1"
    }