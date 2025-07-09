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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews

data class WrapLinkData(
    val textId: Int,
    val isExternal: Boolean = false,
)

private val linkSpacing = TextUnit(value = 0.8f, type = TextUnitType.Sp)

@OptIn(ExperimentalMaterial3Api::class)
val BaseRippleConfiguration: RippleConfiguration
    @Composable get() = RippleConfiguration(
        color = MaterialTheme.colorScheme.secondary,
        rippleAlpha = RippleAlpha(0.1f, 0.1f, 0.04f, 0.3f)
    )



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrapLink(
    data: WrapLinkData,
    onClick: () -> Unit,
) {
    val linkText = buildAnnotatedString {
        withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
            append(stringResource(id = data.textId))
        }
        if (data.isExternal) {
            withStyle(style = SpanStyle()) {
                append(" ↗")
            }
        }
    }

    val textConfig = TextConfig(
        style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = linkSpacing),
        color = MaterialTheme.colorScheme.primary,
    )
    CompositionLocalProvider(LocalRippleConfiguration provides BaseRippleConfiguration) {
        WrapText(
            modifier = Modifier
                .wrapContentWidth()
                .clickable { onClick() },
            text = linkText.toString(),
            textConfig = textConfig
        )
    }
}

@Composable
@ThemeModePreviews
private fun WrapLinkPreview() {
    PreviewTheme {
        WrapLink(
            data = WrapLinkData(
                textId = R.string.wrap_link_consent, isExternal = true
            ), onClick = {
            })
    }
}
