package com.netbridge.share.share

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks live proxy connections per client address so the desktop app can show real
 * per-device usage instead of a single global counter.
 *
 * State is keyed by the peer's IP address and collapses all of that peer's sockets
 * into one logical client.
 */
object ClientRegistry {

    data class Client(
        val address: String,
        val connections: Int,
        val bytesUp: Long,
        val bytesDown: Long,
        val firstSeen: Long,
        val lastSeen: Long
    )

    private class Bucket {
        val connections = AtomicLong(0)
        val bytesUp = AtomicLong(0)
        val bytesDown = AtomicLong(0)
        val firstSeen = AtomicLong(0)
        val lastSeen = AtomicLong(0)
    }

    private val buckets = ConcurrentHashMap<String, Bucket>()

    /** Drop clients we have not heard from in this many ms. */
    private const val STALE_AFTER_MS = 90_000L

    private fun bucketFor(address: String): Bucket =
        buckets.computeIfAbsent(address) {
            Bucket().also { b ->
                val now = System.currentTimeMillis()
                b.firstSeen.set(now)
                b.lastSeen.set(now)
            }
        }

    fun onOpen(address: String) {
        val b = bucketFor(address)
        b.connections.incrementAndGet()
        b.lastSeen.set(System.currentTimeMillis())
    }

    fun onClose(address: String) {
        val b = bucketFor(address)
        val next = b.connections.decrementAndGet()
        if (next < 0) b.connections.set(0)
        b.lastSeen.set(System.currentTimeMillis())
    }

    fun onTraffic(address: String, up: Long, down: Long) {
        if (up <= 0 && down <= 0) return
        val b = bucketFor(address)
        if (up > 0) b.bytesUp.addAndGet(up)
        if (down > 0) b.bytesDown.addAndGet(down)
        b.lastSeen.set(System.currentTimeMillis())
    }

    fun snapshot(): List<Client> {
        val now = System.currentTimeMillis()
        val stale = buckets.entries.filter { (now - it.value.lastSeen.get()) > STALE_AFTER_MS }
        for (entry in stale) {
            if (entry.value.connections.get() <= 0) buckets.remove(entry.key)
        }
        return buckets.entries.map { (address, b) ->
            Client(
                address = address,
                connections = b.connections.get().toInt(),
                bytesUp = b.bytesUp.get(),
                bytesDown = b.bytesDown.get(),
                firstSeen = b.firstSeen.get(),
                lastSeen = b.lastSeen.get()
            )
        }.sortedByDescending { it.lastSeen }
    }

    fun activeCount(): Int {
        val now = System.currentTimeMillis()
        return buckets.values.count { it.connections.get() > 0 || (now - it.lastSeen.get()) <= STALE_AFTER_MS }
    }

    fun reset() {
        buckets.clear()
    }
}
