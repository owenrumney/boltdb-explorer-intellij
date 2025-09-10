package co.uk.owenrumney.boltdb.explorer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

/**
 * Client wrapper that provides the interface expected by the UI components
 * while using the new JsonElement-based BoltHelper
 */
class BoltDBClient {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }
    private var dbPath: String = ""

    fun setDatabasePath(path: String) {
        this.dbPath = path
    }

    fun listKeys(bucketPath: String, afterKey: String? = null, limit: Int = 100): KeysResult {
        val opts = BoltHelper.ListKeysOptions(
            limit = limit,
            afterKey = afterKey
        )

        val jsonResult = BoltHelper.listKeys(dbPath, bucketPath, opts)

        // Convert JsonElement to KeysResult using kotlinx.serialization
        return json.decodeFromJsonElement<KeysResult>(jsonResult)
    }

    fun getKeyHead(bucketPath: String, keyBase64: String): HeadResult {
        val jsonResult = BoltHelper.readHead(dbPath, bucketPath, keyBase64)
        return json.decodeFromJsonElement<HeadResult>(jsonResult)
    }

    fun search(query: String, caseSensitive: Boolean = false, limit: Int = 100): SearchResult {
        val jsonResult = BoltHelper.search(dbPath, query, limit, caseSensitive)
        return json.decodeFromJsonElement<SearchResult>(jsonResult)
    }

    fun createBucket(bucketPath: String) {
        BoltHelper.createBucket(dbPath, bucketPath)
    }

    fun putKey(bucketPath: String, keyBase64: String, valueBase64: String) {
        BoltHelper.putKeyValue(dbPath, bucketPath, keyBase64, valueBase64)
    }

    fun deleteKey(bucketPath: String, keyBase64: String) {
        BoltHelper.deleteKey(dbPath, bucketPath, keyBase64)
    }

    fun deleteBucket(bucketPath: String) {
        BoltHelper.deleteBucket(dbPath, bucketPath)
    }

    fun exportValue(bucketPath: String, keyBase64: String, outputPath: String) {
        BoltHelper.saveToFile(dbPath, bucketPath, keyBase64, outputPath)
    }
}
