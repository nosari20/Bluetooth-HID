package com.fnosari.bluetoothhid.persistance.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Implementation of the PreferencesProvider interface using Android's DataStore for persistent storage.
 *
 * @param context The application context used to access DataStore.
 * @param scope The CoroutineScope used for asynchronous operations.
 */
class DataStorePreferencesProvider(private val context: Context, private val scope: CoroutineScope) : PreferencesProvider {

    companion object {
        private const val LOG_TAG = "DataStorePreferencesProvider"

        /**
         * Extension property to create a DataStore instance named "preferences" for storing key-value pairs.
         */
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore("preferences")
    }

    /**
     * Reads a String value from DataStore.
     * @param key The key to identify the preference.
     * @param default The default value to return if the key does not exist.
     * @return The String value associated with the key, or the default value if not found.
     */
    override fun readString(key: String, default: String): String {
        Log.d(LOG_TAG, "Retrieving string from DataStore with key $key and default $default")

        val data = runBlocking {
            context.dataStore.data
                .map { preferences -> preferences[stringPreferencesKey(key)] ?: default }
                .first()
        }

        Log.d(LOG_TAG, "$key = $data")
        return data
    }

    /**
     * Reads an Int value from DataStore.
     * @param key The key to identify the preference.
     * @param default The default value to return if the key does not exist.
     * @return The Int value associated with the key, or the default value if not found.
     */
    override fun readInt(key: String, default: Int): Int {
        Log.d(LOG_TAG, "Retrieving int from DataStore with key $key and default $default")

        val data = runBlocking {
            context.dataStore.data
                .map { preferences -> preferences[intPreferencesKey(key)] ?: default }
                .first()
        }

        Log.d(LOG_TAG, "$key = $data")
        return data
    }

    /**
     * Reads a Boolean value from DataStore.
     * @param key The key to identify the preference.
     * @param default The default value to return if the key does not exist.
     * @return The Boolean value associated with the key, or the default value if not found.
     */
    override fun readBoolean(key: String, default: Boolean): Boolean {
        Log.d(LOG_TAG, "Retrieving boolean from DataStore with key $key and default $default")

        val data = runBlocking {
            context.dataStore.data
                .map { preferences -> preferences[booleanPreferencesKey(key)] ?: default }
                .first()
        }

        Log.d(LOG_TAG, "$key = $data")
        return data
    }

    /**
     * Reads a Double value from DataStore.
     * @param key The key to identify the preference.
     * @param default The default value to return if the key does not exist.
     * @return The Double value associated with the key, or the default value if not found.
     */
    override fun readDouble(key: String, default: Double): Double {
        Log.d(LOG_TAG, "Retrieving double from DataStore with key $key and default $default")

        val data = runBlocking {
            context.dataStore.data
                .map { preferences -> preferences[doublePreferencesKey(key)] ?: default }
                .first()
        }

        Log.d(LOG_TAG, "$key = $data")
        return data
    }

    /**
     * Writes a String value to DataStore.
     * @param key The key to identify the preference.
     * @param value The String value to store.
     */
    override fun write(key: String, value: String) {
        Log.d(LOG_TAG, "Writing string to DataStore with key $key and value $value")

        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[stringPreferencesKey(key)] = value
            }
        }
    }

    /**
     * Writes an Int value to DataStore.
     * @param key The key to identify the preference.
     * @param value The Int value to store.
     */
    override fun write(key: String, value: Int) {
        Log.d(LOG_TAG, "Writing int to DataStore with key $key and value $value")

        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[intPreferencesKey(key)] = value
            }
        }
    }

    /**
     * Writes a Boolean value to DataStore.
     * @param key The key to identify the preference.
     * @param value The Boolean value to store.
     */
    override fun write(key: String, value: Boolean) {
        Log.d(LOG_TAG, "Writing boolean to DataStore with key $key and value $value")

        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[booleanPreferencesKey(key)] = value
            }
        }
    }

    /**
     * Writes a Double value to DataStore.
     * @param key The key to identify the preference.
     * @param value The Double value to store.
     */
    override fun write(key: String, value: Double) {
        Log.d(LOG_TAG, "Writing double to DataStore with key $key and value $value")

        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[doublePreferencesKey(key)] = value
            }
        }
    }
}
