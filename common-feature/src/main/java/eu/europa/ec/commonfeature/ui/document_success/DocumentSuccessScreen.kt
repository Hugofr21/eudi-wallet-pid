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

package eu.europa.ec.commonfeature.ui.document_success


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIconAndTextDataUi
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.RelyingPartyDataUi
import eu.europa.ec.uilogic.component.content.ContentHeader
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.ExpandableListItemUi
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapExpandableListItem
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import eu.europa.ec.uilogic.extension.cacheDeepLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach

@Composable
fun DocumentSuccessScreen(
    navController: NavController,
    viewModel: DocumentSuccessViewModel,
) {
    val context = LocalContext.current
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    ContentScreen(
        isLoading = false,
        stickyBottom = { paddingValues ->
            WrapStickyBottomContent(
                stickyBottomModifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues),
                stickyBottomConfig = StickyBottomConfig(
                    type = StickyBottomType.OneButton(
                        config = ButtonConfig(
                            type = ButtonType.SECONDARY,
                            enabled = !state.isLoading,
                            onClick = { viewModel.setEvent(Event.StickyButtonPressed) }
                        )
                    )
                )
            ) {
                Text(text = stringResource(R.string.document_success_sticky_button_text))
            }
        },
        navigatableAction = ScreenNavigateAction.NONE,
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onEventSend = { event -> viewModel.setEvent(event) },
            onNavigationRequested = { navigationEffect ->
                when (navigationEffect) {
                    is Effect.Navigation.SwitchScreen -> {
                        navController.navigate(navigationEffect.screenRoute) {
                            navigationEffect.popUpRoute?.let { popUpToRoute ->
                                popUpTo(popUpToRoute) {
                                    inclusive = true
                                }
                            }
                        }
                    }

                    is Effect.Navigation.PopBackStackUpTo -> {
                        navController.popBackStack(
                            route = navigationEffect.screenRoute,
                            inclusive = navigationEffect.inclusive
                        )
                    }

                    is Effect.Navigation.DeepLink -> {
                        context.cacheDeepLink(navigationEffect.link)
                        navigationEffect.routeToPop?.let {
                            navController.popBackStack(
                                route = it,
                                inclusive = false
                            )
                        } ?: navController.popBackStack()
                    }

                    is Effect.Navigation.Pop -> navController.popBackStack()
                }
            },
            paddingValues = paddingValues
        )
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.DoWork)
    }
}

@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(paddingValues)
    ) {
        ContentHeader(
            modifier = Modifier.fillMaxWidth(),
            config = state.headerConfig,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = SPACING_SMALL.dp),
            verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
        ) {
            state.items.forEach { successItem ->
                WrapExpandableListItem(
                    modifier = Modifier.fillMaxWidth(),
                    header = successItem.header,
                    data = successItem.nestedItems,
                    onItemClick = null,
                    onExpandedChange = { expandedItem ->
                        onEventSend(Event.ExpandOrCollapseSuccessDocumentItem(itemId = expandedItem.itemId))
                    },
                    isExpanded = successItem.isExpanded,
                    throttleClicks = false,
                    hideSensitiveContent = false,
                    collapsedMainContentVerticalPadding = SPACING_MEDIUM.dp,
                    expandedMainContentVerticalPadding = SPACING_MEDIUM.dp,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                    )
                )
            }
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


@ThemeModePreviews
@Composable
private fun PreviewDocumentSuccessContent() {
    PreviewTheme {
        // 1) Fake header with both mainText and description
        val fakeHeader = ContentHeaderConfig(
            appIconAndTextData = AppIconAndTextDataUi(
                appIcon = AppIcons.LogoPlain,
                appText = AppIcons.LogoText,
            ),
            mainText = "Document Submitted",
            description = "Your ID document was successfully uploaded and verified.",
            mainTextConfig = TextConfig(
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 1
            ),
            descriptionTextConfig = TextConfig(
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2
            ),
            relyingPartyData = RelyingPartyDataUi(
                name = "Example Service",
                isVerified = true,
            )
        )

        fun makeListItem(
            id: String,
            title: String,
            overline: String? = null,
            supporting: String? = null,
            leading: ListItemLeadingContentDataUi? = null,
            trailing: ListItemTrailingContentDataUi? = null
        ) = ListItemDataUi(
            itemId = id,
            mainContentData = ListItemMainContentDataUi.Text(text = title),
            overlineText = overline,
            supportingText = supporting,
            leadingContentData = leading,
            trailingContentData = trailing
        )


        val fakeItems = listOf(
            ExpandableListItemUi.NestedListItem(
                header = makeListItem("age_check", "Age Verification"),
                nestedItems = listOf(
                    ExpandableListItemUi.SingleListItem(
                        header = makeListItem("age_detail", "User is over 18")
                    )
                ),
                isExpanded = true
            ),

            ExpandableListItemUi.NestedListItem(
                header = makeListItem("doc_status", "Document Status"),
                nestedItems = emptyList(),
                isExpanded = false
            )
        )

        val fakeState = State(
            isLoading = false,
            headerConfig = fakeHeader,
            items = fakeItems
        )

        Content(
            state = fakeState,
            effectFlow = emptyFlow(),
            onEventSend = {},
            onNavigationRequested = {},
            paddingValues = PaddingValues(16.dp)
        )
    }
}
