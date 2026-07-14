package com.fsk.threadwire.session

/**
 * Who a [ChatMessage] came from. Design doc §6.1 documents `ai | human_agent | system`
 * for *incoming* messages after a handoff switch; [USER] is a necessary addition for
 * the outgoing side of the same transcript, not a reinterpretation of that list.
 * [SYSTEM] isn't produced by any code path yet in M1 (no synthesized banners), but
 * keeps this enum forward-compatible with a later "you're being transferred..."
 * banner-as-message (design doc §14.1) without a breaking change.
 */
enum class MessageAuthor { USER, AI, HUMAN_AGENT, SYSTEM }
