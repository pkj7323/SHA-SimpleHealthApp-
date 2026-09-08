# SHA 디자인 적용 명령서

이 문서는 SHA(심플 헬스 앱, `app/src/main/java/com/pkj/leanbulk`) 앱의 UI를
승인된 목업 디자인대로 Jetpack Compose로 옮기기 위한 작업 지시서다.
느낌이 아니라 이 문서에 적힌 값을 그대로 코드에 넣으면 된다.

- 참고용 확정 목업(4화면): https://claude.ai/code/artifact/c905afc4-b27a-44ee-8382-254b371eec6c
- 리서치·토큰 근거 문서: https://claude.ai/code/artifact/84c09768-84e0-4e5f-8cfa-4d628d7f1141

## 진행 상황 (2026-09-09)

`git log --oneline`로도 보이지만, **무엇이 왜 그렇게 정해졌는지**는 커밋 제목에 안 남는다.

### 끝난 것

| | 무엇 | 커밋 |
|---|---|---|
| 토큰 | Color · Shape · Spacing · Typography 신설. 화면은 `ShaTheme.colors/type`으로만 꺼내 쓴다 | `3def0b3` |
| 부품 | `ShaScreen` `ShaCard` `StatTile` `DashedAddButton` `SubTabBar` | `3def0b3` |
| 화면 | **식단 탭 하나만** 새로 짬 | `3def0b3` |
| 색 | 팔레트를 아이콘의 라임으로 통일 (토스 파랑 폐기) | `05242f5` |
| 색 | 주 버튼을 잉크+라임으로. 런처 아이콘 6b로 교체 | `080339c` |
| 설정 | 화면 테마 고르기(밝게/어둡게/시스템), 기본 어둡게. DB 18 → 19 | `080339c` |
| 부품 | `ShaDialog` `ShaPrimaryButton` `ShaSecondaryButton` `ShaTextField` | |
| 창 | 다이얼로그 11개를 `ShaDialog`로 교체 (내보내기 화면 2개 남음) | |
| 창 | 음식 추가 창 속을 다시 짬 — 껍데기만 바꾸니 여전히 투박했다 | |
| 기타 | 스플래시 배경을 어두운 테마에 맞춤 (켤 때마다 흰 화면이 번쩍였다) | |
| 화면 | **운동 탭** 새로 짬. 날짜 헤더를 `ShaDateHeader`로 빼서 식단과 공유 | |
| 화면 | **신체 탭** 새로 짬. 기록을 목록/그래프로 전환, 항목별 추이 차트 신설 | |
| 화면 | **히스토리** 새로 짬. `CopyableHistoryBlock` 신설, 복사·선택 회귀 확인 | |
| 화면 | **내보내기 · 설정** 새로 짬. 남아 있던 `AlertDialog` 2개도 교체 — **앱에 0개** | |
| 색 | 브랜드색을 `#5CE3A2`로. **초록 계열을 전부 라임 하나에서 파생**하게 고침 | `1d7c909` |
| 글꼴 | Pretendard 5벌 내장. 히스토리 복사 표시를 `[운동]`으로 | |

### 정해진 것과 그 이유

- **브랜드색은 라임 `#5CE3A2` 하나뿐이다** — 런처 아이콘에서 왔다. 아이콘과 앱이 같은
  색을 써야 같은 물건으로 보인다.
- **초록 계열은 전부 라임에서 파생한다**(`BrandGreen`, 연한 바탕 둘). 밝은 테마용 딥그린을
  따로 박아두면 브랜드색을 바꿔도 그것만 안 따라와서 화면 절반이 옛 색으로 남는다.
  실제로 그랬고, 색을 바꿔보고서야 알았다.
- **라임은 글자 색으로 안 쓴다.** 흰 바탕 위 라임도, 라임 위 흰 글자도 1.6:1이다.
  글자·링크는 **읽힐 때까지 자동으로 어둡게 내린** 값을 쓴다. 어두운 테마에서만 라임을
  글자로 쓴다(11:1).
- **글자용 초록을 내릴 때는 화면 바탕(`surface`) 기준이다.** 같은 글자가 카드(흰색) 위에도
  앉는데 대비가 더 낮은 쪽은 바탕이다. 흰색 기준으로 맞추면 바탕 위에서 4.2:1이 된다 —
  눈으로는 못 보고 `BrandColorTest`가 잡았다.
- **채우는 색은 늘 배경의 반대다.** 밝은 테마 = 잉크 채움 + 라임 글자,
  어두운 테마 = 라임 채움 + 잉크 글자. 둘 다 11.1:1.
- **기본 테마는 어둡게.** 이 앱의 색이 어두운 쪽 기준으로 잡혀 있다.
- **한 번에 한 화면씩 고친다.** 여섯 화면을 동시에 건드리면 여섯 개가 다 어중간해진다.

### 화면은 다 돌았다

여섯 화면과 다이얼로그 13개가 전부 새 언어를 쓴다. 글꼴(Pretendard)과 복사 텍스트
형식(`[운동]`)까지 정해져서, 명령서에 적힌 것 중 남은 건 아래 §아직 안 정한 것뿐이다.

### 다이얼로그 — 끝남 (내보내기 화면 2개 제외)

화면보다 **먼저 손댄다.** 음식 추가·메모·목표는 거의 모든 화면에서 열리므로, 여기가 옛
모습이면 화면을 아무리 고쳐도 앱을 쓰는 동안 옛 디자인을 계속 보게 된다. 부품 하나
([ShaDialog])를 만들어 전부 갈아끼운다.

### 지금 화면 상태

- **여섯 화면 모두** 새 구조 + 새 색
  (`Theme.kt`는 앱에 하나뿐이라 손대면 전 화면이 따라온다)
- 로직 · ViewModel · Repository · DB 스키마는 화면 작업으로 건드린 적 없다
  (테마·알림 설정을 담느라 `app_meta`에 칸이 늘어난 것만 예외)

### 아직 안 정한 것

1. **밝은 테마에서 라임 자체가 안 보인다.** 글자용 초록은 대비를 맞추느라 어두워져서,
   어떤 라임을 골라도 밝은 테마에서는 비슷한 진초록이 된다(A/D/E가 각각 `#3C7A3A` ·
   `#327B57` · `#49794B`). 브랜드색을 밝은 테마에서도 보이게 하려면 링크를 글자색 대신
   **연한 라임 알약**으로 바꿔야 하는데, 화면 구조가 달라지는 일이라 미뤄뒀다
2. **회색의 온도** — 지금 따뜻한 회색. 순수 회색 / 파랑 회색도 대비는 다 통과한다

### 명령서와 지금 앱이 어긋나는 곳

목업을 그릴 때의 앱과 지금 앱이 달라져서 생긴 것들이다. 해당 화면에 손대기 전에 정해야 한다.

- **§3 "추정치" 표기** — `MacroEstimator`는 이미 지웠다. 추정 매크로라는 개념이 없어서
  붙일 데가 없다
- **§3 끼니 구조** — 목업은 자유 끼니인데 지금은 아침·점심·저녁·간식 **고정 4칸**이다.
  일단 고정 4칸을 유지하고 점선 버튼은 5번째 끼니 추가용으로 뒀다
- **§4에 운동 시작·종료 버튼이 없다.** 0.2.3에서 넣은 운동 시간 타이머가 목업에 안 잡혀 있다.
  `RestTimerBar`도 −10/+10초와 접기가 빠져 있다
- **하단 탭이 3개로 적혀 있는데 실제로는 4개다** (설정 추가됨)

---

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
// ui/theme/Color.kt  — 실제 값은 코드가 기준. 여기는 근거를 적어둔다.
//
// 브랜드색은 런처 아이콘의 라임 #6FE26C 다. 토스 파랑에서 갈아탔다.
//
// **라임은 글자 색으로 못 쓴다.** 흰 바탕 위 라임 글자도, 라임 위 흰 글자도 1.6:1이라
// (기준 4.5:1) 사실상 안 보인다. 그래서 역할을 둘로 가른다:
//
//   primaryFill  = 라임 #6FE26C   채우는 데만 (버튼·진행막대·선택칩). 위 글자는 잉크  → 11.1:1
//   primary      = 딥그린 #2E7D32  글자·아이콘용 (링크, 강조 텍스트)                 →  5.1:1
//
// 어두운 테마에서는 라임이 바탕과 11:1이라 글자로도 쓸 수 있어서 둘이 같아진다.
//
// 중립색은 파랑기를 빼고 살짝 따뜻하게 민다. 파랑 회색은 라임 옆에 두면 서로 튄다.

밝은 테마
  canvas #FFFFFF · surface #F4F5F2 · surfaceRaised #FFFFFF
  border #E6E8E3 · borderStrong #D2D5CD
  muted #6E7269 · bodyText #4E5348 · ink #16150F
  primary #2E7D32 · primaryFill #6FE26C · onPrimaryFill #16150F
  primaryWeakBg #E8F8E7 · primaryWeakFg #2E7D32
  success #2E7D32 · danger #D32F2F

어두운 테마
  canvas #16150F · surface #16150F · surfaceRaised #22211B
  border #2E2D26 · borderStrong #3D3C33
  muted #8E938A · bodyText #B9BDB3 · ink #F4F5F2
  primary #6FE26C · primaryFill #6FE26C · onPrimaryFill #16150F
  primaryWeakBg #1D2A1C · primaryWeakFg #6FE26C
  success #6FE26C · danger #FF6B70
```

**색을 새로 고를 때는 대비를 재고 넣는다.** 작은 글자는 4.5:1, 큰 글자·아이콘은 3:1이
최소다. `muted`를 #8B95A1처럼 흔한 값으로 두면 캡션이 3.3:1이라 밝은 데서 안 읽힌다 —
지금 값(#6E7269)은 카드 위에서도 4.5:1을 넘긴다.

```kotlin
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

**타이포그래피** (Pretendard, `res/font/pretendard_*.otf` — 굵기 5벌):

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

## 2-1. 다이얼로그 (`ShaDialog`)

앱의 다이얼로그는 전부 이 하나를 쓴다. M3 `AlertDialog`를 직접 쓰지 않는다 — 기본
모양이 제 색·제 여백을 들고 오고, 버튼이 오른쪽 아래 글자 버튼 두 개로 고정된다.

```
┌─────────────────────────────┐
│ 제목                    (h1) │
│                             │
│ 내용                        │
│                             │
│ 지우기            (있을 때만) │
│ ┌──────────┐ ┌──────────┐  │
│ │   취소   │ │   저장   │  │
│ └──────────┘ └──────────┘  │
└─────────────────────────────┘
```

- 바탕 `surfaceRaised`, 모서리 `20`, 안쪽 여백 `20`. **테두리·그림자 없음**
- 제목은 `h1`. 다이얼로그는 그 순간의 화면이라 화면 제목과 같은 급이다
- 아래 버튼은 **둘을 같은 크기로 나란히**. 높이 `48`, 각각 `weight 1f`
  - 확인 = 채운 버튼(`primaryFill`), 취소 = `surface` 바탕 + `ink` 글자
  - M3 기본처럼 오른쪽에 글자 버튼 두 개를 몰면 **누를 곳이 작고 둘의 무게가 같다**
- 지우기·해제 같은 **되돌리기 힘든 동작은 버튼 줄 위에 따로** 둔다. 확인 옆에 두면
  저장하려다 지우게 된다. 색은 `danger`, 모양은 글자 버튼
- 목록이 들어가는 다이얼로그(음식 추가 등)는 목록 높이를 `320` 안팎으로 묶는다.
  화면을 꽉 채우면 뒤가 안 보여서 어느 끼니에 담는 중인지 잊는다

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

색 값은 §1에 라이트와 나란히 적어뒀다. 규칙만 다시 적으면:

- `isSystemInDarkTheme()`으로 `ShaColors` 한 벌을 통째로 갈아끼운다
- **화면 코드에서 `Color(0x...)`를 절대 직접 쓰지 않는다.** 하드코딩한 색만 안 따라 바뀌어서
  다크에서 흰 카드에 흰 글씨가 난다
- 라임(`#6FE26C`)은 **양쪽 테마에서 같은 값**이다. 테마 따라 변하는 색은 브랜드색이 아니다
- `MaterialTheme.colorScheme`도 같이 채운다(`surface`·`background`·`surfaceContainer`까지).
  안 채우면 아직 안 고친 화면과 기본 다이얼로그·하단탭에 M3 기본 보라 틴트가 깔린다

## 8. 체크리스트

### 작업 단위

- [x] Color.kt / Shape.kt / Spacing.kt / Typography.kt 생성
- [x] Pretendard 폰트 연결 — 굵기 5벌, APK 12.7 → 17.7MB
- [x] 공용 컴포넌트 — `ShaScreen` / `ShaCard` / `StatTile` / `DashedAddButton` / `SubTabBar`
- [x] `ShaDialog` + 다이얼로그 **전면** 교체. `AlertDialog` 사용처 0개
- [x] 숫자 입력칸(`LabeledNumberField` / `NullableNumberField`)을 `ShaTextField` 모양으로.
      신체 화면과 음식 폼도 같이 따라왔다
- [ ] 나머지 컴포넌트 — `SetRow` / `ExerciseCard` / `RestTimerBar` / `WeightTrendCard` /
      `RecordInputRow` / `CopyableHistoryBlock`
- [x] TodayDietScreen 적용
- [x] TodayWorkoutScreen 적용 (운동 시작·종료 버튼과 휴식 타이머 상태 연동 확인)
- [x] BodyRecordScreen 적용 (추이 카드 3개 미만 숨김 처리 확인)
- [x] HistoryScreen 적용 — 복사 기능 회귀 확인 (드래그 선택·복사 버튼 둘 다)
- [x] 다크모드 색상 적용 및 확인
- [x] ExportScreen · SettingsScreen 재디자인

### 화면 하나를 고칠 때마다 세어보는 것

**세거나 재서 확인할 수 있는 것만 적는다.** "깔끔한가"는 확인할 수 없고, "테두리 버튼이
몇 개인가"는 확인할 수 있다. 하나라도 아니오면 안 고쳐진 것이다.

공통

- [ ] `OutlinedButton`이 화면에 하나도 없다
- [ ] 채운 버튼(`primaryFill`)이 화면에 하나뿐이다
- [ ] 라임을 **글자 색으로 쓴 곳이 없다** (밝은 테마에서 1.6:1이라 안 보인다)
- [ ] `HorizontalDivider`가 없다 — 나누는 일은 여백과 카드가 한다
- [ ] 모든 것이 좌우 같은 선에서 시작한다
- [ ] 글자 크기가 §1 타이포 표의 단계 안에만 있다
- [ ] 배경에 보라 틴트가 없다
- [ ] 숫자가 `Data` 스타일(등폭)이라 세로로 자릿수가 맞는다

다이얼로그

- [ ] `AlertDialog`를 직접 쓴 곳이 없다 (전부 `ShaDialog`)
- [ ] 아래 버튼이 같은 크기 둘이고, 확인만 채워져 있다
- [ ] 지우기가 확인 버튼 옆에 붙어 있지 않다

오늘 / 식단

- [ ] 오늘 먹은 kcal이 화면에서 제일 큰 글자다
- [ ] 끼니 칸이 비었을 때 "0kcal"을 안 적는다 (안 먹은 것과 구분이 안 된다)

오늘 / 운동

- [ ] `운동 시작`이 유일한 채운 버튼이다
- [ ] 운동 중일 때 경과 시간이 화면에서 제일 큰 글자다
- [ ] 세트의 무게·횟수가 `Data` 스타일이다
- [ ] 루틴 이름이 한 줄에 들어간다 (안 접힌다)

히스토리

- [ ] 날짜 카드의 kcal·볼륨이 `Data` 스타일이다
- [ ] 펼친 상세를 **드래그해서 실제로 복사할 수 있다** (`SelectionContainer`)
- [ ] "복사" 버튼이 기존 로직을 그대로 쓴다 (새로 만들지 않았다)

## 9. 이 문서를 고칠 때

화면을 보고 "뭔가 이상한데"라는 말이 나오면, **그 이상함을 숫자나 규칙으로 바꿔서 여기
적는다.** 그게 안 되면 아직 무엇이 문제인지 모르는 것이고, 그 상태로 고치면 다음에 또 같은
말이 나온다.

실제로 만들면서 나온 것들(§3 식단 작업):

- 스탯 타일의 delta가 두 줄이 되면 세 타일의 높이가 어긋난다 → **한 줄로 못 박는다**
- **껍데기만 바꾸면 창은 여전히 투박하다.** 음식 추가 창은 목록이 한 줄 보이기 전에
  조작 줄이 다섯이었다(탭 · 검색방식칩 · 검색칸 · 분류칩 · 선택삭제칩). 줄 수를 세는 것이
  "투박함"을 잴 수 있는 형태로 바꾸는 방법이다 → **목록 위 조작 줄은 세 줄 안쪽으로**
- 목록에 숫자 칸을 두면 **폭을 고정하고 오른쪽에 맞춘다.** 안 그러면 17과 132의 끝이
  어긋나 세로로 훑을 때 비교가 안 된다. 단위는 줄마다 붙이지 말고 머리글에 한 번만
- **아직 안 되는 기능은 늘 띄워두지 않는다.** AI 검색 칩이 화면 위쪽을 계속 차지하고
  있었다 → 검색 결과가 없을 때만 한 줄로 언급
- 화면을 두 개째 만들고 나서야 **날짜 헤더가 공용 부품이어야 한다는 게 보였다.** 하나만
  만들 때는 알 수 없다 — 두 번째에서 베껴 쓰게 되면 그때 빼는 게 맞다
- **"화면당 채운 버튼 하나"는 한 화면이 한 가지 일을 할 때의 규칙이다.** 내보내기처럼
  서로 독립적인 작업이 여럿 있는 화면은 **작업(카드)마다 하나씩** 둔다. 전부 회색으로
  낮추면 화면이 통째로 꺼져 보이고, 하나만 채우면 나머지 둘이 덜 중요한 것처럼 읽힌다
- **눌리지 않는 버튼을 미리 보여주지 않는다.** 파일을 만들기 전 "공유"·"파일로 저장"은
  회색으로 떠 있을 이유가 없다 — 만든 뒤에 나타나게 한다
- **복사해 갈 글은 꾸미지 않는다.** 색을 입히거나 줄마다 크기를 바꾸면 화면에서는
  예뻐 보여도 붙여넣으면 아무 뜻이 없다. 서식은 바깥 카드가 지고 안쪽은 등폭 한 벌로만
- **한 그림에 단위가 다른 값을 겹치지 않는다.** 체중(kg)과 체지방률(%)을 같은 축에
  올리면 어느 쪽 기울기도 사실이 아니다. 항목마다 카드를 따로 그린다
- **점이 셋 미만이면 그래프를 안 그린다.** 두 점을 이은 직선은 추세가 아니다.
  항목마다 따로 센다 — 체중만 열 번 적고 허리는 한 번 잰 경우가 흔하다
- **3분할 타일에는 단위를 붙이지 못한다.** "72.3kg"이 "72.3 k"로 잘린다. 라벨이 이미
  무엇인지 말하므로 단위는 빼고, 필요하면 아래 델타가 들고 간다(칼로리·체중 둘 다 그랬다)
- **부(副) 버튼 바탕에 `surface`를 쓰면 안 된다.** 어두운 테마에서 화면 배경과 같은
  색이라 버튼이 통째로 사라진다. `border`가 화면 바탕 위에서도 다이얼로그 바탕 위에서도
  한 단계 구분되는 유일한 값이다
- 카드 안 버튼은 **전부 글자 버튼**이다. 카드마다 채운 버튼을 두면 종목 수만큼 주 버튼이
  생겨서 화면에서 무엇을 먼저 눌러야 할지가 사라진다
- `testTag`는 **글자를 받는 칸 자체에** 붙어야 한다. 라벨까지 감싼 바깥 칸에 붙이면
  그 노드가 입력을 못 받아 화면 테스트가 깨진다(`ShaTextField.fieldModifier`)
- 3분할 타일에 `209 / 2550 kcal`은 안 들어간다 → 목표치와 단위를 빼고 "2341 남음"만.
  라벨이 이미 "칼로리"라 단위도 중복이었다
