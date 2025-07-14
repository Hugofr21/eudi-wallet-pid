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

package eu.europa.ec.backuplogic.interactor

import eu.europa.ec.backuplogic.controller.ListWordsController
import eu.europa.ec.backuplogic.controller.ListWordsControllerImpl
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.serializer.UiSerializer

interface BackupInteractor {
    suspend fun backupWallet(): String
    suspend fun restoreWallet(backupData: String): Boolean
    suspend fun deleteWallet(): Boolean
    fun getListWords(): List<String>

    fun generateQuiz(list: List<String>): Triple<List<String>, List<String>, List<Int>>

    fun getQuizSlots(): List<String>
}

class BackupInteractorIml (
    private val uiSerializer: UiSerializer,
    private val resourceProvider: ResourceProvider,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val listWordsController: ListWordsController
): BackupInteractor {

    companion object{
        private var lastQuiz: MutableList<String> = mutableListOf()
        private var countTake: Int = 12
        private var wordToGuess: Int = 4
    }

    override suspend fun backupWallet(): String {
        TODO("Not yet implemented")
    }

    override suspend fun restoreWallet(backupData: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun deleteWallet(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getListWords(): List<String> {
        return listWordsController.generateOrderByListWords(countTake)
    }

    override fun getQuizSlots(): List<String> {
        return lastQuiz
    }

    override fun generateQuiz(list: List<String>): Triple<List<String>, List<String>, List<Int>> {
        val originalList = list.shuffled()
        val indicesToRemove = (0 until originalList.size).shuffled().take(wordToGuess)
        val slots = originalList.mapIndexed { index, word ->
            if (index in indicesToRemove) "" else word
        }
        val removedWords = indicesToRemove.map { originalList[it] }
        lastQuiz = slots.toMutableList()
        return Triple(slots, removedWords, indicesToRemove)
    }


}