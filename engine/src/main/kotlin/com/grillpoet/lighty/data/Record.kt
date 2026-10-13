package com.grillpoet.lighty.data

/**
 * 通用数据单元。所有字段平等，全是字符串。
 * UI 只读它认识的 key，源读写它需要的 key。
 */
typealias Record = Map<String, String>