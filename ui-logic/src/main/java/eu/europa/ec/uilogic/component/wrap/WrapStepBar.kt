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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
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
    val textMeasurer = rememberTextMeasurer()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val startIndex = calculateStartIndex(currentStep, steps, textMeasurer)

    // Substituir a shape utilizada anteriormente por uma forma rectangular (0.dp)
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp), // <-- bordas retas (usar 4.dp, 6.dp, etc. para cantos leves)
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = SIZE_EXTRA_SMALL.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        Box {
            Indicator(currentStep, listState)

            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                userScrollEnabled = true
            ) {
                itemsIndexed(steps) { index, text ->
                    Label(
                        text = text,
                        index = index,
                        currentStep = currentStep
                    )
                }
            }
        }
    }

    scrollToTheStartIndex(startIndex, coroutineScope, listState)
}

@Composable
private fun Indicator(
    currentStep: Int,
    listState: LazyListState
) {
    val density = LocalDensity.current
    val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == currentStep }
    if (itemInfo != null) {
        val offsetDp = with(density) { (itemInfo.offset).toDp() }
        val widthDp = with(density) { (itemInfo.size).toDp() }

        // Altura do indicador alinhada visualmente ao conteúdo; ajuste se necessário.
        val indicatorHeight: Dp = 36.dp // reduzir para 28.dp ou aumentar conforme tipografia

        Box(
            Modifier
                .offset(x = offsetDp)
                .width(widthDp)
                .height(indicatorHeight)
                .padding(vertical = 4.dp) // ajusta o espaçamento vertical dentro do cartão
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(2.dp) // cantos muito discretos; usar 0.dp para recto absoluto
                )
        )
    }
}


@Composable
private fun scrollToTheStartIndex(
    startIndex: Int,
    coroutineScope: CoroutineScope,
    listState: LazyListState
) {
    LaunchedEffect(startIndex) {
        coroutineScope.launch {
            listState.scrollToItem(startIndex)
        }
    }
}

@Composable
private fun calculateStartIndex(
    currentStep: Int,
    steps: List<String>,
    textMeasurer: TextMeasurer
): Int {
    if (currentStep == 0) {
        return 0
    } else if (currentStep < steps.size - 2) {
        return currentStep - 1
    }

    // only for the last two steps we calculate the start index in a fancy way
    // taking into account the screen and font size
    var startIndex = calculateStartIndexForTrailingItems(currentStep, steps, textMeasurer)

    return startIndex
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
