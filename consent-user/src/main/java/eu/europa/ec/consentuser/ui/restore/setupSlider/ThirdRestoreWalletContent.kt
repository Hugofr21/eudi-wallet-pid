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

package eu.europa.ec.consentuser.ui.restore.setupSlider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.CheckboxWithTextData
import eu.europa.ec.uilogic.component.WrapCheckboxWithLabel
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapText


@Composable
fun ThirdRestoreWalletContent(
    options: List<String>,
    selected: Set<String>?,
    onOptionToggled: (String) -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier

) {

    Column(
        modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = SPACING_MEDIUM.dp, vertical = SPACING_SMALL.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
    ) {
        WrapText(
            text = stringResource(R.string.consent_backup_third_page_restore_wallet_title),
            textConfig = TextConfig(
                style = MaterialTheme.typography.titleLarge.merge(
                    TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 32.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.02).sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            )
        )

        WrapText(
            text = stringResource(R.string.consent_backup_third_page_restore_wallet_description),
            textConfig = TextConfig(
                style = MaterialTheme.typography.bodyMedium
            )
        )

        ListWrapCheckboxWithLabel(
            listOption = options,
            selectedOptions = selected,
            onOptionToggled = onOptionToggled
        )

    }
}

@Composable
fun ListWrapCheckboxWithLabel(
    listOption: List<String>,
    selectedOptions: Set<String>?,
    onOptionToggled: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        listOption.forEach { option ->
            val isChecked = selectedOptions?.contains(option)?: false
            WrapCheckboxWithLabel(
                checkboxData = CheckboxWithTextData(
                    isChecked = isChecked,
                    onCheckedChange = {
                        onOptionToggled(option)
                    },
                    text = option,
                    enabled = true
                )
            )
        }
    }
}
