import net from 'net'

export interface ChainStats {
  connections: number
  bytesIn: number
  bytesOut: number
}

/**
 * Local HTTP proxy on 127.0.0.1.
 * Clients speak plain HTTP-proxy protocol; we pipe the bytes to the phone's
 * HTTP proxy unchanged (HTTP-proxy → HTTP-proxy chaining).
 */
export class ProxyChain {
  private server: net.Server | null = null
  private phoneHost = ''
  private phonePort = 8080
  private sockets = new Set<net.Socket>()

  stats: ChainStats = { connections: 0, bytesIn: 0, bytesOut: 0 }

  setPhone(host: string, port: number): void {
    this.phoneHost = host
    this.phonePort = port
  }

  get listening(): boolean {
    return this.server !== null && this.server.listening
  }

  async start(port: number): Promise<void> {
    if (this.server) await this.stop()

    await new Promise<void>((resolve, reject) => {
      const server = net.createServer((client) => this.handle(client))
      server.once('error', reject)
      server.listen(port, '127.0.0.1', () => {
        server.removeListener('error', reject)
        resolve()
      })
      this.server = server
    })
  }

  async stop(): Promise<void> {
    for (const s of this.sockets) {
      s.destroy()
    }
    this.sockets.clear()
    const server = this.server
    this.server = null
    if (!server) return
    // server.close() only invokes its callback once every connection has ended.
    // If anything is still lingering (a half-open upstream, a client that never
    // read our error body), the callback never fires and disconnect would hang
    // forever — leaving the System Proxy pointed at a dead port. So bound it.
    await new Promise<void>((resolve) => {
      let settled = false
      const done = (): void => {
        if (settled) return
        settled = true
        resolve()
      }
      const timer = setTimeout(() => {
        // Node >= 18.2 has this; the bundled type defs may not declare it yet.
        const s = server as net.Server & { closeAllConnections?: () => void }
        s.closeAllConnections?.()
        done()
      }, 2000)
      timer.unref?.()
      try {
        server.close(() => {
          clearTimeout(timer)
          done()
        })
      } catch {
        clearTimeout(timer)
        done()
      }
    })
  }

  private handle(client: net.Socket): void {
    if (!this.phoneHost) {
      client.end('HTTP/1.1 503 Phone Not Connected\r\nConnection: close\r\n\r\n')
      return
    }

    this.sockets.add(client)
    this.stats.connections += 1

    const upstream = net.connect({
      host: this.phoneHost,
      port: this.phonePort
    })
    upstream.setTimeout(15_000)

    let bridged = false
    let closed = false

    const cleanup = (): void => {
      if (closed) return
      closed = true
      this.sockets.delete(client)
      client.destroy()
      upstream.destroy()
    }

    upstream.once('connect', () => {
      bridged = true
      upstream.setTimeout(0)

      client.on('data', (chunk) => {
        this.stats.bytesIn += chunk.length
      })
      upstream.on('data', (chunk) => {
        this.stats.bytesOut += chunk.length
      })

      client.pipe(upstream)
      upstream.pipe(client)
    })

    upstream.on('timeout', () => cleanup())
    upstream.on('error', () => {
      if (!bridged) {
        // The phone is unreachable. Answer with a real error, then release the
        // socket. We must keep it tracked until it has actually flushed and
        // ended, otherwise a later stop() cannot reach it and server.close()
        // would never call back.
        if (closed) return
        closed = true
        client.end(
          'HTTP/1.1 502 Phone Unreachable\r\nConnection: close\r\nContent-Length: 0\r\n\r\n',
          () => {
            this.sockets.delete(client)
            client.destroy()
          }
        )
        upstream.destroy()
      } else {
        cleanup()
      }
    })

    client.on('error', () => upstream.destroy())
    client.on('close', () => {
      this.sockets.delete(client)
      upstream.destroy()
    })
  }
}
