package com.example.kairos.model.cliente

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigDecimal

data class PublishedCatalog(val products: List<CustomerProduct>, val truncated: Boolean)

/** Retains only mobile product fields, without materializing the full JSON response. */
object PublishedCatalogReader {
    fun read(input: InputStream, maxProducts: Int = 10_000): PublishedCatalog {
        require(maxProducts>0)
        val bounded=object:java.io.FilterInputStream(input) {
            private var count=0L
            private fun counted(size:Int):Int {
                if(size>0) count+=size
                require(count<=32L*1024*1024) { "O catálogo é muito grande para esta consulta. Solicite ajuda ao suporte do Kairos." }
                return size
            }
            override fun read():Int { val value=`in`.read(); counted(if(value<0) 0 else 1); return value }
            override fun read(buffer:ByteArray,offset:Int,length:Int)=counted(`in`.read(buffer,offset,length))
        }
        try {
            JsonReader(InputStreamReader(bounded,Charsets.UTF_8)).use { reader ->
                val products=ArrayList<CustomerProduct>()
                reader.beginArray()
                while(reader.hasNext()) {
                    if(products.size==maxProducts) return PublishedCatalog(products,true)
                    var id:Long?=null
                    var name:String?=null
                    var brand=""
                    var price:BigDecimal?=null
                    reader.beginObject()
                    while(reader.hasNext()) {
                        when(reader.nextName()) {
                            "id" -> id=reader.nextLong()
                            "name" -> name=reader.nextString()
                            "brand" -> if(reader.peek()==JsonToken.NULL) reader.nextNull() else brand=reader.nextString()
                            "price" -> if(reader.peek()==JsonToken.NULL) reader.nextNull() else price=reader.nextString().toBigDecimal()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    require(id!=null && id>0 && !name.isNullOrBlank()) { "O serviço retornou um produto sem identificação válida." }
                    products.add(CustomerProduct(id,name,brand,price,false))
                }
                reader.endArray()
                return PublishedCatalog(products,false)
            }
        } catch(error:java.io.IOException) {
            throw IllegalArgumentException("Não foi possível concluir a leitura do catálogo. Tente atualizar novamente.",error)
        }
    }
}
