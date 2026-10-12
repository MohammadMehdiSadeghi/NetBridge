import { app, safeStorage } from 'electron'
import { promises as fs } from 'fs'
import path from 'path'

export interface Settings {
  token: string
  phoneHost: string
  localPort: number
  systemProxy: boolean
  pairingCode: string
}

/**
 * On-disk shape. When the OS can protect the credential we store `tokenEnc`
 * instead of `token`; a plaintext `token` is still read for backwards
 * compatibility with settings files written by older builds.
 */
interface StoredSettings extends Partial<Settings> {
  tokenEnc?: string
}

const DEFAULTS: Settings = {
  token: '',
  phoneHost: '',
  localPort: 18080,
  systemProxy: true,
  pairingCode: ''
}

export class Store {
  private file: string
  private data: Settings = { ...DEFAULTS }

  constructor() {
    this.file = path.join(app.getPath('userData'), 'netbridge-settings.json')
  }

  async load(): Promise<Settings> {
    let stored: StoredSettings = {}
    try {
      const raw = await fs.readFile(this.file, 'utf8')
      stored = JSON.parse(raw) as StoredSettings
    } catch {
      stored = {}
    }

    const { tokenEnc, ...rest } = stored
    const merged: Settings = { ...DEFAULTS, ...rest }
    // The pairing token is a bearer credential for the phone's control API. Never
    // leave it in plain text on disk when the OS can hold the key for us.
    let unreadable = false
    if (!merged.token && tokenEnc) {
      merged.token = this.decrypt(tokenEnc)
      unreadable = merged.token === ''
    }
    this.data = merged
    if (unreadable) {
      // The stored credential cannot be read any more (different OS user, reinstall).
      // Drop it so we stop retrying on every launch and the UI cleanly says "not paired".
      await this.write()
    }
    return this.data
  }

  get(): Settings {
    return this.data
  }

  async save(patch: Partial<Settings>): Promise<Settings> {
    this.data = { ...this.data, ...patch }
    await this.write()
    return this.data
  }

  private async write(): Promise<void> {
    const { token, ...rest } = this.data
    const payload: StoredSettings = { ...rest }
    if (token) {
      const encrypted = this.encrypt(token)
      if (encrypted) payload.tokenEnc = encrypted
      else payload.token = token
    }
    await fs.mkdir(path.dirname(this.file), { recursive: true })
    await fs.writeFile(this.file, JSON.stringify(payload, null, 2), 'utf8')
  }

  private encrypt(token: string): string | null {
    try {
      if (safeStorage.isEncryptionAvailable()) {
        return safeStorage.encryptString(token).toString('base64')
      }
    } catch {
      // Fall back to plain text rather than losing the pairing entirely.
    }
    return null
  }

  private decrypt(encoded: string): string {
    try {
      return safeStorage.decryptString(Buffer.from(encoded, 'base64'))
    } catch {
      // Key material changed (OS reinstall, different user profile). Treat the
      // token as gone so the app asks for the 6-digit code again.
      return ''
    }
  }
}
