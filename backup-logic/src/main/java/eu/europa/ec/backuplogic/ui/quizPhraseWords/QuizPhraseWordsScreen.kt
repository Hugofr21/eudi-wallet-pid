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

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.StickyBottomConfig
import eu.europa.ec.uilogic.component.wrap.StickyBottomType
import eu.europa.ec.uilogic.component.wrap.TextConfig
import eu.europa.ec.uilogic.component.wrap.WrapStickyBottomContent
import eu.europa.ec.uilogic.component.wrap.WrapText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import eu.europa.ec.backuplogic.ui.quizPhraseWords.compoment.ResetButton
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import eu.europa.ec.backuplogic.ui.quizPhraseWords.model.DraggedItem
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.text.isEmpty

val LightSkyBlue   = Color(0xFFCAE6FD)
val OceanBlue      = Color(0xFF2A5ED9)
val DeepBlue       = Color(0xFF0048D2)
val SoftYellow     = Color(0xFFFFF1BA)
val CoralRed       = Color(0xFFFF6E70)

@Composable
fun QuizPhraseWordsScreen(navController: NavController, viewModel: QuizPhraseWordsViewModel) {
    val state = viewModel.viewState.collectAsStateWithLifecycle()
    val effectFlow = viewModel.effect

    val configButton = ButtonConfig(
        type = ButtonType.PRIMARY,
        onClick = {
            when(state)
            {
                is State.VerifyOrder -> viewModel.setEvent(Event.Skip)
                else -> {

                }
            }

        }
    )

    ContentScreen(
        isLoading = false,
        navigatableAction = ScreenNavigateAction.BACKABLE,
        onBack = { viewModel.setEvent(Event.GoBack) },

        stickyBottom = { paddingValues ->
            ContinueButton(
                paddingValues = paddingValues,
                config = configButton
            )
        })
    { paddingValues ->
        NavigationSlider(
            paddingValues = paddingValues,
            effectFlow = effectFlow,
            onNavigationRequested = { handleNavigationEffect(it, navController) },
            state = state.value,
            viewModel = viewModel
        )
    }

}

@Composable
private fun ContinueButton(
    paddingValues: PaddingValues,
    config: ButtonConfig,
) {
    WrapStickyBottomContent(
        stickyBottomModifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues),
        stickyBottomConfig = StickyBottomConfig(
            type = StickyBottomType.OneButton(config = config), showDivider = false
        )
    ) {
        Text(text = stringResource(R.string.backup_screen_skip_button))
    }
}


@Composable
private fun NavigationSlider(
    paddingValues: PaddingValues,
    effectFlow: Flow<Effect>,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    state: State,
    viewModel: QuizPhraseWordsViewModel
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues)
    ) {
        MainContent(
            paddingValues = paddingValues,
            state =  state,
            viewModel = viewModel
        )
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
                Effect.Error -> {
                    Toast.makeText(context, "Incorrect word order. Please try again.", Toast.LENGTH_SHORT).show()
                }
                Effect.Success -> {
                    onNavigationRequested(Effect.Navigation.Pop)
                }
            }
        }.collect()
    }
}


private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
) {
    when (navigationEffect) {
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute)
        }

        is Effect.Navigation.Pop -> {
            navController.popBackStack()
        }
    }
}

@Composable
fun MainContent(
    paddingValues: PaddingValues,
    state: State,
    viewModel: QuizPhraseWordsViewModel
) {
    var draggedWord by remember { mutableStateOf<DraggedItem?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val slotBounds = remember { mutableStateMapOf<Int, Rect>() }
    val wordBounds = remember { mutableStateMapOf<String, Offset>() }
    val fullList = (state as State.DisplayAll).fullList
    val quizWords = (state as State.DisplayAll).quizWords
    var startOffset by remember { mutableStateOf(Offset.Zero) }
    val gridState = rememberLazyGridState()
    val placedWords = fullList.filter { it.isNotEmpty() }.toSet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {

        WrapText(
            text = stringResource(R.string.recovery_backup_content_title),
            textConfig = TextConfig(
                style = MaterialTheme.typography.titleLarge.merge(
                    TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 32.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.02).sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            )
        )
        VSpacer.ExtraSmall()
        WrapText(
            text = stringResource(R.string.recovery_backup_mnemonic_description),
            textConfig = TextConfig(
                style = MaterialTheme.typography.titleLarge.merge(
                    TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        letterSpacing = (-0.02).sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            )
        )
        VSpacer.ExtraLarge()

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(fullList) { index, slot ->
                val modifierWithBounds = Modifier
                    .onGloballyPositioned { coords ->
                        val scrollOffset = gridState.firstVisibleItemScrollOffset.toFloat()
                        val bounds = coords.boundsInWindow()
                        slotBounds[index] = Rect(
                            bounds.left,
                            bounds.top - scrollOffset,
                            bounds.right,
                            bounds.bottom - scrollOffset
                        )
//                        println(">>> Slot[$index] bounds: ${slotBounds[index]}")
                    }

                if (slot.isEmpty()) {
                    Box(
                        modifier = modifierWithBounds
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(CoralRed, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = slot.ifEmpty { stringResource(R.string.backup_slot_placeholder) },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = slot,
                        modifier = modifierWithBounds
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(LightSkyBlue,
                                RoundedCornerShape(0.dp))
                            .padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            quizWords.forEachIndexed { originIdx, word ->
                key(word) {
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                // Store word position
                                wordBounds[word] = coords.boundsInWindow().topLeft
                                if (draggedWord?.word == word) {
                                    startOffset = coords.boundsInWindow().topLeft
//                                    println(">>> Setting startOffset for word $word: $startOffset")
                                }
                            }
                            .offset {
                                if (draggedWord?.word == word) {
                                    IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt())
                                } else {
                                    IntOffset.Zero
                                }
                            }
                            .background(
                                if (placedWords.contains(word)) DeepBlue
                                else SoftYellow,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        if (!placedWords.contains(word)) {
                                            draggedWord = DraggedItem(
                                                word = word,
                                                originIndex = originIdx,
                                                fromQuizWords = true
                                            )
                                            dragOffset = Offset.Zero
                                            startOffset = wordBounds[word] ?: Offset.Zero
//                                            println(">>> onDragStart: draggedWord=$draggedWord, startOffset=$startOffset")
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (draggedWord?.word == word) {
                                            change.consume()
                                            dragOffset += dragAmount
//                                            println(">>> onDrag: dragAmount=$dragAmount, dragOffset=$dragOffset")
                                        }
                                    },
                                    onDragEnd = {
                                        if (draggedWord?.word == word) {
                                            draggedWord?.let { item ->
                                                // Calculate drop position
                                                val dropPosition = startOffset + dragOffset

//                                                println(">>> fullList: $fullList")
//                                                println(">>> startOffset: $startOffset")
//                                                println(">>> dragOffset: $dragOffset")
//                                                println(">>> dropPosition: $dropPosition")
//                                                println(">>> slotBounds: $slotBounds")

                                                if (slotBounds.isEmpty()) {
//                                                    println(">>> Error: slotBounds is empty, cannot release the word")
                                                    return@let
                                                }

//                                                slotBounds.forEach { (idx, rect) ->
//                                                    println("Slot[$idx] rect: $rect, contains dropPosition? ${rect.contains(dropPosition)}")
//                                                    if (idx >= 0 && idx < fullList.size) {
//                                                        println("fullList[$idx]: ${fullList[idx]}, isEmpty: ${fullList[idx].isEmpty()}")
//                                                    } else {
//                                                        println("Error: index $idx outside the limits of fullList (size: ${fullList.size})")
//                                                    }
//                                                }

                                                val targetSlot = slotBounds.entries
                                                    .firstOrNull { (index, rect) ->
                                                        val isValidIndex = index >= 0 && index < fullList.size
                                                        val isEmpty = isValidIndex && fullList[index].isEmpty()
                                                        val containsPosition = rect.contains(dropPosition)
//                                                        println("Slot[$index] validIndex: $isValidIndex, isEmpty: $isEmpty, containsPosition: $containsPosition")
                                                        isEmpty && containsPosition
                                                    }?.key

                                                if (targetSlot != null) {
//                                                    println(">>> Word ${item.word} placed in the slot $targetSlot")
                                                    viewModel.setEvent(
                                                        Event.PlaceWord(
                                                            item.word,
                                                            targetSlot
                                                        )
                                                    )
                                                } else {
                                                    println(">>> No valid slots found for dropPosition: $dropPosition")
                                                }
                                            }
                                            // Reset drag state
                                            draggedWord = null
                                            dragOffset = Offset.Zero
                                            startOffset = Offset.Zero
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = word,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        VSpacer.ExtraSmall()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            ResetButton(viewModel)
        }
    }
}

