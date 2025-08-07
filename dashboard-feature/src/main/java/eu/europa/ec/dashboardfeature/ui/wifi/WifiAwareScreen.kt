package eu.europa.ec.dashboardfeature.ui.wifi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.ui.transactions.list.DashboardEvent
import eu.europa.ec.dashboardfeature.ui.transactions.list.OpenSideMenuEvent
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
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiAwareScreen(
    navHostController: NavController,
    viewModel: WifiAwareViewModel,
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val effects = viewModel.effect
    val locationManager = context.getSystemService<LocationManager>()
    var isLocationEnabled by remember { mutableStateOf(true) }



    LaunchedEffect(Unit) {
        isLocationEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ?: false
    }

    val openLocationSettings = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            val denied = results.filterValues { !it }.keys
            if (denied.isEmpty() && isLocationEnabled) {
                viewModel.handleEvents(Event.StartDiscovery)
            } else {
                Toast.makeText(
                    context,
                    "You need to authorize permissions and enable Location.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )


    LaunchedEffect(Unit) {
        if (!isLocationEnabled) {
            openLocationSettings.launch(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            )
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )
            )
        }
    }


    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { context.finish() },
        topBar = {
            TopBar(
                onDashboardEventSent = onDashboardEventSent
            )
        }
    ) { paddingValues ->

        Content(
            state = state,
            effectFlow = viewModel.effect,
            onNavigationRequested = { navigationEffect ->
                handleNavigationEffect(navigationEffect, navHostController, context)
            },
            paddingValues = paddingValues,
            viewModel = viewModel
        )
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is Effect.Navigation.Pop -> navHostController.popBackStack()
                is Effect.Navigation.SwitchScreen -> navHostController.navigate(effect.screenRoute) {
                    popUpTo(effect.popUpToScreenRoute) { inclusive = effect.inclusive }
                }
                is Effect.ShowPermissionDenied -> Toast.makeText(
                    context,
                    "Permissions denied: ${effect.missing.joinToString()}",
                    Toast.LENGTH_LONG
                ).show()
                is Effect.UpdatePeers -> {

                }

                Effect.RequestPermissions -> {
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.NEARBY_WIFI_DEVICES,
                    ))
                }
            }
        }
    }
}


@Composable
private fun TopBar(
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SPACING_SMALL.dp,
                vertical = SPACING_MEDIUM.dp
            )
    ) {
        WrapIconButton(
            modifier = Modifier.align(Alignment.CenterStart),
            iconData = AppIcons.Menu,
            customTint = MaterialTheme.colorScheme.onSurface,
        ) {
            onDashboardEventSent(OpenSideMenuEvent)
        }

        Text(
            modifier = Modifier.align(Alignment.Center),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            text = stringResource(R.string.wifi_screen_title)
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
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(navigationEffect.popUpToScreenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
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
    viewModel: WifiAwareViewModel? = null
) {
    // collect navigation effects
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
        if (state?.isWifiAwareSupported ?: false) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = SPACING_MEDIUM.dp),
                verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
            ) {
                InitWifiAware(viewModel)
            }
        } else {
            PermissionLauncherDenied()
        }
    }
}

@Composable
private fun PermissionLauncherDenied(){
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SPACING_MEDIUM.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.WifiOff,
            contentDescription = stringResource(R.string.wifi_disconnected),
            modifier = Modifier
                .size(96.dp)
                .padding(bottom = SPACING_MEDIUM.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VSpacer.Small()
        Text(
            text = stringResource(R.string.wifi_disconnected_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        VSpacer.Small()
        Text(
            text = stringResource(R.string.wifi_disconnected_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}



@Composable
fun InitWifiAware(viewModel: WifiAwareViewModel?) {
    Text(
        text = stringResource(R.string.wifi_aware_intit_title),
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            fontSize = 14.sp
        )
    )
    Text(
        stringResource(R.string.wifi_aware_intit_description),
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
            fontSize = 8.sp
        )
    )
    VSpacer.Small()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SPACING_MEDIUM.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.WifiFind,
            contentDescription = stringResource(R.string.wifi_disconnected),
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
    VSpacer.Small()
    DoubleBtn(
        { viewModel?.setEvent(Event.StartDiscovery) },
        { viewModel?.setEvent(Event.GoBack) }
    )
}


@Composable
private fun DoubleBtn(
    onScan: () -> Unit,
    onCancel: () -> Unit,
) {
    val buttons = StickyBottomType.TwoButtons(
        primaryButtonConfig = ButtonConfig(
            type = ButtonType.SECONDARY,
            onClick = { onCancel() } ),
        secondaryButtonConfig = ButtonConfig(
            type = ButtonType.PRIMARY,
            onClick = { onScan() })
    )
    WrapStickyBottomContent(
        stickyBottomModifier = Modifier
            .fillMaxWidth()
        ,
        stickyBottomConfig = StickyBottomConfig(type = buttons, showDivider = false)
    ) {
        when (it?.type) {
            ButtonType.PRIMARY -> Text(text = stringResource(id = R.string.generic_confirm_capitalized))
            ButtonType.SECONDARY -> Text(text = stringResource(id = R.string.generic_cancel_capitalized))
            else -> {}
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@ThemeModePreviews
@Composable
private fun WifiWareScreenContentPreview() {
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
                paddingValues = PaddingValues(0.dp)
            )
        }
    }
}

