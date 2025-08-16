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

package eu.europa.ec.verifierfeature.interactor

import android.content.Context
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.authenticationlogic.model.biometric.BiometricCrypto
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.verifierfeature.controller.document.EventPresentationDocumentController
import eu.europa.ec.verifierfeature.controller.document.SendRequestedDocumentsVerifierPartialState
import eu.europa.ec.verifierfeature.controller.document.WalletCoreVerifiedPartialState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import java.net.URI

sealed class PresentationLoadingVerifierObserveResponsePartialState {
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : PresentationLoadingVerifierObserveResponsePartialState()

    data class Failure(val error: String) : PresentationLoadingVerifierObserveResponsePartialState()
    data object Success : PresentationLoadingVerifierObserveResponsePartialState()
    data class Redirect(val uri: URI) : PresentationLoadingVerifierObserveResponsePartialState()
    data object RequestReadyToBeSent : PresentationLoadingVerifierObserveResponsePartialState()
}

sealed class PresentationLoadingSendRequestedDocumentPartialState {
    data class Failure(val error: String) : PresentationLoadingSendRequestedDocumentPartialState()
    data object Success : PresentationLoadingSendRequestedDocumentPartialState()
}

interface PresentationLoadingVerifierInteractor {
    fun observeResponse(): Flow<PresentationLoadingVerifierObserveResponsePartialState>
    fun sendRequestedDocuments(): PresentationLoadingSendRequestedDocumentPartialState
    fun handleUserAuthentication(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult,
    )
}

class PresentationLoadingVerifierInteractorImpl(
    private val eventPresentationDocumentController: EventPresentationDocumentController,
    private val deviceAuthenticationInteractor: DeviceAuthenticationInteractor,
) : PresentationLoadingVerifierInteractor {

    override fun observeResponse(): Flow<PresentationLoadingVerifierObserveResponsePartialState> =
        eventPresentationDocumentController.observeSentDocumentsRequest()
            .mapNotNull { response ->
                println("[PresentationLoadingVerifierInteractorImpl] raw response → $response")

                when (response) {
                    is WalletCoreVerifiedPartialState.Failure -> {
                        println("Failure(error=${response.error})")
                        PresentationLoadingVerifierObserveResponsePartialState.Failure(
                            error = response.error,
                        )
                    }
                    is WalletCoreVerifiedPartialState.Redirect -> {
                        println("Redirect(uri=${response.uri})")
                        PresentationLoadingVerifierObserveResponsePartialState.Redirect(
                            uri = response.uri
                        )
                    }
                    is WalletCoreVerifiedPartialState.Success -> {
                        println("Success")
                        PresentationLoadingVerifierObserveResponsePartialState.Success
                    }
                    is WalletCoreVerifiedPartialState.UserAuthenticationRequired -> {
                        println("UserAuthenticationRequired(authData=${response.authenticationData})")
                        PresentationLoadingVerifierObserveResponsePartialState.UserAuthenticationRequired(
                            response.authenticationData
                        )
                    }
                    is WalletCoreVerifiedPartialState.RequestIsReadyToBeSent -> {
                        println("RequestIsReadyToBeSent")
                        PresentationLoadingVerifierObserveResponsePartialState.RequestReadyToBeSent
                    }
                }
            }

    override fun sendRequestedDocuments(): PresentationLoadingSendRequestedDocumentPartialState {
        return when (val result = eventPresentationDocumentController.sendRequestedDocuments()) {
            is SendRequestedDocumentsVerifierPartialState.RequestSent -> {
                println("SendRequestedDocumentsPartialState.RequestSent $result ")
                PresentationLoadingSendRequestedDocumentPartialState.Success
            }
            is SendRequestedDocumentsVerifierPartialState.Failure -> {
                println("SendRequestedDocumentsPartialState.Failure ${result.error} ")
                PresentationLoadingSendRequestedDocumentPartialState.Failure(
                    result.error
                )
            }
        }
    }

    override fun handleUserAuthentication(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult,
    ) {
        deviceAuthenticationInteractor.getBiometricsAvailability {
            when (it) {
                is BiometricsAvailability.CanAuthenticate -> {
                    deviceAuthenticationInteractor.authenticateWithBiometrics(
                        context = context,
                        crypto = crypto,
                        notifyOnAuthenticationFailure = notifyOnAuthenticationFailure,
                        resultHandler = resultHandler
                    )
                }

                is BiometricsAvailability.NonEnrolled -> {
                    deviceAuthenticationInteractor.launchBiometricSystemScreen()
                }

                is BiometricsAvailability.Failure -> {
                    resultHandler.onAuthenticationFailure()
                }
            }
        }
    }
}