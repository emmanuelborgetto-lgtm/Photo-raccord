/*
 * Photoraccord
 * Copyright (C) 2026 Emmanuel Borgetto
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.props.photo_raccord.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Dessine le bandeau d'information (projet, date, décor, séquence) sur un Canvas.
 * Utilisée à la fois lors de la capture (CameraUtils) et lors de l'édition
 * d'une photo existante (FileUtils), pour éviter de dupliquer le code de rendu.
 *
 * @param bannerTop position Y (en pixels) du haut du bandeau
 * @param bannerBottom position Y (en pixels) du bas du bandeau (= hauteur totale de l'image)
 */
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
        color = Color.GRAY; textSize = textSizeTitle
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }
    val paintDate = Paint().apply {
        color = Color.WHITE; textSize = textSizeDate
        typeface = Typeface.DEFAULT; isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }
    val paintDecor = Paint().apply {
        color = Color.WHITE; textSize = textSizeTitle
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    val paintSequence = Paint().apply {
        color = Color.WHITE; textSize = textSizeTitle
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }

    canvas.drawRect(0f, bannerTop, width.toFloat(), bannerBottom, paintBg)
    canvas.drawText(projet, paddingX, textYTitle, paintProjet)
    canvas.drawText(date, paddingX, textYDate, paintDate)
    canvas.drawText("Décor: $decor", width / 2f, textYCenterRight, paintDecor)
    canvas.drawText("Seq: $sequence", width - paddingX, textYCenterRight, paintSequence)
}