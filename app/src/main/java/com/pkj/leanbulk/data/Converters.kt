package com.pkj.leanbulk.data

import androidx.room.TypeConverter

/**
 * enum을 이름 문자열로 저장한다. 순서(ordinal)로 저장하면 나중에 enum 중간에 값을
 * 끼워 넣는 순간 기존 데이터의 의미가 통째로 밀려버린다.
 */
class Converters {

    @TypeConverter
    fun trackingTypeToString(value: TrackingType): String = value.name

    @TypeConverter
    fun stringToTrackingType(value: String): TrackingType =
        runCatching { TrackingType.valueOf(value) }.getOrDefault(TrackingType.WEIGHT_REPS)

    @TypeConverter
    fun itemSourceToString(value: ItemSource): String = value.name

    @TypeConverter
    fun stringToItemSource(value: String): ItemSource =
        runCatching { ItemSource.valueOf(value) }.getOrDefault(ItemSource.USER)

    @TypeConverter
    fun goalTypeToString(value: GoalType): String = value.name

    @TypeConverter
    fun stringToGoalType(value: String): GoalType =
        runCatching { GoalType.valueOf(value) }.getOrDefault(GoalType.MAINTAIN)

    @TypeConverter
    fun macroSourceToString(value: MacroSource): String = value.name

    @TypeConverter
    fun stringToMacroSource(value: String): MacroSource =
        runCatching { MacroSource.valueOf(value) }.getOrDefault(MacroSource.ENTERED)

    @TypeConverter
    fun activityLevelToString(value: ActivityLevel): String = value.name

    @TypeConverter
    fun stringToActivityLevel(value: String): ActivityLevel =
        runCatching { ActivityLevel.valueOf(value) }.getOrDefault(ActivityLevel.MEDIUM)
}
