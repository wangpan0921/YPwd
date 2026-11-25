package com.wp.ypwd

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tvWelcomeTitle: TextView
    private lateinit var tvWelcomeSubtitle: TextView
    private var hasPassword: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        dbHelper = DatabaseHelper(this)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        tilPassword = findViewById(R.id.til_password)
        tvWelcomeTitle = findViewById(R.id.tv_welcome_title)
        tvWelcomeSubtitle = findViewById(R.id.tv_welcome_subtitle)

        // 根据是否已设置主密码展示不同的文案
        hasPassword = dbHelper.getLoginPassword() != null
        if (hasPassword) {
            tvWelcomeTitle.setText(R.string.welcome_back)
            tvWelcomeSubtitle.setText(R.string.input_pwd)
            btnLogin.setText(R.string.unlock)
        } else {
            tvWelcomeTitle.setText(R.string.welcome_new)
            tvWelcomeSubtitle.setText(R.string.set_master_password)
            btnLogin.setText(R.string.create)
        }

        btnLogin.setOnClickListener { attemptLogin() }

        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else {
                false
            }
        }
    }

    private fun attemptLogin() {
        val password = etPassword.text?.toString()?.trim().orEmpty()
        if (password.isEmpty()) {
            tilPassword.error = getString(R.string.input_pwd)
            return
        }
        tilPassword.error = null

        if (!hasPassword) {
            // 首次设置密码
            val encryptedPassword = EncryptionUtils.encrypt(password, password)
            dbHelper.saveLoginPassword(encryptedPassword)
            Toast.makeText(this, "密码设置成功", Toast.LENGTH_SHORT).show()
            startMainActivity(password)
        } else {
            val storedPassword = dbHelper.getLoginPassword()
            val decryptedPassword = storedPassword?.let {
                EncryptionUtils.decrypt(it, password)
            }

            if (decryptedPassword == password) {
                startMainActivity(password)
            } else {
                tilPassword.error = "密码错误"
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
