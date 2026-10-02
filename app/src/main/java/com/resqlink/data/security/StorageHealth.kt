package com.resqlink.data.security

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update

/** Retains the failure until that source succeeds; a retry never substitutes empty user data. */
@Singleton
class StorageHealth @Inject constructor() {
    private val failures = MutableStateFlow<Set<String>>(emptySet())
    private val retryGeneration = MutableStateFlow(0L)
    val failedSources: StateFlow<Set<String>> = failures

    fun retry() { retryGeneration.update { it + 1 } }

    fun <T> observe(source: String, upstream: Flow<T>): Flow<T> = upstream
        .onEach { failures.update { it - source } }
        .retryWhen { cause, _ ->
            if (cause is CancellationException || cause !is Exception) throw cause
            val generation = retryGeneration.value
            failures.update { it + source }
            retryGeneration.first { it != generation }
            true
        }
}
