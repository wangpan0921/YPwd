package com.wp.ypwd

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wp.ypwd.R

class AddEditActivity : AppCompatActivity() {
    private lateinit var etAccount: EditText
    private lateinit var etPassword: EditText
    private lateinit var etHint: EditText
    private lateinit var btnSave: Button
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var masterPassword: String
    private var currentItem: PasswordItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit)

        masterPassword = intent.getStringExtra("master_password") ?: ""
        currentItem = intent.getParcelableExtra("item")

        dbHelper = DatabaseHelper(this)

        etAccount = findViewById(R.id.et_account)
        etPassword = findViewById(R.id.et_password)
        etHint = findViewById(R.id.et_hint)
        btnSave = findViewById(R.id.btn_save)

        // 如果是编辑模式，填充数据
        currentItem?.let { item ->
            title = "编辑密码"
            etAccount.setText(item.account)
            etPassword.setText(EncryptionUtils.decrypt(item.password, masterPassword))
            etHint.setText(item.hint)
        } ?: run {
            title = "添加密码"
        }

        btnSave.setOnClickListener {
            savePassword()
        }
    }

    private fun savePassword() {
        val account = etAccount.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val hint = etHint.text.toString().trim()

        if (account.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "账户和密码不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        // 加密密码
        val encryptedPassword = EncryptionUtils.encrypt(password, masterPassword)

        val item = currentItem ?: PasswordItem(0, account, encryptedPassword, hint)

        if (currentItem == null) {
            // 添加新记录
            val id = dbHelper.addPasswordItem(item)
            if (id != -1L) {
                Toast.makeText(this, "添加成功", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "添加失败", Toast.LENGTH_SHORT).show()
            }
        } else {
            // 更新记录
            item.account = account
            item.password = encryptedPassword
            item.hint = hint
            val rowsAffected = dbHelper.updatePasswordItem(item)
            if (rowsAffected > 0) {
                Toast.makeText(this, "更新成功", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "更新失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
    