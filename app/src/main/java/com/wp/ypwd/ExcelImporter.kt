package com.wp.ypwd

import android.content.Context
import android.net.Uri
import android.util.Log
import com.wp.ypwd.EncryptionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import java.io.InputStream

class ExcelImporter(private val context: Context) {

    companion object {
        private const val TAG = "ExcelImporter"

        // Excel表头映射
        private const val COLUMN_INDEX = 0     // A列：序号
        private const val COLUMN_ACCOUNT = 1   // B列：账户
        private const val COLUMN_PASSWORD = 2  // C列：密码
        private const val COLUMN_HINT = 3      // D列：提示
    }

    /**
     * 从Excel文件导入密码
     * @param uri Excel文件的Uri
     * @return 导入结果（成功列表和失败列表）
     */
    suspend fun importFromExcel(uri: Uri, masterPassword: String): ImportResult {
        return withContext(Dispatchers.IO) {
            val successItems = mutableListOf<PasswordItem>()
            val failedRows = mutableListOf<FailedRow>()

            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    parseExcelFile(inputStream, successItems, failedRows, masterPassword)
                }

                ImportResult(
                    totalRows = successItems.size + failedRows.size,
                    successCount = successItems.size,
                    failedCount = failedRows.size,
                    successItems = successItems,
                    failedRows = failedRows
                )
            } catch (e: Exception) {
                Log.e(TAG, "导入Excel失败", e)
                ImportResult(
                    totalRows = 0,
                    successCount = 0,
                    failedCount = 0,
                    successItems = emptyList(),
                    failedRows = emptyList(),
                    error = e.message ?: "未知错误"
                )
            }
        }
    }

    private fun parseExcelFile(
        inputStream: InputStream,
        successItems: MutableList<PasswordItem>,
        failedRows: MutableList<FailedRow>,
        masterPassword: String
    ) {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0) // 第一个工作表

        var rowNumber = 0
        for (row in sheet) {
            rowNumber++

            // 跳过表头（第一行）
            if (rowNumber == 1) continue

            // 跳过空行
            if (isRowEmpty(row)) continue

            try {
                val account = getCellStringValue(row.getCell(COLUMN_ACCOUNT))
                val password = getCellStringValue(row.getCell(COLUMN_PASSWORD))

                // 验证必需字段
                if (account.isBlank() || password.isBlank()) {
                    failedRows.add(FailedRow(
                        rowNumber = rowNumber,
                        account = account,
                        error = "账户或密码为空"
                    ))
                    continue
                }

                val hint = getCellStringValue(row.getCell(COLUMN_HINT))

                // 创建密码项
                val passwordItem: PasswordItem = PasswordItem.fromExcelRow(
                    rowNumber = rowNumber,
                    account = account,
//                    password = password,
                    password = EncryptionUtils.encrypt(password, masterPassword),
                    hint = hint
                )

                successItems.add(passwordItem)

            } catch (e: Exception) {
                failedRows.add(FailedRow(
                    rowNumber = rowNumber,
                    account = "",
                    error = "解析失败: ${e.message}"
                ))
            }
        }

        workbook.close()
    }

    private fun getCellStringValue(cell: Cell?): String {
        return when {
            cell == null -> ""
            cell.cellType == CellType.STRING -> cell.stringCellValue
            cell.cellType == CellType.NUMERIC -> {
                // 处理数字单元格
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.dateCellValue.toString()
                } else {
                    cell.numericCellValue.toString()
                }
            }
            cell.cellType == CellType.BOOLEAN -> cell.booleanCellValue.toString()
            cell.cellType == CellType.FORMULA -> {
                try {
                    cell.stringCellValue
                } catch (e: Exception) {
                    cell.numericCellValue.toString()
                }
            }
            else -> ""
        }
    }

    private fun isRowEmpty(row: Row): Boolean {
        for (cell in row) {
            if (cell != null && cell.cellType != CellType.BLANK) {
                return false
            }
        }
        return true
    }

    data class ImportResult(
        val totalRows: Int,
        val successCount: Int,
        val failedCount: Int,
        val successItems: List<PasswordItem>,
        val failedRows: List<FailedRow>,
        val error: String? = null
    )

    data class FailedRow(
        val rowNumber: Int,
        val account: String,
        val error: String
    )
}
