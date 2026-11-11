package com.example.pleos.core

/**
 * Helpers for Vehicle SDK error handling.
 *
 * The documented contract is `VehicleException.errorCode: ErrorCode`, but the 2.3.2 AAR ships the
 * exception class obfuscated (`ai.pleos.playground.vehicle.callback.exception.a`), so we read the
 * error code reflectively (any zero-arg method returning an `ErrorCode` enum) and fall back to the
 * message, which the SDK formats as "...Error code: <CODE>".
 */
object PleosErrors {

    /** Error code names as defined in ai.pleos.playground.vehicle.constant.ErrorCode. */
    const val ERROR_NOT_SUPPORTED_PROPERTY = "ERROR_NOT_SUPPORTED_PROPERTY"
    const val ERROR_NOT_SUPPORTED_AREA = "ERROR_NOT_SUPPORTED_AREA"
    const val ERROR_NOT_SUPPORTED_VALUE_TYPE = "ERROR_NOT_SUPPORTED_VALUE_TYPE"
    const val ERROR_SECURITY_PERMISSION = "ERROR_SECURITY_PERMISSION"
    const val ERROR_SERVICE_CONNECTION_FAILURE = "ERROR_SERVICE_CONNECTION_FAILURE"
    const val ERROR_INTERNAL_SERVICE = "ERROR_INTERNAL_SERVICE"
    const val ERROR_DRIVING_RESTRICTED = "ERROR_DRIVING_RESTRICTED"
    const val ERROR_EMPTY_RESULT = "ERROR_EMPTY_RESULT"
    const val ERROR_UNDEFINED = "ERROR_UNDEFINED"

    /** Returns the Vehicle SDK error code name, or null if [e] is not a Vehicle SDK exception. */
    fun codeOf(e: Throwable): String? {
        if (!e.javaClass.name.startsWith("ai.pleos.playground.vehicle")) return null
        val viaReflection = e.javaClass.methods
            .firstOrNull { m -> m.parameterCount == 0 && m.returnType.isEnum && m.returnType.simpleName == "ErrorCode" }
            ?.runCatching { invoke(e) }
            ?.getOrNull()
            ?.toString()
        if (viaReflection != null) return viaReflection
        val msg = e.message ?: return null
        return Regex("Error code:\\s*([A-Z_]+)").find(msg)?.groupValues?.get(1)
    }

    fun isServiceConnectionFailure(e: Throwable): Boolean =
        codeOf(e) == ERROR_SERVICE_CONNECTION_FAILURE

    fun isNotSupported(e: Throwable): Boolean = when (codeOf(e)) {
        ERROR_NOT_SUPPORTED_PROPERTY, ERROR_NOT_SUPPORTED_AREA, ERROR_NOT_SUPPORTED_VALUE_TYPE -> true
        else -> false
    }

    /** Missing manifest permission, either as a raw SecurityException or as the SDK's error code. */
    fun isPermissionDenied(e: Throwable): Boolean =
        e is SecurityException || codeOf(e) == ERROR_SECURITY_PERMISSION

    fun describe(e: Throwable): String = codeOf(e)?.let { "$it: ${e.message}" } ?: (e.message ?: e.toString())
}
