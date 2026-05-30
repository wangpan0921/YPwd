package com.wp.ypwd

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "PasswordManager.db"
        private const val DATABASE_VERSION = 1

        // 密码表
        private const val TABLE_PASSWORD = "passwords"
        private const val KEY_ID = "_id"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_PASSWORD = "password"
        private const val KEY_HINT = "hint"

        // 登录密码表
        private const val TABLE_LOGIN = "login"
        private const val KEY_LOGIN_PASSWORD = "login_password"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 创建密码表
        val createPasswordTable = """
            CREATE TABLE $TABLE_PASSWORD (
                $KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_ACCOUNT TEXT NOT NULL,
                $KEY_PASSWORD TEXT NOT NULL,
                $KEY_HINT TEXT
            )
        """.trimIndent()
        db.execSQL(createPasswordTable)

        // 创建登录密码表
        val createLoginTable = """
            CREATE TABLE $TABLE_LOGIN (
                $KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_LOGIN_PASSWORD TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createLoginTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PASSWORD")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LOGIN")
        onCreate(db)
    }

    // 保存登录密码
    fun saveLoginPassword(encryptedPassword: String): Long {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(KEY_LOGIN_PASSWORD, encryptedPassword)

        // 先删除已有密码
        db.delete(TABLE_LOGIN, null, null)
        // 插入新密码
        val id = db.insert(TABLE_LOGIN, null, values)
        db.close()
        return id
    }

    // 获取登录密码
    fun getLoginPassword(): String? {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_LOGIN,
            arrayOf(KEY_LOGIN_PASSWORD),
            null, null, null, null, null
        )

        return if (cursor.moveToFirst()) {
            val password = cursor.getString(0)
            cursor.close()
            password
        } else {
            cursor.close()
            null
        }
    }

    // 添加密码项
    fun addPasswordItem(item: PasswordItem): Long {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(KEY_ACCOUNT, item.account)
        values.put(KEY_PASSWORD, item.password)
        values.put(KEY_HINT, item.hint)

        val id = db.insert(TABLE_PASSWORD, null, values)
        db.close()
        return id
    }

    // 获取单个密码项
    fun getPasswordItem(id: Int): PasswordItem? {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_PASSWORD,
            arrayOf(KEY_ID, KEY_ACCOUNT, KEY_PASSWORD, KEY_HINT),
            "$KEY_ID = ?",
            arrayOf(id.toString()),
            null, null, null
        )

        return if (cursor.moveToFirst()) {
            val item = PasswordItem(
                id = cursor.getInt(0),
                account = cursor.getString(1),
                password = cursor.getString(2),
                hint = cursor.getString(3)
            )
            cursor.close()
            item
        } else {
            cursor.close()
            null
        }
    }

    // 获取所有密码项
    fun getAllPasswordItems(): List<PasswordItem> {
        val itemList = mutableListOf<PasswordItem>()
        val selectQuery = "SELECT * FROM $TABLE_PASSWORD"

        val db = this.writableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val item = PasswordItem(
                    id = cursor.getInt(0),
                    account = cursor.getString(1),
                    password = cursor.getString(2),
                    hint = cursor.getString(3)
                )
                itemList.add(item)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return itemList
    }

    // 更新密码项
    fun updatePasswordItem(item: PasswordItem): Int {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(KEY_ACCOUNT, item.account)
        values.put(KEY_PASSWORD, item.password)
        values.put(KEY_HINT, item.hint)

        return db.update(
            TABLE_PASSWORD,
            values,
            "$KEY_ID = ?",
            arrayOf(item.id.toString())
        ).also {
            db.close()
        }
    }

    // 删除密码项
    fun deletePasswordItem(id: Int): Int {
        val db = this.writableDatabase
        val rowsAffected = db.delete(
            TABLE_PASSWORD,
            "$KEY_ID = ?",
            arrayOf(id.toString())
        )
        db.close()
        return rowsAffected
    }
}
    