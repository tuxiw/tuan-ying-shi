package com.example.tuanyingshi.util.source_rule

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 迷你 JsonPath 求值器。
 *
 * 对应 animeko `me.him188.ani.utils.jsonpath.JsonPath` 的常用子集，用于
 * `subjectFormatId = "json-path-indexed"` 的搜索接口。支持语法：
 * - `$`：根；
 * - `[*]`：当前节点的所有子节点（数组元素 / 对象所有值）；
 * - `['url', 'link']`：按名称取值（逗号分隔表示「任一命中」，与 animeko 一致）；
 * - `.title`：按名称取值；
 * - `[0]`：按下标取数组元素。
 *
 * 以上片段可任意拼接，例如 `$[*]['title','name']`、`$.data[*].url`。
 *
 * @return 命中的节点列表；路径语法非法时返回 `null`（区别于「合法但没命中」的空列表）。
 */
object MiniJsonPath {

    fun resolve(root: JsonElement, path: String): List<JsonElement>? {
        var rest = path.trim()
        if (!rest.startsWith("$")) return null
        rest = rest.removePrefix("$")

        var current: List<JsonElement> = listOf(root)
        while (rest.isNotEmpty()) {
            when {
                rest.startsWith("[") -> {
                    val end = rest.indexOf(']')
                    if (end < 0) return null
                    val inner = rest.substring(1, end).trim()
                    rest = rest.substring(end + 1)
                    current = current.flatMap { el -> applyBracket(el, inner) }
                }

                rest.startsWith(".") -> {
                    rest = rest.removePrefix(".")
                    val key = Regex("^[^.\\[]+").find(rest)?.value ?: return null
                    rest = rest.substring(key.length)
                    current = current.flatMap { selectKey(it, key) }
                }

                else -> return null
            }
        }
        return current
    }

    /** 取一个节点的人类可读字符串值：数组/对象取第一个标量的值。 */
    fun firstString(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }
        is JsonArray -> element.firstOrNull()?.let { firstString(it) }
        is JsonObject -> element.values.firstOrNull()?.let { firstString(it) }
        else -> null
    }

    private fun applyBracket(element: JsonElement, inner: String): List<JsonElement> {
        if (inner == "*") return children(element)

        if (inner.startsWith("'") || inner.startsWith("\"")) {
            return inner.split(",")
                .mapNotNull { it.trim().removeSurrounding("'").removeSurrounding("\"").trim().takeIf { k -> k.isNotEmpty() } }
                .flatMap { key -> selectKey(element, key) }
        }

        val index = inner.toIntOrNull()
        if (index != null) return listOfNotNull(children(element).getOrNull(index))

        return emptyList()
    }

    private fun children(element: JsonElement): List<JsonElement> = when (element) {
        is JsonArray -> element
        is JsonObject -> element.values.toList()
        else -> emptyList()
    }

    private fun selectKey(element: JsonElement, key: String): List<JsonElement> = when (element) {
        is JsonObject -> listOfNotNull(element[key])
        is JsonArray -> element.flatMap { selectKey(it, key) }
        else -> emptyList()
    }
}
