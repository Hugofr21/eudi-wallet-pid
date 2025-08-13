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

package eu.europa.ec.backuplogic.ui.listWordsBackup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import eu.europa.ec.uilogic.component.wrap.WrapText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

val LightSkyBlue   = Color(0xFFCAE6FD)
val OceanBlue      = Color(0xFF2A5ED9)
val DeepBlue       = Color(0xFF0048D2)
val SoftYellow     = Color(0xFFFFF1BA)
val CoralRed       = Color(0xFFFF6E70)

@Composable
fun ListWordsBackupScreen(navController: NavController, viewModel: ListWordsBackupViewModel) {
    val state = viewModel.viewState.collectAsStateWithLifecycle()
    val effectFlow = viewModel.effect

    ContentScreen(
        isLoading = false,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) },

        stickyBottom = { paddingValues ->
            ContinueButton(
                paddingValues = paddingValues,
                config = ButtonConfig(
                    type = ButtonType.PRIMARY,
                    onClick = { viewModel.setEvent(Event.GoNext) },
                    enabled = !state.value.isLoading
                )
            )
        })
      { paddingValues ->
        NavigationSlider(
            paddingValues = paddingValues,
            effectFlow = viewModel.effect,
            onNavigationRequested = { handleNavigationEffect(it, navController) },
            state = state.value
        )
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
        Text(text = stringResource(R.string.backup_screen_skip_button))
    }
}


@Composable
private fun NavigationSlider(
 paddingValues: PaddingValues,
 effectFlow: Flow<Effect>,
 onNavigationRequested: (Effect.Navigation) -> Unit,
 state: State,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues)
    ) {
        MainContent(
            paddingValues = paddingValues,
            state =  state,
        )
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
                Effect.Navigation.Pop -> TODO()
                is Effect.Navigation.SwitchScreen -> TODO()
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
fun MainContent(paddingValues: PaddingValues, state: State) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {


        WrapText(
            text = stringResource(R.string.recovery_backup_content_title),
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
            ),
        )
        VSpacer.ExtraSmall()
        WrapText(
            text = stringResource(R.string.recovery_backup_content_description),
            textConfig = TextConfig(
                style = MaterialTheme.typography.titleLarge.merge(
                    TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        letterSpacing = (-0.02).sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            ),
        )
        VSpacer.ExtraLarge()


        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(state.listWords) { index, word ->
                WordItem(index + 1, word)
            }
        }
    }
}


@Composable
fun WordItem(index: Int, word: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = LightSkyBlue,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodyMedium.merge(
                TextStyle(
                    color = Color(0xFF0D0D0D),
                    fontWeight = FontWeight.Bold
                )
            ),
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = word,
            style = MaterialTheme.typography.bodyMedium.merge(
                TextStyle(
                    color = Color(0xFF0D0D0D)
                )
            )
        )
    }
}


@ThemeModePreviews
@Composable
private fun ContentPreview() {
    PreviewTheme {
        ContentScreen(
            isLoading = false,
            navigatableAction = ScreenNavigateAction.NONE,
            onBack = { },
            stickyBottom = { paddingValues ->
            }) { paddingValues ->
            NavigationSlider(
                paddingValues = paddingValues,
                effectFlow = channelFlow {
                },
                onNavigationRequested = {

                },
                state = State(
                    listWords = listOf(
                        "apple", "banana", "cherry", "date",
                        "elderberry", "fig", "grape", "honey"
                    )
                )
            )
        }


    }
}
