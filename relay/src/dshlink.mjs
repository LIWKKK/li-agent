// DSH 客户端：一元 RPC + 同域 GET。零依赖，仅用全局 fetch 与 WebSocket。
import { randomUUID } from 'node:crypto'

const RPC_TIMEOUT_MS = 30_000

export class DshError extends Error {
  constructor(code, message) { super(message); this.name = 'DshError'; this.code = code }
}

export class DshLink {
  constructor(auth, log = () => {}) {
    this.auth = auth
    this.log = log
    this.ws = undefined
    this.clientId = undefined
    this.pending = new Map()      // callId -> eventId（waterfall 提问回填用）
    this.reconnectDelay = 1000
    this.closed = false
    this.onQuestion = undefined   // (ask) => void
  }

  async rpc(method, args) {
    const rpcId = randomUUID()
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), RPC_TIMEOUT_MS)
    let response
    try {
      response = await fetch(`${this.auth.base}/api/${method}`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', cookie: this.auth.cookieHeader() },
        body: JSON.stringify({ type: 'client-request', rpcId, method, payload: { args } }),
        signal: controller.signal,
      })
    } catch (error) {
      throw new DshError('transport', `${method} 请求失败：${error.message}`)
    } finally { clearTimeout(timer) }
    if (response.status === 401) {
      this.auth.loadSecret()
      throw new DshError('unauthorized', `${method} 被拒绝（401），密钥已重载`)
    }
    if (response.status !== 200) throw new DshError('transport', `${method} 返回 HTTP ${response.status}`)
    let envelope
    try { envelope = await response.json() } catch { throw new DshError('protocol', `${method} 响应不是 JSON`) }
    const result = envelope?.result
    if (result === undefined || result === null) throw new DshError('protocol', `${method} 缺少 result`)
    if (result.ok !== true) {
      const error = result.error ?? {}
      throw new DshError(String(error.code ?? 'remote'), `${method}: ${error.message ?? '未知错误'}`)
    }
    return result.value
  }

  async rpcSafe(method, args) {
    try { return await this.rpc(method, args) } catch (error) { this.log(`${method} 失败（忽略）：${error.message}`); return undefined }
  }

  // 同鉴权域内的 GET（例如插件提供的用量聚合）。
  async get(path) {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), RPC_TIMEOUT_MS)
    let response
    try {
      response = await fetch(this.auth.base + path, { headers: { cookie: this.auth.cookieHeader() }, signal: controller.signal })
    } catch (error) {
      throw new DshError('transport', `${path} 请求失败：${error.message}`)
    } finally { clearTimeout(timer) }
    if (response.status !== 200) throw new DshError('transport', `${path} 返回 HTTP ${response.status}`)
    try { return await response.json() } catch { throw new DshError('protocol', `${path} 响应不是 JSON`) }
  }

  // $events 长连：收 waterfall 提问，维护 callId -> eventId，断线自动重连。
  connectEvents() {
    if (this.closed) return
    let ws
    try {
      ws = new WebSocket(`ws://${this.auth.authority}/api/remote.mux`, { headers: { cookie: this.auth.cookieHeader() } })
    } catch (error) { this.log(`事件流建立失败：${error.message}`); this.scheduleReconnect(); return }
    this.ws = ws
    ws.addEventListener('open', () => {
      this.reconnectDelay = 1000
      ws.send(JSON.stringify({ type: 'open', streamId: 'v2', endpoint: '$events', payload: { args: {} } }))
    })
    ws.addEventListener('message', (event) => this.onFrame(String(event.data)))
    ws.addEventListener('close', () => { this.clientId = undefined; this.scheduleReconnect() })
    ws.addEventListener('error', () => {})
  }

  scheduleReconnect() {
    if (this.closed) return
    const delay = this.reconnectDelay
    this.reconnectDelay = Math.min(this.reconnectDelay * 2, 15_000)
    setTimeout(() => this.connectEvents(), delay)
  }

  onFrame(text) {
    let frame
    try { frame = JSON.parse(text) } catch { return }
    if (frame?.type !== 'item') return
    const value = frame.value
    if (value === null || value === undefined) return
    if (value.type === 'ready') { this.clientId = String(value.clientId ?? ''); this.log(`事件流就绪 clientId=${this.clientId}`); return }
    if (value.type !== 'waterfall' || this.onQuestion === undefined) return
    // waterfall 帧：提取提问（questions 数组）与回填所需的 eventId
    const payload = value.payload ?? value
    const questions = Array.isArray(payload?.questions) ? payload.questions : []
    if (questions.length === 0) return
    const eventId = String(value.eventId ?? payload.eventId ?? '')
    const callId = String(payload.callId ?? '')
    if (callId !== '' && eventId !== '') this.pending.set(callId, eventId)
    this.onQuestion({ eventId, callId, questions })
  }

  async resolveEvent(eventId, value) {
    if (this.clientId === undefined) throw new DshError('events-not-ready', '事件流未就绪')
    await this.rpc('$events/result', { clientId: this.clientId, eventId, outcome: { kind: 'result', value } })
    return true
  }

  close() {
    this.closed = true
    try { this.ws?.close() } catch { /* ignore */ }
  }
}
