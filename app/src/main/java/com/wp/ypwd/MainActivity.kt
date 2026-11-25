package com.wp.ypwd

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_EMAIL
import android.content.Intent.EXTRA_SUBJECT
import android.content.Intent.EXTRA_TEXT
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        private const val SEARCH_DELAY_MS = 300L
        private const val REQUEST_ADD_EDIT = 1002
        private const val REQUEST_PERMISSION = 1001
    }

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var passwordList: MutableList<PasswordItem>
    private var filterPasswordList: ArrayList<PasswordItem> = arrayListOf()
    private lateinit var adapter: PasswordAdapter
    private lateinit var masterPassword: String

    private lateinit var excelImporter: ExcelImporter

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchBox: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var emptyView: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptySubtitle: TextView
    private lateinit var searchResultHint: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchContainer: LinearLayout
    private lateinit var headerSubtitle: TextView

    // 搜索状态
    private var currentSearchQuery = ""
    private var searchJob: Job? = null
    private val searchScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        excelImporter = ExcelImporter(this)
        masterPassword = intent.getStringExtra("master_password") ?: ""

        dbHelper = DatabaseHelper(this)
        passwordList = dbHelper.getAllPasswordItems().toMutableList()
        filterPasswordList = ArrayList(passwordList)

        // 设置 Toolbar 为 ActionBar（title 由 header 大字体展示，这里保持 title 为空）
        val toolbar: MaterialToolbar = findViewById(R.id.top_app_bar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        recyclerView = findViewById(R.id.recycler_view)
        searchBox = findViewById(R.id.search_box)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        emptyView = findViewById(R.id.empty_view)
        emptyTitle = findViewById(R.id.empty_title)
        emptySubtitle = findViewById(R.id.empty_subtitle)
        searchResultHint = findViewById(R.id.search_result_hint)
        searchContainer = findViewById(R.id.search_container)
        progressBar = findViewById(R.id.progress_bar)
        headerSubtitle = findViewById(R.id.tv_header_subtitle)

        setupSearchFunctionality()

        recyclerView.setOnTouchListener { _, _ ->
            hideKeyboard()
            searchBox.clearFocus()
            false
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PasswordAdapter(filterPasswordList, masterPassword) { item ->
            val intent = Intent(this, AddEditActivity::class.java)
            intent.putExtra("item", item)
            intent.putExtra("master_password", masterPassword)
            startActivityForResult(intent, REQUEST_ADD_EDIT)
        }
        recyclerView.adapter = adapter

        val fab: ExtendedFloatingActionButton = findViewById(R.id.fab)
        fab.setOnClickListener {
            val intent = Intent(this, AddEditActivity::class.java)
            intent.putExtra("master_password", masterPassword)
            startActivityForResult(intent, REQUEST_ADD_EDIT)
        }

        refreshAll()
        checkPermissions()
    }

    // === 搜索逻辑 ===

    private fun passwordContainsQuery(password: PasswordItem, query: String): Boolean {
        val lowerQuery = query.lowercase(Locale.getDefault())
        return password.account.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                password.password.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                password.hint.lowercase(Locale.getDefault()).contains(lowerQuery)
    }

    private fun showSearchResultHint(query: String, resultCount: Int) {
        if (query.isEmpty()) {
            searchResultHint.visibility = View.GONE
            return
        }
        searchResultHint.text = if (resultCount > 0) {
            "找到 $resultCount 个与\"$query\"相关的结果"
        } else {
            "未找到与\"$query\"相关的结果"
        }
        searchResultHint.visibility = View.VISIBLE
        searchResultHint.setTextColor(
            if (resultCount > 0)
                ContextCompat.getColor(this, R.color.success)
            else
                ContextCompat.getColor(this, R.color.danger)
        )
    }

    private fun updateUI() {
        if (filterPasswordList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            if (currentSearchQuery.isNotEmpty()) {
                emptyTitle.setText(R.string.search_empty_title)
                emptySubtitle.setText(R.string.search_empty_subtitle)
            } else {
                emptyTitle.setText(R.string.empty_title)
                emptySubtitle.setText(R.string.empty_subtitle)
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
        adapter.notifyDataSetChanged()
    }

    private fun performSearch(query: String) {
        passwordList = dbHelper.getAllPasswordItems().toMutableList()
        filterPasswordList.clear()
        if (query.isEmpty()) {
            filterPasswordList.addAll(passwordList)
            searchResultHint.visibility = View.GONE
        } else {
            filterPasswordList.addAll(passwordList.filter { passwordContainsQuery(it, query) })
            showSearchResultHint(query, filterPasswordList.size)
        }
        updateHeaderSubtitle()
        updateUI()
    }

    private fun refreshAll() {
        performSearch(currentSearchQuery)
    }

    private fun updateHeaderSubtitle() {
        val total = passwordList.size
        headerSubtitle.text = if (total == 0) {
            getString(R.string.app_tagline)
        } else {
            "共 $total 条加密记录"
        }
    }

    private fun clearSearch() {
        searchBox.text?.clear()
        searchBox.clearFocus()
        hideKeyboard()
        searchResultHint.visibility = View.GONE
        performSearch("")
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
    }

    private fun updateClearButtonVisibility() {
        btnClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupSearchFunctionality() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim().orEmpty()
                updateClearButtonVisibility()
                searchJob?.cancel()
                searchJob = searchScope.launch {
                    delay(SEARCH_DELAY_MS)
                    performSearch(currentSearchQuery)
                }
            }
        }
        searchBox.addTextChangedListener(textWatcher)
        btnClearSearch.setOnClickListener { clearSearch() }
        searchBox.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                performSearch(currentSearchQuery)
                true
            } else {
                false
            }
        }
    }

    // === 权限 ===

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            val needPermission = permissions.filter {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needPermission.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needPermission.toTypedArray(), REQUEST_PERMISSION)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(this, "需要存储权限才能导出Excel文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // === 菜单 ===

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_excel -> { exportExcel(); true }
            R.id.action_send_email -> { sendEmailWithExcel(); true }
            R.id.action_import_excel -> { checkAndRequestPermission(); true }
            R.id.action_logout -> { logout(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // === Excel 导入 ===

    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                showImportOptions()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                Toast.makeText(this, "请在设置中允许管理所有文件权限", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            showImportOptions()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun showImportOptions() {
        val options = arrayOf(
            "从Excel文件导入 (.xls, .xlsx)",
            "查看导入帮助"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("导入密码")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFilePicker("application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    1 -> showImportHelp()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showImportHelp() {
        val helpText = """
            📋 导入文件格式要求：
            
            1. 支持的格式：
               • Excel文件 (.xls, .xlsx)
            
            2. 文件结构：
               第一行必须是表头，包含4列：
               序号 | 账户 | 密码 | 提示
            
            3. 注意事项：
               • "序号"列从1开始
               • "账户"是某平台账户名
               • "密码"可以是能够提醒自己回忆密码的文字
               • "提示"是平台名称
               • 文件编码建议使用UTF-8
            
            4. 安全提示：
               • 导入前请确保文件来源可靠
               • 导入后建议删除本地临时文件
               • 敏感文件不要长期保存在设备上
        """.trimIndent()
        MaterialAlertDialogBuilder(this)
            .setTitle("导入帮助")
            .setMessage(helpText)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun openFilePicker(mimeType: String) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/csv",
                "text/comma-separated-values"
            ))
        }
        if (intent.resolveActivity(packageManager) != null) {
            filePickerLauncher.launch(intent)
        } else {
            Toast.makeText(this, "未找到可以处理此请求的应用", Toast.LENGTH_SHORT).show()
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> startImportProcess(uri) }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImportOptions()
        } else {
            Toast.makeText(this, "需要存储权限来导入文件", Toast.LENGTH_LONG).show()
        }
    }

    private fun startImportProcess(uri: Uri) {
        val mimeType = contentResolver.getType(uri)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("正在导入")
            .setMessage("正在解析文件，请稍候...")
            .setCancelable(false)
            .create()
        dialog.show()

        lifecycleScope.launch {
            val importResult = if (mimeType?.contains("excel") == true ||
                uri.toString().endsWith(".xls", ignoreCase = true) ||
                uri.toString().endsWith(".xlsx", ignoreCase = true)
            ) {
                excelImporter.importFromExcel(uri, masterPassword)
            } else {
                null
            }
            dialog.dismiss()
            importResult?.let { showImportResult(it) }
        }
    }

    private fun showImportResult(result: ExcelImporter.ImportResult) {
        val message = buildString {
            append("导入完成！\n\n")
            append("总行数: ${result.totalRows}\n")
            append("成功: ${result.successCount}\n")
            append("失败: ${result.failedCount}\n")
            if (result.error != null) append("\n错误: ${result.error}")
            if (result.failedRows.isNotEmpty()) {
                append("\n\n失败的行：")
                result.failedRows.take(5).forEach { failedRow ->
                    append("\n第${failedRow.rowNumber}行: ${failedRow.error}")
                }
                if (result.failedRows.size > 5) {
                    append("\n... 还有${result.failedRows.size - 5}个失败项")
                }
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("导入结果")
            .setMessage(message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                if (result.successItems.isNotEmpty()) {
                    saveToDatabase(result.successItems)
                }
            }
            .apply {
                if (result.failedRows.isNotEmpty()) {
                    setNeutralButton("查看失败详情") { _, _ -> showFailedDetails(result.failedRows) }
                }
            }
            .show()
    }

    private fun showFailedDetails(failedRows: List<ExcelImporter.FailedRow>) {
        val details = failedRows.joinToString("\n") { row ->
            "第${row.rowNumber}行 - 账户: '${row.account}' - 错误: ${row.error}"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("失败详情")
            .setMessage(details)
            .setPositiveButton(R.string.confirm, null)
            .show()
    }

    private fun saveToDatabase(passwordItems: List<PasswordItem>) {
        lifecycleScope.launch(Dispatchers.IO) {
            passwordItems.forEach { dbHelper.addPasswordItem(it) }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "成功导入${passwordItems.size}条密码记录",
                    Toast.LENGTH_LONG
                ).show()
                refreshAll()
            }
        }
    }

    // === 导出与邮件 ===

    private fun exportExcel() {
        if (passwordList.isEmpty()) {
            Toast.makeText(this, "没有密码记录可导出", Toast.LENGTH_SHORT).show()
            return
        }
        val file = ExcelUtils.exportToExcel(this, passwordList, masterPassword)
        if (file != null) {
            Toast.makeText(this, "Excel已导出至: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendEmailWithExcel() {
        if (passwordList.isEmpty()) {
            Toast.makeText(this, "没有密码记录可发送", Toast.LENGTH_SHORT).show()
            return
        }
        val file = ExcelUtils.exportToExcel(this, passwordList, masterPassword) ?: run {
            Toast.makeText(this, "生成Excel失败", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(ACTION_SEND).apply {
                type = "application/vnd.ms-excel"
                putExtra(EXTRA_EMAIL, arrayOf(""))
                putExtra(EXTRA_SUBJECT, "密码记录备份")
                putExtra(EXTRA_TEXT, "这是密码记录的备份文件")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "通过邮件发送"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "未找到邮件应用", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show()
        }
    }

    // === 退出 ===

    private fun logout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗?")
            .setPositiveButton(R.string.confirm) { _, _ ->
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_EDIT && resultCode == RESULT_OK) {
            refreshAll()
        }
    }

    // === 列表 Adapter ===

    inner class PasswordAdapter(
        private val items: List<PasswordItem>,
        private val masterPassword: String,
        private val onItemClick: (PasswordItem) -> Unit
    ) : RecyclerView.Adapter<PasswordAdapter.ViewHolder>() {

        // 记录每条项的"是否显示明文密码"
        private val revealed = mutableSetOf<Int>()

        private val avatarColors = intArrayOf(
            R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4,
            R.color.avatar_5, R.color.avatar_6, R.color.avatar_7, R.color.avatar_8
        )

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val avatarBox: FrameLayout = itemView.findViewById(R.id.avatar_box)
            val tvAvatar: TextView = itemView.findViewById(R.id.tv_avatar)
            val tvAccount: TextView = itemView.findViewById(R.id.tv_account)
            val tvPassword: TextView = itemView.findViewById(R.id.tv_password)
            val tvHint: TextView = itemView.findViewById(R.id.tv_hint)
            val btnToggle: ImageButton = itemView.findViewById(R.id.btn_toggle_password)
            val btnCopy: ImageButton = itemView.findViewById(R.id.btn_copy_password)
            val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_password, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val decrypted = EncryptionUtils.decrypt(item.password, masterPassword) ?: ""

            holder.tvAccount.text = item.account
            if (item.hint.isNullOrBlank()) {
                holder.tvHint.visibility = View.GONE
            } else {
                holder.tvHint.visibility = View.VISIBLE
                holder.tvHint.text = item.hint
            }

            // 首字母头像
            val initial = extractInitial(item.account)
            holder.tvAvatar.text = initial
            val colorRes = avatarColors[Math.abs(item.account.hashCode()) % avatarColors.size]
            holder.avatarBox.background?.setTint(ContextCompat.getColor(this@MainActivity, colorRes))

            // 密码可见性
            val isRevealed = revealed.contains(item.id)
            if (isRevealed) {
                holder.tvPassword.text = decrypted.ifEmpty { getString(R.string.password_mask) }
                holder.btnToggle.setImageResource(R.drawable.ic_eye_off)
                holder.btnToggle.contentDescription = getString(R.string.action_hide)
            } else {
                holder.tvPassword.text = getString(R.string.password_mask)
                holder.btnToggle.setImageResource(R.drawable.ic_eye)
                holder.btnToggle.contentDescription = getString(R.string.action_reveal)
            }

            holder.btnToggle.setOnClickListener {
                if (revealed.contains(item.id)) revealed.remove(item.id) else revealed.add(item.id)
                notifyItemChanged(holder.bindingAdapterPosition)
            }

            holder.btnCopy.setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ypwd_password", decrypted))
                Toast.makeText(this@MainActivity, R.string.toast_copied, Toast.LENGTH_SHORT).show()
            }

            holder.itemView.setOnClickListener { onItemClick(item) }

            holder.btnDelete.setOnClickListener {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.delete_title)
                    .setMessage(R.string.delete_message)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        dbHelper.deletePasswordItem(item.id)
                        revealed.remove(item.id)
                        refreshAll()
                        Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }

        override fun getItemCount() = items.size

        private fun extractInitial(text: String?): String {
            if (text.isNullOrBlank()) return "?"
            val trimmed = text.trim()
            val first = trimmed[0]
            return if (first.code <= 127) first.uppercaseChar().toString() else first.toString()
        }
    }
}
