package com.example.kairos.view

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.widget.EditText

/** Numeric keyboard with automatic DD/MM/YYYY separators, including pasted dates. */
object DateInputMask {
    fun attach(field: EditText) {
        field.keyListener=DigitsKeyListener.getInstance("0123456789/")
        field.setRawInputType(InputType.TYPE_CLASS_NUMBER)
        field.addTextChangedListener(object:TextWatcher {
            private var formatting=false
            private var previous=""
            private var removedSeparator=false
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {
                if(!formatting) previous=s.toString()
            }
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                if(!formatting) removedSeparator=before==1 && count==0 && previous.getOrNull(start)=='/'
            }
            override fun afterTextChanged(value:Editable?) {
                if(formatting || value==null) return
                val cursor=field.selectionStart.coerceIn(0,value.length)
                var beforeCursor=value.take(cursor).count { it in '0'..'9' }
                var digits=value.filter { it in '0'..'9' }.take(8).toString()
                if(removedSeparator && beforeCursor>0) {
                    digits=digits.removeRange(beforeCursor-1,beforeCursor)
                    beforeCursor--
                }
                val formatted=buildString {
                    digits.forEachIndexed { index,char ->
                        if(index==2 || index==4) append('/')
                        append(char)
                    }
                }
                if(value.toString()==formatted) return
                formatting=true
                try {
                    value.replace(0,value.length,formatted)
                    var position=0
                    var seen=0
                    while(position<formatted.length && seen<beforeCursor) {
                        if(formatted[position].isDigit()) seen++
                        position++
                    }
                    field.setSelection(position.coerceAtMost(value.length))
                } finally { formatting=false }
            }
        })
    }
}
