package com.example.cityflux.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max

internal enum class MarkerGlyph {
    PARKING,
    INCIDENT,
    PARKING_ALERT,
    HAWKER,
    CAMERA,
    PERSON,
    HOSPITAL,
    POLICE,
    FIRE,
    TRAFFIC
}

internal fun createSymbolPinBitmap(
    color: Int,
    sizeDp: Int,
    glyph: MarkerGlyph
): Bitmap {
    val width = max((sizeDp * 2.5f).toInt(), 64)
    val height = (width * 1.34f).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = width / 2f
    val circleCy = width * 0.38f
    val circleRadius = width * 0.24f
    val tailTopY = circleCy + circleRadius * 0.78f
    val tipY = height - width * 0.08f
    val tailHalfWidth = circleRadius * 0.58f

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.argb(46, 15, 23, 42)
        style = Paint.Style.FILL
    }
    canvas.drawOval(
        RectF(
            cx - circleRadius * 0.92f,
            height - width * 0.18f,
            cx + circleRadius * 0.92f,
            height - width * 0.05f
        ),
        shadowPaint
    )

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = width * 0.06f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    val tailPath = Path().apply {
        moveTo(cx, tipY)
        quadTo(cx - tailHalfWidth * 0.35f, tailTopY + circleRadius * 0.62f, cx - tailHalfWidth, tailTopY)
        lineTo(cx + tailHalfWidth, tailTopY)
        quadTo(cx + tailHalfWidth * 0.35f, tailTopY + circleRadius * 0.62f, cx, tipY)
        close()
    }
    canvas.drawPath(tailPath, fillPaint)
    canvas.drawCircle(cx, circleCy, circleRadius, fillPaint)
    canvas.drawPath(tailPath, borderPaint)
    canvas.drawCircle(cx, circleCy, circleRadius, borderPaint)

    val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.argb(42, 255, 255, 255)
        style = Paint.Style.FILL
    }
    canvas.drawOval(
        RectF(
            cx - circleRadius * 0.74f,
            circleCy - circleRadius * 0.92f,
            cx + circleRadius * 0.16f,
            circleCy - circleRadius * 0.12f
        ),
        highlightPaint
    )

    val glyphRect = RectF(
        cx - circleRadius * 0.74f,
        circleCy - circleRadius * 0.74f,
        cx + circleRadius * 0.74f,
        circleCy + circleRadius * 0.74f
    )
    drawMarkerGlyph(canvas, glyphRect, glyph)
    return bitmap
}

internal fun createLiveUserPinBitmap(speed: Int, isMe: Boolean = false): Bitmap {
    val color = when {
        isMe -> Color.rgb(37, 99, 235)
        speed >= 40 -> Color.rgb(22, 163, 74)
        speed >= 15 -> Color.rgb(234, 88, 12)
        else -> Color.rgb(220, 38, 38)
    }
    return createSymbolPinBitmap(
        color = color,
        sizeDp = if (isMe) 38 else 36,
        glyph = MarkerGlyph.PERSON
    )
}

private fun drawMarkerGlyph(
    canvas: Canvas,
    rect: RectF,
    glyph: MarkerGlyph
) {
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = rect.width() * 0.11f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    when (glyph) {
        MarkerGlyph.PARKING -> drawParkingGlyph(canvas, rect)
        MarkerGlyph.INCIDENT -> drawIncidentGlyph(canvas, rect, strokePaint, fillPaint)
        MarkerGlyph.PARKING_ALERT -> {
            drawParkingGlyph(canvas, rect.applyInset(horizontal = 0.08f, vertical = 0f), offsetX = -rect.width() * 0.08f)
            drawExclamationGlyph(canvas, rect.applyInset(horizontal = 0.08f, vertical = 0f), offsetX = rect.width() * 0.22f)
        }
        MarkerGlyph.HAWKER -> drawShopGlyph(canvas, rect, strokePaint)
        MarkerGlyph.CAMERA -> drawCameraGlyph(canvas, rect, strokePaint, fillPaint)
        MarkerGlyph.PERSON -> drawPersonGlyph(canvas, rect, fillPaint)
        MarkerGlyph.HOSPITAL -> drawHospitalGlyph(canvas, rect, fillPaint)
        MarkerGlyph.POLICE -> drawPoliceGlyph(canvas, rect, fillPaint)
        MarkerGlyph.FIRE -> drawFireGlyph(canvas, rect, fillPaint)
        MarkerGlyph.TRAFFIC -> drawTrafficGlyph(canvas, rect, strokePaint)
    }
}

private fun drawParkingGlyph(
    canvas: Canvas,
    rect: RectF,
    offsetX: Float = 0f
) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = rect.height() * 0.9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText("P", rect.centerX() + offsetX, baseline, textPaint)
}

private fun drawIncidentGlyph(
    canvas: Canvas,
    rect: RectF,
    strokePaint: Paint,
    fillPaint: Paint
) {
    val triangle = Path().apply {
        moveTo(rect.centerX(), rect.top + rect.height() * 0.14f)
        lineTo(rect.left + rect.width() * 0.24f, rect.bottom - rect.height() * 0.16f)
        lineTo(rect.right - rect.width() * 0.24f, rect.bottom - rect.height() * 0.16f)
        close()
    }
    canvas.drawPath(triangle, strokePaint)

    val cx = rect.centerX()
    canvas.drawLine(cx, rect.top + rect.height() * 0.36f, cx, rect.bottom - rect.height() * 0.34f, strokePaint)
    canvas.drawCircle(cx, rect.bottom - rect.height() * 0.23f, rect.width() * 0.05f, fillPaint)
}

private fun drawExclamationGlyph(
    canvas: Canvas,
    rect: RectF,
    offsetX: Float = 0f
) {
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = rect.width() * 0.12f
        strokeCap = Paint.Cap.ROUND
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    val cx = rect.centerX() + offsetX
    canvas.drawLine(cx, rect.top + rect.height() * 0.28f, cx, rect.bottom - rect.height() * 0.34f, strokePaint)
    canvas.drawCircle(cx, rect.bottom - rect.height() * 0.2f, rect.width() * 0.05f, fillPaint)
}

private fun drawShopGlyph(
    canvas: Canvas,
    rect: RectF,
    strokePaint: Paint
) {
    val topY = rect.top + rect.height() * 0.28f
    val midY = rect.top + rect.height() * 0.46f
    val bottomY = rect.bottom - rect.height() * 0.16f
    canvas.drawLine(rect.left + rect.width() * 0.18f, topY, rect.right - rect.width() * 0.18f, topY, strokePaint)
    canvas.drawLine(rect.left + rect.width() * 0.22f, midY, rect.right - rect.width() * 0.22f, midY, strokePaint)
    canvas.drawRect(
        rect.left + rect.width() * 0.24f,
        midY,
        rect.right - rect.width() * 0.24f,
        bottomY,
        strokePaint
    )
    canvas.drawLine(rect.centerX(), midY, rect.centerX(), bottomY, strokePaint)
}

private fun drawCameraGlyph(
    canvas: Canvas,
    rect: RectF,
    strokePaint: Paint,
    fillPaint: Paint
) {
    val body = RectF(
        rect.left + rect.width() * 0.18f,
        rect.top + rect.height() * 0.32f,
        rect.right - rect.width() * 0.24f,
        rect.bottom - rect.height() * 0.24f
    )
    canvas.drawRoundRect(body, rect.width() * 0.12f, rect.width() * 0.12f, strokePaint)

    val lensCx = body.centerX() + rect.width() * 0.1f
    val lensCy = body.centerY()
    canvas.drawCircle(lensCx, lensCy, rect.width() * 0.11f, strokePaint)
    canvas.drawCircle(lensCx, lensCy, rect.width() * 0.05f, fillPaint)

    val nose = Path().apply {
        moveTo(body.right, body.top + body.height() * 0.18f)
        lineTo(rect.right - rect.width() * 0.08f, body.centerY())
        lineTo(body.right, body.bottom - body.height() * 0.18f)
        close()
    }
    canvas.drawPath(nose, strokePaint)

    val tripodX = body.left + body.width() * 0.28f
    canvas.drawLine(tripodX, body.bottom, tripodX - rect.width() * 0.12f, rect.bottom - rect.height() * 0.02f, strokePaint)
    canvas.drawLine(tripodX, body.bottom, tripodX + rect.width() * 0.12f, rect.bottom - rect.height() * 0.02f, strokePaint)
}

private fun drawPersonGlyph(
    canvas: Canvas,
    rect: RectF,
    fillPaint: Paint
) {
    canvas.drawCircle(rect.centerX(), rect.top + rect.height() * 0.3f, rect.width() * 0.15f, fillPaint)
    val shoulders = RectF(
        rect.left + rect.width() * 0.24f,
        rect.top + rect.height() * 0.48f,
        rect.right - rect.width() * 0.24f,
        rect.bottom - rect.height() * 0.1f
    )
    canvas.drawRoundRect(shoulders, rect.width() * 0.18f, rect.width() * 0.18f, fillPaint)
}

private fun drawHospitalGlyph(
    canvas: Canvas,
    rect: RectF,
    fillPaint: Paint
) {
    val bar = rect.width() * 0.14f
    canvas.drawRoundRect(
        RectF(rect.centerX() - bar / 2f, rect.top + rect.height() * 0.18f, rect.centerX() + bar / 2f, rect.bottom - rect.height() * 0.18f),
        bar,
        bar,
        fillPaint
    )
    canvas.drawRoundRect(
        RectF(rect.left + rect.width() * 0.18f, rect.centerY() - bar / 2f, rect.right - rect.width() * 0.18f, rect.centerY() + bar / 2f),
        bar,
        bar,
        fillPaint
    )
}

private fun drawPoliceGlyph(
    canvas: Canvas,
    rect: RectF,
    fillPaint: Paint
) {
    val shield = Path().apply {
        moveTo(rect.centerX(), rect.top + rect.height() * 0.12f)
        lineTo(rect.right - rect.width() * 0.18f, rect.top + rect.height() * 0.24f)
        lineTo(rect.right - rect.width() * 0.24f, rect.bottom - rect.height() * 0.16f)
        lineTo(rect.centerX(), rect.bottom - rect.height() * 0.06f)
        lineTo(rect.left + rect.width() * 0.24f, rect.bottom - rect.height() * 0.16f)
        lineTo(rect.left + rect.width() * 0.18f, rect.top + rect.height() * 0.24f)
        close()
    }
    canvas.drawPath(shield, fillPaint)
}

private fun drawFireGlyph(
    canvas: Canvas,
    rect: RectF,
    fillPaint: Paint
) {
    val flame = Path().apply {
        moveTo(rect.centerX(), rect.top + rect.height() * 0.08f)
        cubicTo(
            rect.right - rect.width() * 0.08f,
            rect.top + rect.height() * 0.3f,
            rect.right - rect.width() * 0.14f,
            rect.bottom - rect.height() * 0.08f,
            rect.centerX(),
            rect.bottom - rect.height() * 0.1f
        )
        cubicTo(
            rect.left + rect.width() * 0.16f,
            rect.bottom - rect.height() * 0.08f,
            rect.left + rect.width() * 0.1f,
            rect.top + rect.height() * 0.38f,
            rect.centerX(),
            rect.top + rect.height() * 0.08f
        )
        close()
    }
    canvas.drawPath(flame, fillPaint)
}

private fun drawTrafficGlyph(
    canvas: Canvas,
    rect: RectF,
    strokePaint: Paint
) {
    val leftX = rect.left + rect.width() * 0.28f
    val centerX = rect.centerX()
    val rightX = rect.right - rect.width() * 0.28f
    canvas.drawLine(leftX, rect.top + rect.height() * 0.22f, leftX, rect.bottom - rect.height() * 0.16f, strokePaint)
    canvas.drawLine(centerX, rect.top + rect.height() * 0.12f, centerX, rect.bottom - rect.height() * 0.12f, strokePaint)
    canvas.drawLine(rightX, rect.top + rect.height() * 0.22f, rightX, rect.bottom - rect.height() * 0.16f, strokePaint)
}

private fun RectF.applyInset(horizontal: Float, vertical: Float): RectF {
    val dx = width() * horizontal
    val dy = height() * vertical
    return RectF(left + dx, top + dy, right - dx, bottom - dy)
}
