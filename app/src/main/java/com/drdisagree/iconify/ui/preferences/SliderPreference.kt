package com.drdisagree.iconify.ui.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.drdisagree.iconify.R
import com.drdisagree.iconify.utils.HapticUtils.weakVibrate
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.RangeSlider
import java.io.StringReader
import java.io.StringWriter
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.Locale
import java.util.Scanner
import kotlin.math.max
import kotlin.math.min

/*
* From Siavash79/rangesliderpreference
* https://github.com/siavash79/rangesliderpreference
*/

class SliderPreference(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int
) : Preference(context, attrs, defStyleAttr) {

    private var valueFrom: Float
    private var valueTo: Float
    private val tickInterval: Float
    private val showResetButton: Boolean
    private val defaultValue: MutableList<Float> = ArrayList()
    private var slider: RangeSlider? = null
    private var titleView: TextView? = null
    private var summaryView: TextView? = null
    private var mResetButton: MaterialButton? = null

    @Suppress("unused")
    private var sliderValue: TextView? = null
    private var valueCount: Int
    private var valueFormat: String? = null
    private val outputScale: Float
    private val isDecimalFormat: Boolean
    private val showDefault: Boolean
    private var decimalFormat: String? = "#.#"

    var updateConstantly: Boolean
    var showValueLabel: Boolean

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    init {
        isSelectable = false
        layoutResource = R.layout.custom_preference_slider

        context.obtainStyledAttributes(attrs, R.styleable.SliderPreference).apply {
            updateConstantly = getBoolean(R.styleable.SliderPreference_updatesContinuously, false)
            valueCount = getInteger(R.styleable.SliderPreference_valueCount, 1)
            valueFrom = getFloat(R.styleable.SliderPreference_minVal, 0f)
            valueTo = getFloat(R.styleable.SliderPreference_maxVal, 100f)
            tickInterval = getFloat(R.styleable.SliderPreference_tickInterval, 1f)
            showResetButton = getBoolean(R.styleable.SliderPreference_showResetButton, false)
            showValueLabel = getBoolean(R.styleable.SliderPreference_showValueLabel, true)
            valueFormat = getString(R.styleable.SliderPreference_valueFormat)
            isDecimalFormat = getBoolean(R.styleable.SliderPreference_isDecimalFormat, false)
            decimalFormat = if (hasValue(R.styleable.SliderPreference_decimalFormat)) {
                getString(R.styleable.SliderPreference_decimalFormat)
            } else {
                "#.#" // Default decimal format
            }
            outputScale = getFloat(R.styleable.SliderPreference_outputScale, 1f)
            showDefault = getBoolean(R.styleable.SliderPreference_showDefault, false)
            val defaultValStr = getString(androidx.preference.R.styleable.Preference_defaultValue)

            if (valueFormat == null) valueFormat = ""

            try {
                val scanner = Scanner(defaultValStr)
                scanner.useDelimiter(",")
                scanner.useLocale(Locale.ENGLISH)

                while (scanner.hasNext()) {
                    defaultValue.add(scanner.nextFloat())
                }
            } catch (ignored: Exception) {
                Log.e(
                    TAG,
                    String.format("SliderPreference: Error parsing default values for key: $key")
                )
            }

            if (defaultValue.isEmpty()) {
                defaultValue.add(valueFrom)
            }

            recycle()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        titleView = holder.itemView.findViewById(android.R.id.title)
        summaryView = holder.itemView.findViewById(android.R.id.summary)

        if (isEnabled) {
            titleView!!.setTextColor(ContextCompat.getColor(context, R.color.textColorPrimary))
            summaryView!!.setTextColor(ContextCompat.getColor(context, R.color.textColorSecondary))
        }

        slider = holder.itemView.findViewById(R.id.slider)
        slider!!.tag = key

        slider!!.addOnSliderTouchListener(sliderTouchListener)
        slider!!.addOnChangeListener(changeListener)

        slider!!.setLabelFormatter(labelFormatter)

        mResetButton = holder.itemView.findViewById(R.id.reset_button)

        if (showResetButton && defaultValue.isNotEmpty()) {
            mResetButton!!.visibility = View.VISIBLE
            mResetButton!!.isEnabled = isEnabled && !defaultValue.containsAll(slider!!.values)

            mResetButton!!.setOnClickListener { v: View ->
                handleResetButton()
                v.weakVibrate()

                slider!!.values = defaultValue
                mResetButton!!.isEnabled = false

                summaryView!!.apply {
                    text = slider!!.values.joinToString(separator = " - ") { sliderValue ->
                        labelFormatter.getFormattedValue(sliderValue)
                    }
                }

                savePrefs()
            }
        } else {
            mResetButton!!.visibility = View.GONE
        }

        sliderValue = holder.itemView.findViewById(androidx.preference.R.id.seekbar_value)

        slider!!.valueFrom = valueFrom
        slider!!.valueTo = valueTo
        slider!!.stepSize = tickInterval

        syncState()

        summaryView!!.apply {
            text = slider!!.values.joinToString(separator = " - ") { sliderValue ->
                labelFormatter.getFormattedValue(sliderValue)
            }
            visibility = if (showValueLabel) View.VISIBLE else View.GONE
        }

        handleResetButton()
    }

    fun setMin(value: Float) {
        valueFrom = value
        if (slider != null) slider!!.valueFrom = value
    }

    fun setMax(value: Float) {
        valueTo = value
        if (slider != null) slider!!.valueTo = value
    }

    fun setValues(values: List<Float>) {
        defaultValue.clear()
        defaultValue.addAll(values)
        if (slider != null) slider!!.values = values
    }

    private fun handleResetButton() {
        if (mResetButton == null) return

        if (showResetButton) {
            mResetButton!!.visibility = View.VISIBLE

            if (slider!!.values.isNotEmpty()) {
                mResetButton!!.isEnabled = isEnabled && !defaultValue.containsAll(slider!!.values)
            }
        } else {
            mResetButton!!.visibility = View.GONE
        }
    }

    private fun syncState() {
        var needsCommit = false

        var values: MutableList<Float> = getValues(sharedPreferences!!, key, valueFrom)

        // float and double are not accurate when it comes to decimal points
        val step = BigDecimal(slider!!.stepSize.toString())

        for (i in values.indices) {
            val round = BigDecimal(Math.round(values[i] / slider!!.stepSize))
            val value = min(
                max(step.multiply(round).toDouble(), slider!!.valueFrom.toDouble()),
                slider!!.valueTo.toDouble()
            )
            if (value != values[i].toDouble()) {
                values[i] = value.toFloat()
                needsCommit = true
            }
        }

        if (values.size < valueCount) {
            needsCommit = true
            values = defaultValue
            while (values.size < valueCount) {
                values.add(valueFrom)
            }
        } else if (values.size > valueCount) {
            needsCommit = true
            while (values.size > valueCount) {
                values.removeAt(values.size - 1)
            }
        }

        try {
            slider!!.values = values
            if (needsCommit) savePrefs()
        } catch (_: Throwable) {
        }
    }

    var labelFormatter: LabelFormatter = LabelFormatter {
        val formattedValues = slider!!.values.joinToString(separator = " - ") { sliderValue ->
            if (valueFormat != null && (valueFormat!!.isBlank() || valueFormat!!.isEmpty())) {
                if (!isDecimalFormat) {
                    (sliderValue / outputScale).toInt().toString()
                } else {
                    DecimalFormat(decimalFormat).format((sliderValue / outputScale).toDouble())
                }
            } else {
                if (!isDecimalFormat) {
                    (sliderValue / 1f).toInt().toString()
                } else {
                    DecimalFormat(decimalFormat).format((sliderValue / outputScale).toDouble())
                }
            }
        }

        if (showDefault &&
            defaultValue.isNotEmpty() &&
            defaultValue.containsAll(slider!!.values)
        ) {
            getContext().getString(
                R.string.opt_selected3,
                formattedValues,
                valueFormat,
                getContext().getString(R.string.opt_default)
            )
        } else {
            getContext().getString(
                R.string.opt_selected2,
                formattedValues,
                valueFormat
            )
        }
    }

    private var changeListener: RangeSlider.OnChangeListener =
        RangeSlider.OnChangeListener { slider: RangeSlider, value: Float, fromUser: Boolean ->
            if (key != slider.tag) return@OnChangeListener
            if (updateConstantly && fromUser) {
                savePrefs()
            }
        }

    private var sliderTouchListener: RangeSlider.OnSliderTouchListener =
        object : RangeSlider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: RangeSlider) {
                slider.setLabelFormatter(labelFormatter)
            }

            override fun onStopTrackingTouch(slider: RangeSlider) {
                if (key != slider.tag) return

                val summary =
                    (slider.parent.parent as ViewGroup).findViewById<TextView>(android.R.id.summary)
                summary.text = labelFormatter.getFormattedValue(slider.values[0])
                summary.visibility =
                    if (showValueLabel) View.VISIBLE else View.GONE

                handleResetButton()

                if (!updateConstantly) {
                    savePrefs()
                }
            }
        }

    fun savePrefs() {
        setValues(sharedPreferences!!, key, slider!!.values)
        setValues(sharedPreferences!!, key, slider!!.values)
    }

    companion object {
        private val TAG: String = SliderPreference::class.java.simpleName

        fun setValues(
            sharedPreferences: SharedPreferences,
            key: String?,
            values: List<Float>
        ): Boolean {
            try {
                val writer = StringWriter()
                val jsonWriter = JsonWriter(writer)
                jsonWriter.beginObject()
                jsonWriter.name("")
                jsonWriter.beginArray()

                for (value in values) {
                    jsonWriter.value(value.toDouble())
                }
                jsonWriter.endArray()
                jsonWriter.endObject()
                jsonWriter.close()
                val jsonString = writer.toString()

                sharedPreferences.edit().putString(key, jsonString).apply()

                return true
            } catch (ignored: Exception) {
                return false
            }
        }

        fun getValues(
            prefs: SharedPreferences,
            key: String?,
            defaultValue: Float
        ): MutableList<Float> {
            var values: MutableList<Float>

            try {
                val jsonString = prefs.getString(key, "")!!
                values = getValues(jsonString)
            } catch (ignored: Exception) {
                try {
                    val value = prefs.getFloat(key, defaultValue)
                    values = mutableListOf(value)
                } catch (ignored2: Exception) {
                    try {
                        val value = prefs.getInt(key, Math.round(defaultValue))
                        values = mutableListOf(value.toFloat())
                    } catch (ignored3: Exception) {
                        values = mutableListOf(defaultValue)
                    }
                }
            }

            return values
        }

        @Throws(Exception::class)
        fun getValues(jsonString: String): MutableList<Float> {
            val values: MutableList<Float> = ArrayList()

            if (jsonString.trim { it <= ' ' }.isEmpty()) return values

            JsonReader(StringReader(jsonString)).apply {
                beginObject()
                try {
                    nextName()
                    beginArray()
                } catch (ignored: Exception) {
                }

                while (hasNext()) {
                    try {
                        nextName()
                    } catch (ignored: Exception) {
                    }
                    values.add(nextDouble().toFloat())
                }
            }

            return values
        }

        fun getSingleFloatValue(
            prefs: SharedPreferences,
            key: String?,
            defaultValue: Float
        ): Float {
            var result = defaultValue

            try {
                result = getValues(prefs, key, defaultValue)[0]
            } catch (ignored: Throwable) {
            }

            return result
        }

        fun getSingleIntValue(prefs: SharedPreferences, key: String?, defaultValue: Int): Int {
            return Math.round(getSingleFloatValue(prefs, key, defaultValue.toFloat()))
        }
    }
}