package com.grillpoet.lighty.core

/**
 * 已写入 DebugLog 的异常。上层不要重复记录。
 */
class LoggedException(message: String, cause: Throwable? = null) : Exception(message, cause)