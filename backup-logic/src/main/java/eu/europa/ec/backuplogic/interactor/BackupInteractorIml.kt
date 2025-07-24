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
import eu.europa.ec.backuplogic.controller.model.RestoreStatus
import eu.europa.ec.storagelogic.model.BackupLog
import java.io.File
import java.io.InputStream

interface BackupInteractor {
    suspend fun exportBackup(): File?

    suspend fun getLastBackup(): BackupLog?

    fun existBackup(): Boolean
    suspend fun restoreWallet(file: InputStream, passPhrase: List<String>): List<String>
    suspend fun deleteWallet(identifier: String): Boolean
    fun initListWordsPreview(): List<String>

    fun generateQuiz(): Triple<List<String>, List<String>, List<Int>>

    fun getQuizSlots(): List<String>

    fun finalizeRestore(options: List<String>): RestoreStatus

    fun cacheWords(words: List<String>)

    fun getCachedWords(): List<String>
}

class BackupInteractorIml (
    private val listWordsController: ListWordsController,
    private val backupController: BackupController
): BackupInteractor {

    companion object{
        private const val COUNT_TAKE = 12
        private const val WORDS_TO_GUESS = 4
        private const val WALLET_NAME = "wallet-dev"

        private var _cachedWords: MutableList<String>? = null
    }


    override fun initListWordsPreview(): List<String> {
        val generated = listWordsController.generateOrderByListWords(COUNT_TAKE)
        _cachedWords = generated.toMutableList()
        return _cachedWords!!
    }
    override suspend fun exportBackup(): File? {
        println("Phrase to exportBackup: $_cachedWords")
        return if (_cachedWords?.size == COUNT_TAKE) {
            val backupFile = backupController.exportBackup(_cachedWords!!, WALLET_NAME)
            _cachedWords?.clear()
            backupFile
        } else {
            println("Passphrase incomplete or invalid.")
            null
        }
    }

    override suspend fun getLastBackup(): BackupLog? {
        return backupController.getLastBackup()
    }

    override fun existBackup(): Boolean {
        return backupController.existBackupMkdir()
    }

    override suspend fun restoreWallet(file: InputStream, passPhrase: List<String>): List<String> {
        println("Phrase password restore wallet file $passPhrase")
        println("Restore wallet filename: ${file.read()}")
        if (passPhrase.isEmpty() || passPhrase.any { it.isBlank() }) {
            return emptyList()
        }
        return backupController.restoreBackup(file, passPhrase)

    }

    override suspend fun deleteWallet(identifier: String): Boolean {
      return backupController.deleteBackup(identifier)
    }

    override fun cacheWords(words: List<String>) {
        _cachedWords = words.toMutableList()
    }

    override fun getCachedWords(): List<String> {
        return _cachedWords ?: emptyList()
    }


    override fun getQuizSlots(): List<String> {
        return _cachedWords ?: emptyList()
    }

    override fun finalizeRestore(options: List<String>): RestoreStatus {
        TODO("Not yet implemented")
    }


    override fun generateQuiz(): Triple<List<String>, List<String>, List<Int>> {
        println(">>> generateQuiz: list=$_cachedWords")
        val list = _cachedWords ?: throw IllegalStateException("Must call initListWordsPreview() first.")
        val indicesToRemove = (0 until list.size).shuffled().take(WORDS_TO_GUESS).sorted()
        val slots = list.mapIndexed { index, word ->
            if (index in indicesToRemove) "" else word
        }
        val removedWords = indicesToRemove.map { list[it] }
        println(">>> generateQuiz: indicesToRemove=$indicesToRemove")
//        _cachedWords = slots.toMutableList()
        println(">>> generateQuiz: list=$_cachedWords, indicesToRemove=$indicesToRemove, slots=$slots, removedWords=$removedWords")
        return Triple(slots, listWordsController.shuffleRandomQuizSlots(removedWords), indicesToRemove)
    }


}