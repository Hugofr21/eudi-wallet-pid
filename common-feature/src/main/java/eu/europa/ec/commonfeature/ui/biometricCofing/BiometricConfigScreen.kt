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


import android.os.Build.VERSION_CODES.O
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import eu.europa.ec.uilogic.component.AppIconAndText
import eu.europa.ec.uilogic.component.AppIconAndTextDataUi
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.DEFAULT_ACTION_CARD_HEIGHT
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
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
            // padding geral horizontal e respeitar insets que chegam via paddingValues
            .padding(horizontal = 24.dp)
            .padding(paddingValues)
    ) {

        // Topo: icone e texto do app (mantive seu componente)
        AppIconAndText(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 28.dp),
            appIconAndTextData = AppIconAndTextDataUi(),
        )

        // Título principal — central, grande e em negrito
        WrapText(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            textConfig = TextConfig(
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            ),
            text = title
        )

        VSpacer.Large()

        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.CenterHorizontally)
        )

        VSpacer.Large()


        WrapText(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textConfig = TextConfig(
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            ),
            text = description
        )

        BiometricStepsList(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp)
        )

 
        state.biometricsError?.let { error ->
            VSpacer.Small()
            ErrorText(error)
        }
        
        VSpacer.Medium()


        LaunchedEffect(Unit) {
            effectFlow.onEach { effect ->
                when (effect) {
                    is Effect.Navigation -> onNavigationRequested(effect)
                }
            }.collect()
        }
    }
}

@Composable
private fun BiometricStepsList(
    modifier: Modifier
) {
    val steps = listOf(
        "We will check if biometric authentication is available on your device.",
        "If configured, you will be prompted to authenticate.",
        "If not configured, you'll be asked to enroll your fingerprint in system settings.",
        "Your biometric data is never stored or shared by the app."
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier.padding(horizontal = 8.dp)
    ) {
        Text(
            text = "What will happen next:",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )

        steps.forEachIndexed { index, step ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape)
                        .shadow(elevation = 2.dp, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                VSpacer.Small()

                WrapText(
                    modifier = Modifier.weight(1f)
                        .padding( start = 8.dp),
                    textConfig = TextConfig(
                        style = MaterialTheme.typography.bodyMedium,
                    ),
                    text = step
                )
            }
        }
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
            navController.navigate(navigationEffect.screenRoute) {
                navigationEffect.argument?.let { popUpToRoute ->
                    popUpTo(popUpToRoute) {
                        inclusive = navigationEffect.inclusive
                    }
                }
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
