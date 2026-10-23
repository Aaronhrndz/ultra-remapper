package com.aaronhrndz.ultraremapper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
fun CollapseChevron(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF8A93A2)
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()

        if (isExpanded) {
            // Chevron pointing UP (^): (left, bottom) -> (center, top) -> (right, bottom)
            val left = Offset(w * 0.2f, h * 0.65f)
            val center = Offset(w * 0.5f, h * 0.35f)
            val right = Offset(w * 0.8f, h * 0.65f)

            drawLine(color, left, center, strokeWidth, StrokeCap.Round)
            drawLine(color, center, right, strokeWidth, StrokeCap.Round)
        } else {
            // Chevron pointing DOWN (v): (left, top) -> (center, bottom) -> (right, top)
            val left = Offset(w * 0.2f, h * 0.35f)
            val center = Offset(w * 0.5f, h * 0.65f)
            val right = Offset(w * 0.8f, h * 0.35f)

            drawLine(color, left, center, strokeWidth, StrokeCap.Round)
            drawLine(color, center, right, strokeWidth, StrokeCap.Round)
        }
    }
}
