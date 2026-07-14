package com.fsk.threadwire.tools.fakesse

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.delay

/**
 * Minimal local server for manually validating [com.fsk.threadwire.transport.SseChatTransport]
 * against a real HTTP connection - not shipped with `:core`, not a production dependency.
 * See `docs/design-doc.md` §4.3/§4.4 for the event sequence this plays back, and
 * `CONTRIBUTING.md`/`README.md` for how to run it.
 *
 * `POST /chat` with no `Last-Event-ID` header streams the full scripted sequence below,
 * but deliberately drops the connection after [DROP_AFTER_FRAME_ID] (no `finish` frame) to
 * force a real client into reconnecting. `POST /chat` with a `Last-Event-ID` header resumes
 * from the next frame through to `finish`.
 */

private data class ScriptedFrame(val id: Int, val data: String)

private val script = listOf(
    ScriptedFrame(1, """{"type":"card-start","id":"card_123","version":1}"""),
    ScriptedFrame(2, """{"type":"text-start","id":"msg_1"}"""),
    ScriptedFrame(3, """{"type":"text-delta","id":"msg_1","delta":"Confirm the pending payment?"}"""),
    ScriptedFrame(4, """{"type":"text-end","id":"msg_1"}"""),
    ScriptedFrame(5, """{"type":"tool-input-start","toolCallId":"call_1"}"""),
    ScriptedFrame(6, """{"type":"tool-input-available","toolCallId":"call_1","input":{"amount":120}}"""),
    ScriptedFrame(7, """{"type":"card-update","id":"card_123","body":{"status":"processing"}}"""),
    ScriptedFrame(8, """{"type":"handoff-start","reason":"user_requested"}"""),
    ScriptedFrame(9, """{"type":"handoff-agent-joined","agentName":"Maria"}"""),
    ScriptedFrame(10, """{"type":"handoff-end","reason":"agent_resolved"}"""),
    ScriptedFrame(11, """{"type":"finish"}"""),
)

private const val DROP_AFTER_FRAME_ID = 4
private const val PORT = 8080

fun main() {
    val server = embeddedServer(CIO, port = PORT) {
        routing {
            post("/chat") {
                val lastEventId = call.request.header("Last-Event-ID")?.toIntOrNull()
                val framesToSend = if (lastEventId == null) {
                    println("[fake-sse-server] fresh request - will drop after frame id=$DROP_AFTER_FRAME_ID")
                    script.filter { it.id <= DROP_AFTER_FRAME_ID }
                } else {
                    println("[fake-sse-server] resuming after Last-Event-ID=$lastEventId")
                    script.filter { it.id > lastEventId }
                }

                call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                    for (frame in framesToSend) {
                        val line = "id: ${frame.id}\ndata: ${frame.data}\n\n"
                        println("[fake-sse-server] -> ${line.trimEnd()}")
                        writeStringUtf8(line)
                        flush()
                        delay(200)
                    }
                }
            }
        }
    }
    println("[fake-sse-server] starting on :$PORT ...")
    server.start(wait = true)
}
