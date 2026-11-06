package com.example.kairos.model.cliente

import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal

/** Shared display contract for unverified AI suggestions; never a purchase confirmation. */
object ReceiptAiResult {
    fun parse(text:String):JSONObject {
        require(text.toByteArray(Charsets.UTF_8).size<=256*1024) { "A leitura retornou dados demais. Fotografe uma parte menor da nota." }
        val result=JSONObject(text)
        val input=result.getJSONArray("items")
        require(input.length()<=100) { "A leitura excedeu 100 itens. Fotografe a nota em partes." }
        val items=JSONArray()
        for(index in 0 until input.length()) {
            val item=input.getJSONObject(index)
            val description=item.optString("description","").trim()
            if(description.isBlank() || description.length>200) continue
            fun number(name:String,max:String,positive:Boolean):Any {
                val value=(item.opt(name) as? Number)?.toString()?.toBigDecimalOrNull() ?: return JSONObject.NULL
                if(value.signum()<0 || positive && value.signum()==0 || value>BigDecimal(max)) return JSONObject.NULL
                if(name=="amount" && value.stripTrailingZeros().scale()>2) return JSONObject.NULL
                return value
            }
            items.put(JSONObject().put("description",description).put("quantity",number("quantity","100000",true))
                .put("amount",number("amount","1000000",false)))
        }
        val key=result.optString("accessKey","")
        return JSONObject().put("accessKey",if(key.matches(Regex("[0-9]{44}"))) key else JSONObject.NULL)
            .put("items",items).put("source","AI_SUGGESTION_UNVERIFIED")
    }
}
