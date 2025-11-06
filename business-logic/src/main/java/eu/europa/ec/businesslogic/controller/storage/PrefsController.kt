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

package eu.europa.ec.businesslogic.controller.storage

import android.content.SharedPreferences
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.text.Charsets.UTF_8


interface PrefsController {

    /**
     * Defines if [SharedPreferences] contains a value for given [key]. This function will only
     * identify if a key exists in storage and will not check if corresponding value is valid.
     *
     * @param key The name of the preference to check.
     *
     * @return `true` if preferences contain given key. `false` otherwise.
     */
    fun contains(key: String): Boolean

    /**
     * Removes given preference key from shared preferences. Notice that this operation is
     * irreversible and may lead to data loss.
     */
    fun clear(key: String)

    /**
     * Removes all keys from shared preferences. Notice that this operation is
     * irreversible and may lead to data loss.
     */
    fun clear()

    /**
     * Assigns given [value] to device storage - shared preferences given [key]. You can
     * retrieve this value by calling [getString].
     *
     * @param key   Key used to add given [value].
     * @param value Value to add after given [key].
     */
    fun setString(key: String, value: String)

    /**
     * Assigns given [value] to device storage - shared preferences given [key]. You can
     * retrieve this value by calling [getString].
     *
     * @param key   Key used to add given [value].
     * @param value Value to add after given [key].
     */
    fun setLong(
        key: String, value: Long
    )

    /**
     * Assigns given [value] to device storage - shared preferences given [key]. You can
     * retrieve this value by calling [getString].
     *
     * @param key   Key used to add given [value].
     * @param value Value to add after given [key].
     */
    fun setBool(key: String, value: Boolean)

    /**
     * Retrieves a string value from device shared preferences that corresponds to given [key]. If
     * key does not exist or value of given key is null, [defaultValue] is returned.
     *
     * @param key          Key to get corresponding value.
     * @param defaultValue Default value to return if given [key] does not exist in prefs or if
     * key value is invalid.
     */
    fun getString(key: String, defaultValue: String): String

    /**
     * Retrieves a long value from device shared preferences that corresponds to given [key]. If
     * key does not exist or value of given key is null, [defaultValue] is returned.
     *
     * @param key          Key to get corresponding value.
     * @param defaultValue Default value to return if given [key] does not exist in prefs or if
     * key value is invalid.
     */
    fun getLong(key: String, defaultValue: Long): Long

    /**
     * Retrieves a boolean value from the device's shared preferences associated with the given [key].
     *
     * If the [key] is not found in the preferences, or if the value associated with the [key] is null,
     * the [defaultValue] is returned.  Note that if a value exists for the key but is not a valid
     * boolean (e.g., a String or an Int), the platform may also return the [defaultValue], depending on
     * the underlying shared preferences implementation.
     *
     * @param key The key used to retrieve the boolean value.
     * @param defaultValue The boolean value to return if the [key] is not found or has a null value.
     * @return The boolean value associated with the [key], or the [defaultValue] if the [key] is not found or has a null value.
     */
    fun getBool(key: String, defaultValue: Boolean): Boolean

    /**
     * Sets an integer value associated with the given key in the underlying data store.
     * If a value already exists for the key, it will be overwritten.
     *
     * @param key The unique identifier for the integer value.  Must not be null or empty.
     * @param value The integer value to store.
     */
    fun setInt(key: String, value: Int)

    /**
     * Retrieves an integer value associated with the given key from a data source.
     * If the key is not found or the value is not an integer, it returns the specified default value.
     *
     * @param key The key associated with the integer value to retrieve.
     * @param defaultValue The default integer value to return if the key is not found or the value is not an integer.
     * @return The integer value associated with the key, or the default value if the key is not found or the value is invalid.
     */
    fun getInt(key: String, defaultValue: Int): Int
}

/**
 * Implementation of the [PrefsController] interface for managing application preferences.
 *
 * This class provides methods for storing and retrieving various data types (String, Long, Boolean, Int)
 * in the application's SharedPreferences.  All SharedPreferences are
 * stored within a file named "eudi-wallet" accessible only to this application.
 *
 * @property resourceProvider An instance of [ResourceProvider] used to access application resources,
 *                           including the application context for obtaining SharedPreferences.
 */

class PrefsControllerImpl(
    private val resourceProvider: ResourceProvider
) : PrefsController {


    companion object{
        private val datastoreName = "eudi-wallet-secure-datastore"
        private val tinkKeysetPrefKey = "tink_master_keyset"
        private const val masterKeyAlias = "android-keystore://tink_master_key"
        private const val ALGORITHM = "AES256_GCM"
    }

    private val tinkKeysetPrefsName = "${resourceProvider.provideContext().packageName}.tink_keyset"

    // https://github.com/osipxd/encrypted-datastore
    // https://medium.com/@n20/encryptedsharedpreferences-is-deprecated-what-should-android-developers-use-now-7476140e8347

    private val aead: Aead by lazy {
        AeadConfig.register()
        
        val manager = AndroidKeysetManager.Builder()
            .withSharedPref(resourceProvider.provideContext(), tinkKeysetPrefsName, tinkKeysetPrefKey)
            .withKeyTemplate(KeyTemplates.get(ALGORITHM))
            .withMasterKeyUri(masterKeyAlias)
            .build()

        manager.keysetHandle.getPrimitive(Aead::class.java)

    }
    
    
    private val dataStore: DataStore<Preferences> by lazy {
        val ctx = resourceProvider.provideContext()
        PreferenceDataStoreFactory.create(
            produceFile = { ctx.preferencesDataStoreFile(datastoreName) }
        )
    }
    
    private val aad: ByteArray? = resourceProvider.provideContext().packageName.toByteArray(UTF_8)

    private fun prefsKey(key: String) = stringPreferencesKey(key)

    private fun encryptToBase64(plaintext: String): String {
        val pt = plaintext.toByteArray(UTF_8)
        val ct = aead.encrypt(pt, aad)
        return Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun decryptFromBase64(base64Cipher: String): String? {
        return try {
            val ct = Base64.decode(base64Cipher, Base64.NO_WRAP)
            val pt = aead.decrypt(ct, aad)
            String(pt, UTF_8)
        } catch (t: Exception) {
            println("decryptFromBase64 fail!!!")
            null
        }
    }

    /**
     * Defines if [DataStore] contains a value for given [key]. This function will only
     * identify if a key exists in storage and will not check if corresponding value is valid.
     *
     * @param key The name of the preference to check.
     *
     * @return `true` if preferences contain given key. `false` otherwise.
     */
    override fun contains(key: String): Boolean = runBlocking(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        prefs[prefsKey(key)] != null
    }

    /**
     * Removes given preference key from shared preferences. Notice that this operation is
     * irreversible and may lead to data loss.
     */

    override fun clear(key: String): Unit = runBlocking(Dispatchers.IO) {
        dataStore.edit { prefs -> prefs.remove(prefsKey(key)) }
    }

    /**
     * Removes all keys from shared preferences. Notice that this operation is
     * irreversible and may lead to data loss.
     */
    override fun clear(): Unit = runBlocking(Dispatchers.IO) {
        dataStore.edit { prefs -> prefs.clear() }
    }

    /**
     * Assigns given [value] to device storage - shared preferences given [key]. You can
     * retrieve this value by calling [getString].
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key   Key used to add given [value].
     * @param value Value to add after given [key].
     */

    override fun setString(key: String, value: String): Unit {
        runBlocking(Dispatchers.IO) {
            val cipher = encryptToBase64(value)
            dataStore.edit { prefs ->
                prefs[prefsKey(key)] = cipher
            }
        }
    }

    /**
     * Assigns given [value] to device storage - shared preferences given [key]. You can
     * retrieve this value by calling [getString].
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key   Key used to add given [value].
     * @param value Value to add after given [key].
     */
    override fun setLong(key: String, value: Long): Unit = runBlocking(Dispatchers.IO) {
        val cipher = encryptToBase64(value.toString())
        dataStore.edit { it[prefsKey(key)] = cipher }
    }

    /**
     * Assigns given [value] to device storage - shared preferences given [key]. You can
     * retrieve this value by calling [getString].
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key   Key used to add given [value].
     * @param value Value to add after given [key].
     */
    override fun setBool(key: String, value: Boolean): Unit {
        runBlocking(Dispatchers.IO) {
            val cipher = encryptToBase64(value.toString())
            dataStore.edit { prefs ->
                prefs[prefsKey(key)] = cipher
            }
        }
    }

    /**
     * Sets an integer value in the shared preferences.
     *
     * @param key The key under which the value should be stored.
     * @param value The integer value to be stored.
     */
    override fun setInt(key: String, value: Int): Unit {
        runBlocking(Dispatchers.IO) {
            val cipher = encryptToBase64(value.toString())
            dataStore.edit { prefs ->
                prefs[prefsKey(key)] = cipher
            }
        }
    }

    /**
     * Retrieves a string value from device shared preferences that corresponds to given [key]. If
     * key does not exist or value of given key is null, [defaultValue] is returned.
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key          Key to get corresponding value.
     * @param defaultValue Default value to return if given [key] does not exist in prefs or if
     * key value is invalid.
     */

    override fun getString(key: String, defaultValue: String): String = runBlocking(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        val base64 = prefs[prefsKey(key)] ?: return@runBlocking defaultValue
        decryptFromBase64(base64) ?: defaultValue
    }

    /**
     * Retrieves a long value from device shared preferences that corresponds to given [key]. If
     * key does not exist or value of given key is null, [defaultValue] is returned.
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key          Key to get corresponding value.
     * @param defaultValue Default value to return if given [key] does not exist in prefs or if
     * key value is invalid.
     */
    override fun getLong(key: String, defaultValue: Long): Long = runBlocking(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        val base64 = prefs[prefsKey(key)] ?: return@runBlocking defaultValue
        val dec = decryptFromBase64(base64) ?: return@runBlocking defaultValue
        dec.toLongOrNull() ?: defaultValue
    }


    /**
     * Retrieves a boolean value from device shared preferences that corresponds to given [key]. If
     * key does not exist or value of given key is null, [defaultValue] is returned.
     *
     * Shared preferences are encrypted. Do not create your own instance to add or retrieve data.
     * Instead, call operations of this controller.
     *
     * @param key          Key to get corresponding value.
     * @param defaultValue Default value to return if given [key] does not exist in prefs or if
     * key value is invalid.
     */

    override fun getBool(key: String, defaultValue: Boolean): Boolean = runBlocking(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        val base64 = prefs[prefsKey(key)] ?: return@runBlocking defaultValue
        val dec = decryptFromBase64(base64) ?: return@runBlocking defaultValue
        dec.toBooleanStrictOrNull() ?: defaultValue
    }

    /**
     * Retrieves an integer value from SharedPreferences associated with the given key.
     * If no value is found for the key, returns the provided default value.
     *
     * @param key The key associated with the integer value to retrieve.
     * @param defaultValue The default integer value to return if no value is found for the key.
     * @return The integer value associated with the key, or the default value if no value is found.
     */
    override fun getInt(key: String, defaultValue: Int): Int = runBlocking(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        val base64 = prefs[prefsKey(key)] ?: return@runBlocking defaultValue
        val dec = decryptFromBase64(base64) ?: return@runBlocking defaultValue
        dec.toIntOrNull() ?: defaultValue
    }

}



interface PrefKeys {
    fun getAlias(): String
    fun setAlias(value: String)

    fun getECAlias(): String

    fun setECAlias(value: String)

    fun getShowBatchIssuanceCounter(): Boolean
    fun setShowBatchIssuanceCounter(value: Boolean)
}

class PrefKeysImpl(
    private val prefsController: PrefsController
) : PrefKeys {


    /**
     * Returns the biometric alias in order to find the biometric secret key in android keystore.
     */
    override fun getAlias(): String {
        return prefsController.getString("Alias", "")
    }

    /**
     * Stores the biometric alias used for the secret key in android keystore.
     *
     * @param value the biometric alias value.
     */
    override fun setAlias(value: String) {
        prefsController.setString("Alias", value)
    }

    /**
     * Returns the did alias in order to find the biometric secret key in android keystore.
     */
    override fun getECAlias(): String {
        return prefsController.getString("ECAlias", "")
    }

    /**
     * Stores the did alias used for the secret key in android keystore.
     *
     * @param value the did alias value.
     */
    override fun setECAlias(value: String) {
        prefsController.setString("ECAlias", value)
    }

    /**
     * Retrieves the preference for showing the batch issuance counter.
     *
     * @return `true` if the batch issuance counter should be shown, `false` otherwise.
     *         Defaults to `false` if the preference is not set.
     */
    override fun getShowBatchIssuanceCounter(): Boolean {
        return prefsController.getBool("ShowBatchIssuanceCounter", false)
    }

    /**
     * Sets the preference for showing the batch issuance counter.
     *
     * @param value `true` to show the counter, `false` to hide it.
     */
    override fun setShowBatchIssuanceCounter(value: Boolean) {
        prefsController.setBool("ShowBatchIssuanceCounter", value)
    }
}