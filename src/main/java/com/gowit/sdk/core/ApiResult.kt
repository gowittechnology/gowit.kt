package com.gowit.sdk.core

/**
 * Sealed class representing API call results
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()

    data class Error(val exception: GowitException) : ApiResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): ApiResult<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> this
        }
    }

    inline fun onSuccess(action: (T) -> Unit): ApiResult<T> {
        if (this is Success) {
            action(data)
        }
        return this
    }

    inline fun onError(action: (GowitException) -> Unit): ApiResult<T> {
        if (this is Error) {
            action(exception)
        }
        return this
    }
}

/**
 * Custom exception for Gowit SDK
 */
sealed class GowitException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkException(message: String, cause: Throwable? = null) : GowitException(message, cause)

    class ApiException(val code: Int, message: String, cause: Throwable? = null) : GowitException(message, cause)

    class ParseException(message: String, cause: Throwable? = null) : GowitException(message, cause)

    class ConfigurationException(message: String) : GowitException(message)

    class ValidationException(message: String) : GowitException(message)
}
