package eu.europa.ec.corelogic.config

import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.resourceslogic.BuildConfig

data class Issuer(val url:String, val buildConfig: String)


object OpenId4VciModule {
     val issuersList = listOf(
        Issuer("https://issuer.eudiw.dev", BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK),
        Issuer("https://issuer.ageverification.dev", BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
    )
    val clientId = "wallet-dev"

}

class OpenId4VciManagerRegistry(
    private val eudiWallet: EudiWallet,
    issuers: List<Issuer>,
    private val clientId: String = "wallet-dev",
    private val useDPoP: Boolean = true
) {

    val managers: Map<Issuer, OpenId4VciManager> by lazy {
        issuers.associateWith { issuer ->
            println("[OpenId4VciManagerRegistry] Creating manager for issuer: ${issuer.url}")
            val config = OpenId4VciManager.Config.Builder()
                .withIssuerUrl(issuer.url)
                .withClientId(clientId)
                .withAuthFlowRedirectionURI(issuer.buildConfig)
                .withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
                .withUseDPoPIfSupported(useDPoP)
                .build()
            eudiWallet.createOpenId4VciManager(config)
        }
    }


    fun getManager(issuer: Issuer): OpenId4VciManager =
        managers[issuer]
            ?: throw IllegalArgumentException("Issuer not registered: ${issuer.url}")


    fun allIssuers(): List<Issuer> = managers.keys.toList()


    fun allManagers(): Map<Issuer, OpenId4VciManager> = managers
}