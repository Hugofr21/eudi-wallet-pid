package eu.europa.ec.dashboardfeature.ui.profile.compoment

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.wrap.BottomSheetTextDataUi
import eu.europa.ec.uilogic.component.wrap.DialogBottomSheet

@Composable
fun DialogQrCode(){
    DialogBottomSheet(
        textData = BottomSheetTextDataUi(
            title = stringResource(id = R.string.dashboard_bottom_sheet_bluetooth_title),
            message = stringResource(id = R.string.dashboard_bottom_sheet_bluetooth_subtitle),
            positiveButtonText = stringResource(id = R.string.dashboard_bottom_sheet_bluetooth_primary_button_text),
            negativeButtonText = stringResource(id = R.string.dashboard_bottom_sheet_bluetooth_secondary_button_text),
        ),
        onPositiveClick = {

        },
        onNegativeClick = {

        }
    )
}