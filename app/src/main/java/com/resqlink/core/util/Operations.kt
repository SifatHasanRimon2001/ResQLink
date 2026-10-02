package com.resqlink.core.util

import kotlinx.coroutines.CancellationException

/** Recover from operational failures without swallowing coroutine cancellation. */
suspend fun <T> attemptOperation(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}
