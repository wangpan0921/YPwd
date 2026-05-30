package com.wp.ypwd

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wp.ypwd.R

class LoginActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        dbHelper = DatabaseHelper(this)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)

        // 检查是否已设置密码
        val hasPassword = dbHelper.getLoginPassword() != null
        btnLogin.text = if (hasPassword) "登录" else "设置密码"

        btnLogin.setOnClickListener {
            val password = etPassword.text.toString().trim()
            if (password.isEmpty()) {
                Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!hasPassword) {
                // 首次设置密码
                val encryptedPassword = EncryptionUtils.encrypt(password, password)
                dbHelper.saveLoginPassword(encryptedPassword)
                Toast.makeText(this, "密码设置成功", Toast.LENGTH_SHORT).show()
                startMainActivity(password)
            } else {
                // 登录验证
                val storedPassword = dbHelper.getLoginPassword()
                val decryptedPassword = storedPassword?.let {
                    EncryptionUtils.decrypt(it, password)
                }

                if (decryptedPassword == password) {
                    Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
                    startMainActivity(password)
                } else {
                    Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startMainActivity(masterPassword: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("master_password", masterPassword)
        startActivity(intent)
        finish()
    }
}
    