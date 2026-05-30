package com.wp.ypwd

import java.io.Serializable

data class PasswordItem(
    var id: Int = 0,
    var account: String,
    var password: String,
    var hint: String
) : Serializable{
    // 搜索辅助方法
    fun containsKeyword(keyword: String): Boolean {
        val lowerKeyword = keyword.lowercase()
        return account.lowercase().contains(lowerKeyword) ||
                password.lowercase().contains(lowerKeyword) ||
                hint.lowercase().contains(lowerKeyword)
    }

// 从Excel行解析的辅助构造函数
    companion object {
    fun fromExcelRow(
        rowNumber: Int,
        account: String,
        password: String,
        hint: String = ""
    ): PasswordItem {
        return PasswordItem(
            id = rowNumber,
            account = account.trim(),
            password = password.trim(),
            hint = hint.trim()
        )
        }
    }
}
    