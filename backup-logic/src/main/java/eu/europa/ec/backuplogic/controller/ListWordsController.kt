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
import java.security.SecureRandom
import kotlin.random.Random

interface ListWordsController{
    fun generateOrderByListWords(count: Int): List<String>
    fun shuffleRandomQuizSlots(slots: List<String>):List<String>
}

/*
* List of Words Controller: Enable a Passphrase (BIP-39 Optional Word)
* Dictionary Attacks: When Random Isn’t Random Enough
*/

class ListWordsControllerImpl(
    private val context: Context,
): ListWordsController{

    override fun generateOrderByListWords(count: Int): List<String> {
        val listWords = listWords().toMutableList()
        val numberSecured = SecureRandom()
        val randomListWords = listWords.shuffled(numberSecured)
        return randomListWords.take(count)
    }

    override  fun shuffleRandomQuizSlots(slots: List<String>): List<String> {
        val shuffled = slots.toMutableList()

        val rnd = SecureRandom.getInstanceStrong()

        for (i in slots.size -1 downTo 1 ){
            val j = rnd.nextInt(i  + 1)
            val tmp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = tmp

        }
        return shuffled.toList()
    }

    private fun listWords(): List<String> {
        val fileContent = context.assets.open("words.txt").bufferedReader().use { it.readText() }
        return fileContent.split("\n")
            .filter { it.isNotBlank() }
            .map { it.trim() }
            .filter { it.length >= 3 }
    }


}
