package eu.europa.ec.proximityfeature.ui.generateQrcode

import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.commonfeature.config.IssuanceUiConfig
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.corelogic.di.getOrCreatePresentationScope
import eu.europa.ec.proximityfeature.interactor.GenerateQrInteractor
import eu.europa.ec.proximityfeature.interactor.ProximityPidQRWritePartialState
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam


data class State(
    val isLoading: Boolean = false,
    val qrCodeData: String? = null,
    val error: ContentErrorConfig? = null,
) : ViewState

sealed class Event : ViewEvent {
    object GoBack : Event()
    object Share : Event()

    object Init : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        object Pop : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Profile.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()

        object Share : Navigation()
    }
}

@KoinViewModel
class ProximityGenerateQrViewModel(
    private val uiSerializer: UiSerializer,
    private val interactor: GenerateQrInteractor,
    @InjectedParam private val requestUriConfigRaw: String,
) : MviViewModel<Event, State, Effect>() {

    private var threadJob: Job? = null

    override fun setInitialState(): State {


        return State(

        )
    }

    override fun handleEvents(event: Event) {
        when (event) {

            Event.GoBack -> setEffect { Effect.Navigation.Pop }
            Event.Share -> {

            }

            Event.Init -> {
                initializeConfig()
                generateQrCode()
            }
        }
    }


    private fun initializeConfig() {
        val requestUriConfig = uiSerializer.fromBase64(
            requestUriConfigRaw,
            RequestUriConfig::class.java,
            RequestUriConfig.Parser
        ) ?: throw RuntimeException("RequestUriConfig:: is Missing or invalid")
        println("initializeConfig $requestUriConfig")
        interactor.setConfig(requestUriConfig)
    }



    private fun generateQrCode() {
        threadJob?.cancel()

        setState {
            copy(isLoading = true, error = null, qrCodeData = null)
        }

        threadJob = viewModelScope.launch {
            interactor.startQrEngagement().collect { response ->
                when (response) {
                    is ProximityPidQRWritePartialState.Error -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { setEvent(Event.GoBack) },
                                    errorSubTitle = response.error,
                                    onCancel = { setEvent(Event.GoBack) }
                                )
                            )
                        }
                    }

                    is ProximityPidQRWritePartialState.QrReady -> {
                        println("[Pid ViewModel] QrReady response qr code: ${response.qrCode}")
                        setState {
                            copy(
                                isLoading = false,
                                error = null,
                                qrCodeData = response.qrCode
                            )
                        }
                    }

                    else -> {
                        setState { copy(isLoading = false) }
                    }
                }
            }
        }
    }

    /**
     * Required in order to stop receiving emissions from interactor Flow
     */
    private fun unsubscribe() {
        threadJob?.cancel()
        threadJob = null
    }

    /**
     * Stop presentation and remove scope/listeners
     */
    private fun cleanUp() {
        unsubscribe()
        getOrCreatePresentationScope().close()
        interactor.cancelTransfer()
    }

    override fun onCleared() {
        cleanUp()
        super.onCleared()
    }
}
