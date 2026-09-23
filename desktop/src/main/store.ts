import { app } from 'electron'
import { promises as fs } from 'fs'
import path from 'path'

export interface Settings {
  token: string
  phoneHost: string
  localPort: number
  systemProxy: boolean
  pairingCode: string
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
    try {
      const raw = await fs.readFile(this.file, 'utf8')
      this.data = { ...DEFAULTS, ...JSON.parse(raw) }
    } catch {
      this.data = { ...DEFAULTS }
    }
    return this.data
  }

  get(): Settings {
    return this.data
  }

  async save(patch: Partial<Settings>): Promise<Settings> {
    this.data = { ...this.data, ...patch }
    await fs.mkdir(path.dirname(this.file), { recursive: true })
    await fs.writeFile(this.file, JSON.stringify(this.data, null, 2), 'utf8')
    return this.data
  }
}
