package eu.europa.ec.dashboardfeature.ui.wifi

import android.net.wifi.aware.PeerHandle
import eu.europa.ec.dashboardfeature.interactor.WifiAwareInteractor
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import org.koin.android.annotation.KoinViewModel


data class State(
    val isLoading: Boolean = false,
    val hasPermissions: Boolean = false,
    val isWifiAwareSupported: Boolean,
    val isDiscovering: Boolean = false,
    val discoveredPeers: List<PeerHandle> = emptyList()
) : ViewState

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        object Pop : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Profile.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()
    }
    object RequestPermissions : Effect()
    data class ShowPermissionDenied(val missing: List<String>) : Effect()
    data class UpdatePeers(val peers: List<PeerHandle>) : Effect()
}

sealed class Event : ViewEvent {
    object GoBack : Event()
    object GoNext : Event()
    object CheckPermissions : Event()
    object StartDiscovery : Event()
}

@KoinViewModel
class WifiAwareViewModel(
    private val interactor: WifiAwareInteractor
) : MviViewModel<Event, State, Effect>(

) {
    override fun setInitialState(): State {
        return  State(
            isWifiAwareSupported = interactor.isWifiAvailable()
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {

            Event.GoBack -> {

            }

            Event.GoNext -> {

            }

            Event.CheckPermissions -> TODO()
            Event.StartDiscovery -> {
                interactor.scanPeers()
            }
        }
    }

}


