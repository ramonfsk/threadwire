package com.fsk.threadwire

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform