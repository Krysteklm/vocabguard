package com.vocabguard.lock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocabguard.domain.QuizQuestion
import com.vocabguard.sound.LocalSoundPlayer
import com.vocabguard.sound.SoundPlayer
import com.vocabguard.ui.components.rememberPressScale
import com.vocabguard.ui.components.rememberReducedMotion
import com.vocabguard.ui.theme.VGColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SHAKE_KEYFRAMES = listOf(-14f, 12f, -9f, 7f, -4f, 2f, 0f)

@Composable
fun LockScreenRoute(
    viewModel: LockScreenViewModel,
    onUnlocked: (String?) -> Unit,
    onExitHome: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val latestOnUnlocked by rememberUpdatedState(onUnlocked)
    val latestOnExitHome by rememberUpdatedState(onExitHome)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LockEvent.Unlocked -> latestOnUnlocked(event.targetPackage)
                LockEvent.ExitToHome -> latestOnExitHome()
            }
        }
    }

    // System back (including predictive back) only shows a hint.
    BackHandler(enabled = true, onBack = viewModel::onBackAttempt)

    LockScreenContent(
        state = state,
        actions = QuizActions(
            onChoice = viewModel::onChoiceSelected,
            onTruth = viewModel::onTrueFalseSelected,
            onTyped = viewModel::onTypedAnswer,
        ),
        onExit = viewModel::onExitRequested,
    )
}

class QuizActions(
    val onChoice: (Int) -> Unit,
    val onTruth: (Boolean) -> Unit,
    val onTyped: (String) -> Unit,
)

@Composable
fun LockScreenContent(
    state: LockUiState,
    actions: QuizActions,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val soundPlayer = LocalSoundPlayer.current
    // One sound per answer. The final correct answer plays the unlock arpeggio instead of the chime.
    LaunchedEffect(state.questionNumber, state.feedback) {
        when (state.feedback) {
            AnswerFeedback.Correct, AnswerFeedback.Almost -> soundPlayer?.play(
                if (state.streak >= state.requiredStreak) SoundPlayer.Sound.Unlock else SoundPlayer.Sound.Correct,
            )
            AnswerFeedback.Wrong -> soundPlayer?.play(SoundPlayer.Sound.Wrong)
            AnswerFeedback.None -> Unit
        }
    }
    LaunchedEffect(state.shakeTrigger) {
        if (state.shakeTrigger > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(state.isUnlocking) {
        if (state.isUnlocking) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val tapActions = remember(actions, haptics) {
        QuizActions(
            onChoice = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); actions.onChoice(it) },
            onTruth = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); actions.onTruth(it) },
            onTyped = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); actions.onTyped(it) },
        )
    }

    // safeDrawing includes the keyboard, so the typing card is never hidden behind it.
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(VGColors.Background)
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LockHeader(state)
                if (!state.limitReached) {
                    Spacer(Modifier.height(28.dp))
                    UnlockProgress(streak = state.streak, required = state.requiredStreak)
                }
            }

            Spacer(Modifier.height(24.dp))

            if (state.limitReached) {
                LimitReachedCard(state)
            } else {
                QuestionArea(state = state, actions = tapActions)
            }

            Spacer(Modifier.height(20.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FeedbackLine(state)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onExit, enabled = !state.isUnlocking && !state.isLeaving) {
                    Text(
                        if (state.limitReached) "Take me home" else "Not now, take me home",
                        color = VGColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LockHeader(state: LockUiState) {
    LockBadge(state)
    Spacer(Modifier.height(20.dp))
    Text(
        text = when {
            state.isUnlocking -> "Unlocked"
            state.limitReached -> "That’s it for today"
            else -> "One quick pause"
        },
        style = MaterialTheme.typography.headlineSmall,
        color = VGColors.TextPrimary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    val subtitle = when {
        state.limitReached -> "You’ve used all of today’s unlocks for ${state.appLabel}."
        state.unlocksLeftToday != null ->
            "Get ${state.requiredStreak} right in a row to open ${state.appLabel}. " +
                "${state.unlocksLeftToday} ${if (state.unlocksLeftToday == 1) "unlock" else "unlocks"} left today."
        else -> "Get ${state.requiredStreak} right in a row to open ${state.appLabel}."
    }
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = VGColors.TextSecondary,
        textAlign = TextAlign.Center,
    )
}

private val WOBBLE_KEYFRAMES = listOf(-14f, 11f, -7f, 4f, -2f, 0f)
private const val BURST_PARTICLES = 16

/**
 * The lock icon: breathes gently while waiting, wobbles on a wrong answer, and bursts into a
 * ring of particles when the app unlocks. All of it is skipped with "Remove animations" on.
 */
@Composable
private fun LockBadge(state: LockUiState) {
    val reduced = rememberReducedMotion()
    val idle = !state.isUnlocking && !state.limitReached && !reduced

    val breathing = rememberInfiniteTransition(label = "lockBreathing")
    val breath by breathing.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lockBreath",
    )

    val wobble = remember { Animatable(0f) }
    LaunchedEffect(state.shakeTrigger) {
        if (state.shakeTrigger == 0 || reduced) return@LaunchedEffect
        for (angle in WOBBLE_KEYFRAMES) wobble.animateTo(angle, tween(55, easing = LinearEasing))
    }

    val burst = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(state.isUnlocking) {
        if (!state.isUnlocking || reduced) return@LaunchedEffect
        burst.snapTo(0f)
        pop.snapTo(0.8f)
        coroutineScope {
            launch { burst.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }

    val iconBackground by animateColorAsState(
        targetValue = when {
            state.isUnlocking -> VGColors.Success.copy(alpha = 0.16f)
            state.limitReached -> VGColors.Warning.copy(alpha = 0.14f)
            else -> VGColors.Primary.copy(alpha = 0.12f)
        },
        label = "lockIconBackground",
    )

    Box(contentAlignment = Alignment.Center) {
        // Drawn without clipping, so the burst can fly outside the 64dp badge.
        Canvas(Modifier.size(64.dp)) {
            val p = burst.value
            if (p <= 0f || p >= 1f) return@Canvas
            val radius = size.minDimension / 2f
            drawCircle(
                color = VGColors.Success.copy(alpha = (1f - p) * 0.6f),
                radius = radius * (1f + p * 1.6f),
                style = Stroke(width = 3.dp.toPx() * (1f - p)),
            )
            repeat(BURST_PARTICLES) { i ->
                val angle = (i * 2 * PI / BURST_PARTICLES + 0.3).toFloat()
                val distance = radius * (1.1f + p * (1.6f + (i % 3) * 0.35f))
                drawCircle(
                    color = (if (i % 2 == 0) VGColors.Success else VGColors.Primary).copy(alpha = 1f - p),
                    radius = 3.dp.toPx() * (1f - p) + 0.5f,
                    center = center + Offset(cos(angle) * distance, sin(angle) * distance),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    val s = (if (idle) breath else 1f) * pop.value
                    scaleX = s
                    scaleY = s
                    rotationZ = wobble.value
                }
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = state.isUnlocking to state.limitReached, label = "lockIcon") { (unlocked, limited) ->
                Icon(
                    imageVector = when {
                        unlocked -> Icons.Rounded.LockOpen
                        limited -> Icons.Rounded.Block
                        else -> Icons.Rounded.Lock
                    },
                    contentDescription = null,
                    tint = when {
                        unlocked -> VGColors.Success
                        limited -> VGColors.Warning
                        else -> VGColors.Primary
                    },
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun UnlockProgress(streak: Int, required: Int) {
    Column(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(required) { index ->
                val filled = index < streak
                val fill by animateFloatAsState(
                    targetValue = if (filled) 1f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                    label = "progressSegment$index",
                )
                // A segment "jumps" when it fills, so each correct answer feels earned.
                val bump = remember { Animatable(1f) }
                LaunchedEffect(filled) {
                    if (filled) {
                        bump.animateTo(1.9f, tween(110))
                        bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { scaleY = bump.value }
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(VGColors.Track),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(50))
                            .background(VGColors.Primary),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = streak,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { h -> if (up) h else -h } + fadeIn())
                        .togetherWith(slideOutVertically { h -> if (up) -h else h } + fadeOut())
                        .using(SizeTransform(clip = false))
                },
                label = "streakCounter",
            ) { value ->
                Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = VGColors.Primary)
            }
            Text(" of $required in a row", style = MaterialTheme.typography.titleMedium, color = VGColors.TextSecondary)
        }
    }
}

/** Snapshot of one card, so the outgoing card keeps its own highlight while animating out. */
private data class QuestionFrame(
    val number: Int,
    val question: QuizQuestion,
    val selectedIndex: Int?,
    val selectedTruth: Boolean?,
    val typedAnswer: String?,
    val feedback: AnswerFeedback,
    val enabled: Boolean,
) {
    val answeredCorrectly: Boolean
        get() = feedback == AnswerFeedback.Correct || feedback == AnswerFeedback.Almost
}

@Composable
private fun QuestionArea(state: LockUiState, actions: QuizActions) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.shakeTrigger) {
        if (state.shakeTrigger == 0) return@LaunchedEffect
        shake.snapTo(0f)
        for (offset in SHAKE_KEYFRAMES) shake.animateTo(offset, tween(durationMillis = 45, easing = LinearEasing))
    }

    Box(
        modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .graphicsLayer { translationX = shake.value.dp.toPx() },
        contentAlignment = Alignment.Center,
    ) {
        val question = state.question
        if (question == null) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = VGColors.Surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VGColors.Primary, strokeWidth = 3.dp)
                }
            }
        } else {
            val frame = QuestionFrame(
                number = state.questionNumber,
                question = question,
                selectedIndex = state.selectedIndex,
                selectedTruth = state.selectedTruth,
                typedAnswer = state.typedAnswer,
                feedback = state.feedback,
                enabled = state.acceptsInput,
            )
            AnimatedContent(
                targetState = frame,
                contentKey = { it.number },
                transitionSpec = {
                    (
                        fadeIn(tween(220, delayMillis = 60)) +
                            slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 4 } +
                            scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.96f)
                        )
                        .togetherWith(fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it / 4 })
                        .using(SizeTransform(clip = false))
                },
                label = "questionCard",
            ) { current ->
                QuestionCard(frame = current, actions = actions)
            }
        }
    }
}

@Composable
private fun QuestionCard(frame: QuestionFrame, actions: QuizActions) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = VGColors.Surface,
        border = BorderStroke(1.dp, VGColors.Outline),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = when (val q = frame.question) {
                    is QuizQuestion.Choice -> if (q.reversed) "Which word means this?" else "What does this mean?"
                    is QuizQuestion.Typing -> "Type the translation"
                    is QuizQuestion.TrueFalse -> "Is this the right translation?"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = VGColors.TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(frame.question.prompt, style = MaterialTheme.typography.headlineLarge, color = VGColors.TextPrimary)
            Spacer(Modifier.height(24.dp))
            when (val q = frame.question) {
                is QuizQuestion.Choice -> ChoiceBody(q, frame, actions.onChoice)
                is QuizQuestion.TrueFalse -> TrueFalseBody(q, frame, actions.onTruth)
                is QuizQuestion.Typing -> TypingBody(frame, actions.onTyped)
            }
        }
    }
}

@Composable
private fun ChoiceBody(question: QuizQuestion.Choice, frame: QuestionFrame, onChoice: (Int) -> Unit) {
    question.options.forEachIndexed { index, option ->
        if (index > 0) Spacer(Modifier.height(12.dp))
        val visual = when {
            frame.feedback == AnswerFeedback.None -> OptionVisual.Idle
            index == frame.selectedIndex -> if (frame.answeredCorrectly) OptionVisual.Correct else OptionVisual.Wrong
            frame.feedback == AnswerFeedback.Wrong && index == question.correctIndex -> OptionVisual.Revealed
            else -> OptionVisual.Dimmed
        }
        OptionButton(text = option, visual = visual, enabled = frame.enabled, onClick = { onChoice(index) })
    }
}

@Composable
private fun TrueFalseBody(question: QuizQuestion.TrueFalse, frame: QuestionFrame, onTruth: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = VGColors.SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Means", style = MaterialTheme.typography.labelMedium, color = VGColors.TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(question.proposedTranslation, style = MaterialTheme.typography.titleLarge, color = VGColors.Primary)
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(true, false).forEach { value ->
            val visual = when {
                frame.feedback == AnswerFeedback.None -> OptionVisual.Idle
                frame.selectedTruth == value -> if (frame.answeredCorrectly) OptionVisual.Correct else OptionVisual.Wrong
                frame.feedback == AnswerFeedback.Wrong && value == question.isMatch -> OptionVisual.Revealed
                else -> OptionVisual.Dimmed
            }
            OptionButton(
                text = if (value) "True" else "False",
                visual = visual,
                enabled = frame.enabled,
                onClick = { onTruth(value) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TypingBody(frame: QuestionFrame, onTyped: (String) -> Unit) {
    var text by rememberSaveable(frame.number) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val submitted = frame.feedback != AnswerFeedback.None
    val canSubmit = frame.enabled && text.isNotBlank()

    LaunchedEffect(frame.number) {
        if (frame.enabled) {
            delay(300) // let the card's enter animation settle before the keyboard opens
            runCatching { focusRequester.requestFocus() }
        }
    }

    val resultColor = when (frame.feedback) {
        AnswerFeedback.Correct, AnswerFeedback.Almost -> VGColors.Success
        AnswerFeedback.Wrong -> VGColors.Error
        AnswerFeedback.None -> VGColors.Outline
    }

    OutlinedTextField(
        value = if (submitted) frame.typedAnswer.orEmpty() else text,
        onValueChange = { if (!submitted && it.length <= 60) text = it },
        enabled = frame.enabled,
        singleLine = true,
        placeholder = { Text("Your answer", color = VGColors.TextSecondary.copy(alpha = 0.6f)) },
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (canSubmit) onTyped(text) }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VGColors.Primary,
            unfocusedBorderColor = VGColors.Outline,
            disabledBorderColor = resultColor,
            cursorColor = VGColors.Primary,
            focusedTextColor = VGColors.TextPrimary,
            unfocusedTextColor = VGColors.TextPrimary,
            disabledTextColor = if (submitted) resultColor else VGColors.TextSecondary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { onTyped(text) },
        enabled = canSubmit,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VGColors.Primary,
            contentColor = VGColors.OnPrimary,
            disabledContainerColor = VGColors.SurfaceHigh,
            disabledContentColor = VGColors.TextSecondary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text("Check", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LimitReachedCard(state: LockUiState) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = VGColors.Surface,
        border = BorderStroke(1.dp, VGColors.Outline),
        modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("Daily limit reached", style = MaterialTheme.typography.titleMedium, color = VGColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "${state.appLabel} opens again tomorrow. You can change this limit in VocabGuard under Apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = VGColors.TextSecondary,
            )
        }
    }
}

private enum class OptionVisual { Idle, Correct, Wrong, Revealed, Dimmed }

@Composable
private fun OptionButton(
    text: String,
    visual: OptionVisual,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorSpec = tween<Color>(durationMillis = 220)
    val container by animateColorAsState(
        targetValue = when (visual) {
            OptionVisual.Idle -> VGColors.SurfaceHigh
            OptionVisual.Correct, OptionVisual.Revealed -> VGColors.Success.copy(alpha = 0.16f)
            OptionVisual.Wrong -> VGColors.Error.copy(alpha = 0.16f)
            OptionVisual.Dimmed -> VGColors.SurfaceHigh.copy(alpha = 0.5f)
        },
        animationSpec = colorSpec,
        label = "optionContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = when (visual) {
            OptionVisual.Idle -> VGColors.Outline
            OptionVisual.Correct, OptionVisual.Revealed -> VGColors.Success
            OptionVisual.Wrong -> VGColors.Error
            OptionVisual.Dimmed -> Color.Transparent
        },
        animationSpec = colorSpec,
        label = "optionBorder",
    )
    val contentColor by animateColorAsState(
        targetValue = when (visual) {
            OptionVisual.Idle -> VGColors.TextPrimary
            OptionVisual.Correct, OptionVisual.Revealed -> VGColors.Success
            OptionVisual.Wrong -> VGColors.Error
            OptionVisual.Dimmed -> VGColors.TextSecondary.copy(alpha = 0.6f)
        },
        animationSpec = colorSpec,
        label = "optionContent",
    )
    val scale by animateFloatAsState(
        targetValue = if (visual == OptionVisual.Correct) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "optionScale",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interaction, pressedScale = 0.96f)

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(18.dp),
        color = container,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .graphicsLayer {
                scaleX = scale * pressScale
                scaleY = scale * pressScale
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = contentColor)
            AnimatedVisibility(
                visible = visual == OptionVisual.Correct || visual == OptionVisual.Wrong || visual == OptionVisual.Revealed,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = fadeOut(),
            ) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (visual == OptionVisual.Wrong) Icons.Rounded.Close else Icons.Rounded.Check,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackLine(state: LockUiState) {
    val question = state.question
    val message: Pair<String, Color>? = when {
        state.isUnlocking ->
            "${state.appLabel} is open for ${state.sessionMinutes} min." to VGColors.Success
        state.feedback == AnswerFeedback.Correct ->
            "Correct. ${state.requiredStreak - state.streak} more to go." to VGColors.Success
        state.feedback == AnswerFeedback.Almost ->
            "Close enough. It’s spelled “${question?.correctAnswer.orEmpty()}”." to VGColors.Success
        state.feedback == AnswerFeedback.Wrong -> when (question) {
            is QuizQuestion.TrueFalse ->
                if (question.isMatch) "That pairing was right. Streak reset." to VGColors.Error
                else "“${question.prompt}” means “${question.correctAnswer}”. Streak reset." to VGColors.Error
            else -> "It’s “${question?.correctAnswer.orEmpty()}”. Streak reset." to VGColors.Error
        }
        state.showBackHint ->
            "Back is off here. Answer, or choose “Not now”." to VGColors.TextSecondary
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = message,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "feedbackLine",
        ) { current ->
            if (current != null) {
                Text(current.first, color = current.second, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            } else {
                Spacer(Modifier.height(1.dp))
            }
        }
    }
}
