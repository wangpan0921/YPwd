package com.wp.ypwd

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_SENDTO
import android.content.Intent.EXTRA_EMAIL
import android.content.Intent.EXTRA_SUBJECT
import android.content.Intent.EXTRA_TEXT
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wp.ypwd.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

//private var LinearLayout.text: String

//private val recyclerview: Any = Any()
private lateinit var recyclerView: RecyclerView

// search
private lateinit var searchBox: EditText
private lateinit var btnClearSearch: ImageButton
//private lateinit var emptyView: TextView
private lateinit var emptyView: LinearLayout
private lateinit var searchResultHint: TextView
private lateinit var progressBar: ProgressBar
private lateinit var searchContainer: LinearLayout

// 搜索相关
private var currentSearchQuery = ""
private var searchJob: Job? = null
private val searchScope = CoroutineScope(Dispatchers.Main + SupervisorJob())


class MainActivity : AppCompatActivity() {
    companion object {
        private const val SEARCH_DELAY_MS = 300L
    }
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var passwordList: MutableList<PasswordItem>
//    private lateinit var filterPasswordList: List<PasswordItem>
    private var filterPasswordList: ArrayList<PasswordItem> = arrayListOf()
    private lateinit var adapter: PasswordAdapter
    private lateinit var masterPassword: String

    private lateinit var excelImporter: ExcelImporter
    private val REQUEST_PERMISSION = 1001
    private val REQUEST_ADD_EDIT = 1002

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化导入器
        excelImporter = ExcelImporter(this)

        // 获取主密码,主密码是进入密码列表界面前的app密码
        masterPassword = intent.getStringExtra("master_password") ?: ""

        dbHelper = DatabaseHelper(this)
        passwordList = dbHelper.getAllPasswordItems().toMutableList()


        // 设置 Toolbar 为 ActionBar
        val toolbar: MaterialToolbar = findViewById(R.id.top_app_bar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.recycler_view)
        searchBox = findViewById(R.id.search_box)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        emptyView = findViewById(R.id.empty_view)
        searchResultHint = findViewById(R.id.search_result_hint)
        searchContainer = findViewById(R.id.search_container)
        progressBar = findViewById(R.id.progress_bar)

        setupSearchFunctionality()
        // 触摸外部区域关闭键盘
        recyclerView.setOnTouchListener { _, _ ->
            hideKeyboard()
            searchBox.clearFocus()
            false
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PasswordAdapter(passwordList, masterPassword) { item ->
            // 点击编辑
            val intent = Intent(this, AddEditActivity::class.java)
            intent.putExtra("item", item)
            intent.putExtra("master_password", masterPassword)
            startActivityForResult(intent, REQUEST_ADD_EDIT)
        }
        recyclerView.adapter = adapter

        // 添加按钮
        val fab: FloatingActionButton = findViewById(R.id.fab)
        fab.setOnClickListener {
            val intent = Intent(this, AddEditActivity::class.java)
            intent.putExtra("master_password", masterPassword)
            startActivityForResult(intent, REQUEST_ADD_EDIT)
        }

        // 检查权限
        checkPermissions()
    }
    private fun passwordContainsQuery(password: PasswordItem, query: String): Boolean {
        val lowerQuery = query.lowercase(Locale.getDefault())
        return password.account.lowercase(Locale.getDefault()).contains(lowerQuery)
                || password.password.lowercase(Locale.getDefault()).contains(lowerQuery)
                || password.hint.lowercase(Locale.getDefault()).contains(lowerQuery)
    }

    private fun showSearchResultHint(query: String, resultCount: Int) {
        searchResultHint.text = if (resultCount > 0) {
            "找到 $resultCount 个与\"$query\"相关的结果"
        } else {
            "未找到与\"$query\"相关的结果"
        }
        searchResultHint.visibility = View.VISIBLE
        searchResultHint.setTextColor(
            if (resultCount > 0) Color.parseColor("#4CAF50") else Color.parseColor("#FF5722")
        )
    }
    private fun updateUI() {
//        adapter.submitList(filterPasswordList)

        if (filterPasswordList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
//            emptyView.text = if (searchBox.text.isNotEmpty()) {
//                "未找到相关密码记录"
//            } else {
//                "无密码记录"
//            }
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
        refreshList(filterPasswordList)
    }
    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            // 显示所有密码
//            filterPasswordList = passwordList as ArrayList<PasswordItem>

//            filterPasswordList = passwordList.toCollection(ArrayList())
            filterPasswordList = dbHelper.getAllPasswordItems() as ArrayList<PasswordItem>
            searchResultHint.visibility = View.GONE
        } else {
            // 执行搜索
            filterPasswordList = passwordList.filter { password ->
                // 支持多字段搜索
                passwordContainsQuery(password, query)
            } as MutableList<PasswordItem> as ArrayList<PasswordItem>

            // 显示搜索结果提示
            showSearchResultHint(query, filterPasswordList.size)
        }

        updateUI()
    }
    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            recyclerView.alpha = 0.5f
        } else {
            recyclerView.alpha = 1f
        }
    }
    private fun isAdvancedSearch(query: String): Boolean {
        return query.contains(":") || query.startsWith("#") || query.startsWith("@")
    }
    private data class SearchCriteria(
        val account: String? = null,
        val password: String? = null,
        val hint: String? = null,
        val keywords: List<String> = emptyList()
    )
    private fun parseSearchCriteria(query: String): SearchCriteria {
        val criteria = SearchCriteria()
        val parts = query.split(" ").filter { it.isNotBlank() }

        parts.forEach { part ->
            when {
                part.startsWith("account:") -> criteria.copy(account = part.substringAfter(":"))
                part.startsWith("password:") -> criteria.copy(password = part.substringAfter(":"))
                part.startsWith("hint:") -> criteria.copy(hint = part.substringAfter(":"))
                else -> criteria.keywords.plus(part)
            }
        }

        return criteria
    }
    private fun matchesSearchCriteria(item: PasswordItem, criteria: SearchCriteria): Boolean {
        return (criteria.account == null || item.account.contains(criteria.account, true)) &&
                (criteria.password == null || item.password.contains(criteria.password, true)) &&
                (criteria.hint == null || item.hint?.contains(criteria.hint, true) == true) &&
                (criteria.keywords.isEmpty() || criteria.keywords.all { keyword ->
                    item.containsKeyword(keyword)
                })
    }
    private fun performAdvancedSearchSync(query: String): List<PasswordItem> {
        val criteria = parseSearchCriteria(query)
        return passwordList.filter { item ->
            matchesSearchCriteria(item, criteria)
        }
    }

    private fun searchPasswordsSync(query: String): List<PasswordItem> {
        return if (isAdvancedSearch(query)) {
            performAdvancedSearchSync(query)
        } else {
            passwordList.filter { it.containsKeyword(query) }
        }
    }
    private fun updateEmptyState(query: String, isEmpty: Boolean) {
        if (isEmpty) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
//            emptyView.text = if (query.isNotEmpty()) {
//                "没有找到匹配 \"$query\" 的密码"
//            } else {
//                "还没有保存任何密码\n点击右下角 + 号添加"men
//            }
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }
    /*
    private val listLock = Any()
    private fun updateUIWithResults(results: List<PasswordItem>, query: String) {
        synchronized(listLock) {
            filterPasswordList.clear()
            filterPasswordList.addAll(results)
        }

        // 更新适配器
        adapter.submitList(results.toList())
        adapter.updateSearchQuery(query)

        // 显示搜索结果提示
        if (query.isNotEmpty()) {
            val hintText = if (results.isNotEmpty()) {
                "找到 ${results.size} 个与 \"$query\" 相关的结果"
            } else {
                "未找到与 \"$query\" 相关的结果"
            }
//            showSearchResultHint(hintText, results.isNotEmpty())
            showSearchResultHint(hintText, results.size)
        } else {
            searchResultHint.visibility = View.GONE
        }

        // 更新空状态
        updateEmptyState(query, results.isEmpty())
    }
    private fun performSearchImmediate(query: String) {
        showProgress(true)

        val results = if (query.isEmpty()) {
            passwordList
        } else {
            // 主线程搜索小数据集，大数据集应在后台线程
            searchPasswordsSync(query)
        }

        updateUIWithResults(results, query)
        showProgress(false)
    }
    */
    private fun clearSearch() {
        searchBox.text.clear()
        searchBox.clearFocus()
        hideKeyboard()
        searchResultHint.visibility = View.GONE
        performSearch("")
    }

    private fun animateSearchContainer() {
        searchContainer.animate()
            .translationZ(8f)
            .setDuration(200)
            .start()
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

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString().trim()
                updateClearButtonVisibility()

                // 取消之前的搜索任务
                searchJob?.cancel()

                // 延迟搜索，避免频繁搜索
                searchJob = searchScope.launch {
                    delay(SEARCH_DELAY_MS)
                    performSearch(currentSearchQuery)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        }
        // 搜索框文本变化监听（带防抖）
        searchBox.addTextChangedListener(textWatcher)

        // 清除搜索按钮
        btnClearSearch.setOnClickListener {
            clearSearch()
        }

        // 搜索框焦点变化
        searchBox.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                animateSearchContainer()
            }
        }

        // 键盘搜索按钮
        searchBox.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                performSearch(currentSearchQuery)
//                performSearchImmediate(currentSearchQuery)
                return@setOnEditorActionListener true
            }
            false
        }

//        // 触摸外部区域关闭键盘
//        recyclerView.setOnTouchListener { _, _ ->
//            hideKeyboard()
//            searchBox.clearFocus()
//            false
//        }
    }

    // 检查权限
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
                ActivityCompat.requestPermissions(
                    this,
                    needPermission.toTypedArray(),
                    REQUEST_PERMISSION
                )
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

    // 创建菜单
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    // 菜单点击事件
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_excel -> {
                exportExcel()
                true
            }
            R.id.action_send_email -> {
                sendEmailWithExcel()
                true
            }
            R.id.action_import_excel -> {
                checkAndRequestPermission()
                true
            }
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                showImportOptions()
//                openFilePicker(mimeType) // 已有权限
                return
            } else {
                // 引导用户去系统设置开启权限
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                Toast.makeText(this, "请在设置中允许管理所有文件权限", Toast.LENGTH_LONG).show()
                return
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                showImportOptions()
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
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
            .setNegativeButton("取消", null)
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
//            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(mimeType))
            putExtra(Intent.EXTRA_MIME_TYPES,
                arrayOf(
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

    // 文件选择结果回调
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                startImportProcess(uri)
            }
        }
    }

    // 存储权限请求
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
                uri.toString().endsWith(".xlsx", ignoreCase = true)) {
                excelImporter.importFromExcel(uri, masterPassword)
            } else {
//                csvImporter.importFromCsv(uri)
            }

            dialog.dismiss()
            showImportResult(importResult as ExcelImporter.ImportResult)
        }
    }

    private fun showImportResult(result: ExcelImporter.ImportResult) {
        val message = buildString {
            append("导入完成！\n\n")
            append("总行数: ${result.totalRows}\n")
            append("成功: ${result.successCount}\n")
            append("失败: ${result.failedCount}\n")

            if (result.error != null) {
                append("\n错误: ${result.error}")
            }

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
            .setPositiveButton("确定") { _, _ ->
                // 导入成功后，保存到数据库并刷新列表
                if (result.successItems.isNotEmpty()) {
                    saveToDatabase(result.successItems)
                }
            }
            .apply {
                if (result.failedRows.isNotEmpty()) {
                    setNeutralButton("查看失败详情") { _, _ ->
                        showFailedDetails(result.failedRows)
                    }
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
            .setPositiveButton("确定", null)
            .show()
    }

    private fun saveToDatabase(passwordItems: List<PasswordItem>) {
        lifecycleScope.launch(Dispatchers.IO) {
            // 这里替换为你的数据库插入逻辑
            passwordItems.forEach { passwordItem ->
                dbHelper.addPasswordItem(passwordItem)
            }

            withContext(Dispatchers.Main) {
                // 刷新UI
                Toast.makeText(
                    this@MainActivity,
                    "成功导入${passwordItems.size}条密码记录",
                    Toast.LENGTH_LONG
                ).show()

                // 刷新列表
                loadPasswords()
            }
        }
    }

    private fun loadPasswords() {
        refreshList(dbHelper.getAllPasswordItems())
    }

    // 导出Excel
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

    // 发送邮件
    private fun sendEmailWithExcel() {
        if (passwordList.isEmpty()) {
            Toast.makeText(this, "没有密码记录可发送", Toast.LENGTH_SHORT).show()
            return
        }

        val file = ExcelUtils.exportToExcel(this, passwordList, masterPassword)
        if (file != null) {
            try {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )

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
        } else {
            Toast.makeText(this, "生成Excel失败", Toast.LENGTH_SHORT).show()
        }
    }

    // 退出登录
    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗?")
            .setPositiveButton("确定") { _, _ ->
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 刷新列表
//    private fun refreshList() {
//        passwordList.clear()
//        passwordList.addAll(dbHelper.getAllPasswordItems())
//        adapter.notifyDataSetChanged()
//    }
    private fun refreshList(datas: List<PasswordItem>) {
        passwordList.clear()
        passwordList.addAll(datas)
        adapter.notifyDataSetChanged()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_EDIT && resultCode == RESULT_OK) {
            refreshList(dbHelper.getAllPasswordItems())
        }
    }

    // 密码列表适配器
    inner class PasswordAdapter(
        private val items: List<PasswordItem>,
        private val masterPassword: String,
        private val onItemClick: (PasswordItem) -> Unit
    ) : RecyclerView.Adapter<PasswordAdapter.ViewHolder>() {

        private var searchQuery: String = ""

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvAccount: TextView = itemView.findViewById(R.id.tv_account)
            val tvPassword: TextView = itemView.findViewById(R.id.tv_password)
            val tvHint: TextView = itemView.findViewById(R.id.tv_hint)
            val btnDelete: TextView = itemView.findViewById(R.id.btn_delete)
        }

//        private val differ = AsyncListDiffer(this, DiffCallback())
//
//        fun submitList(list: List<PasswordItem>) {
//            differ.submitList(list)
//            passwordList.clear()
//            passwordList.addAll(list)
//        }
//        inner class DiffCallback : DiffUtil.ItemCallback<PasswordItem>() {
//            override fun areItemsTheSame(oldItem: PasswordItem, newItem: PasswordItem): Boolean {
//                return oldItem.id == newItem.id
//            }
//
//            override fun areContentsTheSame(oldItem: PasswordItem, newItem: PasswordItem): Boolean {
//                return oldItem == newItem
//            }
//        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_password, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvAccount.text = item.account
            holder.tvPassword.text = EncryptionUtils.decrypt(item.password, masterPassword)
            holder.tvHint.text = item.hint

            holder.itemView.setOnClickListener {
                onItemClick(item)
            }

            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("删除")
                    .setMessage("确定要删除这条记录吗?")
                    .setPositiveButton("确定") { _, _ ->
                        dbHelper.deletePasswordItem(item.id)
                        refreshList(dbHelper.getAllPasswordItems())
                        Toast.makeText(this@MainActivity, "已删除", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        override fun getItemCount() = items.size

        fun updateSearchQuery(query: String) {
            searchQuery = query
            runOnUiThread {
                notifyDataSetChanged()
            }
        }
    }


}

    