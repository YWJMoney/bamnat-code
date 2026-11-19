package com.bamnat.app.tier

import com.bamnat.app.BuildConfig
import com.bamnat.core.flags.Released

/**
 * 2.0.0 [RVO-12] 검토 HQ-46 (사용자 2026-11-12 20:32 KST "테스트는 모든 기능을 기반으로 동작해야하므로 유지하고 실 배포 버젼만 정상적으로
 * 나오면돼"): whether this build reads [Released], the 정식 출시 it shipped knowing. On in release builds only, so a debug build, the
 * local test server and every unit and screenshot test keep every feature open as before; a test that looks at the released app's
 * look turns it on for a moment and back ([ReleaseBuildTest][com.bamnat.app.tier.ReleaseBuildTest]).
 */
object ReleaseBuild {
    @Volatile var on: Boolean = !BuildConfig.DEBUG
}

/** 2.0.0 [RVO-12] The 정식 출시 moment: the server's, or in a release build the one it shipped knowing. */
fun LaunchConfig.launchAt(released: Boolean = ReleaseBuild.on): java.time.Instant? = launchedAt ?: Released.AT.takeIf { released }

/** 2.0.0 [RVO-12] The 감사 months: the server's once it answered, before that in a release build the real server's own. */
fun LaunchConfig.thanks(released: Boolean = ReleaseBuild.on): Int = if (fetchedAt == null && released) Released.THANKS_MONTHS else thanksMonths
