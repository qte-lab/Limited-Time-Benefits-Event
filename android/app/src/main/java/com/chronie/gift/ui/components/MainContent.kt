package com.chronie.gift.ui.components

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chronie.gift.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.net.HttpURLConnection
import java.net.URL

data class Activity(
    val id: String,
    val title: String,
    val startTime: String,
    val endTime: String,
    val url: String,
    val description: String,
    val type: String
)

private fun fetchActivitiesFromNetwork(): Pair<List<Activity>, String?> {
    return try {
        val url = URL("http://192.168.10.9:3002/api/activities")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().readText()
            val jsonObject = JSONObject(response)
            if (jsonObject.optBoolean("success", false)) {
                val dataArray = jsonObject.optJSONArray("data") ?: JSONArray()
                val activityList = mutableListOf<Activity>()
                
                for (i in 0 until dataArray.length()) {
                    val activityJson = dataArray.getJSONObject(i)
                    activityList.add(
                        Activity(
                            id = activityJson.optString("id", ""),
                            title = activityJson.optString("title", ""),
                            startTime = activityJson.optString("startTime", ""),
                            endTime = activityJson.optString("endTime", ""),
                            url = activityJson.optString("url", ""),
                            description = activityJson.optString("description", ""),
                            type = activityJson.optString("type", "")
                        )
                    )
                }
                Pair(activityList, null)
            } else {
                Pair(emptyList(), jsonObject.optString("message", "Get activities failed"))
            }
        } else {
            Pair(emptyList(), "HTTP error: $responseCode")
        }
    } catch (e: java.net.UnknownHostException) {
        Pair(emptyList(), "Cannot resolve domain: ${e.message}")
    } catch (e: java.net.ConnectException) {
        Pair(emptyList(), "Connection failed, please check if the server is running: ${e.message}")
    } catch (e: java.net.SocketTimeoutException) {
        Pair(emptyList(), "Connection timeout: ${e.message}")
    } catch (e: javax.net.ssl.SSLException) {
        Pair(emptyList(), "SSL error: ${e.message}")
    } catch (e: org.json.JSONException) {
        Pair(emptyList(), "JSON parse error: ${e.message}")
    } catch (e: Exception) {
        Pair(emptyList(), "Error[${e.javaClass.simpleName}]: ${e.message}")
    }
}

@Composable
fun MainContent() {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val openLinkFailedMsg = stringResource(id = R.string.open_link_failed)
    val noBrowserMsg = stringResource(id = R.string.no_browser_found)
    var activities by remember { mutableStateOf<List<Activity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scrollBehavior = MiuixScrollBehavior()
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val refreshData = suspend {
        isLoading = true
        try {
            val result = withContext(Dispatchers.IO) {
                fetchActivitiesFromNetwork()
            }
            activities = result.first
            errorMessage = result.second
        } catch (e: Exception) {
            errorMessage = "Error[${e.javaClass.simpleName}]: ${e.message}"
        } finally {
            isLoading = false
        }
    }
    
    LaunchedEffect(Unit, refreshTrigger) {
        refreshData()
    }

    // Safely launch the activity URL: guards against empty/blank URLs and catches
    // any exceptions (malformed URL, ActivityNotFoundException when no browser is
    // installed, etc.) so the app never crashes.
    val openUrlSafely: (String) -> Unit = { url ->
        if (url.isBlank()) {
            Toast.makeText(context, openLinkFailedMsg, Toast.LENGTH_SHORT).show()
        } else {
            try {
                uriHandler.openUri(url)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, noBrowserMsg, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, openLinkFailedMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.tab_home),
                largeTitle = stringResource(id = R.string.tab_home),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp

            PullToRefresh(
                isRefreshing = isLoading,
                onRefresh = { refreshTrigger++ }
            ) {
                // On large screens the content is capped to 80% width and centered so it does not
                // stretch uncomfortably across a wide window. Home cards then flow in a responsive
                // grid (multiple per row) instead of a single tall column.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = if (isWideScreen) Alignment.TopCenter else Alignment.TopStart
                ) {
                    if (isWideScreen) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(
                                top = paddingValues.calculateTopPadding(),
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp
                            )
                        ) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (errorMessage != null) {
                                item {
                                    Text(
                                        text = errorMessage ?: "",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.error
                                    )
                                }
                            } else {
                                items(activities) { activity ->
                                    ActivityCard(
                                        activity = activity,
                                        onClick = { openUrlSafely(activity.url) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(
                                top = paddingValues.calculateTopPadding(),
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp
                            )
                        ) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))

                                if (errorMessage != null) {
                                    Text(
                                        text = errorMessage ?: "",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.error
                                    )
                                } else {
                                    activities.forEach { activity ->
                                        ActivityCard(
                                            activity = activity,
                                            onClick = { openUrlSafely(activity.url) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityCard(
    activity: Activity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = activity.title,
                style = MiuixTheme.textStyles.title2
            )
            if (activity.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = activity.description,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                )
            }
        }
    }
}
