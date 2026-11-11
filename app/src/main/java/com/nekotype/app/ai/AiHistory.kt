package com.nekotype.app.ai

import com.nekotype.app.prefs.AppPrefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 改写历史：最近 N 条「原文 → 改写后」的本地记录（含失败原因），供设置页回看效果。
 *
 * 设计：
 * - 只存本机 SharedPreferences，不参与配置导出（含聊天内容，属敏感数据）；
 * - 每条原文/结果都截断到 500 字，20 条上限 → 存储占用有硬上限（约几十 KB）；
 * - 记录点在 AiRewriter.call()：手动悬浮球改写与设置页「测试」都会走这里，
 *   两处产生的真实 HTTP 调用都会被记录；未开启/未配密钥等前置拦截不记录（不是真实尝试）；
 * - 任何异常静默吞掉：历史记录绝不能影响改写主流程。
 */
object AiHistory {

    private const val MAX = 20

    /** 一条历史：persona=使用的人设名；ok=false 时 out 存失败原因 */
    data class Entry(
        val time: Long,
        val persona: String,
        val ok: Boolean,
        val src: String,
        val out: String
    )

    /** 记录一次真实调用结果（最新在前，超上限淘汰最旧） */
    fun record(src: String, outcome: AiRewriter.AiOutcome) {
        try {
            val e = when (outcome) {
                is AiRewriter.AiOutcome.Ok -> Entry(
                    System.currentTimeMillis(), outcome.personaName, true,
                    src.take(500), outcome.text.take(500)
                )
                is AiRewriter.AiOutcome.Err -> Entry(
                    System.currentTimeMillis(),
                    try { AiPersonas.pick().name } catch (_: Throwable) { "" },
                    false,
                    src.take(500), outcome.message.take(500)
                )
            }
            val list = load().toMutableList()
            list.add(0, e)
            while (list.size > MAX) list.removeAt(list.size - 1)
            val arr = JSONArray()
            list.forEach {
                arr.put(
                    JSONObject()
                        .put("t", it.time)
                        .put("p", it.persona)
                        .put("ok", it.ok)
                        .put("s", it.src)
                        .put("o", it.out)
                )
            }
            AppPrefs.aiHistoryJson = arr.toString()
        } catch (_: Throwable) { }
    }

    /** 读取全部历史（时间倒序：最新在前） */
    fun load(): List<Entry> = try {
        val arr = JSONArray(AppPrefs.aiHistoryJson)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Entry(
                o.optLong("t"),
                o.optString("p"),
                o.optBoolean("ok"),
                o.optString("s"),
                o.optString("o")
            )
        }
    } catch (_: Throwable) {
        emptyList()
    }

    /** 一键清空 */
    fun clear() {
        AppPrefs.aiHistoryJson = ""
    }

    /** 设置页行内摘要 */
    fun summary(): String = "${load().size} 条"
}
