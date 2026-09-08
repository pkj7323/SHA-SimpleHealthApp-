package com.pkj.leanbulk.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pkj.leanbulk.BuildConfig
import com.pkj.leanbulk.data.ThemeMode
import com.pkj.leanbulk.ui.theme.ShaSpacing
import com.pkj.leanbulk.ui.theme.ShaTheme
import androidx.compose.runtime.DisposableEffect

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val restNotifications by vm.restNotifications.collectAsState()
    val themeMode by vm.themeMode.collectAsState()

    /**
     * 시스템 알림 권한 상태. **화면으로 돌아올 때마다 다시 본다** — 설정 앱에 갔다가 켜고
     * 돌아왔는데 여기가 "거부됨"으로 남아 있으면 사용자는 안 켜졌다고 여긴다.
     */
    var notificationsAllowed by remember { mutableStateOf(hasNotificationPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val colors = ShaTheme.colors
    val type = ShaTheme.type

    ShaScreen {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ShaSpacing.lg).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ShaSpacing.md)
    ) {
        Text(
            "설정",
            style = type.h1,
            color = colors.ink,
            modifier = Modifier.padding(top = ShaSpacing.lg, bottom = ShaSpacing.md)
        )

        ShaCard {
            Column(verticalArrangement = Arrangement.spacedBy(ShaSpacing.md)) {
                Text("화면", style = type.h2, color = colors.ink)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ShaSpacing.md)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { vm.setThemeMode(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("theme_" + mode.name)
                        )
                    }
                }
            }
        }

        ShaCard {
            Column(verticalArrangement = Arrangement.spacedBy(ShaSpacing.md)) {
                Text("휴식 알림", style = type.h2, color = colors.ink)

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // 스위치와 붙여놓으면 줄바꿈된 설명이 스위치 아래로 기어들어간다.
                    Column(Modifier.weight(1f).padding(end = ShaSpacing.md)) {
                        Text("휴식이 끝나면 알리기", style = type.body, color = colors.ink)
                        Text(
                            "다른 앱을 보고 있어도 알림창에 뜹니다. 꺼도 진동은 울립니다.",
                            style = type.caption,
                            color = colors.muted
                        )
                    }
                    Switch(
                        checked = restNotifications,
                        onCheckedChange = vm::setRestNotifications,
                        modifier = Modifier.testTag("setting_rest_notifications")
                    )
                }

                // 켜진 권한은 앱이 도로 뺏을 수 없다(안드로이드가 막는다). 상태만 보여주고
                // 실제로 끄고 켜는 일은 시스템 설정에 맡긴다.
                Column {
                    Text(
                        text = if (notificationsAllowed) "시스템 알림 권한: 허용됨" else "시스템 알림 권한: 꺼짐",
                        style = type.body,
                        color = if (notificationsAllowed) colors.ink else colors.danger
                    )
                    Text(
                        text = if (notificationsAllowed) {
                            "권한을 끄면 위 스위치가 켜져 있어도 알림이 안 뜹니다."
                        } else {
                            "이 상태에서는 알림이 안 뜹니다. 아래에서 켜 주세요."
                        },
                        style = type.caption,
                        color = colors.muted
                    )
                }
                ShaSecondaryButton(
                    text = "시스템 알림 설정 열기",
                    modifier = Modifier.fillMaxWidth().testTag("open_notification_settings"),
                    onClick = { context.startActivity(appNotificationSettings(context.packageName)) }
                )
            }
        }

        ShaCard {
            Column(verticalArrangement = Arrangement.spacedBy(ShaSpacing.xs)) {
                Text("앱 정보", style = type.h2, color = colors.ink)
                Text(
                    "심플 헬스 앱 " + BuildConfig.VERSION_NAME,
                    style = type.body,
                    color = colors.bodyText
                )
                Text(
                    "기록은 이 기기 안에만 있습니다. 백업은 내보내기 탭에서 파일로 만듭니다.",
                    style = type.caption,
                    color = colors.muted
                )
            }
        }

        Box(Modifier.padding(bottom = ShaSpacing.xxl))
    }
    }
}

/** 안드로이드 13 미만에는 알림 권한이라는 게 없다 — 늘 허용된 것으로 본다. */
fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 이 앱의 알림 설정 화면. 26 미만에는 그 화면이 없어서 앱 정보로 보낸다 —
 * 아무 데도 안 가는 버튼보다는 한 단계 위로라도 보내는 게 낫다.
 */
private fun appNotificationSettings(packageName: String): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
    }
