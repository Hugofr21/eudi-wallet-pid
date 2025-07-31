package eu.europa.ec.corelogic.service

import eu.europa.ec.businesslogic.config.ConfigLogic
import okhttp3.OkHttpClient

/**
 *  Electronic Signatures and Trust Infrastructures (ESI);
 *  Trusted Lists
 *  ETSI TS 119 612 V2.3.1 (2024-11)
    https://ec.europa.eu/tools/lotl/eu-lotl.xml
 */
class LotlService(
    private val client: OkHttpClient,
    private val configLogic: ConfigLogic
) {


}