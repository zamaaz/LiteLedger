package com.liteledger.app.data

enum class SortOption {
    RECENT_ACTIVITY,
    OLDEST_ACTIVITY,
    HIGHEST_AMOUNT,
    LOWEST_AMOUNT,
    NAME_AZ,
    UNSETTLED_FIRST
}

val SortOption.displayName: String
    get() = when (this) {
        SortOption.RECENT_ACTIVITY -> "Recent activity"
        SortOption.OLDEST_ACTIVITY -> "Oldest activity"
        SortOption.HIGHEST_AMOUNT -> "Highest amount"
        SortOption.LOWEST_AMOUNT -> "Lowest amount"
        SortOption.NAME_AZ -> "Name (A–Z)"
        SortOption.UNSETTLED_FIRST -> "Unsettled first"
    }
