package com.fsk.threadwire.tools.fakesse

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.delay

/**
 * Minimal local server for manually validating [com.fsk.threadwire.transport.SseChatTransport]
 * against a real HTTP connection - not shipped with `:core`, not a production dependency.
 * See `docs/design-doc.md` §4.3/§4.4 for the event sequence each scenario plays back, and
 * `README.md` for how to run it and which message keyword triggers which scenario.
 *
 * `POST /chat` with no `Last-Event-ID` header streams a scenario selected by keyword-matching
 * the request body's `"message"` field (see [selectScenario]); `POST /chat` with a
 * `Last-Event-ID` header resumes that same scenario from the next frame. This works across
 * resumes because [com.fsk.threadwire.transport.SseChatTransport] resends the exact same
 * request body (including the original message) on every reconnect attempt - only the
 * `Last-Event-ID` header changes - so scenario selection is always deterministic and
 * consistent between the fresh attempt and every resumed one.
 */

private data class ScriptedFrame(val id: Int, val data: String)

private data class Scenario(
    val name: String,
    val keyword: String?,
    val frames: List<ScriptedFrame>,
    val dropAfterFrameId: Int? = null,
)

private val happyPathScenario = Scenario(
    name = "happy-path",
    keyword = null,
    frames = listOf(
        ScriptedFrame(1, """{"type":"text-start","id":"msg_1"}"""),
        ScriptedFrame(2, """{"type":"text-delta","id":"msg_1","delta":"Hi there! "}"""),
        ScriptedFrame(3, """{"type":"text-delta","id":"msg_1","delta":"How can I help you today?"}"""),
        ScriptedFrame(4, """{"type":"text-end","id":"msg_1"}"""),
        ScriptedFrame(5, """{"type":"finish"}"""),
    ),
)

// Unchanged from the original single-script version - only renamed/keyed by "reconnect".
private val reconnectScenario = Scenario(
    name = "reconnect",
    keyword = "reconnect",
    frames = listOf(
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
    ),
    dropAfterFrameId = 4,
)

private val errorScenario = Scenario(
    name = "error",
    keyword = "error",
    frames = listOf(
        ScriptedFrame(1, """{"type":"text-start","id":"msg_1"}"""),
        ScriptedFrame(2, """{"type":"text-delta","id":"msg_1","delta":"Let me check that for you..."}"""),
        // An error event alone doesn't end the stream at the transport level - a dropped
        // connection right after it, with no `finish`, is indistinguishable from a raw
        // mid-stream drop, so a real BFF (and this scenario) must still send `finish`.
        ScriptedFrame(3, """{"type":"error","code":"UPSTREAM_TIMEOUT","message":"The upstream model timed out. Please try again."}"""),
        ScriptedFrame(4, """{"type":"finish"}"""),
    ),
)

private val handoffScenario = Scenario(
    name = "handoff",
    keyword = "handoff",
    frames = listOf(
        ScriptedFrame(1, """{"type":"text-start","id":"msg_1"}"""),
        ScriptedFrame(2, """{"type":"text-delta","id":"msg_1","delta":"Connecting you to a human agent now."}"""),
        ScriptedFrame(3, """{"type":"text-end","id":"msg_1"}"""),
        ScriptedFrame(4, """{"type":"handoff-start","reason":"user_requested"}"""),
        ScriptedFrame(5, """{"type":"handoff-agent-joined","agentName":"Maria"}"""),
        ScriptedFrame(6, """{"type":"handoff-end","reason":"agent_resolved"}"""),
        ScriptedFrame(7, """{"type":"finish"}"""),
    ),
)

// Deliberately tall reply: a single "long" send overflows the viewport by itself, so
// scroll / jump-to-latest / auto-follow can be exercised in ONE turn instead of five.
// Streamed in sentence-sized chunks (not word-by-word) so it fills the screen in a few
// seconds, and it uses light Markdown (heading, bold/italic/code, a bulleted list) to
// exercise the markdown renderer at the same time.
private val longScenario = Scenario(
    name = "long",
    keyword = "long",
    frames = buildList {
        add(ScriptedFrame(1, """{"type":"text-start","id":"msg_1"}"""))
        val chunks = listOf(
            "## Streaming stress-test\n\n",
            "This is a **deliberately long** streaming reply used to manually stress-test how the UI handles a tall message that overflows the viewport in a single turn. ",
            "It is intentionally verbose so *one* send is enough to fill the screen and exercise scrolling, the jump-to-latest button, and auto-follow.\n\n",
            "Here are a few things it covers:\n",
            "- Incremental text deltas arriving over a real connection\n",
            "- Markdown rendering: **bold**, *italic*, and `inline code`\n",
            "- A bulleted list that adds vertical height\n",
            "- Multiple paragraphs so the bubble grows well past the fold\n\n",
            "The quick brown fox jumps over the lazy dog. ",
            "The quick brown fox jumps over the lazy dog, again, to add a little more height. ",
            "Pack my box with five dozen liquor jugs. ",
            "How vexingly quick daft zebras jump!\n\n",
            "When this reply finishes, the view should be parked at the very bottom with the last line fully visible - not cut off mid-sentence. ",
            "Scroll up and the jump-to-latest button should appear; tap it and it should return all the way back down to here.\n\n",
            "That's the end of the long streaming reply.",
        )
        chunks.forEachIndexed { index, chunk ->
            val escaped = chunk.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            add(ScriptedFrame(2 + index, """{"type":"text-delta","id":"msg_1","delta":"$escaped"}"""))
        }
        add(ScriptedFrame(2 + chunks.size, """{"type":"text-end","id":"msg_1"}"""))
        add(ScriptedFrame(3 + chunks.size, """{"type":"finish"}"""))
    },
)

// Checked in this order; first keyword match wins, "happy-path" is the fallback.
private val scenariosByPriority = listOf(reconnectScenario, errorScenario, handoffScenario, longScenario)

private val messageFieldRegex = Regex(""""message"\s*:\s*"((?:[^"\\]|\\.)*)"""")

private fun selectScenario(rawBody: String): Scenario {
    val message = (messageFieldRegex.find(rawBody)?.groupValues?.get(1) ?: rawBody).lowercase()
    return scenariosByPriority.firstOrNull { it.keyword != null && message.contains(it.keyword) } ?: happyPathScenario
}

private const val PORT = 8080

fun main() {
    val server = embeddedServer(CIO, port = PORT) {
        routing {
            post("/chat") {
                // Must read the body before starting the streamed response - Ktor requires
                // consuming the request before the response begins.
                val body = call.receiveText()
                val scenario = selectScenario(body)
                val lastEventId = call.request.header("Last-Event-ID")?.toIntOrNull()
                val framesToSend = if (lastEventId == null) {
                    println("[fake-sse-server] scenario=${scenario.name} - fresh request" + (scenario.dropAfterFrameId?.let { " - will drop after frame id=$it" } ?: ""))
                    scenario.dropAfterFrameId?.let { drop -> scenario.frames.filter { it.id <= drop } } ?: scenario.frames
                } else {
                    println("[fake-sse-server] scenario=${scenario.name} - resuming after Last-Event-ID=$lastEventId")
                    scenario.frames.filter { it.id > lastEventId }
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
    println("[fake-sse-server] scenarios: happy-path (default), reconnect, error, handoff, long - trigger by including the keyword in the sent message")
    server.start(wait = true)
}
