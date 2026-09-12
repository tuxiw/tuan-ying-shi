package com.example.tuanyingshi.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend inline fun <reified T> safeCall(
    crossinline apiCall: suspend () -> T
): Result<T> {
    return withContext(Dispatchers.IO) {
        try {
            Result.Success(apiCall())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

suspend fun <T> invokeApi(
    apiCall: suspend () -> T
): Resource<T> {
    return withContext(Dispatchers.IO) {
        try {
            Resource.Success(apiCall.invoke())
        } catch (throwable: Throwable) {
            Resource.Error(error = throwable)
        }
    }
}

sealed interface Result<out T> {
    data class Success<out T>(val data: T) : Result<T>
    data class Error(val error: Throwable) : Result<Nothing>
}

inline fun <T, R> Result<T>.map(map: (T) -> R): Result<R> {
    return when (this) {
        is Result.Error -> Result.Error(error)
        is Result.Success -> Result.Success(map(data))
    }
}

inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    return when (this) {
        is Result.Error -> this
        is Result.Success -> {
            action(data)
            this
        }
    }
}

inline fun <T> Result<T>.onError(action: (Throwable) -> Unit): Result<T> {
    return when (this) {
        is Result.Error -> {
            action(error)
            this
        }
        is Result.Success -> this
    }
}

fun <T> Result<T>.asEmptyDataResult(): EmptyResult {
    return map { }
}

typealias EmptyResult = Result<Unit>
