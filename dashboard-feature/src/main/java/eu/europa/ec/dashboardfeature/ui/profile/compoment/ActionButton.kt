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

package eu.europa.ec.dashboardfeature.ui.profile.compoment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.dashboardfeature.ui.profile.Event
import eu.europa.ec.dashboardfeature.ui.profile.ProfileViewModel
import eu.europa.ec.dashboardfeature.ui.wifi.WifiAwareViewModel
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent



@Composable
fun ActionButtons(
    viewModel: ProfileViewModel?,
    paddingValues: PaddingValues,
    isLoading: Boolean,
) {
    val buttons = StickyBottomType.TwoButtons(
        primaryButtonConfig = ButtonConfig(
            type = ButtonType.PRIMARY,
            onClick = { viewModel?.setEvent(Event.AddDocument) }
        ),
        secondaryButtonConfig = ButtonConfig(
            type = ButtonType.SECONDARY,
            enabled = !isLoading,
            onClick = {
                viewModel?.setEvent(Event.CreateQrCode)
            }
        )
    )
    WrapStickyBottomContent(
        stickyBottomModifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues),
        stickyBottomConfig = StickyBottomConfig(type = buttons, showDivider = false)
    ) { buttonType ->
        when (buttonType?.type) {
            ButtonType.PRIMARY -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WrapIconButton(
                        modifier = Modifier.size(32.dp),
                        iconData = AppIcons.Add,
                        enabled = !isLoading,
                        onClick = { viewModel?.setEvent(Event.AddDocument) }
                    )
                    VSpacer.ExtraSmall()
                    Text(
                        text = stringResource(R.string.string_document),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
            ButtonType.SECONDARY -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WrapIconButton(
                        modifier = Modifier.size(32.dp),
                        iconData = AppIcons.QrScanner,
                        enabled = !isLoading,
                        onClick = {
                            viewModel?.setEvent(Event.CreateQrCode)
                        }
                    )
                    VSpacer.ExtraSmall()
                    Text(
                        text = stringResource(R.string.create_qr_code),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {}
        }
    }
}
