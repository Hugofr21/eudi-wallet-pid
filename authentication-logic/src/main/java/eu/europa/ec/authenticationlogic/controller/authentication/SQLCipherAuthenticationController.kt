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
import android.util.Base64

import eu.europa.ec.authenticationlogic.controller.storage.SQLCipherStorageController
import eu.europa.ec.resourceslogic.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom


interface SQLCipherAuthenticationController {
    fun deviceSupportsSQLCipher(listener: (SQLCipherAvailability) -> Unit)
    suspend fun authenticate(value: String): SQLCipherAuthenticate
    fun getKey(): String
    fun hasKey(): Boolean
}

class SQLCipherAuthenticationControllerImpl(
    private val context: Context,
    private val sqlCipherStorageController: SQLCipherStorageController,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SQLCipherAuthenticationController {

    override fun deviceSupportsSQLCipher(listener: (SQLCipherAvailability) -> Unit) {
        try {
            if (!hasKey()) {

                val randomBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val randomValue = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
                sqlCipherStorageController.setSQLCipherKey(randomValue)
            }
            listener(SQLCipherAvailability.CanAuthenticate)
        } catch (e: Exception) {
            listener(SQLCipherAvailability.Failure(
                context.getString(R.string.generic_error_description)
            ))
        }
    }


    override suspend fun authenticate(value: String): SQLCipherAuthenticate =
        withContext(dispatcher) {
            try {
                if (!sqlCipherStorageController.isSQLCipherKeyValid(value)) {
                    return@withContext SQLCipherAuthenticate.Failure(
                        context.getString(R.string.error_invalid_pin)
                    )
                }
                val pass = sqlCipherStorageController.retrieveSQLCipherKey()
                SQLCipherAuthenticate.Success(pass)
            } catch (e: IllegalStateException) {
                SQLCipherAuthenticate.Failure(
                    context.getString(R.string.error_no_sqlcipher_key)
                )
            } catch (t: Throwable) {
                SQLCipherAuthenticate.Failure(
                    context.getString(R.string.generic_error_description)
                )
            }
        }


    override fun getKey(): String {
        return sqlCipherStorageController.retrieveSQLCipherKey()
    }

    override fun hasKey(): Boolean {
        return sqlCipherStorageController.hasSQLCipherKey()
    }


}

sealed class SQLCipherAvailability {
    object CanAuthenticate : SQLCipherAvailability()
    object NonEnrolled : SQLCipherAvailability()
    data class Failure(val errorMessage: String) : SQLCipherAvailability()
}

sealed class SQLCipherAuthenticate {
    data class Success(val passphrase: String) : SQLCipherAuthenticate()
    data class Failure(val errorMessage: String) : SQLCipherAuthenticate()
}
