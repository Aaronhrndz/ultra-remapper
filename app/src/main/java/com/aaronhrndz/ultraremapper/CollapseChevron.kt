package com.aaronhrndz.ultraremapper

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

@Composable
fun ForwardChevron(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF8A93A2)
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.2.dp.toPx()

        // Chevron pointing RIGHT (>): (left, top) -> (right, center) -> (left, bottom)
        val top = Offset(w * 0.35f, h * 0.2f)
        val center = Offset(w * 0.7f, h * 0.5f)
        val bottom = Offset(w * 0.35f, h * 0.8f)

        drawLine(color, top, center, strokeWidth, StrokeCap.Round)
        drawLine(color, center, bottom, strokeWidth, StrokeCap.Round)
    }
}

@Composable
fun SwitchLoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SwitchSpinner")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 800,
                easing = LinearEasing
            )
        ),
        label = "SpinnerAngle"
    )

    Canvas(modifier = modifier.size(14.dp)) {
        val strokeWidth = 2.dp.toPx()
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )
    }
}
