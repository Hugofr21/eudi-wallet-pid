package eu.europa.ec.dashboardfeature.ui.home.compoment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.dashboardfeature.ui.home.Event
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapText

@Composable
fun ScanButton(
    onEventSend: (Event) -> Unit,
) {
    Column {
        VSpacer.Small()
        FloatingActionButton(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            onClick = {
                onEventSend(Event.GoToScanQR)
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
        ) {
            WrapIcon(
                modifier = Modifier.padding(SPACING_LARGE.dp),
                iconData = AppIcons.QrScanner
            )
        }
        VSpacer.ExtraSmall()
        WrapText(
            text = stringResource(R.string.home_screen_sign_document_option_scan_qr),
            textConfig = TextConfig(
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        VSpacer.Small()
    }
}