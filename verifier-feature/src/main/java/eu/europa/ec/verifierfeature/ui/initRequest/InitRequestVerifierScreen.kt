package eu.europa.ec.verifierfeature.ui.initRequest


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.LightSkyBlue
import eu.europa.ec.uilogic.component.wrap.SoftYellow
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import kotlinx.coroutines.flow.Flow


@Composable
fun InitRequestVerifierScreen(
    navController: NavController,
    viewModel: InitRequestVerifierViewModel
) {
    val state by viewModel.viewState.collectAsState()
    val effectFlow = viewModel.effect

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) },
        stickyBottom = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
            ) {
                AlertFooter()
                DoubleBtn(
                    onCancel = { viewModel.setEvent(Event.GoBack) },
                    onShare = { viewModel.setEvent(Event.SubmitSelection) }
                )
            }
        }
    ) { paddingValues ->
        RequestVerifierBody(
            paddingValues = paddingValues,
            state = state,
            effectFlow = effectFlow,
            onToggle = { id, checked -> viewModel.setEvent(Event.ToggleFieldLabel(id, checked)) },
            onNavigationRequested = { effect -> handleNavigationEffect(effect, navController) }
        )
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
private fun RequestVerifierBody(
    paddingValues: PaddingValues,
    state: State,
    effectFlow: Flow<Effect>,
    onToggle: (String, Boolean) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val base = LightSkyBlue
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Text(
            style = MaterialTheme.typography.headlineSmall.copy(
                color = colorScheme.primary
            ),
            text = stringResource(R.string.sharing_data_title),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        VSpacer.Small()

        Text(
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colorScheme.onBackground
            ),
            text = stringResource(R.string.sharing_data_description),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        VSpacer.ExtraLarge()

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 0.dp)
        ) {
            items(state.availableFields, key = { it.itemId }) { item ->
                val isChecked = item.itemId in state.selectedFieldLabels || item.isMandatory

                Card(
                    onClick = {
                        if (!item.isMandatory) onToggle(item.itemId, !isChecked)
                    },
                    shape = RoundedCornerShape(0.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = base),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .align(Alignment.TopStart)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color(0xFF0D0D0D)
                                    ),
                                    color = Color(0xFF0D0D0D),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            item.subtitle?.let {
                                VSpacer.ExtraSmall()
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF0D0D0D)
                                    ),
                                    color = Color(0xFF0D0D0D).copy(alpha = 0.7f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (item.isMandatory) {
                                VSpacer.ExtraSmall()
                                Text(
                                    text = "(Mandatory)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF0D0D0D).copy(alpha = 0.7f)
                                )
                            }
                        }

                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = {
                                if (!item.isMandatory) onToggle(item.itemId, it)
                            },
                            enabled = !item.isMandatory,
                            colors = CheckboxDefaults.colors(
                                checkedColor = colorScheme.primary,
                                uncheckedColor = Color(0xFF0D0D0D).copy(alpha = 0.6f),
                                checkmarkColor = colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(effectFlow) {
        effectFlow.collect { effect ->
            if (effect is Effect.Navigation) onNavigationRequested(effect)
        }
    }
}

@Composable
private fun AlertFooter() {
    val base = SoftYellow
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(0.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = base)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF0D0D0D),
                text = stringResource(R.string.sharing_data_footer_title)
            )
            VSpacer.Small()
            Text(
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF0D0D0D)
                ),
                color = Color(0xFF0D0D0D).copy(alpha = 0.85f),
                text = stringResource(R.string.sharing_data_footer_description)
            )
        }
    }
}


@Composable
private fun DoubleBtn(
    onCancel: () -> Unit,
    onShare: () -> Unit,
) {
    val buttons = StickyBottomType.TwoButtons(
        primaryButtonConfig = ButtonConfig(
            type = ButtonType.SECONDARY,
            onClick = { onCancel() } ),
        secondaryButtonConfig = ButtonConfig(
            type = ButtonType.PRIMARY,
            onClick = {onShare() })
    )
    WrapStickyBottomContent(
        stickyBottomModifier = Modifier
            .fillMaxWidth()
        ,
        stickyBottomConfig = StickyBottomConfig(type = buttons, showDivider = false)
    ) {
        when (it?.type) {
            ButtonType.PRIMARY -> Text(text = stringResource(id = R.string.generic_confirm_capitalized))
            ButtonType.SECONDARY -> Text(text = stringResource(id = R.string.generic_cancel_capitalized))
            else -> {}
        }
    }
}