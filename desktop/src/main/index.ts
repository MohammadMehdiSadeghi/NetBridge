import { app, BrowserWindow, shell } from 'electron'
import path from 'path'
import { Store } from './store'
import { ProxyChain } from './proxyChain'
import { AppController } from './ipc'

let mainWindow: BrowserWindow | null = null
let controller: AppController | null = null

function createWindow(): void {
  mainWindow = new BrowserWindow({
    width: 1040,
    height: 720,
    minWidth: 880,
    minHeight: 620,
    show: false,
    autoHideMenuBar: true,
    backgroundColor: '#0B0F14',
    title: 'NetBridge',
    webPreferences: {
      preload: path.join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  })

  mainWindow.on('ready-to-show', () => mainWindow?.show())

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    void shell.openExternal(url)
    return { action: 'deny' }
  })

  if (process.env.ELECTRON_RENDERER_URL) {
    void mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL)
  } else {
    void mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'))
  }
}

app.whenReady().then(async () => {
  const store = new Store()
  await store.load()
  const chain = new ProxyChain()
  const saved = store.get()
  chain.setPhone(saved.phoneHost, 8080)

  controller = new AppController(store, chain, () => mainWindow)
  controller.registerIpc()

  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})

app.on('before-quit', (event) => {
  if (controller) {
    event.preventDefault()
    const c = controller
    controller = null
    void c.shutdown().finally(() => app.exit(0))
  }
})
