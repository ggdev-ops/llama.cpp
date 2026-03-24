package klama.ai.compose.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import klama.ai.compose.io.FileSystemProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

data class ModelStatus(
    val modelPath: String?,
    val modelName: String?,
    val architecture: String?,
    val chatHistory: String?
)

class SettingsRepository(private val fileSystemProvider: FileSystemProvider) {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
        val safeZone = fileSystemProvider.getSafeZoneDirectory()
        safeZone.resolve("settings.preferences_pb").toString().toPath()
    }

    val modelStatus: Flow<ModelStatus> = dataStore.data.map { prefs ->
        ModelStatus(
            modelPath = prefs[MODEL_PATH_KEY],
            modelName = prefs[MODEL_NAME_KEY],
            architecture = prefs[ARCHITECTURE_KEY],
            chatHistory = prefs[CHAT_HISTORY_KEY]
        )
    }

    suspend fun saveModelStatus(status: ModelStatus) {
        dataStore.edit { prefs ->
            val cleanPath = status.modelPath?.trim()
            if (cleanPath != null) prefs[MODEL_PATH_KEY] = cleanPath else prefs.remove(MODEL_PATH_KEY)
            if (status.modelName != null) prefs[MODEL_NAME_KEY] = status.modelName else prefs.remove(MODEL_NAME_KEY)
            if (status.architecture != null) prefs[ARCHITECTURE_KEY] = status.architecture else prefs.remove(ARCHITECTURE_KEY)
            if (status.chatHistory != null) prefs[CHAT_HISTORY_KEY] = status.chatHistory else prefs.remove(CHAT_HISTORY_KEY)
        }
    }

    companion object {
        private val MODEL_PATH_KEY = stringPreferencesKey("model_path")
        private val MODEL_NAME_KEY = stringPreferencesKey("model_name")
        private val ARCHITECTURE_KEY = stringPreferencesKey("architecture")
        private val CHAT_HISTORY_KEY = stringPreferencesKey("chat_history")
    }
}
