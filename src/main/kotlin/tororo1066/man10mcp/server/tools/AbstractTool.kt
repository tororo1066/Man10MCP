package tororo1066.man10mcp.server.tools

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool

abstract class AbstractTool {

    abstract fun definition(): Tool

    abstract fun process(request: CallToolRequest): CallToolResult

    fun getRegisteredTool(): RegisteredTool {
        return RegisteredTool(
            tool = definition(),
            handler = { request ->
                process(request)
            }
        )
    }
}