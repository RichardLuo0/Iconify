package com.drdisagree.iconify.xposed.modules.extras.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.toPx

object ArcProgressWidget {

    fun generateBitmap(
        context: Context,
        percentage: Int = 100,
        textInside: String = "",
        textInsideSizePx: Int,
        iconDrawable: Drawable? = null,
        iconSizePx: Int = 28,
        textBottom: String = "...",
        textBottomSizePx: Int = 28,
        typeface: Typeface? = Typeface.create("sans-serif", Typeface.BOLD)
    ): Bitmap {
        val width = 400
        val height = 400
        val stroke = 40
        val padding = 5
        val minAngle = 135
        val maxAngle = 275

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
        paint.strokeWidth = stroke.toFloat()
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        val mTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = context.toPx(textInsideSizePx).toFloat()
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
        }

        val arc = RectF()
        arc[stroke.toFloat() / 2 + padding, stroke.toFloat() / 2 + padding, width - padding - stroke.toFloat() / 2] =
            height - padding - stroke.toFloat() / 2

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        paint.color = Color.argb(75, 255, 255, 255)
        canvas.drawArc(arc, minAngle.toFloat(), maxAngle.toFloat(), false, paint)

        paint.color = Color.WHITE
        canvas.drawArc(arc, minAngle.toFloat(), maxAngle.toFloat() / 100 * percentage, false, paint)

        canvas.drawText(
            textInside,
            bitmap.getWidth().toFloat() / 2,
            (bitmap.getHeight() - mTextPaint.ascent() * 0.7f) / 2,
            mTextPaint
        )

        iconDrawable?.apply {
            val size = context.toPx(iconSizePx)
            val left = (bitmap.getWidth() - size) / 2
            val top = bitmap.getHeight() - (size / 1.3).toInt() - (stroke + padding)
            val right = left + size
            val bottom = top + size

            setBounds(left, top, right, bottom)
            colorFilter = BlendModeColorFilter(Color.WHITE, BlendMode.SRC_IN)
            draw(canvas)
        } ?: run {
            mTextPaint.textSize = context.toPx(textBottomSizePx).toFloat()
            canvas.drawText(
                textBottom,
                bitmap.getWidth().toFloat() / 2,
                (bitmap.getHeight() - (stroke + padding)).toFloat(),
                mTextPaint
            )
        }

        return bitmap
    }
}