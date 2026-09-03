# 린매스업 트래커 (SHA · SimpleHealthApp)

개인용 운동+식단 통합 기록 앱. 광고·서버·회원가입 없이 폰 안에만 데이터가 남는다.
기존에 쓰던 `운동일지.xlsx` 스프레드시트를 대체하면서, 그 워크플로우(엑셀로 보고 정리하기)는 그대로 유지하는 걸 목표로 만들었다.

## 화면 구성

**오늘** — 식단 / 운동 / 신체 세 개의 하위 탭. 전부 편집하는 즉시 자동 저장된다(0.7초 디바운스).

- **식단**: 날짜별로 끼니를 자유롭게 추가·삭제한다. 아침/점심 같은 고정 슬롯이 아니라 이름도 마음대로 바꿀 수 있다. 영양정보(칼로리/단백질/탄수/지방/당)는 몰라도 되며, 비워두면 메뉴 이름으로 대략값을 추정해 참고용으로만 보여준다(`MacroEstimator`). 음식은 **카탈로그에서 검색해 고른다**(분류 필터). 처음부터 60여 개가 깔려 있고, 직접 적은 음식도 자동으로 같은 목록에 쌓인다. 여러 끼니 구성을 "식단표"로 저장해두고 재사용 가능(운동 루틴과 같은 개념).
- **운동**: 종목을 **카탈로그에서 검색해 고른다**(부위 필터, 없으면 직접 등록). 종목마다 기록 방식이 정해져 있어서 고르는 순간 입력 칸이 바뀐다 — 벤치프레스는 무게×횟수, 플랭크는 시간(초), 트레드밀은 거리+시간. 루틴을 저장해두고 불러올 수 있다.
- **신체**: 체중/골격근량/체지방량/체지방률/허리둘레를 기록한다. 체지방률을 비워두면 체중·체지방량으로 자동 계산한다.

**히스토리** — 날짜별로 그날 기록을 요약해서 보여주고, 펼치면 세트/끼니별 상세 내역이 나온다. 선택·복사 가능한 텍스트로 나와 있어 그대로 붙여넣기 좋다.

**내보내기** — 두 가지가 있다.

- **엑셀(xlsx)** — 기존 `운동일지.xlsx`와 동일한 시트 구성(운동일지/종목목록/신체기록/식단체크/식단상세/사용법). 전체 기간 또는 날짜 범위만 골라 내보낸다. *사람이 열어서 보는 용도*.
- **백업·복원(JSON)** — 모든 기록을 파일 하나로 저장하고 되돌린다. 폰을 바꾸거나 기록을 옮길 때 쓴다. 덮어쓰기 직전 상태를 앱 안에 자동으로 남겨둬서 잘못 불러와도 되돌릴 수 있다.

## 자정 처리

앱을 켜둔 채 자정을 넘기거나, 다음날 다시 앱을 열면 "오늘" 날짜가 자동으로 갱신된다. 단, 사용자가 달력에서 직접 다른 날짜를 골라 편집 중이면 그 날짜는 유지된다.

## 프로젝트 구조

```
app/src/main/java/com/pkj/leanbulk/
├── data/                 # Room 엔티티 · DAO · Repository · 시드 데이터
│   ├── Entities.kt       # Exercise(종목 카탈로그), MealEntry, WorkoutExercise, BodyRecord ...
│   ├── Dao.kt
│   ├── Repository.kt
│   ├── SeedData.kt       # 시드 버전 + 기본 루틴/식단표 + 종목 카탈로그 50개
│   ├── MacroEstimator.kt # 영양정보 미입력 시 어림값 추정
│   ├── Converters.kt     # enum ↔ 문자열 (ordinal로 저장하면 값 추가 시 의미가 밀린다)
│   └── backup/           # 백업·복원용 JSON 스냅샷 포맷
│       ├── Snapshot.kt   # 와이어 포맷 DTO. 엔티티와 일부러 분리해둠
│       ├── SnapshotCodec.kt
│       └── BackupFiles.kt
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

## DB 스키마를 바꿀 때

예전에는 스키마를 바꾸면 기존 로컬 데이터가 초기화됐지만(`fallbackToDestructiveMigration`), 지금은 아니다. 대신 **버전을 올릴 때 마이그레이션을 직접 써야 한다**:

1. `AppDatabase`의 `@Database(version = ...)`과 `AppDatabase.VERSION`을 같이 올린다
2. `AppDatabase.MIGRATIONS`에 `Migration(n, n+1)`을 추가한다
3. 빌드하면 생기는 `app/schemas/.../<n+1>.json`을 **커밋한다** — 다음 마이그레이션의 기준선이다

빠뜨리면 `MigrationTest`가 잡는다 — 기존 사용자 앱이 열리다 죽는 걸 막기 위함이다. 개발 중에는 앱을 지웠다 깔면 그만이라 눈치채기 어렵고, 배포하고 나서야 터지는 종류다.

## 시드(기본 데이터)를 늘릴 때

`SeedData.VERSION`을 올리고 `Repository.applySeeds()`에 그 버전의 단계를 추가한다. 버전을 안 올리면 새 시드가 기존 사용자에게 영영 안 들어간다(적용된 버전이 `app_meta.seedVersion`에 기록돼 있어서 건너뛴다).

**이미 깔린 걸 다시 덮어쓰지 않도록 주의한다.** 루틴·식단표·목표처럼 사용자가 고치는 콘텐츠는 첫 설치에만 깔아야 하고, 재적용해도 되는 건 고유 키(slug) 기준 upsert로만 넣는다.
