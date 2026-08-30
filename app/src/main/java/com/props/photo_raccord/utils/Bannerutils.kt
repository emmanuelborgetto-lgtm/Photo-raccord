/*
 * Photoraccord
 * Copyright (C) 2026 Emmanuel Borgetto
 */
package com.props.photo_raccord.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/** Dessine le bandeau dans la zone située sous la photo, sans recouvrir celle-ci. */
fun drawInfoBanner(
    canvas: Canvas,
    width: Int,
    bannerTop: Float,
    bannerBottom: Float,
    projet: String,
    date: String,
    decor: String,
    sequence: String
) {
    val bannerHeight = bannerBottom - bannerTop
    val paddingX = width * 0.02f
    val textSizeTitle = width * 0.035f
    val textSizeDate = width * 0.028f
    val textYTitle = bannerTop + bannerHeight * 0.45f
    val textYDate = bannerTop + bannerHeight * 0.85f
    val textYCenterRight = bannerTop + bannerHeight * 0.62f

    val paintBg = Paint().apply { color = Color.BLACK }
    val paintProjet = Paint().apply {
        color = Color.GRAY; textSize = textSizeTitle; typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true; textAlign = Paint.Align.LEFT
    }
    val paintDate = Paint().apply {
        color = Color.WHITE; textSize = textSizeDate; typeface = Typeface.DEFAULT
        isAntiAlias = true; textAlign = Paint.Align.LEFT
    }
    val paintDecor = Paint().apply {
        color = Color.WHITE; textSize = textSizeTitle; typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true; textAlign = Paint.Align.CENTER
    }
    val paintSequence = Paint().apply {
        color = Color.WHITE; textSize = textSizeTitle; typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true; textAlign = Paint.Align.RIGHT
    }

    canvas.drawRect(0f, bannerTop, width.toFloat(), bannerBottom, paintBg)
    canvas.drawText(projet, paddingX, textYTitle, paintProjet)
    canvas.drawText(date, paddingX, textYDate, paintDate)
    canvas.drawText("Décor: $decor", width / 2f, textYCenterRight, paintDecor)
    canvas.drawText("Seq: $sequence", width - paddingX, textYCenterRight, paintSequence)
}
