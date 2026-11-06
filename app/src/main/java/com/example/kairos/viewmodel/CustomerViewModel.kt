package com.example.kairos.viewmodel

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.*
import com.example.kairos.model.cliente.*
import java.util.concurrent.Executors
import com.example.kairos.model.network.ApiFailure

data class CustomerUiState(val data: CustomerData? = null,val loading: Boolean = false,val message: String = "",val httpStatus: Int? = null)
class CustomerViewModel : ViewModel() {
    private val repository: CustomerRepository=PublishedCustomerRepository()
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val mutable=MutableLiveData(CustomerUiState())
    val state: LiveData<CustomerUiState> = mutable
    private var owner: String?=null
    private var generation=0L
    fun reset() {
        owner=null
        generation++
        mutable.value=CustomerUiState()
    }
    fun load(uid: String,force: Boolean=false) {
        if(mutable.value?.loading==true || owner==uid && !force) return
        owner=uid
        val ticket=++generation
        mutable.value=mutable.value!!.copy(loading=true,message="",httpStatus=null)
        worker.execute {
            val result=runCatching { repository.home(uid) }
            main.post {
                if(generation==ticket && owner==uid) mutable.value=result.fold(
                    { CustomerUiState(data=it) },
                    { CustomerUiState(message=it.message ?: "Não foi possível carregar. Tente novamente.",httpStatus=(it as? ApiFailure)?.status) })
            }
        }
    }
    fun favorite(uid: String,product: CustomerProduct) {
        if(owner!=uid || mutable.value?.loading==true) return
        val data=mutable.value?.data ?: return
        val ticket=++generation
        mutable.value=mutable.value!!.copy(loading=true,message="")
        worker.execute {
            val result=runCatching {
                repository.favorite(uid,product.id,!product.favorite)
                data.copy(products=data.products.map { if(it.id==product.id) it.copy(favorite=!product.favorite) else it })
            }
            main.post {
                if(generation==ticket && owner==uid) mutable.value=result.fold(
                    { CustomerUiState(data=it,message="Favoritos salvos neste aparelho.") },
                    { mutable.value!!.copy(loading=false,message=it.message ?: "Não foi possível salvar o favorito.",httpStatus=(it as? ApiFailure)?.status) })
            }
        }
    }
    override fun onCleared() { generation++; worker.shutdownNow(); main.removeCallbacksAndMessages(null) }
}
