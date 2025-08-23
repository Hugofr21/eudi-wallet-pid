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

package eu.europa.ec.verifierfeature.ui.request

import androidx.lifecycle.viewModelScope
import eu.europa.ec.businesslogic.extension.ifEmptyOrNull
import eu.europa.ec.commonfeature.config.BiometricMode
import eu.europa.ec.commonfeature.config.BiometricUiConfig
import eu.europa.ec.commonfeature.config.OnBackNavigationConfig
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.ui.request.Event
import eu.europa.ec.commonfeature.ui.request.RequestViewModel
import eu.europa.ec.commonfeature.ui.request.model.RequestDocumentItemUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.RelyingPartyDataUi
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.config.ConfigNavigation
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.navigation.CommonScreens
import eu.europa.ec.uilogic.navigation.VerifierScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import eu.europa.ec.verifierfeature.interactor.PresentationRequestVerifierInteractor
import eu.europa.ec.verifierfeature.interactor.PresentationRequestVerifierInteractorPartialState
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import org.koin.core.annotation.InjectedParam
import java.util.concurrent.CancellationException

@KoinViewModel
class PresentationRequestVerifierViewModel(
    private val interactor: PresentationRequestVerifierInteractor,
    private val resourceProvider: ResourceProvider,
    private val uiSerializer: UiSerializer,
    @InjectedParam private val requestUriConfigRaw: String
) : RequestViewModel() {

    override fun getHeaderConfig(): ContentHeaderConfig {
        return ContentHeaderConfig(
            description = resourceProvider.getString(R.string.request_header_description),
            mainText = resourceProvider.getString(R.string.request_header_main_text),
            relyingPartyData = getRelyingPartyData(
                name = null,
                isVerified = false,
            ),
        )
    }

    override fun getNextScreen(): String {
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
                                navigationType = NavigationType.PushScreen(VerifierScreens.PresentationLoadingVerifier),
                            ),
                            onBackNavigationConfig = OnBackNavigationConfig(
                                onBackNavigation = ConfigNavigation(
                                    navigationType = NavigationType.PopTo(VerifierScreens.PresentationLoadingVerifier),
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

    override fun doWork() {
        println("[doWork] start")
        setState {
            copy(
                isLoading = true,
                error = null
            )
        }

        println("[doWork] decoding RequestUriConfig from base64...")
        val requestUriConfig = try {
            uiSerializer.fromBase64(
                requestUriConfigRaw,
                RequestUriConfig::class.java,
                RequestUriConfig.Parser
            ) ?: run {
                println("[doWork] ERROR: RequestUriConfig is null after decoding")
                throw RuntimeException("RequestUriConfig:: is Missing or invalid")
            }
        } catch (e: Exception) {
            println("[doWork] Exception while decoding RequestUriConfig: ${e.message}")
            e.printStackTrace()
            throw RuntimeException("RequestUriConfig:: is Missing or invalid", e)
        }

        println("[doWork] requestUriConfig decoded: $requestUriConfig")
        interactor.setConfig(requestUriConfig)
        println("[doWork] interactor.setConfig() called")

        viewModelJob = viewModelScope.launch {
            println("[doWork][coroutine] launched - collecting getRequestDocuments()")
            try {
                interactor.getRequestDocuments()
                    .onStart { println("[doWork][flow] onStart emission") }
                    .onEach { println("[doWork][flow] onEach emission: $it") }
                    .catch { e ->
                        println("[doWork][flow] upstream exception: ${e.message}")
                        e.printStackTrace()
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    onRetry = { setEvent(Event.DoWork) },
                                    errorSubTitle = "Erro interno: ${e.message}",
                                    onCancel = { setEvent(Event.Pop) }
                                )
                            )
                        }
                    }
                    .collect { response ->
                        println("[doWork][collect] received response: ${response::class.simpleName}")
                        when (response) {
                            is PresentationRequestVerifierInteractorPartialState.Failure -> {
                                println("[doWork][collect] Failure -> error = ${response.error}")
                                setState {
                                    copy(
                                        isLoading = false,
                                        error = ContentErrorConfig(
                                            onRetry = { setEvent(Event.DoWork) },
                                            errorSubTitle = response.error,
                                            onCancel = { setEvent(Event.Pop) }
                                        )
                                    )
                                }
                            }

                            is PresentationRequestVerifierInteractorPartialState.Success -> {
                                println("[doWork][collect] Success -> requestDocuments.size = ${response.requestDocuments.size}")
                                println("[doWork][collect] verifierName = ${response.verifierName}, isTrusted = ${response.verifierIsTrusted}")
                                updateData(response.requestDocuments)

                                val updatedHeaderConfig = viewState.value.headerConfig.copy(
                                    relyingPartyData = getRelyingPartyData(
                                        name = response.verifierName,
                                        isVerified = response.verifierIsTrusted,
                                    )
                                )

                                setState {
                                    copy(
                                        isLoading = false,
                                        error = null,
                                        headerConfig = updatedHeaderConfig,
                                        items = response.requestDocuments,
                                    )
                                }
                                println("[doWork][collect] State updated for Success")
                            }

                            is PresentationRequestVerifierInteractorPartialState.Disconnect -> {
                                println("[doWork][collect] Disconnect -> popping")
                                setEvent(Event.Pop)
                            }

                            is PresentationRequestVerifierInteractorPartialState.NoData -> {
                                println("[doWork][collect] NoData -> verifierName = ${response.verifierName}")
                                val updatedHeaderConfig = viewState.value.headerConfig.copy(
                                    relyingPartyData = getRelyingPartyData(
                                        name = response.verifierName,
                                        isVerified = response.verifierIsTrusted,
                                    )
                                )

                                setState {
                                    copy(
                                        isLoading = false,
                                        error = null,
                                        headerConfig = updatedHeaderConfig,
                                        noItems = true,
                                    )
                                }
                            }
                        }
                    }
                println("[doWork][coroutine] collect finished normally")
            } catch (e: CancellationException) {
                println("[doWork][coroutine] cancelled: ${e.message}")
                throw e
            } catch (e: Exception) {
                println("[doWork][coroutine] uncaught exception: ${e.message}")
                e.printStackTrace()
                setState {
                    copy(
                        isLoading = false,
                        error = ContentErrorConfig(
                            onRetry = { setEvent(Event.DoWork) },
                            errorSubTitle = "Erro: ${e.message}",
                            onCancel = { setEvent(Event.Pop) }
                        )
                    )
                }
            }
        }

        viewModelJob?.invokeOnCompletion { throwable ->
            if (throwable == null) {
                println("[doWork] viewModelJob completed successfully")
            } else {
                println("[doWork] viewModelJob completed with throwable: ${throwable.message}")
                throwable.printStackTrace()
            }
        }
        println("[doWork] end")
    }

    override fun updateData(
        updatedItems: List<RequestDocumentItemUi>,
        allowShare: Boolean?
    ) {
        super.updateData(updatedItems, allowShare)
        interactor.updateRequestedDocuments(updatedItems)
    }

    override fun cleanUp() {
        super.cleanUp()
        interactor.stopPresentation()
    }

    private fun getRelyingPartyData(
        name: String?,
        isVerified: Boolean,
    ): RelyingPartyDataUi {
        return RelyingPartyDataUi(
            isVerified = isVerified,
            name = name.ifEmptyOrNull(
                default = resourceProvider.getString(R.string.request_relying_party_default_name)
            ),
            description = resourceProvider.getString(R.string.request_relying_party_description),
        )
    }
}