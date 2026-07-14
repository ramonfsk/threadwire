package com.fsk.threadwire.protocol

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * One case per literal event type (design doc §4.3/§4.4), plus the dynamic `data-*`
 * custom part and the forward-compatible fallback paths. Reconciliation of repeated
 * IDs (e.g. a later `card-update` replacing an earlier one) is intentionally NOT
 * tested here - that's the future session state machine's job (M1), not this parser's;
 * this file only verifies raw, event-by-event decoding.
 */
class ChatEventParserTest {

    @Test
    fun parsesTextStart() {
        val event = ChatEventParser.parse("""{"type":"text-start","id":"msg_1"}""")
        assertEquals(ChatEvent.TextStart(id = "msg_1"), event)
    }

    @Test
    fun parsesTextDelta() {
        val event = ChatEventParser.parse("""{"type":"text-delta","id":"msg_1","delta":"Hello"}""")
        assertEquals(ChatEvent.TextDelta(id = "msg_1", delta = "Hello"), event)
    }

    @Test
    fun parsesTextEnd() {
        val event = ChatEventParser.parse("""{"type":"text-end","id":"msg_1"}""")
        assertEquals(ChatEvent.TextEnd(id = "msg_1"), event)
    }

    @Test
    fun parsesToolInputStart() {
        val event = ChatEventParser.parse("""{"type":"tool-input-start","toolCallId":"call_1"}""")
        assertEquals(ChatEvent.ToolInputStart(toolCallId = "call_1"), event)
    }

    @Test
    fun parsesToolInputDelta() {
        val event = ChatEventParser.parse(
            """{"type":"tool-input-delta","toolCallId":"call_1","inputTextDelta":"partial"}"""
        )
        assertEquals(ChatEvent.ToolInputDelta(toolCallId = "call_1", inputTextDelta = "partial"), event)
    }

    @Test
    fun parsesToolInputAvailable() {
        val event = ChatEventParser.parse(
            """{"type":"tool-input-available","toolCallId":"call_1","input":{"amount":120}}"""
        )
        val available = assertIs<ChatEvent.ToolInputAvailable>(event)
        assertEquals("call_1", available.toolCallId)
        assertEquals("120", available.input.jsonObject["amount"]?.jsonPrimitive?.content)
    }

    @Test
    fun parsesFinish() {
        val event = ChatEventParser.parse("""{"type":"finish"}""")
        assertEquals(ChatEvent.Finish, event)
    }

    @Test
    fun parsesCardStart() {
        val event = ChatEventParser.parse("""{"type":"card-start","id":"card_123","version":1}""")
        assertEquals(ChatEvent.CardStart(id = "card_123", version = 1), event)
    }

    @Test
    fun parsesCardUpdate() {
        val event = ChatEventParser.parse(
            """{"type":"card-update","id":"card_123","body":{"status":"confirmed"}}"""
        )
        val update = assertIs<ChatEvent.CardUpdate>(event)
        assertEquals("card_123", update.id)
        assertEquals("confirmed", update.body["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun parsesCardEnd() {
        val event = ChatEventParser.parse("""{"type":"card-end","id":"card_123"}""")
        assertEquals(ChatEvent.CardEnd(id = "card_123"), event)
    }

    @Test
    fun parsesHandoffStart() {
        val event = ChatEventParser.parse("""{"type":"handoff-start","reason":"user_requested"}""")
        assertEquals(ChatEvent.HandoffStart(reason = "user_requested"), event)
    }

    @Test
    fun parsesHandoffAgentJoined() {
        val event = ChatEventParser.parse("""{"type":"handoff-agent-joined","agentName":"Maria"}""")
        assertEquals(ChatEvent.HandoffAgentJoined(agentName = "Maria"), event)
    }

    @Test
    fun parsesHandoffEnd() {
        val event = ChatEventParser.parse("""{"type":"handoff-end","reason":"agent_resolved"}""")
        assertEquals(ChatEvent.HandoffEnd(reason = "agent_resolved"), event)
    }

    @Test
    fun parsesError() {
        val event = ChatEventParser.parse(
            """{"type":"error","code":"rate_limited","message":"Too many requests"}"""
        )
        assertEquals(ChatEvent.Error(code = "rate_limited", message = "Too many requests"), event)
    }

    @Test
    fun parsesCustomDataEvent() {
        val event = ChatEventParser.parse("""{"type":"data-weather","id":"w1","city":"Sao Paulo"}""")
        val custom = assertIs<ChatEvent.Custom>(event)
        assertEquals("data-weather", custom.type)
        assertEquals("w1", custom.id)
        assertEquals("Sao Paulo", custom.raw["city"]?.jsonPrimitive?.content)
    }

    @Test
    fun parsesCustomDataEventWithoutId() {
        val event = ChatEventParser.parse("""{"type":"data-ping"}""")
        val custom = assertIs<ChatEvent.Custom>(event)
        assertEquals("data-ping", custom.type)
        assertNull(custom.id)
    }

    @Test
    fun fallsBackToUnknownForUnrecognizedType() {
        val event = ChatEventParser.parse("""{"type":"future-event","foo":"bar"}""")
        val unknown = assertIs<ChatEvent.Unknown>(event)
        assertEquals("future-event", unknown.type)
        assertEquals("bar", unknown.raw["foo"]?.jsonPrimitive?.content)
    }

    @Test
    fun fallsBackToUnknownForMissingTypeField() {
        val event = ChatEventParser.parse("""{"foo":"bar"}""")
        val unknown = assertIs<ChatEvent.Unknown>(event)
        assertEquals("unknown", unknown.type)
    }

    @Test
    fun fallsBackToUnknownForMalformedJson() {
        // Not valid JSON at all - kotlinx.serialization throws a SerializationException
        // (a JSON decoding failure), which the parser must swallow, not propagate.
        val event = ChatEventParser.parse("not json at all {{{")
        val unknown = assertIs<ChatEvent.Unknown>(event)
        assertEquals("parse-error", unknown.type)
    }

    @Test
    fun fallsBackToUnknownForBlankPayload() {
        val event = ChatEventParser.parse("")
        val unknown = assertIs<ChatEvent.Unknown>(event)
        assertEquals("empty", unknown.type)
    }
}
