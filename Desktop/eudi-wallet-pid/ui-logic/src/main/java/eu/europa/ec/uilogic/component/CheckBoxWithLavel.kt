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

package eu.europa.ec.uilogic.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.wrap.CheckboxDataUi


data class CheckboxWithTextData(
    val isChecked: Boolean,
    val enabled: Boolean = true,
    val onCheckedChange: ((Boolean) -> Unit)? = null,
    val text: String,
    val textPadding: PaddingValues = PaddingValues(0.dp)
)

@Composable
fun WrapCheckboxWithLabel(
    checkboxData: CheckboxWithTextData,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.padding(contentPadding)
        ) {
            Checkbox(
                checked = checkboxData.isChecked,
                onCheckedChange = checkboxData.onCheckedChange,
                enabled = checkboxData.enabled,
                colors = CheckboxDefaults.colors(
                    uncheckedColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = checkboxData.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable {
                    checkboxData.onCheckedChange?.invoke(!checkboxData.isChecked)
                }
            )
        }
    }
}

@ThemeModePreviews
@Composable
private fun WrapCheckboxWithLabelPreview() {

    var isCheckedOne by remember { mutableStateOf(true) }
    var isCheckedTwo by remember { mutableStateOf(false) }

    val checkboxDataOne = CheckboxWithTextData(
        isChecked = isCheckedOne,
        onCheckedChange = { isCheckedOne = it },
        text = "Option One",
        textPadding = PaddingValues(8.dp)
    )

    val checkboxDataTwo = CheckboxWithTextData(
        isChecked = isCheckedTwo,
        onCheckedChange = { isCheckedTwo = it },
        text = "Option Two",
        textPadding = PaddingValues(8.dp)
    )

    PreviewTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            WrapCheckboxWithLabel(
                checkboxData = checkboxDataOne
            )
            Spacer(Modifier.height(12.dp))
            WrapCheckboxWithLabel(
                checkboxData = checkboxDataTwo
            )
        }
    }
}
