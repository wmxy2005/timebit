package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DatabaseProvider
import com.example.data.TimeLog
import com.example.data.TimeTrackRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class TimeTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TimeTrackRepository
    val prefs = application.getSharedPreferences("time_track_prefs", Context.MODE_PRIVATE)

    // Time Log flows
    private val _allLogs = MutableStateFlow<List<TimeLog>>(emptyList())
    val allLogs: StateFlow<List<TimeLog>> = _allLogs

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded
    
    private val _currentHistoryMonth = MutableStateFlow<Calendar>(Calendar.getInstance())
    val currentHistoryMonth: StateFlow<Calendar> = _currentHistoryMonth

    private val _currentStatsMonth = MutableStateFlow<Calendar>(Calendar.getInstance())
    val currentStatsMonth: StateFlow<Calendar> = _currentStatsMonth

    private val _currentDateTrigger = MutableStateFlow(System.currentTimeMillis())
    val currentDateTrigger: StateFlow<Long> = _currentDateTrigger

    fun refreshCurrentDate() {
        _currentDateTrigger.value = System.currentTimeMillis()
    }

    val historyLogs: StateFlow<List<TimeLog>> = combine(allLogs, _currentHistoryMonth) { logs, viewMonthCal ->
        val yr = viewMonthCal.get(Calendar.YEAR)
        val mo = viewMonthCal.get(Calendar.MONTH)
        logs.filter { log ->
            val logCal = Calendar.getInstance().apply { timeInMillis = log.startTime }
            logCal.get(Calendar.YEAR) == yr && logCal.get(Calendar.MONTH) == mo
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun changeHistoryMonth(offset: Int) {
        val current = _currentHistoryMonth.value.clone() as Calendar
        current.add(Calendar.MONTH, offset)
        _currentHistoryMonth.value = current
    }

    fun resetHistoryMonthToCurrent() {
        _currentHistoryMonth.value = Calendar.getInstance()
    }

    fun changeStatsMonth(offset: Int) {
        val current = _currentStatsMonth.value.clone() as Calendar
        current.add(Calendar.MONTH, offset)
        _currentStatsMonth.value = current
    }

    fun resetStatsMonthToCurrent() {
        _currentStatsMonth.value = Calendar.getInstance()
    }

    private val _resumingLogId = MutableStateFlow<Int?>(null)
    val resumingLogId: StateFlow<Int?> = _resumingLogId
    
    // UI selections
    private val _selectedTab = MutableStateFlow(0) // 0: Timer, 1: History, 2: Statistics, 3: Settings
    val selectedTab: StateFlow<Int> = _selectedTab

    // Active Timer (Stopwatch) states
    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

    private val _timerCategory = MutableStateFlow(prefs.getString("default_category", "工作") ?: "工作")
    val timerCategory: StateFlow<String> = _timerCategory

    private val _timerDescription = MutableStateFlow("")
    val timerDescription: StateFlow<String> = _timerDescription

    private val _timerStartMillis = MutableStateFlow(0L)
    val timerStartMillis: StateFlow<Long> = _timerStartMillis

    private val _timerElapsedSeconds = MutableStateFlow(0L)
    val timerElapsedSeconds: StateFlow<Long> = _timerElapsedSeconds

    // Statistics Filter: "今天" (Today), "按月" (Monthly)
    private val _statsPeriod = MutableStateFlow("今天")
    val statsPeriod: StateFlow<String> = _statsPeriod

    // Settings States
    private val _defaultCategory = MutableStateFlow(prefs.getString("default_category", "工作") ?: "工作")
    val defaultCategory: StateFlow<String> = _defaultCategory

    // Theme Settings State: "auto", "light", "dark"
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "auto") ?: "auto")
    val themeMode: StateFlow<String> = _themeMode

    fun updateThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    // Last user-selected category on the timer screen
    private val _selectedTrackerCategory = MutableStateFlow(prefs.getString("default_category", "工作") ?: "工作")
    val selectedTrackerCategory: StateFlow<String> = _selectedTrackerCategory

    fun updateSelectedCategory(category: String) {
        _selectedTrackerCategory.value = category
    }

    private val _autoTimerEnabled = MutableStateFlow(prefs.getBoolean("auto_timer_enabled", false))
    val autoTimerEnabled: StateFlow<Boolean> = _autoTimerEnabled

    private val _autoTimerTime = MutableStateFlow(prefs.getString("auto_timer_time", "09:00") ?: "09:00")
    val autoTimerTime: StateFlow<String> = _autoTimerTime

    private val _autoTimerWeekdays = MutableStateFlow(loadAutoTimerWeekdays())
    val autoTimerWeekdays: StateFlow<Set<Int>> = _autoTimerWeekdays

    private fun loadAutoTimerWeekdays(): Set<Int> {
        val raw = prefs.getString("auto_timer_weekdays", "1,2,3,4,5,6,7") ?: "1,2,3,4,5,6,7"
        if (raw.isEmpty()) return emptySet()
        return raw.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }

    // Target settings triggers and reactive maps
    val targetUpdatedTrigger = MutableStateFlow(0)

    val timerMonthTargets: StateFlow<Map<String, Float>> = combine(_currentDateTrigger, targetUpdatedTrigger) { dateTrigger, _ ->
        val cal = Calendar.getInstance().apply { timeInMillis = dateTrigger }
        val monthKey = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(cal.time)
        getMonthlyTargetsForMonth(monthKey)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = loadMonthlyTargets()
    )

    val statsMonthTargets: StateFlow<Map<String, Float>> = combine(_currentStatsMonth, targetUpdatedTrigger) { statsMonthCal, _ ->
        val monthKey = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(statsMonthCal.time)
        getMonthlyTargetsForMonth(monthKey)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = loadMonthlyTargets()
    )

    // Monthly category targets in hours
    private val _monthlyCategoryTargets = MutableStateFlow<Map<String, Float>>(loadMonthlyTargets())
    val monthlyCategoryTargets: StateFlow<Map<String, Float>> = _monthlyCategoryTargets

    private var timerJob: Job? = null

    init {
        val database = DatabaseProvider.getDatabase(application)
        repository = TimeTrackRepository(database.timeLogDao())
        
        viewModelScope.launch {
            repository.allLogs.collect { logs ->
                _allLogs.value = logs
                _isLoaded.value = true
            }
        }

        // Restore running timer state
        val savedIsRunning = prefs.getBoolean("timer_is_running", false)
        if (savedIsRunning) {
            val savedStartMillis = prefs.getLong("timer_start_millis", 0L)
            if (savedStartMillis > 0L) {
                _timerCategory.value = prefs.getString("timer_category", "工作") ?: "工作"
                _timerDescription.value = prefs.getString("timer_description", "") ?: ""
                _timerStartMillis.value = savedStartMillis
                val savedResId = prefs.getInt("timer_resuming_id", -1)
                _resumingLogId.value = if (savedResId != -1) savedResId else null
                _isTimerRunning.value = true
                _timerElapsedSeconds.value = (System.currentTimeMillis() - savedStartMillis) / 1000
                startTimerCounting(savedStartMillis)
            }
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
        refreshCurrentDate()
        if (index == 0) {
            checkAndTriggerAutoTimerOnSwitch()
        }
    }

    fun checkAndTriggerAutoTimerOnSwitch() {
        viewModelScope.launch {
            if (_autoTimerEnabled.value && !_isTimerRunning.value) {
                // If state flow is empty, wait briefly for database load on startup
                if (allLogs.value.isEmpty()) {
                    delay(300)
                }
                val now = Calendar.getInstance()
                val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
                if (!_autoTimerWeekdays.value.contains(dayOfWeek)) {
                    return@launch
                }

                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(now.time)
                val isAbandoned = prefs.getString("last_abandoned_date", "") == todayStr
                if (isAbandoned) return@launch

                val timeParts = _autoTimerTime.value.split(":")
                if (timeParts.size == 2) {
                    val targetHour = timeParts[0].toIntOrNull() ?: 9
                    val targetMin = timeParts[1].toIntOrNull() ?: 0
                    
                    val targetCal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, targetHour)
                        set(Calendar.MINUTE, targetMin)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    
                    if (System.currentTimeMillis() >= targetCal.timeInMillis) {
                        val autoRecordExists = allLogs.value.any { log ->
                            isToday(log.startTime) && (log.description == "自动开始计时" || log.description == "自动计时")
                        }
                        if (!autoRecordExists && !_isTimerRunning.value) {
                            startTimerFromPast(_defaultCategory.value, "自动计时", targetCal.timeInMillis, -1)
                        }
                    }
                }
            }
        }
    }

    private fun loadMonthlyTargets(): Map<String, Float> {
        val defaults = mapOf(
            "工作" to 0.0f,
            "学习" to 0.0f,
            "运动" to 0.0f,
            "休息" to 0.0f,
            "娱乐" to 0.0f,
            "日常" to 0.0f
        )
        return defaults.mapValues { (cat, defaultVal) ->
            prefs.getFloat("target_$cat", defaultVal)
        }
    }

    fun getMonthlyTargetsForMonth(monthKey: String?): Map<String, Float> {
        val defaults = mapOf(
            "工作" to 0.0f,
            "学习" to 0.0f,
            "运动" to 0.0f,
            "休息" to 0.0f,
            "娱乐" to 0.0f,
            "日常" to 0.0f
        )
        val defaultTargets = loadMonthlyTargets()
        if (monthKey == null) {
            return defaultTargets
        }
        return defaults.mapValues { (cat, _) ->
            val hasMonthSpecific = prefs.contains("target_${monthKey}_$cat")
            if (hasMonthSpecific) {
                val monthVal = prefs.getFloat("target_${monthKey}_$cat", 0.0f)
                if (monthVal == 0.0f) {
                    defaultTargets[cat] ?: 0.0f
                } else {
                    monthVal
                }
            } else {
                defaultTargets[cat] ?: 0.0f
            }
        }
    }



    fun setStatsPeriod(period: String) {
        _statsPeriod.value = period
    }

    // Settings Modification Actions
    fun updateDefaultCategory(category: String) {
        _defaultCategory.value = category
        _selectedTrackerCategory.value = category
        if (!_isTimerRunning.value) {
            _timerCategory.value = category
        }
        prefs.edit()
            .putString("default_category", category)
            .putString("last_abandoned_date", "")
            .apply()
    }

    fun updateAutoTimerEnabled(enabled: Boolean) {
        _autoTimerEnabled.value = enabled
        prefs.edit()
            .putBoolean("auto_timer_enabled", enabled)
            .putString("last_abandoned_date", "")
            .apply()
        if (!enabled) {
            // clear today auto started state so that toggling it back on can allow re-trigger
            prefs.edit().putString("last_auto_started_date", "").apply()
        }
    }

    fun updateAutoTimerTime(time: String) {
        _autoTimerTime.value = time
        prefs.edit()
            .putString("auto_timer_time", time)
            .putString("last_abandoned_date", "")
            .apply()
        // clear auto started state on time change to allow fresh check
        prefs.edit().putString("last_auto_started_date", "").apply()
    }

    fun updateAutoTimerWeekdays(weekdays: Set<Int>) {
        _autoTimerWeekdays.value = weekdays
        val str = weekdays.joinToString(",")
        prefs.edit()
            .putString("auto_timer_weekdays", str)
            .putString("last_abandoned_date", "")
            .apply()
    }

    fun updateTimerDescription(desc: String) {
        _timerDescription.value = desc
        if (_isTimerRunning.value) {
            persistRunningTimer()
        }
    }

    fun resetAbandonedState() {
        prefs.edit().putString("last_abandoned_date", "").apply()
    }

    fun updateMonthlyTarget(monthKey: String? = null, category: String, hours: Float, rawInput: String, unit: String) {
        val keyPrefix = if (monthKey == null) "" else "${monthKey}_"
        prefs.edit()
            .putFloat("target_${keyPrefix}$category", hours)
            .putString("target_input_${keyPrefix}$category", rawInput)
            .putString("target_unit_${keyPrefix}$category", unit)
            .apply()

        if (monthKey == null) {
            val current = _monthlyCategoryTargets.value.toMutableMap()
            current[category] = hours
            _monthlyCategoryTargets.value = current
        }

        targetUpdatedTrigger.value++
    }

    private fun persistRunningTimer() {
        prefs.edit()
            .putBoolean("timer_is_running", true)
            .putLong("timer_start_millis", _timerStartMillis.value)
            .putString("timer_category", _timerCategory.value)
            .putString("timer_description", _timerDescription.value)
            .putInt("timer_resuming_id", _resumingLogId.value ?: -1)
            .apply()
    }

    private fun clearPersistedRunningTimer() {
        prefs.edit()
            .putBoolean("timer_is_running", false)
            .putLong("timer_start_millis", 0L)
            .putString("timer_category", "")
            .putString("timer_description", "")
            .putInt("timer_resuming_id", -1)
            .apply()
    }

    // Timer Actions
    fun startTimer(category: String, description: String) {
        if (_isTimerRunning.value) return
        _timerCategory.value = category
        _timerDescription.value = description
        _timerStartMillis.value = System.currentTimeMillis()
        _isTimerRunning.value = true
        _timerElapsedSeconds.value = 0L
        _resumingLogId.value = null

        startTimerCounting(System.currentTimeMillis())
        persistRunningTimer()
    }

    // Start timer using a specified starting time from the past
    fun startTimerFromPast(category: String, description: String, startMillis: Long, id: Int) {
        _timerCategory.value = category
        _timerDescription.value = description
        _timerStartMillis.value = startMillis
        _resumingLogId.value = id
        _isTimerRunning.value = true
        _timerElapsedSeconds.value = (System.currentTimeMillis() - startMillis) / 1000

        startTimerCounting(startMillis)
        persistRunningTimer()
    }

    private fun startTimerCounting(startMillis: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _timerElapsedSeconds.value = (System.currentTimeMillis() - _timerStartMillis.value) / 1000
            }
        }
    }

    fun resetTimer() {
        timerJob?.cancel()
        _isTimerRunning.value = false
        _timerElapsedSeconds.value = 0L
        _timerDescription.value = ""
        _resumingLogId.value = null

        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        prefs.edit().putString("last_abandoned_date", todayStr).apply()
        clearPersistedRunningTimer()
    }

    fun saveActiveTimer() {
        val durationSecs = _timerElapsedSeconds.value
        val durationMins = (durationSecs + 59) / 60 // round up to minutes
        if (durationMins > 0) {
            val resId = _resumingLogId.value
            if (resId != null && resId != -1) {
                // Update/overwrite existing log
                val log = TimeLog(
                    id = resId,
                    category = _timerCategory.value,
                    description = _timerDescription.value.ifEmpty { "快捷计时" },
                    startTime = _timerStartMillis.value,
                    durationMinutes = durationMins
                )
                updateLog(log)
            } else {
                // Insert new log
                val log = TimeLog(
                    category = _timerCategory.value,
                    description = _timerDescription.value.ifEmpty { "快捷计时" },
                    startTime = _timerStartMillis.value,
                    durationMinutes = durationMins
                )
                addLog(log)
            }
        }
        resetTimer()
    }

    fun updateLog(log: TimeLog) {
        viewModelScope.launch {
            repository.updateLog(log)
        }
    }

    // Manual CRUD Actions
    fun addLog(log: TimeLog) {
        viewModelScope.launch {
            repository.insertLog(log)
        }
    }

    fun addManualLog(category: String, description: String, durationMins: Long, dateOffsetDays: Int) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -dateOffsetDays)
            val log = TimeLog(
                category = category,
                description = description.ifEmpty { "手动记录" },
                startTime = calendar.timeInMillis,
                durationMinutes = durationMins
            )
            repository.insertLog(log)
        }
    }

    fun deleteLog(id: Int) {
        viewModelScope.launch {
            repository.deleteLogById(id)
        }
    }

    // Filtered logs for simple stats or daily stats
    val filteredLogsForStats: StateFlow<List<TimeLog>> = combine(allLogs, _statsPeriod, _currentStatsMonth, _currentDateTrigger) { logs, period, viewMonthCal, triggerTime ->
        when (period) {
            "今天" -> {
                val todayRange = getDayBounds(0, triggerTime)
                logs.filter { it.startTime >= todayRange.first && it.startTime <= todayRange.second }
            }
            "按月" -> {
                val yr = viewMonthCal.get(Calendar.YEAR)
                val mo = viewMonthCal.get(Calendar.MONTH)
                logs.filter { log ->
                    val calLog = Calendar.getInstance().apply { timeInMillis = log.startTime }
                    calLog.get(Calendar.YEAR) == yr && calLog.get(Calendar.MONTH) == mo
                }
            }
            else -> logs
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun getCategoryTimeTodayMinutes(category: String, referenceTime: Long = System.currentTimeMillis()): Long {
        val todayLogs = allLogs.value.filter { isToday(it.startTime, referenceTime) }
        return todayLogs.filter { it.category == category }.sumOf { it.durationMinutes }
    }

    fun isToday(timeMillis: Long, referenceTime: Long = System.currentTimeMillis()): Boolean {
        val calLog = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        val calNow = Calendar.getInstance().apply { timeInMillis = referenceTime }
        return calLog.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
               calLog.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
    }

    // Get bounds for day relative to today (0 for today, 1 for yesterday, etc.)
    private fun getDayBounds(offsetDays: Int, referenceTime: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = referenceTime }
        cal.add(Calendar.DAY_OF_YEAR, -offsetDays)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return Pair(start, end)
    }

    // Export daily time logs into a valid JSON string
    fun exportAllLogsToJson(): String {
        val list = _allLogs.value
        val recordsArray = org.json.JSONArray()
        for (log in list) {
            val obj = org.json.JSONObject()
            obj.put("category", log.category)
            obj.put("description", log.description)
            obj.put("startTime", log.startTime)
            obj.put("durationMinutes", log.durationMinutes)
            obj.put("endTime", log.endTime)
            obj.put("updatedTime", log.updatedTime)
            obj.put("belongDate", log.belongDate)
            recordsArray.put(obj)
        }
        
        val checksum = calculateChecksumForLogs(list)
        
        val root = org.json.JSONObject()
        root.put("version", 1)
        root.put("checksum", checksum)
        root.put("records", recordsArray)
        
        return root.toString(2)
    }

    // Hash calculation logic
    private fun calculateChecksumForLogs(logs: List<TimeLog>): String {
        val sb = java.lang.StringBuilder()
        val sortedLogs = logs.sortedBy { it.startTime }
        for (log in sortedLogs) {
            sb.append(log.category)
              .append("|")
              .append(log.description)
              .append("|")
              .append(log.startTime)
              .append("|")
              .append(log.durationMinutes)
              .append("|")
              .append(log.belongDate)
              .append(";")
        }
        sb.append("TimeTrackAppSafeBackupSalt_2026")
        val rawBytes = sb.toString().toByteArray(Charsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(rawBytes)
        return hashBytes.joinToString("") { String.format("%02x", it) }
    }

    // Validate a json string of records and parse it if correct
    fun validateAndParseJson(jsonStr: String): List<TimeLog>? {
        return try {
            val root = org.json.JSONObject(jsonStr)
            val expectedChecksum = root.optString("checksum", "")
            val recordsArray = root.optJSONArray("records") ?: return null
            
            val parsedLogs = mutableListOf<TimeLog>()
            for (i in 0 until recordsArray.length()) {
                val obj = recordsArray.getJSONObject(i)
                val log = TimeLog(
                    id = 0,
                    category = obj.getString("category"),
                    description = obj.optString("description", ""),
                    startTime = obj.getLong("startTime"),
                    durationMinutes = obj.getLong("durationMinutes"),
                    endTime = obj.optLong("endTime", 0L),
                    updatedTime = obj.optLong("updatedTime", System.currentTimeMillis()),
                    belongDate = obj.optString("belongDate", "")
                )
                parsedLogs.add(log)
            }
            
            val actualChecksum = calculateChecksumForLogs(parsedLogs)
            if (expectedChecksum == actualChecksum) {
                parsedLogs
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Import validated records - clear database first, then insert list
    fun clearAndImportLogs(logs: List<TimeLog>, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteAllLogs()
                for (log in logs) {
                    repository.insertLog(log)
                }
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "导入过程发生错误")
            }
        }
    }
}
