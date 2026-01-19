package com.liteledger.app.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine as combine5

// Extension to combine 6 flows (standard only supports 5)
inline fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6) -> R
): Flow<R> = combine5(
    combine5(flow1, flow2, flow3) { a, b, c -> Triple(a, b, c) },
    combine5(flow4, flow5, flow6) { d, e, f -> Triple(d, e, f) }
) { (a, b, c), (d, e, f) ->
    transform(a, b, c, d, e, f)
}
