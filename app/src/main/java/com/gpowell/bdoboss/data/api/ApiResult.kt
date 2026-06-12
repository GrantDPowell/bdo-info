package com.gpowell.bdoboss.data.api

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data object NoKey : ApiResult<Nothing>()
    data class HttpError(val code: Int) : ApiResult<Nothing>()
    data object Offline : ApiResult<Nothing>()

    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        NoKey -> NoKey
        is HttpError -> HttpError(code)
        Offline -> Offline
    }
}
