package com.supakeyboard.neuralkey.ime.keyboard

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.supakeyboard.neuralkey.ime.mvi.KeyboardIntent
import com.supakeyboard.neuralkey.ime.mvi.KeyboardUiState

@Composable
fun KeyboardGridView(
    uiState: KeyboardUiState,
    onIntent: (KeyboardIntent) -> Unit,
    modifier: Modifier = Modifier,
    inputType: Int = EditorInfo.IME_NULL,
    theme: com.supakeyboard.neuralkey.ime.theme.KeyboardTheme = uiState.currentTheme,
    isSplitLayout: Boolean = false
) {
    val isNumericField = inputType and EditorInfo.TYPE_MASK_CLASS == EditorInfo.TYPE_CLASS_NUMBER
    val isPhoneField = inputType and EditorInfo.TYPE_MASK_CLASS == EditorInfo.TYPE_CLASS_PHONE

    val activeLayout = when {
        isPhoneField -> KeyboardLayouts.phoneKeypad
        isNumericField && !uiState.isSymbols -> KeyboardLayouts.numericKeypad
        isNumericField && !uiState.isSymbolsPage2 -> KeyboardLayouts.symbolsRowsPage1
        isNumericField -> KeyboardLayouts.symbolsRowsPage2
        !uiState.isSymbols -> KeyboardLayouts.qwertyRows
        !uiState.isSymbolsPage2 -> KeyboardLayouts.symbolsRowsPage1
        else -> KeyboardLayouts.symbolsRowsPage2
    }

    var gridRootPosition by remember { mutableStateOf(Offset.Zero) }
    val characterKeyBounds = remember { mutableStateMapOf<Char, Rect>() }
    val glideTrailPoints = remember { mutableStateListOf<Offset>() }
    val glidedChars = remember { mutableStateListOf<Char>() }

    val handleGlideUpdate: (Char, Offset) -> Unit = { startChar, rootPos ->
        if (glidedChars.isEmpty()) {
            glidedChars.add(startChar)
            characterKeyBounds[startChar]?.center?.let { startCenter ->
                glideTrailPoints.add(startCenter - gridRootPosition)
            }
        }
        val localPoint = rootPos - gridRootPosition
        glideTrailPoints.add(localPoint)
        if (glideTrailPoints.size > 44) {
            glideTrailPoints.removeAt(0)
        }
        val hitEntry = characterKeyBounds.entries.firstOrNull { (_, rect) ->
            rect.contains(rootPos)
        }
        if (hitEntry != null && glidedChars.lastOrNull() != hitEntry.key) {
            glidedChars.add(hitEntry.key)
        }
    }

    val handleGlideFinish: () -> Unit = {
        val path = glidedChars.toList()
        glidedChars.clear()
        glideTrailPoints.clear()
        if (path.size >= 2) {
            val rawSequence = buildString {
                path.forEachIndexed { idx, c ->
                    if (idx == 0 && (uiState.isShifted || uiState.isCapsLocked)) {
                        append(c.uppercaseChar())
                    } else {
                        append(c)
                    }
                }
            }
            onIntent(KeyboardIntent.GlideTypingPath(rawSequence))
        }
    }

    val handleGlideCancel: () -> Unit = {
        glidedChars.clear()
        glideTrailPoints.clear()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .onGloballyPositioned { coords ->
                    gridRootPosition = coords.positionInRoot()
                }
                .drawWithContent {
                    drawContent()
                    if (glideTrailPoints.size >= 2) {
                        val trailPath = Path().apply {
                            moveTo(glideTrailPoints.first().x, glideTrailPoints.first().y)
                            for (i in 1 until glideTrailPoints.size) {
                                val prev = glideTrailPoints[i - 1]
                                val curr = glideTrailPoints[i]
                                quadraticTo(
                                    prev.x,
                                    prev.y,
                                    (prev.x + curr.x) / 2f,
                                    (prev.y + curr.y) / 2f
                                )
                            }
                            lineTo(glideTrailPoints.last().x, glideTrailPoints.last().y)
                        }
                        // Outer neon halo
                        drawPath(
                            path = trailPath,
                            color = theme.accentPrimary.copy(alpha = 0.28f),
                            style = Stroke(
                                width = 16.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        // Mid neon energy beam
                        drawPath(
                            path = trailPath,
                            color = theme.accentSecondary.copy(alpha = 0.65f),
                            style = Stroke(
                                width = 8.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        // Inner bright core
                        drawPath(
                            path = trailPath,
                            color = Color.White.copy(alpha = 0.92f),
                            style = Stroke(
                                width = 3.2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        // Comet head orb
                        val head = glideTrailPoints.last()
                        drawCircle(
                            color = theme.accentPrimary.copy(alpha = 0.45f),
                            radius = 10.dp.toPx(),
                            center = head
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4.5.dp.toPx(),
                            center = head
                        )
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            activeLayout.forEach { rowKeys ->
                val displayKeys = if (isSplitLayout && !isPhoneField && !isNumericField) {
                    buildSplitRow(rowKeys)
                } else {
                    rowKeys
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.Center
                ) {
                    displayKeys.forEach { key ->
                        KeyView(
                            key = key,
                            isShifted = uiState.isShifted,
                            isCapsLocked = uiState.isCapsLocked,
                            hapticEnabled = uiState.hapticEnabled,
                            hapticIntensity = uiState.hapticIntensity,
                            activeModelName = uiState.activeModel?.name,
                            imeAction = uiState.imeAction,
                            theme = theme,
                            onIntent = onIntent,
                            glideTypingEnabled = uiState.glideTypingEnabled && !uiState.isSymbols && !isNumericField && !isPhoneField,
                            onCharacterKeyBoundsChanged = { ch, rect ->
                                characterKeyBounds[ch] = rect
                            },
                            onGlideUpdate = handleGlideUpdate,
                            onGlideFinish = handleGlideFinish,
                            onGlideCancel = handleGlideCancel
                        )
                    }
                }
            }
        }
    }
}

/**
 * Inserts an ergonomic central thumb gap for unfolded foldable inner screens and tablets.
 * On the bottom row, splits the spacebar into left and right thumb spacebars separated by the gap.
 */
private fun buildSplitRow(rowKeys: List<KeyDefinition>): List<KeyDefinition> {
    val spaceIndex = rowKeys.indexOfFirst { it.type == KeyType.SPACE }
    if (spaceIndex != -1) {
        val spaceKey = rowKeys[spaceIndex]
        val halfSpace = spaceKey.copy(weight = (spaceKey.weight / 2f).coerceAtLeast(1.8f))
        return buildList {
            addAll(rowKeys.subList(0, spaceIndex))
            add(halfSpace)
            add(KeyDefinition(primaryText = "", type = KeyType.SPACER, weight = 1.4f))
            add(halfSpace)
            addAll(rowKeys.subList(spaceIndex + 1, rowKeys.size))
        }
    }
    val nonSpacers = rowKeys.filter { it.type != KeyType.SPACER }
    val splitIndex = nonSpacers.size / 2
    return buildList {
        addAll(nonSpacers.subList(0, splitIndex))
        add(KeyDefinition(primaryText = "", type = KeyType.SPACER, weight = 1.4f))
        addAll(nonSpacers.subList(splitIndex, nonSpacers.size))
    }
}
