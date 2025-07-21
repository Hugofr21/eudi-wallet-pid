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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import eu.europa.ec.uilogic.component.utils.SIZE_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SIZE_MEDIUM
import eu.europa.ec.uilogic.component.utils.SIZE_XXX_LARGE
import eu.europa.ec.uilogic.component.utils.SIZE_XX_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL_12
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
    val indexedWords: Map<Int, String> = wordState.mapIndexed { index, word -> index to word }.toMap()
    val allFilled = wordState.all { it.isNotBlank() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SIZE_MEDIUM.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
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

        // Grid de inputs
        Column(
            verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            wordState.chunked(3).forEachIndexed { rowIndex, rowWords ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowWords.forEachIndexed { colIndex, _ ->
                        val idx = rowIndex * 3 + colIndex
                        OutlinedTextField(
                            value = wordState[idx],
                            onValueChange = { newValue ->
                                wordState[idx] = newValue
                                onWordsChanged(wordState.toList())
                            },
                            label = { Text("${idx + 1}") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 14.sp,
                                lineHeight = 16.sp

                            ),
                            shape = RoundedCornerShape(0.dp)
                        )
                    }
                }
            }
        }

        if (!allFilled) {
            Text(
                text = stringResource(R.string.error_fill_all_words),
                color = Color.Red,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = SPACING_SMALL.dp)
            )
        }

        Spacer(modifier = Modifier.height(SPACING_SMALL.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {  val wordsArray: Array<String> = wordState.toTypedArray()
                    println("List word $wordsArray")
                              onSubmit(wordsArray.toList())
                          },
                enabled = allFilled,
                modifier = Modifier
                    .height(36.dp)
                    .wrapContentWidth()
                    .defaultMinSize(minWidth = 80.dp),
                shape = RoundedCornerShape(0.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_submit),
                    style = MaterialTheme.typography.labelLarge.merge(
                        TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    )
                )
            }
        }
    }
}
