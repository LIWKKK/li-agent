// DSH 代签 cookie：从 $DSH_HOME/.credentials.yaml 读 browser-session secret，
// 按同一算法为 127.0.0.1:<port> 签票。有效期 29 天（DSH 上限 30 天）。
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { createHash, createHmac } from 'node:crypto'

const SECRET_BYTES = 32
const TTL_MS = 29 * 24 * 3600 * 1000

const b64url = (value) => Buffer.from(value).toString('base64').replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/u, '')

function decodeB64url(value) {
  const padding = '='.repeat((4 - (value.length % 4)) % 4)
  return Buffer.from(value.replaceAll('-', '+').replaceAll('_', '/') + padding, 'base64')
}

export function locateCredentialsHome(explicit) {
  const candidates = [
    explicit, process.env.DSH_HOME,
    join(process.env.HOME ?? '', '.dsh'),
    join(process.env.HOME ?? '', '.config', 'dsh'),
    '/vol1/@apphome/fn-deepseek-harness',
  ].filter((item) => typeof item === 'string' && item !== '')
  for (const candidate of candidates) {
    try {
      if (readFileSync(join(candidate, '.credentials.yaml'), 'utf8').includes('client-connection/browser-session:')) return candidate
    } catch { /* next */ }
  }
  return null
}

export class DshAuth {
  constructor(dshHome, port) {
    this.dshHome = dshHome
    this.port = port
    this.authority = '127.0.0.1:' + String(port)
    this.base = 'http://' + this.authority
    this.secret = undefined
    this.cookie = undefined
  }

  loadSecret() {
    const file = join(this.dshHome, '.credentials.yaml')
    const text = readFileSync(file, 'utf8')
    const at = text.indexOf('client-connection/browser-session:')
    if (at === -1) throw new Error('凭据里没有 browser-session 记录')
    const match = /^[ \t]*secret:[ \t]*["'`]?([A-Za-z0-9_\-+/=]+)/mu.exec(text.slice(at))
    if (match === null) throw new Error('browser-session 记录缺少 secret')
    const secret = decodeB64url(match[1].replaceAll('+', '-').replaceAll('/', '_'))
    if (secret.byteLength !== SECRET_BYTES) throw new Error('secret 长度应为 32 字节')
    this.secret = secret
    this.cookie = undefined
    return secret
  }

  cookieHeader() {
    if (this.secret === undefined) this.loadSecret()
    if (this.cookie !== undefined) return this.cookie
    const issuedAt = Date.now()
    const payload = { version: 1, authority: this.authority, issuedAt, expiresAt: issuedAt + TTL_MS }
    const body = b64url(Buffer.from(JSON.stringify(payload), 'utf8'))
    const signature = b64url(createHmac('sha256', this.secret).update(body).digest())
    const name = 'dsh-auth-' + b64url(createHash('sha256').update(this.authority).digest())
    this.cookie = name + '=v1.' + body + '.' + signature
    return this.cookie
  }
}
