package com.example.kairos.viewmodel

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.kairos.model.home.StockerHomeData
import com.example.kairos.model.home.StockerHomeRepository
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

data class StockerHomeUiState(
    val loading: Boolean = false,
    val data: StockerHomeData? = null,
    val failed: Boolean = false,
    val message: String = "",
    val httpStatus: Int? = null
)

class StockerHomeViewModel(
    private val repository: StockerHomeRepository,
    private val worker: ExecutorService = Executors.newCachedThreadPool()
) : ViewModel() {
    private val mutableState = MutableLiveData(StockerHomeUiState())
    val state: LiveData<StockerHomeUiState> = mutableState
    private var activeRequest: Future<*>? = null
    private val main = Handler(Looper.getMainLooper())
    private var currentUserId: String? = null
    private var generation = 0L

    fun load(userId: String, force: Boolean = false) {
        if (userId == currentUserId && (!force || mutableState.value?.loading == true)) return
        currentUserId = userId
        val request = ++generation
        activeRequest?.cancel(true)
        mutableState.value = StockerHomeUiState(loading = true)
        // A previous repository call may ignore interruption; give the new call its own worker.
        activeRequest = worker.submit {
            val result = try {
                StockerHomeUiState(data = repository.load(userId))
            } catch (error: Exception) {
                StockerHomeUiState(failed = true, message = if (error is com.example.kairos.model.network.ApiFailure || error is IllegalArgumentException)
                    error.message.orEmpty() else "Não foi possível carregar os dados. Tente novamente.",
                    httpStatus = (error as? com.example.kairos.model.network.ApiFailure)?.status)
            }
            main.post {
                // A result from a previous account must never reach the current Home.
                if (request == generation) mutableState.value = result
            }
        }
    }

    fun reset() {
        generation++
        activeRequest?.cancel(true)
        activeRequest = null
        currentUserId = null
        mutableState.value = StockerHomeUiState()
    }

    override fun onCleared() {
        generation++
        worker.shutdownNow()
        main.removeCallbacksAndMessages(null)
    }

    class Factory(private val repository: StockerHomeRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StockerHomeViewModel(repository) as T
    }
}
