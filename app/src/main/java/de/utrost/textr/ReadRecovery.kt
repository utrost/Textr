package de.utrost.textr

/** Classify only evidence provided by the platform; a missing stream does not prove deletion. */
internal fun readErrorMessage(error: Exception): Int = when (error) {
    is SecurityException -> R.string.access_denied
    is java.io.FileNotFoundException -> R.string.file_unavailable
    is android.os.DeadObjectException -> R.string.provider_unavailable
    is DocumentException -> when (error.reason) {
        ReadFailure.TOO_LARGE -> R.string.too_large
        ReadFailure.BINARY -> R.string.binary_file
        ReadFailure.ENCODING -> R.string.unsupported_encoding
        ReadFailure.SOURCE -> R.string.unsupported_uri
        ReadFailure.ACCESS -> R.string.access_error
    }
    else -> R.string.access_error
}
