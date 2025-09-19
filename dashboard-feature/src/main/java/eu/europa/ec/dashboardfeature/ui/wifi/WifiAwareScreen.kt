package eu.europa.ec.dashboardfeature.ui.wifi

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import eu.europa.ec.uilogic.extension.findActivity
import eu.europa.ec.uilogic.extension.openAppSettings

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WifiAwareScreen(
    navHostController: NavController,
    viewModel: WifiAwareViewModel,
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val effects = viewModel.effect

    val showRationale = remember { mutableStateOf(false) }
    val rationalePermissions = remember { mutableStateOf<List<String>>(emptyList()) }
    val isRequesting = remember { mutableStateOf(false) }
    val needsBackgroundLocation = remember { mutableStateOf(false) }
    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val fineLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val perm = Manifest.permission.ACCESS_FINE_LOCATION
        val shouldShow = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, perm) } == true
        if (shouldShow) {
            rationalePermissions.value = listOf(perm)
            showRationale.value = true
        } else {
            context.openAppSettings()
        }
    }


    val nearbyWifiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->

        val perm = Manifest.permission.NEARBY_WIFI_DEVICES
        val shouldShow = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, perm) } == true
        if (shouldShow) {
            rationalePermissions.value = listOf(perm)
            showRationale.value = true
        } else {
            context.openAppSettings()
        }
    }



    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is Effect.RequestPermissions -> {
                    if (isRequesting.value) return@collect

                    val permsToRequest = effect.permissions.distinct().toList()
                    when {
                        permsToRequest.contains(Manifest.permission.ACCESS_FINE_LOCATION) && !isGranted(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                            isRequesting.value = true
                            fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                        permsToRequest.contains(Manifest.permission.NEARBY_WIFI_DEVICES) && !isGranted(Manifest.permission.NEARBY_WIFI_DEVICES) -> {
                            isRequesting.value = true
                            nearbyWifiLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                        }

                        else -> {
                            viewModel.setState { copy(hasPermissions = true) }
                            viewModel.handleEvents(Event.CheckPermissions)
                        }
                    }
                }
                is Effect.ShowPermissionDenied -> {
                    context.openAppSettings()
                }
                is Effect.Navigation -> handleNavigationEffect(effect, navHostController, context)
                is Effect.UpdatePeers -> println("[WifiAwareScreen] Peers updated: ${effect.peers}")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!state.hasPermissions) {
            val toRequest = mutableListOf<String>()

            if (needsBackgroundLocation.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (!isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    toRequest += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                }
            }

            if (toRequest.isNotEmpty()) {
                viewModel.setEffect { Effect.RequestPermissions(toRequest) }
            } else {
                viewModel.setState { copy(hasPermissions = true) }
                viewModel.handleEvents(Event.CheckPermissions)
            }
        } else {
            viewModel.handleEvents(Event.CheckPermissions)
        }
    }

    if (showRationale.value) {
        PermissionRationaleDialog(
            permissions = rationalePermissions.value,
            onConfirm = {
                showRationale.value = false
                val perms = rationalePermissions.value.toTypedArray()
                when {
                    perms.contains(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                        isRequesting.value = true
                        fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    perms.contains(Manifest.permission.NEARBY_WIFI_DEVICES) -> {
                        isRequesting.value = true
                        nearbyWifiLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }

                }
            },
            onDismiss = {
                showRationale.value = false
                viewModel.setEffect { Effect.ShowPermissionDenied(rationalePermissions.value) }
            }
        )
    }

    ContentScreen(
        isLoading = state.isLoading == true,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { context.finish() },
        topBar = { TopBar(onDashboardEventSent = onDashboardEventSent) }
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



@Composable
private fun PermissionRationaleDialog(
    permissions: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val message = buildString {
        append("The app needs the following permissions for Wi-Fi Aware operation:\n\n")
        permissions.forEach { p ->
            append(
                when (p) {
                    Manifest.permission.NEARBY_WIFI_DEVICES -> "• Nearby Wi-Fi Devices: Access nearby Wi-Fi devices.\n"
                    Manifest.permission.ACCESS_COARSE_LOCATION -> "• Location (basic): Approximate detection of nearby devices.\n"
                    Manifest.permission.ACCESS_FINE_LOCATION -> "• Location (precise): Precise detection of nearby devices.\n"
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "• Background Location: Required for continuous background discovery.\n"
                    Manifest.permission.FOREGROUND_SERVICE_LOCATION -> "• Foreground Service (Location): Required for running Wi-Fi Aware service in the foreground.\n"
                    else -> "• $p\n"
                }
            )
        }
        append("\nIf you permanently deny these permissions, you will need to manually grant them in the app settings.")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Required Permissions", style = MaterialTheme.typography.headlineSmall) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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

        println("[Content] State: isWifiAwareSupported=${state?.isWifiAwareSupported}, hasPermissions=${state?.hasPermissions}, isDiscovering=${state?.isDiscovering}, discoveredPeers=${state?.discoveredPeers}")
        if (state?.isWifiAwareSupported == true) {
            if (state.hasPermissions) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = SPACING_MEDIUM.dp),
                    verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
                ) {
                    InitWifiAware(viewModel, state)
                    if (state.isDiscovering == true) {
                        Text(
                            text = "Discovering peers...",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(SPACING_MEDIUM.dp)
                        )
                    }
                    if (state.discoveredPeers?.isNotEmpty() == true) {
                        Text(
                            text = "Peers found: ${state.discoveredPeers.size}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(SPACING_MEDIUM.dp)
                        )
                        state.discoveredPeers.forEach { peer ->
                            Text(
                                text = "Peer: $peer",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = SPACING_MEDIUM.dp)
                            )
                        }
                    }
                }
            } else {
                PermissionLauncherDenied(
                    message = "Required permissions not granted. Please grant permissions to use Wi-Fi Aware.",
                    onRetry = { viewModel?.handleEvents(Event.CheckPermissions) }
                )
            }
        } else {
            PermissionLauncherDenied(
                message = "Wi-Fi Aware not available. Ensure Wi-Fi and location services are enabled.",
                onRetry = { viewModel?.handleEvents(Event.CheckPermissions) }
            )
        }
    }
}

@Composable
private fun PermissionLauncherDenied(
    message: String = stringResource(R.string.wifi_disconnected_description),
    onRetry: (() -> Unit)? = null
) {
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
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        VSpacer.Medium()
        if (onRetry != null) {
            WrapStickyBottomContent(
                stickyBottomModifier = Modifier.fillMaxWidth(),
                stickyBottomConfig = StickyBottomConfig(
                    type = StickyBottomType.OneButton(
                        ButtonConfig(
                            type = ButtonType.PRIMARY,
                            onClick = onRetry
                        )
                    ),
                    showDivider = false
                )
            ) {
                Text(text = "Try Again")
            }
        }
    }
}

@Composable
fun InitWifiAware(viewModel: WifiAwareViewModel?, state: State?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.wifi_aware_intit_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 14.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.wifi_aware_intit_description),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                    fontSize = 12.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )

            VSpacer.Medium()

            Icon(
                imageVector = Icons.Filled.WifiFind,
                contentDescription = stringResource(R.string.wifi_disconnected),
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            VSpacer.Medium()

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state?.isDiscovering == true) {
                    WrapStickyBottomContent(
                        stickyBottomModifier = Modifier.fillMaxWidth().zIndex(99f),

                        stickyBottomConfig = StickyBottomConfig(
                            type = StickyBottomType.OneButton(
                                ButtonConfig(
                                    type = ButtonType.PRIMARY,
                                    onClick = { viewModel?.setEvent(Event.StopDiscovery) }
                                )
                            ),
                            showDivider = false
                        )
                    ) {
                        Text(
                            text = "Stop Discovery",
                            modifier = Modifier
                                .clickable {
                                    println("[UI] Inner Text clickable pressed")
                                    viewModel?.setEvent(Event.StopDiscovery)
                                }
                                .padding(12.dp)
                        )
                    }
                } else {
                    DoubleBtn(
                        onPublisher = { viewModel?.setEvent(Event.StartDiscovery) },
                        onSubscriber = { viewModel?.setEvent(Event.StartSubscription) },
                    )
                }
            }
        }
    }
}


@Composable
private fun DoubleBtn(
    onPublisher: () -> Unit,
    onSubscriber: () -> Unit,
) {
    val buttons = StickyBottomType.TwoButtons(
        primaryButtonConfig = ButtonConfig(
            type = ButtonType.SECONDARY,
            onClick = { onSubscriber() }
        ),
        secondaryButtonConfig = ButtonConfig(
            type = ButtonType.PRIMARY,
            onClick = { onPublisher() }
        )
    )
    WrapStickyBottomContent(
        stickyBottomModifier = Modifier.fillMaxWidth(),
        stickyBottomConfig = StickyBottomConfig(type = buttons, showDivider = false)
    ) {
        when (it?.type) {
            ButtonType.PRIMARY -> Text(text = stringResource(id = R.string.generic_wifi_publisher))
            ButtonType.SECONDARY -> Text(text = stringResource(id = R.string.generic_wifi_subscriber))
            else -> {}
        }
    }
}


@Composable
private fun IconLeftTutorial( viewModel: WifiAwareViewModel,){
    IconButton(
        onClick = { viewModel?.handleEvents(Event.Info) },
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Open WiFi troubleshooting steps",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
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
                paddingValues = PaddingValues(0.dp),
                viewModel = null
            )
        }
    }
}

