# SHA 디자인 적용 명령서

이 문서는 SHA(린매스업 트래커, `app/src/main/java/com/pkj/leanbulk`) 앱의 UI를
승인된 목업 디자인대로 Jetpack Compose로 옮기기 위한 작업 지시서다.
느낌이 아니라 이 문서에 적힌 값을 그대로 코드에 넣으면 된다.

- 참고용 확정 목업(4화면): https://claude.ai/code/artifact/c905afc4-b27a-44ee-8382-254b371eec6c
- 리서치·토큰 근거 문서: https://claude.ai/code/artifact/84c09768-84e0-4e5f-8cfa-4d628d7f1141

## 0. 작업 순서

1. `ui/theme/Color.kt`, `Shape.kt`, `Spacing.kt`, `Typography.kt` 생성 (아래 §1)
2. `res/font/`에 Pretendard 추가하고 Typography에 연결
3. `ui/Widgets.kt`에 공용 컴포넌트 작성 (아래 §2) — 이번 작업의 핵심. 여기가 끝나면 화면은 조립만 하면 됨
4. `TodayDietScreen.kt` 적용 (§3)
5. `TodayWorkoutScreen.kt` 적용 (§4)
6. `BodyRecordScreen.kt` 적용 (§5)
7. `HistoryScreen.kt` 적용 (§6) — **가장 중요한 기존 기능(복사용 텍스트)을 절대 깨지 않을 것**
8. 다크모드 값 반영 후 전체 재확인

기존 로직(`MainViewModel`, `Repository`, DB 스키마 등)은 건드리지 않는다.
이 명령서는 **화면(Compose UI)만** 대상으로 한다.

## 1. 디자인 토큰

```kotlin
// ui/theme/Color.kt
object ShaColors {
    val Canvas = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF2F4F6)       // 화면 배경
    val SurfaceRaised = Color(0xFFFFFFFF) // 카드 배경
    val Border = Color(0xFFE5E8EB)
    val BorderStrong = Color(0xFFD1D6DB)  // 인풋 테두리, 점선
    val Muted = Color(0xFF8B95A1)
    val BodyText = Color(0xFF4E5968)
    val Ink = Color(0xFF191F28)
    val Primary = Color(0xFF3182F6)
    val PrimaryHover = Color(0xFF2272EB)
    val PrimaryWeakBg = Color(0xFFE8F3FF)
    val PrimaryWeakFg = Color(0xFF1B64DA)
    val Success = Color(0xFF06B26B)
    val Danger = Color(0xFFE42939)
}

// ui/theme/Shape.kt
val ShaShapes = Shapes(
    small = RoundedCornerShape(4.dp),       // 뱃지, 세트 번호 칩
    medium = RoundedCornerShape(6.dp),      // 인풋
    large = RoundedCornerShape(14.dp),      // 카드, 주요 버튼, 점선 추가 버튼
    extraLarge = RoundedCornerShape(16.dp)  // 스탯 타일, 큰 카드
)

// ui/theme/Spacing.kt
object ShaSpacing {
    val xs = 4.dp; val sm = 6.dp; val md = 8.dp
    val lg = 16.dp; val xl = 24.dp; val xxl = 32.dp
}
```

**타이포그래피** (Pretendard, `res/font`에 추가):

| 역할 | 크기/굵기 | 용도 |
|---|---|---|
| Display | 22~34sp / ExtraBold | 스탯 타일 큰 숫자 |
| H1 | 22sp / Bold | 화면 타이틀 ("오늘", "히스토리") |
| H2 | 15~16sp / Bold | 카드/섹션 타이틀 (운동 종목명, "새 기록 입력") |
| Body Strong | 15sp / SemiBold | 리스트 제목 (음식명, 필드 라벨) |
| Body | 15sp / Regular | 본문, 캡션 상위 |
| Caption | 12~13sp / Medium, Muted 색 | 보조 텍스트, 요약 라인 |
| Data | 12~19sp / Bold, `FontFamily.Monospace` 또는 등폭 처리 | 무게·횟수·칼로리·체중 등 **모든 숫자**. 오른쪽 정렬 기본 |

숫자는 반드시 등폭(tabular) 처리해서 세트 사이에 자릿수가 흔들리지 않게 한다.

## 2. 공용 컴포넌트 (`ui/Widgets.kt`)

이번 작업에서 새로 만들거나 다시 짤 컴포넌트 목록. 화면 파일들은 이것만 갖다 쓴다.

- **`ShaScreenScaffold`** — 화면 배경 `Surface` 색, 상단 세이프에어리어 여백, 하단 `BottomTabBar` 공간 확보(70dp)
- **`SubTabBar(selected: String, onSelect)`** — 식단/운동/신체 pill 3개. 선택된 pill은 배경 `Ink`+글자 `Canvas`, 미선택은 글자만 `Muted`. radius 10dp, padding 8x14
- **`BottomTabBar(selected: Tab)`** — 오늘/히스토리/내보내기 3탭. 아이콘 26dp, 선택 시 아이콘 배경 `PrimaryWeakBg` + 원형(radius 8dp), 라벨 색 `Primary`(선택)/`Muted`(비선택)
- **`StatTile(label, value, unit, delta, deltaTone)`** — `SurfaceRaised` 배경, radius `extraLarge`, elevation은 `shadow(1dp, ambient)` 정도로 아주 미세하게만. `label`은 Caption/Muted, `value`+`unit`은 Data, `delta`는 12sp (`Success`/`Muted`/`Danger` 중 하나)
- **`DashedAddButton(text, onClick)`** — `BorderStrong` 1.5dp 점선, radius `large`, 가운데 정렬 + 십자 아이콘, 글자 `Muted`
- **`SetRow(index, valueText, unitText, done: Boolean, onToggle, onEdit)`** — `done=true`면 왼쪽 번호칩(`Surface` 배경)+숫자(Data)+오른쪽 초록 체크(`Success` 원+흰 체크 아이콘). `done=false`면 행 배경 `PrimaryWeakBg`, 번호칩 배경 흰색+`Primary` 글자, 값 자리는 입력 가능한 필드(placeholder `--`)
- **`ExerciseCard(name, bodyPart, sets: List<SetRow>, onAddSet, onMenu)`** — `SurfaceRaised` 카드, 상단 종목명(H2)+부위(Caption)+`⋯` 메뉴, 세트 목록, 하단 "세트 추가" 텍스트 링크(`Primary` 색 + 십자 아이콘, 배경 없음)
- **`RestTimerBar(remaining, onSkip)`** — `PrimaryWeakBg` 풀와이드 바, 왼쪽 타이머 아이콘+"휴식 중"+Data 시간, 오른쪽 "건너뛰기"
- **`MealCard(mealName, totalKcal, items, expanded, onToggle)`** — 접힌 상태는 이름+총kcal만, 펼치면 음식별 행(이름/추정 매크로 Caption + kcal Data 오른쪽)
- **`WeightTrendCard(points: List<Float>, fromLabel, toLabel)`** — 8주 등 추이를 얇은 라인 스파크라인으로. `Primary` 선 2.5dp, 아래 `PrimaryWeakBg` 영역 채움, 마지막 포인트만 강조 점. Compose Canvas로 그린다
- **`RecordInputRow(label, value, unit, note?)`** — 라벨 왼쪽, 값+단위 오른쪽, 하단 보더 1dp `Border`. `note`가 있으면 라벨 아래 작은 Caption ("비워두면 자동 계산" 등)
- **`CopyableHistoryBlock(text: String, onCopy)`** — `Surface` 배경의 내부 카드, 등폭 폰트 12.5sp/line-height 1.85, `SelectionContainer`로 감싸서 실제 텍스트 선택 가능하게. 우상단에 "복사" 칩 버튼(클립보드로 복사)

## 3. TodayDietScreen — 오늘 · 식단

- 상단: 날짜 이동(좌우 화살표 + 날짜 텍스트 H1) + 우측 "목표" 아웃라인 버튼(작은 타겟 아이콘)
- `SubTabBar` (식단 선택)
- `StatTile` 3개를 `Row`로: 칼로리(값/목표 + "N 남음"), 단백질(값 + "목표 도달" 또는 "목표까지 Ng"), 탄수화물(값 + 잔여량). 매크로 값이 비어있으면(`MacroEstimator` 추정치) delta 자리에 "추정치" 표기
- 그 아래 `MealCard` 리스트 (세로 스크롤), 마지막에 `DashedAddButton("끼니 추가")`
- `MealCard` 내부 음식 행: 추정 매크로는 항상 Caption/Muted로 "추정 · 단백질 13g" 형태 — 사용자가 직접 입력한 값과 시각적으로 구분되게 절대 진한 색을 쓰지 않는다

## 4. TodayWorkoutScreen — 오늘 · 운동

- 상단: 날짜 이동 + 우측 "루틴 불러오기" 아웃라인 버튼
- `SubTabBar` (운동 선택)
- 휴식 타이머 진행 중일 때만 `RestTimerBar` 표시 (`WorkoutTimerUi.kt`/`Timers.kt` 상태와 연결)
- `ExerciseCard` 리스트. 세트는 종목의 기록 방식(무게×횟수 / 시간 / 거리+시간)에 따라 `SetRow`의 `valueText`/`unitText` 조합만 바뀐다 — 컴포넌트 자체는 하나로 통일
- 마지막에 `DashedAddButton("종목 추가 · 카탈로그에서 검색")`

## 5. BodyRecordScreen — 오늘 · 신체

- 상단: 날짜 이동만 (목표/루틴 버튼 없음)
- `SubTabBar` (신체 선택)
- `StatTile` 3개: 체중(전일 대비 delta), 골격근량(전일 대비 delta), 체지방률(delta 자리에 "자동 계산됨" — 실제 자동계산일 때만 표시, 사용자가 직접 입력했으면 delta 정상 표시)
- `WeightTrendCard` — 최근 8주 정도. 데이터 3개 미만이면 카드 자체를 숨긴다(추이라 부를 게 없으므로)
- 입력 폼 카드: `RecordInputRow` 5개(체중/골격근량/체지방량/체지방률/허리둘레), 체지방률 행에만 note 표시
- 하단 `Primary` 풀와이드 버튼 "기록 저장"

## 6. HistoryScreen — 히스토리

가장 중요한 기존 기능이니 아래 순서를 반드시 지킨다.

- 상단: "히스토리" H1 + 우측 검색 아이콘(선택 사항)
- 날짜별 리스트, 각 항목은 접힌 카드: 날짜(Body Strong) + 요약 한 줄(Caption/Muted, 예: "운동 2종목 · 식단 1,847kcal · 체중 72.3kg") + 우측 셰브론
- 펼치면 카드 안쪽에 `CopyableHistoryBlock` 삽입. **이 블록의 텍스트는 순수 텍스트여야 하고, `SelectionContainer`로 실제 드래그·복사가 되어야 한다** — 지금 목업의 서식(색·카드)은 장식일 뿐, 텍스트 자체는 꾸미지 않는다
- 텍스트 포맷 예시 (섹션 헤더는 `[운동]` `[식단]` `[신체]` 대괄호 유지):
  ```
  [운동]
  벤치프레스 60kg×8, 60kg×7, 60kg×6
  플랭크 60초

  [식단] 1,762kcal
  아침: 계란후라이 2개, 현미밥 150g
  점심: 닭가슴살 도시락

  [신체]
  체중 72.5kg · 골격근량 34.1kg · 체지방률 18.6%
  ```
- 우상단 "복사" 칩은 `ClipboardManager`로 전체 텍스트를 그대로 복사 — 기존 `HistoryScreen.kt`에 이미 있는 복사 로직이 있다면 그 로직을 그대로 새 UI에 연결하고, 새로 만들지 않는다
- 기록 없는 날은 리스트에서 아예 제외("휴식일"처럼 최소 기록만 있는 날은 요약에 "휴식일"만 표시)

## 7. 다크모드

`ShaColors`를 다크 변형으로 한 벌 더 만든다 (§1 색상표의 라이트 값 기준, 명도 대비를 더 강하게):

```
Canvas #111418 · Surface #1A1E24 · SurfaceRaised #20252C
Border #2B3138 · BorderStrong #3A414A
Ink #F2F4F6 · BodyText #B8C0CC · Muted #7B8794
Primary #5B9DFF · PrimaryWeakBg #16273F · PrimaryWeakFg #8FC0FF
Success #33D698 · Danger #FF6B70
```

`isSystemInDarkTheme()` 기준으로 `ShaColors`를 스위칭하고, 화면 코드에서는 색을 직접 쓰지 않고 항상 `ShaColors.xxx`를 통해서만 참조한다 (라이트/다크 하드코딩 금지).

## 8. 체크리스트

- [ ] Color.kt / Shape.kt / Spacing.kt / Typography.kt 생성
- [ ] Pretendard 폰트 연결
- [ ] Widgets.kt 컴포넌트 9종 작성
- [ ] TodayDietScreen 적용
- [ ] TodayWorkoutScreen 적용 (타이머 상태 연동 확인)
- [ ] BodyRecordScreen 적용 (추이 카드 3개 미만 숨김 처리 확인)
- [ ] HistoryScreen 적용 — 복사 기능 회귀 테스트 필수
- [ ] 다크모드 색상 적용 및 확인
- [ ] ExportScreen은 이번 범위 밖 — 별도 작업으로 진행
