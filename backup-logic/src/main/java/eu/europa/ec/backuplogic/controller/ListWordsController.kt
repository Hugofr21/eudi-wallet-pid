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

interface ListWordsController{
    fun generateOrderByListWords(): List<String>
}

class ListWordsControllerImpl(
    private val context: Context,
): ListWordsController{

    override fun generateOrderByListWords(): List<String> {
        val listWords = listWords().toMutableList()
        val numberSecured = SecureRandom()
        val randomListWords = listWords.shuffled(numberSecured)
        return randomListWords
    }



    private fun listWords(): List<String> = listOf(
        "red",
        "orange",
        "yellow",
        "green",
        "blue",
        "gray",
        "black",
        "white",
        "brown",
        "beige"
    )
}
