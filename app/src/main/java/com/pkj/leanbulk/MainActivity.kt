package com.pkj.leanbulk

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pkj.leanbulk.ui.ExportScreen
import com.pkj.leanbulk.ui.HistoryScreen
import com.pkj.leanbulk.ui.MainViewModel
import com.pkj.leanbulk.ui.TodayDietScreen
import com.pkj.leanbulk.ui.TodayWorkoutScreen
import com.pkj.leanbulk.ui.ViewModelFactory
import com.pkj.leanbulk.ui.theme.LeanBulkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        ViewModelFactory((application as LeanBulkApp).repository)
    }

    /** 백그라운드에 있는 동안 자정이 지났을 수 있으므로 돌아올 때마다 날짜를 다시 확인한다. */
    override fun onResume() {
        super.onResume()
        viewModel.refreshToday()
    }

    /** 앱을 내릴 때 아직 대기 중인 자동 저장이 있으면 즉시 확정한다. */
    override fun onPause() {
        super.onPause()
        viewModel.flushPendingSaves()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LeanBulkTheme {
                Surface {
                    RootScreen(viewModel)
                }
            }
        }
    }
}

private enum class RootTab(val label: String) { TODAY("오늘"), HISTORY("히스토리"), EXPORT("내보내기") }

@Composable
private fun RootScreen(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(RootTab.TODAY) }
    // 오늘 탭의 식단/운동 선택은 여기서 들고 있어야 다른 탭에 갔다 와도 유지된다.
    var todaySubTab by rememberSaveable { mutableIntStateOf(0) }
    val toast by vm.toast.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == RootTab.TODAY,
                    onClick = { tab = RootTab.TODAY },
                    icon = { Icon(Icons.Filled.Restaurant, contentDescription = null) },
                    label = { Text(RootTab.TODAY.label) }
                )
                NavigationBarItem(
                    selected = tab == RootTab.HISTORY,
                    onClick = { tab = RootTab.HISTORY },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    label = { Text(RootTab.HISTORY.label) }
                )
                NavigationBarItem(
                    selected = tab == RootTab.EXPORT,
                    onClick = { tab = RootTab.EXPORT },
                    icon = { Icon(Icons.Filled.IosShare, contentDescription = null) },
                    label = { Text(RootTab.EXPORT.label) }
                )
            }
        }
    ) { padding ->
        Surface(modifier = Modifier.padding(padding)) {
            when (tab) {
                RootTab.TODAY -> TodayScreen(vm, todaySubTab) { todaySubTab = it }
                RootTab.HISTORY -> HistoryScreen(vm)
                RootTab.EXPORT -> ExportScreen(vm)
            }
        }
    }
}

@Composable
private fun TodayScreen(vm: MainViewModel, subTab: Int, onSubTabChange: (Int) -> Unit) {
    androidx.compose.foundation.layout.Column {
        TabRow(selectedTabIndex = subTab) {
            Tab(selected = subTab == 0, onClick = { onSubTabChange(0) }, text = { Text("식단") })
            Tab(selected = subTab == 1, onClick = { onSubTabChange(1) }, text = { Text("운동") })
        }
        if (subTab == 0) TodayDietScreen(vm) else TodayWorkoutScreen(vm)
    }
}
