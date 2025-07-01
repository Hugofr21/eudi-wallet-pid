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

package eu.europa.ec.eudi.consent_user.ui.verification

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.TopStepBar
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.HSpacer
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import eu.europa.ec.uilogic.component.wrap.WrapText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach


@Composable
fun VerificationScreen(navController: NavController, viewModel: VerificationViewModel) {
    val state = viewModel.viewState.collectAsStateWithLifecycle()


    val config = ButtonConfig(
        type = ButtonType.PRIMARY,
        onClick = { viewModel.setEvent(Event.GoNext) },
        enabled = state.value.isButtonEnabled
    )

    ContentScreen(
        isLoading = false,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { viewModel.setEvent(Event.GoBack) },
        stickyBottom = { paddingValues ->
            ContinueButton(paddingValues, config)
        }) { paddingValues ->

        NavigationSlider(
            paddingValues = paddingValues,
            effectFlow = viewModel.effect,
            onNavigationRequested = { navigationEffect ->
                handleNavigationEffect(navigationEffect, navController)
            })
    }
}

@Composable
private fun NavigationSlider(
    paddingValues: PaddingValues,
    effectFlow: Flow<Effect>,
    onNavigationRequested: (Effect.Navigation) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues)
    ) {
        TopStepBar(currentStep = 3)
        MainContent(
            paddingValues = paddingValues,
        )
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
            }
        }.collect()
    }
}

private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
) {
    when (navigationEffect) {
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute)
        }

        is Effect.Navigation.Pop -> {
            navController.popBackStack()
        }
    }
}


@Composable
private fun MainContent(
    paddingValues: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {

        TopStepBar(currentStep = 2)

        VSpacer.ExtraLarge()

        WrapIcon(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.CenterHorizontally),
            iconData = AppIcons.WalletSecured,
            customTint = MaterialTheme.colorScheme.primary
        )

        VSpacer.ExtraLarge()

        WrapText(
            text = stringResource(R.string.consent_verification_content_title),
            textConfig = TextConfig(
                style = MaterialTheme.typography.titleLarge,
            ),
        )
        VSpacer.Medium()

        WrapText(
            text = stringResource(R.string.consent_verification_content_description),
            textConfig = TextConfig(
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 6
            ),
        )

        VSpacer.Large()

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {

            VerificationCard(
                onClick = {  }
            )
            VSpacer.Medium()
        }
    }
}

@Composable
private fun VerificationCard(
    onClick: () -> Unit,
){
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ){
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SPACING_MEDIUM.dp),
        ) {
            WrapIcon(
                modifier = Modifier.size(24.dp),
                iconData = AppIcons.eID,
                customTint = MaterialTheme.colorScheme.primary
            )

            HSpacer.Small()
            Column {
                WrapText(
                    text = "Electronic Identification",
                    textConfig = TextConfig(
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                VSpacer.ExtraSmall()
                WrapText(
                    text = "Under applicable regulations and legislation, you may not create a profile solely to obtain identification. To verify your identity, please request credential validation directly from the PID provider.",
                    textConfig = TextConfig(
                        style = MaterialTheme.typography.bodyMedium,
                    )
                )
            }
        }
    }
}


@Composable
private fun ContinueButton(
    paddingValues: PaddingValues,
    config: ButtonConfig,
) {
    WrapStickyBottomContent(
        stickyBottomModifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues),
        stickyBottomConfig = StickyBottomConfig(
            type = StickyBottomType.OneButton(config = config), showDivider = false
        )
    ) {
        Text(text = stringResource(R.string.consent_screen_confirm_button))
    }
}



@ThemeModePreviews
@Composable
private fun ContentPreview() {
    PreviewTheme {

        val buttonConfig = ButtonConfig(
            type = ButtonType.PRIMARY,
            onClick = { },
            enabled = true
        )

        ContentScreen(
            stickyBottom = {
                ContinueButton(
                    paddingValues = it,
                    config = buttonConfig
                )
            }
        ) { paddingValues ->
            MainContent(
                paddingValues = paddingValues,
            )
        }
    }
}
