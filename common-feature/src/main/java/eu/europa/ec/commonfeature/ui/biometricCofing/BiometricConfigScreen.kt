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

package eu.europa.ec.commonfeature.ui.biometricCofing


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.DEFAULT_ACTION_CARD_HEIGHT
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import eu.europa.ec.uilogic.component.wrap.WrapText
import eu.europa.ec.uilogic.navigation.CommonScreens
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow



@Composable
fun BiometricConfigScreen(navController: NavController, viewModel: BiometricSetupViewModel) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val title = stringResource(id = R.string.biometric_setup_title)
    val appName = context.getString(R.string.landing_screen_title)
    val descriptionWithAppName = stringResource(
        id = R.string.biometric_setup_description,
        appName
    )


    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { viewModel.setEvent(Event.SkipButtonPressed) },
        stickyBottom = { paddingValues ->
            ActionButtons(viewModel, paddingValues, state.isBiometricsAvailable)
        }) { paddingValues ->
        Content(
            title = title,
            description = descriptionWithAppName,
            state = state,
            effectFlow = viewModel.effect,
            onNavigationRequested = { navigationEffect ->
                handleNavigationEffect(navController, navigationEffect)
            },
            paddingValues = paddingValues
        )
    }

    ObserveScreenResume(viewModel)
}

@Composable
private fun ActionButtons(
    viewModel: BiometricSetupViewModel?,
    paddingValues: PaddingValues,
    biometricsAvailable: Boolean
) {
    val context = LocalContext.current
    val buttons = StickyBottomType.TwoButtons(
        primaryButtonConfig = ButtonConfig(
            type = ButtonType.SECONDARY,
            onClick = { viewModel?.setEvent(Event.SkipButtonPressed) }),
        secondaryButtonConfig = ButtonConfig(
            type = ButtonType.PRIMARY,
            enabled = biometricsAvailable,
            onClick = { viewModel?.setEvent(Event.NextButtonPressed(context)) })
    )
    WrapStickyBottomContent(
        stickyBottomModifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues),
        stickyBottomConfig = StickyBottomConfig(type = buttons, showDivider = false)
    ) {
        when (it?.type) {
            ButtonType.PRIMARY -> Text(text = stringResource(id = R.string.biometric_setup_enable))
            ButtonType.SECONDARY -> Text(text = stringResource(id = R.string.biometric_setup_skip))
            else -> {}
        }
    }
}

@Composable
private fun ObserveScreenResume(viewModel: BiometricSetupViewModel) {
    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(Event.ScreenResumed)
    }
}

@Composable
private fun Content(
    title: String,
    description: String,
    state: State,
    effectFlow: Flow<Effect>,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {

        WrapText(
            modifier = Modifier.fillMaxWidth(),
            textConfig = TextConfig(
                style = MaterialTheme.typography.titleLarge
                    .copy(fontWeight = FontWeight.Bold),
                maxLines = 4,
                textAlign = TextAlign.Center
            ),
            text = title
        )

        VSpacer.Large()


        WrapImage(
            modifier = Modifier
                .wrapContentSize()
                .defaultMinSize(minHeight = DEFAULT_ACTION_CARD_HEIGHT.dp)
                .align(Alignment.CenterHorizontally)
                .size(80.dp),
            iconData = AppIcons.Fingerprint,
            contentScale = ContentScale.Fit
        )

        VSpacer.Large()

        WrapText(
            textConfig = TextConfig(style = MaterialTheme.typography.bodyMedium, maxLines = 6),
            text = description
        )

        state.biometricsError?.let { error ->
            ErrorText(error)
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
private fun ErrorText(error: String) {
    VSpacer.Medium()
    WrapText(
        textConfig = TextConfig(
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 5
        ), text = error
    )
}

private fun handleNavigationEffect(
    navController: NavController,
    navigationEffect: Effect.Navigation,
) {
    when (navigationEffect) {
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screen) {
                popUpTo(CommonScreens.BiometricCreatedConfig.screenRoute) { inclusive = true }
            }
        }
    }
}

@ThemeModePreviews
@Composable
private fun BiometricSetupScreenPreview() {
    PreviewTheme {
        Content(
            title = "Biometrically unlock the app",
            description = "Use your fingerprint or facial recognition to log in to the app.",
            state = State(isBiometricsAvailable = true),
            effectFlow = Channel<Effect>().receiveAsFlow(),
            onNavigationRequested = {},
            paddingValues = PaddingValues(16.dp)
        )
    }
}

@ThemeModePreviews
@Composable
private fun BiometricSetupScreenErrorPreview() {
    PreviewTheme {
        Content(
            title = "Biometrically unlock the app",
            description = "Use your fingerprint or facial recognition to log in to the app.",
            state = State(
                isBiometricsAvailable = false,
                biometricsError = "Biometric authentication is not available on this device."
            ),
            effectFlow = Channel<Effect>().receiveAsFlow(),
            onNavigationRequested = {},
            paddingValues = PaddingValues(16.dp)
        )
    }
}

@ThemeModePreviews
@Composable
private fun actionButtonsPreview(){
    PreviewTheme {
        ActionButtons(
            viewModel = null,
            paddingValues = PaddingValues(16.dp),
            biometricsAvailable = true
        )
    }
}
