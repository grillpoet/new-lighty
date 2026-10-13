package com.grillpoet.lighty.nav

import androidx.compose.runtime.Immutable
import com.grillpoet.lighty.data.Record

@Immutable
sealed interface Screen {
    data object Main : Screen
    data object Player : Screen
    data object Favorites : Screen
    data object Config : Screen
    data object Downloads : Screen

    /** 统一详情页 */
    data class Detail(val sourceId: String, val record: Record) : Screen

    data class Video(val sourceId: String, val record: Record) : Screen
    data class Manga(val sourceId: String, val record: Record) : Screen
    data class Novel(val sourceId: String, val record: Record) : Screen

    /** 通用子页面：源声明一个 action，页面加载并展示列表 */
    data class Page(
        val sourceId: String,
        val title: String,
        val action: String,
        val args: Record
    ) : Screen
}