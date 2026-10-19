package com.vintagesoulprint.wallmuse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Freeform, touch-driven crop editor. The selection is stored in normalized
 * original-image coordinates and NEVER constrained to the print/frame ratio.
 * Corner handles adjust two edges independently; side handles adjust one.
 */
class CropEditorView(
    context: Context,
    private val bitmap: Bitmap,
    layer: WallCanvasView.ArtLayer
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val picture = RectF()
    private val crop = RectF()
    private val original = RectF()
    private val shade = 0xB4000000.toInt()
    private val gold = 0xFFD6BA88.toInt()
    private val handleRadius = 6f * density
    private val hitRadius = 28f * density
    private val minFraction = .025f
    private var startX = 0f
    private var startY = 0f
    // 1 move, 2 TL, 3 TR, 4 BR, 5 BL, 6 top, 7 right, 8 bottom, 9 left.
    private var action = 0

    init {
        val saved = RectF(layer.cropLeft, layer.cropTop, layer.cropRight, layer.cropBottom)
        val valid = saved.left >= 0f && saved.top >= 0f &&
            saved.right <= 1f && saved.bottom <= 1f &&
            saved.width() >= minFraction && saved.height() >= minFraction
        val hasSaved = valid && (saved.left > .0001f || saved.top > .0001f ||
            saved.right < .9999f || saved.bottom < .9999f)
        if (hasSaved) {
            crop.set(saved)
        } else {
            // Compatibility with designs saved before the interactive editor.
            val trim = layer.trim.coerceIn(0f, .24f)
            crop.set(trim, trim, 1f - trim, 1f - trim)
        }
        isClickable = true
        contentDescription = "Freeform crop. Drag the image selection, a side or a corner."
    }

    fun resetCrop() {
        crop.set(0f, 0f, 1f, 1f)
        invalidate()
    }

    fun cropRect(): RectF = RectF(crop)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (390f * density).toInt()
        setMeasuredDimension(resolveSize(desired, widthMeasureSpec), resolveSize(desired, heightMeasureSpec))
    }

    private fun imageRect(): RectF {
        val padding = 12f * density
        val availableWidth = (width - 2f * padding).coerceAtLeast(1f)
        val availableHeight = (height - 2f * padding).coerceAtLeast(1f)
        val scale = min(availableWidth / bitmap.width.coerceAtLeast(1),
            availableHeight / bitmap.height.coerceAtLeast(1))
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        return RectF(width / 2f - w / 2f, height / 2f - h / 2f,
            width / 2f + w / 2f, height / 2f + h / 2f)
    }

    private fun pxX(x: Float) = picture.left + x * picture.width()
    private fun pxY(y: Float) = picture.top + y * picture.height()
    private fun normX(x: Float) = ((x - picture.left) / picture.width()).coerceIn(0f, 1f)
    private fun normY(y: Float) = ((y - picture.top) / picture.height()).coerceIn(0f, 1f)

    private fun handlePoints(): List<Pair<Float, Float>> {
        val l = pxX(crop.left)
        val t = pxY(crop.top)
        val r = pxX(crop.right)
        val b = pxY(crop.bottom)
        val cx = (l + r) * .5f
        val cy = (t + b) * .5f
        return listOf(l to t, r to t, r to b, l to b,
            cx to t, r to cy, cx to b, l to cy)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF202423.toInt())
        picture.set(imageRect())
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        canvas.drawBitmap(bitmap, null, picture, paint)

        val l = pxX(crop.left)
        val t = pxY(crop.top)
        val r = pxX(crop.right)
        val b = pxY(crop.bottom)
        paint.color = shade
        canvas.drawRect(picture.left, picture.top, picture.right, t, paint)
        canvas.drawRect(picture.left, b, picture.right, picture.bottom, paint)
        canvas.drawRect(picture.left, t, l, b, paint)
        canvas.drawRect(r, t, picture.right, b, paint)

        paint.color = gold
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.3f * density
        canvas.drawRect(l, t, r, b, paint)
        paint.strokeWidth = 1f * density
        paint.color = 0x9FFFFFFF.toInt()
        for (i in 1..2) {
            val x = l + (r - l) * i / 3f
            val y = t + (b - t) * i / 3f
            canvas.drawLine(x, t, x, b, paint)
            canvas.drawLine(l, y, r, y, paint)
        }
        paint.style = Paint.Style.FILL
        for ((x, y) in handlePoints()) {
            paint.color = 0xFF292925.toInt()
            canvas.drawCircle(x, y, handleRadius + 2f * density, paint)
            paint.color = gold
            canvas.drawCircle(x, y, handleRadius, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                picture.set(imageRect())
                val handles = handlePoints()
                val near = handles.withIndex().minByOrNull {
                    hypot(event.x - it.value.first, event.y - it.value.second)
                }
                action = if (near != null &&
                    hypot(event.x - near.value.first, event.y - near.value.second) <= hitRadius)
                    2 + near.index
                else if (event.x in pxX(crop.left)..pxX(crop.right) &&
                    event.y in pxY(crop.top)..pxY(crop.bottom)) 1 else 0
                startX = normX(event.x)
                startY = normY(event.y)
                original.set(crop)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (action == 0) return true
                val x = normX(event.x)
                val y = normY(event.y)
                if (action == 1) {
                    val left = (original.left + x - startX)
                        .coerceIn(0f, 1f - original.width())
                    val top = (original.top + y - startY)
                        .coerceIn(0f, 1f - original.height())
                    crop.set(left, top, left + original.width(), top + original.height())
                } else {
                    var l = original.left
                    var t = original.top
                    var r = original.right
                    var b = original.bottom

                    // No aspect-ratio lock: X affects only the horizontal side;
                    // Y affects only the vertical side. Opposite edges stay put.
                    if (action == 2 || action == 5 || action == 9)
                        l = x.coerceIn(0f, r - minFraction)
                    if (action == 3 || action == 4 || action == 7)
                        r = x.coerceIn(l + minFraction, 1f)
                    if (action == 2 || action == 3 || action == 6)
                        t = y.coerceIn(0f, b - minFraction)
                    if (action == 4 || action == 5 || action == 8)
                        b = y.coerceIn(t + minFraction, 1f)
                    crop.set(l, t, r, b)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                action = 0
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                action = 0
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
