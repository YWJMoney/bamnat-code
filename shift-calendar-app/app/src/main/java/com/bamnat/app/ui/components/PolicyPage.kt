package com.bamnat.app.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bamnat.app.ui.components.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 1.36 PC W43 (처리방침이 앱 밖 Chrome으로 열리고, Chrome을 처음 쓰는 폰이면 Google 로그인 화면부터 떠요): the policy pages (처리방침,
 * 약관, 광장 운영 규칙) open on a page inside the app with a visible 닫기. Only our own pages load there; a link elsewhere goes
 * to the browser. No script, no files, no saved data: they are plain pages. Offline it says so and offers the browser.
 */
object PolicyPage {
    private val _url = MutableStateFlow<Pair<String, String>?>(null)
    val url = _url.asStateFlow()

    /** Opens [url] titled [title]. Pages not ours go to the browser as before. */
    fun open(context: android.content.Context, url: String, title: String) {
        if (!ours(url)) { browser(context, url); return }
        _url.value = title to url
    }

    fun close() { _url.value = null }

    internal fun ours(url: String): Boolean = runCatching { java.net.URI(url) }.getOrNull()?.let {
        it.scheme == "https" && it.host?.lowercase() in HOSTS
    } == true

    internal fun browser(context: android.content.Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private val HOSTS = setOf("ywjmoney.github.io", "bamnat.app", "www.bamnat.app")
}

/** The page, once in the app frame (AppRoot). */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PolicyPageHost() {
    val open by PolicyPage.url.collectAsStateWithLifecycle()
    val (title, url) = open ?: return
    val context = LocalContext.current
    var loading by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }
    // 2.0.0 [RVP-33] (검토 UIX-56·U13b-13) Back goes back inside the page first, a server error says so with 다시 열기, the page
    // follows a dark screen, and the view is let go when the window closes.
    var web by remember(url) { mutableStateOf<WebView?>(null) }
    var back by remember(url) { mutableStateOf(false) }
    var serverDown by remember(url) { mutableStateOf(false) }
    val dark = isDarkSurface()
    Dialog(onDismissRequest = PolicyPage::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = PolicyPage::close) { Icon(Icons.Filled.Close, "닫기") } },
                actions = { TextButton(onClick = { PolicyPage.browser(context, url) }) { Text("브라우저로") } },
            )
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                androidx.activity.compose.BackHandler(enabled = back && !failed) { web?.goBack() }
                if (failed) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                        Text(if (serverDown) "지금은 이 쪽을 열 수 없어요. 잠시 뒤 다시 열어 주세요." else "인터넷에 연결되지 않아 열지 못했어요.",
                            style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { failed = false; serverDown = false; loading = true }) { Text("다시 열기") }
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = false
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.domStorageEnabled = false
                                settings.textZoom = (ctx.resources.configuration.fontScale * 100).toInt().coerceIn(85, 200)
                                if (dark) darkPage(this)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val next = request.url.toString()
                                        if (PolicyPage.ours(next)) return false
                                        PolicyPage.browser(ctx, next)
                                        return true
                                    }
                                    override fun onPageFinished(view: WebView, u: String?) { loading = false; back = view.canGoBack() }
                                    override fun doUpdateVisitedHistory(view: WebView, u: String?, isReload: Boolean) { back = view.canGoBack() }
                                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                        if (request.isForMainFrame) { failed = true; loading = false }
                                    }
                                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
                                        if (request.isForMainFrame && response.statusCode >= 400) { serverDown = true; failed = true; loading = false }
                                    }
                                }
                                loadUrl(url)
                                web = this
                            }
                        },
                        onRelease = { it.stopLoading(); it.destroy(); web = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (loading) LinearProgressIndicator(Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }
}

/** [RVP-33] A page made dark by the WebView itself when the app is dark (Android 10+), so it does not glare at night. */
@Suppress("DEPRECATION")
private fun darkPage(view: WebView) {
    when {
        android.os.Build.VERSION.SDK_INT >= 33 -> view.settings.isAlgorithmicDarkeningAllowed = true
        android.os.Build.VERSION.SDK_INT >= 29 -> view.settings.forceDark = android.webkit.WebSettings.FORCE_DARK_ON
    }
}
