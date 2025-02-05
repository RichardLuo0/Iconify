package com.drdisagree.iconify.xposed.modules.extras.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.drdisagree.iconify.common.Preferences.DEPTH_WALLPAPER_SWITCH
import com.drdisagree.iconify.common.Preferences.ICONIFY_DEPTH_WALLPAPER_FOREGROUND_TAG
import com.drdisagree.iconify.common.Preferences.ICONIFY_LOCKSCREEN_CONTAINER_TAG
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs

object ViewHelper {

    fun setMargins(viewGroup: Any, context: Context, left: Int, top: Int, right: Int, bottom: Int) {
        when (viewGroup) {
            is View -> {
                when (val layoutParams = viewGroup.layoutParams) {
                    is LinearLayout.LayoutParams -> {
                        layoutParams.setMargins(
                            context.toPx(left),
                            context.toPx(top),
                            context.toPx(right),
                            context.toPx(bottom)
                        )
                    }

                    is FrameLayout.LayoutParams -> {
                        layoutParams.setMargins(
                            context.toPx(left),
                            context.toPx(top),
                            context.toPx(right),
                            context.toPx(bottom)
                        )
                    }

                    is RelativeLayout.LayoutParams -> {
                        layoutParams.setMargins(
                            context.toPx(left),
                            context.toPx(top),
                            context.toPx(right),
                            context.toPx(bottom)
                        )
                    }

                    is ConstraintLayout.LayoutParams -> {
                        layoutParams.setMargins(
                            context.toPx(left),
                            context.toPx(top),
                            context.toPx(right),
                            context.toPx(bottom)
                        )
                    }

                    else -> {
                        if (layoutParams != null) {
                            log(this@ViewHelper, "Unsupported type: $layoutParams")
                        }
                    }
                }
            }

            is MarginLayoutParams -> {
                viewGroup.setMargins(
                    context.toPx(left),
                    context.toPx(top),
                    context.toPx(right),
                    context.toPx(bottom)
                )
            }

            else -> {
                throw IllegalArgumentException("The viewGroup object has to be either a View or a ViewGroup.MarginLayoutParams. Found ${viewGroup.javaClass.simpleName} instead.")
            }
        }
    }

    fun setPaddings(
        viewGroup: ViewGroup,
        context: Context,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        viewGroup.setPadding(
            context.toPx(left),
            context.toPx(top),
            context.toPx(right),
            context.toPx(bottom)
        )
    }

    fun Context.toPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        resources.displayMetrics
    ).toInt()

    fun findViewWithTagAndChangeColor(view: View?, tagContains: String, color: Int) {
        if (view == null) return

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child: View = view.getChildAt(i)
                checkTagAndChangeColor(child, tagContains, color)

                if (child is ViewGroup) {
                    findViewWithTagAndChangeColor(child, tagContains, color)
                }
            }
        } else {
            checkTagAndChangeColor(view, tagContains, color)
        }
    }

    fun findViewWithTagAndChangeColor(
        view: View?,
        tagContains: String,
        color1: Int,
        color2: Int,
        cornerRadius: Int
    ) {
        if (view == null) return

        val drawable = GradientDrawable()
        drawable.colors = intArrayOf(color1, color2)
        drawable.orientation = GradientDrawable.Orientation.LEFT_RIGHT
        drawable.cornerRadius = view.context.toPx(cornerRadius).toFloat()

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child: View = view.getChildAt(i)
                checkTagAndChangeBackgroundColor(child, tagContains, drawable)

                if (child is ViewGroup) {
                    checkTagAndChangeBackgroundColor(child, tagContains, drawable)
                }
            }
        } else {
            checkTagAndChangeBackgroundColor(view, tagContains, drawable)
        }

    }

    private fun checkTagAndChangeColor(view: View, tag: String, color: Int) {
        if (view.tag?.toString()?.let { isTagMatch(tag, it) } == true) {
            changeViewColor(view, color)
        }
    }

    private fun checkTagAndChangeBackgroundColor(view: View, tag: String, bkg: Drawable) {
        if (view.tag?.toString()?.let { isTagMatch(tag, it) } == true) {
            changeViewBackgroundColor(view, bkg)
        }
    }

    private fun changeViewColor(view: View, color: Int) {
        when (view) {
            is TextView -> {
                view.setTextColor(color)

                val drawablesRelative: Array<Drawable?> = view.compoundDrawablesRelative
                for (drawable in drawablesRelative) {
                    drawable?.let {
                        it.mutate()
                        it.setTint(color)
                        it.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                    }
                }

                val drawables: Array<Drawable?> = view.compoundDrawables
                for (drawable in drawables) {
                    drawable?.let {
                        it.mutate()
                        it.setTint(color)
                        it.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                    }
                }
            }

            is ImageView -> {
                view.setColorFilter(color)
            }

            is ViewGroup -> {
                view.setBackgroundTintList(ColorStateList.valueOf(color))
            }

            is ProgressBar -> {
                view.progressTintList = ColorStateList.valueOf(color)
                view.progressBackgroundTintList = ColorStateList.valueOf(color)
            }

            else -> {
                view.background.mutate().setTint(color)
            }
        }
    }

    private fun changeViewBackgroundColor(view: View, bkg: Drawable) {
        view.background = bkg
    }

    fun applyFontRecursively(view: View?, typeface: Typeface?) {
        if (view == null) return

        if (view is ViewGroup) {
            val childCount: Int = view.childCount

            for (i in 0 until childCount) {
                val child: View = view.getChildAt(i)

                if (child is ViewGroup) {
                    applyFontRecursively(child, typeface)
                } else (child as? TextView)?.setTypeface(typeface)
            }
        } else (view as? TextView)?.setTypeface(typeface)
    }

    fun applyTextMarginRecursively(context: Context, view: View?, topMargin: Int) {
        if (view == null) return

        val topMarginInDp = context.toPx(topMargin)

        if (view is ViewGroup) {
            val childCount: Int = view.childCount

            for (i in 0 until childCount) {
                val child: View = view.getChildAt(i)

                if (child is ViewGroup) {
                    applyTextMarginRecursively(context, child, topMargin)
                } else if (child is TextView) {
                    setTextMargins(child, topMarginInDp)
                }
            }
        } else if (view is TextView) {
            setTextMargins(view, topMarginInDp)
        }
    }

    private fun setTextMargins(child: View, topMarginInDp: Int) {
        if (child.tag?.toString()?.let { isTagMatch("nolineheight", it) } == true) {
            return
        }

        when (val params = child.layoutParams) {
            is LinearLayout.LayoutParams -> {
                params.topMargin += topMarginInDp
                child.layoutParams = params
            }

            is FrameLayout.LayoutParams -> {
                params.topMargin += topMarginInDp
                child.layoutParams = params
            }

            is RelativeLayout.LayoutParams -> {
                params.topMargin += topMarginInDp
                child.layoutParams = params
            }

            else -> {
                log(this@ViewHelper, "Invalid params: $params")
            }
        }
    }

    fun applyTextScalingRecursively(view: View?, scaleFactor: Float) {
        if (view == null) return

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child: View = view.getChildAt(i)

                if (child is ViewGroup) {
                    applyTextScalingRecursively(child, scaleFactor)
                } else if (child is TextView) {
                    setTextScaling(child, scaleFactor)
                }
            }
        } else if (view is TextView) {
            setTextScaling(view, scaleFactor)
        }
    }

    private fun setTextScaling(view: View, scaleFactor: Float) {
        val originalSize = (view as TextView).textSize
        val newSize = originalSize * scaleFactor
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize)
    }

    fun View.findViewContainsTag(tag: String): View? {
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)

                if (child.tag?.toString()?.let { isTagMatch(tag, it) } == true) {
                    return child
                }

                if (child is ViewGroup) {
                    val result = child.findViewContainsTag(tag)
                    if (result != null) {
                        return result
                    }
                }
            }
        } else {
            if (getTag()?.toString()?.let { isTagMatch(tag, it) } == true) {
                return this
            }
        }

        return null
    }

    fun View.findViewIdContainsTag(tag: String): Int {
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)

                if (child.tag?.toString()?.let { isTagMatch(tag, it) } == true) {
                    return i
                }

                if (child is ViewGroup) {
                    val result = child.findViewContainsTag(tag)
                    if (result != null) {
                        return i
                    }
                }
            }
        } else {
            if (getTag()?.toString()?.let { isTagMatch(tag, it) } == true) {
                return 0
            }
        }

        return -1
    }

    private fun isTagMatch(tagToCheck: String, targetTag: String): Boolean {
        val parts = targetTag.split("|")
        return parts.any { it.trim() == tagToCheck }
    }

    fun Drawable.applyBlur(context: Context, radius: Float): Drawable {
        if (radius == 0f) {
            return this
        }

        val bitmap = drawableToBitmap(this)

        val blurredBitmap = bitmap.applyBlur(context, radius.coerceIn(1f, 25f))

        return BitmapDrawable(context.resources, blurredBitmap)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    @Suppress("deprecation")
    fun Bitmap.applyBlur(context: Context, radius: Float): Bitmap {
        if (radius == 0f) {
            return this
        }

        var tempImage = this

        try {
            tempImage = rgb565toArgb888()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val bitmap = Bitmap.createBitmap(
            tempImage.width, tempImage.height,
            Bitmap.Config.ARGB_8888
        )
        val renderScript = RenderScript.create(context)
        val blurInput = Allocation.createFromBitmap(renderScript, tempImage)
        val blurOutput = Allocation.createFromBitmap(renderScript, bitmap)

        ScriptIntrinsicBlur.create(
            renderScript,
            Element.U8_4(renderScript)
        ).apply {
            setInput(blurInput)
            setRadius(radius.coerceIn(0.0001f, 25f))
            forEach(blurOutput)
        }

        blurOutput.copyTo(bitmap)
        renderScript.destroy()

        return bitmap
    }

    private fun Bitmap.rgb565toArgb888(): Bitmap {
        val numPixels = width * height
        val pixels = IntArray(numPixels)

        // Get JPEG pixels. Each int is the color values for one pixel.
        getPixels(pixels, 0, width, 0, 0, width, height)

        // Create a Bitmap of the appropriate format.
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Set RGB pixels.
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

        return result
    }

    fun View?.hideView() {
        if (this == null) return

        fun hide() {
            apply {
                layoutParams.height = 0
                layoutParams.width = 0
                visibility = View.INVISIBLE
            }
        }

        hide()
        viewTreeObserver?.addOnGlobalLayoutListener {
            hide()
        }
        viewTreeObserver?.addOnDrawListener {
            hide()
        }
    }

    fun View?.assignIdsToViews() {
        if (this == null) return

        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)

                if (child is ViewGroup) {
                    child.assignIdsToViews()
                }

                if (child.id == View.NO_ID) {
                    child.id = View.generateViewId()
                }
            }
        } else {
            if (id == View.NO_ID) {
                id = View.generateViewId()
            }
        }
    }

    fun ViewGroup?.getLsItemsContainer(): LinearLayout? {
        if (this == null) return null

        var layout: LinearLayout? = findViewWithTag(ICONIFY_LOCKSCREEN_CONTAINER_TAG)

        if (layout == null) {
            val showDepthWallpaper = Xprefs.getBoolean(DEPTH_WALLPAPER_SWITCH, false)
            val idx = if (showDepthWallpaper) {
                val tempIdx = rootView.findViewIdContainsTag(ICONIFY_DEPTH_WALLPAPER_FOREGROUND_TAG)
                if (tempIdx == -1) 0 else tempIdx
            } else {
                0
            }

            layout = LinearLayout(this.context).apply {
                id = View.generateViewId()
                tag = ICONIFY_LOCKSCREEN_CONTAINER_TAG
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
            }
            addView(layout, idx)
        }

        return layout
    }

    fun ViewGroup.reAddView(childView: View?, index: Int) {
        childView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(childView)
            addView(view, index)
        }
    }
}
