package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.ads.AdIds
import com.bamnat.app.appContainer
import com.bamnat.app.tier.Feature
import com.bamnat.core.tier.AdRules
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import java.time.Instant
import com.bamnat.core.time.Today

/**
 * [REL-02][MON-07] The one place a banner goes (결정 1: A): between the content and the bottom bar
 * (phones, folded) or under the content pane (Fold unfolded), and only on the 근무·급여 and 돈 tabs
 * ([AdRules.banner]). Calendar cells and buttons never sit under it, so it cannot cover anything the
 * user taps. Nothing before the 정식 출시 switch, for 프로, in the first days after install, or in a
 * build without an ad unit id.
 */
/**
 * G 1.32 [WTYP-01] When the app last came to the front (MainActivity.onStart) and whether the banner was closed since:
 * closed, it stays away until the app is opened again.
 */
object AdMoment {
    val openedAt = kotlinx.coroutines.flow.MutableStateFlow<Instant?>(null)
    val closed = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun opened(now: Instant) { openedAt.value = now; closed.value = false }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AdSlot(place: AdRules.Place, modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val config by c.remoteConfig.config.collectAsStateWithLifecycle()
    val ready by c.ads.ready.collectAsStateWithLifecycle()
    // [FLAG-09] The `ads` row in place of the 정식 출시 switch.
    val rows by c.flags.byKey.collectAsStateWithLifecycle()
    val show = ready && AdIds.banner.isNotBlank() && !gate.preview &&
        AdRules.banner(place, rows[com.bamnat.core.flags.FlagCatalog.ADS]?.on == true, gate.allows(Feature.NO_ADS), true, c.installedAt, Today.instant(), config.adQuietDays)
    if (!show) return
    // G 1.32 [WTYP-01] Not in the first seconds after opening, not while typing, and gone once closed.
    val openedAt by AdMoment.openedAt.collectAsStateWithLifecycle()
    val closed by AdMoment.closed.collectAsStateWithLifecycle()
    val typing = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    val now by androidx.compose.runtime.produceState(Today.instant(), openedAt) {
        val wait = openedAt?.let { java.time.Duration.between(Today.instant(), it.plusSeconds(AdRules.LAUNCH_QUIET_SECONDS)).toMillis() } ?: 0
        if (wait > 0) kotlinx.coroutines.delay(wait + 50)
        value = Today.instant()
    }
    if (!AdRules.bannerNow(true, openedAt, now, typing, closed)) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth.value.toInt()
        val context = LocalContext.current
        val size = remember(width) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val view = remember(size) {
            AdView(context).apply {
                setAdSize(size)
                adUnitId = AdIds.banner
                loadAd(AdRequest.Builder().build())
            }
        }
        DisposableEffect(view, lifecycle) {
            val watch = LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_PAUSE -> view.pause()
                    Lifecycle.Event.ON_RESUME -> view.resume()
                    else -> Unit
                }
            }
            lifecycle.addObserver(watch)
            onDispose {
                lifecycle.removeObserver(watch)
                view.destroy()
            }
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(size.height.dp))
            // The 닫기 is always there, outside the ad's own area so it is never part of the ad.
            androidx.compose.material3.Surface(
                onClick = { AdMoment.closed.value = true },
                shape = androidx.compose.foundation.shape.CircleShape,
                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                    .semantics { contentDescription = "광고 닫기" },
            ) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, null,
                    modifier = Modifier.padding(4.dp).size(16.dp))
            }
        }
    }
}
