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

package eu.europa.ec.backuplogic.ui.quizPhraseWords


import eu.europa.ec.backuplogic.interactor.BackupInteractor
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import org.koin.android.annotation.KoinViewModel

sealed class State : ViewState {
    object Loading : State()
    data class DisplayAll(
        val originalList: List<String>,
        val fullList: List<String>,
        val quizWords: List<String>,
        val indexRemove: List<Int>
    ) : State()
    data class VerifyOrder(
        val originalList: List<String>,
        val fullList: List<String>,
        val quizWords: List<String>,
        val indexRemove: List<Int>
    ) : State()
}

sealed class Event : ViewEvent {
    object Skip : Event()
    data class PlaceWord(val word: String, val index: Int) : Event()
    object GoBack : Event()

    object ResetWords : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        object Pop : Navigation()
    }
    object Success : Effect()
    object Error : Effect()
}


@KoinViewModel
class QuizPhraseWordsViewModel(
    private val interactor: BackupInteractor
) : MviViewModel<Event, State, Effect>() {

    companion object {
        private var originalList = listOf<String>()
        private var initialSlots = listOf<String>()
        private var initialRemovedWords = listOf<String>()
        private var initialIndicesRemoved = listOf<Int>()
    }

    override fun setInitialState(): State {
        originalList = interactor.getListWords()
        val (slots, removedWords, indicesRemoved) = interactor.generateQuiz(originalList)
//        println("Original list: $originalList")
//        println("Slots: $slots")
//        println("Removed words: $removedWords")
//        println("Indices removed: $indicesRemoved")
        initialSlots = slots
        initialRemovedWords = removedWords
        initialIndicesRemoved = indicesRemoved

        return State.DisplayAll(
            originalList = originalList,
            fullList = slots,
            quizWords = removedWords,
            indexRemove = indicesRemoved
        )
    }


    override fun handleEvents(event: Event) {
        val s = viewState.value
        when (s) {
            is State.DisplayAll -> when (event) {
                Event.Skip -> setState { State.VerifyOrder(s.originalList, s.fullList, s.quizWords, s.indexRemove) }
                is Event.PlaceWord -> {
                    val newSlots = s.fullList.toMutableList()
                    newSlots[event.index] = event.word
//                    println("New slots: $newSlots")
                    val newQuizWords = s.quizWords.toMutableList().apply {
                        remove(event.word)
                    }
//                    println("New quiz words: $newQuizWords")
                    setState {
                        s.copy(
                            fullList = newSlots,
                            quizWords = newQuizWords
                        )
                    }
                }
                Event.ResetWords -> {
//                    println(">>> ResetWords: Restoring initial state")
//                    println(">>> Original list: $originalList")
//                    println(">>> Initial slots: $initialSlots")
//                    println(">>> Initial removed words: $initialRemovedWords")
//                    println(">>> Initial indices removed: $initialIndicesRemoved")
                    setState {
                        State.DisplayAll(
                            originalList = originalList,
                            fullList = initialSlots,
                            quizWords = initialRemovedWords,
                            indexRemove = initialIndicesRemoved
                        )
                    }
                }
                else -> {}
            }
            is State.VerifyOrder -> when (event) {
                Event.Skip -> {
                    println("Incorrect word order. Please try again.")
                    println("Original list: $originalList")
                    println("Full list: ${s.fullList}")
                    if (s.fullList == s.originalList) setEffect { Effect.Success }
                    else setEffect { Effect.Error }
                }
                Event.GoBack -> setEffect { Effect.Navigation.Pop }
                else -> {}
            }
            else -> {}
        }
    }
}