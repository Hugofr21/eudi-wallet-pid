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

package eu.europa.ec.backuplogic.ui.restoring

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import eu.europa.ec.uilogic.component.wrap.WrapText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import eu.europa.ec.backuplogic.ui.restoring.setupSlider.EnterPhraseContentPage
import eu.europa.ec.backuplogic.ui.restoring.setupSlider.FirstPage
import eu.europa.ec.backuplogic.ui.restoring.setupSlider.RestoreWalletContent
import eu.europa.ec.uilogic.component.utils.ICON_SIZE_40
import eu.europa.ec.uilogic.component.utils.SIZE_XX_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.WrapPageIndicator


@Composable
fun RestoreBackupScreen(navController: NavController, viewModel: RestoreBackupViewModel) {
    val state = viewModel.viewState.collectAsStateWithLifecycle()
    val effectFlow = viewModel.effect
    val pageSingleState = rememberPagerState { 3 }

    val configButton = ButtonConfig(
        type = ButtonType.PRIMARY,
        onClick = {
            if (pageSingleState.currentPage < 2) {
                viewModel.setEvent(Event.NextPage(pageSingleState.currentPage + 1))
            } else {
                viewModel.setEvent(Event.Restore)
            }
        }
    )

    ContentScreen(
        isLoading = false,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) },

        stickyBottom = { paddingValues ->
            SkipButton(
                paddingValues = paddingValues,
                config = configButton
            )
        })
    { paddingValues ->
        NavigationSlider(
            paddingValues = paddingValues,
            effectFlow = effectFlow,
            onNavigationRequested = { handleNavigationEffect(it, navController) },
            state = state.value,
            viewModel = viewModel,
            pageSingleState = pageSingleState
        )
    }

}



@Composable
private fun NavigationSlider(
    paddingValues: PaddingValues,
    effectFlow: Flow<Effect>,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    state: State,
    viewModel: RestoreBackupViewModel,
    pageSingleState: PagerState
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues)
    ) {
        MainContent(
            paddingValues = paddingValues,
            state =  state,
            viewModel = viewModel,
            pageSingleState = pageSingleState
        )
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
                is Effect.Error -> {
                    Toast.makeText(context, "Restore failed. Please check the file or recovery phrase.", Toast.LENGTH_SHORT).show()
                }
                is Effect.Success -> {
                    onNavigationRequested(Effect.Navigation.Pop)
                }
                is Effect.NavigateToPage -> {
                    pageSingleState.scrollToPage(effect.page)
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
private fun SkipButton(
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
        Text(text = stringResource(R.string.backup_screen_created_button))
    }
}


@Composable
private fun MainContent(
    paddingValues: PaddingValues,
    state: State,
    viewModel: RestoreBackupViewModel,
    pageSingleState: PagerState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues)
            .padding(horizontal = SPACING_MEDIUM.dp, vertical = SPACING_MEDIUM.dp)
    ) {
        WrapText(
            text = stringResource(R.string.settings_screen_option_restore_backup),
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
        VSpacer.Custom(SPACING_LARGE) // 24.dp

        RestoreGrid()

        VSpacer.Custom(SPACING_EXTRA_LARGE) // 32.dp

        HorizontalListOfPager(
            pagerState = pageSingleState,
            state = state,
            viewModel = viewModel
        )
        WrapPageIndicator(pageSingleState)
    }
}
@Composable
private fun HorizontalListOfPager(
    pagerState: PagerState,
    state: State,
    viewModel: RestoreBackupViewModel
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .defaultMinSize(minHeight = SIZE_XX_LARGE.dp)
    ) { page ->
        when (page) {
            0 -> FirstPage(
                onFileSelected = { uri ->
                    viewModel.setEvent(Event.FileSelected(uri))
                },
                modifier = Modifier.fillMaxWidth()
            )
            1 -> EnterPhraseContentPage(
                words = (state as? State.Default)?.words ?: emptyList(),
                onWordsChanged = { words ->
                    viewModel.setEvent(Event.WordsChanged(words))
                },
                onSubmit = { words ->
                    viewModel.setEvent(Event.SubmitWords(words))
                },
                modifier = Modifier.fillMaxWidth()
            )
            2 -> RestoreWalletContent(
                onRestore = {
                    viewModel.setEvent(Event.Restore)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RestoreGrid() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_SMALL.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
    ) {
        RestoreGridItem(
            icon = Icons.Default.Info,
            title = stringResource(R.string.consent_backup_first_page_title),
            description = stringResource(R.string.select_file_button)
        )
        RestoreGridItem(
            icon = Icons.Default.Info,
            title = stringResource(R.string.consent_backup_phase_page_title),
            description = stringResource(R.string.backup_recovery_phrase)
        )
        RestoreGridItem(
            icon = Icons.Default.Info,
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
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(0.dp)
            )
            .padding(SPACING_MEDIUM.dp),
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
