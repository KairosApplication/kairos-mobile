package com.example.kairos.model.cliente

import android.content.Context
import com.example.kairos.BuildConfig
import java.security.MessageDigest

/** Device-only product IDs, separated by account and API origin. No authentication data. */
class CustomerFavorites(context:Context,uid:String,origin:String=BuildConfig.SPRING_API_URL) {
    private val preferences=context.applicationContext.getSharedPreferences("customer_favorites",Context.MODE_PRIVATE)
    private val key=MessageDigest.getInstance("SHA-256").digest("$origin\n$uid".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    init { require(uid.isNotBlank()) }
    fun ids():Set<Long> = preferences.getStringSet(key,emptySet()).orEmpty().mapNotNull { it.toLongOrNull()?.takeIf { id -> id>0 } }.toSet()
    fun set(productId:Long,enabled:Boolean) = synchronized(lock) {
        require(productId>0)
        val ids=ids().toMutableSet()
        if(enabled) ids.add(productId) else ids.remove(productId)
        check(preferences.edit().putStringSet(key,ids.map { it.toString() }.toSet()).commit()) {
            "Não foi possível salvar o favorito neste aparelho. Tente novamente."
        }
    }
    companion object {
        private val lock=Any()
        @Volatile private var application:Context?=null
        fun initialize(context:Context) { application=context.applicationContext }
        fun forUser(uid:String)=CustomerFavorites(checkNotNull(application) { "Preferências não inicializadas." },uid)
    }
}
