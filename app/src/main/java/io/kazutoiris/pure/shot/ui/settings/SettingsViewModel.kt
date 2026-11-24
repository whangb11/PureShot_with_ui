package io.kazutoiris.pure.shot.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val state: StateFlow<SettingsState> = repository.state

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.initialize()
        }
    }

    fun set(key: String, value: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!repository.setBoolean(key, value)) {
                _events.emit(UiEvent.SaveFailed)
            }
        }
    }

    fun reset() {
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.resetToDefaults()) {
                _events.emit(UiEvent.ResetDone)
            } else {
                _events.emit(UiEvent.SaveFailed)
            }
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}

sealed interface UiEvent {
    data object SaveFailed : UiEvent
    data object ResetDone : UiEvent
}
