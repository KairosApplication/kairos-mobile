package com.example.kairos.model.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/** Shared image preparation for the stocker scanner and customer damage attachment. */
object CapturedPhoto {
    fun preview(file:File,maxDimension:Int=600):Bitmap {
        require(maxDimension in 1..1600)
        require(file.length() in 1..24L*1024*1024) { "Foto inválida ou maior que 24 MiB. Repita a captura." }
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(file.path,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=100_000_000) {
            "Resolução inválida."
        }
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxDimension) sample*=2
        val decoded=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample })
            ?: throw IllegalArgumentException("Não foi possível ler a foto.")
        try {
            val matrix=Matrix()
            when(ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f,1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f,-1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f);matrix.postScale(-1f,1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f);matrix.postScale(-1f,1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            }
            if(matrix.isIdentity) return decoded
            return Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
                .also { if(it!==decoded) decoded.recycle() }
        } catch(error:Exception) { decoded.recycle();throw error }
    }

    fun jpeg(file:File,maxBytes:Int=4*1024*1024):ByteArray {
        require(maxBytes in 1..4*1024*1024)
        val bitmap=preview(file,1600)
        val bytes=try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG,85,output)) { "Não foi possível preparar a foto." }
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
        require(bytes.size<=maxBytes) { "A imagem excede o limite permitido. Repita a captura." }
        return bytes
    }
}
