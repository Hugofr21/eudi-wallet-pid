package eu.europa.ec.dashboardfeature.ui.wifi.compoment

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
import eu.europa.ec.dashboardfeature.ui.wifi.Event
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
    viewModel: WifiAwareViewModel?,
    paddingValues: PaddingValues,
    isLoading: Boolean,
    onShowQrCode: () -> Unit
) {
    val buttons = StickyBottomType.TwoButtons(
        primaryButtonConfig = ButtonConfig(
            type = ButtonType.SECONDARY,
            onClick = { viewModel?.setEvent(Event.GoBack) }
        ),
        secondaryButtonConfig = ButtonConfig(
            type = ButtonType.PRIMARY,
            enabled = !isLoading,
            onClick = {
                viewModel?.setEvent(Event.StartDiscovery)
                onShowQrCode()
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
                        onClick = { viewModel?.setEvent(Event.GoBack) }
                    )
                    VSpacer.Small()
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
                            viewModel?.setEvent(Event.StartDiscovery)
                            onShowQrCode()
                        }
                    )
                    VSpacer.Small()
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
