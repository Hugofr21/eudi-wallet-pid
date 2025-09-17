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

package eu.europa.ec.dashboardfeature.ui.profile


import android.Manifest
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.model.ClaimsUI
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapText
import eu.europa.ec.uilogic.extension.finish
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import eu.europa.ec.dashboardfeature.model.ClaimValue
import eu.europa.ec.dashboardfeature.ui.home.BleAvailability
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.dashboardfeature.ui.profile.compoment.ActionButtons
import eu.europa.ec.dashboardfeature.ui.profile.compoment.NoResults
import eu.europa.ec.uilogic.extension.openAppSettings
import eu.europa.ec.uilogic.extension.openBleSettings


typealias DashboardEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event
typealias OpenSideMenuEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event.SideMenu.Open

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navHostController: NavController,
    viewModel: ProfileViewModel,
) {
    val context = LocalContext.current
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val effects = viewModel.effect

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) },
        stickyBottom = { paddingValues ->
            if (!state.firstName.isNullOrBlank() || !state.lastName.isNullOrBlank()) {
                ActionButtons(
                    viewModel = viewModel,
                    paddingValues = paddingValues,
                    isLoading = state.isLoading,
                )
            }
        }
    ) { paddingValues ->
        println("ProfileScreen: firstName='${state.firstName}', lastName='${state.lastName}'")

        if (state.firstName.isNullOrBlank() && state.lastName.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = SPACING_MEDIUM.dp),
                contentAlignment = Alignment.Center
            ) {
                NoResults(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Content(
                state = state,
                effectFlow = viewModel.effect,
                onNavigationRequested = { navigationEffect ->
                    handleNavigationEffect(navigationEffect, navHostController, context)
                },
                paddingValues = paddingValues
            )
        }
    }

    if (state.bleAvailability == BleAvailability.NO_PERMISSION) {
        RequiredPermissionsAsk(state) { event -> viewModel.setEvent(event) }
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is Effect.Navigation.Pop -> {
                    navHostController.popBackStack()
                }
                is Effect.Navigation.SwitchScreen -> {
                    navHostController.navigate(effect.screenRoute) {
                        popUpTo(effect.popUpToScreenRoute) { inclusive = effect.inclusive }
                    }
                }

                is Effect.Navigation.OnAppSettings -> context.openAppSettings()
                is Effect.Navigation.OnSystemSettings -> context.openBleSettings()

            }
        }
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

        is Effect.Navigation.OnAppSettings -> context.openAppSettings()
        is Effect.Navigation.OnSystemSettings -> context.openBleSettings()
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onNavigationRequested: (navigationEffect: Effect.Navigation) -> Unit,
    paddingValues: PaddingValues
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                paddingValues = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = 0.dp,
                    start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                    end = paddingValues.calculateEndPadding(LayoutDirection.Ltr)
                )
            )
            .verticalScroll(scrollState)
            .padding(vertical = SPACING_MEDIUM.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        CardItem(state)
        ListItemFirstNameAndLastName(state)
        ListItemOther(state)
    }


    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
                else -> {}
            }

        }.collect()
    }
}


@Composable
private fun CardItem(
    state: State,
) {
    val imageBase64 = state.imageBase64

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.medium
            )
            .clip(MaterialTheme.shapes.medium)
    ) {

        ProfileImage(imageBase64)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )

        Text(
            text = "Profile",
            style = MaterialTheme.typography.titleLarge.copy(
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            ),

            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProfileImage(
    imageBase64: String?
) {
    val imageBytes: ByteArray? = remember(imageBase64) {
        imageBase64
            ?.substringAfter(",", missingDelimiterValue = imageBase64)
            ?.replace('_', '/')
            ?.replace('-', '+')
            ?.replace("\\s".toRegex(), "")
            ?.let {
                try {
                    Base64.decode(it, Base64.DEFAULT or Base64.NO_WRAP)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
    }
    
    val decodedBitmap: Bitmap? = remember(imageBytes) {
        imageBytes
            ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    }

    if (decodedBitmap != null) {
        Image(
            bitmap = decodedBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {

        WrapImage(
            iconData = AppIcons.User as IconDataUi,
            modifier = Modifier.fillMaxSize(),
            colorFilter = null,
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun ListItemFirstNameAndLastName(
    state: State ,
){
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_MEDIUM.dp),
        horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
        ) {
            Text(
                text = "First Name",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            WrapText(
                text = state.firstName,
                modifier = Modifier.fillMaxWidth(),
                textConfig = TextConfig(
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            )
        }

        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
        ) {
            Text(
                text = "Last Name",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            WrapText(
                text = state.lastName,
                modifier = Modifier.fillMaxWidth(),
                textConfig = TextConfig(
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            )
        }

    }
}



@Composable
fun ListItemOther(
    state: State,
) {
    val rows = state.claimsUi.chunked(2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_MEDIUM.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        rows.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
            ) {
                pair.forEach { claim ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
                    ) {
                        Text(
                            text = claim.key,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        ClaimValueView(claim.value as ClaimValue)
                    }
                }

                if (pair.size == 1) VSpacer.Small()
            }
        }
    }
}


@Composable
private fun ClaimValueView(value: ClaimValue) {
    when (val v = value) {
        is ClaimValue.Obj -> {
            v.entries.forEach { (subKey, subValue) ->
                Text(
                    text = "$subKey = $subValue",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        is ClaimValue.Arr -> {
            Text(
                text = v.items.joinToString(", ") { it.toString() },
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        is ClaimValue.Simple -> {
            WrapText(
                text = v.text,
                modifier = Modifier.fillMaxWidth(),
                textConfig = TextConfig(
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            )
        }
    }
}



@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RequiredPermissionsAsk(
    state: State,
    onEventSend: (Event) -> Unit
) {
    val permissions: MutableList<String> = mutableListOf()

    permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
    permissions.add(Manifest.permission.BLUETOOTH_SCAN)
    permissions.add(Manifest.permission.BLUETOOTH_CONNECT)

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 && state.isBleCentralClientModeEnabled) {
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val permissionsState = rememberMultiplePermissionsState(permissions = permissions)

    when {
        permissionsState.allPermissionsGranted -> onEventSend(Event.CreateQrCode)
        else -> {
            onEventSend(Event.OnPermissionStateChanged(BleAvailability.UNKNOWN))
            LaunchedEffect(Unit) {
                permissionsState.launchMultiplePermissionRequest()
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@ThemeModePreviews
@Composable
private fun HomeScreenContentPreview() {
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
                state = State(
                    documentsUi = emptyList(),
                    firstName = "John",
                    lastName = "Doe",
                    isLoading = false,
                    imageBase64 = null,
                    claimsUi = listOf(
                        ClaimsUI("Claim 1", "Value 1"),
                        ClaimsUI("Claim 2", "Value 2"),
                        ClaimsUI("Claim 3", "Value 3"),
                        ClaimsUI("Claim 4", "Value 4"),
                        ClaimsUI("Claim 5", "Value 5"),
                        ClaimsUI("Claim 6", "Value 6"),
                        ClaimsUI("Claim 7", "Value 7"),
                        ClaimsUI("Claim 8", "Value 8"),
                        ClaimsUI("Claim 9", "Value 9"),
                        ClaimsUI("Claim 10", "Value 10"),
                    )
                ),
                effectFlow = Channel<Effect>().receiveAsFlow(),
                onNavigationRequested = {},
                paddingValues = PaddingValues(0.dp)
            )
        }
    }
}

