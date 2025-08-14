package eu.europa.ec.verifierfeature.ui.request


import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.commonfeature.config.BiometricMode
import eu.europa.ec.commonfeature.config.BiometricUiConfig
import eu.europa.ec.commonfeature.config.OnBackNavigationConfig
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.corelogic.model.ClaimDomain
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.ProximityScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import eu.europa.ec.verifierfeature.interactor.AgeProofInteractor
import eu.europa.ec.verifierfeature.ui.fieldLabelsPrrofAge.model.RequestArgs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam

sealed class BiometricStatus {
    object Unknown : BiometricStatus()
    object NotSupported : BiometricStatus()
    object NoneEnrolled : BiometricStatus()
    object Supported : BiometricStatus()
    data class AuthResult(val success: Boolean, val message: String?) : BiometricStatus()
}

data class ListItemData(
    val itemId: String,
    val title: String,
    val subtitle: String? = null,
    val isMandatory: Boolean = false,
    val biometricStatus: BiometricStatus = BiometricStatus.Unknown,
)

data class State(
    val isLoading: Boolean = false,
    val availableFields: List<ListItemData> = emptyList(),
    val selectedFieldLabels: Set<String> = emptySet(),
    val notifyOnAuthenticationFailure: Boolean = false
) : ViewState

sealed class Event : ViewEvent {
    data class ToggleFieldLabel(val key: String, val isChecked: Boolean) : Event()
    object SubmitSelection : Event()
    object GoBack : Event()

    data class BiometricChecked(val status: BiometricStatus) : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        object Pop : Navigation()
        data class SwitchScreen(
            val screenRoute: String,
            val popUpToScreenRoute: String = DashboardScreens.Profile.screenRoute,
            val inclusive: Boolean = false
        ) : Navigation()
    }
}


@KoinViewModel
class RequestVerifierViewModel(
    private val interactor: AgeProofInteractor,
    @InjectedParam private val encodedArgs: String,
    private val resourceProvider: ResourceProvider,
    private val uiSerializer: UiSerializer,

    ) : MviViewModel<Event, State, Effect>() {
    private lateinit var requestArgs: RequestArgs

    override fun setInitialState(): State {

        val decodedJson = Uri.decode(encodedArgs)
        requestArgs = Json.decodeFromString(decodedJson)

        println("[RequestVerifierViewModel] documentId: ${requestArgs.documentId}")
        println("[RequestVerifierViewModel] detailsType: ${requestArgs.detailsType}")
        println("[RequestVerifierViewModel] fieldLabels: ${requestArgs.fieldLabels.size}")
        viewModelScope.launch {

            val success = interactor.getDocumentDetails(requestArgs.documentId)
            println("Issuer: ${success.issuerName}")
            println("Logo URI: ${success.issuerLogo}")
            println("Is bookmarked: ${success.documentIsBookmarked}")
            println("Is revoked: ${success.isRevoked}")
            println("Credentials info: ${success.documentCredentialsInfoUi}")
            println("Claims: ${success.documentDetailsDomain.documentClaims}")

            val availableFields = success.documentDetailsDomain.documentClaims.mapNotNull { claim ->
                when (claim) {
                    is ClaimDomain.Primitive -> ListItemData(
                        itemId = claim.key,
                        title = claim.displayTitle,
                        subtitle = claim.value,
                        isMandatory = true
                    )
                    else -> null
                }
            }

            setState {
                copy(
                    isLoading = false,
                    availableFields = availableFields,
                )
            }


        }

        return State(
            isLoading = true,
            availableFields = emptyList(),
        )

    }

    override fun handleEvents(event: Event) {
        when (event) {
            Event.SubmitSelection -> {
                val selected = viewState.value.selectedFieldLabels.toList()
                viewModelScope.launch {
                    interactor.intFlowVerifier(requestArgs.documentId, requestArgs.fieldLabels)
                }
            }
            Event.GoBack -> setEffect { Effect.Navigation.Pop }
            is Event.ToggleFieldLabel -> {
                val updated = if (event.isChecked) {
                    viewState.value.selectedFieldLabels + event.key
                } else {
                    viewState.value.selectedFieldLabels - event.key
                }
                setState { copy(selectedFieldLabels = updated) }
            }

            is Event.BiometricChecked -> {
                val selected = viewState.value.selectedFieldLabels.toList()
                viewModelScope.launch {
                    interactor.intFlowVerifier(requestArgs.documentId, requestArgs.fieldLabels)
                }
            }
        }
    }

     fun getNextScreen(): String {
        return generateComposableNavigationLink(
            screen = CommonScreens.Biometric,
            arguments = generateComposableArguments(
                mapOf(
                    BiometricUiConfig.serializedKeyName to uiSerializer.toBase64(
                        BiometricUiConfig(
                            mode = BiometricMode.Default(
                                descriptionWhenBiometricsEnabled = resourceProvider.getString(R.string.loading_biometry_biometrics_enabled_description),
                                descriptionWhenBiometricsNotEnabled = resourceProvider.getString(R.string.loading_biometry_biometrics_not_enabled_description),
                                textAbovePin = resourceProvider.getString(R.string.biometric_default_mode_text_above_pin_field),
                            ),
                            isPreAuthorization = false,
                            shouldInitializeBiometricAuthOnCreate = true,
                            onSuccessNavigation = ConfigNavigation(
                                navigationType = NavigationType.PushScreen(ProximityScreens.Loading)
                            ),
                            onBackNavigationConfig = OnBackNavigationConfig(
                                onBackNavigation = ConfigNavigation(
                                    navigationType = NavigationType.PopTo(ProximityScreens.Request),
                                ),
                                hasToolbarBackIcon = true
                            )
                        ),
                        BiometricUiConfig.Parser
                    ).orEmpty()
                )
            )
        )
    }
}
