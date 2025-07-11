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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.backuplogic.ui.backup.Effect
import eu.europa.ec.backuplogic.ui.backup.Event
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach


@Composable
fun ListWordsBackupScreen(navController: NavController, viewModel: ListWordsBackupViewModel) {
    val state = viewModel.viewState.collectAsStateWithLifecycle()
    val effectFlow = viewModel.effect

    ContentScreen(
        isLoading = false,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { viewModel.setEvent(Event.GoBack) },

        stickyBottom = { paddingValues ->
        }) { paddingValues ->
        NavigationSlider(
            paddingValues = paddingValues,
            effectFlow = effectFlow,
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
 State: State = State(),
) {
    Column(
        modifier = Modifier
         .fillMaxWidth()
         .padding(paddingValues)
    ) {
        MainContent(
            paddingValues = paddingValues,
            state =  State,
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
    navigationEffect: eu.europa.ec.backuplogic.ui.backup.Effect.Navigation,
    navController: NavController,
) {
    when (navigationEffect) {
        is eu.europa.ec.backuplogic.ui.backup.Effect.Navigation.SwitchScreen -> {
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

        val list = state.listWords.listIterator()

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



    }
}


fun ListWords() {

}