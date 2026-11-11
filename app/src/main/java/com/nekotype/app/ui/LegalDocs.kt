package com.nekotype.app.ui

import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * 法律文本（隐私政策 / 第三方信息共享清单 / 开源许可）。
 *
 * 单独放在一个文件里，便于法务相关文案统一维护与审阅。
 * 语言跟随应用当前语言（AppCompatDelegate 的应用级语言，未设置时用系统语言）。
 *
 * 内容依据（写之前逐条核对过，避免写出不属实的承诺）：
 * - AndroidManifest 已声明 INTERNET 权限，但只服务于可选的「AI 改写」功能：
 *   该功能默认关闭，关闭时应用不发起任何网络请求（见 AiRewriter 的实现）
 * - 权限清单：SYSTEM_ALERT_WINDOW / BIND_ACCESSIBILITY_SERVICE / FOREGROUND_SERVICE
 *   / POST_NOTIFICATIONS / REQUEST_IGNORE_BATTERY_OPTIMIZATIONS / QUERY_ALL_PACKAGES
 *   / RECEIVE_BOOT_COMPLETED / INTERNET
 * - 依赖：AndroidX、Material、Kotlin/coroutines、Shizuku API、argon2kt（均 Apache-2.0）
 *   + Termux 终端模拟器代码（GPL-3.0）
 */
object LegalDocs {

    private fun lang(): String {
        val tag = try {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        } catch (_: Throwable) { "" }
        val t = tag.ifEmpty { Locale.getDefault().toString() }
        return when {
            t.startsWith("zh") && (t.contains("TW") || t.contains("Hant") || t.contains("HK")) -> "tw"
            t.startsWith("zh") -> "zh"
            else -> "en"
        }
    }

    // ---------- 隐私政策 ----------

    fun privacy(): String = when (lang()) {
        "zh" -> """
            NekoType 隐私政策
            更新日期：2026-11-10

            【一、关于联网与数据收集】
            本应用本身不收集任何数据：没有账号体系，不含广告、统计、推送、社交类 SDK，
            应用自身没有任何服务器。
            应用已申请 INTERNET（联网）权限，但它只服务于可选的「AI 改写」功能：
            该功能默认关闭，只有当你在设置中主动开启 AI 改写并填入自己的接口地址与
            API 密钥后，应用才会在你点悬浮球改写时，把当前输入框文本发送到你自己
            配置的 AI 服务商。未开启 AI 改写时，应用不会发起任何网络请求。

            【二、输入内容的处理】
            为实现"打字即改写"，本应用通过安卓无障碍服务读取你当前输入框中的文本，
            仅用于按你自己配置的规则（前缀 / 后缀 / 随机 / 替换等）在本机改写并写回输入框。
            这些文本不会被记录、不会上传、不会离开本机。
            关闭无障碍服务后，本应用无法读取任何输入内容。
            若你开启 AI 改写：点悬浮球改写时，当前输入框文本会被发送到你配置的 AI 接口
            用于改写，改写结果再写回输入框；请自行选择可信的服务商，关闭 AI 改写即可
            恢复完全离线。

            【三、保存在本机的数据】
            · 规则与预设、行为与样式设置
            · 悬浮按钮位置与外观、应用名单
            · AI 改写配置（接口地址 / 模型名 / API 密钥 / 提示词，仅存本机，
              导出配置刻意不含 API 密钥）
            · 使用次数统计
            · 应用内日志（用于排查问题，可在设置中一键清空）
            以上全部保存在设备本地，卸载应用即全部删除。

            【四、权限用途说明】
            · 无障碍服务：读取并改写输入框文本、为悬浮按钮提供操作能力
            · 悬浮窗：显示悬浮按钮
            · 通知：显示常驻前台服务通知
            · 联网（INTERNET）：仅 AI 改写功能使用；未开启该功能时不会有任何网络请求
            · 忽略电池优化 / 自启动：避免后台被系统冻结导致功能失效
            · 查询已安装应用：用于应用名单选择、显示悬浮按钮所在应用名
            · 开机自启：重启后恢复服务
            · Shizuku / Root（可选，需你主动授权）：仅执行你主动触发的系统操作
              （如关闭电池优化、隐藏桌面图标），只执行写死的固定命令，
              不提供任意命令通道

            【五、未成年人】
            未成年人请在监护人指导下使用本应用。

            【六、变更与联系】
            本政策如有更新会在应用内展示。有任何疑问可通过项目主页反馈。
        """.trimIndent()

        "tw" -> """
            NekoType 隱私政策
            更新日期：2026-11-10

            【一、關於連網與資料收集】
            本應用本身不收集任何資料：沒有帳號系統，不含廣告、統計、推播、社群類 SDK，
            應用自身沒有任何伺服器。
            應用已申請 INTERNET（連網）權限，但它只服務於選用的「AI 改寫」功能：
            該功能預設關閉，只有當你在設定中主動開啟 AI 改寫並填入自己的介面位址與
            API 金鑰後，應用才會在你點懸浮球改寫時，把目前輸入框文字傳送到你自己
            設定的 AI 服務商。未開啟 AI 改寫時，應用不會發出任何網路請求。

            【二、輸入內容的處理】
            為實現「打字即改寫」，本應用透過安卓無障礙服務讀取你目前輸入框中的文字，
            僅用於依你自己設定的規則（前綴 / 後綴 / 隨機 / 替換等）在本機改寫並寫回輸入框。
            這些文字不會被記錄、不會上傳、不會離開本機。
            關閉無障礙服務後，本應用無法讀取任何輸入內容。
            若你開啟 AI 改寫：點懸浮球改寫時，目前輸入框文字會被傳送到你設定的 AI 介面
            用於改寫，改寫結果再寫回輸入框；請自行選擇可信的服務商，關閉 AI 改寫即可
            恢復完全離線。

            【三、保存在本機的資料】
            · 規則與預設、行為與樣式設定
            · 懸浮按鈕位置與外觀、應用黑名單
            · AI 改寫設定（介面位址 / 模型名稱 / API 金鑰 / 提示詞，僅存本機，
              匯出設定刻意不含 API 金鑰）
            · 使用次數統計
            · 應用內日誌（用於排查問題，可在設定中一鍵清空）
            以上全部保存在裝置本機，解除安裝即全部刪除。

            【四、權限用途說明】
            · 無障礙服務：讀取並改寫輸入框文字、為懸浮按鈕提供操作能力
            · 懸浮窗：顯示懸浮按鈕
            · 通知：顯示常駐前景服務通知
            · 連網（INTERNET）：僅 AI 改寫功能使用；未開啟該功能時不會有任何網路請求
            · 忽略電池最佳化 / 自啟動：避免後台被系統凍結導致功能失效
            · 查詢已安裝應用：用於應用黑名單選擇、顯示懸浮按鈕所在應用名稱
            · 開機自啟：重開機後恢復服務
            · Shizuku / Root（選用，需你主動授權）：僅執行你主動觸發的系統操作
              （如關閉電池最佳化、隱藏桌面圖示），只執行寫死的固定指令，
              不提供任意指令通道

            【五、未成年人】
            未成年人請在監護人指導下使用本應用。

            【六、變更與聯絡】
            本政策如有更新會在應用內顯示。有任何疑問可透過專案首頁回饋。
        """.trimIndent()

        else -> """
            NekoType Privacy Policy
            Last updated: 2026-11-10

            [1. Networking and data collection]
            The app itself collects nothing: there is no account system, and no ads, analytics,
            push or social SDKs are included. The app has no server of its own.
            The app requests the INTERNET permission, but it is used only by the optional
            "AI rewrite" feature, which is OFF by default. Only after you enable AI rewrite in
            Settings and enter your own endpoint and API key will the app — when you tap the
            floating button to rewrite — send the current input-box text to the AI provider you
            configured yourself. With AI rewrite disabled, the app issues no network requests
            at all.

            [2. How your typed text is handled]
            To rewrite text as you type, the app reads the text in the current input box through
            the Android accessibility service and rewrites it in place according to the rules you
            configured (prefix / suffix / random / replace). That text is never recorded, uploaded
            or sent off the device. With the accessibility service turned off, the app cannot read
            any input content.
            If you enable AI rewrite: when you tap the floating button, the current input-box text
            is sent to the AI endpoint you configured for rewriting, and the rewritten result is
            written back. Choose a provider you trust; disabling AI rewrite restores fully
            offline operation.

            [3. Data stored on your device]
            · Rules and presets, behaviour and style settings
            · Floating button position/appearance, app lists
            · AI rewrite configuration (endpoint / model / API key / prompts, stored locally only;
              exported configs deliberately exclude the API key)
            · Usage counters
            · In-app logs (for troubleshooting, clearable in Settings)
            All of it stays on the device and is deleted when you uninstall the app.

            [4. Why each permission is used]
            · Accessibility service: read/rewrite input box text, drive the floating button
            · Display over other apps: show the floating button
            · Notifications: show the persistent foreground-service notification
            · Internet: used only by the AI rewrite feature; no network requests are made while
              that feature is disabled
            · Ignore battery optimization / auto-start: keep the service from being frozen
            · Query installed apps: app list picker and showing the target app name
            · Boot completed: restore the service after a restart
            · Shizuku / Root (optional, you must grant it): only performs system operations you
              explicitly trigger (e.g. battery whitelist, hiding the launcher icon) using fixed,
              hard-coded commands — never an arbitrary command channel

            [5. Minors]
            Minors should use this app under guardian supervision.

            [6. Changes and contact]
            Updates to this policy will be shown in the app. Questions are welcome via the
            project page.
        """.trimIndent()
    }

    // ---------- 第三方信息共享清单 ----------

    fun thirdParty(): String = when (lang()) {
        "zh" -> """
            NekoType 第三方信息共享清单
            更新日期：2026-09-24

            【结论：不与任何第三方共享你的个人信息】
            本应用不收集设备标识（IMEI / OAID / Android ID / MAC 地址），
            不读取位置、通讯录、短信、相册，不集成广告、统计分析、推送、社交 SDK，
            不存在向第三方共享、转让或公开披露个人信息的行为。

            【随应用打包的开源组件（均为本地组件，不向第三方传输数据）】
            · AndroidX（core-ktx / appcompat / lifecycle / preference）
              提供方：Google    许可：Apache-2.0    用途：基础框架与界面
            · Material Components for Android
              提供方：Google    许可：Apache-2.0    用途：界面组件
            · Kotlin 标准库 / kotlinx.coroutines
              提供方：JetBrains    许可：Apache-2.0    用途：语言运行时与协程调度
            · Shizuku API / Provider
              提供方：Rikka    许可：Apache-2.0    用途：与本机 Shizuku 服务通信（仅本机）
            · argon2kt
              提供方：lambdapioneer    许可：Apache-2.0    用途：密码锁定的本地哈希计算
            · Termux 终端模拟器代码（com.termux.terminal）
              提供方：Termux 项目    许可：GPL-3.0    用途：内置终端实现

            【数据出境】
            默认不发生。应用不集成任何向第三方传输数据的 SDK；仅当你开启 AI 改写并配置
            接口后，输入框文本才会发送到你自己填写的 AI 服务商地址（由你自行选择与信任），
            应用自身没有服务器，也不存在其他任何数据传输。
        """.trimIndent()

        "tw" -> """
            NekoType 第三方資訊共享清單
            更新日期：2026-09-24

            【結論：不與任何第三方共享你的個人資訊】
            本應用不收集裝置識別碼（IMEI / OAID / Android ID / MAC 位址），
            不讀取位置、通訊錄、簡訊、相簿，不整合廣告、統計分析、推播、社群 SDK，
            不存在向第三方共享、轉讓或公開揭露個人資訊的行為。

            【隨應用打包的開源元件（均為本機元件，不向第三方傳輸資料）】
            · AndroidX（core-ktx / appcompat / lifecycle / preference）
              提供方：Google    授權：Apache-2.0    用途：基礎框架與介面
            · Material Components for Android
              提供方：Google    授權：Apache-2.0    用途：介面元件
            · Kotlin 標準庫 / kotlinx.coroutines
              提供方：JetBrains    授權：Apache-2.0    用途：語言執行環境與協程排程
            · Shizuku API / Provider
              提供方：Rikka    授權：Apache-2.0    用途：與本機 Shizuku 服務通訊（僅本機）
            · argon2kt
              提供方：lambdapioneer    授權：Apache-2.0    用途：密碼鎖定的本機雜湊計算
            · Termux 終端機模擬器程式碼（com.termux.terminal）
              提供方：Termux 專案    授權：GPL-3.0    用途：內建終端機實作

            【資料跨境傳輸】
            預設不發生。應用不整合任何向第三方傳輸資料的 SDK；僅當你開啟 AI 改寫並設定
            介面後，輸入框文字才會傳送到你自己填寫的 AI 服務商位址（由你自行選擇與信任），
            應用自身沒有伺服器，也不存在其他任何資料傳輸。
        """.trimIndent()

        else -> """
            NekoType Third-Party Data Sharing List
            Last updated: 2026-09-24

            [Summary: nothing is shared with any third party]
            The app does not collect device identifiers (IMEI / OAID / Android ID / MAC),
            does not read location, contacts, SMS or photos, and bundles no advertising,
            analytics, push or social SDKs. No personal information is shared with, transferred
            to, or publicly disclosed to any third party.

            [Open-source components bundled with the app — all local, no data leaves the device]
            · AndroidX (core-ktx / appcompat / lifecycle / preference)
              Provider: Google    License: Apache-2.0    Purpose: base framework and UI
            · Material Components for Android
              Provider: Google    License: Apache-2.0    Purpose: UI components
            · Kotlin standard library / kotlinx.coroutines
              Provider: JetBrains    License: Apache-2.0    Purpose: runtime and coroutines
            · Shizuku API / Provider
              Provider: Rikka    License: Apache-2.0    Purpose: talk to the local Shizuku service
            · argon2kt
              Provider: lambdapioneer    License: Apache-2.0    Purpose: local password hashing
            · Termux terminal emulator code (com.termux.terminal)
              Provider: Termux project    License: GPL-3.0    Purpose: built-in terminal

            [Cross-border transfer]
            None by default. The app bundles no SDK that transfers data to third parties; only
            when you enable AI rewrite and configure an endpoint will the input-box text be sent
            to the AI provider address you entered yourself (chosen and trusted by you). The app
            has no server of its own, and no other data transfer occurs.
        """.trimIndent()
    }

    // ---------- 开源许可 ----------

    fun licenses(): String = when (lang()) {
        "zh" -> """
            NekoType 开源许可
            更新日期：2026-11-11

            本应用基于以下开源项目构建，在此致谢并保留其原始许可：

            【Apache License 2.0】
            · AndroidX（core-ktx 1.13.1 / appcompat 1.7.1 / lifecycle 2.8.6 / preference 1.2.1）
            · Material Components for Android 1.12.0
            · Kotlin 标准库、kotlinx.coroutines 1.9.0
            · Shizuku API / Provider 13.1.5（作者 Rikka）
            · argon2kt 1.5.0（作者 lambdapioneer）
            许可全文：https://www.apache.org/licenses/LICENSE-2.0
            说明：应用内语言选择（跟随系统语义、语言名称的双语展示与资源标签对应）
                  参考 Shizuku 管理器的实现思路。

            【GNU General Public License v3.0】
            · Termux 终端模拟器代码（com.termux.terminal 包下的终端实现）
              来源：https://github.com/termux/termux-app
              许可全文：https://www.gnu.org/licenses/gpl-3.0.html
            说明：依据 GPLv3，包含该部分代码的完整对应源码可公开获取，
                  见下方项目主页；该部分代码的修改与再分发需遵循 GPLv3。
            · Hail（雹）—— 应用冻结 / 隐藏工具
              来源：https://github.com/aistra0528/Hail
              许可全文：https://www.gnu.org/licenses/gpl-3.0.html
            说明：本应用的 Shizuku 特权通道（经 moe.shizuku.server.IShizukuService
                  直连 newProcess 起 shell 进程执行固定动作）与隐藏模式中的
                  「Shizuku 隐藏」工作模式（包级隐藏需 root、以 adb 权限运行时明确提示）
                  参考其实现思路与行为定义。

            【GNU Lesser General Public License v3.0】
            · Operit AI —— Android 端 AI 助手
              来源：https://github.com/AAswordman/Operit
              许可全文：https://www.gnu.org/licenses/lgpl-3.0.html
            说明：集成终端的文本选取复制交互（长按选取后经 ActionMode 弹出「复制」
                  菜单、拖动选区时的放大镜与边缘自动滚动）参考其实现思路。

            【支持 · 衍生项目】
            本项目为以下项目提供代码支持（它们基于/借鉴本项目的实现衍生而来）：
            · XERJ AI
              地址：https://github.com/xerj-org/xerj
            · 游龙工具
              地址：https://xn--s6w866h.top/

            【本项目自身】
            项目主页：https://github.com/Yukstarlight/NekoType
            许可：GNU General Public License v3.0（GPL-3.0）
            许可全文：https://www.gnu.org/licenses/gpl-3.0.html

            向所有开源作者致谢。
        """.trimIndent()

        "tw" -> """
            NekoType 開源授權
            更新日期：2026-11-11

            本應用基於以下開源專案建置，在此致謝並保留其原始授權：

            【Apache License 2.0】
            · AndroidX（core-ktx 1.13.1 / appcompat 1.7.1 / lifecycle 2.8.6 / preference 1.2.1）
            · Material Components for Android 1.12.0
            · Kotlin 標準庫、kotlinx.coroutines 1.9.0
            · Shizuku API / Provider 13.1.5（作者 Rikka）
            · argon2kt 1.5.0（作者 lambdapioneer）
            授權全文：https://www.apache.org/licenses/LICENSE-2.0
            說明：應用內語言選擇（跟隨系統語意、語言名稱的雙語顯示與資源標籤對應）
                  參考 Shizuku 管理器的實作思路。

            【GNU General Public License v3.0】
            · Termux 終端機模擬器程式碼（com.termux.terminal 套件下的終端機實作）
              來源：https://github.com/termux/termux-app
              授權全文：https://www.gnu.org/licenses/gpl-3.0.html
            說明：依 GPLv3，包含該部分程式碼的完整對應原始碼可公開取得，
                  見下方專案首頁；該部分程式碼的修改與再散布需遵循 GPLv3。
            · Hail（雹）—— 應用凍結 / 隱藏工具
              來源：https://github.com/aistra0528/Hail
              授權全文：https://www.gnu.org/licenses/gpl-3.0.html
            說明：本應用的 Shizuku 特權通道（經 moe.shizuku.server.IShizukuService
                  直連 newProcess 起 shell 行程執行固定動作）與隱藏模式中的
                  「Shizuku 隱藏」工作模式（套件層級隱藏需 root、以 adb 權限執行時明確提示）
                  參考其實作思路與行為定義。

            【GNU Lesser General Public License v3.0】
            · Operit AI —— Android 端 AI 助手
              來源：https://github.com/AAswordman/Operit
              授權全文：https://www.gnu.org/licenses/lgpl-3.0.html
            說明：整合終端的文字選取複製互動（長按選取後經 ActionMode 彈出「複製」
                  選單、拖動選取區時的放大鏡與邊緣自動捲動）參考其實作思路。

            【支持 · 衍生專案】
            本專案為以下項目提供程式碼支持（它們基於/借鑒本專案的實現衍生而來）：
            · XERJ AI
              地址：https://github.com/xerj-org/xerj
            · 遊龍工具
              地址：https://xn--s6w866h.top/

            【本專案自身】
            專案首頁：https://github.com/Yukstarlight/NekoType
            授權：GNU General Public License v3.0（GPL-3.0）
            授權全文：https://www.gnu.org/licenses/gpl-3.0.html

            向所有開源作者致謝。
        """.trimIndent()

        else -> """
            NekoType Open Source Licenses
            Last updated: 2026-11-11

            This app is built on the following open-source projects. Thanks to their authors;
            their original licenses are retained.

            [Apache License 2.0]
            · AndroidX (core-ktx 1.13.1 / appcompat 1.7.1 / lifecycle 2.8.6 / preference 1.2.1)
            · Material Components for Android 1.12.0
            · Kotlin standard library, kotlinx.coroutines 1.9.0
            · Shizuku API / Provider 13.1.5 (by Rikka)
            · argon2kt 1.5.0 (by lambdapioneer)
            Full text: https://www.apache.org/licenses/LICENSE-2.0
            Note: the in-app language picker (follow-system semantics, bilingual locale
                  names and resource tag mapping) follows the approach of Shizuku Manager.

            [GNU General Public License v3.0]
            · Termux terminal emulator code (the terminal implementation under com.termux.terminal)
              Source: https://github.com/termux/termux-app
              Full text: https://www.gnu.org/licenses/gpl-3.0.html
            Note: under GPLv3 the complete corresponding source for that part is publicly
                  available at the project page below; modifications and redistribution of that
                  part must follow GPLv3.
            · Hail - app freezer / hider
              Source: https://github.com/aistra0528/Hail
              Full text: https://www.gnu.org/licenses/gpl-3.0.html
            Note: this app's Shizuku privileged channel (directly calling
                  moe.shizuku.server.IShizukuService#newProcess to run fixed actions in a
                  shell process) and the "Shizuku - Hide" working mode of hidden mode
                  (package-level hiding needs root; an explicit hint is shown when Shizuku
                  runs with adb privileges) follow its implementation approach and behaviour.

            [GNU Lesser General Public License v3.0]
            · Operit AI - Android AI assistant
              Source: https://github.com/AAswordman/Operit
              Full text: https://www.gnu.org/licenses/lgpl-3.0.html
            Note: the integrated terminal's text-selection copy interaction (long-press to
                  select, then an ActionMode menu with "Copy", plus magnifier and edge
                  auto-scroll while dragging) follows its implementation approach.

            [Supported · derivative projects]
            This project provides code support to the following projects
            (they are derived from / based on this project's implementation):
            · XERJ AI
              https://github.com/xerj-org/xerj
            · YouLong Tools (遊龍工具)
              https://xn--s6w866h.top/

            [This project]
            Project page: https://github.com/Yukstarlight/NekoType
            License: GNU General Public License v3.0 (GPL-3.0)
            Full text: https://www.gnu.org/licenses/gpl-3.0.html

            Thanks to every open-source author.
        """.trimIndent()
    }
}
