package com.drdisagree.iconify.ui.fragments.xposed

import com.drdisagree.iconify.R
import com.drdisagree.iconify.common.Preferences.HIDE_LOCKSCREEN_CARRIER
import com.drdisagree.iconify.common.Preferences.HIDE_LOCKSCREEN_LOCK_ICON
import com.drdisagree.iconify.common.Preferences.HIDE_LOCKSCREEN_STATUSBAR
import com.drdisagree.iconify.common.Preferences.HIDE_QS_ON_LOCKSCREEN
import com.drdisagree.iconify.ui.activities.MainActivity
import com.drdisagree.iconify.ui.base.ControlledPreferenceFragmentCompat

class Lockscreen : ControlledPreferenceFragmentCompat() {

    override val title: String
        get() = getString(R.string.activity_title_lockscreen)

    override val backButtonEnabled: Boolean
        get() = true

    override val layoutResource: Int
        get() = R.xml.xposed_lockscreen

    override val hasMenu: Boolean
        get() = true

    override fun updateScreen(key: String?) {
        super.updateScreen(key)

        when (key) {
            HIDE_LOCKSCREEN_LOCK_ICON,
            HIDE_QS_ON_LOCKSCREEN,
            HIDE_LOCKSCREEN_CARRIER,
            HIDE_LOCKSCREEN_STATUSBAR -> {
                MainActivity.showOrHidePendingActionButton(
                    activityBinding = (requireActivity() as MainActivity).binding,
                    requiresSystemUiRestart = true
                )
            }
        }
    }
}
