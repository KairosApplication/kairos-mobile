package com.example.kairos.model.home

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONObject
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader

/** Reads published lists without retaining unrelated rows or nested response fields. */
object StockerListReader {
    fun alerts(input: InputStream, employeeId: Long): List<JSONObject> = read(input,
        setOf("id", "status", "employeeId", "shelfId", "productId", "description", "stockoutDate")) {
        it.optString("status") == "OPEN" && it.optLong("employeeId", -1) == employeeId
    }

    fun read(input: InputStream, fields: Set<String>, keep: (JSONObject) -> Boolean): List<JSONObject> {
        val bounded = object : FilterInputStream(input) {
            private var count = 0L
            private fun counted(size: Int): Int {
                if (size > 0) count += size
                require(count <= 32L * 1024 * 1024) { "A lista do mercado excedeu o tamanho permitido. Solicite ajuda ao suporte." }
                return size
            }
            override fun read(): Int = `in`.read().also { counted(if (it < 0) 0 else 1) }
            override fun read(buffer: ByteArray, offset: Int, length: Int) = counted(`in`.read(buffer, offset, length))
        }
        JsonReader(InputStreamReader(bounded, Charsets.UTF_8)).use { reader ->
            val result = ArrayList<JSONObject>()
            var rows = 0
            reader.beginArray()
            while (reader.hasNext()) {
                require(++rows <= 200_000) { "O mercado retornou registros demais para esta consulta." }
                val row = JSONObject()
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name !in fields) { reader.skipValue(); continue }
                    val value: Any = when (reader.peek()) {
                        JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
                        JsonToken.STRING -> reader.nextString()
                        JsonToken.NUMBER -> reader.nextString().toLong()
                        JsonToken.BOOLEAN -> reader.nextBoolean()
                        else -> throw IllegalArgumentException("O serviço retornou um campo em formato inválido.")
                    }
                    row.put(name, value)
                }
                reader.endObject()
                if (keep(row)) {
                    require(result.size < 10_000) { "Há registros demais para exibir. Solicite uma consulta menor ao suporte." }
                    result.add(row)
                }
            }
            reader.endArray()
            require(reader.peek() == JsonToken.END_DOCUMENT) { "O serviço retornou dados adicionais inesperados." }
            return result
        }
    }
}
