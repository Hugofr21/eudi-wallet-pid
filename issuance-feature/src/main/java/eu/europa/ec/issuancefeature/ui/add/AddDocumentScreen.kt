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

package eu.europa.ec.issuancefeature.ui.add

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.corelogic.controller.IssuanceMethod
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.issuancefeature.ui.add.model.AddDocumentUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.content.BroadcastAction
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ContentTitle
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.SIZE_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapListItem
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.extension.getPendingDeepLink
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.handleDeepLinkAction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
fun AddDocumentScreen(
    navController: NavController,
    viewModel: AddDocumentViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = state.navigatableAction,
        onBack = state.onBackAction,
        contentErrorConfig = state.error,
        broadcastAction = BroadcastAction(
            intentFilters = listOf(
                CoreActions.VCI_RESUME_ACTION,
                CoreActions.VCI_DYNAMIC_PRESENTATION
            ),
            callback = {
                when (it?.action) {
                    CoreActions.VCI_RESUME_ACTION -> it.extras?.getString("uri")?.let { link ->
                        viewModel.setEvent(Event.OnResumeIssuance(link))
                    }

                    CoreActions.VCI_DYNAMIC_PRESENTATION -> it.extras?.getString("uri")
                        ?.let { link ->
                            viewModel.setEvent(Event.OnDynamicPresentation(link))
                        }
                }
            }
        )
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onEventSend = { viewModel.setEvent(it) },
            onNavigationRequested = { navigationEffect ->
                when (navigationEffect) {
                    is Effect.Navigation.Pop -> navController.popBackStack()
                    is Effect.Navigation.SwitchScreen -> {
                        navController.navigate(navigationEffect.screenRoute) {
                            popUpTo(IssuanceScreens.AddDocument.screenRoute) {
                                inclusive = navigationEffect.inclusive
                            }
                        }
                    }

                    is Effect.Navigation.Finish -> context.finish()
                    is Effect.Navigation.OpenDeepLinkAction -> handleDeepLinkAction(
                        navController,
                        navigationEffect.deepLinkUri,
                        navigationEffect.arguments
                    )
                }
            },
            paddingValues = paddingValues,
            context = context,
        )
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_PAUSE
    ) {
        viewModel.setEvent(Event.OnPause)
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(Event.Init(context.getPendingDeepLink()))
    }
}

@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues,
    context: Context
) {
    val layoutDirection = LocalLayoutDirection.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        MainContent(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(
                    paddingValues = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = 0.dp,
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        end = paddingValues.calculateEndPadding(layoutDirection)
                    )
                ),
            state = state,
            onEventSend = onEventSend,
            paddingValues = paddingValues,
            context = context,
        )

        if (state.showFooterScanner) {
            VSpacer.ExtraSmall()
            Footer(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = SIZE_LARGE.dp, topEnd = SIZE_LARGE.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(
                        top = SPACING_MEDIUM.dp,
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        end = paddingValues.calculateEndPadding(layoutDirection),
                        bottom = paddingValues.calculateBottomPadding()
                    ),
                onEventSend = onEventSend,
            )
        }
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
            }
        }.collect()
    }
}
@Composable
private fun MainContent(
    modifier: Modifier = Modifier,
    state: State,
    onEventSend: (Event) -> Unit,
    paddingValues: PaddingValues,
    context: Context,
) {
    Column(modifier = modifier) {
        ContentTitle(
            modifier = Modifier.fillMaxWidth(),
            title = state.title,
            subtitle = state.subtitle
        )


        val groupedOptions = state.options


        groupedOptions.forEach { (issuerId, documents) ->
            Text(
                text = issuerId,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = paddingValues.calculateBottomPadding(),
                    start = 16.dp,
                    end = 16.dp
                )
            ) {
                items(
                    items = documents,
                    key = { addDoc -> "${issuerId}_${addDoc.itemData.itemId}" }
                ) { addDoc ->
                    WrapListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f),
                        item = addDoc.itemData,
                        mainContentVerticalPadding = SPACING_LARGE.dp,
                        mainContentTextStyle = MaterialTheme.typography.titleMedium,
                        onItemClick = { optionListItemDataUi ->
                            onEventSend(
                                Event.IssueDocument(
                                    issuanceMethod = IssuanceMethod.OPENID4VCI,
                                    configId = optionListItemDataUi.itemId,
                                    context = context,
                                    issuerId = issuerId
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun Footer(
    modifier: Modifier = Modifier,
    onEventSend: (Event) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.issuance_add_document_scan_qr_footer_text),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            textAlign = TextAlign.Center
        )

        WrapImage(
            iconData = AppIcons.AddDocumentFromQr
        )

        WrapButton(
            modifier = Modifier.fillMaxWidth(),
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                buttonColors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                onClick = {
                    onEventSend(Event.GoToQrScan)
                }
            )
        ) {
            Text(text = stringResource(R.string.issuance_add_document_scan_qr_footer_button_text))
        }
    }
}


@ThemeModePreviews
@Composable
private fun IssuanceAddDocumentScreenPreview() {
    PreviewTheme {
        Content(
            state = State(
                showFooterScanner = true,
                navigatableAction = ScreenNavigateAction.NONE,
                title = "Add Document",
                subtitle = "Select a document to add",
                options = listOf(
                    "Issuer 1" to listOf(
                        AddDocumentUi(
                            itemData = ListItemDataUi(
                                itemId = "configId1",
                                mainContentData = ListItemMainContentDataUi.Text(text = "National ID"),
                                trailingContentData = ListItemTrailingContentDataUi.Icon(iconData = AppIcons.Add)
                            ),
                            credentialIssuerId = "Issuer 1"
                        ),
                        AddDocumentUi(
                            itemData = ListItemDataUi(
                                itemId = "configId2",
                                mainContentData = ListItemMainContentDataUi.Text(text = "Driving Licence"),
                                trailingContentData = ListItemTrailingContentDataUi.Icon(iconData = AppIcons.Add)
                            ),
                            credentialIssuerId = "Issuer 1"
                        )
                    ),
                    "Issuer 2" to listOf(
                        AddDocumentUi(
                            itemData = ListItemDataUi(
                                itemId = "configId3",
                                mainContentData = ListItemMainContentDataUi.Text(text = "Residence Permit"),
                                trailingContentData = ListItemTrailingContentDataUi.Icon(iconData = AppIcons.Add)
                            ),
                            credentialIssuerId = "Issuer 2"
                        )
                    )
                ),
                onBackAction = {},
                issuanceConfig = null,
                isLoading = false,
                error = null,
                isInitialised = true,
                notifyOnAuthenticationFailure = false,
                noOptions = false
            ),
            effectFlow = emptyFlow(),
            onEventSend = {},
            onNavigationRequested = {},
            paddingValues = PaddingValues(all = SPACING_LARGE.dp),
            context = LocalContext.current
        )
    }
}

@ThemeModePreviews
@Composable
private fun DashboardAddDocumentScreenPreview() {
    PreviewTheme {
        Content(
            state = State(
                showFooterScanner = false,
                navigatableAction = ScreenNavigateAction.BACKABLE,
                title = "Add Document",
                subtitle = "Select a document to add",
                options = listOf(
                    "Issuer 3" to listOf(
                        AddDocumentUi(
                            itemData = ListItemDataUi(
                                itemId = "configId1",
                                mainContentData = ListItemMainContentDataUi.Text(text = "National ID"),
                                trailingContentData = ListItemTrailingContentDataUi.Icon(iconData = AppIcons.Add)
                            ),
                            credentialIssuerId = "Issuer 3"
                        )
                    ),
                    "Issuer 4" to listOf(
                        AddDocumentUi(
                            itemData = ListItemDataUi(
                                itemId = "configId2",
                                mainContentData = ListItemMainContentDataUi.Text(text = "Driving Licence"),
                                trailingContentData = ListItemTrailingContentDataUi.Icon(iconData = AppIcons.Add)
                            ),
                            credentialIssuerId = "Issuer 4"
                        )
                    )
                ),
                onBackAction = {},
                issuanceConfig = null,
                isLoading = false,
                error = null,
                isInitialised = true,
                notifyOnAuthenticationFailure = false,
                noOptions = false
            ),
            effectFlow = emptyFlow(),
            onEventSend = {},
            onNavigationRequested = {},
            paddingValues = PaddingValues(all = SPACING_LARGE.dp),
            context = LocalContext.current
        )
    }
}
