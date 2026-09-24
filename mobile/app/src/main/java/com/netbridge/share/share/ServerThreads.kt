package com.netbridge.share.share

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * A private, unbounded thread pool for one server.
 *
 * The control API, the HTTP proxy and the SOCKS proxy all used to run on the shared
 * [kotlinx.coroutines.Dispatchers.IO], which is capped (64 threads by default) and
 * holds one blocked thread for the whole life of a proxy tunnel — plus every
 * upstream connect attempt, which blocks for up to 12s when the phone's VPN is slow.
 * Once those tunnels fill the pool, the *accept* loops of every server stop being
 * scheduled. The listening sockets stay bound, so the kernel keeps queuing completed
 * handshakes until the backlog (32 / 128) is full and then silently drops SYNs.
 * From the PC that looks exactly like a dead phone: ping works, closed ports answer
 * with RST, but every real port gives "no answer from 10.x.x.x:7777".
 *
 * A dedicated pool per server makes that impossible: the proxy can only ever starve
 * itself, never the control API.
 */
object ServerThreads {
    /** New threads are only created when every existing one is busy, then reaped after 60s. */
    fun pool(name: String): ExecutorCoroutineDispatcher {
        val seq = AtomicInteger(0)
        val factory = ThreadFactory { r ->
            Thread(r, "netbridge-$name-${seq.incrementAndGet()}").apply { isDaemon = true }
        }
        return Executors.newCachedThreadPool(factory).asCoroutineDispatcher()
    }
}
