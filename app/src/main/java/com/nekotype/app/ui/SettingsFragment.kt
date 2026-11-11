package com.nekotype.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.nekotype.app.ai.AiHistory
import com.nekotype.app.ai.AiPersonas
import com.nekotype.app.ai.AiProfiles
import com.nekotype.app.ai.AiRewriter
import com.nekotype.app.R
import com.nekotype.app.accessibility.NekoTypeAccessibilityService
import com.nekotype.app.databinding.FragmentSettingsBinding
import com.nekotype.app.prefs.AppPrefs
import com.nekotype.app.overlay.FloatingButtonService
import com.nekotype.app.sys.SysPower
import com.nekotype.app.util.BgUtils
import com.nekotype.app.util.NekoLang
import com.nekotype.app.util.NekoLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.nekotype.app.util.setTextSizeDimen

/**
 * 设置页 Fragment：外观 / 悬浮按钮 / 高级 / 数据 / 黑名单 / 详细信息 / 关于 / 反馈。
 * 从 SettingsActivity 迁移而来，保留全部功能。
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val bgPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val out = File(requireContext().filesDir, "custom_bg.jpg")
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                AppPrefs.customBackgroundPath = out.absolutePath
                BgUtils.apply(binding.root)
                toast(getString(R.string.u1))
            } catch (_: Throwable) {
                toast(getString(R.string.u38))
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // inflate 兜底：布局加载失败返回空视图，绝不闪退（防止特定主题/ROM 组合崩溃）
        _binding = try {
            FragmentSettingsBinding.inflate(inflater, container, false)
        } catch (e: Throwable) {
            NekoLog.error("设置页布局加载失败：${e.javaClass.simpleName}")
            return android.widget.FrameLayout(inflater.context).apply {
                addView(android.widget.TextView(context).apply {
                    text = getString(R.string.hc_load_fail_settings)
                    gravity = android.view.Gravity.CENTER
                })
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (_binding == null) return
        // 每个模块独立 try-catch：一个模块初始化失败不影响其他按钮绑定
        try { BgUtils.apply(binding.root) } catch (_: Throwable) { }
        NekoLog.nav("进入设置页（Fragment）")

        // 自定义背景
        try {
            binding.btnPickBg.setOnClickListener {
                try { bgPicker.launch("image/*") } catch (_: Throwable) { toast(getString(R.string.u33)) }
            }
            binding.btnClearBg.setOnClickListener {
                AppPrefs.customBackgroundPath = ""
                BgUtils.notifyChanged()
                BgUtils.apply(binding.root)
                toast(getString(R.string.u41))
            }
        } catch (_: Throwable) { }

        // 主题（2x2 网格 + 猫娘，手动单选）
        try {
            val themeButtons = mapOf(
                R.id.btnThemeSystem to "system",
                R.id.btnThemeDark to "dark",
                R.id.btnThemeLight to "light",
                R.id.btnThemeStar to "star",
                R.id.btnThemeNeko to "neko",
                R.id.btnThemeCccp to "cccp",
            )
            themeButtons.forEach { (id, mode) ->
                view.findViewById<com.google.android.material.button.MaterialButton>(id)?.setOnClickListener {
                    switchTheme(mode)
                }
            }
        } catch (_: Throwable) { }

        // 人设语气包
        try { setupPersonaButtons(view) } catch (_: Throwable) { }

        // 情绪小猫概率
        try { setupMoodProbability(view) } catch (_: Throwable) { }

        // 悬浮按钮（Slider 值强制 clamp 到合法范围，防止 IllegalArgumentException 闪退）
        // 注意：slFabOpacity 的 valueFrom=10，不是 0！
        try {
            binding.slFabSize.value = AppPrefs.fabSizeDp.coerceIn(40, 96).toFloat()
            binding.slFabOpacity.value = AppPrefs.fabOpacity.coerceIn(10, 100).toFloat()
            binding.tvFabSize.text = "${AppPrefs.fabSizeDp}dp"
            binding.tvFabOpacity.text = "${AppPrefs.fabOpacity}%"
            binding.slFabSize.addOnChangeListener { _, value, _ ->
                val v = value.toInt()
                AppPrefs.fabSizeDp = v
                binding.tvFabSize.text = "${v}dp"
                FloatingButtonService.applyStyle(v, AppPrefs.fabOpacity)
            }
            binding.slFabOpacity.addOnChangeListener { _, value, _ ->
                val v = value.toInt()
                AppPrefs.fabOpacity = v
                binding.tvFabOpacity.text = "${v}%"
                FloatingButtonService.applyStyle(AppPrefs.fabSizeDp, v)
            }
            binding.swFabCollapse.isChecked = AppPrefs.fabCollapseEnabled
            binding.swFabCollapse.setOnCheckedChangeListener { _, checked ->
                AppPrefs.fabCollapseEnabled = checked
                FloatingButtonService.reload()
            }
            binding.swFabGlass.isChecked = AppPrefs.fabGlassEnabled
            binding.swFabGlass.setOnCheckedChangeListener { _, checked ->
                AppPrefs.fabGlassEnabled = checked
                FloatingButtonService.reload()
            }
            binding.swFabLongMenu.isChecked = AppPrefs.fabLongPressMenu
            binding.swFabLongMenu.setOnCheckedChangeListener { _, checked ->
                AppPrefs.fabLongPressMenu = checked
                // 关闭时收起已打开的菜单，避免遗留
                if (!checked) FloatingButtonService.dismissMenu()
            }
        } catch (_: Throwable) { }

        // 开机自启
        try {
            binding.swLogEnabled.isChecked = AppPrefs.logEnabled
        binding.swLogEnabled.setOnCheckedChangeListener { _, checked ->
            AppPrefs.logEnabled = checked
            NekoLog.adjust(if (checked) "开启运行日志" else "关闭运行日志")
        }
        binding.rowLog.setOnClickListener {
            startActivity(Intent(requireContext(), LogActivity::class.java))
        }
        binding.swAutoStart.isChecked = AppPrefs.autoStartEnabled
            binding.swAutoStart.setOnCheckedChangeListener { _, checked ->
                if (!checked) {
                    if (AppPrefs.lockEnabled) {
                        toast(getString(R.string.u20))
                        binding.swAutoStart.isChecked = true
                        return@setOnCheckedChangeListener
                    }
                    AppPrefs.autoStartEnabled = false
                    return@setOnCheckedChangeListener
                }
                // 每次开启都提示：开机自启依赖 Shizuku 以 root 运行
                NekoDialog.builder(requireContext())
                    .setTitle(getString(R.string.st_autostart_title))
                    .setMessage(getString(R.string.st_autostart_msg))
                    .setPositiveButton(getString(R.string.st_autostart_ok)) { _, _ ->
                        AppPrefs.autoStartEnabled = true
                    }
                    .setNegativeButton(getString(R.string.st_autostart_later)) { _, _ ->
                        binding.swAutoStart.isChecked = false
                    }
                    .setOnCancelListener { binding.swAutoStart.isChecked = false }
                    .show()
            }
        } catch (_: Throwable) { }

        // 打开应用自动启动服务 / 打开应用自动 Shizuku 授权
        try {
            binding.swAutoShizukuGrant.isChecked = AppPrefs.autoShizukuGrantOnLaunch
            binding.swAutoShizukuGrant.setOnCheckedChangeListener { _, checked ->
                AppPrefs.autoShizukuGrantOnLaunch = checked
                NekoLog.adjust(if (checked) "开启：打开应用自动 Shizuku 授权所有权限" else "关闭：打开应用自动 Shizuku 授权")
                if (checked) {
                    when {
                        !SysPower.isShizukuAvailable() -> toast(getString(R.string.u184))
                        !SysPower.isShizukuPermissionGranted() -> toast(getString(R.string.hc_shizuku_requesting))
                        else -> toast(getString(R.string.st_auto_shizuku_hint))
                    }
                }
            }
        } catch (_: Throwable) { }

        // 数据导入导出
        try {
            binding.btnExportConfig.setOnClickListener { exportConfig() }
            binding.btnImportConfig.setOnClickListener { showImportDialog() }
        } catch (_: Throwable) { }

        // 模块（ZIP）：管理 / 导入 / 导出
        try {
            binding.rowModule.setOnClickListener {
                startActivity(Intent(requireContext(), ModuleActivity::class.java))
            }
            binding.btnEnterModule.setOnClickListener {
                startActivity(Intent(requireContext(), ModuleActivity::class.java))
            }
        } catch (_: Throwable) { }

        // 模块钩子：隐藏被模块声明的内置入口 + 注入模块卡片
        try {
            com.nekotype.app.module.ModuleUi.applyHidden(binding.btnOpenTerminal, com.nekotype.app.module.FeatureKeys.TERMINAL)
            com.nekotype.app.module.ModuleUi.applyHidden(binding.cardSponsor, com.nekotype.app.module.FeatureKeys.SPONSOR)
            com.nekotype.app.module.ModuleUi.applyHidden(binding.cardImportExport, com.nekotype.app.module.FeatureKeys.IMPORT_EXPORT)
            com.nekotype.app.module.ModuleUi.applyHidden(binding.rowModule, com.nekotype.app.module.FeatureKeys.MODULES)
            com.nekotype.app.module.ModuleUi.injectSettingsCards(requireContext(), binding.settingsRoot)
        } catch (_: Throwable) { }

        // AI 改写（可选联网）：程序化卡片，初始化失败不影响其他设置项
        try { setupAiCard(view) } catch (_: Throwable) { }

        // 黑名单
        try {
            binding.btnBlacklist.setOnClickListener {
                startActivity(Intent(requireContext(), BlacklistActivity::class.java))
            }
        } catch (_: Throwable) { }

        // 开发者模式：关于标题连点 7 次触发（带getString(R.string.hc_download_extra_title)确认）
        try {
            wireDevModeTrigger()
        } catch (_: Throwable) { }

        // 支持与反馈
        try {
            binding.ivSponsorQr.setOnClickListener { showSponsorDialog() }
            binding.btnFeedback.setOnClickListener { sendFeedbackEmail() }
            binding.btnOpenTerminal.setOnClickListener {
                startActivity(Intent(requireContext(), TerminalActivity::class.java))
            }
            binding.btnCopyGroup.setOnClickListener {
                copyToClipboard("1007865515")
                tryOpenQqChannel()
            }
            binding.ivSponsorQrAlipay.setOnClickListener { showSponsorDialog(R.drawable.sponsor_qr_alipay) }
            binding.btnAfdian.setOnClickListener { tryOpenAfdian() }
        } catch (_: Throwable) { }

        // 刷新显示
        try {
            refreshTheme()
            refreshVersion()
            refreshStatsDaily()
        } catch (_: Throwable) { }
        // 隐私卡下方追加三份法律文档入口（与免责声明弹窗共用同一份文本，单一来源）
        try {
            appendLegalEntries()
        } catch (_: Throwable) { }
    }

    /** 在设置页隐私卡内追加：免责声明 / 隐私政策 / 第三方信息共享清单 / 开源许可 */
    private fun appendLegalEntries() {
        val box = _binding?.llLegal ?: return
        // 已追加过就不重复（onViewCreated 只跑一次，这里再兜一层）
        if (box.childCount > 2) return
        fun divider() = android.view.View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1
            )
            setBackgroundColor(0x1F888888)
        }
        box.addView(divider())
        // 免责声明：同意过一次后首次弹窗不再出现，这里随时可查阅；长按可重置，下次启动重新弹出
        val rowDisclaimer = NekoDialog.row(requireContext(), getString(R.string.i46)) {
            NekoDialog.showDoc(requireContext(), getString(R.string.i46), getString(R.string.i47))
        }
        rowDisclaimer.setOnLongClickListener {
            AppPrefs.disclaimerAccepted = false
            toast(getString(R.string.i60))
            true
        }
        box.addView(rowDisclaimer)
        box.addView(divider())
        box.addView(NekoDialog.row(requireContext(), getString(R.string.i55)) {
            NekoDialog.showDoc(requireContext(), getString(R.string.i55), LegalDocs.privacy())
        })
        box.addView(android.widget.TextView(requireContext()).apply {
            text = getString(R.string.privacy_hint)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.fg_2))
            setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
        })
        box.addView(divider())
        box.addView(NekoDialog.row(requireContext(), getString(R.string.i56)) {
            NekoDialog.showDoc(requireContext(), getString(R.string.i56), LegalDocs.thirdParty())
        })
        box.addView(divider())
        box.addView(NekoDialog.row(requireContext(), getString(R.string.i57)) {
            NekoDialog.showDoc(requireContext(), getString(R.string.i57), LegalDocs.licenses())
        })
    }

    // ---------- 开发者模式 ----------

    /** 关于标题连点 7 次进入开发者模式；已开启则再点 7 次直接打开开发者页。
     *  手感：2.5 秒内连点都计数，第 3 下开始有 toast 反馈，标题带水波纹按压效果。 */
    private fun wireDevModeTrigger() {
        var taps = 0
        var lastAt = 0L
        val onClick = {
            val now = System.currentTimeMillis()
            taps = if (now - lastAt < 2500) taps + 1 else 1
            lastAt = now
            when {
                taps >= 7 -> {
                    taps = 0
                    if (AppPrefs.devModeEnabled) {
                        startActivity(Intent(requireContext(), DevActivity::class.java))
                        NekoLog.nav("开发者模式：进入开发者页")
                    } else {
                        showDevDownloadDialog()
                    }
                }
                taps >= 3 -> toast(getString(R.string.hc_dev_mode_taps, 7 - taps))
            }
        }
        binding.tvAboutTitle.setOnClickListener { onClick() }
        binding.cardAbout.setOnClickListener { onClick() }
    }

    /** 「下载额外组件」确认弹窗：确认后模拟下载进度，完成即开启开发者模式 */
    private fun showDevDownloadDialog() {
        val dialog = NekoDialog.builder(requireContext())
            .setTitle(getString(R.string.hc_dev_mode_title))
            .setMessage(getString(R.string.hc_dev_need_components))
            .setPositiveButton(getString(R.string.hc_download)) { d, _ ->
                d.dismiss()
                showDevDownloadProgress()
            }
            .setNegativeButton(getString(R.string.u72), null)
            .create()
        dialog.show()
    }

    /** 模拟下载进度条（约 2.5 秒到 100%），完成后开启开发者模式 */
    private fun showDevDownloadProgress() {
        val progress = android.widget.ProgressBar(
            requireContext(), null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
        }
        val tvState = android.widget.TextView(requireContext()).apply {
            text = getString(R.string.hc_downloading, 0)
            setTextSizeDimen(R.dimen.ts_13)
        }
        val dialog = NekoDialog.builder(requireContext())
            .setTitle(getString(R.string.hc_download_extra_title))
            .setView(NekoDialog.column(requireContext(), tvState, progress))
            .setCancelable(false)
            .create()
        dialog.show()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                step++
                progress.progress = step * 2
                tvState.text = getString(R.string.hc_downloading, step * 2)
                if (step < 50) {
                    handler.postDelayed(this, 50)
                } else {
                    AppPrefs.devModeEnabled = true
                    dialog.dismiss()
                    NekoLog.adjust("开发者模式已开启")
                    toast(getString(R.string.hc_dev_mode_on))
                }
            }
        }
        handler.postDelayed(runnable, 50)
    }

    override fun onResume() {
        super.onResume()
        if (_binding == null) return
        try {
            NekoLang.apply(binding.root)
            refreshTheme()
            refreshStatsDaily()
            _binding?.tvDetails?.text = buildDetails()
            // 模块可能在页面创建之后才安装/加载，这里重算一次（注入与隐藏都是幂等的）
            try {
                com.nekotype.app.module.ModuleUi.injectSettingsCards(requireContext(), binding.settingsRoot)
                com.nekotype.app.module.ModuleUi.applyHidden(binding.btnOpenTerminal, com.nekotype.app.module.FeatureKeys.TERMINAL)
                com.nekotype.app.module.ModuleUi.applyHidden(binding.cardSponsor, com.nekotype.app.module.FeatureKeys.SPONSOR)
                com.nekotype.app.module.ModuleUi.applyHidden(binding.cardImportExport, com.nekotype.app.module.FeatureKeys.IMPORT_EXPORT)
                com.nekotype.app.module.ModuleUi.applyHidden(binding.rowModule, com.nekotype.app.module.FeatureKeys.MODULES)
            } catch (_: Throwable) { }
        } catch (_: Throwable) { }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && view != null) {
            refreshTheme()
            refreshStatsDaily()
            try {
                com.nekotype.app.module.ModuleUi.injectSettingsCards(requireContext(), binding.settingsRoot)
            } catch (_: Throwable) { }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------- 主题 ----------

    private fun switchTheme(mode: String) {
        if (AppPrefs.themeMode == mode) return
        AppPrefs.themeMode = mode
        // 猫娘主题同步开启猫娘用语
        AppPrefs.nekoMode = (mode == "neko")
        applyThemeMode()
        // 立即刷新当前页面背景
        BgUtils.apply(binding.root)
        refreshTheme()
        // 重建 Activity 让全局主题生效
        requireActivity().recreate()
    }

    private fun applyThemeMode() {
        when (AppPrefs.themeMode) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "star" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "cccp" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "neko" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun refreshTheme() {
        if (_binding == null) return
        val selectedId = when (AppPrefs.themeMode) {
            "dark" -> R.id.btnThemeDark
            "light" -> R.id.btnThemeLight
            "star" -> R.id.btnThemeStar
            "neko" -> R.id.btnThemeNeko
            "cccp" -> R.id.btnThemeCccp
            else -> R.id.btnThemeSystem
        }
        listOf(R.id.btnThemeSystem, R.id.btnThemeDark, R.id.btnThemeLight, R.id.btnThemeStar, R.id.btnThemeNeko, R.id.btnThemeCccp).forEach { id ->
            view?.findViewById<com.google.android.material.button.MaterialButton>(id)?.isChecked = (id == selectedId)
        }
        binding.tvThemeHint.text = when (AppPrefs.themeMode) {
            "dark" -> getString(R.string.u134)
            "light" -> getString(R.string.u135)
            "star" -> getString(R.string.theme_star_hint)
            "neko" -> getString(R.string.hc_theme_neko_toast)
            "cccp" -> getString(R.string.theme_cccp_hint)
            else -> getString(R.string.u136)
        }
    }

    // ---------- 统计 ----------

    private fun refreshStatsDaily() {
        if (_binding == null) return
        val stats = AppPrefs.dailyStats
        val fmt = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
        val today = fmt.format(java.util.Date())
        val sb = StringBuilder()
        sb.append(getString(R.string.u127, stats[today] ?: 0, AppPrefs.transformCount))
        val cal = java.util.Calendar.getInstance()
        for (i in 6 downTo 0) {
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val day = fmt.format(cal.time)
            sb.append(getString(R.string.u128, day, stats[day] ?: 0))
        }
        binding.tvStatsDaily.text = sb.toString()
    }

    // ---------- 导入导出 ----------

    private fun exportConfig() {
        val text = AppPrefs.exportConfigText()
        if (text.isEmpty()) {
            toast(getString(R.string.u40))
            return
        }
        try {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("nekotype_config", text))
        } catch (_: Throwable) { }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.u123))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.u124)))
    }

    private fun showImportDialog() {
        // Material 多行输入框（替代裸 EditText + 自定义按钮行，统一成交互规范的对话框按钮）
        val inText = NekoDialog.input(requireContext(), getString(R.string.u129), multiline = true)
        // 限制输入区高度：粘贴超长配置时不会把「取消 / 导入」按钮顶出屏幕。
        // 注意：不能改 editText 的 layoutParams —— TextInputLayout 内部按 FrameLayout.LayoutParams
        // 取子 View 参数，塞 LinearLayout 参数会在 onMeasure 里抛 ClassCastException（导入直接崩）。
        // 用 minLines/maxLines + maxHeight 限制即可。
        inText.editText?.apply {
            minLines = 3
            maxLines = 5
            maxHeight = (resources.displayMetrics.density * 128).toInt()
        }
        try {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.let {
                if (it.startsWith("{") && it.contains("NekoType")) inText.editText?.setText(it)
            }
        } catch (_: Throwable) { }

        val dialog = NekoDialog.builder(requireContext())
            .setTitle(getString(R.string.u39))
            .setMessage(getString(R.string.u42))
            .setView(NekoDialog.scroll(NekoDialog.column(requireContext(), inText)))
            .setPositiveButton(getString(R.string.u130)) { _, _ ->
                val text = NekoDialog.textOf(inText).trim()
                if (text.isEmpty()) { toast(getString(R.string.u9)); return@setPositiveButton }
                val err = AppPrefs.importConfigText(text)
                if (err != null) {
                    toast(err)
                } else {
                    toast(getString(R.string.u0))
                    applyThemeMode()
                    refreshStatsDaily()
                    BgUtils.apply(binding.root)
                }
            }
            .setNegativeButton(getString(R.string.u72), null)
            .create()
        dialog.show()
    }

    // ---------- 详细信息 ----------

    private fun buildDetails(): String {
        val ctx = requireContext()
        val sb = StringBuilder()
        sb.append(getString(R.string.u137)).append(if (Settings.canDrawOverlays(ctx)) getString(R.string.u138) else getString(R.string.u139)).append('\n')
        sb.append(getString(R.string.u140)).append(if (isAccessibilityEnabled()) getString(R.string.u141) else getString(R.string.u142)).append('\n')
        sb.append(getString(R.string.u143)).append(if (SysPower.isIgnoringBatteryOptimizations()) getString(R.string.u144) else getString(R.string.u145)).append('\n')
        sb.append(getString(R.string.u146)).append(if (SysPower.isShizukuAvailable()) getString(R.string.u147) else getString(R.string.u148)).append('\n')
        sb.append(getString(R.string.u150)).append(if (SysPower.isDeviceAdminActive()) getString(R.string.u151) else getString(R.string.u152)).append('\n')
        sb.append(getString(R.string.u153)).append(if (AppPrefs.serviceEnabled) getString(R.string.u97) else getString(R.string.u98)).append('\n')
        sb.append(getString(R.string.u154, AppPrefs.transformCount))
        return sb.toString()
    }

    private fun refreshVersion() {
        if (_binding == null) return
        val info = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        val versionName = info.versionName ?: "?"
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toString() else info.versionCode.toString()
        binding.tvVersion.text = getString(R.string.u155, versionName, versionCode, requireContext().packageName)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val ctx = requireContext()
        val cmp = "${ctx.packageName}/${com.google.android.accessibility.selecttospeak.SelectToSpeakService::class.java.name}"
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(":").any { it.equals(cmp, ignoreCase = true) }
    }

    // ---------- 支持与反馈 ----------

    private fun showSponsorDialog(resId: Int = R.drawable.sponsor_qr) {
        val img = ImageView(requireContext()).apply {
            setImageResource(resId)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        NekoDialog.builder(requireContext())
            .setTitle(getString(R.string.u44))
            .setMessage(getString(R.string.u31))
            .setView(img)
            .setPositiveButton(getString(R.string.u156), null)
            .show()
    }

    private fun sendFeedbackEmail() {
        try {
            val info = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val version = "${info.versionName} (${if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode})"
            val subject = getString(R.string.u157)
            val body = getString(R.string.u158, version, Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE,
                if (SysPower.isShizukuPermissionGranted()) getString(R.string.u109) else getString(R.string.u108))
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:TR114512@qq.com")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.u159)))
        } catch (_: Throwable) {
            toast(getString(R.string.u58))
            copyToClipboard("TR114512@qq.com")
        }
    }

    private fun tryOpenQqChannel() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/q/pEKhYV2lI6")))
        } catch (_: Throwable) {
            toast(getString(R.string.u57))
        }
    }

    private fun tryOpenAfdian() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://afdian.com/a/Yukstarlight")))
        } catch (_: Throwable) {
            toast(getString(R.string.u57))
            copyToClipboard("https://afdian.com/a/Yukstarlight")
        }
    }

    private fun copyToClipboard(text: String) {
        try {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("nekotype", text))
            toast(getString(R.string.u15, text))
        } catch (_: Throwable) { }
    }

    private fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    // ================= AI 改写（可选联网） =================

    /** 程序化构建 AI 设置卡片（与模块卡片同一外观，不动 XML 布局） */
    private fun setupAiCard(view: View) {
        val root = view.findViewById<android.widget.LinearLayout>(R.id.settingsRoot) ?: return
        val ctx = requireContext()
        val box = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        root.addView(com.nekotype.app.module.ModuleUi.buildCard(ctx, "AI 改写（可选联网）", box))

        fun dpx(v: Int) = (v * resources.displayMetrics.density).toInt()

        // 行内摘要刷新：任何一项改完都调用，保证卡片上展示的是最新值
        val refreshers = mutableListOf<() -> Unit>()
        fun refreshAll() {
            refreshers.forEach { r -> try { r() } catch (_: Throwable) { } }
        }

        /** 带当前值摘要的可点击行（NekoDialog.row 会在末尾自动加 › 箭头） */
        fun addValueRow(build: () -> String, onClick: () -> Unit) {
            val tv = NekoDialog.row(ctx, build()) { onClick() }
            box.addView(tv)
            refreshers.add { tv.text = "${build()}    ›" }
        }

        /** 通用文本编辑对话框（保存后刷新摘要） */
        fun editDialog(title: String, hint: String, value: String, password: Boolean = false, numeric: Boolean = false, multiline: Boolean = false, onSave: (String) -> Unit) {
            val input = NekoDialog.input(ctx, hint, value = value, password = password, numeric = numeric, multiline = multiline)
            NekoDialog.builder(ctx)
                .setTitle(title)
                .setView(NekoDialog.scroll(NekoDialog.column(ctx, input)))
                .setPositiveButton("保存") { _, _ ->
                    val v = NekoDialog.textOf(input).trim()
                    if (v.isNotEmpty()) {
                        onSave(v)
                        refreshAll()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        /** 摘要文本用的次级颜色（跟随主题） */
        fun axColor(): Int {
            val tvAttr = android.util.TypedValue()
            return if (ctx.theme.resolveAttribute(android.R.attr.textColorSecondary, tvAttr, true)) {
                if (tvAttr.resourceId != 0) androidx.core.content.ContextCompat.getColor(ctx, tvAttr.resourceId) else tvAttr.data
            } else 0xFF888888.toInt()
        }

        // ---------- 服务商档案 ----------

        /**
         * 删除档案的二级弹窗。onClosed 在弹窗关闭（无论删除还是取消）后回调——
         * 由档案管理弹窗传入「重开自己」，保证管理列表永远是最新数据。
         */
        fun showProfileDelete(onClosed: () -> Unit) {
            val profiles = AiProfiles.load()
            if (profiles.isEmpty()) {
                toast("还没有可删除的档案")
                onClosed()
                return
            }
            val names = profiles.map { it.name }.toTypedArray()
            NekoDialog.builder(ctx)
                .setTitle("删除哪个档案？")
                .setItems(names) { d, which ->
                    AiProfiles.delete(names[which])
                    toast("已删除：${names[which]}")
                    d.dismiss()
                    refreshAll()
                    onClosed()
                }
                .setNegativeButton("取消") { d, _ ->
                    d.dismiss()
                    onClosed()
                }
                .show()
        }

        /**
         * 服务商档案管理：单击应用 / 保存当前为新档案 / 删除。
         * 弹窗按加载时刻的数据一次性画出，任何增删改后都「关掉自己再重开」，
         * 让 ✓ 标记与列表按最新数据重建（refreshAll 只刷新设置卡片行，刷不到已弹出的对话框）。
         */
        fun showProfileManager() {
            val box = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
            }
            var self: androidx.appcompat.app.AlertDialog? = null
            val profiles = AiProfiles.load()
            if (profiles.isEmpty()) {
                box.addView(TextView(ctx).apply {
                    text = "还没有档案。先把下面的接口地址、密钥、模型名填好，再点「保存当前配置为新档案」，以后换服务商点一下就行。"
                    setTextSizeDimen(R.dimen.ts_12_5)
                    setTextColor(axColor())
                    setPadding(dpx(4), dpx(6), dpx(4), dpx(6))
                })
            }
            profiles.forEach { p ->
                val isCur = p.name == AppPrefs.aiActiveProfileName
                box.addView(NekoDialog.row(ctx, (if (isCur) "✓ " else "") + "${p.name}（${p.model}）") {
                    AiProfiles.apply(p)
                    toast("已切换档案：${p.name}")
                    refreshAll()
                    try { self?.dismiss() } catch (_: Throwable) { }
                    showProfileManager()
                })
            }
            box.addView(NekoDialog.row(ctx, "➕ 保存当前配置为新档案") {
                val input = NekoDialog.input(ctx, "档案名称（如：DeepSeek / GLM）")
                NekoDialog.builder(ctx)
                    .setTitle("保存当前为新档案")
                    .setView(NekoDialog.column(ctx, input))
                    .setPositiveButton("保存") { _, _ ->
                        val name = NekoDialog.textOf(input).trim()
                        if (name.isNotEmpty()) {
                            AiProfiles.addCurrent(name)
                            toast("已保存档案：$name")
                            refreshAll()
                            try { self?.dismiss() } catch (_: Throwable) { }
                            showProfileManager()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            })
            if (profiles.isNotEmpty()) {
                box.addView(NekoDialog.row(ctx, "🗑 删除档案…") {
                    try { self?.dismiss() } catch (_: Throwable) { }
                    showProfileDelete { showProfileManager() }
                })
            }
            val dlg = NekoDialog.builder(ctx)
                .setTitle("服务商档案")
                .setView(NekoDialog.scroll(NekoDialog.column(ctx, box)))
                .setNegativeButton("关闭", null)
                .create()
            self = dlg
            dlg.show()
        }

        // ---------- AI 改写历史 ----------

        /** 历史查看：最新在前，每条含时间/人设/原文/改写（或失败原因）；支持一键清空 */
        fun showAiHistory() {
            val entries = AiHistory.load()
            val box = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
            }
            if (entries.isEmpty()) {
                box.addView(TextView(ctx).apply {
                    text = "还没有记录。开了 AI 之后，每次改写（包括下面的测试按钮）都会在这里留一条。"
                    setTextSizeDimen(R.dimen.ts_12_5)
                    setTextColor(axColor())
                    setPadding(dpx(4), dpx(6), dpx(4), dpx(6))
                })
            } else {
                val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                entries.forEach { e ->
                    box.addView(TextView(ctx).apply {
                        text = buildString {
                            append(fmt.format(java.util.Date(e.time)))
                            if (e.persona.isNotEmpty()) append("  ·  ").append(e.persona)
                            if (!e.ok) append("  ❌")
                            append("\n原文：").append(e.src)
                            if (e.ok) append("\n改写：").append(e.out) else append("\n失败：").append(e.out)
                        }
                        setTextSizeDimen(R.dimen.ts_12_5)
                        setTextColor(if (e.ok) axColor() else 0xFFD0604C.toInt())
                        setPadding(dpx(4), dpx(8), dpx(4), dpx(2))
                        setTextIsSelectable(true)
                    })
                    box.addView(View(ctx).apply {
                        setBackgroundColor(0x1A888888)
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dpx(1)
                        )
                    })
                }
            }
            NekoDialog.builder(ctx)
                .setTitle("AI 改写历史（${entries.size}/20）")
                .setView(NekoDialog.scroll(NekoDialog.column(ctx, box)))
                .setPositiveButton("清空") { _, _ ->
                    AiHistory.clear()
                    toast("已清空历史")
                    refreshAll()
                }
                .setNegativeButton("关闭", null)
                .show()
        }

        // ① 总开关：开启后悬浮球手动改写才会走 AI（强制篡改键盘等自动模式不受影响）
        val swRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dpx(4), dpx(10), dpx(4), dpx(10))
        }
        val swLabel = TextView(ctx).apply {
            text = "开启 AI 改写（点悬浮球改写时才走 AI）"
            setTextSizeDimen(R.dimen.ts_14)
        }
        val swAi = com.google.android.material.materialswitch.MaterialSwitch(ctx).apply {
            isChecked = AppPrefs.aiEnabled
            setOnCheckedChangeListener { _, checked ->
                AppPrefs.aiEnabled = checked
                NekoLog.adjust(if (checked) "开启 AI 改写" else "关闭 AI 改写")
                if (checked && AppPrefs.aiApiKey.isBlank()) toast("已开启 AI 改写：请先配置 API 密钥")
            }
        }
        swRow.addView(swLabel, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        swRow.addView(swAi, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
        // 整行可点（不只是点开关本身），与设置页其他行的交互习惯一致
        swRow.setOnClickListener { swAi.toggle() }
        box.addView(swRow)

        // ② 服务商档案：命名快照，一键切换「接口+密钥+模型」组合
        addValueRow(
            { "服务商档案：${AiProfiles.summary()}" },
            { showProfileManager() }
        )

        // ③ 接口地址（OpenAI 兼容 base_url，自动补全 /chat/completions）
        // ③④⑤ 手动改接口/密钥/模型都会清空「当前档案名」：生效值一旦偏离档案快照，
        // 摘要里「当前：X」就名不副实（档案本体仍保留，重新点一下即可重新对齐）
        addValueRow(
            { "接口地址：${AppPrefs.aiBaseUrl}" },
            { editDialog("接口地址", "OpenAI 兼容 base_url，如 https://api.deepseek.com/v1", AppPrefs.aiBaseUrl) { AppPrefs.aiBaseUrl = it; AppPrefs.aiActiveProfileName = "" } }
        )

        // ④ API 密钥：密码框（可切换明文）；只在输入时可见，卡片上只显示位数
        addValueRow(
            { "API 密钥：" + if (AppPrefs.aiApiKey.isBlank()) "（未设置）" else "已设置（${AppPrefs.aiApiKey.length} 位）" },
            { editDialog("API 密钥", "sk-…（仅保存在本机，导出配置不含密钥）", AppPrefs.aiApiKey, password = true) { AppPrefs.aiApiKey = it; AppPrefs.aiActiveProfileName = "" } }
        )

        // ⑤ 模型名
        addValueRow(
            { "模型名：${AppPrefs.aiModel}" },
            { editDialog("模型名", "如 deepseek-chat / glm-4-flash / moonshot-v1-8k", AppPrefs.aiModel) { AppPrefs.aiModel = it; AppPrefs.aiActiveProfileName = "" } }
        )

        // ⑥ 超时秒数（AppPrefs setter 内部已 clamp 到 3-120）
        addValueRow(
            { "超时秒数：${AppPrefs.aiTimeoutSec}" },
            {
                val input = NekoDialog.input(ctx, "超时秒数（3-120）", value = AppPrefs.aiTimeoutSec.toString(), numeric = true)
                NekoDialog.builder(ctx)
                    .setTitle("请求超时秒数")
                    .setView(NekoDialog.column(ctx, input))
                    .setPositiveButton("保存") { _, _ ->
                        NekoDialog.textOf(input).trim().toIntOrNull()?.let { AppPrefs.aiTimeoutSec = it }
                        refreshAll()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )

        // ⑦ 提示词模式：跟随当前预设人设 / 固定人设 / 自定义提示词
        addValueRow(
            {
                "提示词模式：" + when (AppPrefs.aiPromptMode) {
                    1 -> "固定人设"
                    2 -> "自定义提示词"
                    else -> "跟随当前预设人设"
                }
            },
            {
                val modes = arrayOf("跟随当前预设人设", "固定人设", "自定义提示词")
                NekoDialog.builder(ctx)
                    .setTitle("提示词模式")
                    .setSingleChoiceItems(modes, AppPrefs.aiPromptMode) { d, which ->
                        AppPrefs.aiPromptMode = which
                        d.dismiss()
                        refreshAll()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )

        // ⑧ 固定人设（提示词模式=固定人设时生效；9 套内置提示词见 AiPersonas）
        addValueRow(
            { "固定人设：${AiPersonas.byId(AppPrefs.aiPersonaId)?.name ?: AiPersonas.default().name}" },
            {
                val names = AiPersonas.ALL.map { it.name }.toTypedArray()
                val checked = AiPersonas.ALL.indexOfFirst { it.id == AppPrefs.aiPersonaId }.coerceAtLeast(0)
                NekoDialog.builder(ctx)
                    .setTitle("选择固定人设")
                    .setSingleChoiceItems(names, checked) { d, which ->
                        AppPrefs.aiPersonaId = AiPersonas.ALL[which].id
                        d.dismiss()
                        refreshAll()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )

        // ⑨ 自定义提示词（模式 2 时整段作为 system 提示词）
        addValueRow(
            { "自定义提示词：" + if (AppPrefs.aiCustomPrompt.isBlank()) "（未设置）" else "已设置（${AppPrefs.aiCustomPrompt.length} 字）" },
            { editDialog("自定义提示词", "整段作为 system 提示词发给 AI，例如：把我的消息改成东北话", AppPrefs.aiCustomPrompt, multiline = true) { AppPrefs.aiCustomPrompt = it } }
        )

        // ⑩ AI 改写历史：最近 20 条 原文 → 改写后（含失败原因），仅存本机
        addValueRow(
            { "AI 改写历史：${AiHistory.summary()}" },
            { showAiHistory() }
        )

        // ⑪ 测试：发一条固定文本走完整链路，直接展示 AI 返回或人话错误
        box.addView(NekoDialog.row(ctx, "测试 AI 连接（发送：你好，测试一下）") {
            val waiting = NekoDialog.builder(ctx)
                .setTitle("测试 AI 改写")
                .setMessage("正在请求 AI 接口……（超时 ${AppPrefs.aiTimeoutSec}s）")
                .setCancelable(false)
                .create()
            waiting.show()
            lifecycleScope.launch {
                val outcome = AiRewriter.call("你好，测试一下")
                try { waiting.dismiss() } catch (_: Throwable) { }
                when (outcome) {
                    is AiRewriter.AiOutcome.Ok -> NekoDialog.builder(ctx)
                        .setTitle("测试成功（${outcome.personaName}，${outcome.elapsedMs}ms）")
                        .setMessage(outcome.text)
                        .setPositiveButton("确定", null)
                        .show()
                    is AiRewriter.AiOutcome.Err -> NekoDialog.builder(ctx)
                        .setTitle("测试失败")
                        .setMessage(outcome.message)
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        })

        // 隐私说明：如实告知"文本会离开手机"这一行为
        val hint = TextView(ctx).apply {
            text = "开了之后，点悬浮球改写会先把输入框里的字发给你填的 AI 接口（OpenAI 兼容的都行，DeepSeek、GLM、Kimi 这些），按人设改写完再发出去。超时或出错会自动再试一次，还不行就用本地规则的结果。接口、密钥、模型可以存成档案，切换的时候不用重填。注意：你打的字会发到接口服务商那边，服务商自己选靠谱的；密钥和历史只存在手机里，不进导出文件。"
            setTextSizeDimen(R.dimen.ts_12_5)
            setPadding(dpx(4), dpx(10), dpx(4), 0)
            val tvAttr = android.util.TypedValue()
            if (ctx.theme.resolveAttribute(android.R.attr.textColorSecondary, tvAttr, true)) {
                setTextColor(
                    if (tvAttr.resourceId != 0) androidx.core.content.ContextCompat.getColor(ctx, tvAttr.resourceId)
                    else tvAttr.data
                )
            }
        }
        box.addView(hint)
    }

    // ================= 人设语气包 =================

    private fun setupPersonaButtons(view: View) {
        val personas = mapOf(
            R.id.btnPersonaLoli to "萝莉语",
            R.id.btnPersonaAncient to "古风文言",
            R.id.btnPersonaDub to "译制片腔",
            R.id.btnPersonaSarcasm to "阴阳怪气",
            R.id.btnPersonaMars to "火星文",
            R.id.btnPersonaYandere to "病娇",
            R.id.btnPersonaFuta to "雌小鬼",
            R.id.btnPersonaNeko to "猫娘"
        )
        personas.forEach { (id, name) ->
            view.findViewById<TextView>(id)?.setOnClickListener {
                applyPersona(name)
            }
        }
    }

    private fun setupMoodProbability(view: View) {
        val seek = view.findViewById<android.widget.SeekBar>(R.id.seekMoodProb)
        val tvVal = view.findViewById<TextView>(R.id.tvMoodProbVal)
        val tvHint = view.findViewById<TextView>(R.id.tvMoodProbHint)
        seek?.progress = AppPrefs.nekoMoodProbability
        tvVal?.text = "${AppPrefs.effectiveMoodProbability}%"
        tvHint?.text = if (AppPrefs.nekoMode) {
            getString(R.string.i30, AppPrefs.effectiveMoodProbability, AppPrefs.nekoMoodProbability)
        } else {
            getString(R.string.i28)
        }
        seek?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, v: Int, fromUser: Boolean) {
                AppPrefs.nekoMoodProbability = v
                tvVal?.text = "${AppPrefs.effectiveMoodProbability}%"
                tvHint?.text = if (AppPrefs.nekoMode) {
                    getString(R.string.i30, AppPrefs.effectiveMoodProbability, v)
                } else {
                    getString(R.string.i28)
                }
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
    }

    private fun applyPersona(name: String) {
        if (AppPrefs.hasPresetName(name)) {
            // 已存在则直接切换
            val preset = AppPrefs.presetList().firstOrNull { it.second == name }
            if (preset != null) {
                AppPrefs.selectPreset(preset.first)
                toast(getString(R.string.hc_switched_to, name))
                NekoLog.adjust("切换人设语气包：$name")
                return
            }
        }
        val rules = when (name) {
            "萝莉语" -> buildLoliRules()
            "古风文言" -> buildAncientRules()
            "译制片腔" -> buildDubRules()
            "阴阳怪气" -> buildSarcasmRules()
            "火星文" -> buildMarsRules()
            "病娇" -> buildYandereRules()
            "雌小鬼" -> buildFutaRules()
            "猫娘" -> buildNekoRules()
            else -> emptyList()
        }
        if (rules.isEmpty()) {
            toast(getString(R.string.hc_rule_gen_fail))
            return
        }
        AppPrefs.createPreset(name = name, rules = rules, switchTo = true)
        toast(getString(R.string.hc_preset_created, name))
        NekoLog.adjust("创建人设语气包：$name（${rules.size}条规则）")
    }

    private fun rule(id: String, type: AppPrefs.RuleType, value: String, replaceTo: String = "", chance: Int = 50) =
        AppPrefs.NekoRule(id = id, type = type, value = value, replaceTo = replaceTo, chance = chance)

    private fun buildLoliRules(): List<AppPrefs.NekoRule> = listOf(
        rule("loli_replace_1", AppPrefs.RuleType.REPLACE, "我", "人家"),
        rule("loli_replace_2", AppPrefs.RuleType.REPLACE, "你", "你酱"),
        rule("loli_replace_3", AppPrefs.RuleType.REPLACE, "什么", "什喵"),
        rule("loli_replace_4", AppPrefs.RuleType.REPLACE, "没有", "才没有呢"),
        rule("loli_suffix", AppPrefs.RuleType.RANDOM_SUFFIX, "呢~|呀~|喵~|的说~|啦~|嘛~", chance = 70)
    )

    private fun buildAncientRules(): List<AppPrefs.NekoRule> = listOf(
        rule("ancient_replace_1", AppPrefs.RuleType.REPLACE, "我", "吾"),
        rule("ancient_replace_2", AppPrefs.RuleType.REPLACE, "你", "汝"),
        rule("ancient_replace_3", AppPrefs.RuleType.REPLACE, "是", "乃"),
        rule("ancient_replace_4", AppPrefs.RuleType.REPLACE, "的", "之"),
        rule("ancient_replace_5", AppPrefs.RuleType.REPLACE, "了", "矣"),
        rule("ancient_replace_6", AppPrefs.RuleType.REPLACE, "吗", "乎"),
        rule("ancient_replace_7", AppPrefs.RuleType.REPLACE, "很", "甚"),
        rule("ancient_replace_8", AppPrefs.RuleType.REPLACE, "不要", "勿"),
        rule("ancient_suffix", AppPrefs.RuleType.SUFFIX, "也")
    )

    private fun buildDubRules(): List<AppPrefs.NekoRule> = listOf(
        rule("dub_prefix", AppPrefs.RuleType.RANDOM_PREFIX, "哦，|听着，|我说，|看在上帝的份上，|老伙计，|我亲爱的朋友，", chance = 60),
        rule("dub_suffix", AppPrefs.RuleType.RANDOM_SUFFIX, "，我的老伙计|，我敢打赌|，这是真的|，相信我|，我向你保证", chance = 50),
        rule("dub_replace_1", AppPrefs.RuleType.REPLACE, "好的", "哦，当然"),
        rule("dub_replace_2", AppPrefs.RuleType.REPLACE, "不行", "哦，这可不行")
    )

    private fun buildSarcasmRules(): List<AppPrefs.NekoRule> = listOf(
        rule("sarcasm_suffix", AppPrefs.RuleType.RANDOM_SUFFIX, "，哦？|，是吗？|，不会吧不会吧|，就这？|，哦~|，笑死|，真的吗|，我不信", chance = 80),
        rule("sarcasm_replace_1", AppPrefs.RuleType.REPLACE, "好", "哦，好呢"),
        rule("sarcasm_replace_2", AppPrefs.RuleType.REPLACE, "行", "哦，行吧"),
        rule("sarcasm_replace_3", AppPrefs.RuleType.REPLACE, "可以", "哦，可以可以"),
        rule("sarcasm_replace_4", AppPrefs.RuleType.REPLACE, "厉害", "哦，厉害厉害")
    )

    private fun buildMarsRules(): List<AppPrefs.NekoRule> = listOf(
        rule("mars_replace_1", AppPrefs.RuleType.REPLACE, "我", "ωǒ"),
        rule("mars_replace_2", AppPrefs.RuleType.REPLACE, "你", "伱"),
        rule("mars_replace_3", AppPrefs.RuleType.REPLACE, "的", "の"),
        rule("mars_replace_4", AppPrefs.RuleType.REPLACE, "是", "④"),
        rule("mars_replace_5", AppPrefs.RuleType.REPLACE, "爱", "じ☆ve"),
        rule("mars_replace_6", AppPrefs.RuleType.REPLACE, "么", "麽"),
        rule("mars_replace_7", AppPrefs.RuleType.REPLACE, "吗", "嗎"),
        rule("mars_replace_8", AppPrefs.RuleType.REPLACE, "了", "嘞"),
        rule("mars_replace_9", AppPrefs.RuleType.REPLACE, "人", "亽"),
        rule("mars_replace_10", AppPrefs.RuleType.REPLACE, "心", "惢"),
        rule("mars_suffix", AppPrefs.RuleType.SUFFIX, "~")
    )

    private fun buildYandereRules(): List<AppPrefs.NekoRule> = listOf(
        rule("yd_prefix", AppPrefs.RuleType.PREFIX, "呵呵..."),
        rule("yd_suffix", AppPrefs.RuleType.SUFFIX, "♡"),
        rule("yd_rand_prefix", AppPrefs.RuleType.RANDOM_PREFIX,
            "你只能是我的|呵呵，又在看别人？|我盯着你哦|不许离开我|你的一切都是我的|为什么不回消息？|我会一直陪着你的|你逃不掉的|眼里只能有我", chance = 55),
        rule("yd_rand_suffix", AppPrefs.RuleType.RANDOM_SUFFIX,
            "...呵呵|你逃不掉的♡|只能是我的|我好喜欢你啊|不许不理我|你的心只能属于我|呵呵，真可爱|想把你藏起来|永远在一起吧", chance = 55),
        rule("yd_rep_1", AppPrefs.RuleType.REPLACE, "喜欢", "只喜欢我一个吧"),
        rule("yd_rep_2", AppPrefs.RuleType.REPLACE, "再见", "不许走"),
        rule("yd_rep_3", AppPrefs.RuleType.REPLACE, "朋友", "只能有我一个"),
        rule("yd_rep_4", AppPrefs.RuleType.REPLACE, "晚安", "梦里也只能想我"),
        rule("yd_rep_5", AppPrefs.RuleType.REPLACE, "哈哈", "呵呵"),
        rule("yd_rep_6", AppPrefs.RuleType.REPLACE, "嗯", "你在敷衍我吗")
    )

    /** 雌小鬼语言包：毒舌嘲讽、嘴硬心软，自称高高在上但被夸会害羞 */
    private fun buildFutaRules(): List<AppPrefs.NekoRule> = listOf(
        rule("futa_rand_prefix", AppPrefs.RuleType.RANDOM_PREFIX,
            "哈？|杂鱼杂鱼~|就这？|哼！|你这种杂鱼也配？|真是的，又要本小姐出手|弱爆了啦", chance = 60),
        rule("futa_rand_suffix", AppPrefs.RuleType.RANDOM_SUFFIX,
            "，杂鱼~|，不行呢~|，垃圾垃圾~|，嘿嘿♪|，弱爆了|，真是拿你没办法", chance = 60),
        rule("futa_rep_1", AppPrefs.RuleType.REPLACE, "你好", "哟，杂鱼你好啊"),
        rule("futa_rep_2", AppPrefs.RuleType.REPLACE, "谢谢", "哼，才不是特意帮你呢"),
        rule("futa_rep_3", AppPrefs.RuleType.REPLACE, "喜欢", "喜欢我？你还差得远呢"),
        rule("futa_rep_4", AppPrefs.RuleType.REPLACE, "厉害", "这算什么，本小姐随便就做到了"),
        rule("futa_rep_5", AppPrefs.RuleType.REPLACE, "好的", "哼，知道了啦"),
        rule("futa_rep_6", AppPrefs.RuleType.REPLACE, "对不起", "知道错了就好，原谅你了♪"),
        rule("futa_rep_7", AppPrefs.RuleType.REPLACE, "你好厉害", "哈？这不是理所当然的吗"),
        rule("futa_rep_8", AppPrefs.RuleType.REPLACE, "晚安", "哼，本小姐才不陪你熬夜呢……不过还是晚安啦")
    )

    /** 猫娘语言包：喵语口癖 + 撒娇卖萌，自称喵 */
    private fun buildNekoRules(): List<AppPrefs.NekoRule> = listOf(
        rule("neko_pkg_rand_prefix", AppPrefs.RuleType.RANDOM_PREFIX,
            "喵~|喵喵？|喵呜~|呜喵~", chance = 50),
        rule("neko_pkg_rand_suffix", AppPrefs.RuleType.RANDOM_SUFFIX,
            "喵~|喵♪|的说喵~|呢喵~|喵呜", chance = 70),
        rule("neko_pkg_rep_1", AppPrefs.RuleType.REPLACE, "好的", "好哒喵~"),
        rule("neko_pkg_rep_2", AppPrefs.RuleType.REPLACE, "知道了", "知道啦喵~"),
        rule("neko_pkg_rep_3", AppPrefs.RuleType.REPLACE, "嗯", "嗯喵~"),
        rule("neko_pkg_rep_4", AppPrefs.RuleType.REPLACE, "谢谢", "谢谢喵~"),
        rule("neko_pkg_rep_5", AppPrefs.RuleType.REPLACE, "不要", "不要嘛喵~"),
        rule("neko_pkg_rep_6", AppPrefs.RuleType.REPLACE, "你好", "喵呜~你好呀"),
        rule("neko_pkg_rep_7", AppPrefs.RuleType.REPLACE, "晚安", "晚安喵，要梦到喵哦~"),
        rule("neko_pkg_rep_8", AppPrefs.RuleType.REPLACE, "喜欢", "最喜欢你啦喵~")
    )
}
