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

package eu.europa.ec.commonfeature.interactor

import eu.europa.ec.authenticationlogic.controller.storage.PinStorageController
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.businesslogic.validator.FormValidator
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface QuickPinInteractor : FormValidator {
    /**
     * Defines the initial PIN. Validates if the two inputs match and meet
     * the complexity requirements.
     */
    fun setPin(newPin: String, confirmationPin: String): Flow<QuickPinInteractorSetPinPartialState>

    /**
     * Changes the existing PIN. REQUIRES the current PIN to authorize the operation.
     * @param currentPin The current PIN for authentication.
     * @param newPin The new PIN required.
     */
    fun changePin(currentPin: String, newPin: String): Flow<QuickPinInteractorSetPinPartialState>

    fun isCurrentPinValid(pin: String): Flow<QuickPinInteractorPinValidPartialState>

    fun hasPin(): Boolean
}

class QuickPinInteractorImpl(
    private val formValidator: FormValidator,
    private val pinStorageController: PinStorageController,
    private val resourceProvider: ResourceProvider,
) : FormValidator by formValidator, QuickPinInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun hasPin(): Boolean = pinStorageController.hasPin()

    override fun setPin(
        newPin: String,
        confirmationPin: String
    ): Flow<QuickPinInteractorSetPinPartialState> = flow {

        // 1. Validação de Integridade (Match)
        if (newPin != confirmationPin) {
            emit(QuickPinInteractorSetPinPartialState.Failed(
                resourceProvider.getString(R.string.quick_pin_non_match)
            ))
            return@flow
        }

        // 2. Validação de Segurança (Complexidade)
        if (isPinWeak(newPin)) {
            // CORREÇÃO: Uso de ResourceProvider ao invés de string hardcoded
            emit(QuickPinInteractorSetPinPartialState.Failed(
                resourceProvider.getString(R.string.quick_pin_too_weak_error)
            ))
            return@flow
        }

        // 3. Persistência Segura
        pinStorageController.setPin(newPin)
        emit(QuickPinInteractorSetPinPartialState.Success)

    }.safeAsync {
        QuickPinInteractorSetPinPartialState.Failed(it.localizedMessage ?: genericErrorMsg)
    }

    override fun changePin(
        currentPin: String,
        newPin: String
    ): Flow<QuickPinInteractorSetPinPartialState> = flow {
        if (!pinStorageController.isPinValid(currentPin)) {
            emit(QuickPinInteractorSetPinPartialState.Failed(
                resourceProvider.getString(R.string.quick_pin_invalid_error)
            ))
            return@flow
        }


        if (isPinWeak(newPin)) {
            emit(QuickPinInteractorSetPinPartialState.Failed(
                "O PIN é muito simples."
            ))
            return@flow
        }

        if (currentPin == newPin) {
            emit(QuickPinInteractorSetPinPartialState.Failed(
                "O novo PIN não pode ser igual ao atual."
            ))
            return@flow
        }


        pinStorageController.setPin(newPin)
        emit(QuickPinInteractorSetPinPartialState.Success)

    }.safeAsync {
        QuickPinInteractorSetPinPartialState.Failed(it.localizedMessage ?: genericErrorMsg)
    }

    override fun isCurrentPinValid(pin: String): Flow<QuickPinInteractorPinValidPartialState> = flow {
        if (pinStorageController.isPinValid(pin)) {
            emit(QuickPinInteractorPinValidPartialState.Success)
        } else {
            emit(QuickPinInteractorPinValidPartialState.Failed(
                resourceProvider.getString(R.string.quick_pin_invalid_error)
            ))
        }
    }.safeAsync {
        QuickPinInteractorPinValidPartialState.Failed(it.localizedMessage ?: genericErrorMsg)
    }

    private fun isPinWeak(pin: String): Boolean {
        val sequences = listOf("1234", "0000", "1111", "1212")
        return pin.length < 4 || sequences.contains(pin)
    }
}
sealed class QuickPinInteractorSetPinPartialState {
    data object Success : QuickPinInteractorSetPinPartialState()
    data class Failed(val errorMessage: String) : QuickPinInteractorSetPinPartialState()
}

sealed class QuickPinInteractorPinValidPartialState {
    data object Success : QuickPinInteractorPinValidPartialState()
    data class Failed(val errorMessage: String) : QuickPinInteractorPinValidPartialState()
}

sealed class QuickBiometricInteractorSetPinPartialState {
    data object Success : QuickBiometricInteractorSetPinPartialState()
    data class Failed(val errorMessage: String) : QuickBiometricInteractorSetPinPartialState()
}

sealed class QuickBiometricInteractorPinValidPartialState {
    data object Success : QuickBiometricInteractorPinValidPartialState()
    data class Failed(val errorMessage: String) : QuickBiometricInteractorPinValidPartialState()
}