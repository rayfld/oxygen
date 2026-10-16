package app.oxygen.service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.oxygen.MainActivity
import app.oxygen.OxygenApp
import app.oxygen.R
class GenerationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { id ->
                    OxygenApp.graph.generationManager.stop(id)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val notification = buildNotification()
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        0
                    },
                )
            }
        }
        return START_NOT_STICKY
    }
    private fun buildNotification(): Notification {
        ensureChannel(this)
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_generating))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.notification_stop), stopIntent())
            .build()
    }
    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, GenerationService::class.java).apply {
            action = ACTION_STOP
            activeConversationId?.let { putExtra(EXTRA_CONVERSATION_ID, it) }
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    companion object {
        private const val CHANNEL_ID = "generations"
        private const val NOTIFICATION_ID = 41
        private const val READY_NOTIFICATION_ID = 42
        private const val ACTION_STOP = "app.oxygen.action.STOP_GENERATION"
        private const val EXTRA_CONVERSATION_ID = "conversation_id"
        var activeConversationId: String? = null
            private set
        fun start(context: Context, conversationId: String? = null) {
            if (conversationId != null) activeConversationId = conversationId
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, GenerationService::class.java))
            }
        }
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, GenerationService::class.java))
            }
        }
        fun postReadyNotification(context: Context, conversationId: String, title: String) {
            ensureChannel(context)
            val intent = PendingIntent.getActivity(
                context,
                conversationId.hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title.ifBlank { context.getString(R.string.app_name) })
                .setContentText(context.getString(R.string.notification_response_ready))
                .setAutoCancel(true)
                .setContentIntent(intent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            val manager = context.getSystemService(NotificationManager::class.java)
            runCatching { manager?.notify(READY_NOTIFICATION_ID, notification) }
        }
        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notification_channel_generations),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(R.string.notification_channel_generations_desc)
                    },
                )
            }
        }
    }
}
