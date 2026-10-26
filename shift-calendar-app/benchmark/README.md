# 시작 시간 재기와 Baseline Profile (1.23 M 순번 324-5, 1.28 AO 순번 457)

1.28부터 이 모듈은 `androidx.baselineprofile` 플러그인의 **프로필 만드는 쪽**이에요. 앱(`:app`)은 만든 프로필을
`app/src/main/generated/baselineProfiles/`에 두고, 손으로 쓴 `app/src/main/baseline-prof.txt`와 **함께** 써요.
측정하고 프로필을 만드는 빌드는 플러그인이 만드는 `benchmarkRelease`예요. 출시와 똑같이 줄이고, 디버그 키로 서명하며,
profileable(`app/src/benchmarkRelease`)이에요. 이 빌드는 올리지 않아요. 보통 빌드(`assembleRelease`, CI)는 프로필을
만들지 않고, 소스에 있는 파일만 써요.

## A. 휴대폰 없이 GitHub에서 만들기 (권장)

1. GitHub › Actions › **Baseline Profile (밤낮)** › Run workflow (브랜치 main). 30~50분 걸려요.
2. 끝나면 실행 화면 아래 Artifacts의 `baseline-profile`을 내려받아요.
3. 압축 속 `baseline-prof.txt`와 `startup-prof.txt`를 `shift-calendar-app/app/src/main/generated/baselineProfiles/`에
   넣고 커밋해요(예: `1.28: 만든 Baseline Profile 넣기 [PERF-13]`).
4. `sh ./gradlew :app:testDebugUnitTest --tests '*BaselineProfileRulesTest*'`가 통과하는지 봐요.

## B. 이 컴퓨터에서 만들기

```bash
# 에뮬레이터(Gradle managed device pixel6Api34, KVM 필요)로
sh ./gradlew :app:generateBaselineProfile
# 연결한 휴대폰(Android 9 이상, USB 디버깅)으로
sh ./gradlew :app:generateBaselineProfile -Pbamnat.profileOnPhone
```

결과가 바로 `app/src/main/generated/baselineProfiles/`에 들어가요. 커밋만 하면 돼요.

- `BaselineProfileGenerator.start`: 첫 달까지만 열어요. 시작 프로필(`startup-prof.txt`)로도 쓰여서, 시작에 쓰는
  클래스를 dex 앞쪽에 모아요(`dexLayoutOptimization`).
- `BaselineProfileGenerator.generate`: 열고 나서 메모·통계·설정·달력 탭을 돌아요. Baseline Profile에만 들어가요.

## C. 시작 시간 재기 (휴대폰)

```bash
sh ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest -P android.testInstrumentationRunnerArguments.class=com.bamnat.benchmark.StartupBenchmark
```

프로필 없이/있이 각각 5번 열어요. 결과는 `benchmark/build/outputs/connected_android_test_additional_output/`에 있어요.
`timeToInitialDisplayMs`의 중앙값을 그 판 계획 문서의 숫자 표에 적어요.

실제 사용자들의 시작 시간은 관리자 앱 › 지표 › **앱 시작 시간**에서 볼 수 있어요. 사용 통계와 처리방침 3.2에 동의한
휴대폰만 보내고, 판마다 가운데 값과 90% 값을 보여 줘요. 내 휴대폰의 마지막 값은 앱의 ‘알람 점검’ 화면(기기 부분)에 있어요.
