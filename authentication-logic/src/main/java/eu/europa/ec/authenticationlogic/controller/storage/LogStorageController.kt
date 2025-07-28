package eu.europa.ec.authenticationlogic.controller.storage

import eu.europa.ec.authenticationlogic.config.StorageConfig

interface LogStorageController {
    fun retrieveLogKey(): String
    fun setLogKey(key: String)
    fun isLogKeyValid(key: String): Boolean

}

class LogStorageControllerImpl(private val storageConfig: StorageConfig) : LogStorageController {
    override fun retrieveLogKey(): String = storageConfig.logStorageProvider.retrieveLogKey()

    override fun setLogKey(key: String) {
        storageConfig.logStorageProvider.setLogKey(key)
    }

    override fun isLogKeyValid(key: String): Boolean = storageConfig.logStorageProvider.isLogKeyValid(key)
}