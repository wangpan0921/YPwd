package com.wp.ypwd

import android.content.Context
import android.os.Build
import android.os.Environment
import android.widget.Toast
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import java.io.File
import java.io.FileOutputStream

object ExcelUtils {
    // 检查是否有存储权限
    private fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 检查运行时权限
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            // 6.0以下默认有权限
            true
        }
    }
    // 导出密码数据到Excel
    fun exportToExcel(context: Context, items: List<PasswordItem>, masterPassword: String): File? {
        if (!hasStoragePermission(context)) {
            Toast.makeText(context, "请先授予存储权限", Toast.LENGTH_SHORT).show()
            return null
        }

        return try {
            // 创建工作簿
            val workbook: Workbook = HSSFWorkbook()
            // 创建工作表
            val sheet: Sheet = workbook.createSheet("密码记录")

            // 创建表头
            val headerRow: Row = sheet.createRow(0)
            val headers = arrayOf("序号", "账户", "密码", "提示")
            headers.forEachIndexed { index, header ->
                val cell: Cell = headerRow.createCell(index)
                cell.setCellValue(header)
            }

            // 填充数据
            items.forEachIndexed { index, item ->
                val row: Row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(item.id.toDouble())
                row.createCell(1).setCellValue(item.account)
                // 解密密码后存储
                row.createCell(2).setCellValue(EncryptionUtils.decrypt(item.password, masterPassword))
                row.createCell(3).setCellValue(item.hint)
            }

            // 调整列宽
//            for (i in headers.indices) {
//                sheet.autoSizeColumn(i)
//            }

            // 保存文件到外部存储
//            val directory = File(Environment.getExternalStorageDirectory(), "密码管理器")
            val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // download 目录
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "密码管理器")
                // Android 10+ 推荐使用应用专属目录（无需额外权限）
//                File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "密码管理器")
            } else {
                // 旧版本使用公共目录（需权限）
                File(Environment.getExternalStorageDirectory(), "密码管理器")
            }
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, "密码记录_${System.currentTimeMillis()}.xls")
            val fileOut = FileOutputStream(file)
            workbook.write(fileOut)
            fileOut.close()
            workbook.close()

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

}
    