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

data class StockerHomeUiState(
    val loading: Boolean = false,
    val data: StockerHomeData? = null,
    val failed: Boolean = false
)

class StockerHomeViewModel(private val repository: StockerHomeRepository) : ViewModel() {
    private val mutableState = MutableLiveData(StockerHomeUiState())
    val state: LiveData<StockerHomeUiState> = mutableState
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var currentUserId: String? = null
    private var generation = 0L

    fun load(userId: String, force: Boolean = false) {
        if (userId == currentUserId && (!force || mutableState.value?.loading == true)) return
        currentUserId = userId
        val request = ++generation
        mutableState.value = StockerHomeUiState(loading = true)
        worker.execute {
            val result = try {
                StockerHomeUiState(data = repository.load(userId))
            } catch (_: Exception) {
                StockerHomeUiState(failed = true)
            }
            main.post {
                // A result from a previous account must never reach the current Home.
                if (request == generation) mutableState.value = result
            }
        }
    }

    fun reset() {
        generation++
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
