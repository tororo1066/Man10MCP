package tororo1066.man10mcp.server.tools

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.*
import tororo1066.tororopluginapi.SJavaPlugin
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class LatestLog: AbstractTool() {

    override fun definition(): Tool {
        return Tool(
            name = "mc_server_latest_log",
            description = "Get latest.log file",
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("rows") {
                        put("type", "number")
                        put("description", "Number of rows to display")
                    }
                    putJsonObject("after_time") {
                        put("type", "string")
                        put("description", "Filter logs after this time (format: HH:mm:ss)")
                    }
                }
            )
        )
    }

    override fun process(request: CallToolRequest): CallToolResult {
        val file = File(SJavaPlugin.Companion.plugin.dataFolder.parentFile.parentFile, "logs/latest.log")
        if (!file.exists()) {
            return CallToolResult(
                content = listOf(TextContent(text = "File not found: ${file.absolutePath}"))
            )
        }
        val lines = file.readLines()

        val rows = request.arguments["rows"]?.jsonPrimitive?.intOrNull ?: 10
        if (rows <= 0) {
            return CallToolResult(
                content = listOf(TextContent(text = "Invalid number of rows: $rows"))
            )
        }
        val afterTimeStr = request.arguments["after_time"]?.jsonPrimitive?.contentOrNull

        val afterTime = afterTimeStr?.let {
            runCatching {
                LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm:ss"))
            }.getOrNull()
        }

        val filteredLines = if (afterTime != null) {
            val regex = Regex("""\[(\d{2}:\d{2}:\d{2})\s\w+]""") // [HH:mm:ss INFO]
            lines.filter { line ->
                val matchResult = regex.find(line)
                val timestamp = matchResult?.groups?.get(1)?.value ?: return@filter false
                runCatching {
                    LocalTime.parse(timestamp, DateTimeFormatter.ofPattern("HH:mm:ss"))
                }.getOrNull()?.isAfter(afterTime) == true
            }
        } else {
            lines
        }

        val start = (filteredLines.size - rows).coerceAtLeast(0)
        val end = filteredLines.size
        val logLines = filteredLines.subList(start, end).joinToString("\n")
        return CallToolResult(
            content = listOf(TextContent(text = logLines))
        )
    }
}