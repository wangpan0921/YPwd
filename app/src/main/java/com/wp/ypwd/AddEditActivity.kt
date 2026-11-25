package com.wp.ypwd

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AddEditActivity : AppCompatActivity() {
    private lateinit var etAccount: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etHint: TextInputEditText
    private lateinit var tilAccount: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var btnSave: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvSubtitle: TextView
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var masterPassword: String
    private var currentItem: PasswordItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit)

        masterPassword = intent.getStringExtra("master_password") ?: ""
        @Suppress("DEPRECATION")
        currentItem = intent.getSerializableExtra("item") as? PasswordItem

        dbHelper = DatabaseHelper(this)

        toolbar = findViewById(R.id.top_app_bar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        etAccount = findViewById(R.id.et_account)
        etPassword = findViewById(R.id.et_password)
        etHint = findViewById(R.id.et_hint)
        tilAccount = findViewById(R.id.til_account)
        tilPassword = findViewById(R.id.til_password)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
        tvSubtitle = findViewById(R.id.tv_subtitle)

        currentItem?.let { item ->
            toolbar.title = getString(R.string.title_edit)
            tvSubtitle.setText(R.string.subtitle_edit)
            etAccount.setText(item.account)
            etPassword.setText(EncryptionUtils.decrypt(item.password, masterPassword))
            etHint.setText(item.hint)
        } ?: run {
            toolbar.title = getString(R.string.title_add)
            tvSubtitle.setText(R.string.subtitle_add)
        }

        btnSave.setOnClickListener { savePassword() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun savePassword() {
        val account = etAccount.text?.toString()?.trim().orEmpty()
        val password = etPassword.text?.toString()?.trim().orEmpty()
        val hint = etHint.text?.toString()?.trim().orEmpty()

        var hasError = false
        if (account.isEmpty()) {
            tilAccount.error = getString(R.string.hint_account)
            hasError = true
        } else {
            tilAccount.error = null
        }
        if (password.isEmpty()) {
            tilPassword.error = getString(R.string.hint_password)
            hasError = true
        } else {
            tilPassword.error = null
        }
        if (hasError) {
            Toast.makeText(this, R.string.validate_empty, Toast.LENGTH_SHORT).show()
            return
        }

        // 加密密码
        val encryptedPassword = EncryptionUtils.encrypt(password, masterPassword)

        val item = currentItem ?: PasswordItem(0, account, encryptedPassword, hint)

        if (currentItem == null) {
            val id = dbHelper.addPasswordItem(item)
            if (id != -1L) {
                Toast.makeText(this, "添加成功", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "添加失败", Toast.LENGTH_SHORT).show()
            }
        } else {
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
