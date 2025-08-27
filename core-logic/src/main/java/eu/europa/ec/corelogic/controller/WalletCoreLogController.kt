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

package eu.europa.ec.corelogic.controller

import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.eudi.wallet.logging.Logger
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class LogEvent(
    val event: String,
    val correlationId: String,
    val component: String,
    val payload: String
)

interface WalletCoreLogController : Logger


class WalletCoreLogControllerImpl(
    private val logController: LogController,
    private val uuidProvider: UuidProvider,
    private val json: Json = Json { encodeDefaults = true }
) : WalletCoreLogController {
    private data class Rule(
        val prefix: String,
        val event: String,
        val component: String
    )

    private val rules = listOf(
        Rule("REQUEST:",  "HttpRequest", "HttpClient"),
        Rule("RESPONSE:", "HttpResponse", "HttpClient"),
        Rule("SYS:",      "SystemEvent", "System"),
        Rule("SEC:",      "SecurityEvent", "Security"),
        Rule("AUTH:",     "AuthEvent", "Authentication")
    )

    override fun log(record: Logger.Record) {
        val raw   = record.message.trim()
        val level = record.level

        println("[${levelName(level)}] $raw")

        val matched = rules.firstOrNull { raw.startsWith(it.prefix) }


        if (matched != null) {
            val payload = raw.removePrefix(matched.prefix)
            val corrId  = uuidProvider.provideUuid()
            val event   = LogEvent(
                event = matched.event,
                correlationId = corrId,
                component = matched.component,
                payload = payload
            )

            logController.d(matched.component) {
                json.encodeToString(event)
            }
            return
        }
        val component = "Generic"

        when (level) {
            Logger.LEVEL_ERROR -> record.thrown
                ?.let { logController.e(component, it) }
                ?: logController.e(component) { raw }

            Logger.LEVEL_INFO  -> logController.i(component) { raw }
            Logger.LEVEL_DEBUG -> logController.d(component) { raw }
            else               -> logController.d(component) { raw }
        }

    }

    private fun levelName(level: Int): String = when (level) {
        Logger.LEVEL_ERROR -> "ERROR"
        Logger.LEVEL_INFO  -> "INFO"
        Logger.LEVEL_DEBUG -> "DEBUG"
        else               -> "DEBUG"
    }
}
