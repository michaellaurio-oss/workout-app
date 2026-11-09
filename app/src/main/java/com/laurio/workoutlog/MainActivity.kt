package com.laurio.workoutlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.laurio.workoutlog.ui.AppViewModel
import com.laurio.workoutlog.ui.AreasScreen
import com.laurio.workoutlog.ui.ExercisesScreen
import com.laurio.workoutlog.ui.HistoryScreen
import com.laurio.workoutlog.ui.InsightsIcon
import com.laurio.workoutlog.ui.InsightsScreen
import com.laurio.workoutlog.ui.LogScreen
import com.laurio.workoutlog.ui.Muted
import com.laurio.workoutlog.ui.NextScreen
import com.laurio.workoutlog.ui.WorkoutTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { WorkoutTheme { AppRoot(vm) } }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    NEXT("Next", Icons.Filled.Home),
    LOG("Log", Icons.Filled.Add),
    HISTORY("History", Icons.Filled.DateRange),
    INSIGHTS("Insights", InsightsIcon),
    EXERCISES("Exercises", Icons.Filled.Edit),
    AREAS("Areas", Icons.Filled.Settings),
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.NEXT) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.message.collect { m ->
            if (m != null) {
                vm.message.value = null
                snackbar.showSnackbar(m)
            }
        }
    }
    // Keep "today" right if the app stays open past midnight or comes back the next day.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshToday() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            vm.refreshToday()
        }
    }
    // Back goes to the Next tab before leaving the app.
    BackHandler(enabled = tab != Tab.NEXT) { tab = Tab.NEXT }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, maxLines = 1, softWrap = false) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.NEXT && vm.picked.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { tab = Tab.LOG },
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    text = { Text("Log ${vm.picked.size} picked") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val d = data
            if (d == null) {
                Text("Loading your workouts…", Modifier.padding(24.dp), color = Muted)
            } else {
                when (tab) {
                    Tab.NEXT -> NextScreen(d, vm)
                    Tab.LOG -> LogScreen(d, vm, onSaved = { tab = Tab.NEXT })
                    Tab.HISTORY -> HistoryScreen(d, vm)
                    Tab.INSIGHTS -> InsightsScreen(d, vm.today)
                    Tab.EXERCISES -> ExercisesScreen(d, vm)
                    Tab.AREAS -> AreasScreen(d, vm)
                }
            }
        }
    }
}
