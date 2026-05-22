package com.research.detectmind.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.research.detectmind.data.local.dao.EsmDao
import com.research.detectmind.data.local.entity.EsmScheduleEntity
import com.research.detectmind.data.repository.EnrollmentRepository
import com.research.detectmind.esm.EsmAlarmReceiver
import com.research.detectmind.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@HiltWorker
class EsmSchedulerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val esmDao: EsmDao,
    private val dataStore: DataStore<Preferences>
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = dataStore.data.first()
        val studyId = prefs[EnrollmentRepository.KEY_STUDY_ID] ?: return Result.success()

        val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val schedules = esmDao.getEnabledSchedules(studyId)

        for (schedule in schedules) {
            when (schedule.scheduleType) {
                "fixed" -> scheduleFixed(alarmManager, schedule)
                "random" -> scheduleRandom(alarmManager, schedule)
            }
        }

        return Result.success()
    }

    private fun scheduleFixed(alarmManager: AlarmManager, schedule: EsmScheduleEntity) {
        val times = schedule.timesOfDay?.let {
            runCatching { Json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull()
        } ?: return

        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        for ((index, timeStr) in times.withIndex()) {
            val time = runCatching { LocalTime.parse(timeStr) }.getOrNull() ?: continue
            var triggerAt = LocalDateTime.of(today, time)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (triggerAt <= now) {
                triggerAt = LocalDateTime.of(today.plusDays(1), time)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            setAlarm(alarmManager, schedule, triggerAt,
                requestCode = (schedule.id + "_f$index").hashCode())
        }
    }

    private fun scheduleRandom(alarmManager: AlarmManager, schedule: EsmScheduleEntity) {
        val start = schedule.randomWindowStart?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        } ?: return
        val end = schedule.randomWindowEnd?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        } ?: return

        val today = LocalDate.now()
        val now = System.currentTimeMillis()

        // Clamp window start to now so random picks are always in the future
        val windowStartMs = maxOf(
            LocalDateTime.of(today, start).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            now + 60_000L  // at least 1 minute from now
        )
        val windowEndMs = LocalDateTime.of(today, end).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val windowMs = windowEndMs - windowStartMs
        if (windowMs <= 0) return  // window already passed entirely today

        val count = schedule.randomCount ?: 1

        // Generate unique sorted times within the remaining window — no duplicates
        val triggers = mutableSetOf<Long>()
        var attempts = 0
        while (triggers.size < count && attempts < count * 10) {
            triggers.add(windowStartMs + Random.nextLong(windowMs))
            attempts++
        }

        triggers.sorted().forEachIndexed { index, triggerAt ->
            setAlarm(alarmManager, schedule, triggerAt,
                requestCode = (schedule.id + "_r${today}_$index").hashCode())
        }
    }

    private fun setAlarm(
        alarmManager: AlarmManager,
        schedule: EsmScheduleEntity,
        triggerAtMs: Long,
        requestCode: Int
    ) {
        val intent = Intent(applicationContext, EsmAlarmReceiver::class.java).apply {
            putExtra(Constants.EXTRA_SCHEDULE_ID, schedule.id)
            putExtra(Constants.EXTRA_TRIGGERED_AT, triggerAtMs)
            putExtra("title", schedule.notificationTitle)
            putExtra("body", schedule.notificationBody)
            putExtra("expiry_minutes", schedule.expiryMinutes)
        }
        val pi = PendingIntent.getBroadcast(
            applicationContext, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        }
    }

    companion object {
        fun buildDailyRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<EsmSchedulerWorker>(1, TimeUnit.DAYS)
                .addTag(Constants.ESM_SCHEDULER_WORK_TAG)
                .build()

        fun buildOneShot(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<EsmSchedulerWorker>()
                .addTag(Constants.ESM_SCHEDULER_WORK_TAG)
                .build()
    }
}

