package com.example.kairos

import android.app.NotificationManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.model.home.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AvatarNotificationsTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext

    @Test fun avatarPersistsPerAccountAndRejectsChangedSession() {
        assertTrue(context.packageName.endsWith(".integration"))
        val first="avatar-A-${UUID.randomUUID()}";val second="avatar-B-${UUID.randomUUID()}"
        val store=AvatarStore(context)
        val input=File(context.cacheDir,"avatar-source-test.jpg")
        val bitmap=Bitmap.createBitmap(900,600,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) };bitmap.recycle()
        try {
            store.save(first,Uri.fromFile(input)) { true }
            AvatarStore(context).load(first)!!.let { assertEquals(512,it.width);assertEquals(512,it.height);it.recycle() }
            assertNull(store.load(second))
            assertThrows(IllegalStateException::class.java) { store.save(second,Uri.fromFile(input)) { false } }
            assertNull(store.load(second))
            store.remove(first);assertNull(AvatarStore(context).load(first))
        } finally { store.remove(first);store.remove(second);input.delete() }
    }

    @Test fun realAndroidNotificationAndNewAlertDetection() {
        assertTrue(context.packageName.endsWith(".integration"))
        if(Build.VERSION.SDK_INT>=33) instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").use {
            java.io.FileInputStream(it.fileDescriptor).readBytes()
        }
        val owner="notice-${UUID.randomUUID()}"
        val manager=context.getSystemService(NotificationManager::class.java)
        fun awaitNotification(id: Int,present: Boolean) {
            val deadline=android.os.SystemClock.uptimeMillis()+3000
            while(manager.activeNotifications.any { it.id==id }!=present && android.os.SystemClock.uptimeMillis()<deadline) android.os.SystemClock.sleep(25)
            assertEquals(present,manager.activeNotifications.any { it.id==id })
        }
        val notifications=LocalAlertNotifications(context)
        val preferences=StockerPreferences(context,owner)
        preferences.setEnabled("general",true);preferences.setEnabled("restocking",true)
        fun data(ids: List<String>)=StockerHomeData(0,0,emptyList(),emptyList(),emptyList(),false,
            restockingAlerts=ids.map { RestockingRecord(it,"Test product","","1",java.time.Instant.now(),RestockingStatus.REQUIRED,productId=1) })
        try {
            assertTrue(notifications.allowed());assertTrue(notifications.test(owner))
            awaitNotification(9101,true)
            assertTrue(manager.activeNotifications.any { it.id==9101 && it.notification.extras.getString("android.title").orEmpty().contains("teste") })
            notifications.observe(owner,data(listOf("one")))
            assertFalse(manager.activeNotifications.any { it.id==9102 })
            notifications.observe(owner,data(listOf("one","two")))
            awaitNotification(9102,true)
            manager.cancel(9102)
            awaitNotification(9102,false)
            notifications.observe(owner,data(listOf("one","two")))
            assertFalse(manager.activeNotifications.any { it.id==9102 })
        } finally {
            manager.cancel(9101);manager.cancel(9102)
            context.getSharedPreferences("notification-alerts",0).edit().remove("$owner:seen").commit()
        }
    }
}
