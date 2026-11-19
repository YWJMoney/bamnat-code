# K 묶음 A 작업 중이던 것 (2.1로 넘김)

사용자 결정(11-19 12:53 KST): 2.0은 지금 하던 것까지만 닫고 못 한 것은 2.1로. 이 패치는 **컴파일·시험을 돌려 보기 전**의 작업 중 코드예요
(저장소 통합 머리 + RVK-61 위, 파일 `K-묶음A-작업중.patch`, `git apply`로 적용). 2.0 PR에는 넣지 않았어요.

| 줄 | 한 것 (패치 안) | 남은 것 |
|---|---|---|
| HQ-146 | 가계부 요약 예산 줄을 `SideOrBelow`로(큰 글자에서 단추를 아래로) | 사진(밝은·어두운 100·200%, 접은·편 폭), 같은 '글+글자 단추' 줄 찾기 |
| HQ-161 | 분류 고치기 줄을 `SideOrBelow`로(이름은 온전히, 단추 넷은 아래로) | 사진, 시험 |
| MONEY-22·U06a-08 | `MoneyLayers` 접힌 점검 층은 센 개수만 두고 안 그림(`probe`), `LedgerSmarts.daysSinceLine` + `RecordsGapLine` '어제까지 적었어요 / N일 비었어요', 시험 `RecordsGapTest` | 사진, 접힌 점검 층 개수가 맞는지 앱 시험 |
| MONEY-08 | 카드값 '어디로' 규칙을 core `CardBillTarget`으로 빼고 시험 `CardBillTargetTest` | 시트 동작이 전과 같은지 앱 시험 |
| MONEY-33·80 | 시험 `MoneyScreenLogicTest`(app), `RiskyMathTest`(core: 모든 달력 합계·소득 종류·FillMath) | 돌려 보기 |
| U06a-04 | 금액 칸에 들어가 있는 동안 '자주 쓰는 곳 3개'가 금액 칸 바로 아래에(`LedgerEntrySheet`) | 사진, 시험 |
| U06a-03·I06aa-08 | 영수증 '찍어서 채우기'(카메라), 사진 눌러 크게 보기(`ReceiptPhotoDialog`) | 사진, 카메라 없는 기기 |
| MONEY-28·I06aa-07 | 손으로 환불: `LedgerChecks.refundLeft·refundLine`, 시트 '환불 받았어요', 시험 `HandRefundTest` | 사진, 앱 시험 |

그 밖의 남은 K 줄(MONEY-65·T06b-11·I06bb-02 통화, T06a-03 경비 나누기=DB 38 필요, I06ab-02·06~09, I06bb-03~06, I06ac-01~04, I06bc-01~05, T06b-15·16, K-결정-23·23b)은 코드를 시작하지 않았어요.
I06ba-05·07은 이미 RVK-37·RVK-38로 고쳐져 있어요(추적표 note 연결만 남음).
