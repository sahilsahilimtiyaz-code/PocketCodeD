package com.example.middleware

import com.example.model.ToolFunctionSpec
import com.example.model.ToolPropertySpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dynamically converts OpenAI / DeepSeek `tools` and `functions` JSON schemas into
 * strict `llama.cpp` GBNF (GGML BNF) grammar production rules.
 *
 * When a 1.5B–3B parameter model generates a tool call under this GBNF grammar,
 * the token sampler masks out any token that would violate the JSON schema,
 * guaranteeing 100% syntactically valid JSON tool invocations for `opencode` and Aider.
 */
object GbnfGrammarCompiler {

    /**
     * Extracts structured [ToolFunctionSpec] definitions from an OpenAI `/v1/chat/completions` JSON request.
     */
    fun parseToolsFromRequest(requestJson: JSONObject): List<ToolFunctionSpec> {
        val result = mutableListOf<ToolFunctionSpec>()
        val toolsArray = requestJson.optJSONArray("tools")
        if (toolsArray != null) {
            for (i in 0 until toolsArray.length()) {
                val item = toolsArray.optJSONObject(i) ?: continue
                val fnObj = item.optJSONObject("function") ?: item
                parseSingleFunctionObject(fnObj)?.let { result.add(it) }
            }
        }
        val legacyFunctions = requestJson.optJSONArray("functions")
        if (legacyFunctions != null && result.isEmpty()) {
            for (i in 0 until legacyFunctions.length()) {
                val fnObj = legacyFunctions.optJSONObject(i) ?: continue
                parseSingleFunctionObject(fnObj)?.let { result.add(it) }
            }
        }
        return result
    }

    private fun parseSingleFunctionObject(fnObj: JSONObject): ToolFunctionSpec? {
        val name = fnObj.optString("name", "").trim()
        if (name.isEmpty()) return null
        val description = fnObj.optString("description", "")
        val paramsObj = fnObj.optJSONObject("parameters") ?: JSONObject()

        val requiredList = mutableListOf<String>()
        val reqArray = paramsObj.optJSONArray("required")
        if (reqArray != null) {
            for (i in 0 until reqArray.length()) {
                val reqName = reqArray.optString(i, "")
                if (reqName.isNotEmpty()) requiredList.add(reqName)
            }
        }

        val propsMap = linkedMapOf<String, ToolPropertySpec>()
        val propsObj = paramsObj.optJSONObject("properties")
        if (propsObj != null) {
            val keys = propsObj.keys()
            while (keys.hasNext()) {
                val propKey = keys.next()
                val propSchema = propsObj.optJSONObject(propKey) ?: continue
                val type = propSchema.optString("type", "string")
                val propDesc = propSchema.optString("description", "")
                val enums = mutableListOf<String>()
                val enumArr = propSchema.optJSONArray("enum")
                if (enumArr != null) {
                    for (eIdx in 0 until enumArr.length()) {
                        enums.add(enumArr.optString(eIdx))
                    }
                }
                propsMap[propKey] = ToolPropertySpec(
                    name = propKey,
                    type = type,
                    description = propDesc,
                    enumValues = enums
                )
            }
        }

        return ToolFunctionSpec(
            name = name,
            description = description,
            parametersSchemaJson = paramsObj.toString(),
            requiredFields = requiredList,
            properties = propsMap
        )
    }

    /**
     * Compiles a list of [ToolFunctionSpec] schemas into a complete `llama.cpp` GBNF grammar string.
     */
    fun compileToolsToGbnf(tools: List<ToolFunctionSpec>): String {
        if (tools.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("# PocketCodeD Dynamic GBNF Grammar for OpenAI/opencode Tool Calling")
        val toolRuleNames = mutableListOf<String>()

        for ((index, tool) in tools.withIndex()) {
            val rulePrefix = "tool_${index}_${sanitizeRuleName(tool.name)}"
            toolRuleNames.add(rulePrefix)
        }

        // Root production allows any of the declared tool call envelopes
        sb.appendLine("root ::= ws (${toolRuleNames.joinToString(" | ")}) ws")
        sb.appendLine()

        for ((index, tool) in tools.withIndex()) {
            val rulePrefix = toolRuleNames[index]
            val argsRuleName = "${rulePrefix}_args"
            sb.appendLine(
                """$rulePrefix ::= "{" ws "\"tool\"" ws ":" ws "\"${escapeGbnfLiteral(tool.name)}\"" ws "," ws "\"arguments\"" ws ":" ws $argsRuleName ws "}""""
            )

            if (tool.properties.isEmpty()) {
                sb.appendLine("""$argsRuleName ::= json-object""")
            } else {
                val propRules = mutableListOf<String>()
                for ((propName, propSpec) in tool.properties) {
                    val propRuleId = "${rulePrefix}_prop_${sanitizeRuleName(propName)}"
                    val valueRule = buildValueRuleForProperty(propSpec)
                    sb.appendLine(
                        """$propRuleId ::= "\"${escapeGbnfLiteral(propName)}\"" ws ":" ws $valueRule"""
                    )
                    propRules.add(propRuleId)
                }
                val joinedProps = propRules.joinToString(""" ws "," ws """)
                sb.appendLine("""$argsRuleName ::= "{" ws $joinedProps ws "}"""")
            }
            sb.appendLine()
        }

        // Standard GBNF primitive terminals matching llama.cpp json.gbnf conventions
        sb.appendLine("""json-value ::= json-object | json-array | string | number | boolean | "null"""")
        sb.appendLine("""json-object ::= "{" ws (string ws ":" ws json-value (ws "," ws string ws ":" ws json-value)*)? ws "}"""")
        sb.appendLine("""json-array ::= "[" ws (json-value (ws "," ws json-value)*)? ws "]"""")
        sb.appendLine("""string ::= "\"" ([^"\\\x7F\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4}))* "\"" ws""")
        sb.appendLine("""number ::= ("-"? ([0-9] | [1-9] [0-9]{0,15})) ("." [0-9]+)? ([eE] [-+]? [0-9] [1-9]{0,15})? ws""")
        sb.appendLine("""integer ::= ("-"? ([0-9] | [1-9] [0-9]{0,15})) ws""")
        sb.appendLine("""boolean ::= ("true" | "false") ws""")
        sb.appendLine("""ws ::= | " " | "\n" [ \t]{0,20}""")

        return sb.toString().trim()
    }

    private fun buildValueRuleForProperty(prop: ToolPropertySpec): String {
        if (prop.enumValues.isNotEmpty()) {
            val literals = prop.enumValues.joinToString(" | ") { "\"\\\"${escapeGbnfLiteral(it)}\\\"\"" }
            return "($literals)"
        }
        return when (prop.type.lowercase()) {
            "string" -> "string"
            "integer" -> "integer"
            "number" -> "number"
            "boolean" -> "boolean"
            "array" -> "json-array"
            "object" -> "json-object"
            else -> "json-value"
        }
    }

    /**
     * Validates that a generated JSON tool call string satisfies the compiled GBNF tool envelope.
     */
    fun validateJsonAgainstGrammar(jsonCandidate: String, compiledGbnf: String): Boolean {
        return try {
            val obj = JSONObject(jsonCandidate)
            val toolName = obj.optString("tool", "")
            val args = obj.optJSONObject("arguments")
            toolName.isNotEmpty() && args != null && compiledGbnf.contains(toolName)
        } catch (e: Exception) {
            false
        }
    }

    private fun sanitizeRuleName(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun escapeGbnfLiteral(raw: String): String =
        raw.replace("\\", "\\\\").replace("\"", "\\\"")
}
