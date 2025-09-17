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

package eu.europa.ec.dashboardfeature.ui.profile
import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.commonfeature.config.IssuanceUiConfig
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.corelogic.di.getOrCreatePresentationScope
import eu.europa.ec.dashboardfeature.interactor.PersonIdentificationDataInteractor
import eu.europa.ec.dashboardfeature.model.ClaimsUI
import eu.europa.ec.dashboardfeature.ui.home.BleAvailability
import eu.europa.ec.dashboardfeature.ui.home.Effect.Navigation
import eu.europa.ec.dashboardfeature.ui.home.HomeScreenBottomSheetContent
import eu.europa.ec.dashboardfeature.ui.home.HomeScreenBottomSheetContent.Bluetooth
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.ProximityScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

enum class BleAvailability {
    AVAILABLE, NO_PERMISSION, DISABLED, UNKNOWN
}


data class State(
    val firstName: String,
    val lastName: String,
    val isLoading: Boolean = false,
    val imageBase64: String? = null,
    val claimsUi: List<ClaimsUI> = emptyList(),
    val documentsUi: List<Any> = emptyList(),
    val bleAvailability: BleAvailability = BleAvailability.UNKNOWN,
    val isBleCentralClientModeEnabled: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    object GoBack : Event()
    object CreateQrCode : Event()
    object AddDocument : Event()

    data class OnPermissionStateChanged(val availability: BleAvailability) : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        object Pop : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Profile.screenRoute,
            val inclusive: Boolean = false,
        ) : Navigation()

        data object OnAppSettings : Navigation()
        data object OnSystemSettings : Navigation()

    }
}

@KoinViewModel
class ProfileViewModel(
    private val uiSerializer: UiSerializer,
    private val personIdentificationDataInteractor: PersonIdentificationDataInteractor,
) : MviViewModel<Event, State, Effect>() {


    override fun setInitialState(): State {
        val (firstName, lastName) = personIdentificationDataInteractor.getUserFirstAndLastName()
        val portrait = personIdentificationDataInteractor.getUserWithPortrait()
        personIdentificationDataInteractor.printAllDocumentDetails()

        return State(
            documentsUi = personIdentificationDataInteractor.getPidDocuments(),
            firstName = firstName,
            lastName = lastName,
            isLoading = false,
            imageBase64 = portrait,
            claimsUi = personIdentificationDataInteractor.getListClaims()
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            Event.AddDocument -> navigateToNextScreenAddDocument()
            Event.CreateQrCode -> {
                handleCreateQrRequest()
            }
            Event.GoBack -> setEffect { Effect.Navigation.Pop }
            is Event.OnPermissionStateChanged -> {
                setState { copy(bleAvailability = event.availability) }
                if (event.availability == BleAvailability.AVAILABLE) {
                    proceedToGenerateQrIfReady()
                }
            }
        }
    }

    private fun navigateToNextScreenAddDocument() {
        val addDocumentScreenRoute = generateComposableNavigationLink(
            screen = IssuanceScreens.AddDocument,
            arguments = generateComposableArguments(
                mapOf(
                    IssuanceUiConfig.serializedKeyName to uiSerializer.toBase64(
                        model = IssuanceUiConfig(
                            flowType = IssuanceFlowType.ExtraDocument(
                                formatType = null
                            )
                        ),
                        parser = IssuanceUiConfig.Parser
                    )
                )
            )
        )
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = addDocumentScreenRoute
            )
        }
    }


    private fun navigateToNextScreenGenerateQr() {
       val screenRoute = generateComposableNavigationLink(
            screen = ProximityScreens.GenerateQR,
            arguments = generateComposableArguments(
                mapOf(
                    RequestUriConfig.serializedKeyName to uiSerializer.toBase64(
                        RequestUriConfig(PresentationMode.QrCodeMdoc(DashboardScreens.Profile.screenRoute)),
                        RequestUriConfig.Parser
                    )
                )
            )
        )
        setEffect {
            Effect.Navigation.SwitchScreen(
                screenRoute = screenRoute
            )
        }
    }


    private fun handleCreateQrRequest() {
        viewModelScope.launch {
            if (!personIdentificationDataInteractor.hasRequiredBlePermissions()) {
                setState { copy(bleAvailability = BleAvailability.NO_PERMISSION) }
                return@launch
            }

            if (!personIdentificationDataInteractor.isBluetoothSupported()) {
                setState { copy(bleAvailability = BleAvailability.UNKNOWN) }
                return@launch
            }

            if (!personIdentificationDataInteractor.isBleAvailable()) {
                setState { copy(bleAvailability = BleAvailability.DISABLED) }
                setEffect { Effect.Navigation.OnSystemSettings }
                return@launch
            }

            setState { copy(bleAvailability = BleAvailability.AVAILABLE) }
            getOrCreatePresentationScope()
            navigateToNextScreenGenerateQr()
        }
    }


    private fun proceedToGenerateQrIfReady() {
        viewModelScope.launch {
            if (personIdentificationDataInteractor.hasRequiredBlePermissions()
                && personIdentificationDataInteractor.isBluetoothSupported()
                && personIdentificationDataInteractor.isBleAvailable()
            ) {
                setState { copy(bleAvailability = BleAvailability.AVAILABLE) }
                getOrCreatePresentationScope()
                navigateToNextScreenGenerateQr()
            }
        }
    }


}
