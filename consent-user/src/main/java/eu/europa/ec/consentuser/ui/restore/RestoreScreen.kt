package eu.europa.ec.consentuser.ui.restore

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.consentuser.ui.restore.Event.*
import eu.europa.ec.consentuser.ui.restore.setupSlider.FirstPage
import eu.europa.ec.consentuser.ui.restore.setupSlider.SecondEnterPhraseContentPage
import eu.europa.ec.consentuser.ui.restore.setupSlider.ThirdRestoreWalletContent
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.TopStepBar
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.DEFAULT_ACTION_CARD_HEIGHT
import eu.europa.ec.uilogic.component.utils.ICON_SIZE_40
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach


@Composable
fun RestoreScreen(navController: NavController, viewModel: RestoreViewModel) {
    val state = viewModel.viewState.collectAsStateWithLifecycle()

    val config = ButtonConfig(
        type = ButtonType.PRIMARY,
        onClick = { viewModel.setEvent(GoNext) },
        enabled = state.value.isButtonEnabled
    )

    ContentScreen(
        isLoading = false,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { viewModel.setEvent(GoBack) },
        stickyBottom = { paddingValues ->
            ContinueButton(paddingValues, config)
        }) { paddingValues ->

        NavigationSlider(
            paddingValues = paddingValues,
            effectFlow = viewModel.effect,
            onNavigationRequested = { navigationEffect ->
                handleNavigationEffect(navigationEffect, navController)
            },
            state.value,
            viewModel
        )
    }
}

@Composable
private fun NavigationSlider(
    paddingValues: PaddingValues,
    effectFlow: Flow<Effect>,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    state: State,
    viewModel: RestoreViewModel
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues)
    ) {
        TopStepBar(currentStep = 3)
        MainContent(
            paddingValues = paddingValues,
            state =  state,
            viewModel =  viewModel
        )
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
                is Effect.ShowError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                }
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
    state: State?,
    viewModel: RestoreViewModel?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {

        Icon(
            imageVector = Icons.Default.Backup,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICON_SIZE_40.dp)
        )

        VSpacer.Small()

        Text(
            text = stringResource(R.string.backup_you_wallet),
            style = MaterialTheme.typography.titleLarge
        )
        VSpacer.Small()
        Text(
            text = stringResource(R.string.consent_backup_third_page_restore_wallet_description),
            style = MaterialTheme.typography.bodyLarge,

        )

        RestoreGrid()
        VSpacer.Small()
        StepFormContent(state = state, viewModel = viewModel)
    }
}


@Composable
private fun RestoreGrid() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SPACING_SMALL.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
    ) {
        RestoreGridItem(
            icon = Icons.Default.Backup,
            title = stringResource(R.string.consent_backup_first_page_title),
            description = stringResource(R.string.select_file_button)
        )
        RestoreGridItem(
            icon = Icons.Default.TextIncrease,
            title = stringResource(R.string.consent_backup_phase_page_title),
            description = stringResource(R.string.backup_recovery_phrase)
        )
        RestoreGridItem(
            icon = Icons.Default.Restore,
            title = stringResource(R.string.consent_backup_third_page_restore_wallet_title),
            description = stringResource(R.string.consent_backup_third_page_restore_wallet_description)
        )
    }
}


@Composable
private fun RestoreGridItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.Transparent,
                shape = RoundedCornerShape(0.dp)
            )
            .padding( horizontal = SPACING_MEDIUM.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICON_SIZE_40.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color =  MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
private fun StepFormContent(
    state: State?,
    viewModel: RestoreViewModel?
) {
    when (state?.page) {
        RestorePage.First -> FirstPage(
            onFileSelected = { viewModel?.setEvent(FileSelected(it)) }
        )
        RestorePage.Second -> SecondEnterPhraseContentPage(
            words = state.mnemonicWords,
            onWordsChanged = { viewModel?.setEvent(WordsChanged(it)) },
            onSubmit = { viewModel?.setEvent(SubmitWords) }
        )
        RestorePage.Third -> ThirdRestoreWalletContent(
            options = state.options,
            selected = state.selectedOptions,
            onOptionToggled = { viewModel?.setEvent(OptionToggled(it)) },
            onRestore = { viewModel?.setEvent(Event.Restore) }
        )
        null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No page found",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
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
        Text(text = stringResource(R.string.consent_screen_restore_button))
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
                state = null,
                viewModel = null
            )
        }
    }
}
