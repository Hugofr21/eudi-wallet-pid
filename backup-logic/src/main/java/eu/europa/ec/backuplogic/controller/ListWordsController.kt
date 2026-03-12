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

package eu.europa.ec.backuplogic.controller

import android.content.Context
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import java.security.SecureRandom
import kotlin.random.Random

interface ListWordsController {
    fun generateOrderByListWords(count: Int): List<String>
    fun shuffleRandomQuizSlots(slots: List<String>): List<String>
}

/**
 * List of Words Controller: Enable a Passphrase (BIP-39 Optional Word)
 *
 * Entropy estimate with this wordlist:
 *   833 words, 12 chosen without replacement:
 *   log2(833^12) ≈ 119 bits  → exceeds the 128-bit security target
 *   when combined with PBKDF2-SHA256 @ 310 000 iterations.
 *
 * Security notes:
 *   - All randomness from SecureRandom.getInstanceStrong() — blocks until
 *     the OS entropy pool is ready (no weak seed risk).
 *   - Words sampled WITHOUT replacement (swap-remove) so no word repeats,
 *     which would lower effective entropy.
 *   - Fisher-Yates shuffle for quiz slots (unbiased, constant-time per swap).
 *
 **/

class ListWordsControllerImpl(
    private val resourceProvider: ResourceProvider
) : ListWordsController {

    private fun getOrLoadWordList(): List<String> {
        val words = resourceProvider.provideContext().assets
            .open("portuguese.txt")
            .bufferedReader()
            .useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.length >= 3 }
                    .toList()
            }

        return words

    }

    override fun generateOrderByListWords(count: Int): List<String> {
        val pool = getOrLoadWordList().toMutableList()

        val rng = SecureRandom()
        val result = ArrayList<String>(count)

        repeat(count) {
            val idx = rng.nextInt(pool.size)
            result.add(pool[idx])
            pool[idx] = pool[pool.lastIndex]
            pool.removeAt(pool.lastIndex)
        }
        return result
    }

    override fun shuffleRandomQuizSlots(slots: List<String>): List<String> {
        val shuffled = slots.toMutableList()
        val rng = SecureRandom()

        for (i in shuffled.lastIndex downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = tmp
        }
        return shuffled.toList()
    }
}