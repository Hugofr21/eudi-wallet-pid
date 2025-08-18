package eu.europa.ec.verifierfeature.ui.choiseVerifier

import android.content.Context
import androidx.lifecycle.viewModelScope
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.VerifierScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.verifierfeature.controller.verifier.VerifierEUDIController
import eu.europa.ec.verifierfeature.model.VerifierModule
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

data class VerifierItem(
    val id: String,
    val displayName: String,
    val url: String,
    val isSelected: Boolean
)

data class State(
    val isLoading: Boolean = false,
    val trustListVerifier: List<String> = emptyList(),
    val verifiers: List<VerifierItem> = emptyList()
) : ViewState

sealed class Event : ViewEvent {
    object GoBack : Event()
    data class ToggleVerifier(val verifierId: String, val isChecked: Boolean) : Event()
    object SubmitSelection : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        object Pop : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Profile.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()
    }

    data class ShowMessage(val message: String): Effect()
}

@KoinViewModel
class ChoiceVerifierViewModel(
    private val verifierEUDIController: VerifierEUDIController,
    private val context: Context,
    @InjectedParam private val documentId: DocumentId,
) : MviViewModel<Event, State, Effect>() {
    override fun setInitialState(): State = with(VerifierModule) {
        val trustList = trustListVerifier()
        val options = trustList.map { it.name }

        val items = trustList.map { v ->
            VerifierItem(
                id = v.id,
                displayName = v.name,
                url = v.url,
                isSelected = false
            )
        }

        return State(
            trustListVerifier = options,
            verifiers = items
        )
    }

    override  fun handleEvents(event: Event) {
        when (event) {
            is Event.ToggleVerifier -> {
                val updated = viewState.value.verifiers.map { item ->
                    when (item.id) {
                        event.verifierId -> item.copy(isSelected = event.isChecked)
                        else -> item.copy(isSelected = false)
                    }
                }
                setState { copy(verifiers = updated) }
            }
            Event.SubmitSelection -> {
                val selectedVerifier = viewState.value.verifiers.firstOrNull { it.isSelected }
                    ?: run {
                        setEffect { Effect.ShowMessage("Selecione um verificador") }
                        return
                    }

                when(selectedVerifier.id){
                    "age" -> {
                        setEffect {
                            Effect.Navigation.SwitchScreen(
                                screenRoute = generateComposableNavigationLink(
                                    screen = VerifierScreens.FieldsLabels.screenRoute,
                                    arguments = generateComposableArguments(
                                        mapOf("documentId" to documentId)
                                    )
                                ),
                                inclusive = false
                            )
                        }
                    }
                    "eudiw_other" -> {
                        setEffect {
                            Effect.Navigation.SwitchScreen(
                                screenRoute = generateComposableNavigationLink(
                                    screen = VerifierScreens.VerifierOther.screenRoute,
                                    arguments = generateComposableArguments(
                                        mapOf("documentId" to documentId)
                                    )
                                ),
                                inclusive = false
                            )
                        }
                    }
                    "test_rp" -> {
                        setState { copy(isLoading = true) }
                        viewModelScope.launch {
                            verifierEUDIController.launchTestVerifierAndGetResult(context)
                        }
                    }
                    "eudiw" ->{
                        setState { copy(isLoading = true) }
                        viewModelScope.launch {
                            verifierEUDIController.launchTestVerifierEudiAndGetResult(context)
                        }
                    }

                    "proof_age" ->{
                        setState { copy(isLoading = true) }
                        viewModelScope.launch {
                            verifierEUDIController.launchTestVerifierAgeProofAndGetResult(context)
                        }
                    }
                }

            }

            Event.GoBack -> setEffect { Effect.Navigation.Pop }
        }
    }
}
