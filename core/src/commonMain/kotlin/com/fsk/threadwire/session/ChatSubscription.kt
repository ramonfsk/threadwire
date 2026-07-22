package com.fsk.threadwire.session

/** A handle returned by [ChatSession.observeState]. Call [close] to stop receiving updates. */
fun interface ChatSubscription {
    fun close()
}
