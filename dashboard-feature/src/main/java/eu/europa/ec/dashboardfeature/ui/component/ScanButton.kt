package eu.europa.ec.dashboardfeature.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.WrapIcon

@Composable
fun ScanButton(
    onClick: () -> Unit,
) {
    Column {
        VSpacer.Small()
        FloatingActionButton(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
        ) {
            WrapIcon(
                modifier = Modifier.padding(SPACING_LARGE.dp),
                iconData = AppIcons.QrScanner
            )
        }
    }
}