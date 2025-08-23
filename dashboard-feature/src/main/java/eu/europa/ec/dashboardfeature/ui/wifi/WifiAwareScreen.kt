package eu.europa.ec.dashboardfeature.ui.wifi

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import eu.europa.ec.uilogic.extension.openAppSettings
import eu.europa.ec.uilogic.extension.openWifiSettings

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WifiAwareScreen(
    navHostController: NavController,
    viewModel: WifiAwareViewModel,
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val effects = viewModel.effect
    val isRequestingPermissions = remember { mutableStateOf(false) }
    val showRationale = remember { mutableStateOf(false) }
    val rationalePermissions = remember { mutableStateOf<List<String>>(emptyList()) }
    val currentPermission = remember { mutableStateOf<String?>(null) }
    val permissionQueue = remember {
        mutableStateListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            println("[WifiAwareScreen] Permission result for ${currentPermission.value}: $isGranted")
            isRequestingPermissions.value = false
            currentPermission.value?.let { perm ->
                if (!isGranted) {
                    val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                        context as Activity,
                        perm
                    )
                    if (shouldShowRationale) {
                        rationalePermissions.value = listOf(perm)
                        showRationale.value = true
                    } else {
                        context.openAppSettings()
                    }
                } else {
                    println("[WifiAwareScreen] Permission granted: $perm")
                }
            }
            if (permissionQueue.isNotEmpty()) {
                permissionQueue.removeAt(0)
            } else {
                viewModel.setState { copy(hasPermissions = true) }
                viewModel.handleEvents(Event.CheckPermissions)
            }
        }
    )


    LaunchedEffect(permissionQueue.size) {
        if (permissionQueue.isNotEmpty() && !isRequestingPermissions.value) {
            val next = permissionQueue[0]
            currentPermission.value = next
            isRequestingPermissions.value = true
            permissionLauncher.launch(next)
        } else if (permissionQueue.isEmpty()) {
            viewModel.setState { copy(hasPermissions = true) }
            viewModel.handleEvents(Event.CheckPermissions)
        }
    }

    if (showRationale.value) {
        PermissionRationaleDialog(
            permissions = rationalePermissions.value,
            onConfirm = {
                showRationale.value = false
                isRequestingPermissions.value = true
                currentPermission.value?.let { perm ->
                    permissionLauncher.launch(perm)
                }
            },
            onDismiss = {
                showRationale.value = false
                viewModel.setEffect { Effect.ShowPermissionDenied(rationalePermissions.value) }
            }
        )
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is Effect.RequestPermissions -> {
                    if (!isRequestingPermissions.value) {
                        println("[WifiAwareScreen] Starting sequential permission requests")
                        permissionQueue.clear()
                        permissionQueue.addAll(effect.permissions)
                        requestNextPermission(permissionQueue, permissionLauncher, currentPermission, isRequestingPermissions, rationalePermissions, showRationale, context)
                    }
                }
                is Effect.ShowPermissionDenied -> {
                    Toast.makeText(
                        context,
                        "Permissions denied: ${effect.missing.joinToString()}. Enable Wi-Fi and location services.",
                        Toast.LENGTH_LONG
                    ).show()
                    context.openWifiSettings()
                }
                is Effect.Navigation -> {
                    handleNavigationEffect(effect, navHostController, context)
                }
                is Effect.UpdatePeers -> {
                    println("[WifiAwareScreen] Peers updated: ${effect.peers}")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        println("[WifiAwareScreen] Initiating permission check")
        viewModel.handleEvents(Event.CheckPermissions)
    }

    ContentScreen(
        isLoading = state.isLoading == true,
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
}

private fun requestNextPermission(
    permissionQueue: SnapshotStateList<String>,
    permissionLauncher: ActivityResultLauncher<String>,
    currentPermission: MutableState<String?>,
    isRequestingPermissions: MutableState<Boolean>,
    rationalePermissions: MutableState<List<String>>,
    showRationale: MutableState<Boolean>,
    context: Context
) {
    if (permissionQueue.isNotEmpty()) {
        val perm = permissionQueue.first()
        currentPermission.value = perm
        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            context as Activity,
            perm
        )
        if (shouldShowRationale) {
            rationalePermissions.value = listOf(perm)
            showRationale.value = true
        } else {
            isRequestingPermissions.value = true
            permissionLauncher.launch(perm)
        }
    }
}

@Composable
private fun PermissionRationaleDialog(
    permissions: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Permission Required",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "This app needs the following permissions to enable Wi-Fi Aware functionality:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                permissions.forEach { permission ->
                    Text(
                        text = when (permission) {
                            Manifest.permission.ACCESS_FINE_LOCATION -> "• Location: Required to discover nearby devices."
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "• Background Location: Required for continuous scanning."
                            Manifest.permission.NEARBY_WIFI_DEVICES -> "• Nearby Wi-Fi Devices: Required to connect to devices via Wi-Fi Aware."
                            else -> "• $permission: Required for app functionality."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
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
                    InitWifiAware(viewModel)
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
fun InitWifiAware(viewModel: WifiAwareViewModel?) {
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
                DoubleBtn(
                    onPublisher = { viewModel?.setEvent(Event.StartDiscovery) },
                    onSubscriber = { viewModel?.setEvent(Event.StartSubscription) },
                )
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

