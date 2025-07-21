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

import eu.europa.ec.backuplogic.controller.BackupController
import eu.europa.ec.backuplogic.controller.ListWordsController
import eu.europa.ec.backuplogic.controller.ListWordsControllerImpl
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.serializer.UiSerializer
import java.io.File

interface BackupInteractor {
    suspend fun exportBackup(): File?

    fun existBackup(): Boolean
    suspend fun restoreWallet(backupData: String): Boolean
    suspend fun deleteWallet(): Boolean
    fun getListWords(): List<String>

    fun generateQuiz(list: List<String>): Triple<List<String>, List<String>, List<Int>>

    fun getQuizSlots(): List<String>
    fun validateRecoveryPhrase(words: List<String>): Boolean
}

class BackupInteractorIml (
    private val listWordsController: ListWordsController,
    private val backupController: BackupController
): BackupInteractor {

    companion object{
        private var lastQuiz: MutableList<String> = mutableListOf()

        private var listPhrase: MutableList<String> = mutableListOf()
        private var countTake: Int = 12
        private var wordToGuess: Int = 4

        private val myWalletnameProvide = "wallet-dev"
    }

    override suspend fun exportBackup(): File? {
        print("Phrase expor tBackup $listPhrase")
        return if (listPhrase.size == countTake) {
            val backupFile = backupController.exportBackup(listPhrase, myWalletnameProvide)
            lastQuiz.clear()
            listPhrase.clear()
            backupFile
        } else {
            println("Passphrase contains invalid words.")
            null
        }

    }

    override fun existBackup(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun restoreWallet(backupData: String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun deleteWallet(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getListWords(): List<String> {
        listPhrase = listWordsController.generateOrderByListWords(countTake)
                as MutableList<String>
        return listPhrase
    }

    override fun getQuizSlots(): List<String> {
        return lastQuiz
    }

    override fun validateRecoveryPhrase(words: List<String>): Boolean {
        TODO("Not yet implemented")
    }

    override fun generateQuiz(list: List<String>): Triple<List<String>, List<String>, List<Int>> {
        println(">>> generateQuiz: list=$list")
        val indicesToRemove = (0 until list.size).shuffled().take(wordToGuess).sorted()
        println(">>> generateQuiz: indicesToRemove=$indicesToRemove")
        val slots = list.mapIndexed { index, word ->
            if (index in indicesToRemove) "" else word
        }
        val removedWords = indicesToRemove.map { list[it] }
        lastQuiz = slots.toMutableList()
        println(">>> generateQuiz: list=$list, indicesToRemove=$indicesToRemove, slots=$slots, removedWords=$removedWords")
        return Triple(slots, removedWords, indicesToRemove)
    }


}