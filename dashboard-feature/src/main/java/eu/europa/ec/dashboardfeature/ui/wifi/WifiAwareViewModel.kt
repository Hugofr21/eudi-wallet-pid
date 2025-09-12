package eu.europa.ec.dashboardfeature.ui.wifi

import android.net.wifi.aware.PeerHandle
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import eu.europa.ec.corelogic.controller.WalletLiveDataController
import eu.europa.ec.corelogic.service.NetworkStatus
import eu.europa.ec.dashboardfeature.interactor.WifiAwareInteractor
import eu.europa.ec.dashboardfeature.ui.wifi.Effect.*
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.WIFIScreens
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel


data class State(
    val isWifiAwareSupported: Boolean,
    val hasPermissions: Boolean,
    val discoveredPeers: List<PeerHandle>? = emptyList<PeerHandle>(),
    val isDiscovering: Boolean? = false,
    val isLoading: Boolean?  = false,
    val networkStatus: NetworkStatus? = null
) : ViewState

sealed class Effect : ViewSideEffect {
    data class RequestPermissions(val permissions: List<String>) : Effect()
    data class ShowPermissionDenied(val missing: List<String>) : Effect()
    data class UpdatePeers(val peers: List<PeerHandle>) : Effect()
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
        data class SwitchScreen(val screenRoute: String, val popUpToScreenRoute: String? = null, val inclusive: Boolean = false) : Navigation()
    }
}

sealed class Event : ViewEvent {
    object GoBack : Event()
    object CheckPermissions : Event()
    object StartDiscovery : Event()
    object StartSubscription : Event()

    object StopDiscovery : Event()

    object Info : Event()
}

@KoinViewModel
class WifiAwareViewModel(
    private val interactor: WifiAwareInteractor,
    private val walletLiveDataController: WalletLiveDataController
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State {
        return State(
            isWifiAwareSupported = interactor.isWifiAvailable(),
            hasPermissions = walletLiveDataController.checkAndRequestWifiAwarePermissions(),
        )
    }

    init {
        viewModelScope.launch {
            walletLiveDataController.peersLiveData.asFlow().collect { peers ->
                println("[WifiAwareViewModel] Peers recebidos: $peers")
                setState { copy(discoveredPeers = peers, isLoading = false) }
                setEffect({ UpdatePeers(peers) })
            }
        }
    }

    override fun handleEvents(event: Event) {
        println("[WifiAwareViewModel] Evento recebido: $event")
        when (event) {
            Event.GoBack -> {
                setEffect({ Navigation.Pop })
            }
            Event.CheckPermissions -> {
                if (!walletLiveDataController.checkAndRequestWifiAwarePermissions()) {
                    println("[WifiAwareViewModel] Permissões não concedidas, solicitando...")
                    setEffect { RequestPermissions(walletLiveDataController.getMissingPermissions()) }
                } else {
                    println("[WifiAwareViewModel] Todas as permissões concedidas")
                    setState { copy(hasPermissions = true) }
                    if (interactor.isWifiAvailable()) {
                        handleEvents(Event.StartDiscovery)
                    } else {
                        setEffect({ ShowPermissionDenied(listOf("Wi-Fi Aware not available")) })
                    }
                }
            }
            Event.StartDiscovery -> {
                if (!interactor.isWifiAvailable()) {
                    println("[WifiAwareViewModel] Wi-Fi Aware not available")
                    setState { copy(isWifiAwareSupported = false) }
                    setEffect({ ShowPermissionDenied(listOf("Wi-Fi Aware not available")) })
                    return
                }
                if (!viewState.value.hasPermissions) {
                    println("[WifiAwareViewModel] Permissões não concedidas, verificando...")
                    handleEvents(Event.CheckPermissions)
                    return
                }
                println("[WifiAwareViewModel] Iniciando descoberta")
                setState { copy(isDiscovering = true, isLoading = true) }
                interactor.scanPeers()
            }

            Event.StopDiscovery ->{
                interactor.stopScan()
                setState { copy(isDiscovering = false, isLoading = false) }
            }

            Event.StartSubscription -> {
                 interactor.startSubscription()
            }

            Event.Info -> {
//                setEffect {
//                    Navigation.SwitchScreen(
//                        screenRoute = WIFIScreens.Info.screenRoute,
//                    )
//                }
            }
        }
    }
}