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

package eu.europa.ec.uilogic.component.utils

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle

fun measureTextWidthInPx(
    text: String,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    fontScale: Float
): Int {
    val scaledTextStyle = textStyle.copy(fontSize = textStyle.fontSize * fontScale)
    val textLayoutResult = textMeasurer.measure(
        text = text,
        style = scaledTextStyle,
        maxLines = 1,
        softWrap = false
    )
    return textLayoutResult.size.width
}