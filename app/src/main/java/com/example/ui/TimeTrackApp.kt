package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.R
import com.example.data.TimeLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun getLocalizedCategoryName(categoryName: String): String {
    return when (categoryName) {
        "工作", "work" -> stringResource(R.string.cat_work)
        "学习", "study" -> stringResource(R.string.cat_study)
        "运动", "sports" -> stringResource(R.string.cat_sports)
        "休息", "rest" -> stringResource(R.string.cat_rest)
        "娱乐", "entertainment" -> stringResource(R.string.cat_entertainment)
        "日常", "routine" -> stringResource(R.string.cat_routine)
        else -> categoryName
    }
}

@Composable
fun getLocalizedDescription(description: String, timerType: Int = 0): String {
    if (description.isEmpty()) {
        return when (timerType) {
            1 -> stringResource(R.string.desc_auto_timer_start)
            2 -> stringResource(R.string.desc_manual_record)
            else -> stringResource(R.string.desc_quick_timer)
        }
    }
    return when (description) {
        "自动计时" -> stringResource(R.string.desc_auto_timer)
        "自动开始计时" -> stringResource(R.string.desc_auto_timer_start)
        "手动记录" -> stringResource(R.string.desc_manual_record)
        "快捷计时", "Quick Timer" -> stringResource(R.string.desc_quick_timer)
        else -> description
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTrackApp(viewModel: TimeTrackViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val currentDateTrigger by viewModel.currentDateTrigger.collectAsStateWithLifecycle()
    var showManualDialog by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var showMonthTargetDialog by remember { mutableStateOf(false) }
    val currentStatsMonth by viewModel.currentStatsMonth.collectAsStateWithLifecycle()
    val statsPeriod by viewModel.statsPeriod.collectAsStateWithLifecycle()
    val currentStatsDay by viewModel.currentStatsDay.collectAsStateWithLifecycle()
    var isHistoryEditMode by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var tempImportLogs by remember { mutableStateOf<List<TimeLog>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonStr = viewModel.exportAllLogsToJson()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonStr.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, context.getString(R.string.toast_export_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_export_failed, e.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonStr = inputStream.bufferedReader(Charsets.UTF_8).readText()
                    val parsedLogs = viewModel.validateAndParseJson(jsonStr)
                    if (parsedLogs != null) {
                        tempImportLogs = parsedLogs
                        showImportConfirmDialog = true
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_validation_failed), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_read_failed, e.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCurrentDate()
                if (viewModel.selectedTab.value == 0) {
                    viewModel.checkAndTriggerAutoTimerOnSwitch()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentTabIconAndTitle = remember(selectedTab) {
        when (selectedTab) {
            0 -> Pair(Icons.Default.PlayCircle, R.string.tab_timer)
            1 -> Pair(Icons.Default.History, R.string.tab_history)
            2 -> Pair(Icons.Default.TrendingUp, R.string.tab_stats)
            3 -> Pair(Icons.Default.Settings, R.string.tab_settings)
            else -> Pair(Icons.Default.Timeline, R.string.app_name)
        }
    }

    val titleText = remember(selectedTab, currentDateTrigger) {
        if (selectedTab == 0) {
            val locale = Locale.getDefault()
            val pattern = if (locale.language == "zh") "MM月dd日 EEEE" else "EEE, MMM d"
            val sdf = SimpleDateFormat(pattern, locale)
            sdf.format(Date(currentDateTrigger))
        } else {
            null
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            viewModel.checkAndTriggerAutoTimerOnSwitch()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = currentTabIconAndTitle.first,
                                contentDescription = "Active Tab Icon",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = titleText ?: stringResource(id = currentTabIconAndTitle.second),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    if (selectedTab == 1) {
                        // History edit switcher button to the left of Add button
                        IconButton(
                            onClick = { isHistoryEditMode = !isHistoryEditMode },
                            modifier = Modifier.testTag("toggle_history_edit_mode")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isHistoryEditMode) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = if (isHistoryEditMode) stringResource(R.string.content_save) else stringResource(R.string.content_edit_toggle),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    if (selectedTab == 0 || selectedTab == 1) {
                        // Manual supplement entry button
                        IconButton(
                            onClick = { showManualDialog = true },
                            modifier = Modifier.testTag("open_manual_entry_dialog")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.manual_add_title),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else if (selectedTab == 2) {
                        // Month-specific target settings button (left of default target settings icon)
                        val currentMonthLabel = remember(statsPeriod, currentStatsMonth, currentStatsDay) {
                            val calendar = if (statsPeriod == "今天") currentStatsDay else currentStatsMonth
                            val locale = Locale.getDefault()
                            val pattern = if (locale.language == "zh") "yyyy年M月" else "MMM yyyy"
                            SimpleDateFormat(pattern, locale).format(calendar.time)
                        }

                        FilledTonalButton(
                            onClick = { showMonthTargetDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("open_month_specific_targets_dialog")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentMonthLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Target settings button
                        IconButton(
                            onClick = { showTargetDialog = true },
                            modifier = Modifier.testTag("open_targets_dialog")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrackChanges,
                                    contentDescription = stringResource(R.string.monthly_target_setting),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else if (selectedTab == 3) {
                        // Import data button
                        IconButton(
                            onClick = { importLauncher.launch("application/json") },
                            modifier = Modifier.testTag("open_import_file_picker")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = stringResource(R.string.btn_import_data),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        // Export data button
                        IconButton(
                            onClick = { exportLauncher.launch("time_track_backup.json") },
                            modifier = Modifier.testTag("open_export_file_picker")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = stringResource(R.string.btn_export_data),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(64.dp),
                tonalElevation = 8.dp
            ) {
                val tabs = listOf(
                    Triple(0, if (selectedTab == 0) Icons.Default.PlayCircle else Icons.Outlined.Schedule, R.string.tab_timer),
                    Triple(1, if (selectedTab == 1) Icons.Default.History else Icons.Outlined.History, R.string.tab_history),
                    Triple(2, if (selectedTab == 2) Icons.Default.TrendingUp else Icons.Outlined.TrendingUp, R.string.tab_stats),
                    Triple(3, if (selectedTab == 3) Icons.Default.Settings else Icons.Outlined.Settings, R.string.tab_settings)
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { (index, icon, labelResId) ->
                        val isSelected = selectedTab == index
                        val tag = when (index) {
                            0 -> "nav_tab_timer"
                            1 -> "nav_tab_history"
                            2 -> "nav_tab_stats"
                            else -> "nav_tab_settings"
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag(tag)
                                .clickable { viewModel.selectTab(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            val contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                if (isSystemInDarkTheme()) Color(0xFF8E8E93) else Color(0xFF757575)
                            }

                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = stringResource(labelResId),
                                    tint = contentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(labelResId),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor,
                                    maxLines = 1,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> TimerScreen(viewModel)
                1 -> HistoryScreen(viewModel = viewModel, isEditMode = isHistoryEditMode)
                2 -> StatsScreen(viewModel)
                3 -> SettingsScreen(viewModel)
            }
        }
    }

    if (showManualDialog) {
        ManualRecordDialog(
            onDismiss = { showManualDialog = false },
            onSave = { category, desc, mins, startTimeMillis ->
                viewModel.addManualLog(category, desc, mins, startTimeMillis)
                showManualDialog = false
            }
        )
    }

    if (showTargetDialog) {
        MonthlyTargetSettingsDialog(
            viewModel = viewModel,
            monthKey = null,
            onDismiss = { showTargetDialog = false }
        )
    }

    if (showMonthTargetDialog) {
        val statsMonthKey = remember(statsPeriod, currentStatsMonth, currentStatsDay) {
            val calendar = if (statsPeriod == "今天") currentStatsDay else currentStatsMonth
            java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(calendar.time)
        }
        MonthlyTargetSettingsDialog(
            viewModel = viewModel,
            monthKey = statsMonthKey,
            onDismiss = { showMonthTargetDialog = false }
        )
    }

    if (showImportConfirmDialog && tempImportLogs != null) {
        AlertDialog(
            onDismissRequest = { 
                showImportConfirmDialog = false
                tempImportLogs = null
            },
            title = { Text(stringResource(R.string.dialog_import_title)) },
            text = { 
                Text(
                    stringResource(
                        R.string.dialog_import_message,
                        tempImportLogs?.size ?: 0
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val logs = tempImportLogs
                        if (logs != null) {
                            viewModel.clearAndImportLogs(
                                logs = logs,
                                onSuccess = {
                                    Toast.makeText(context, context.getString(R.string.toast_import_success), Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { err ->
                                    Toast.makeText(context, context.getString(R.string.toast_import_failed, err), Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                        showImportConfirmDialog = false
                        tempImportLogs = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.dialog_import_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showImportConfirmDialog = false
                        tempImportLogs = null
                    }
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
fun TimerScreen(viewModel: TimeTrackViewModel) {
    val isRunning by viewModel.isTimerRunning.collectAsStateWithLifecycle()
    val activeCategory by viewModel.timerCategory.collectAsStateWithLifecycle()
    val activeDesc by viewModel.timerDescription.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.timerElapsedSeconds.collectAsStateWithLifecycle()
    val allLogs by viewModel.allLogs.collectAsStateWithLifecycle()
    val monthlyCategoryTargets by viewModel.timerMonthTargets.collectAsStateWithLifecycle()
    val defaultCategory by viewModel.defaultCategory.collectAsStateWithLifecycle()
    val selectedCategoryFromVm by viewModel.selectedTrackerCategory.collectAsStateWithLifecycle()

    val timerType by viewModel.timerType.collectAsStateWithLifecycle()
    val currentDateTrigger by viewModel.currentDateTrigger.collectAsStateWithLifecycle()

    var descField by remember { mutableStateOf("") }
    val selectedCat = if (isRunning) activeCategory else selectedCategoryFromVm

    LaunchedEffect(isRunning, activeCategory, activeDesc) {
        if (isRunning) {
            descField = activeDesc
        }
    }

    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(isRunning) {
        if (wasRunning && !isRunning) {
            descField = ""
        }
        wasRunning = isRunning
    }

    val currentMonthSelectedCatMins = remember(allLogs, selectedCat, currentDateTrigger) {
        val now = java.util.Calendar.getInstance().apply { timeInMillis = currentDateTrigger }
        val currentYear = now.get(java.util.Calendar.YEAR)
        val currentMonth = now.get(java.util.Calendar.MONTH)
        allLogs.filter { log ->
            if (log.category == selectedCat) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = log.startTime }
                cal.get(java.util.Calendar.YEAR) == currentYear && cal.get(java.util.Calendar.MONTH) == currentMonth
            } else {
                false
            }
        }.sumOf { it.durationMinutes }
    }

    // Dynamic stats: today's total minutes of selected category
    val todaySelectedCatMins = remember(allLogs, selectedCat, currentDateTrigger) {
        viewModel.getCategoryTimeTodayMinutes(selectedCat, currentDateTrigger)
    }

    // Dynamic stats: newest record details of selected category (only search for today's logs)
    val latestLog = remember(allLogs, selectedCat, currentDateTrigger) {
        allLogs.firstOrNull { it.category == selectedCat && viewModel.isToday(it.startTime, currentDateTrigger) }
    }

    val noRecordsPlaceholder = stringResource(R.string.label_no_records)
    val latestLogTimeString = remember(latestLog, noRecordsPlaceholder) {
        if (latestLog != null) {
            val locale = Locale.getDefault()
            val format = SimpleDateFormat("HH:mm:ss", locale)
            format.format(Date(latestLog.updatedTime))
        } else {
            noRecordsPlaceholder
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Upper dynamic dashboard card displaying total stats, last entry and CONTINUE action
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${getLocalizedCategoryName(selectedCat)} | ${stringResource(R.string.stats_today)}${stringResource(R.string.total_suffix)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${todaySelectedCatMins}${stringResource(R.string.mins_suffix)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = getCategoryColor(selectedCat)
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.last_changed_time),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Text(
                            text = latestLogTimeString,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                val targetHours = monthlyCategoryTargets[selectedCat] ?: 40.0f
                val targetMins = (targetHours * 60).toLong()
                val progressPercent = if (targetMins > 0) {
                    kotlin.math.floor((currentMonthSelectedCatMins.toDouble() / targetMins) * 100.0).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                val progressCoerced = progressPercent / 100f

                val actualHourPart = currentMonthSelectedCatMins / 60
                val actualMinPart = currentMonthSelectedCatMins % 60

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (targetMins > 0) stringResource(R.string.monthly_progress_title) else stringResource(R.string.monthly_total_title),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (targetMins > 0) "${currentMonthSelectedCatMins}${stringResource(R.string.mins_suffix)} / ${targetMins}${stringResource(R.string.mins_suffix)}" else "${currentMonthSelectedCatMins}${stringResource(R.string.mins_suffix)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (targetMins > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(if (progressCoerced > 0f) progressCoerced else 0.005f)
                                        .clip(CircleShape)
                                        .background(getCategoryColor(selectedCat))
                                )
                            }
                            Text(
                                text = "$progressPercent%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = getCategoryColor(selectedCat)
                            )
                        }
                    }
                }

                if (latestLog != null && !isRunning) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            viewModel.startTimerFromPast(
                                latestLog.category,
                                latestLog.description,
                                latestLog.startTime,
                                latestLog.id,
                                latestLog.timerType
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = getCategoryColor(selectedCat),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("continue_timer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${stringResource(R.string.continue_btn)}: [${getLocalizedCategoryName(latestLog.category)}] ${getLocalizedDescription(latestLog.description, latestLog.timerType)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // TIMER VISUAL CIRCLE
        Box(
            modifier = Modifier
                .testTag("timer_stopwatch_container")
                .then(if (isRunning) Modifier.size(240.dp) else Modifier.size(0.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isRunning) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse_ring")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                val activeColor = getCategoryColor(selectedCat)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .drawBehind {
                            val strokeWidth = 8.dp.toPx()
                            val pulseRad = (size.minDimension / 2) * pulseScale
                            drawCircle(
                                color = activeColor.copy(alpha = 0.08f),
                                radius = pulseRad,
                                center = center
                            )
                            drawCircle(
                                color = activeColor.copy(alpha = 0.2f),
                                radius = (size.minDimension - strokeWidth) / 2,
                                center = center,
                                style = Stroke(width = strokeWidth)
                            )
                        }
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(selectedCat),
                        contentDescription = null,
                        tint = activeColor,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val hrs = elapsedSeconds / 3600
                    val mins = (elapsedSeconds % 3600) / 60
                    val secs = elapsedSeconds % 60
                    val timeString = String.format("%02d:%02d:%02d", hrs, mins, secs)

                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = getLocalizedCategoryName(activeCategory),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = activeColor
                    )
                }
            }
        }

        // DESCRIPTION ROW
        OutlinedTextField(
            value = descField,
            onValueChange = {
                descField = it
                if (isRunning) {
                    viewModel.updateTimerDescription(it)
                }
            },
            placeholder = {
                val placeholderRes = if (isRunning) {
                    if (timerType == 1) R.string.desc_auto_timer_start else R.string.desc_quick_timer
                } else {
                    R.string.hint_desc
                }
                Text(stringResource(placeholderRes))
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("timer_desc_input"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = getCategoryColor(selectedCat)
            )
        )

        // CHIPS FOR CATEGORY SELECT
        if (!isRunning) {
            Text(
                text = stringResource(R.string.manual_label_category),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val chunks = Categories.chunked(3)
                chunks.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { cat ->
                            val isSelected = selectedCat == cat.name
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) cat.color else cat.lightColor)
                                    .clickable { viewModel.updateSelectedCategory(cat.name) }
                                    .testTag("category_chip_${cat.id}")
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = cat.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else cat.color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = getLocalizedCategoryName(cat.name),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // RUNNING ACTIONS OR START BUTTON
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isRunning) {
                Button(
                    onClick = { viewModel.startTimer(selectedCat, descField) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = getCategoryColor(selectedCat)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_timer_button")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_start_tracking),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.resetTimer() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("cancel_timer_button"),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { viewModel.saveActiveTimer() },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = getCategoryColor(selectedCat)
                    ),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(56.dp)
                        .testTag("stop_timer_button")
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_stop_tracking),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(viewModel: TimeTrackViewModel, isEditMode: Boolean) {
    val logs by viewModel.historyLogs.collectAsStateWithLifecycle()
    val currentHistoryMonth by viewModel.currentHistoryMonth.collectAsStateWithLifecycle()
    val isLoaded by viewModel.isLoaded.collectAsStateWithLifecycle()

    var editingLog by remember { mutableStateOf<TimeLog?>(null) }
    var deletingLogId by remember { mutableStateOf<Int?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val groupedLogs = remember(logs) {
        logs.groupBy { it.belongDate }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            initialYear = currentHistoryMonth.get(Calendar.YEAR),
            initialMonth = currentHistoryMonth.get(Calendar.MONTH),
            onDismiss = { showMonthPicker = false },
            onConfirm = { year, month ->
                viewModel.setHistoryMonth(year, month)
                showMonthPicker = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Month Switcher Row
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.changeHistoryMonth(-1) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.content_prev_month),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                val today = remember(currentHistoryMonth) { Calendar.getInstance() }
                val isCurrentHistoryMonth = today.get(Calendar.YEAR) == currentHistoryMonth.get(Calendar.YEAR) &&
                        today.get(Calendar.MONTH) == currentHistoryMonth.get(Calendar.MONTH)

                val historyMonthText = remember(currentHistoryMonth) {
                    val locale = Locale.getDefault()
                    val pattern = if (locale.language == "zh") "yyyy年M月" else "MMMM yyyy"
                    SimpleDateFormat(pattern, locale).format(currentHistoryMonth.time)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (!isCurrentHistoryMonth) {
                        IconButton(
                            onClick = { viewModel.resetHistoryMonthToCurrent() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = stringResource(R.string.content_reset_to_current_month),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showMonthPicker = true
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = historyMonthText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrentHistoryMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.content_select_month),
                            tint = if (isCurrentHistoryMonth) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.changeHistoryMonth(1) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.content_next_month),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (!isLoaded) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth())
        } else if (logs.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.empty_logs_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.empty_logs_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                groupedLogs.forEach { (day, logsInDay) ->
                    item(key = "header_$day") {
                        val firstLog = logsInDay.firstOrNull()
                        val headerText = if (firstLog != null) {
                            remember(firstLog.startTime) {
                                val locale = java.util.Locale.getDefault()
                                val pattern = if (locale.language == "zh") "MM月dd日 EEEE" else "EEE, MMM d"
                                SimpleDateFormat(pattern, locale).format(Date(firstLog.startTime))
                            }
                        } else {
                            day
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = headerText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    item(key = "card_$day") {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Column {
                                logsInDay.forEachIndexed { index, log ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                    HistoryListItem(
                                        log = log,
                                        isEditMode = isEditMode,
                                        onEdit = { editingLog = log },
                                        onDelete = { deletingLogId = log.id }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingLog?.let { logToEdit ->
        EditTimeLogDialog(
            log = logToEdit,
            onDismiss = { editingLog = null },
            onSave = { updated ->
                viewModel.updateLog(updated)
                editingLog = null
            }
        )
    }

    deletingLogId?.let { logIdToDelete ->
        DeleteConfirmationDialog(
            onDismiss = { deletingLogId = null },
            onConfirm = {
                viewModel.deleteLog(logIdToDelete)
                deletingLogId = null
            }
        )
    }
}

@Composable
fun HistoryListItem(log: TimeLog, isEditMode: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val catColor = getCategoryColor(log.category)
    val catIcon = getCategoryIcon(log.category)

    val minsSuf = stringResource(R.string.mins_suffix)
    val durationText = remember(log.durationMinutes, minsSuf) { "${log.durationMinutes}$minsSuf" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(catColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = catIcon,
                        contentDescription = null,
                        tint = catColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    val startStr = timeFormatter.format(Date(log.startTime))
                    val endStr = timeFormatter.format(Date(log.startTime + log.durationMinutes * 60 * 1000L))
                    Text(
                        text = "$startStr - $endStr",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = getLocalizedCategoryName(log.category),
                            style = MaterialTheme.typography.bodySmall,
                            color = catColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = getLocalizedDescription(log.description, log.timerType),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = catColor
                )

                if (isEditMode) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("edit_log_${log.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit entry",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_log_${log.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete entry",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
}

@Composable
fun StatsScreen(viewModel: TimeTrackViewModel) {
    val statsPeriod by viewModel.statsPeriod.collectAsStateWithLifecycle()
    val rawLogs by viewModel.filteredLogsForStats.collectAsStateWithLifecycle()
    val monthlyCategoryTargets by viewModel.statsMonthTargets.collectAsStateWithLifecycle()
    val currentStatsMonth by viewModel.currentStatsMonth.collectAsStateWithLifecycle()
    val allLogs by viewModel.allLogs.collectAsStateWithLifecycle()

    var selectedDayForDetail by remember { mutableStateOf<Int?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }

    if (showMonthPicker) {
        MonthPickerDialog(
            initialYear = currentStatsMonth.get(Calendar.YEAR),
            initialMonth = currentStatsMonth.get(Calendar.MONTH),
            onDismiss = { showMonthPicker = false },
            onConfirm = { year, month ->
                viewModel.setStatsMonth(year, month)
                showMonthPicker = false
            }
        )
    }

    val currentYear = currentStatsMonth.get(Calendar.YEAR)
    val currentMonthIdx = currentStatsMonth.get(Calendar.MONTH) // 0-based
    val currentMonthName = remember(currentStatsMonth) {
        val locale = Locale.getDefault()
        val pattern = if (locale.language == "zh") "yyyy年M月" else "MMMM yyyy"
        SimpleDateFormat(pattern, locale).format(currentStatsMonth.time)
    }

    val currentStatsDay by viewModel.currentStatsDay.collectAsStateWithLifecycle()

    val todayCategoryTotals = remember(allLogs, currentStatsDay) {
        val totals = mutableMapOf<String, Float>()
        Categories.forEach { cat -> totals[cat.name] = 0f }
        val targetYear = currentStatsDay.get(Calendar.YEAR)
        val targetDayOfYear = currentStatsDay.get(Calendar.DAY_OF_YEAR)
        val c = Calendar.getInstance()
        allLogs.forEach { log ->
            c.timeInMillis = log.startTime
            if (c.get(Calendar.YEAR) == targetYear && c.get(Calendar.DAY_OF_YEAR) == targetDayOfYear) {
                totals[log.category] = (totals[log.category] ?: 0f) + log.durationMinutes
            }
        }
        totals
    }

    val activeYear = remember(statsPeriod, currentStatsMonth, currentStatsDay) {
        if (statsPeriod == "今天") currentStatsDay.get(Calendar.YEAR) else currentStatsMonth.get(Calendar.YEAR)
    }
    val activeMonthIdx = remember(statsPeriod, currentStatsMonth, currentStatsDay) {
        if (statsPeriod == "今天") currentStatsDay.get(Calendar.MONTH) else currentStatsMonth.get(Calendar.MONTH)
    }

    val monthlyCategoryTotalsFromAllLogs = remember(allLogs, activeYear, activeMonthIdx) {
        val totals = mutableMapOf<String, Float>()
        Categories.forEach { cat -> totals[cat.name] = 0f }
        val c = Calendar.getInstance()
        allLogs.forEach { log ->
            c.timeInMillis = log.startTime
            if (c.get(Calendar.YEAR) == activeYear && c.get(Calendar.MONTH) == activeMonthIdx) {
                totals[log.category] = (totals[log.category] ?: 0f) + log.durationMinutes
            }
        }
        totals
    }

    val totalMinutes = remember(rawLogs) {
        rawLogs.sumOf { it.durationMinutes }.toFloat()
    }

    val categoryTotals = remember(rawLogs) {
        val totals = mutableMapOf<String, Float>()
        Categories.forEach { cat -> totals[cat.name] = 0f }
        rawLogs.forEach { log ->
            totals[log.category] = (totals[log.category] ?: 0f) + log.durationMinutes
        }
        totals
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {


        // Period single choice segmented tab
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            val periods = listOf("今天", "按月")
            periods.forEachIndexed { index, label ->
                val localizedLabel = when (label) {
                    "今天" -> stringResource(R.string.stats_today)
                    "按月" -> stringResource(R.string.stats_monthly)
                    else -> label
                }
                SegmentedButton(
                    selected = statsPeriod == label,
                    onClick = { viewModel.setStatsPeriod(label) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                    modifier = Modifier.testTag("period_tab_$index")
                ) {
                    Text(localizedLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (statsPeriod == "今天") {
            val context = LocalContext.current
            val statsDayFormatted = remember(currentStatsDay) {
                val locale = Locale.getDefault()
                val pattern = if (locale.language == "zh") "yyyy年M月d日" else "EEEE, MMMM d, yyyy"
                SimpleDateFormat(pattern, locale).format(currentStatsDay.time)
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val todayCal = Calendar.getInstance()
                    val isToday = todayCal.get(Calendar.YEAR) == currentStatsDay.get(Calendar.YEAR) &&
                            todayCal.get(Calendar.DAY_OF_YEAR) == currentStatsDay.get(Calendar.DAY_OF_YEAR)

                    if (!isToday) {
                        IconButton(
                            onClick = { viewModel.resetStatsDayToCurrent() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = stringResource(R.string.content_reset_to_current_month),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val currentY = currentStatsDay.get(Calendar.YEAR)
                                val currentM = currentStatsDay.get(Calendar.MONTH)
                                val currentD = currentStatsDay.get(Calendar.DAY_OF_MONTH)
                                
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        viewModel.setStatsDay(year, month, dayOfMonth)
                                    },
                                    currentY,
                                    currentM,
                                    currentD
                                ).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statsDayFormatted,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.content_select_date),
                            tint = if (isToday) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        } else {
            // MONTHLY CALENDAR GRID
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.changeStatsMonth(-1) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.content_prev_month),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        val today = remember(currentStatsMonth) { Calendar.getInstance() }
                        val isCurrentStatsMonth = today.get(Calendar.YEAR) == currentStatsMonth.get(Calendar.YEAR) &&
                                today.get(Calendar.MONTH) == currentStatsMonth.get(Calendar.MONTH)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (!isCurrentStatsMonth) {
                                IconButton(
                                    onClick = { viewModel.resetStatsMonthToCurrent() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = stringResource(R.string.content_reset_to_current_month),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        showMonthPicker = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = currentMonthName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrentStatsMonth) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = stringResource(R.string.content_select_month),
                                    tint = if (isCurrentStatsMonth) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.changeStatsMonth(1) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.content_next_month),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Week headers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val weeks = listOf("一", "二", "三", "四", "五", "六", "日")
                        weeks.forEach { wk ->
                            val localizedWk = when (wk) {
                                "一" -> stringResource(R.string.week_monday)
                                "二" -> stringResource(R.string.week_tuesday)
                                "三" -> stringResource(R.string.week_wednesday)
                                "四" -> stringResource(R.string.week_thursday)
                                "五" -> stringResource(R.string.week_friday)
                                "六" -> stringResource(R.string.week_saturday)
                                "日" -> stringResource(R.string.week_sunday)
                                else -> wk
                            }
                            Text(
                                text = localizedWk,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Dynamically compute calendar parameters
                    val tempCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, currentYear)
                        set(Calendar.MONTH, currentMonthIdx)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                    val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
                    val startOffset = ((dayOfWeek - 2) + 7) % 7 // 0 (Monday) to 6 (Sunday)
                    val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val totalCells = startOffset + daysInMonth
                    val rowCount = (totalCells + 6) / 7

                    // Log totals by DAY_OF_MONTH
                    val dayMinutesMap = remember(rawLogs) {
                        val c = Calendar.getInstance()
                        rawLogs.groupBy {
                            c.timeInMillis = it.startTime
                            c.get(Calendar.DAY_OF_MONTH)
                        }.mapValues { entry ->
                            entry.value.sumOf { it.durationMinutes }
                        }
                    }

                    val todayCal = Calendar.getInstance()
                    val isCurrentViewingTodayMonth = todayCal.get(Calendar.YEAR) == currentYear && todayCal.get(Calendar.MONTH) == currentMonthIdx
                    val todayDay = if (isCurrentViewingTodayMonth) todayCal.get(Calendar.DAY_OF_MONTH) else -1

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (r in 0 until rowCount) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (c in 0 until 7) {
                                    val cellIndex = r * 7 + c
                                    val dayNum = cellIndex - startOffset + 1

                                    if (dayNum in 1..daysInMonth) {
                                        val dayMins = dayMinutesMap[dayNum] ?: 0L
                                        val isToday = dayNum == todayDay

                                        // 每个格子，有记录的格子颜色和普通格子一样
                                        val minsColor = when {
                                            dayMins <= 0L -> Color.Transparent
                                            dayMins < 30L -> Color(0xFFEF4444)      // 小于30显示红色
                                            dayMins < 60L -> Color(0xFFF97316)      // 大于等于30小于60显示橙色
                                            dayMins < 90L -> Color(0xFF10B981)      // 大于等于60小于90显示绿色
                                            else -> Color(0xFF3B82F6)               // 大于等于90显示蓝色
                                        }

                                        val cellBorder = if (isToday) {
                                            BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                                        } else null

                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(0.8f)
                                                .clickable { selectedDayForDetail = dayNum },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                            border = cellBorder
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(vertical = 4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.SpaceEvenly
                                            ) {
                                                // 固定上部分显示日期
                                                Text(
                                                    text = dayNum.toString(),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                // 下部分显示分钟数，分钟数只需要显示数字，根据分钟数显示不同颜色
                                                Text(
                                                    text = if (dayMins > 0L) dayMins.toString() else "",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = minsColor
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // PIE CHART CARD (Month logs or Today's logs)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (statsPeriod == "今天") stringResource(R.string.stats_title_today) else stringResource(R.string.stats_title_monthly),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CustomRingPieChart(
                        categoryTotals = categoryTotals,
                        totalMinutes = totalMinutes,
                        modifier = Modifier.fillMaxSize()
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.total_suffix),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${totalMinutes.toInt()}${stringResource(R.string.mins_suffix)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Grid of Categories and percentage progress bars with monthly target tracking
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Categories.forEach { category ->
                        val targetHours = monthlyCategoryTargets[category.name] ?: 40.0f
                        val targetMins = targetHours * 60f

                        if (statsPeriod == "今天") {
                            val todayMins = todayCategoryTotals[category.name] ?: 0f
                            if (todayMins > 0f) {
                                val monthMins = monthlyCategoryTotalsFromAllLogs[category.name] ?: 0f
                                val progressPercent = if (targetMins > 0f) {
                                    kotlin.math.floor((monthMins.toDouble() / targetMins.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                val progressFactor = progressPercent / 100f
                                val progressPctText = "$progressPercent%"

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.widthIn(max = 120.dp)
                                    ) {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = null,
                                            tint = category.color,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = getLocalizedCategoryName(category.name),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = "${todayMins.toInt()}${stringResource(R.string.mins_suffix)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = category.color,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    Text(
                                        text = if (targetMins > 0f) "${monthMins.toInt()}/${targetMins.toInt()}${stringResource(R.string.mins_suffix)}" else "${monthMins.toInt()}${stringResource(R.string.mins_suffix)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                if (targetMins > 0f) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(if (progressFactor > 0f) progressFactor else 0.005f)
                                                    .clip(CircleShape)
                                                    .background(category.color)
                                            )
                                        }

                                        Text(
                                            text = progressPctText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = category.color,
                                            modifier = Modifier.widthIn(min = 40.dp),
                                            textAlign = TextAlign.End,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            }
                        } else {
                            val mins = categoryTotals[category.name] ?: 0f
                            val percentage = if (totalMinutes > 0f) mins / totalMinutes else 0f
                            val progressPercent = if (targetMins > 0f) {
                                kotlin.math.floor((mins.toDouble() / targetMins.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            val progressFactor = progressPercent / 100f
                            val progressPctText = "$progressPercent%"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.width(85.dp)
                                ) {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = null,
                                        tint = category.color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = getLocalizedCategoryName(category.name),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = if (targetMins > 0f) "${mins.toInt()}/${targetMins.toInt()}${stringResource(R.string.mins_suffix)}" else "${mins.toInt()}${stringResource(R.string.mins_suffix)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(95.dp),
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                if (targetMins > 0f) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(if (progressFactor > 0f) progressFactor else 0.005f)
                                                    .clip(CircleShape)
                                                    .background(category.color)
                                            )
                                        }

                                        Text(
                                            text = progressPctText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = category.color,
                                            modifier = Modifier.widthIn(min = 28.dp),
                                            textAlign = TextAlign.End,
                                            maxLines = 1
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedDayForDetail?.let { dayNum ->
        DayDetailDialog(
            dayNum = dayNum,
            year = currentYear,
            month = currentMonthIdx,
            dayLogs = rawLogs.filter { log ->
                val c = Calendar.getInstance().apply { timeInMillis = log.startTime }
                c.get(Calendar.YEAR) == currentYear &&
                c.get(Calendar.MONTH) == currentMonthIdx &&
                c.get(Calendar.DAY_OF_MONTH) == dayNum
            },
            onDismiss = { selectedDayForDetail = null },
            onDelete = { logId -> viewModel.deleteLog(logId) },
            onUpdate = { updatedLog -> viewModel.updateLog(updatedLog) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailDialog(
    dayNum: Int,
    year: Int,
    month: Int,
    dayLogs: List<TimeLog>,
    onDismiss: () -> Unit,
    onDelete: (Int) -> Unit,
    onUpdate: (TimeLog) -> Unit
) {
    var editingLog by remember { mutableStateOf<TimeLog?>(null) }
    var deletingLogId by remember { mutableStateOf<Int?>(null) }
    var isEditMode by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val dayTitleText = remember(year, month, dayNum) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayNum)
                    }
                    val locale = Locale.getDefault()
                    val pattern = if (locale.language == "zh") "MM月dd日 EEEE" else "EEEE, MMM d"
                    val sdf = SimpleDateFormat(pattern, locale)
                    sdf.format(cal.time)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dayTitleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { isEditMode = !isEditMode }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (isEditMode) stringResource(R.string.content_save) else stringResource(R.string.content_edit_toggle),
                                tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_close))
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (dayLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.history_empty_day),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(dayLogs, key = { it.id }) { log ->
                            val catColor = getCategoryColor(log.category)
                            val catIcon = getCategoryIcon(log.category)
                            val durationText = "${log.durationMinutes}${stringResource(R.string.mins_suffix)}"
                            val logStartTimeStr = remember(log.startTime) {
                                SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(log.startTime))
                            }

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(catColor.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = catIcon,
                                                contentDescription = null,
                                                tint = catColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = getLocalizedDescription(log.description, log.timerType),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${getLocalizedCategoryName(log.category)} • $logStartTimeStr • $durationText",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = catColor,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    if (isEditMode) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            IconButton(
                                                onClick = { editingLog = log },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = stringResource(R.string.content_edit_log),
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { deletingLogId = log.id },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.content_delete_log),
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_back))
                }
            }
        }
    }

    editingLog?.let { logToEdit ->
        EditTimeLogDialog(
            log = logToEdit,
            onDismiss = { editingLog = null },
            onSave = { updated ->
                onUpdate(updated)
                editingLog = null
            }
        )
    }

    deletingLogId?.let { logIdToDelete ->
        DeleteConfirmationDialog(
            onDismiss = { deletingLogId = null },
            onConfirm = {
                onDelete(logIdToDelete)
                deletingLogId = null
            }
        )
    }
}

@Composable
fun DeleteConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_delete_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_delete_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.dialog_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTimeLogDialog(
    log: TimeLog,
    onDismiss: () -> Unit,
    onSave: (TimeLog) -> Unit
) {
    val localFocusManager = LocalFocusManager.current
    var desc by remember { mutableStateOf(log.description) }
    var selectedCategory by remember { mutableStateOf(log.category) }
    var durationMinutesTextState by remember {
        mutableStateOf(
            TextFieldValue(
                text = log.durationMinutes.toString(),
                selection = TextRange.Zero
            )
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .pointerInput(Unit) {
                    detectTapGestures {
                        localFocusManager.clearFocus()
                    }
                },
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                val dateFormat = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, Locale.getDefault()) }
                val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                val dateStr = remember(log.startTime) { dateFormat.format(Date(log.startTime)) }
                val startTimeStr = remember(log.startTime) { timeFormat.format(Date(log.startTime)) }
                val endTimeStr = remember(log.startTime, log.durationMinutes) {
                    val targetEnd = if (log.endTime > 0) log.endTime else (log.startTime + log.durationMinutes * 60 * 1000)
                    timeFormat.format(Date(targetEnd))
                }

                val titleText = remember(dateStr) {
                    if (Locale.getDefault().language == "zh") {
                        "编辑 $dateStr"
                    } else {
                        "Edit $dateStr"
                    }
                }

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text(stringResource(R.string.edit_log_desc_label)) },
                    placeholder = {
                        val placeholderRes = when (log.timerType) {
                            1 -> R.string.desc_auto_timer_start
                            2 -> R.string.desc_manual_record
                            else -> R.string.desc_quick_timer
                        }
                        Text(stringResource(placeholderRes))
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.edit_log_start_time_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = startTimeStr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.edit_log_end_time_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = endTimeStr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.edit_log_change_category),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val rowLength = 3
                    val chunked = Categories.chunked(rowLength)
                    chunked.forEach { rowCategories ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowCategories.forEach { category ->
                                val isSelected = selectedCategory == category.name
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) category.color else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .clickable { selectedCategory = category.name }
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else category.color,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = getLocalizedCategoryName(category.name),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.edit_log_duration_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = durationMinutesTextState,
                    onValueChange = {
                        if (it.text.all { char -> char.isDigit() }) {
                            durationMinutesTextState = it
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                durationMinutesTextState = durationMinutesTextState.copy(
                                    selection = TextRange(0, durationMinutesTextState.text.length)
                                )
                            }
                        },
                    singleLine = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }

                    Button(
                        onClick = {
                            val durationVal = durationMinutesTextState.text.toLongOrNull() ?: log.durationMinutes
                            if (durationVal > 0) {
                                val nowTime = System.currentTimeMillis()
                                val newEnd = log.startTime + durationVal * 60 * 1000
                                val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                val newBelong = format.format(java.util.Date(log.startTime))
                                onSave(
                                    log.copy(
                                        category = selectedCategory,
                                        description = desc,
                                        durationMinutes = durationVal,
                                        endTime = newEnd,
                                        updatedTime = nowTime,
                                        belongDate = newBelong
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text(stringResource(R.string.content_save))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyTargetSettingsDialog(
    viewModel: TimeTrackViewModel,
    monthKey: String?,
    onDismiss: () -> Unit
) {
    val targetUpdatedTrigger by viewModel.targetUpdatedTrigger.collectAsStateWithLifecycle()
    val monthlyTargets = remember(monthKey, targetUpdatedTrigger) {
        viewModel.getMonthlyTargetsForMonth(monthKey)
    }

    val context = LocalContext.current
    val dialogTitle = remember(monthKey, context) {
        if (monthKey == null) {
            context.getString(R.string.monthly_target_setting)
        } else {
            try {
                val date = SimpleDateFormat("yyyy-MM", java.util.Locale.US).parse(monthKey)
                if (date != null) {
                    val locale = Locale.getDefault()
                    val monthPattern = if (locale.language == "zh") "yyyy年M月" else "MMM yyyy"
                    val monthStr = SimpleDateFormat(monthPattern, locale).format(date)
                    val space = if (locale.language == "zh") "" else " "
                    "$monthStr$space${context.getString(R.string.monthly_target_setting)}"
                } else {
                    monthKey
                }
            } catch (e: Exception) {
                monthKey
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("monthly_targets_dialog_parent_card"),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = dialogTitle ?: stringResource(R.string.monthly_target_setting),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Categories.forEach { category ->
                    val totalInHours = monthlyTargets[category.name] ?: 0f
                    
                    val keyPrefix = if (monthKey == null) "" else "${monthKey}_"
                    val storedInput = remember(totalInHours, monthKey) {
                        viewModel.prefs.getString("target_input_${keyPrefix}${category.name}", null)
                            ?: viewModel.prefs.getString("target_input_${category.name}", null)
                    }
                    val storedUnit = remember(totalInHours, monthKey) {
                        viewModel.prefs.getString("target_unit_${keyPrefix}${category.name}", null)
                            ?: viewModel.prefs.getString("target_unit_${category.name}", null)
                    }

                    var rawText by remember(totalInHours) {
                        mutableStateOf(
                            storedInput ?: if (totalInHours == 0f) {
                                "0"
                            } else {
                                val minutesVal = (totalInHours * 60f + 0.5f).toInt()
                                if (minutesVal % 60 == 0) {
                                    (minutesVal / 60).toString()
                                } else {
                                    minutesVal.toString()
                                }
                            }
                        )
                    }

                    var unit by remember(totalInHours) {
                        mutableStateOf(
                            storedUnit ?: if (totalInHours == 0f) {
                                "小时"
                            } else {
                                val minutesVal = (totalInHours * 60f + 0.5f).toInt()
                                if (minutesVal % 60 == 0) "小时" else "分钟"
                            }
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Category Badge (fixed width)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.width(90.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(category.color.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = null,
                                        tint = category.color,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = getLocalizedCategoryName(category.name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Manual numeric text field input (0 as default)
                            OutlinedTextField(
                                value = rawText,
                                onValueChange = { newValue ->
                                    val filtered = newValue.filter { it.isDigit() }
                                    rawText = filtered
                                    val parsedValue = filtered.toFloatOrNull() ?: 0f
                                    val calculatedHours = if (unit == "分钟") parsedValue / 60f else parsedValue
                                    viewModel.updateMonthlyTarget(monthKey, category.name, calculatedHours, filtered, unit)
                                },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("target_input_${category.id}"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = category.color,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )

                            // Unit selector
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(2.dp)
                            ) {
                                listOf("小时", "分钟").forEach { u ->
                                    val active = unit == u
                                    val dispUnit = if (u == "小时") stringResource(R.string.unit_hours) else stringResource(R.string.unit_minutes)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (active) category.color else Color.Transparent)
                                            .clickable {
                                                unit = u
                                                val parsedValue = rawText.toFloatOrNull() ?: 0f
                                                val calculatedHours = if (u == "分钟") parsedValue / 60f else parsedValue
                                                viewModel.updateMonthlyTarget(monthKey, category.name, calculatedHours, rawText, u)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = dispUnit,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("dismiss_targets_button")
                ) {
                    Text(stringResource(R.string.target_settings_done))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: TimeTrackViewModel) {
    val defaultCategory by viewModel.defaultCategory.collectAsStateWithLifecycle()
    val autoTimerEnabled by viewModel.autoTimerEnabled.collectAsStateWithLifecycle()
    val autoTimerTime by viewModel.autoTimerTime.collectAsStateWithLifecycle()
    val autoTimerWeekdays by viewModel.autoTimerWeekdays.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    var timeInputText by remember { mutableStateOf(autoTimerTime) }
    var isTimeError by remember { mutableStateOf(false) }

    // Synchronize UI string state when viewModel triggers state updates
    LaunchedEffect(autoTimerTime) {
        timeInputText = autoTimerTime
        isTimeError = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {


        // 1. THEME SELECTION CARD
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_theme_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = stringResource(R.string.settings_theme_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf(
                        Triple("auto", stringResource(R.string.theme_auto), Icons.Default.BrightnessAuto),
                        Triple("light", stringResource(R.string.theme_light), Icons.Default.LightMode),
                        Triple("dark", stringResource(R.string.theme_dark), Icons.Default.DarkMode)
                    )

                    modes.forEach { (modeKey, modeName, modeIcon) ->
                        val isSelected = themeMode == modeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable { viewModel.updateThemeMode(modeKey) }
                                .testTag("settings_theme_chip_$modeKey")
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = modeName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. DEFAULT CATEGORY CARD
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_default_category),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = stringResource(R.string.settings_default_category_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val chunks = Categories.chunked(3)
                    chunks.forEach { rowCategories ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowCategories.forEach { category ->
                                val isSelected = defaultCategory == category.name
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) category.color else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .clickable { viewModel.updateDefaultCategory(category.name) }
                                        .testTag("settings_default_chip_${category.id}")
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else category.color,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = getLocalizedCategoryName(category.name),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. AUTO-TIMER TOGGLE CARD & TIME INPUT
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text(
                            text = stringResource(R.string.settings_auto_timer),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.settings_auto_timer_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = autoTimerEnabled,
                        onCheckedChange = { viewModel.updateAutoTimerEnabled(it) },
                        modifier = Modifier.testTag("auto_timer_switch")
                    )
                }

                AnimatedVisibility(
                    visible = autoTimerEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Text(
                            text = stringResource(R.string.settings_auto_timer_time),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = stringResource(R.string.settings_auto_timer_weekdays),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val weekdayLabels = listOf(
                                Pair(java.util.Calendar.MONDAY, stringResource(R.string.week_monday)),
                                Pair(java.util.Calendar.TUESDAY, stringResource(R.string.week_tuesday)),
                                Pair(java.util.Calendar.WEDNESDAY, stringResource(R.string.week_wednesday)),
                                Pair(java.util.Calendar.THURSDAY, stringResource(R.string.week_thursday)),
                                Pair(java.util.Calendar.FRIDAY, stringResource(R.string.week_friday)),
                                Pair(java.util.Calendar.SATURDAY, stringResource(R.string.week_saturday)),
                                Pair(java.util.Calendar.SUNDAY, stringResource(R.string.week_sunday))
                            )

                            weekdayLabels.forEach { (dayInt, label) ->
                                val isDaySelected = autoTimerWeekdays.contains(dayInt)
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isDaySelected) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .clickable {
                                            val newSet = if (isDaySelected) {
                                                autoTimerWeekdays - dayInt
                                            } else {
                                                autoTimerWeekdays + dayInt
                                            }
                                            viewModel.updateAutoTimerWeekdays(newSet)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDaySelected) MaterialTheme.colorScheme.onPrimary 
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = timeInputText,
                                onValueChange = { input ->
                                    timeInputText = input
                                    viewModel.resetAbandonedState()
                                    // Validate HH:mm format
                                    val regex = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$".toRegex()
                                    if (regex.matches(input)) {
                                        isTimeError = false
                                        viewModel.updateAutoTimerTime(input)
                                    } else {
                                        isTimeError = true
                                    }
                                },
                                label = { Text(stringResource(R.string.settings_time_label)) },
                                placeholder = { Text("09:00") },
                                isError = isTimeError,
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .width(180.dp)
                                    .testTag("auto_timer_time_input")
                            )

                            // Quick preset presets (单行显示并且水平滚动)
                            val presets = listOf("08:00", "09:00", "18:00", "19:00")
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_preset_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    presets.forEach { time ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .clickable { viewModel.updateAutoTimerTime(time) }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = time,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isTimeError) {
                            Text(
                                text = stringResource(R.string.settings_time_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomRingPieChart(
    categoryTotals: Map<String, Float>,
    totalMinutes: Float,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val placeholderColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)

    Canvas(modifier = modifier) {
        val strokeWidth = 18.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)

        if (totalMinutes == 0f) {
            drawCircle(
                color = placeholderColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )
            return@Canvas
        }

        var startAngle = -90f
        val activeEntries = categoryTotals.filter { it.value > 0f }

        if (activeEntries.size == 1) {
            val entry = activeEntries.entries.first()
            drawCircle(
                color = getCategoryColor(entry.key),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )
            return@Canvas
        }

        activeEntries.forEach { (catName, value) ->
            val sweepAngle = (value / totalMinutes) * 360f
            val color = getCategoryColor(catName)

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )
            startAngle += sweepAngle
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRecordDialog(
    onDismiss: () -> Unit,
    onSave: (category: String, description: String, durationMinutes: Long, startTimeMillis: Long) -> Unit
) {
    val localFocusManager = LocalFocusManager.current
    val context = LocalContext.current
    var desc by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("工作") }
    var durationMinutesTextState by remember {
        mutableStateOf(
            TextFieldValue(
                text = "30",
                selection = TextRange.Zero
            )
        )
    }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("manual_entry_parent_card")
                .pointerInput(Unit) {
                    detectTapGestures {
                        localFocusManager.clearFocus()
                    }
                },
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.manual_add_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Task details
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text(stringResource(R.string.manual_label_desc)) },
                    placeholder = { Text(stringResource(R.string.desc_manual_record)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_desc_input")
                )

                // Category select label
                Text(
                    text = stringResource(R.string.manual_label_category),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Small grid list
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val rowLength = 3
                    val chunked = Categories.chunked(rowLength)
                    chunked.forEach { rowCategories ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowCategories.forEach { category ->
                                val isSelected = selectedCategory == category.name
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) category.color else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .clickable { selectedCategory = category.name }
                                        .testTag("dialog_category_chip_${category.id}")
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else category.color,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = getLocalizedCategoryName(category.name),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Date Select Badge
                Text(
                    text = stringResource(R.string.manual_label_date),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                val manualDateFormat = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, Locale.getDefault()) }
                val selectedDateText = remember(selectedDateMillis) { manualDateFormat.format(Date(selectedDateMillis)) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .clickable {
                            val currentCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val newCal = Calendar.getInstance().apply {
                                        timeInMillis = selectedDateMillis
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    selectedDateMillis = newCal.timeInMillis
                                },
                                currentCal.get(Calendar.YEAR),
                                currentCal.get(Calendar.MONTH),
                                currentCal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Today,
                            contentDescription = stringResource(R.string.content_select_month),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = selectedDateText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Duration Text / Quick choices
                Text(
                    text = stringResource(R.string.manual_label_duration),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = durationMinutesTextState,
                        onValueChange = {
                            if (it.text.all { char -> char.isDigit() }) {
                                durationMinutesTextState = it
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(80.dp)
                            .testTag("manual_duration_input")
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    durationMinutesTextState = durationMinutesTextState.copy(
                                        selection = TextRange(0, durationMinutesTextState.text.length)
                                    )
                                }
                            },
                        singleLine = true
                    )

                    // Quick buttons
                    val options = listOf(15, 30, 45, 60, 120)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        options.forEach { mins ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        durationMinutesTextState = TextFieldValue(
                                            text = mins.toString(),
                                            selection = TextRange(0, mins.toString().length)
                                        )
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "+${mins}m",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // SUBMIT OR CLOSE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_cancel_button")
                    ) {
                        Text(stringResource(R.string.btn_close))
                    }

                    Button(
                        onClick = {
                            val durationVal = durationMinutesTextState.text.toLongOrNull() ?: 30L
                            onSave(selectedCategory, desc, durationVal, selectedDateMillis)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("dialog_save_button")
                    ) {
                        Text(stringResource(R.string.btn_save))
                    }
                }
            }
        }
    }
}

@Composable
fun MonthPickerDialog(
    initialYear: Int,
    initialMonth: Int, // 0-based
    onDismiss: () -> Unit,
    onConfirm: (year: Int, month: Int) -> Unit
) {
    var selectedYear by remember { mutableStateOf(initialYear) }
    var selectedMonth by remember { mutableStateOf(initialMonth) }
    
    val years = remember {
        val currentY = Calendar.getInstance().get(Calendar.YEAR)
        ((currentY - 10)..(currentY + 5)).toList()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.month_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Year list (scrollable Column)
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(years) { y ->
                        val isSelected = y == selectedYear
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { selectedYear = y }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (Locale.getDefault().language == "zh") "${y}年" else "$y",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                
                // Month list
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(12) { m ->
                        val isSelected = m == selectedMonth
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { selectedMonth = m }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val mText = if (Locale.getDefault().language == "zh") {
                                "${m + 1}月"
                            } else {
                                val dfs = java.text.DateFormatSymbols(Locale.getDefault())
                                dfs.months[m]
                            }
                            Text(
                                text = mText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedYear, selectedMonth) }
            ) {
                Text(stringResource(R.string.month_picker_confirm), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.month_picker_cancel))
            }
        }
    )
}

