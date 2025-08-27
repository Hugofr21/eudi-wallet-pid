package eu.europa.ec.verifierfeature.ui.choiseVerifier

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.DEFAULT_ACTION_CARD_HEIGHT
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import eu.europa.ec.uilogic.component.wrap.WrapText
import eu.europa.ec.uilogic.extension.openWifiSettings
import eu.europa.ec.verifierfeature.ui.choiseVerifier.compoment.TrustListGrid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach


@Composable
fun ChoiceVerifierScreen(
    navController: NavController,
    viewModel: ChoiceVerifierViewModel
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val effectFlow = viewModel.effect
    val context = LocalContext.current

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) },
        stickyBottom = { paddingValues ->
            ContinueButton(
                paddingValues = paddingValues,
                config = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    onClick = { viewModel.setEvent(Event.SubmitSelection) },
                    enabled = !state.isLoading
                ),
                isInternetAvailable = state.isInternetAvailable,
                onOpenWifiSettings = {
                    context.openWifiSettings()
                }
            )
        }
    ) { paddingValues ->
        NavigationSlider(
            paddingValues = paddingValues,
            effectFlow = effectFlow,
            onNavigationRequested = { navigationEffect ->
                handleNavigationEffect(navigationEffect, navController)
            },
            state = state,
            onToggle = { id, checked -> viewModel.setEvent(Event.ToggleVerifier(id, checked)) }
        )
    }
}

@Composable
private fun NavigationSlider(
    paddingValues: PaddingValues,
    effectFlow: Flow<Effect>,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    state: State,
    onToggle: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues)
    ) {
        MainContent(
            paddingValues = paddingValues,
            state = state,
            onToggle = onToggle
        )
    }

    LaunchedEffect(effectFlow) {
        effectFlow.collect { effect ->
            if (effect is Effect.Navigation) onNavigationRequested(effect)
        }
    }
}

private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
) {
    when (navigationEffect) {
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(navigationEffect.popUpToScreenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
        }

        is Effect.Navigation.Pop -> {
            navController.popBackStack()
        }
    }
}

@Composable
private fun MainContent(
    paddingValues: PaddingValues,
    state: State,
    onToggle: (String, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        WrapText(
            text = stringResource(R.string.verifier_content_title),
            textConfig = TextConfig(
                style = MaterialTheme.typography.titleLarge.merge(
                    TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 32.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.02).sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            )
        )
        VSpacer.Small()

        WrapImage(
            modifier = Modifier
                .wrapContentSize()
                .defaultMinSize(minHeight = DEFAULT_ACTION_CARD_HEIGHT.dp)
                .align(Alignment.CenterHorizontally),
            iconData = AppIcons.Verified,
            contentScale = ContentScale.Fit
        )

        VSpacer.Small()

        WrapText(
            text = stringResource(R.string.verifier_content_description),
            textConfig = TextConfig(
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 6
            )
        )

        VSpacer.Small()

        TrustListGrid(
            items = state.verifiers,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun ContinueButton(
    paddingValues: PaddingValues,
    config: ButtonConfig,
    isInternetAvailable: Boolean,
    onOpenWifiSettings: () -> Unit
) {
    WrapStickyBottomContent(
        stickyBottomModifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues),
        stickyBottomConfig = StickyBottomConfig(
            type = StickyBottomType.OneButton(
                config = config.copy(
                    enabled = config.enabled && isInternetAvailable
                )
            ),
            showDivider = false
        )
    ) {
        if (!isInternetAvailable) {
            Text(
                text = stringResource(R.string.no_internet_open_wifi_settings),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenWifiSettings() }
                    .padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        } else {
            Text(text = stringResource(R.string.backup_screen_skip_button))
        }
    }
}

@ThemeModePreviews
@Composable
private fun ContentPreview() {
    PreviewTheme {
        val buttonConfig = ButtonConfig(
            type = ButtonType.PRIMARY,
            onClick = {},
            enabled = true
        )

        ContentScreen(
            stickyBottom = {
                ContinueButton(
                    paddingValues = it,
                    config = buttonConfig,
                    isInternetAvailable =  false,
                    onOpenWifiSettings = {}
                )
            }
        ) { paddingValues ->
            MainContent(
                paddingValues = paddingValues,
                state = State(
                    isLoading = false,
                    verifiers = listOf(
                        VerifierItem("1", "Option 1", "", true),
                        VerifierItem("2", "Option 2", "", false)
                    )
                ),
                onToggle = { _, _ -> }
            )
        }
    }
}