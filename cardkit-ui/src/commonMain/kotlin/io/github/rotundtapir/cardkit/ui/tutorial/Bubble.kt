// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Shared machinery for a speech bubble anchored to an on-screen element (the tutorial advice bubble
 * and the emote bubble both use it). [target] is the anchor's bounds in root coords; [overlayOrigin]
 * is the game overlay's origin, so the bubble is placed in overlay-local space. The bubble is
 * horizontally centred on the anchor (with a tail sliding to point at it) and vertically placed by
 * [yPlacement]; [tailDown] chooses whether the tail is under the bubble (pointing down at an anchor
 * below) or above it (pointing up at an anchor above).
 */
@Composable
fun BubbleLayout(
    target: Rect,
    overlayOrigin: Offset,
    tailDown: Boolean,
    maxWidth: Dp,
    yPlacement: (local: Rect, bubbleHeight: Int, gap: Int) -> Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var bubbleLeft by remember { mutableIntStateOf(0) }
    val local = target.translate(-overlayOrigin)
    val tailWidth = with(density) { TAIL_WIDTH.toPx() }
    val tailX = { (local.center.x - bubbleLeft - tailWidth / 2).roundToInt() }
    // The overlay is edge-to-edge, so the top clamp must not let a bubble slide under the status bar.
    val topInset = WindowInsets.safeDrawing.getTop(density)

    Layout(
        modifier = modifier,
        content = {
            Column {
                if (!tailDown) BubbleTail(pointUp = true, offsetX = tailX)
                content()
                if (tailDown) BubbleTail(pointUp = false, offsetX = tailX)
            }
        },
    ) { measurables, constraints ->
        val margin = with(density) { 8.dp.roundToPx() }
        val cap = minOf(constraints.maxWidth - margin * 2, with(density) { maxWidth.roundToPx() })
        val placeable = measurables[0].measure(
            Constraints(minWidth = 0, maxWidth = cap, minHeight = 0, maxHeight = constraints.maxHeight),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (local.center.x - placeable.width / 2f).roundToInt()
                .coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
            val gap = with(density) { 2.dp.roundToPx() }
            val topFloor = topInset + margin
            val y = yPlacement(local, placeable.height, gap)
                .coerceIn(topFloor, (constraints.maxHeight - placeable.height - margin).coerceAtLeast(topFloor))
            bubbleLeft = x
            placeable.place(x, y)
        }
    }
}

/** The bubble's little triangular tail, slid horizontally to point at the anchor. */
@Composable
fun BubbleTail(pointUp: Boolean, offsetX: () -> Int, modifier: Modifier = Modifier) {
    val tailColor = Color(0xFFFAFAFA)
    Canvas(
        modifier = modifier
            .offset { IntOffset(offsetX().coerceAtLeast(0), 0) }
            .size(TAIL_WIDTH, 12.dp),
    ) {
        val path = Path().apply {
            if (pointUp) {
                moveTo(0f, size.height)
                lineTo(size.width, size.height)
                lineTo(size.width / 2f, 0f)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
            }
            close()
        }
        drawPath(path, tailColor)
    }
}

private val TAIL_WIDTH = 26.dp
