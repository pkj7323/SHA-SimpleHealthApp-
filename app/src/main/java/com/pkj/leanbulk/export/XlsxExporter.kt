package com.pkj.leanbulk.export

import android.content.Context
import com.pkj.leanbulk.data.BodyRecord
import com.pkj.leanbulk.data.DietTarget
import com.pkj.leanbulk.data.Exercise
import com.pkj.leanbulk.data.ExerciseWithSets
import com.pkj.leanbulk.data.TrackingType
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.data.RoutineWithExercises
import com.pkj.leanbulk.data.SeedData
import com.pkj.leanbulk.data.WorkoutSet
import org.dhatim.fastexcel.Workbook
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 원본 `운동일지.xlsx`와 동일한 시트 구성(운동일지/종목목록/신체기록/식단체크/사용법),
 * 동일한 컬럼 순서·헤더 문구로 내보낸다. 수식 대신 미리 계산한 값을 그대로 쓴다.
 */
object XlsxExporter {

    fun export(
        context: Context,
        meals: List<MealEntry>,
        workouts: List<ExerciseWithSets>,
        bodyRecords: List<BodyRecord>,
        routines: List<RoutineWithExercises>,
        exercises: List<Exercise>,
        target: DietTarget?
    ): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File(exportDir, "운동일지_$stamp.xlsx")

        // 일단 임시 파일로 만든 뒤 Excel이 싫어하는 빈 요소를 걷어내고 최종 파일로 쓴다.
        val temp = File(exportDir, "raw_$stamp.xlsx")
        FileOutputStream(temp).use { out ->
            val wb = Workbook(out, "린매스업 트래커", "1.0")

            writeWorkoutSheet(wb, workouts)
            writeExerciseListSheet(wb, routines, exercises)
            writeBodyRecordSheet(wb, bodyRecords)
            writeDietCheckSheet(wb, meals, target)
            writeDietDetailSheet(wb, meals)
            writeHowToSheet(wb)

            wb.finish()
        }

        try {
            FileOutputStream(file).use { out -> sanitize(temp, out) }
        } finally {
            temp.delete()
        }
        return file
    }

    /**
     * fastexcel은 내용이 없어도 `<numFmts count="0">` 같은 빈 껍데기 요소를 써넣는데,
     * Excel은 이걸 손상된 파일로 보고 "복구할까요?" 창을 띄운다. 그 빈 요소만 지운다.
     * (xlsx는 zip이라 해당 XML 조각만 바꿔 다시 묶으면 된다.)
     */
    private val emptyElements = listOf(
        "<numFmts count=\"0\"></numFmts>",
        "<numFmts count=\"0\"/>",
        "<dxfs count=\"0\"></dxfs>",
        "<dxfs count=\"0\"/>",
        "<definedNames></definedNames>",
        "<definedNames/>"
    )

    private val patchedParts = setOf("xl/styles.xml", "xl/workbook.xml")

    /**
     * fastexcel은 스트리밍으로 쓰기 때문에 각 항목 앞머리에 크기를 0으로 적어둔다.
     * 그래서 앞에서부터 훑는 ZipInputStream은 쓰지 못하고,
     * 파일 끝의 목차(central directory)를 읽는 ZipFile로 열어야 한다.
     */
    private fun sanitize(source: File, out: java.io.OutputStream) {
        ZipFile(source).use { zip ->
            ZipOutputStream(out).use { zout ->
                for (entry in zip.entries()) {
                    var content = zip.getInputStream(entry).use { it.readBytes() }
                    if (entry.name in patchedParts) {
                        var xml = content.toString(Charsets.UTF_8)
                        emptyElements.forEach { xml = xml.replace(it, "") }
                        content = xml.toByteArray(Charsets.UTF_8)
                    }
                    // 압축 크기/CRC가 달라지므로 이름만 물려받은 새 항목으로 쓴다.
                    zout.putNextEntry(ZipEntry(entry.name))
                    zout.write(content)
                    zout.closeEntry()
                }
            }
        }
    }

    /**
     * 원본 시트는 한 줄에 "무게 1개 + 세트1~4 횟수"만 담을 수 있다.
     * 세트마다 무게가 다르거나 5세트 이상이면 같은 무게끼리 묶어 여러 줄로 나눠 쓴다 —
     * 컬럼 구조를 그대로 유지하면서 데이터도 잃지 않기 위함.
     */
    private fun writeWorkoutSheet(wb: Workbook, workouts: List<ExerciseWithSets>) {
        val ws = wb.newWorksheet("운동일지")
        ws.value(0, 0, "운동일지")
        ws.value(1, 0, "노란색 셀에 입력하세요. 볼륨은 자동 계산됩니다.")
        // "기록방식"은 맨 뒤에 덧붙인다. 앞 열의 위치가 그대로여야 기존 엑셀 수식이 안 깨진다.
        val headers = listOf(
            "날짜", "분할", "종목", "무게(kg)", "세트1", "세트2", "세트3", "세트4",
            "총 횟수", "볼륨(kg)", "RIR", "메모", "기록방식"
        )
        headers.forEachIndexed { c, h -> ws.value(2, c, h) }

        var row = 3
        workouts
            .sortedWith(compareBy({ it.exercise.date }, { it.exercise.orderIndex }))
            .forEach { item ->
                val doneSets = item.sets.sortedBy { it.setIndex }.filter { it.done }
                if (doneSets.isEmpty()) return@forEach

                val type = item.exercise.trackingType
                val usesWeight = type == TrackingType.WEIGHT_REPS || type == TrackingType.WEIGHT_TIME

                groupRows(doneSets, usesWeight).forEach { group ->
                    // 세트 칸에 들어갈 값의 뜻이 종목마다 다르다. 시간형에 횟수(0)를 쓰면 거짓 기록이 된다.
                    val values = group.sets.map { primaryValue(type, it) }
                    val total = values.sum()

                    ws.value(row, 0, item.exercise.date)
                    ws.value(row, 1, item.exercise.routineName)
                    ws.value(row, 2, item.exercise.exerciseName)
                    if (usesWeight) ws.value(row, 3, group.weight)
                    values.forEachIndexed { i, v -> ws.value(row, 4 + i, v) }
                    ws.value(row, 8, total)
                    // 볼륨은 무게×횟수일 때만 뜻이 있다.
                    if (type == TrackingType.WEIGHT_REPS) ws.value(row, 9, group.weight * total)

                    val memo = buildList {
                        if (item.exercise.memo.isNotBlank()) add(item.exercise.memo)
                        // 유산소는 세트 칸에 시간을 쓰므로 거리는 따로 적어준다.
                        if (type == TrackingType.DISTANCE_TIME) {
                            val meters = group.sets.sumOf { it.distanceM ?: 0.0 }
                            if (meters > 0) add("거리 ${meters.toInt()}m")
                        }
                    }.joinToString(" / ")
                    if (memo.isNotBlank()) ws.value(row, 11, memo)

                    ws.value(row, 12, unitLabel(type))
                    row++
                }
            }
    }

    /** 세트 칸에 적을 값. 종목의 기록 방식에 따라 횟수일 수도, 초일 수도 있다. */
    private fun primaryValue(type: TrackingType, set: WorkoutSet): Double = when (type) {
        TrackingType.WEIGHT_REPS, TrackingType.REPS -> set.reps.toDouble()
        TrackingType.TIME, TrackingType.WEIGHT_TIME, TrackingType.DISTANCE_TIME ->
            (set.durationSec ?: 0).toDouble()
    }

    /** 세트 칸의 숫자가 무슨 단위인지 알려준다. 없으면 시간이 횟수로 읽힌다. */
    private fun unitLabel(type: TrackingType): String = when (type) {
        TrackingType.WEIGHT_REPS -> "무게 × 횟수"
        TrackingType.REPS -> "횟수"
        TrackingType.TIME -> "시간(초)"
        TrackingType.WEIGHT_TIME -> "무게 + 시간(초)"
        TrackingType.DISTANCE_TIME -> "시간(초), 거리는 메모"
    }

    private class SetGroup(val weight: Double, val sets: List<WorkoutSet>)

    /** 무게를 안 쓰는 종목은 무게로 나눌 이유가 없으므로 순서대로만 자른다. */
    private fun groupRows(sets: List<WorkoutSet>, usesWeight: Boolean): List<SetGroup> =
        if (usesWeight) {
            sets.groupBy { it.weightKg }
                .flatMap { (weight, group) -> group.chunked(4).map { SetGroup(weight, it) } }
        } else {
            sets.chunked(4).map { SetGroup(0.0, it) }
        }

    private fun writeExerciseListSheet(
        wb: Workbook,
        routines: List<RoutineWithExercises>,
        exercises: List<Exercise>
    ) {
        val ws = wb.newWorksheet("종목목록")
        listOf("종목", "분할", "기본 세팅", "부위", "기구", "기록방식")
            .forEachIndexed { c, h -> ws.value(0, c, h) }

        val catalogByName = exercises.associateBy { it.name }
        var row = 1
        routines.forEach { item ->
            item.exercises.sortedBy { it.exercise.orderIndex }.forEach { (ex, _) ->
                val catalog = catalogByName[ex.exerciseName]
                ws.value(row, 0, ex.exerciseName)
                ws.value(row, 1, item.routine.name)
                ws.value(row, 2, ex.defaultSetting)
                ws.value(row, 3, catalog?.bodyPart ?: "")
                ws.value(row, 4, catalog?.equipment ?: "")
                ws.value(row, 5, unitLabel(ex.trackingType))
                row++
            }
        }
    }

    private fun writeBodyRecordSheet(wb: Workbook, records: List<BodyRecord>) {
        val ws = wb.newWorksheet("신체기록")
        ws.value(0, 0, "신체 변화 기록")
        ws.value(1, 0, "2주 간격 측정 권장 · 노란색 셀에 입력")
        val headers = listOf("날짜", "체중(kg)", "골격근량(kg)", "체지방량(kg)", "체지방률(%)", "허리둘레(cm)", "측정도구", "메모")
        headers.forEachIndexed { c, h -> ws.value(2, c, h) }

        records.sortedBy { it.date }.forEachIndexed { i, r ->
            val row = 3 + i
            ws.value(row, 0, r.date)
            ws.value(row, 1, r.weightKg)
            r.muscleMassKg?.let { ws.value(row, 2, it) }
            r.fatMassKg?.let { ws.value(row, 3, it) }
            r.fatPercent?.let { ws.value(row, 4, it) }
            r.waistCm?.let { ws.value(row, 5, it) }
            if (r.tool.isNotBlank()) ws.value(row, 6, r.tool)
            if (r.memo.isNotBlank()) ws.value(row, 7, r.memo)
        }
    }

    /**
     * 원본 시트의 컬럼(날짜~메모)은 그대로 두고, 새로 생긴 "당(g)"만 맨 뒤에 덧붙인다.
     * 기존 컬럼 위치를 밀지 않아야 예전 파일과 같은 방식으로 읽을 수 있다.
     */
    private fun writeDietCheckSheet(wb: Workbook, meals: List<MealEntry>, target: DietTarget?) {
        val ws = wb.newWorksheet("식단체크")
        ws.value(0, 0, "식단 체크")
        val goalKcal = target?.kcal ?: SeedData.TARGET_KCAL
        val goalProtein = target?.protein ?: SeedData.TARGET_PROTEIN
        ws.value(1, 0, "목표: ${goalKcal.roundToInt()}kcal / 단백질 ${goalProtein.roundToInt()}g")
        val headers = listOf("날짜", "칼로리", "단백질(g)", "탄수(g)", "지방(g)", "목표달성", "메모", "당(g)")
        headers.forEachIndexed { c, h -> ws.value(2, c, h) }

        val byDate = meals.groupBy { it.date }.toSortedMap()
        byDate.entries.forEachIndexed { i, (date, dayMeals) ->
            val row = 3 + i
            val eaten = dayMeals.filter { it.eaten }
            val kcal = eaten.sumOf { it.macros.kcal ?: 0.0 }
            val protein = eaten.sumOf { it.macros.protein ?: 0.0 }
            val carbs = eaten.sumOf { it.macros.carbs ?: 0.0 }
            val fat = eaten.sumOf { it.macros.fat ?: 0.0 }
            val sugar = eaten.sumOf { it.macros.sugar ?: 0.0 }
            val achieved = kotlin.math.abs(kcal - goalKcal) <= 150 && protein >= goalProtein - 10

            ws.value(row, 0, date)
            ws.value(row, 1, kcal)
            ws.value(row, 2, protein)
            ws.value(row, 3, carbs)
            ws.value(row, 4, fat)
            ws.value(row, 5, if (achieved) "O" else "X")

            val notes = buildList {
                val skipped = dayMeals.filter { !it.eaten }.joinToString(", ") { it.slotName }
                if (skipped.isNotBlank()) add("미섭취: $skipped")
                val unknown = eaten.count { it.macros.isEmpty }
                if (unknown > 0) add("영양정보 미입력 ${unknown}끼니")
            }
            if (notes.isNotEmpty()) ws.value(row, 6, notes.joinToString(" / "))
            ws.value(row, 7, sugar)
        }
    }

    /**
     * 원본 시트 5개는 그대로 두고, "뭐를 먹었는지"를 남기는 시트를 뒤에 더한다.
     * 하루 합계만 있는 식단체크 시트만으로는 메뉴를 되짚어볼 수 없기 때문.
     */
    private fun writeDietDetailSheet(wb: Workbook, meals: List<MealEntry>) {
        val ws = wb.newWorksheet("식단상세")
        ws.value(0, 0, "끼니별 식단 기록")
        ws.value(1, 0, "섭취 여부까지 그대로 남긴다. 영양정보를 모르는 끼니는 빈 칸으로 둔다.")
        val headers = listOf("날짜", "끼니", "메뉴", "섭취", "칼로리", "단백질(g)", "탄수(g)", "지방(g)", "당(g)")
        headers.forEachIndexed { c, h -> ws.value(2, c, h) }

        var row = 3
        meals.sortedWith(compareBy({ it.date }, { it.orderIndex })).forEach { m ->
            ws.value(row, 0, m.date)
            ws.value(row, 1, m.slotName)
            if (m.menuText.isNotBlank()) ws.value(row, 2, m.menuText)
            ws.value(row, 3, if (m.eaten) "O" else "X")
            m.macros.kcal?.let { ws.value(row, 4, it) }
            m.macros.protein?.let { ws.value(row, 5, it) }
            m.macros.carbs?.let { ws.value(row, 6, it) }
            m.macros.fat?.let { ws.value(row, 7, it) }
            m.macros.sugar?.let { ws.value(row, 8, it) }
            row++
        }
    }

    private fun writeHowToSheet(wb: Workbook) {
        val ws = wb.newWorksheet("사용법")
        val lines = listOf(
            "린매스업 트래커 앱 내보내기 안내",
            "[ 시트 구성 ]",
            "· 운동일지 — 앱에서 기록한 종목별 세트 기록 (체크한 세트만 집계)",
            "· 종목목록 — 앱에 등록된 루틴별 기본 종목/세팅",
            "· 신체기록 — 체중/체성분 측정 기록",
            "· 식단체크 — 하루 섭취 칼로리 및 매크로 합계 (체크한 끼니만 집계)",
            "· 식단상세 — 끼니별로 실제 먹은 메뉴와 영양정보 (새로 추가된 시트)",
            "· 세트마다 무게가 다르거나 5세트 이상이면 같은 무게끼리 묶어 여러 줄로 나눠 기록됩니다.",
            "· 이 파일은 앱에서 자동 생성되었습니다. 수식 대신 계산된 값이 그대로 들어 있습니다."
        )
        lines.forEachIndexed { i, line -> ws.value(i, 0, line) }
    }
}
