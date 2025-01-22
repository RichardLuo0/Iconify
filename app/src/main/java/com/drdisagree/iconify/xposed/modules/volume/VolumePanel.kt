package com.drdisagree.iconify.xposed.modules.volume

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.drdisagree.iconify.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.common.Preferences.VOLUME_COLORED_RINGER_ICON
import com.drdisagree.iconify.common.Preferences.VOLUME_PANEL_PERCENTAGE
import com.drdisagree.iconify.common.Preferences.VOLUME_PANEL_SAFETY_WARNING
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.SettingsLibUtils
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.toPx
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getFieldSilently
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setField
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import com.drdisagree.iconify.xposed.utils.XPrefs.XprefsIsInitialized
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlin.math.ceil
import kotlin.properties.Delegates

@SuppressLint("DiscouragedApi", "DefaultLocale")
class VolumePanel(context: Context) : ModPack(context) {

    private var showPercentage = false
    private var showWarning = true
    private var coloredRingerIcon = false
    private lateinit var mSelectedRingerIcon: ImageView
    private var ringerIconDefaultColor by Delegates.notNull<Int>()
    private var ringerIconTextColor by Delegates.notNull<Int>()

    override fun updatePrefs(vararg key: String) {
        if (!XprefsIsInitialized) return

        Xprefs.apply {
            showPercentage = getBoolean(VOLUME_PANEL_PERCENTAGE, false)
            showWarning = getBoolean(VOLUME_PANEL_SAFETY_WARNING, true)
            coloredRingerIcon = getBoolean(VOLUME_COLORED_RINGER_ICON, false)
        }

        if (key.isNotEmpty()) {
            key[0].let {
                if (it == VOLUME_COLORED_RINGER_ICON) {
                    setRingerIconColor()
                }
            }
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        coloredSelectedRingerIcon()
        showVolumePercentage()
        showSafetyWarning()
    }

    private fun coloredSelectedRingerIcon() {
        val volumeDialogImplClass = findClass("$SYSTEMUI_PACKAGE.volume.VolumeDialogImpl")

        volumeDialogImplClass
            .hookMethod("initDialog")
            .runAfter { param ->
                mSelectedRingerIcon = param.thisObject.getField(
                    "mSelectedRingerIcon"
                ) as ImageView

                ringerIconDefaultColor = mSelectedRingerIcon.imageTintList!!.defaultColor
                ringerIconTextColor = SettingsLibUtils.getColorAttrDefaultColor(
                    mSelectedRingerIcon.context,
                    android.R.attr.textColorPrimary
                )

                setRingerIconColor()
            }
    }

    private fun setRingerIconColor() {
        mSelectedRingerIcon.imageTintList = ColorStateList.valueOf(
            if (coloredRingerIcon) {
                ringerIconTextColor
            } else {
                ringerIconDefaultColor
            }
        )
    }

    private fun showVolumePercentage() {
        val volumeDialogImplClass = findClass("$SYSTEMUI_PACKAGE.volume.VolumeDialogImpl")
        val audioStreamStateClass = findClass(
            "$SYSTEMUI_PACKAGE.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel\$State",
            suppressError = true
        )

        volumeDialogImplClass
            .hookMethod("initRow")
            .runAfter { param ->
                if (!showPercentage) return@runAfter

                val rowHeader: TextView = param.args[0].getField("header") as TextView

                if ((rowHeader.parent as ViewGroup).findViewById<TextView>(
                        mContext.resources.getIdentifier(
                            "volume_number",
                            "id",
                            mContext.packageName
                        )
                    ) != null
                ) return@runAfter

                val volumeNumber = createVolumeTextView()
                (rowHeader.parent as ViewGroup).addView(volumeNumber, 0)

                param.args[0].setField(
                    "number",
                    (param.args[0].getField("view") as View).findViewById(
                        mContext.resources.getIdentifier(
                            "volume_number",
                            "id",
                            mContext.packageName
                        )
                    )
                )
            }

        volumeDialogImplClass
            .hookMethod("updateVolumeRowH")
            .runAfter { param ->
                if (!showPercentage) return@runAfter

                val volumeNumber: TextView =
                    (param.args[0].getField("view") as View).findViewById(
                        mContext.resources.getIdentifier(
                            "volume_number",
                            "id",
                            mContext.packageName
                        )
                    ) ?: return@runAfter

                val mState: Any = param.thisObject.getFieldSilently("mState") ?: return@runAfter

                val ss = mState
                    .getField("states")
                    .callMethod(
                        "get",
                        param.args[0].getField("stream")
                    ) ?: return@runAfter

                val levelMax: Int = ss.getField("levelMax") as Int

                volumeNumber.let {
                    if (it.text.isEmpty()) {
                        it.text = "0"
                    }

                    if (it.text.contains("%")) {
                        it.text = it.text.subSequence(0, it.text.length - 1)
                    }

                    var level = ceil(it.text.toString().toFloat() / levelMax * 100f).toInt()

                    if (level > 100) {
                        level = 100
                    } else if (level < 0) {
                        level = 0
                    }

                    it.text = String.format("%d%%", level)
                }
            }

        // Compose implementation of extended volume panel
        audioStreamStateClass
            .hookConstructor()
            .suppressError()
            .runAfter { param ->
                if (!showPercentage) return@runAfter

                val currentValue = param.thisObject.getField("value") as Float
                val maxValue = param.thisObject
                    .getField("valueRange")
                    .getField("_endInclusive") as Float
                val percentage = 100 * currentValue / maxValue
                var label = param.thisObject.getField("label") as String
                label = String.format("$label - ${Math.round(percentage)}%%")

                param.thisObject.setField("label", label)
            }
    }

    private fun showSafetyWarning() {
        val volumeDialogImplClass = findClass("$SYSTEMUI_PACKAGE.volume.VolumeDialogImpl")

        volumeDialogImplClass
            .hookMethod(
                "onShowSafetyWarning",
                "showSafetyWarningH"
            )
            .runBefore { param ->
                if (!showWarning) {
                    param.result = null
                }
            }
    }

    private fun createVolumeTextView(): TextView {
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = mContext.toPx(8)
        }

        val volumeNumber = TextView(mContext).apply {
            layoutParams = params
            id = mContext.resources.getIdentifier(
                "volume_number",
                "id",
                mContext.packageName
            )
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(
                mContext.resources.getColor(
                    mContext.resources.getIdentifier(
                        "android:color/system_accent1_300",
                        "color",
                        mContext.packageName
                    ), mContext.theme
                )
            )
            text = String.format("%d%%", 0)
        }

        return volumeNumber
    }
}