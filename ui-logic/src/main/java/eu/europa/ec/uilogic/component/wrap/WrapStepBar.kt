/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.uilogic.component.wrap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SIZE_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL_12
import eu.europa.ec.uilogic.component.utils.measureTextWidthInPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch



private val horizontalLabelPaddingDp = SPACING_SMALL_12.dp
private val paddingValues = PaddingValues(
    horizontal = horizontalLabelPaddingDp, vertical = SPACING_EXTRA_SMALL.dp
)
private val labelTextStyle: TextStyle
    @Composable
    get() = MaterialTheme.typography.titleSmall

private const val inactiveTextColorAlpha = 0.35f

@Composable
fun WrapStepBar(currentStep: Int, steps: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentStep) {
        coroutineScope.launch {
            val target = (currentStep - 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            userScrollEnabled = true,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            itemsIndexed(steps) { index, text ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(1.dp)
                            .background(
                                if (index <= currentStep)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }

                StepItem(
                    index = index,
                    label = text,
                    state = when {
                        index < currentStep  -> StepState.DONE
                        index == currentStep -> StepState.ACTIVE
                        else                 -> StepState.PENDING
                    }
                )
            }
        }
    }
}

private enum class StepState { DONE, ACTIVE, PENDING }

@Composable
private fun StepItem(index: Int, label: String, state: StepState) {
    val primary   = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val success   = Color(0xFF1D9E75)
    val onSuccess = Color(0xFF04342C)
    val outline   = MaterialTheme.colorScheme.outlineVariant

    val circleColor = when (state) {
        StepState.DONE    -> success
        StepState.ACTIVE  -> primary
        StepState.PENDING -> MaterialTheme.colorScheme.surfaceVariant
    }
    val circleContent = when (state) {
        StepState.DONE    -> onSuccess
        StepState.ACTIVE  -> onPrimary
        StepState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val labelColor = when (state) {
        StepState.ACTIVE  -> primary
        StepState.DONE    -> MaterialTheme.colorScheme.onSurfaceVariant
        StepState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val barColor = when (state) {
        StepState.DONE, StepState.ACTIVE -> primary
        StepState.PENDING                -> outline
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
    ) {

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .background(circleColor, CircleShape)
                .then(
                    if (state == StepState.ACTIVE)
                        Modifier.border(3.dp, primary.copy(alpha = 0.18f), CircleShape)
                    else Modifier
                )
        ) {
            if (state == StepState.DONE) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = circleContent,
                    modifier = Modifier.size(13.dp)
                )
            } else {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = circleContent,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (state == StepState.ACTIVE) FontWeight.Medium else FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Barra indicadora por baixo
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(2.dp)
                .background(barColor, RoundedCornerShape(1.dp))
        )
    }
}
@Composable
private fun calculateStartIndexForTrailingItems(
    currentStep: Int,
    steps: List<String>,
    textMeasurer: TextMeasurer
): Int {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val configuration = LocalConfiguration.current
    val rowWidthPx = with(density) { (configuration.screenWidthDp - 2 * SPACING_LARGE).dp.toPx() }
    val paddingWidthPx = with(density) { 2 * horizontalLabelPaddingDp.toPx() }

    var totalWidth = 0f
    var startIndex = currentStep
    for (i in steps.size - 1 downTo 0) {
        val textWidth = measureTextWidthInPx(steps[i], textMeasurer, labelTextStyle, fontScale)
        val stepWidth = textWidth + paddingWidthPx
        if (totalWidth + stepWidth > rowWidthPx) {
            break
        }
        totalWidth += stepWidth
        startIndex = i
    }
    return startIndex
}



@Composable
private fun Label(text: String, index: Int, currentStep: Int) {
    val isActive = index == currentStep
    val textColor = getColor(isActive, inactiveTextColorAlpha)

    Text(
        modifier = Modifier.padding(paddingValues),
        text = text,
        color = textColor,
        style = labelTextStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun getColor(isActive: Boolean, inactiveAlpha: Float) =
    if (isActive) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = inactiveAlpha)


@Composable
@ThemeModePreviews
fun StartupProgressBarPreview() {
    PreviewTheme {
        WrapStepBar(
            currentStep = 1,
            steps = listOf("Welcome", "Consent", "Verification")
        )
    }
}
