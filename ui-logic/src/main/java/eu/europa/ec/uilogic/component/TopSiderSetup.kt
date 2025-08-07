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


import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_LARGE
import eu.europa.ec.uilogic.component.wrap.WrapStepBar

@Composable
fun TopStepBar(currentStep: Int) {

    val step1 = stringResource(id = R.string.user_consent_step_1_title)
    val step2 = stringResource(id = R.string.user_consent_step_2_title)
    val step3 = stringResource(id = R.string.user_consent_step_3_title)
    val step4 = stringResource(id = R.string.user_consent_step_4_title)

    WrapStepBar(
        currentStep = currentStep,
        steps = listOf(step1, step2, step3, step4),
        modifier = Modifier.padding(
            top = SPACING_EXTRA_LARGE.dp,
        ),
    )
}

@ThemeModePreviews
@Composable
fun TopStepBarPreview() {
    TopStepBar(currentStep = 1)
}