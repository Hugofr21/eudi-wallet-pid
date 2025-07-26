package eu.europa.ec.corelogic.provider

import eu.europa.ec.corelogic.model.CredentialIssuerMetadataDefault

interface DefaultOpenId4VciManagerInterface {
    suspend fun getIssuerMetadataDefault(): Result<CredentialIssuerMetadataDefault>
}