package co.uk.owenrumney.boltdb.explorer

import kotlinx.serialization.Serializable

/**
 * Data classes for communication with the Go helper binary
 */
@Serializable
data class BoltKey(
    val keyBase64: String,
    val valueSize: Int,
    val isBucket: Boolean
)

@Serializable
data class KeysResult(
    val items: List<BoltKey>? = null,
    val nextAfterKey: String? = null,
    val approxReturned: Int
) {
    // Helper property to get non-null items
    val safeItems: List<BoltKey> get() = items ?: emptyList()
}

@Serializable
data class HeadResult(
    val mode: String,
    val totalSize: Int,
    val valueHeadBase64: String
)

@Serializable
data class SearchItem(
    val path: List<String>,
    val keyBase64: String,
    val valueSize: Int,
    val isBucket: Boolean,
    val type: String
)

@Serializable
data class SearchResult(
    val items: List<SearchItem>? = null,
    val total: Int = 0,
    val limited: Boolean = false
) {
    // Helper property to get non-null items
    val safeItems: List<SearchItem> get() = items ?: emptyList()
}
