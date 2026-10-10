package com.fnosari.bluetoothhid.persistance.preferences

/**
 * PreferencesProvider is an interface for managing key-value preferences in an application.
 * It provides methods to read and write preferences with various data types.
 *
 * Methods:
 * - `readString`: Reads a String value associated with the given key. Returns a default value if the key is not found.
 * - `readInt`: Reads an Int value associated with the given key. Returns a default value if the key is not found.
 * - `readBoolean`: Reads a Boolean value associated with the given key. Returns a default value if the key is not found.
 * - `readDouble`: Reads a Double value associated with the given key. Returns a default value if the key is not found.
 * - `write`: Stores a value associated with the given key. Supports String, Int, Boolean, and Double data types.
 *
 * Usage:
 * Implement this interface to provide a concrete implementation for reading and writing preferences,
 * such as using SharedPreferences in Android or any other storage mechanism.
 */

interface PreferencesProvider {

    /**
     * Reads a String value associated with the given key.
     * @param key The key to identify the preference.
     * @param default The default value to return if the key does not exist.
     * @return The String value associated with the key, or the default value if the key is not found.
     */
    fun readString(key: String, default: String): String

    /**
     * Reads an Int value associated with the given key.
     * @param key The key to identify the preference.
     * @param default The default value to return if the key does not exist.
     * @return The Int value associated with the key, or the default value if the key is not found.
     */
    fun readInt(key: String, default: Int): Int

    /**
     * Reads a Boolean value associated with the given key.
     * @param key The key to identify the preference.
     * @param default The default value to return if the key does not exist.
     * @return The Boolean value associated with the key, or the default value if the key is not found.
     */
    fun readBoolean(key: String, default: Boolean): Boolean

    /**
     * Reads a Double value associated with the given key.
     * @param key The key to identify the preference.
     * @param default The default value to return if the key does not exist.
     * @return The Double value associated with the key, or the default value if the key is not found.
     */
    fun readDouble(key: String, default: Double): Double

    /**
     * Stores a String value associated with the given key.
     * @param key The key to identify the preference.
     * @param value The String value to store.
     */
    fun write(key: String, value: String)

    /**
     * Stores an Int value associated with the given key.
     * @param key The key to identify the preference.
     * @param value The Int value to store.
     */
    fun write(key: String, value: Int)

    /**
     * Stores a Boolean value associated with the given key.
     * @param key The key to identify the preference.
     * @param value The Boolean value to store.
     */
    fun write(key: String, value: Boolean)

    /**
     * Stores a Double value associated with the given key.
     * @param key The key to identify the preference.
     * @param value The Double value to store.
     */
    fun write(key: String, value: Double)


}