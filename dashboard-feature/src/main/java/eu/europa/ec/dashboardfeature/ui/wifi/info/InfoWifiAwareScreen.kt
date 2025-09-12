package eu.europa.ec.dashboardfeature.ui.wifi.info

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.ui.transactions.list.DashboardEvent
import eu.europa.ec.dashboardfeature.ui.transactions.list.OpenSideMenuEvent
import eu.europa.ec.dashboardfeature.ui.wifi.Effect
import eu.europa.ec.dashboardfeature.ui.wifi.Event
import eu.europa.ec.dashboardfeature.ui.wifi.State
import eu.europa.ec.dashboardfeature.ui.wifi.WifiAwareViewModel
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import eu.europa.ec.uilogic.extension.finish
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow


@Composable
fun InfoWifiAware(
    navHostController: NavController,
    viewModel: WifiAwareViewModel,
) {
    val context = LocalContext.current
    val state by viewModel.viewState.collectAsStateWithLifecycle()

    ContentScreen(
        isLoading = state.isLoading == true,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { context.finish() },
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onNavigationRequested = { navigationEffect -> handleNavigationEffect(navigationEffect, navHostController, context) },
            paddingValues = paddingValues,
            viewModel = viewModel
        )
    }
}


private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
    context: Context,
) {
    when (navigationEffect) {
        is Effect.Navigation.Pop -> context.finish()
        is Effect.Navigation.SwitchScreen -> {
            val dest = navigationEffect.screenRoute
            navigationEffect.popUpToScreenRoute?.takeIf { it.isNotBlank() }?.let { popRoute ->
                navController.navigate(dest) {
                    popUpTo(popRoute) { inclusive = navigationEffect.inclusive }
                }
            } ?: navController.navigate(dest)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    effectFlow: Flow<Effect>,
    onNavigationRequested: (navigationEffect: Effect.Navigation) -> Unit,
    paddingValues: PaddingValues,
    state: State?,
    viewModel: WifiAwareViewModel?
) {
    LaunchedEffect(effectFlow) {
        effectFlow.collect { effect ->
            if (effect is Effect.Navigation) {
                onNavigationRequested(effect)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = paddingValues.calculateTopPadding(),
                start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                end = paddingValues.calculateEndPadding(LayoutDirection.Ltr)
            )
    ) {
        WifiTroubleshootingSteps(viewModel)
    }
}

@Composable
fun WifiTroubleshootingSteps(viewModel: WifiAwareViewModel?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = stringResource(R.string.wifi_aware_troubleshoot_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 18.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )


            TroubleshootingStep(
                stepNumber = 1,
                icon = Icons.Default.Wifi,
                title = "Check if WiFi is on",
                description = "Open quick settings (scroll from top to bottom) and tap the WiFi icon to turn it on."
            )

            VSpacer.Medium()


            TroubleshootingStep(
                stepNumber = 2,
                icon = Icons.Default.Settings,
                title = "Check app permissions",
                description = "Tap the app icon, then go to 'App info' > 'Permissions'. Make sure 'Nearby devices' and 'Location' are enabled."
            )

            VSpacer.Medium()


            TroubleshootingStep(
                stepNumber = 3,
                icon = Icons.Default.LocationOn,
                title = "Turn on location services",
                description = "In 'Settings' > 'Location', enable the location toggle. For WiFi Aware, enable 'WiFi Scanning' under 'Location Services'."
            )

            VSpacer.Medium()

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                WrapStickyBottomContent(
                    stickyBottomModifier = Modifier.fillMaxWidth(),
                    stickyBottomConfig = StickyBottomConfig(
                        type = StickyBottomType.OneButton(
                            ButtonConfig(
                                type = ButtonType.PRIMARY,
                                onClick = { viewModel?.setEvent(Event.GoBack) }
                            )
                        ),
                        showDivider = false
                    )
                ) {
                    Text(text = stringResource(id = R.string.close))
                }
            }
        }
    }
}

@Composable
fun TroubleshootingStep(
    stepNumber: Int,
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .padding(end = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                text = "Passo $stepNumber: $title",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp
                ),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@ThemeModePreviews
@Composable
private fun InfoWifiWareScreenContentPreview() {
    PreviewTheme {

        ContentScreen(
            isLoading = false,
            navigatableAction = ScreenNavigateAction.BACKABLE,
            onBack = {

            },
            stickyBottom = {
            }
        ) { paddingValues ->
            Content(
                state = null,
                effectFlow = Channel<Effect>().receiveAsFlow(),
                onNavigationRequested = {},
                paddingValues = PaddingValues(0.dp),
                viewModel = null
            )
        }
    }
}

