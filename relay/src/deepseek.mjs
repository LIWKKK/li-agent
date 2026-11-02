// DeepSeek 网页版通道：登录态、会话列表、SSE 流式对话。
// PoW：官方 WASM sha3 求解器（wasm/ 下，与网页版逐字节相同）。
// 协议事实（2026-10 实测）：challenge === SHA3(salt + '_' + expire_at + '_' + answer)
// 请求头 X-Ds-Pow-Response = base64(JSON{algorithm,challenge,salt,answer,signature,target_path})
import { readFileSync, writeFileSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import { solvePow, powHeaderValue } from './pow.mjs'

const BASE = 'https://chat.deepseek.com/api/v0'
// 上游有请求指纹校验：只带 authorization + user-agent 会被判为 Request Blocked。
// 必须补齐 sec-ch-ua / sec-fetch-* / accept-language 这一整套浏览器头，
// 否则 create_pow_challenge 直接返回 HTML 拦截页（对照实测确认）。
const CLIENT_HEADERS = {
  'accept': '*/*',
  'accept-language': 'zh-CN,zh;q=0.9',
  'x-app-version': '2.7.0',
  'x-client-platform': 'web',
  'x-client-version': '2.7.0',
  'origin': 'https://chat.deepseek.com',
  'referer': 'https://chat.deepseek.com/',
  'sec-ch-ua': '"Chromium";v="131", "Not_A Brand";v="24"',
  'sec-ch-ua-mobile': '?0',
  'sec-ch-ua-platform': '"Windows"',
  'sec-fetch-dest': 'empty',
  'sec-fetch-mode': 'cors',
  'sec-fetch-site': 'same-origin',
  'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
}

function json(res, status, payload) {
  const body = JSON.stringify(payload)
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'content-length': Buffer.byteLength(body) })
  res.end(body)
}

const safeNum = (v) => (typeof v === 'number' && Number.isSafeInteger(v) && v >= 0 ? v : 0)
const safeStr = (v) => (typeof v === 'string' ? v : '')



export class DeepSeekChannel {
  constructor(stateFile, log) {
    this.stateFile = stateFile
    this.log = log
    this.token = undefined
    this.user = undefined
    this.streams = new Map() // chatId -> AbortController

  }

  /** 本次进程内新建/使用过的会话（上游历史接口不可用时的兜底列表）。 */
  recentChats() {
    if (this.recent === undefined) this.recent = new Map()
    return [...this.recent.entries()].map(([id, title]) => ({ id, title, updatedAt: Date.now() }))
  }

  rememberChat(id, title) {
    if (this.recent === undefined) this.recent = new Map()
    if (id === '') return
    this.recent.set(id, title === '' ? '新对话' : title)
  }

  restore() {
    try {
      const parsed = JSON.parse(readFileSync(this.stateFile, 'utf8'))
      if (typeof parsed?.token === 'string' && parsed.token !== '') {
        this.token = parsed.token
        this.user = parsed.user ?? undefined
        this.log('已恢复 DeepSeek 网页版令牌')
      }
    } catch { /* 未配置 */ }
  }

  save() {
    try {
      if (this.token === undefined) { rmSync(this.stateFile, { force: true }); return }
      writeFileSync(this.stateFile, JSON.stringify({ version: 1, token: this.token, user: this.user ?? null }, null, 2), { mode: 0o600 })
    } catch (error) { this.log('保存 DeepSeek 令牌失败：' + error.message) }
  }

  async api(path, options = {}) {
    if (this.token === undefined) throw new Error('尚未登录 DeepSeek（先粘贴 userToken）')
    const headers = { ...CLIENT_HEADERS, authorization: 'Bearer ' + this.token, ...options.headers }
    if (options.body !== undefined) headers['content-type'] = 'application/json'
    const response = await fetch(BASE + path, { ...options, headers, signal: options.signal })
    const text = await response.text()
    if (text.charCodeAt(0) === 0x3c) throw new Error('上游返回了 HTML（可能被限流，稍后再试）')
    let parsed
    try { parsed = JSON.parse(text) } catch { throw new Error('上游响应不是 JSON：HTTP ' + response.status) }
    if (parsed?.code !== 0) throw new Error('DeepSeek 错误 ' + parsed?.code + ': ' + (parsed?.msg ?? ''))
    if (parsed?.data?.biz_code !== undefined && parsed.data.biz_code !== 0) {
      throw new Error('DeepSeek 业务错误：' + (parsed.data.biz_msg ?? parsed.data.biz_code))
    }
    return parsed.data
  }

  /** 取一道 PoW 题并解出请求头值。targetPath 必须是完整 API 路径。 */
  async powHeader(targetPath) {
    const data = await this.api('/chat/create_pow_challenge', {
      method: 'POST',
      body: JSON.stringify({ target_path: targetPath }),
    })
    // 实测层级：data(=parsed.data) → biz_data → challenge 才是题对象。
    // 兼容几种常见包裹形态，避免上游改层级就整条断掉。
    const task = data?.biz_data?.challenge ?? data?.challenge ?? data?.biz_data ?? data
    if (task === undefined || task === null || task.challenge === undefined) {
      throw new Error('上游没有返回 PoW 挑战（可能被限流）')
    }
    const solved = {
      algorithm: String(task.algorithm ?? 'DeepSeekHashV1'),
      challenge: String(task.challenge),
      salt: String(task.salt ?? ''),
      difficulty: Number(task.difficulty ?? 0),
      expire_at: Number(task.expire_at ?? 0),
      signature: String(task.signature ?? ''),
      target_path: String(task.target_path ?? targetPath),
    }
    return powHeaderValue(solved, solvePow(solved))
  }

  status() {
    return { ok: true, loggedIn: this.token !== undefined, nickname: safeStr(this.user?.nickname) || safeStr(this.user?.email), models: [
      { id: 'deepseek-chat', name: 'DeepSeek V3' },
      { id: 'deepseek-reasoner', name: 'DeepSeek R1（深度思考）' },
    ] }
  }

  async handle(req, res, path, method) {
    try { await this.route(req, res, path, method) }
    catch (error) {
      if (!res.headersSent) json(res, 200, { ok: false, error: error.message })
      else if (!res.writableEnded) { res.write('data: ' + JSON.stringify({ type: 'error', message: error.message }) + '\n\n'); res.end() }
    }
  }

  async route(req, res, path, method) {
    if (path === '/api/ds/status' && method === 'GET') { json(res, 200, this.status()); return }
    if (path === '/api/ds/token' && method === 'POST') {
      const body = await new Promise((resolve) => { const c = []; req.on('data', (d) => c.push(d)); req.on('end', () => { try { resolve(JSON.parse(Buffer.concat(c).toString('utf8'))) } catch { resolve(null) } }) })
      const token = String(body?.token ?? '').trim()
      if (token === '') { json(res, 200, { ok: false, error: '请先粘贴令牌' }); return }
      const previous = this.token
      this.token = token
      try {
        const data = await this.api('/users/current', { method: 'GET' })
        this.user = data?.user ?? undefined
        this.save()
        json(res, 200, { ok: true, loggedIn: true, nickname: safeStr(this.user?.nickname) || safeStr(this.user?.email) })
      } catch (error) {
        this.token = previous
        json(res, 200, { ok: false, error: '令牌无效：' + error.message })
      }
      return
    }
    if (path === '/api/ds/logout' && method === 'POST') {
      this.token = undefined; this.user = undefined; this.save()
      json(res, 200, { ok: true })
      return
    }
    if (path === '/api/ds/chats' && method === 'GET') {
      // 上游历史列表接口对第三方调用恒返回 HTML 反爬页，这里降级为空列表：
      // 手表端仍可"新建对话"正常聊天，只是看不到历史记录。
      // 服务端暂存本次进程内创建的会话，至少让刚聊过的能再进去。
      try {
        const data = await this.api('/chat_session/fetch_list', { method: 'POST', body: JSON.stringify({}) })
        const list = Array.isArray(data?.biz_data) ? data.biz_data
          : (Array.isArray(data?.biz_data?.chat_sessions) ? data.biz_data.chat_sessions : [])
        const chats = list.map((item) => ({
          id: safeStr(item?.chat_session_id ?? item?.id),
          title: safeStr(item?.title) || '未命名对话',
          updatedAt: safeNum(item?.update_time ?? item?.updated_at),
        })).filter((c) => c.id !== '').slice(0, 60)
        json(res, 200, { ok: true, chats: chats.length > 0 ? chats : this.recentChats() })
      } catch {
        json(res, 200, { ok: true, chats: this.recentChats(), degraded: true })
      }
      return
    }
    if (path === '/api/ds/new' && method === 'POST') {
      const data = await this.api('/chat_session/create', { method: 'POST', body: JSON.stringify({}) })
      // 实测形状：biz_data.chat_session.id（不是 chat_session_id）
      const chatId = safeStr(data?.biz_data?.chat_session?.id) || safeStr(data?.biz_data?.chat_session_id)
      if (chatId === '') throw new Error('创建失败：上游没有返回会话 id')
      this.rememberChat(chatId, '新对话')
      json(res, 200, { ok: true, chatId })
      return
    }
    if (path === '/api/ds/stop' && method === 'POST') {
      const body = await new Promise((resolve) => { const c = []; req.on('data', (d) => c.push(d)); req.on('end', () => { try { resolve(JSON.parse(Buffer.concat(c).toString('utf8'))) } catch { resolve(null) } }) })
      const chatId = String(body?.chatId ?? '')
      const controller = this.streams.get(chatId)
      if (controller !== undefined) controller.abort()
      try { await this.api('/chat_session/stop_streaming', { method: 'POST', body: JSON.stringify({ chat_session_id: chatId }) }) } catch { /* 上游可能已停 */ }
      json(res, 200, { ok: true })
      return
    }
    if (path === '/api/ds/send' && method === 'POST') {
      const body = await new Promise((resolve) => { const c = []; req.on('data', (d) => c.push(d)); req.on('end', () => { try { resolve(JSON.parse(Buffer.concat(c).toString('utf8'))) } catch { resolve(null) } }) })
      const chatId = String(body?.chatId ?? '')
      const text = String(body?.text ?? '')
      if (chatId === '' || text.trim() === '') throw new Error('缺少 chatId 或 text')
      // PoW 的 target_path 必须是完整 API 路径（含 /api/v0 前缀），
      // 传 '/chat/completion' 上游会返回 INVALID_TARGET_PATH。
      const pow = await this.powHeader('/api/v0/chat/completion')
      const controller = new AbortController()
      this.streams.set(chatId, controller)
      res.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-store', 'connection': 'keep-alive' })
      const write = (frame) => { if (!res.writableEnded) { try { res.write('data: ' + JSON.stringify(frame) + '\n\n') } catch { /* ignore */ } } }
      // 心跳：思考阶段可能静默一分钟以上
      const beat = setInterval(() => { if (!res.writableEnded) { try { res.write(': ping\n\n') } catch { /* ignore */ } } }, 15000)
      beat.unref?.()
      try {
        const response = await fetch(BASE + '/chat/completion', {
          method: 'POST',
          headers: {
            ...CLIENT_HEADERS,
            authorization: 'Bearer ' + this.token,
            'content-type': 'application/json',
            'x-ds-pow-response': pow,
            'accept': 'text/event-stream',
          },
          body: JSON.stringify({
            chat_session_id: chatId,
            prompt: text,
            ref_file_ids: [],
            thinking_enabled: body?.thinking === true,
            search_enabled: body?.search === true,
          }),
          signal: controller.signal,
        })
        if (response.status !== 200) throw new Error('上游 HTTP ' + response.status)
        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let done = false
        while (!done) {
          const { value, done: streamDone } = await reader.read()
          if (streamDone) break
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() ?? ''
          for (const line of lines) {
            const trimmed = line.trim()
            // event: 行是事件名，不是数据，跳过（否则会被当 JSON 解析失败）
            if (trimmed.startsWith('event:')) {
              if (trimmed.slice(6).trim() === 'close') { done = true; break }
              continue
            }
            if (!trimmed.startsWith('data:')) continue
            const payload = trimmed.slice(5).trim()
            if (payload === '[DONE]') { done = true; break }
            let event
            try { event = JSON.parse(payload) } catch { continue }
            // 上游是 JSON-Patch 风格（实测，与 OpenAI 的 choices/delta 完全不同）：
            //   {"v":"文字"}                                    → 正文增量
            //   {"p":"response/fragments/-1/content","o":"APPEND","v":"字"} → 正文追加
            //   {"p":"response","o":"BATCH","v":[...]}          → 批量状态
            //   {"p":"response/status","o":"SET","v":"FINISHED"} → 结束
            //   event: close                                    → 关闭
            if (typeof event?.v === 'string') { write({ type: 'delta', text: event.v }); continue }
            const patchPath = typeof event?.p === 'string' ? event.p : ''
            if (patchPath !== '') {
              if (patchPath.includes('content') && event?.o === 'APPEND' && typeof event.v === 'string') {
                write({ type: 'delta', text: event.v })
                continue
              }
              if (patchPath === 'response/status' && event?.v === 'FINISHED') { done = true; break }
              if (patchPath === 'response') continue
              continue
            }
            // event: close / title / update_session 等控制事件
            if (event?.content !== undefined && event?.request_message_id === undefined) continue
            if (event?.request_message_id !== undefined) continue
          }
        }
        write({ type: 'done' })
      } catch (error) {
        if (error.name === 'AbortError') write({ type: 'done' })
        else write({ type: 'error', message: error.message })
      } finally {
        clearInterval(beat)
        this.streams.delete(chatId)
        if (!res.writableEnded) res.end()
      }
      return
    }
    json(res, 404, { ok: false, error: '未知 DeepSeek 接口' })
  }
}
