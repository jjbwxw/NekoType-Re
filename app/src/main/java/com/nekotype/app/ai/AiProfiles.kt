package com.nekotype.app.ai

import com.nekotype.app.prefs.AppPrefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 服务商档案：把「接口地址 + API 密钥 + 模型名」存成命名快照，一键切换服务商。
 *
 * 设计：
 * - 生效值始终是 AppPrefs.aiBaseUrl / aiApiKey / aiModel 三个既有字段——
 *   AiRewriter / AiPersonas 完全不用感知档案的存在，切档案 = 把快照写回这三个字段；
 * - 档案含 API 密钥 → 仅存本机 SharedPreferences，刻意不参与配置导出（防剪贴板泄露）；
 * - 同名档案重复保存 = 覆盖旧值（把同名当 upsert 处理，最符合直觉）。
 */
object AiProfiles {

    /** 一份服务商档案快照 */
    data class AiProfile(val name: String, val baseUrl: String, val apiKey: String, val model: String)

    /** 读取全部档案（JSON 解析失败一律返回空列表，不让坏数据炸界面） */
    fun load(): List<AiProfile> = try {
        val arr = JSONArray(AppPrefs.aiProfilesJson)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val n = o.optString("name").trim()
            if (n.isEmpty()) null
            else AiProfile(n, o.optString("baseUrl"), o.optString("apiKey"), o.optString("model"))
        }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun save(list: List<AiProfile>) {
        try {
            val arr = JSONArray()
            list.forEach { p ->
                arr.put(
                    JSONObject()
                        .put("name", p.name)
                        .put("baseUrl", p.baseUrl)
                        .put("apiKey", p.apiKey)
                        .put("model", p.model)
                )
            }
            AppPrefs.aiProfilesJson = arr.toString()
        } catch (_: Throwable) { }
    }

    /** 把当前生效的 接口/密钥/模型 存为一个新档案（同名覆盖） */
    fun addCurrent(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val rest = load().filter { it.name != n }
        save(rest + AiProfile(n, AppPrefs.aiBaseUrl, AppPrefs.aiApiKey, AppPrefs.aiModel))
        AppPrefs.aiActiveProfileName = n
    }

    /** 应用档案：把快照写进当前生效三项 */
    fun apply(p: AiProfile) {
        AppPrefs.aiBaseUrl = p.baseUrl
        AppPrefs.aiApiKey = p.apiKey
        AppPrefs.aiModel = p.model
        AppPrefs.aiActiveProfileName = p.name
        try {
            com.nekotype.app.util.NekoLog.adjust("切换 AI 服务商档案：${p.name}（${p.model}）")
        } catch (_: Throwable) { }
    }

    /** 删除档案；若删的是当前生效档案，清掉生效名（三项值保持不变，防误删后丢配置） */
    fun delete(name: String) {
        save(load().filter { it.name != name })
        if (AppPrefs.aiActiveProfileName == name) AppPrefs.aiActiveProfileName = ""
    }

    /** 设置页行内摘要 */
    fun summary(): String {
        val list = load()
        if (list.isEmpty()) return "未创建"
        val cur = AppPrefs.aiActiveProfileName
        return if (cur.isNotEmpty() && list.any { it.name == cur }) {
            "${list.size} 个（当前：$cur）"
        } else {
            "${list.size} 个"
        }
    }
}
