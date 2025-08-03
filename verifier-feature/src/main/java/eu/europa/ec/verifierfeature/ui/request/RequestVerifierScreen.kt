package eu.europa.ec.verifierfeature.ui.request


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.ClickableArea
import eu.europa.ec.uilogic.component.ListItem
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.SwitchDataUi
import kotlinx.coroutines.flow.Flow

@Composable
fun RequestVerifierScreen(
    navController: NavController,
    viewModel: RequestVerifierViewModel
) {
    val state by viewModel.viewState.collectAsState()
    val effectFlow = viewModel.effect

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) }
    ) { paddingValues ->
        RequestVerifierContent(
            paddingValues = paddingValues,
            state = state,
            effectFlow = effectFlow,
            onToggle = { id, checked -> viewModel.setEvent(Event.ToggleFieldLabel(id, checked)) },
            onCancel = { viewModel.setEvent(Event.GoBack) },
            onShare = { viewModel.setEvent(Event.SubmitSelection) }
        ) { effect ->
            handleNavigationEffect(effect, navController)
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
private fun RequestVerifierContent(
    paddingValues: PaddingValues,
    state: State,
    effectFlow: Flow<Effect>,
    onToggle: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Text(
            style = MaterialTheme.typography.headlineSmall,
            text = stringResource(R.string.sharing_data_title)
        )
        VSpacer.Small()
        Text(
            style = MaterialTheme.typography.bodyMedium,
            text = stringResource(R.string.sharing_data_description)
        )
        VSpacer.Small()
        ListSharingData(
            items = state.availableFields,
            selectedIds = state.selectedFieldLabels,
            onToggle = onToggle,
            modifier = Modifier.weight(1f)
        )

        // Footer popup with actions
        Alert(
            onCancel = onCancel,
            onShare = onShare
        )
    }

    LaunchedEffect(effectFlow) {
        effectFlow.collect { effect ->
            if (effect is Effect.Navigation) onNavigationRequested(effect)
        }
    }
}

@Composable
fun ListSharingData(
    items: List<ListItemDataUi>,
    selectedIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        items(items, key = { it.itemId }) { item ->
            val isChecked = item.itemId in selectedIds
            ListItem(
                item = item.copy(
                    trailingContentData = ListItemTrailingContentDataUi.Switch(
                        switchData = SwitchDataUi(
                            isChecked = isChecked
                        )
                    )
                ),
                onItemClick = { onToggle(item.itemId, !isChecked) },
                clickableAreas = listOf(ClickableArea.TRAILING_CONTENT)
            )
            VSpacer.Small()
        }
    }
}

@Composable
fun Alert(
    onCancel: () -> Unit,
    onShare: () -> Unit
) {
    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(x = 0, y = -16)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    style = MaterialTheme.typography.titleMedium,
                    text = stringResource(R.string.sharing_data_footer_title)
                )
                VSpacer.Small()
                Text(
                    style = MaterialTheme.typography.bodySmall,
                    text = stringResource(R.string.sharing_data_footer_description)
                )
                VSpacer.Small()
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onCancel) {
                        Text(text = stringResource(R.string.generic_cancel_capitalized))
                    }
                    Button(onClick = onShare) {
                        Text(text = stringResource(R.string.generic_confirm_capitalized))
                    }
                }
            }
        }
    }
}