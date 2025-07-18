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

package eu.europa.ec.backuplogic.ui.restoring.setupSlider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.utils.ALPHA_DISABLED
import eu.europa.ec.uilogic.component.utils.ALPHA_ENABLED
import eu.europa.ec.uilogic.component.utils.SIZE_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapText


@Composable
fun EnterPhraseContentPage(
    words: List<String>,
    onWordsChanged: (List<String>) -> Unit,
    onSubmit: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val wordState = remember { mutableStateListOf(*words.toTypedArray()) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(0.dp)
            )
            .padding(SIZE_MEDIUM.dp)
    ) {
        WrapText(
            text = stringResource(R.string.consent_backup_phase_page_title),
            textConfig = TextConfig(
                style = MaterialTheme.typography.titleMedium.merge(
                    TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        letterSpacing = (-0.02).sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            )
        )
        VSpacer.Custom(SPACING_SMALL)

        Column(
            verticalArrangement = Arrangement.spacedBy(SPACING_EXTRA_SMALL.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            wordState.chunked(3).forEachIndexed { rowIndex, rowWords ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SPACING_EXTRA_SMALL.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowWords.forEachIndexed { colIndex, _ ->
                        val index = rowIndex * 3 + colIndex
                        OutlinedTextField(
                            value = wordState[index],
                            onValueChange = { newValue ->
                                wordState[index] = newValue
                                onWordsChanged(wordState.toList())
                            },
                            label = { Text(text = "${index + 1}") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(SIZE_EXTRA_LARGE.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                            enabled = true,
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}
