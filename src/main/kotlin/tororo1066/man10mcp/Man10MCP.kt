package tororo1066.man10mcp

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.util.collections.ConcurrentMap
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.*
import tororo1066.man10mcp.server.resources.AbstractResource
import tororo1066.man10mcp.server.tools.AbstractTool
import tororo1066.tororopluginapi.SJavaPlugin
import java.io.File
import java.util.jar.JarFile

class Man10MCP: SJavaPlugin() {

    private val classes by lazy {
        val classes = mutableListOf<Class<*>>()
        val file = File(this.javaClass.protectionDomain.codeSource.location.toURI())
        val jar = JarFile(file)
        jar.stream().filter { entry ->
            entry.name.endsWith(".class") && entry.name.startsWith("tororo1066/man10mcp/")
        }.forEach { entry ->
            val className = entry.name.replace("/", ".").removeSuffix(".class")
            runCatching {
                classes.add(Class.forName(className))
            }
        }
        classes.toList()
    }
    lateinit var sseServer: EmbeddedServer<*,*>

    override fun onStart() {

        //http://localhost:3001
        val servers = ConcurrentMap<String, Server>()
        sseServer = embeddedServer(CIO, host = "0.0.0.0", port = 3001) {
            install(SSE)
            routing {
                sse("/sse") {
                    val transport = SseServerTransport("/message", this)
                    val server = createServer()

                    servers[transport.sessionId] = server

                    server.onClose {
                        servers.remove(transport.sessionId)
                    }

                    server.connect(transport)
                }

                post("/message") {
                    val sessionId: String = call.request.queryParameters["sessionId"]!!
                    val transport = servers[sessionId]?.transport as? SseServerTransport
                    if (transport == null) {
                        call.respond(HttpStatusCode.NotFound, "Session not found")
                        return@post
                    }

                    transport.handlePostMessage(call)
                }
            }
        }.start(wait = false)
    }

    override fun onEnd() {
        sseServer.stop(0, 0)
    }

    private fun createServer(): Server {
        val server = Server(
            Implementation(
                name = "Minecraft Server",
                version = "1.0.0"
            ),
            ServerOptions(
                capabilities = ServerCapabilities(
                    prompts = ServerCapabilities.Prompts(listChanged = true),
                    resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                    tools = ServerCapabilities.Tools(listChanged = true),
                )
            )
        )
        server.addResources(getResources())
        server.addTools(getTools())
        return server
    }

    private fun getResources(): List<RegisteredResource> {
        return classes.filter { it.superclass == AbstractResource::class.java }
            .mapNotNull { it.getDeclaredConstructor().newInstance() as? AbstractResource }
            .map { resource ->
                resource.getRegisteredResource()
            }
    }

    private fun getTools(): List<RegisteredTool> {
        return classes.filter { it.superclass == AbstractTool::class.java }
            .mapNotNull { it.getDeclaredConstructor().newInstance() as? AbstractTool }
            .map { tool ->
                tool.getRegisteredTool()
            }
    }



}