package com.example.kairos.model.home

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.kairos.R
import com.example.kairos.view.MainActivity

/** Device notifications when new server alerts are observed. No remote push or background polling. */
class LocalAlertNotifications(private val context: Context) {
    private val manager=context.getSystemService(NotificationManager::class.java)
    private val seen=context.getSharedPreferences("notification-alerts",Context.MODE_PRIVATE)
    fun allowed(): Boolean = manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT<33 ||
        ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
    private fun channel(uid: String): String {
        val prefs=StockerPreferences(context,uid)
        val sound=prefs.enabled("sound");val vibration=prefs.enabled("vibration")
        val id="kairos_restocking_${if(sound) "sound" else "quiet"}_${if(vibration) "vibrate" else "still"}"
        manager.createNotificationChannel(NotificationChannel(id,"Reposições · ${if(sound) "com som" else "sem som"} · ${if(vibration) "com vibração" else "sem vibração"}",NotificationManager.IMPORTANCE_DEFAULT).apply {
            description="Avisos de novas pendências consultadas no mercado e testes solicitados no aplicativo."
            if(!sound) setSound(null,null)
            enableVibration(vibration)
        })
        return id
    }
    private fun post(uid: String,title: String,message: String,id: Int): Boolean {
        if(!allowed()) return false
        val channel=channel(uid)
        if(manager.getNotificationChannel(channel)?.importance==NotificationManager.IMPORTANCE_NONE) return false
        val intent=Intent(context,MainActivity::class.java).setAction("com.example.kairos.OPEN_ALERTS")
            .putExtra("notificationOwner",uid).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending=PendingIntent.getActivity(context,uid.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id,Notification.Builder(context,channel).setSmallIcon(R.drawable.ic_kairos_notification)
            .setContentTitle(title).setContentText(message).setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(pending).setAutoCancel(true).build())
        return true
    }
    fun test(uid: String): Boolean {
        if(!allowed()) return false
        return post(uid,"Kairos · Notificação de teste","Notificações ativas neste aparelho. Esta mensagem foi enviada pelo aplicativo para conferência.",9101)
    }
    fun observe(uid: String,data: StockerHomeData) {
        if(data.isDemo) return
        val current=data.restockingAlerts.map { it.id }.toSet()
        val key="$uid:seen"
        val previous=seen.getStringSet(key,null)
        seen.edit().putStringSet(key,current).apply()
        val prefs=StockerPreferences(context,uid)
        if(previous==null || !prefs.enabled("general",false) || !prefs.enabled("restocking") || !allowed()) return
        val fresh=current-previous
        if(fresh.isNotEmpty()) post(uid,"Novas pendências no mercado",
            "${fresh.size} novo(s) alerta(s) de reposição. Abra o Kairos para conferir os produtos.",9102)
    }
}
