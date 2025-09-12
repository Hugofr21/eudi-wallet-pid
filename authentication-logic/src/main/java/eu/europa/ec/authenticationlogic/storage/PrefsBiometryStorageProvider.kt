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

package eu.europa.ec.authenticationlogic.storage

import com.google.gson.Gson
import eu.europa.ec.authenticationlogic.model.biometric.BiometricAuthentication
import eu.europa.ec.authenticationlogic.provider.BiometryStorageProvider
import eu.europa.ec.businesslogic.controller.storage.PrefsController

class PrefsBiometryStorageProvider(
    private val prefsController: PrefsController
) : BiometryStorageProvider {

    private val gson = Gson()

    override fun getBiometricAuthentication(): BiometricAuthentication? {
        val raw = prefsController.getString("BiometricAuthentication", "")
        if (raw.isBlank() || raw == "null") return null

        return try {
            gson.fromJson(raw, BiometricAuthentication::class.java)
        } catch (e: Exception) {
            println("Failed to parse BiometricAuthentication JSON $e")
            null
        }
    }

    override fun setBiometricAuthentication(value: BiometricAuthentication?) {
        if (value == null) {
            prefsController.clear("BiometricAuthentication")
            return
        }
        val json = try {
            gson.toJson(value)
        } catch (e: Exception) {
            println("Failed to serialize BiometricAuthentication $e")
            return
        }
        prefsController.setString("BiometricAuthentication", json)
    }

    override fun setUseBiometricsAuth(value: Boolean) {
        prefsController.setBool("UseBiometricsAuth", value)
    }

    override fun getUseBiometricsAuth(): Boolean {
        return prefsController.getBool("UseBiometricsAuth", false)
    }

}
