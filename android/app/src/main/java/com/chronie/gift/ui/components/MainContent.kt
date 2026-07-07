package com.chronie.gift.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chronie.gift.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.input.nestedscroll.nestedScroll

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
        val url = URL("http://192.168.10.6:3001/api/activities")
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
    var activities by remember { mutableStateOf<List<Activity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scrollBehavior = MiuixScrollBehavior()
    
    LaunchedEffect(Unit) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.tab_home),
                largeTitle = stringResource(id = R.string.tab_home),
                scrollBehavior = scrollBehavior
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = it.calculateTopPadding(),
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            )
        ) {
            item {
                // Add a small space below largeTitle
                Spacer(modifier = Modifier.height(8.dp))
                
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(id = R.string.loading_text),
                                style = MiuixTheme.textStyles.body2
                            )
                        }
                    }
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error
                    )
                } else {
                    activities.forEach { activity ->
                        ActivityCard(
                            activity = activity,
                            onClick = { uriHandler.openUri(activity.url) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
            }
        }
    }
}

@Composable
fun ActivityCard(
    activity: Activity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
