package eu.europa.ec.proximityfeature.ui.generateQrcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.proximityfeature.ui.generateQrcode.qrcode.QRCode
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ContentTitle
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.navigation.ProximityScreens
import io.ktor.util.sha1
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach


@Composable
fun ProximityGenerateScreen(
    navController: NavController,
    viewModel: ProximityGenerateQrViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) },
        contentErrorConfig = state.error,
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onNavigationRequested = { navigationEffect ->
                when (navigationEffect) {
                    is Effect.Navigation.SwitchScreen -> {
                        navController.navigate(navigationEffect.screenRoute) {
                            popUpTo(ProximityScreens.QR.screenRoute) {
                                inclusive = true
                            }
                        }
                    }

                    is Effect.Navigation.Pop -> {
                        navController.popBackStack()
                    }

                    Effect.Navigation.Share -> {

                    }
                }
            },
            paddingValues = paddingValues,
            viewModel
        )
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.Init)
    }


}

@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onNavigationRequested: (navigationEffect: Effect.Navigation) -> Unit,
    paddingValues: PaddingValues,
    viewModel: ProximityGenerateQrViewModel,
) {
    val configuration = LocalConfiguration.current
    val qrSize = (configuration.screenWidthDp / 1.5).dp


    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(paddingValues)
        ) {
            ContentTitle(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(id = R.string.proximity_qr_title),
                subtitle = stringResource(id = R.string.proximity_qr_subtitle)
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                QRDialog(
                    state = state,
                    onDismissRequest = { viewModel.setEvent(Event.GoBack) },
                    onRetry = { viewModel.setEvent(Event.GoBack) },
                    onShare = { viewModel.setEvent(Event.Share) },
                    qrSize = qrSize
                )

            }
        }

    }


    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
            }
        }.collect()
    }
}


@Composable
private fun QRDialog(
    state: State,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
    onShare: (String) -> Unit = {},
    qrSize: Dp = 300.dp
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.qr_code_title, "QR Code"),
            style = MaterialTheme.typography.titleMedium
        )

        VSpacer.Medium()

        when {
            state.isLoading && state.qrCodeData.isNullOrBlank() -> {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(qrSize),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    VSpacer.Small()
                    Text(
                        text = stringResource(
                            id = R.string.qr_generating_text
                        )
                    )
                }
            }

            !state.qrCodeData.isNullOrBlank() -> {

                QRCode(
                    modifier = Modifier
                        .size(qrSize)
                        .align(Alignment.CenterHorizontally),
                    qrCode = state.qrCodeData ?: "",
                    qrSize = qrSize
                )

                VSpacer.Medium()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        shape = RoundedCornerShape(0.dp),
                        onClick = { onShare(state.qrCodeData ?: "") }) {
                        Text(text = stringResource(id = R.string.request_sticky_button_text))
                    }

                    OutlinedButton(onClick = { onDismissRequest() }) {
                        Text(text = stringResource(id = R.string.close))
                    }
                }
            }

            state.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(qrSize),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.error.errorSubTitle
                            ?: stringResource(id = R.string.qr_error)
                    )
                    VSpacer.Small()
                    Row {
                        Button(
                            shape = RoundedCornerShape(0.dp),
                            onClick = { onRetry() }) {
                            Text(
                                text = stringResource(id = R.string.retry),)

                        }
                        VSpacer.ExtraSmall()

                        OutlinedButton(onClick = { onDismissRequest() }) {
                            Text(text = stringResource(id = R.string.close))
                        }
                    }
                }
            }

            else -> {

                Text(text = stringResource(id = R.string.qr_no_data))
                VSpacer.Small()
                OutlinedButton(onClick = { onDismissRequest() }) {
                    Text(text = stringResource(id = R.string.close))
                }
            }
        }
    }
}
