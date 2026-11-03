package com.example.kairos.model.home

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

/** Private, account-scoped device avatar. No gallery URI, EXIF or cloud upload is retained. */
class AvatarStore(private val context: Context) {
    private fun file(uid: String): File {
        val key=MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir,"avatars").apply { mkdirs() },"$key.jpg")
    }
    fun load(uid: String): Bitmap? = runCatching { BitmapFactory.decodeFile(file(uid).path) }.getOrNull()
    fun remove(uid: String) { val target=file(uid); check(!target.exists() || target.delete()) }
    fun save(uid: String,uri: Uri,authorized: () -> Boolean) {
        val bytes=context.contentResolver.openInputStream(uri)?.use { input ->
            val output=java.io.ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            while(true) {
                val count=input.read(buffer); if(count<0) break
                require(output.size()+count<=20*1024*1024) { "Escolha uma imagem de até 20 MB." }
                output.write(buffer,0,count)
            }
            output.toByteArray()
        } ?: error("Não foi possível abrir essa foto.")
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=100_000_000) { "Escolha uma imagem válida." }
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1024) sample*=2
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample }) ?: error("Foto inválida.")
        val orientation=runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) }.getOrDefault(1)
        val matrix=Matrix().apply {
            when(orientation) {
                2 -> setScale(-1f,1f); 3 -> setRotate(180f); 4 -> setScale(1f,-1f)
                5 -> { setRotate(90f); postScale(-1f,1f) }; 6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f,1f) }; 8 -> setRotate(270f)
            }
        }
        val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        val output=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(output);canvas.drawColor(Color.WHITE)
        val edge=minOf(rotated.width,rotated.height)
        val left=(rotated.width-edge)/2;val top=(rotated.height-edge)/2
        canvas.drawBitmap(rotated,Rect(left,top,left+edge,top+edge),Rect(0,0,512,512),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        val target=file(uid);val temporary=File.createTempFile("avatar-",".jpg",target.parentFile)
        try {
            temporary.outputStream().use { stream -> check(output.compress(Bitmap.CompressFormat.JPEG,88,stream));stream.fd.sync() }
            check(authorized()) { "A conta mudou. Escolha a foto novamente." }
            check(temporary.renameTo(target)) { "Não foi possível salvar a foto." }
        } finally {
            temporary.delete();output.recycle();if(rotated!==bitmap) rotated.recycle();bitmap.recycle()
        }
    }
}
