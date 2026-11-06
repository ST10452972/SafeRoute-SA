package com.example.opsc6312_poe

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class PendingRecord(val id: String, val uid: String, val collection: String, val payload: String)

/** App-private SQLite outbox. Stable IDs make retries idempotent in Firestore. */
class OfflineQueue(context: Context) : SQLiteOpenHelper(context, "saferoute_outbox.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE outbox (id TEXT PRIMARY KEY, uid TEXT NOT NULL, collection TEXT NOT NULL, payload TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun enqueue(uid: String, collection: String, payload: Map<String, String>): String {
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id); put("uid", uid); put("collection", collection)
            put("payload", JSONObject(payload).toString())
        }
        writableDatabase.insertOrThrow("outbox", null, values)
        return id
    }

    @Synchronized fun pending(uid: String): List<PendingRecord> {
        val result = mutableListOf<PendingRecord>()
        readableDatabase.query("outbox", arrayOf("id", "uid", "collection", "payload"), "uid = ?", arrayOf(uid), null, null, "rowid ASC").use { c ->
            while (c.moveToNext()) result += PendingRecord(c.getString(0), c.getString(1), c.getString(2), c.getString(3))
        }
        return result
    }

    @Synchronized fun remove(id: String) { writableDatabase.delete("outbox", "id = ?", arrayOf(id)) }
}

fun scheduleOutboxSync(context: Context) {
    val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork("saferoute-outbox-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
}

fun startSafetyCheck(context: Context) {
    context.getSharedPreferences("saferoute_preferences", Context.MODE_PRIVATE).edit().putBoolean("safety_check_active", true).apply()
    val request = OneTimeWorkRequestBuilder<SafetyCheckAlertWorker>().setInitialDelay(30, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniqueWork("saferoute-safety-check", ExistingWorkPolicy.REPLACE, request)
}

fun cancelSafetyCheck(context: Context) {
    context.getSharedPreferences("saferoute_preferences", Context.MODE_PRIVATE).edit().putBoolean("safety_check_active", false).apply()
    WorkManager.getInstance(context).cancelUniqueWork("saferoute-safety-check")
}

class SafetyCheckAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val active = applicationContext.getSharedPreferences("saferoute_preferences", Context.MODE_PRIVATE).getBoolean("safety_check_active", false)
        if (!active) return Result.success()
        createSafetyNotificationChannel(applicationContext)
        val notice = androidx.core.app.NotificationCompat.Builder(applicationContext, SAFETY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.check_missed_title))
            .setContentText(applicationContext.getString(R.string.check_missed_body))
            .setAutoCancel(true).setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH).build()
        try { androidx.core.app.NotificationManagerCompat.from(applicationContext).notify(202, notice) }
        catch (e: SecurityException) { Log.w("SafeRoute", "Safety check notification permission was not granted", e) }
        applicationContext.getSharedPreferences("saferoute_preferences", Context.MODE_PRIVATE).edit().putBoolean("safety_check_active", false).apply()
        return Result.success()
    }
}

class OutboxSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (FirebaseApp.getApps(applicationContext).isEmpty()) return Result.retry()
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return Result.success()
        val queue = OfflineQueue(applicationContext)
        var hasFailures = false
        for (record in queue.pending(user.uid)) {
            if (record.uid != user.uid) continue
            try {
                val body = JSONObject(record.payload).toMap().toMutableMap()
                // Drop photo fields from records queued by an older build. This version
                // stores text-only incident reports and does not require Firebase Storage.
                body.remove("localImageUri")
                body.remove("imageContentType")
                body.remove("imageStoragePath")
                val complete = body + mapOf(
                    "userId" to record.uid,
                    "clientEventId" to record.id,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                FirebaseFirestore.getInstance().collection(record.collection).document(record.id).set(complete).await()
                queue.remove(record.id)
            } catch (e: Exception) {
                hasFailures = true
                Log.w("SafeRoute", "Could not sync record ${record.id}; it remains queued", e)
            }
        }
        return if (hasFailures) Result.retry() else Result.success()
    }
}

private fun JSONObject.toMap(): Map<String, Any> = keys().asSequence().associateWith { key -> get(key) }
