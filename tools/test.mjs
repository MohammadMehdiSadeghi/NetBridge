/**
 * NetBridge — one-command test runner.
 *
 *   node tools/test.mjs
 *
 * Runs everything that can run without a phone, an Android SDK, or the Electron
 * binary. Reports clearly on what it could NOT verify, so a green run is never
 * mistaken for "the whole product works".
 */

import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const results = []

function run(label, command, args, cwd = root) {
  process.stdout.write(`\n\u2500\u2500 ${label}\n`)
  const r = spawnSync(command, args, { cwd, stdio: 'inherit', shell: false })
  results.push({ label, code: r.status })
  return r.status === 0
}

// 1. Desktop type safety — catches a whole class of bugs for free.
run('Desktop typecheck (main + renderer)', 'node', [
  path.join('node_modules', 'typescript', 'bin', 'tsc'),
  '--noEmit',
  '-p',
  'tsconfig.node.json'
], path.join(root, 'desktop'))

run('Desktop typecheck (renderer)', 'node', [
  path.join('node_modules', 'typescript', 'bin', 'tsc'),
  '--noEmit',
  '-p',
  'tsconfig.web.json'
], path.join(root, 'desktop'))

// 2. Build the app bundles.
run('Desktop build', 'node', [
  path.join('node_modules', 'electron-vite', 'bin', 'electron-vite.js'),
  'build'
], path.join(root, 'desktop'))

// 3. Compile ProxyChain standalone so the smoke test can drive the real class.
run('Compile ProxyChain for smoke test', 'node', [
  path.join('node_modules', 'typescript', 'bin', 'tsc'),
  'src/main/proxyChain.ts',
  '--outDir',
  '.smoke',
  '--module',
  'commonjs',
  '--target',
  'es2022',
  '--esModuleInterop',
  '--skipLibCheck'
], path.join(root, 'desktop'))

// 4. The traffic path itself.
run('Traffic-path smoke test', 'node', ['tools/smoke-test.mjs'])

// 5. Cross-language contract between Kotlin and TypeScript.
run('Control-API contract test', 'node', ['tools/contract-test.mjs'])

// ---- Summary ---------------------------------------------------------------
console.log('\n' + '='.repeat(64))
console.log('SUMMARY')
console.log('='.repeat(64))
for (const r of results) {
  console.log(`  ${r.code === 0 ? 'PASS' : 'FAIL'}  ${r.label}`)
}

const failed = results.filter((r) => r.code !== 0)
console.log('='.repeat(64))

console.log(`
NOT covered by this run (and why):

  Android APK       No JDK 17, no Android SDK, no Gradle on this machine.
                    Verify in Android Studio: open mobile/ -> Build APK.
  Windows .exe      electron-builder must fetch a 115 MB Electron binary from
                    GitHub; that download is blocked here. Run "npm run dist"
                    on a machine with open access to github.com.
  Real VPN plumbing Needs an actual phone with a VPN app running.
                    See docs/testing.md for the on-device procedure.
  System Proxy      Needs an interactive Windows session to read back
                    HKCU Internet Settings. See docs/testing.md step 4.
`)

process.exit(failed.length === 0 ? 0 : 1)
