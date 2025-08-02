package eu.europa.ec.verifierfeature.ui.fieldLabelsPrrofAge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.CheckboxWithTextData
import eu.europa.ec.uilogic.component.WrapCheckboxWithLabel
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
import eu.europa.ec.verifierfeature.model.FieldLabel
import eu.europa.ec.verifierfeature.model.fieldLabels
import kotlinx.coroutines.flow.Flow


@Composable
fun FieldLabelsProofAgeScreen(
    navController: NavController,
    viewModel: FieldLabelsProofAgeViewModel
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val effectFlow = viewModel.effect

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
                )
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
            onToggle = { id, checked -> viewModel.setEvent(Event.ToggleFieldLabel(id, checked)) }
        )
    }
}

@Composable
private fun NavigationSlider(
    paddingValues: PaddingValues,
    effectFlow: Flow<Effect>,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    state: State,
    onToggle: (String, Boolean) -> Unit
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
            text = stringResource(R.string.verifier_prove_title),
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
            iconData = AppIcons.Info,
            contentScale = ContentScale.Fit
        )

        VSpacer.Small()

        WrapText(
            text = stringResource(R.string.verifier_prove_content_description),
            textConfig = TextConfig(
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 6
            )
        )

        VSpacer.Small()

        ListAgeGrid(
            items = state.options,
            selectedKeys = state.selectedKeys,
            onCheckedChange = onToggle
        )
    }
}

@Composable
fun ListAgeGrid(
    items: List<FieldLabel>,
    selectedKeys: Set<String>,
    onCheckedChange: (String, Boolean) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) { item ->
            val checked = item.key in selectedKeys

            WrapCheckboxWithLabel(
                checkboxData = CheckboxWithTextData(
                    isChecked = checked,
                    enabled = true,
                    onCheckedChange = { isChecked ->
                        onCheckedChange(item.key, isChecked)
                    },
                    text = item.label,
                    textPadding = PaddingValues(8.dp)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            )
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
        Text(text = stringResource(R.string.backup_screen_confirm_button))
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
                    config = buttonConfig
                )
            }
        ) { paddingValues ->
            MainContent(
                paddingValues = paddingValues,
                state = State(
                    isLoading = false,
                    options = fieldLabels,
                ),
                onToggle = { _, _ -> }
            )
        }
    }
}