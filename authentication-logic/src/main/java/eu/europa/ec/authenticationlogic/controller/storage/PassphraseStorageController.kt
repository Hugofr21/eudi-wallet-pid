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
    fun getPassphrase(): String?
    fun getHasPassphrase(): Boolean
    fun retrievePassphrase(): String
    fun setPassphrase(passphrase: String)
    fun isPassphraseValid(passphrase: String): Boolean
}

class PassphraseStorageControllerImpl(private val storageConfig: StorageConfig) : PassphraseStorageController {
    override fun getPassphrase(): String? = storageConfig.passphraseStorageProvider.getPassphrase()


    override fun getHasPassphrase(): Boolean = storageConfig.passphraseStorageProvider.getHasPassphrase()

    override fun retrievePassphrase(): String = storageConfig.passphraseStorageProvider.retrievePassphrase()

    override fun setPassphrase(passphrase: String) {
        storageConfig.passphraseStorageProvider.setPassphrase(passphrase)
    }

    override fun isPassphraseValid(passphrase: String): Boolean = storageConfig.passphraseStorageProvider.isPassphraseValid(passphrase)
}