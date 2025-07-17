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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
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
import java.time.format.TextStyle


@Composable
fun RestoreBackupScreen(navController: NavController, viewModel: RestoreBackupViewModel) {
    val state = viewModel.viewState.collectAsStateWithLifecycle()
    val effectFlow = viewModel.effect

    val configButton = ButtonConfig(
        type = ButtonType.PRIMARY,
        onClick = {}
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
            viewModel = viewModel
        )
    }

}



@Composable
private fun NavigationSlider(
    paddingValues: PaddingValues,
    effectFlow: Flow<Effect>,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    state: State,
    viewModel: RestoreBackupViewModel
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
            viewModel = viewModel
        )
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
                Effect.Error -> {
                    Toast.makeText(context, "Incorrect word order. Please try again.", Toast.LENGTH_SHORT).show()
                }
                Effect.Success -> {
                    onNavigationRequested(Effect.Navigation.Pop)
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
    viewModel: RestoreBackupViewModel
){
//    WrapText(
//        text = stringResource(R.string.backup_screen_created_title),
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(paddingValues)
//            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
//        textConfig  = TextConfig(
//            style = MaterialTheme.typography.titleLarge
//                .merge(
//                    TextStyle(
//                        color = MaterialTheme.colorScheme.primary,
//                    )
//                )
//        ),
//    )
    VerificationGrid()

}
@Composable
private fun VerificationGrid() {
    Column (
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        VerificationItem(
            icon = Icons.Default.KeyboardArrowDown,
            description = stringResource(R.string.backup_import),
        )
        VerificationItem(
            icon = Icons.Default.Refresh,
            description = stringResource(R.string.backup_recovery_phrase),
        )
        VerificationItem(
            icon = Icons.Default.Info,
            description = stringResource(R.string.backup_you_wallet),
        )
    }
}

@Composable
private fun VerificationItem(
    icon: ImageVector,
    description: String,
) {
    Row (
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(40.dp)
                .padding(8.dp)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
