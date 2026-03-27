package com.chronie.gift.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.selection.SelectionContainer
import com.chronie.gift.ui.markdown.MarkdownRenderer
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.*
import com.chronie.gift.R
import androidx.core.content.res.ResourcesCompat
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.extra.WindowListPopup
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import androidx.compose.ui.input.nestedscroll.nestedScroll

// API model for response
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T,
    val message: String? = null
)

// Markdown file content model
@Serializable
data class MarkdownContent(
    val filename: String,
    val content: String
)

// Singleton HTTP client
object ApiClient {
    val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json()
        }
    }
}

@Composable
fun AnswersScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.tab_answers),
                largeTitle = stringResource(id = R.string.tab_answers)
            )
        }
    ) { paddingValues ->
        MainContent(paddingValues = paddingValues)
    }
}

@Composable
fun MainContent(paddingValues: PaddingValues) {
    val baseUrl = "http://192.168.0.197:3001"
    val (markdownFiles, setMarkdownFiles) = remember { mutableStateOf<List<String>>(emptyList()) }
    val (selectedFile, setSelectedFile) = remember { mutableStateOf<String?>(null) }
    val (markdownContent, setMarkdownContent) = remember { mutableStateOf<String?>(null) }
    val (isLoading, setIsLoading) = remember { mutableStateOf(false) }
    val (errorMessage, setErrorMessage) = remember { mutableStateOf<String?>(null) }
    
    // Responsive layout - get screen width
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isSmallScreen = screenWidth < 600.dp
    
    // Preload all required string resources
    val failedToGetFiles = stringResource(id = R.string.failed_to_get_files)
    val failedToGetContent = stringResource(id = R.string.failed_to_get_content)
    val errorGettingFiles = stringResource(id = R.string.error_getting_files)
    val errorGettingContent = stringResource(id = R.string.error_getting_content)
    val selectFileLabel = stringResource(id = R.string.please_select_file)
    val activityString = stringResource(id = R.string.activity)

    // Initialize - fetch markdown file list
    LaunchedEffect(Unit) {
        setIsLoading(true)
        setErrorMessage(null)
        try {
            val files = fetchMarkdownFiles(baseUrl)
            setMarkdownFiles(files)
            if (files.isNotEmpty()) {
                setSelectedFile(files.first())
            }
        } catch (e: Exception) {
            val errorMsg = "$errorGettingFiles: ${e.message ?: ""}"
            setErrorMessage(errorMsg)
            e.printStackTrace()
        } finally {
            setIsLoading(false)
        }
    }
    
    // When selected file changes, fetch its content
    LaunchedEffect(selectedFile) {
        if (selectedFile != null) {
            setIsLoading(true)
            setErrorMessage(null)
            try {
                val content = fetchMarkdownContent(baseUrl, selectedFile)
                setMarkdownContent(content)
            } catch (e: Exception) {
                val errorMsg = "$errorGettingContent: ${e.message ?: ""}"
                setErrorMessage(errorMsg)
                e.printStackTrace()
            } finally {
                setIsLoading(false)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Watermark
        Watermark()

        if (isSmallScreen) {
            // Small screen layout - dropdown menu at top
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
                // Add small space below largeTitle
                Spacer(modifier = Modifier.height(8.dp))
                
                // Top dropdown menu
                if (markdownFiles.isNotEmpty() && selectedFile != null) {
                    val selectedIndex = markdownFiles.indexOf(selectedFile)
                    val (year, month, activity) = parseFilename(selectedFile)
                    val displayText = if (year.isNotEmpty() && month.isNotEmpty()) {
                        val monthName = getMonthName(month)
                        "${stringResource(id = R.string.date_format, year, monthName)} ${if (activity.isNotEmpty()) "$activityString$activity" else ""}"
                    } else {
                        selectedFile
                    }
                    
                    val showPopup = remember { mutableStateOf(false) }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            pressFeedbackType = PressFeedbackType.Sink,
                            showIndication = true,
                            onClick = { showPopup.value = true }
                        ) {
                            Text(
                                text = displayText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                style = MiuixTheme.textStyles.body2,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        WindowListPopup(
                            show = showPopup,
                            alignment = top.yukonga.miuix.kmp.basic.PopupPositionProvider.Align.BottomStart,
                            onDismissRequest = { showPopup.value = false }
                        ) {
                            ListPopupColumn {
                                markdownFiles.forEachIndexed { index, file ->
                                    val (itemYear, itemMonth, itemActivity) = parseFilename(file)
                                    val itemDisplayText = if (itemYear.isNotEmpty() && itemMonth.isNotEmpty()) {
                                        val itemMonthName = getMonthName(itemMonth)
                                        "${stringResource(id = R.string.date_format, itemYear, itemMonthName)} ${if (itemActivity.isNotEmpty()) "$activityString$itemActivity" else ""}"
                                    } else {
                                        file
                                    }
                                    
                                    top.yukonga.miuix.kmp.basic.DropdownImpl(
                                        text = itemDisplayText,
                                        optionSize = markdownFiles.size,
                                        isSelected = selectedIndex == index,
                                        onSelectedIndexChange = {
                                            setSelectedFile(markdownFiles[index])
                                            showPopup.value = false
                                        },
                                        index = index
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Content area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    ContentArea(
                    content = markdownContent,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    paddingValues = PaddingValues(start = 16.dp, end = 16.dp)
                )
                }
            }
        } else {
            // Large screen layout - sidebar on left
            Row(modifier = Modifier.fillMaxSize()) {
                // Sidebar
                Sidebar(
                    files = markdownFiles,
                    selectedFile = selectedFile,
                    onFileSelected = { file -> setSelectedFile(file) },
                    paddingValues = paddingValues
                )

                // Content area
                Box(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Column {
                        // Add small space below largeTitle
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        ContentArea(
                            content = markdownContent,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            paddingValues = paddingValues
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Sidebar(
    files: List<String>,
    selectedFile: String?,
    onFileSelected: (String) -> Unit,
    paddingValues: PaddingValues = PaddingValues(0.dp)
) {
    Surface(
        modifier = Modifier
            .width(250.dp)
            .fillMaxHeight()
            .padding(top = paddingValues.calculateTopPadding())
            .border(1.dp, MiuixTheme.colorScheme.outline, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
        color = Color.Transparent
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            items(files) { file ->
                val (year, month, activity) = parseFilename(file)
                val displayText = if (year.isNotEmpty() && month.isNotEmpty()) {
                    val monthName = getMonthName(month)
                    "${stringResource(id = R.string.date_format, year, monthName)} ${if (activity.isNotEmpty()) "${stringResource(id = R.string.activity)}$activity" else ""}"
                } else {
                    file
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    pressFeedbackType = PressFeedbackType.Sink,
                    showIndication = true,
                    onClick = { onFileSelected(file) }
                ) {
                    Text(
                        text = displayText,
                        modifier = Modifier.padding(12.dp),
                        style = MiuixTheme.textStyles.body2
                    )
                }
            }
        }
    }
}

@Composable
fun ContentArea(
    content: String?,
    isLoading: Boolean,
    errorMessage: String?,
    paddingValues: PaddingValues
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = paddingValues.calculateTopPadding(),
                start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                end = paddingValues.calculateEndPadding(LocalLayoutDirection.current)
            ),
        color = Color.Transparent
    ) {
        when {
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = stringResource(id = R.string.loading_text), style = MiuixTheme.textStyles.body2)
                }
            }
            errorMessage != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.error_title),
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = errorMessage, style = MiuixTheme.textStyles.body2)
                }
            }
            content != null -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Use custom Markdown renderer to render content
                    MarkdownRenderer(markdown = content)
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = stringResource(id = R.string.please_select_file), style = MiuixTheme.textStyles.body2)
                }
            }
        }
    }
}

// Parse filename to extract year, month, and activity
fun parseFilename(filename: String): Triple<String, String, String> {
    // filename format: 251001.md -> 2025.10 activity1
    val match = Regex("^(\\d{2})(\\d{2})(\\d{2})\\.md$").matchEntire(filename)
    if (match != null) {
        val year = "20${match.groupValues[1]}"
        val month = match.groupValues[2]
        val activity = match.groupValues[3]
        return Triple(year, month, activity)
    }
    return Triple("", "", "")
}

// Get month name from month number
@Composable
fun getMonthName(month: String): String {
    // Map month number to name
    val monthNames = listOf(
        stringResource(id = R.string.month_1),
        stringResource(id = R.string.month_2),
        stringResource(id = R.string.month_3),
        stringResource(id = R.string.month_4),
        stringResource(id = R.string.month_5),
        stringResource(id = R.string.month_6),
        stringResource(id = R.string.month_7),
        stringResource(id = R.string.month_8),
        stringResource(id = R.string.month_9),
        stringResource(id = R.string.month_10),
        stringResource(id = R.string.month_11),
        stringResource(id = R.string.month_12)
    )
    
    // Convert month string to number index
    return try {
        val monthIndex = month.toInt() - 1
        if (monthIndex in 0..11) {
            monthNames[monthIndex]
        } else {
            month
        }
    } catch (e: Exception) {
        month
    }
}

// Get list of Markdown files
suspend fun fetchMarkdownFiles(baseUrl: String): List<String> {
    val response = ApiClient.client.get("$baseUrl/api/outdate-test/markdown")
    val apiResponse = response.body<ApiResponse<List<String>>>()
    if (apiResponse.success) {
        return apiResponse.data
    } else {
        throw Exception("API returned success=false")
    }
}

// Get Markdown file content
suspend fun fetchMarkdownContent(baseUrl: String, filename: String): String {
    val response = ApiClient.client.get("$baseUrl/api/outdate-test/markdown/$filename")
    val apiResponse = response.body<ApiResponse<MarkdownContent>>()
    if (apiResponse.success) {
        return apiResponse.data.content
    } else {
        throw Exception("API returned success=false")
    }
}

// Watermark component
@Composable
fun Watermark() {
    val watermarkText = stringResource(id = R.string.watermark_text)
    val rows = 4
    val cols = 1
    
    // Responsive font size, similar to CSS clamp(1rem, 3vw, 2.2rem)
    val fontSize = 24.sp
    
    // Use grid layout to create multiple small watermarks
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxSize(),
            content = {
                items(rows * cols) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(16.dp)
                            .wrapContentSize(Alignment.Center)
                    ) {
                        Text(
                            text = watermarkText,
                            fontSize = fontSize,
                            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.25f),
                            textAlign = TextAlign.Center,
                            letterSpacing = 0.15.em,
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                            modifier = Modifier
                                .rotate(-30f)
                        )
                    }
                }
            }
        )
    }
}

