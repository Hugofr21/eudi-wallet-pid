package eu.europa.ec.authenticationlogic.provider

interface LogStorageProvider {
        fun retrieveLogKey(): String
        fun setLogKey(key: String)
        fun isLogKeyValid(key: String): Boolean

}