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

package eu.europa.ec.authenticationlogic.controller.authentication

import android.content.Context
import eu.europa.ec.authenticationlogic.controller.storage.PassphraseStorageController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


enum class PassphraseAuthentication {
    SUCCESS,
    INVALID_PASSPHRASE,
    STORAGE_ERROR,
    UNKNOWN_ERROR
}

interface PassphraseAuthenticationController {
    fun hasPassphrase(): Boolean
    fun getSaltAndHash(): Pair<String, String>?

    fun setPassphrase(passphrase: List<String>)

    fun verifyPassphrase(input: List<String>): Boolean

    fun retrievePassphrase(): String

    suspend fun authenticate(value: List<String>): PassphraseAuthentication
}

class PassphraseAuthenticationControllerImpl(
    private val context: Context,
    private val resourceProvider: ResourceProvider,
    private val passphraseStorageController: PassphraseStorageController

):PassphraseAuthenticationController {
    override suspend fun authenticate(value: List<String>): PassphraseAuthentication = withContext(Dispatchers.IO) {
        try {
            if (value.isEmpty()) {
                return@withContext PassphraseAuthentication.INVALID_PASSPHRASE
            }
            val isValid = passphraseStorageController.verifyPassphrase(value)
            if (isValid) {
                PassphraseAuthentication.SUCCESS
            } else {
                PassphraseAuthentication.INVALID_PASSPHRASE
            }
        } catch (e: Exception) {
            PassphraseAuthentication.STORAGE_ERROR
        }
    }

    override fun retrievePassphrase(): String {
        return try {
            passphraseStorageController.retrievePassphrase()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to retrieve passphrase: ${e.message}")
        }
    }

    override fun hasPassphrase(): Boolean {
        return passphraseStorageController.hasPassphrase()
    }

    override fun getSaltAndHash(): Pair<String,String>? {
        return passphraseStorageController.getSaltAndHash()
    }


    override fun setPassphrase(passphrase: List<String>) {
        passphraseStorageController.setPassphrase(passphrase)

    }

    override fun verifyPassphrase(input: List<String>): Boolean {
        return passphraseStorageController.verifyPassphrase(input)
    }

}