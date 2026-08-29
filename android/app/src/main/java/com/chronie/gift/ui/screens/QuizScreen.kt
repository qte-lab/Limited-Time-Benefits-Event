package com.chronie.gift.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.chronie.gift.R
import com.chronie.gift.data.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** Event server that serves the quiz + OAuth config endpoints. */
private const val QUIZ_BASE_URL = "http://192.168.10.9:3002"

/**
 * Shared Ktor HTTP client. Reused by [GpcOAuthManager] and [QuizApi] so the
 * whole app keeps a single connection pool.
 */
object ApiClient {
    val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            connectTimeout = 10000
            socketTimeout = 30000
        }
    }
}

/* ------------------------------------------------------------------ */
/* Lightweight inline-markdown renderer (avoids nested scroll containers) */
/* ------------------------------------------------------------------ */

private val INLINE_MD = Regex("(\\*\\*([^*]+)\\*\\*|\\*([^*]+)\\*|`([^`]+)`)")

private fun String.parseInlineMarkdown(): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var last = 0
    for (m in INLINE_MD.findAll(this)) {
        if (m.range.first > last) builder.append(substring(last, m.range.first))
        when {
            m.groupValues[2].isNotEmpty() -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                builder.append(m.groupValues[2])
                builder.pop()
            }
            m.groupValues[3].isNotEmpty() -> {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                builder.append(m.groupValues[3])
                builder.pop()
            }
            m.groupValues[4].isNotEmpty() -> {
                builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                builder.append(m.groupValues[4])
                builder.pop()
            }
        }
        last = m.range.last + 1
    }
    if (last < length) builder.append(substring(last))
    return builder.toAnnotatedString()
}

@Composable
private fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MiuixTheme.textStyles.body2,
    color: Color = MiuixTheme.colorScheme.onSurface
) {
    Text(
        text = text.parseInlineMarkdown(),
        modifier = modifier,
        style = style,
        color = color
    )
}

/* ------------------------------------------------------------------ */
/* Screen entry                                                       */
/* ------------------------------------------------------------------ */

@Composable
fun QuizScreen() {
    Scaffold(
        topBar = {
            SmallTopAppBar(title = stringResource(id = R.string.quiz_title))
        }
    ) { paddingValues ->
        QuizContent(paddingValues = paddingValues)
    }
}

@Composable
private fun QuizContent(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by GpcOAuthManager.state.collectAsState()

    val rootModifier = Modifier
        .fillMaxSize()
        .padding(top = paddingValues.calculateTopPadding())

    when (val s = authState) {
        is GpcAuthState.Unauthorized ->
            AuthGate(error = null, onAuthorize = { scope.launch { openAuthorize(context) } }, modifier = rootModifier)

        is GpcAuthState.Authorizing ->
            AuthorizingView(onRetry = { scope.launch { openAuthorize(context) } }, modifier = rootModifier)

        is GpcAuthState.Error ->
            AuthGate(error = s.message, onAuthorize = { scope.launch { openAuthorize(context) } }, modifier = rootModifier)

        is GpcAuthState.Authorized ->
            QuizView(token = s.token, paddingValues = paddingValues)
    }
}

private suspend fun openAuthorize(context: Context) {
    try {
        val url = GpcOAuthManager.buildAuthorizeUrl(QUIZ_BASE_URL)
        GpcOAuthManager.markAuthorizing()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        GpcOAuthManager.markError(e.message ?: "打开授权页失败")
    }
}

/* ------------------------------------------------------------------ */
/* Authorization gate                                                 */
/* ------------------------------------------------------------------ */

@Composable
private fun AuthGate(error: String?, onAuthorize: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.quiz_auth_required),
            style = MiuixTheme.textStyles.headline1,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.quiz_auth_desc),
            style = MiuixTheme.textStyles.body2,
            textAlign = TextAlign.Center,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(20.dp))
        if (error != null) {
            Text(
                text = error,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }
        PrimaryButton(text = stringResource(R.string.quiz_authorize), onClick = onAuthorize)
    }
}

@Composable
private fun AuthorizingView(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.quiz_authorizing),
            style = MiuixTheme.textStyles.body2,
            textAlign = TextAlign.Center,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = stringResource(R.string.quiz_auth_retry), onClick = onRetry)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Card(
        modifier = modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = enabled,
        onClick = { if (enabled) onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = MiuixTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MiuixTheme.textStyles.body2
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Quiz view                                                          */
/* ------------------------------------------------------------------ */

@Composable
private fun QuizView(token: String, paddingValues: PaddingValues) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var questions by remember { mutableStateOf<List<QuestionPublic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SubmitResponse?>(null) }
    // One-time guard: once the user has submitted the quiz (server-side), the
    // submit button is disabled and a "已提交" banner is shown.
    var alreadySubmitted by remember { mutableStateOf(false) }

    val singleSel = remember { mutableStateMapOf<String, Int>() }
    val multiSel = remember { mutableStateMapOf<String, List<Int>>() }
    val boolSel = remember { mutableStateMapOf<String, Boolean>() }

    fun load() {
        scope.launch {
            isLoading = true
            error = null
            try {
                val qs = QuizApi.fetchQuestions(QUIZ_BASE_URL)
                questions = qs
                val fp = QuizPrefs.fingerprint(qs)
                val saved = QuizPrefs.load(context)
                if (saved.fp == fp) {
                    // Same quiz version: restore the user's previous answers and
                    // any completion recorded earlier (so killing the app loses
                    // nothing).
                    singleSel.putAll(saved.single)
                    multiSel.putAll(saved.multi)
                    boolSel.putAll(saved.bool)
                    if (saved.completed) {
                        alreadySubmitted = true
                        result = SubmitResponse(
                            success = true,
                            totalAwarded = saved.totalAwarded,
                            balance = saved.balance,
                            dailyUsed = saved.dailyUsed,
                            dailyLimit = saved.dailyLimit
                        )
                    }
                } else if (saved.fp.isNotEmpty()) {
                    // A different quiz was deployed: drop stale answers but keep
                    // the completed flag (the server is authoritative anyway).
                    QuizPrefs.resetFor(context, fp, saved.completed)
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    // Reflect the server-side one-time flag across restarts.
    LaunchedEffect(token) {
        try {
            alreadySubmitted = QuizApi.getStatus(QUIZ_BASE_URL, token)
        } catch (_: Exception) {
            // status unavailable (e.g. network) — leave button enabled; the
            // server will still reject a duplicate submit.
        }
    }

    LaunchedEffect(Unit) { load() }

    // Persist answer selections as the user fills them in, so progress survives
    // the app being killed or backgrounded.
    LaunchedEffect(Unit) {
        snapshotFlow {
            QuizPrefs.fingerprint(questions) to Triple(
                singleSel.toMap(),
                multiSel.toMap(),
                boolSel.toMap()
            )
        }.collect { (fp, triple) ->
            if (fp.isNotEmpty()) {
                QuizPrefs.saveSelections(context, fp, triple.first, triple.second, triple.third)
            }
        }
    }

    val buildAnswers: () -> List<AnswerSubmission> = {
        val list = mutableListOf<AnswerSubmission>()
        for (q in questions) {
            when (q.type) {
                "single" -> singleSel[q.id]?.let { list.add(AnswerSubmission(q.id, JsonPrimitive(it))) }
                "bool" -> boolSel[q.id]?.let { list.add(AnswerSubmission(q.id, JsonPrimitive(it))) }
                "multiple" -> {
                    val sel = multiSel[q.id]
                    if (!sel.isNullOrEmpty()) {
                        val arr = JsonArray(sel.sorted().map { JsonPrimitive(it) })
                        list.add(AnswerSubmission(q.id, arr))
                    }
                }
            }
        }
        list
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = paddingValues.calculateTopPadding())
    ) {
        val completed = alreadySubmitted || result?.success == true

        when {
            isLoading -> CenterMessage(stringResource(R.string.quiz_loading))
            error != null -> CenterMessage(text = error!!, onRetry = ::load)
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)
                ) {
                    if (completed) {
                        item { CompletedHeader(result = result) }
                    }
                    items(questions) { q ->
                        QuestionCard(
                            q = q,
                            singleSel = singleSel,
                            multiSel = multiSel,
                            boolSel = boolSel,
                            resultItem = result?.results?.firstOrNull { it.id == q.id }
                        )
                    }
                    item {
                        PrimaryButton(
                            text = stringResource(R.string.quiz_submit),
                            icon = MiuixIcons.Send,
                            enabled = !submitting && !completed,
                            onClick = {
                                scope.launch {
                                    submitting = true
                                    try {
                                        val resp = QuizApi.submit(QUIZ_BASE_URL, token, buildAnswers())
                                        if (resp.alreadySubmitted) {
                                            // Rejected re-submit: lock the screen and explain.
                                            alreadySubmitted = true
                                            QuizPrefs.saveCompleted(context, QuizPrefs.fingerprint(questions), null)
                                        } else if (resp.success) {
                                            // First successful submit: mark done and show the reward.
                                            alreadySubmitted = true
                                            result = resp
                                            QuizPrefs.saveCompleted(context, QuizPrefs.fingerprint(questions), resp)
                                        } else {
                                            error = resp.message
                                        }
                                    } catch (e: Exception) {
                                        error = e.message
                                    } finally {
                                        submitting = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (onRetry == null) CircularProgressIndicator()
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            textAlign = TextAlign.Center,
            color = MiuixTheme.colorScheme.onSurface
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = stringResource(R.string.quiz_retry), onClick = onRetry)
        }
    }
}

/**
 * Full-width "已完成本期答题" header. Replaces the previous compact card so the
 * completion state reads as a prominent page section rather than an inline card.
 */
@Composable
private fun CompletedHeader(result: SubmitResponse?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (result != null) MiuixIcons.Ok else MiuixIcons.Lock,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.quiz_completed),
            style = MiuixTheme.textStyles.headline1,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        if (result != null) {
            Text(
                stringResource(R.string.quiz_result_total, result.totalAwarded),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.quiz_balance, result.balance),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.quiz_daily, result.dailyUsed, result.dailyLimit),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                stringResource(R.string.quiz_already_submitted),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun QuestionCard(
    q: QuestionPublic,
    singleSel: MutableMap<String, Int>,
    multiSel: MutableMap<String, List<Int>>,
    boolSel: MutableMap<String, Boolean>,
    resultItem: QuizResultItem?
) {
    val accent = when {
        resultItem == null -> MiuixTheme.colorScheme.onSurface
        resultItem.correct -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.error
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.quiz_reward, q.reward),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                resultItem?.let {
                    val badge = when {
                        it.limited == true -> stringResource(R.string.quiz_limited)
                        it.alreadyClaimed == true -> stringResource(R.string.quiz_claimed)
                        it.correct -> stringResource(R.string.quiz_correct, it.awarded)
                        else -> stringResource(R.string.quiz_wrong)
                    }
                    Text(badge, style = MiuixTheme.textStyles.body2, color = accent)
                }
            }
            Spacer(Modifier.height(8.dp))
            MarkdownText(text = q.content, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            when (q.type) {
                "bool" -> OptionsBool(
                    selected = boolSel[q.id],
                    onSelect = { boolSel[q.id] = it }
                )
                "multiple" -> OptionsMulti(
                    q = q,
                    selected = multiSel[q.id] ?: emptyList(),
                    onToggle = { i ->
                        val cur = (multiSel[q.id] ?: emptyList()).toMutableList()
                        if (cur.contains(i)) cur.remove(i) else cur.add(i)
                        multiSel[q.id] = cur
                    }
                )
                else -> OptionsSingle(
                    q = q,
                    selected = singleSel[q.id] ?: -1,
                    onSelect = { singleSel[q.id] = it }
                )
            }
        }
    }
}

@Composable
private fun OptionRow(isSel: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSel) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            content()
        }
    }
}

@Composable
private fun OptionsSingle(q: QuestionPublic, selected: Int, onSelect: (Int) -> Unit) {
    (q.options ?: emptyList()).forEachIndexed { i, opt ->
        OptionRow(isSel = selected == i, onClick = { onSelect(i) }) {
            MarkdownText(text = opt, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun OptionsBool(selected: Boolean?, onSelect: (Boolean) -> Unit) {
    val items = listOf(
        true to stringResource(R.string.quiz_opt_true),
        false to stringResource(R.string.quiz_opt_false)
    )
    items.forEach { (value, label) ->
        OptionRow(isSel = selected == value, onClick = { onSelect(value) }) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun OptionsMulti(q: QuestionPublic, selected: List<Int>, onToggle: (Int) -> Unit) {
    (q.options ?: emptyList()).forEachIndexed { i, opt ->
        OptionRow(isSel = selected.contains(i), onClick = { onToggle(i) }) {
            MarkdownText(text = opt, modifier = Modifier.weight(1f))
        }
    }
}
