# 린매스업 트래커 (SHA · SimpleHealthApp)

개인용 운동+식단 통합 기록 앱. 광고·서버·회원가입 없이 폰 안에만 데이터가 남는다.
기존에 쓰던 `운동일지.xlsx` 스프레드시트를 대체하면서, 그 워크플로우(엑셀로 보고 정리하기)는 그대로 유지하는 걸 목표로 만들었다.

## 화면 구성

**오늘** — 식단 / 운동 / 신체 세 개의 하위 탭. 전부 편집하는 즉시 자동 저장된다(0.7초 디바운스).

- **식단**: 날짜별로 끼니를 자유롭게 추가·삭제한다. 아침/점심 같은 고정 슬롯이 아니라 이름도 마음대로 바꿀 수 있다. 영양정보(칼로리/단백질/탄수/지방/당)는 몰라도 되며, 비워두면 메뉴 이름으로 대략값을 추정해 참고용으로만 보여준다(`MacroEstimator`). 한 번 입력한 음식은 자동으로 "내 음식 목록"에 쌓여 다음부터 검색해 불러올 수 있다. 여러 끼니 구성을 "식단표"로 저장해두고 재사용 가능(운동 루틴과 같은 개념).
- **운동**: 종목별로 세트를 추가·삭제하며 무게/횟수/완료 체크를 기록한다. 루틴을 저장해두고 불러올 수 있다.
- **신체**: 체중/골격근량/체지방량/체지방률/허리둘레를 기록한다. 체지방률을 비워두면 체중·체지방량으로 자동 계산한다.

**히스토리** — 날짜별로 그날 기록을 요약해서 보여주고, 펼치면 세트/끼니별 상세 내역이 나온다. 선택·복사 가능한 텍스트로 나와 있어 그대로 붙여넣기 좋다.

**내보내기** — 기존 `운동일지.xlsx`와 동일한 시트 구성(운동일지/종목목록/신체기록/식단체크/식단상세/사용법)으로 xlsx를 생성한다. 전체 기간 또는 원하는 날짜 범위만 골라 내보낼 수 있다.

## 자정 처리

앱을 켜둔 채 자정을 넘기거나, 다음날 다시 앱을 열면 "오늘" 날짜가 자동으로 갱신된다. 단, 사용자가 달력에서 직접 다른 날짜를 골라 편집 중이면 그 날짜는 유지된다.

## 프로젝트 구조

```
app/src/main/java/com/pkj/leanbulk/
├── data/                 # Room 엔티티 · DAO · Repository · 시드 데이터
│   ├── Entities.kt       # MealEntry, WorkoutExercise, BodyRecord, DietTemplate, FoodItem, DietTarget ...
│   ├── Dao.kt
│   ├── Repository.kt
│   ├── SeedData.kt       # 첫 실행 시 깔리는 기본 루틴/식단표
│   ├── MacroEstimator.kt # 영양정보 미입력 시 어림값 추정
│   └── backup/           # 백업·동기화용 JSON 스냅샷 포맷 (아직 UI 없음)
│       ├── Snapshot.kt   # 와이어 포맷 DTO. 엔티티와 일부러 분리해둠
│       └── SnapshotCodec.kt
├── ui/                   # Compose 화면 + MainViewModel
│   ├── MainViewModel.kt  # 날짜 상태, 자동 저장(디바운스), 자정 감시
│   ├── TodayDietScreen.kt / TodayWorkoutScreen.kt / BodyRecordScreen.kt
│   ├── HistoryScreen.kt
│   └── ExportScreen.kt
├── export/
│   └── XlsxExporter.kt   # fastexcel로 생성 후 Excel 호환을 위해 빈 XML 요소를 후처리로 제거
└── MainActivity.kt
```

## 앞으로 할 것

- [`docs/ROADMAP.md`](docs/ROADMAP.md) — 만들려는 기능과 그 순서
- [`docs/SYNC-DESIGN.md`](docs/SYNC-DESIGN.md) — 폰-PC 동기화 저장 구조 설계

## 빌드 & 실행

```bash
./gradlew assembleDebug
```

Android Studio에서 열어 에뮬레이터/실기기로 Run 하면 된다.

### 테스트

```bash
./gradlew connectedDebugAndroidTest
```

백업 JSON 왕복이 무손실인지 검증한다. Room을 실제로 띄워야 해서 **에뮬레이터나 실기기가 연결돼 있어야** 한다.

## 내보낸 xlsx 빠르게 확인하기 (에뮬레이터 사용 시)

에뮬레이터의 `/sdcard/Download`는 PC 탐색기에서 직접 열 수 없으므로, 아래 스크립트로 최신 xlsx를 바탕화면으로 꺼내온다.

```bash
python scripts/pull_export.py
```

## 알려진 제약

- 디버그 빌드는 최적화가 꺼져 있어 에뮬레이터에서 실제 기기보다 느리게 느껴질 수 있다.
- DB 스키마가 바뀌면(버전업) 기존 로컬 데이터가 초기화된다 — 아직 마이그레이션을 붙이지 않았다.
