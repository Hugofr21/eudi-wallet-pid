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

package eu.europa.ec.corelogic.config

import android.content.Context
import eu.europa.ec.corelogic.BuildConfig
import eu.europa.ec.corelogic.util.BuildConfigConstant
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import eu.europa.ec.eudi.wallet.transfer.openId4vp.ClientIdScheme
import eu.europa.ec.eudi.wallet.transfer.openId4vp.EncryptionAlgorithm
import eu.europa.ec.eudi.wallet.transfer.openId4vp.EncryptionMethod
import eu.europa.ec.eudi.wallet.transfer.openId4vp.Format
import eu.europa.ec.resourceslogic.R


internal class WalletCoreConfigImpl(
    private val context: Context
) : WalletCoreConfig {

    private companion object {
        const val AUTHENTICATION_REQUIRED = false

        // verifier
        const val VERIFIER_URL = "https://verifier.eudiw.dev/home"
        // Proof age verification
        const val VERIFIER_URL_AGE_VERIFICATION = "https://verifier.ageverification.dev/"
    }

    private var _config: EudiWalletConfig? = null

    override val config: EudiWalletConfig
        get() {
            if (_config == null) {
                _config = EudiWalletConfig {
                    configureDocumentKeyCreation(
                        userAuthenticationRequired = AUTHENTICATION_REQUIRED,
                        userAuthenticationTimeout = 30_000L, // 30s
                        useStrongBoxForKeys = true
                    )
                    configureOpenId4Vp {
                        withEncryptionAlgorithms(listOf(EncryptionAlgorithm.ECDH_ES))
                        withEncryptionMethods(
                            listOf(
                                EncryptionMethod.A128CBC_HS256,
                                EncryptionMethod.A256GCM,
                                EncryptionMethod.A256CBC_HS512,
                                EncryptionMethod.A192CBC_HS384
                            )
                        )

                        withClientIdSchemes(
                            listOf(ClientIdScheme.X509SanDns)
                        )
                        withSchemes(
                            listOf(
                                BuildConfig.OPENID4VP_SCHEME,
                                BuildConfig.EUDI_OPENID4VP_SCHEME,
                                BuildConfig.MDOC_OPENID4VP_SCHEME,
                                BuildConfigConstant.AV_OPENID4VP_SCHEME,
                                BuildConfigConstant.AVSP_SCHEME
                            )
                        )
                        withFormats(
                            Format.MsoMdoc,
                            Format.SdJwtVc.ES256,
                        )
                    }

//                    VCI_ISSUER_URLS.forEach { issuerUrl ->
//                        configureOpenId4Vci {
//                            withIssuerUrl(issuerUrl = issuerUrl)
//                            withClientId(clientId = VCI_CLIENT_ID)
//                            withAuthFlowRedirectionURI(BuildConfig.ISSUE_AUTHORIZATION_DEEPLINK)
//                            withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
//                            withUseDPoPIfSupported(true)
//                        }
//                    }


                    // reader C.A
                    configureReaderTrustStore(
                        context,
                        R.raw.pidissuerca02_cz,
                        R.raw.pidissuerca02_ee,
                        R.raw.pidissuerca02_eu,
                        R.raw.pidissuerca02_lu,
                        R.raw.pidissuerca02_nl,
                        R.raw.pidissuerca02_pt,
                        R.raw.pidissuerca02_ut,
                    )
                }
            }
            return _config!!
        }
}