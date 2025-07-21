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

package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.config.StorageConfig

interface PassphraseStorageController {
    fun hasPassphrase(): Boolean
    fun getSaltAndHash(): Pair<String, String>?

    fun setPassphrase(passphrase: List<String>)

    fun retrievePassphrase(): String

    fun verifyPassphrase(input: List<String>): Boolean
}

class PassphraseStorageControllerImpl(private val storageConfig: StorageConfig) : PassphraseStorageController {
    override fun getSaltAndHash(): Pair<String, String>? = storageConfig.passphraseStorageProvider.getSaltAndHash()


    override fun hasPassphrase(): Boolean = storageConfig.passphraseStorageProvider.hasPassphrase()

    override fun retrievePassphrase(): String = storageConfig.passphraseStorageProvider.retrievePassphrase()

    override fun setPassphrase(passphrase: List<String>) {
        storageConfig.passphraseStorageProvider.setPassphrase(passphrase)
    }


    override fun verifyPassphrase(input: List<String>): Boolean = storageConfig.passphraseStorageProvider.verifyPassphrase(input)


}