package com.nekotype.app.ai

import com.nekotype.app.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/**
 * AI 改写执行器：OpenAI 兼容 chat-completions 接口的手写 HTTP 调用（零第三方依赖）。
 *
 * 兼容范围：任何实现 OpenAI `/v1/chat/completions` 协议的服务商均可——
 * DeepSeek（https://api.deepseek.com/v1）、智谱 GLM（https://open.bigmodel.cn/api/paas/v4）、
 * Kimi（https://api.moonshot.cn/v1）、OpenAI、硅基流动、各类中转站……
 * 地址规范化按 OpenAI SDK 的 base_url 约定：未以 /chat/completions 结尾就自动补全，
 * 所以填 base_url 或完整 endpoint 都能用。
 *
 * 线程与取消模型：
 * - call() 是挂起函数，内部 withContext(Dispatchers.IO) 阻塞式请求，主协程不会被卡住；
 * - 手动触发序号（token）：每次悬浮球改写先 beginToken() 拿到新序号，等 AI 返回后
 *   isCurrent() 校验——期间用户又点了一次悬浮球，旧序号即失效，旧结果直接作废，
 *   避免慢的旧请求把新一次改写的结果覆盖掉。
 *
 * 隐私：仅当用户在设置里开启「AI 改写」并填了 API 密钥时才会发起网络请求；
 * 关闭状态下本类完全不会联网。文本只发往用户自己配置的接口地址。
 */
object AiRewriter {

    /** 改写成功结果：personaName 用于日志展示，elapsedMs 用于"测试"按钮的耗时反馈 */
    data class AiResult(val text: String, val personaName: String, val elapsedMs: Long)

    /** 调用结果：Err 携带面向用户的人话错误信息（测试按钮直接展示）；
     *  retryable=true 表示是超时/网络抖动/网关类临时故障，上层会自动重试一次 */
    sealed class AiOutcome {
        data class Ok(val text: String, val personaName: String, val elapsedMs: Long) : AiOutcome()
        data class Err(val message: String, val retryable: Boolean = false) : AiOutcome()
    }

    /** 手动触发序号（单调递增）：新触发使所有旧触发的在途请求作废 */
    private val seq = AtomicLong(0)

    fun beginToken(): Long = seq.incrementAndGet()

    /** 该次触发的 token 是否仍是最新（false = 期间有了更新的触发，本次结果应作废） */
    fun isCurrent(token: Long): Boolean = token == seq.get()

    /**
     * 服务端使用的快捷入口：把输入框原文交给 AI 改写。
     * 仅当 AI 开启、密钥已配置、返回非空且确实改动了原文时才返回成功结果，其余一律 null。
     */
    suspend fun rewrite(text: String): AiResult? {
        return when (val o = call(text)) {
            is AiOutcome.Ok -> {
                val t = o.text.trim()
                if (t.isNotEmpty() && t != text.trim()) {
                    AiResult(t, o.personaName, o.elapsedMs)
                } else null
            }
            is AiOutcome.Err -> null
        }
    }

    /** 无 token 版：调用方不关心手动触发序号（如设置页「测试」按钮），内部自取新序号 */
    suspend fun call(text: String): AiOutcome = call(text, null)

    /**
     * 完整流程：读取设置 → 选提示词 → HTTP 调用（网络类失败自动重试一次）→ 解析 → 清洗。
     * 任何异常都被吞掉并转成 Err（项目全局约定：永不向调用方抛异常）。
     * 每次真实 HTTP 调用的结果都会写入本地改写历史（见 AiHistory）。
     *
     * @param token 手动触发序号（悬浮球改写传自己 beginToken() 拿到的值）：
     *              重试前会校验它，作废的旧轮次不再重试；传 null 则内部自取新序号。
     */
    suspend fun call(text: String, token: Long?): AiOutcome {
        if (!AppPrefs.aiEnabled) return AiOutcome.Err("AI 改写未开启")
        val key = AppPrefs.aiApiKey.trim()
        if (key.isEmpty()) return AiOutcome.Err("未配置 API 密钥")
        val raw = text.trim()
        if (raw.isEmpty()) return AiOutcome.Err("原文为空")
        if (raw.length > 4000) return AiOutcome.Err("原文过长（>${raw.length} 字），请缩短后再试")
        val t = token ?: beginToken()
        val outcome = withContext(Dispatchers.IO) { doCallWithRetry(raw, key, t) }
        AiHistory.record(raw, outcome)
        return outcome
    }

    /**
     * 网络类临时故障（超时 / IO 异常 / 5xx 网关）自动重试一次：
     * 手动点悬浮球后被"请求超时"打断的体验很差，而这类故障重试命中率很高；
     * 4xx（密钥错/模型名错）不重试——重试也一样失败，白等两倍超时；
     * 重试前校验触发序号：本轮已作废（期间有了更新的手动触发）就不再重试，省一次白等的请求。
     */
    private fun doCallWithRetry(text: String, key: String, token: Long): AiOutcome {
        val first = doCall(text, key)
        if (first !is AiOutcome.Err || !first.retryable) return first
        if (!isCurrent(token)) return first
        val second = doCall(text, key)
        return when (second) {
            is AiOutcome.Ok -> second
            is AiOutcome.Err -> AiOutcome.Err(
                if (second.retryable) "${second.message}（已自动重试 1 次仍失败）" else second.message,
                retryable = false
            )
        }
    }

    /** 阻塞式调用（必须在 Dispatchers.IO 上跑）：请求构造 → 发送 → 解析 */
    private fun doCall(text: String, key: String): AiOutcome {
        val started = System.currentTimeMillis()
        val persona = AiPersonas.pick()
        val timeoutSec = try { AppPrefs.aiTimeoutSec } catch (_: Throwable) { 20 }.coerceIn(3, 120)
        val conn: HttpURLConnection
        try {
            conn = URL(normalizeUrl(AppPrefs.aiBaseUrl)).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = timeoutSec * 1000
            conn.doOutput = true
            conn.doInput = true
            conn.useCaches = false
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $key")
        } catch (t: Throwable) {
            return AiOutcome.Err("接口地址无效：${t.message ?: t.javaClass.simpleName}")
        }
        try {
            val model = AppPrefs.aiModel.trim().ifEmpty { "deepseek-chat" }
            val body = JSONObject().apply {
                put("model", model)
                put("stream", false)
                put("temperature", 0.8)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", persona.prompt))
                    put(JSONObject().put("role", "user").put("content", text))
                })
            }
            conn.outputStream.use { os ->
                os.write(body.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }
            val code = conn.responseCode
            val respText = try {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    .orEmpty()
            } catch (_: Throwable) { "" }

            if (code !in 200..299) {
                // 错误响应尽量提取 OpenAI 风格的 error.message（401 密钥错 / 429 限流 / 404 模型名错等）
                val msg = try {
                    val e = JSONObject(respText).optJSONObject("error")
                    val m = e?.optString("message").orEmpty()
                    if (m.isNotEmpty()) m else respText.take(300).ifEmpty { "（无响应体）" }
                } catch (_: Throwable) {
                    respText.take(300).ifEmpty { "（无响应体）" }
                }
                val hint = when (code) {
                    401 -> "（API 密钥无效或未授权）"
                    404 -> "（接口地址或模型名可能填错了）"
                    429 -> "（请求过于频繁或额度不足）"
                    else -> ""
                }
                // 5xx 是网关/服务端临时故障，值得重试；4xx 是配置错误，重试无意义
                return AiOutcome.Err("HTTP $code：$msg$hint", retryable = code in 500..599)
            }

            val content = try {
                val root = JSONObject(respText)
                val choice = root.optJSONArray("choices")?.optJSONObject(0)
                choice?.optJSONObject("message")?.optString("content").orEmpty()
            } catch (_: Throwable) { "" }
            if (content.isBlank()) {
                return AiOutcome.Err("响应解析失败：接口没有返回改写文本（可能不是 OpenAI 兼容接口）")
            }
            val cleaned = cleanResult(content)
            if (cleaned.isEmpty()) {
                return AiOutcome.Err("改写结果清洗后为空")
            }
            return AiOutcome.Ok(cleaned, persona.name, System.currentTimeMillis() - started)
        } catch (t: Throwable) {
            val msg = when (t) {
                is SocketTimeoutException -> "请求超时（${timeoutSec}s），可在设置里调大超时秒数"
                is IOException -> "网络错误：${t.message ?: t.javaClass.simpleName}"
                else -> "${t.javaClass.simpleName}: ${t.message ?: ""}"
            }
            // 超时与网络抖动属于临时故障 → 允许自动重试一次
            return AiOutcome.Err(msg, retryable = t is SocketTimeoutException || t is IOException)
        } finally {
            try { conn.disconnect() } catch (_: Throwable) { }
        }
    }

    /**
     * 地址规范化（对齐 OpenAI SDK base_url 语义）：
     * - 无协议头自动补 https://；去尾部斜杠；
     * - 不以 /chat/completions 结尾则补全（填 https://api.deepseek.com/v1 或完整 endpoint 都行）。
     */
    private fun normalizeUrl(raw: String): String {
        var u = raw.trim()
        if (u.isEmpty()) u = "https://api.deepseek.com/v1"
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        u = u.trimEnd('/')
        if (!u.endsWith("/chat/completions")) u = "$u/chat/completions"
        return u
    }

    /**
     * 清洗模型输出：去首尾空白 → 剥引导词（"改写：/改写后：/结果："）→
     * 剥一层包裹引号（中英皆可）→ 长度截断到 4000（写回输入框的兜底上限）。
     */
    private fun cleanResult(raw: String): String {
        var t = raw.trim()
        // 引导词：仅剥行首一次，避免误伤正文里正常的词
        val labelRegex = Regex("^(改写|改写后|结果|输出)[:：]\\s*", RegexOption.IGNORE_CASE)
        t = t.replaceFirst(labelRegex, "").trim()
        // 包裹引号：成对出现才剥
        val quotePairs = listOf(
            "\"" to "\"", "“" to "”", "「" to "」", "『" to "』", "《" to "》", "‘" to "’"
        )
        for ((l, r) in quotePairs) {
            if (t.length >= 2 && t.startsWith(l) && t.endsWith(r)) {
                t = t.substring(1, t.length - 1).trim()
                break
            }
        }
        if (t.length > 4000) t = t.take(4000)
        return t
    }
}
