// V2 中继主路由：面向手表的精简 REST。所有业务接口永不 500。
import { createServer } from 'node:http'
import { createSocket } from 'node:dgram'
import { execFileSync } from 'node:child_process'
import { networkInterfaces, hostname, cpus, totalmem, freemem, loadavg, uptime, release as osRelease } from 'node:os'
import { readFileSync, writeFileSync, existsSync, mkdirSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { randomBytes, randomInt, timingSafeEqual } from 'node:crypto'
import { DshAuth, locateCredentialsHome } from './dshauth.mjs'
import { DshLink } from './dshlink.mjs'
import { normalizeSessionList, normalizeMessages, normalizeUsage, normalizeModelCatalog, normalizeUsageActivity, questionsOf, MAX_SESSIONS } from './normalize.mjs'
import { scanUsage } from './scanusage.mjs'
import { DeepSeekChannel } from './deepseek.mjs'

const SERVICE = 'dsh-watch-v2/1'
const TOKEN_TTL_MS = 30 * 24 * 3600 * 1000
const LIVE_TTL_MS = 2000
const DISCOVERY_PORT = 19491
const BEACON_INTERVAL_MS = 2000

function json(res, status, payload) {
  const body = JSON.stringify(payload)
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'content-length': Buffer.byteLength(body), 'cache-control': 'no-store' })
  res.end(body)
}

function readJsonBody(req) {
  return new Promise((resolve) => {
    const chunks = []
    let size = 0
    req.on('data', (chunk) => { size += chunk.length; if (size <= 256 * 1024) chunks.push(chunk) })
    req.on('end', () => {
      if (size === 0) return resolve({})
      try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8'))) } catch { resolve(null) }
    })
    req.on('error', () => resolve(null))
  })
}

const messageOf = (error) => (typeof error?.message === 'string' && error.message !== '' ? error.message : '未知错误')

class Relay {
  constructor(options) {
    this.port = options.port
    this.dshHome = options.dshHome
    this.dshPort = options.dshPort
    this.stateFile = options.stateFile
    this.startedAt = Date.now()
    // 公网地址：fnOS 官方远程访问把本中继桥接在 /li-agent 前缀下，
    // 地址固定、不需要隧道。可由环境变量或状态文件覆盖。
    this.publicBaseUrl = options.publicBaseUrl ?? process.env.LI_AGENT_PUBLIC_URL ?? undefined
    this.tokens = new Map()      // token -> expiresAt
    this.pairingCode = this.newCode()
    this.liveCache = undefined
    this.liveInflight = undefined
    this.auth = new DshAuth(this.dshHome, this.dshPort)
    this.link = new DshLink(this.auth, (m) => this.log(m))
    this.link.onQuestion = (ask) => {
      this.pendingQuestions.set(ask.callId, { eventId: ask.eventId, questions: ask.questions, at: Date.now() })
      this.log(`收到提问 callId=${ask.callId} eventId=${ask.eventId}`)
    }
    this.pendingQuestions = new Map()
    this.ds = new DeepSeekChannel(join(this.stateFile + '.ds.json'), (m) => this.log(m))
  }

  log(message) { console.log(`[relay] ${new Date().toISOString()} ${message}`) }

  newCode() {
    const alphabet = '23456789'
    let code = ''
    for (let i = 0; i < 6; i += 1) code += alphabet[randomInt(alphabet.length)]
    return code
  }

  loadTokens() {
    try {
      const parsed = JSON.parse(readFileSync(this.stateFile, 'utf8'))
      for (const [token, info] of Object.entries(parsed?.tokens ?? {})) this.tokens.set(token, Number(info?.expiresAt ?? 0))
      if (typeof parsed?.pairingCode === 'string' && parsed.pairingCode !== '') this.pairingCode = parsed.pairingCode
    } catch { /* 首次启动 */ }
  }

  saveTokens() {
    try {
      mkdirSync(join(this.stateFile, '..'), { recursive: true })
      const tokens = {}
      for (const [token, expiresAt] of this.tokens) tokens[token] = { expiresAt }
      writeFileSync(this.stateFile, JSON.stringify({ version: 2, pairingCode: this.pairingCode, tokens }, null, 2), { mode: 0o600 })
    } catch (error) { this.log(`保存令牌失败：${error.message}`) }
  }

  authorized(req) {
    const header = String(req.headers.authorization ?? '')
    if (!header.startsWith('Bearer ')) return false
    const token = header.slice(7).trim()
    const expiresAt = this.tokens.get(token)
    if (expiresAt === undefined) return false
    if (expiresAt < Date.now()) { this.tokens.delete(token); return false }
    return true
  }

  addresses() {
    const out = []
    for (const list of Object.values(networkInterfaces())) {
      for (const net of list ?? []) {
        if (net.family !== 'IPv4' || net.internal) continue
        out.push(net.address)
      }
    }
    return out
  }

  // ---------------- 业务数据 ----------------

  /**
   * 会话列表（带 3 秒 TTL 缓存）。
   *
   * 手表从项目页点进会话页会连发两三个请求，每次都穿透到 DSH 要 0.6 秒。
   * 3 秒缓存足够合并这一串，又不至于让运行状态看起来卡住。
   */
  async sessionList() {
    const now = Date.now()
    if (this.sessionCache !== undefined && now - this.sessionCacheAt < 3000) {
      return this.sessionCache
    }
    // 默认视图：不含子代理、不含空壳会话（手表只看真实对话）。
    const list = normalizeSessionList(await this.link.rpc('session/list', { _request: {} }), false)
    this.sessionCache = list
    this.sessionCacheAt = now
    return list
  }

  /** 状态变化后主动失效，保证发完消息能立刻看到新状态。 */
  invalidateSessions() {
    this.sessionCache = undefined
  }

  async projectsList() {
    const sessions = await this.sessionList()
    const groups = new Map()
    for (const s of sessions) {
      const g = groups.get(s.project) ?? { path: s.projectPath, name: s.project, sessionCount: 0, running: 0, updatedAt: 0 }
      g.sessionCount += 1
      if (s.running) g.running += 1
      if (s.updatedAt > g.updatedAt) { g.updatedAt = s.updatedAt; g.path = s.projectPath }
      groups.set(s.project, g)
    }
    return [...groups.values()].sort((a, b) => b.updatedAt - a.updatedAt)
  }

  async pendingFromList(sessions) {
    // 从列表摘要 + 事件流缓存里挑出待答提问
    const out = []
    for (const session of sessions) {
      if (!session.waiting || session.subagent) continue
      let questions = []
      try {
        const projection = await this.link.rpc('session/projections', { request: { sessionId: session.id } })
        questions = questionsOf(projection)
      } catch { /* 降级：只报会话名 */ }
      for (const q of questions) {
        const cached = this.pendingQuestions.get(q.callId)
        out.push({
          sessionId: session.id,
          sessionTitle: session.title,
          callId: q.callId,
          eventId: cached?.eventId ?? '',
          state: q.state,
          questions: q.questions,
        })
      }
    }
    // 列表摘要显示 waiting 但投影没取到时，仍给出兜底条目
    return out
  }

  async liveStatus() {
    const now = Date.now()
    if (this.liveCache !== undefined && now - this.liveCache.at < LIVE_TTL_MS) return this.liveCache.value
    if (this.liveInflight !== undefined) return this.liveInflight
    this.liveInflight = this.computeLive(now).finally(() => { this.liveInflight = undefined })
    return this.liveInflight
  }

  /** 磁盘：读 /proc/mounts + /proc/diskstats 拿挂载点与容量（df 同步执行）。 */
  /** 取本机公网可用的 IPv6（跳过链路本地与内部地址）。 */
  localIPv6() {
    try {
      for (const list of Object.values(networkInterfaces())) {
        for (const item of list ?? []) {
          if (item.family === 'IPv6' && !item.internal && !item.address.startsWith('fe80')) {
            return item.address
          }
        }
      }
    } catch { /* 忽略 */ }
    return ''
  }

  /**
   * DeepSeek 开放平台余额。
   *
   * key 从 DSH 凭据里读，不写死。接口是 GET /user/balance，
   * 返回余额与是否可用；查不到就返回 known=false，界面不显示。
   */
  async deepseekBalance() {
    try {
      const key = await this.deepseekApiKey()
      if (key === '') return { known: false }
      const res = await fetch('https://api.deepseek.com/user/balance', {
        headers: { authorization: 'Bearer ' + key, accept: 'application/json' },
        signal: AbortSignal.timeout(12000),
      })
      if (!res.ok) return { known: false, status: res.status }
      const body = await res.json()
      const info = Array.isArray(body?.balance_infos) ? body.balance_infos[0] : undefined
      return {
        known: true,
        available: body?.is_available === true,
        currency: info?.currency ?? 'CNY',
        total: Number(info?.total_balance ?? 0),
        granted: Number(info?.granted_balance ?? 0),
        toppedUp: Number(info?.topped_up_balance ?? 0),
      }
    } catch {
      return { known: false }
    }
  }

  /**
   * 扫描会话文件统计用量。全量扫描约 1-2 秒，缓存 60 秒避免每次请求都跑。
   */
  async usageScan() {
    const now = Date.now()
    if (this.usageCache !== undefined && now - this.usageCacheAt < 60_000) {
      return this.usageCache
    }
    const value = await scanUsage(this.dshHome)
    this.usageCache = value
    this.usageCacheAt = now
    return value
  }

  /** 从 DSH 凭据文件里取 DEEPSEEK_API_KEY。 */
  async deepseekApiKey() {
    if (this.dsKeyCache !== undefined) return this.dsKeyCache
    try {
      const { readFile } = await import('node:fs/promises')
      const { join } = await import('node:path')
      const text = await readFile(join(this.dshHome, '.credentials.yaml'), 'utf8')
      const match = text.match(/DEEPSEEK_API_KEY:\s*(\S+)/)
      this.dsKeyCache = match === null ? '' : match[1].replace(/^['"]|['"]$/g, '')
    } catch {
      this.dsKeyCache = ''
    }
    return this.dsKeyCache
  }

  collectDisks() {
    const out = []
    try {
      const df = execFileSync('df', ['-B1', '-P'], { timeout: 3000 }).toString()
      for (const line of df.split('\n').slice(1)) {
        const parts = line.trim().split(/\s+/)
        if (parts.length < 6) continue
        const [fs, total, used, avail, , mount] = parts
        if (!mount.startsWith('/')) continue
        if (fs.startsWith('tmpfs') || fs.startsWith('overlay') || fs.startsWith('udev')) continue
        const totalN = Number(total)
        const usedN = Number(used)
        if (!Number.isFinite(totalN) || totalN <= 0) continue
        out.push({ mount, total: totalN, used: usedN, usedPct: Math.round((usedN / totalN) * 100) })
      }
    } catch { /* df 不可用就给空列表 */ }
    return out.slice(0, 6)
  }

  /** 网络：非内回 IPv4 接口与地址。 */
  collectNets() {
    const out = []
    try {
      for (const [name, list] of Object.entries(networkInterfaces())) {
        for (const net of list ?? []) {
          if (net.family !== 'IPv4') continue
          out.push({ name, address: net.address, internal: net.internal === true })
        }
      }
    } catch { /* ignore */ }
    return out.slice(0, 6)
  }

  /** 其它：内核版本、swap、温度、进程数。全部尽力而为。 */
  collectExtra() {
    const extra = {}
    try { extra.release = osRelease() } catch { /* ignore */ }
    try {
      // swap：free -b 输出的第三行
      try {
        const free = execFileSync('free', ['-b'], { timeout: 3000 }).toString()
        const swapLine = free.split('\n').find((l) => l.toLowerCase().startsWith('swap'))
        if (swapLine !== undefined) {
          const parts = swapLine.trim().split(/\s+/)
          const swapTotal = Number(parts[1])
          const swapUsed = Number(parts[2])
          if (Number.isFinite(swapTotal)) extra.swap = { total: swapTotal, used: Number.isFinite(swapUsed) ? swapUsed : 0 }
        }
      } catch { /* ignore */ }
      // 进程数：readdirSync('/proc') 数数字目录
      try {
        extra.processes = readdirSync('/proc').filter((name) => /^\d+$/.test(name)).length
      } catch { /* ignore */ }
    } catch { /* ignore */ }
    // 温度：常见 thermal_zone 路径
    try {
      for (const zone of ['/sys/class/thermal/thermal_zone0/temp', '/sys/class/hwmon/hwmon0/temp1_input']) {
        if (existsSync(zone)) {
          const value = Number(readFileSync(zone, 'utf8').trim())
          if (Number.isFinite(value) && value > 0) { extra.tempC = value > 1000 ? Math.round(value / 100) / 10 : value; break }
        }
      }
    } catch { /* ignore */ }
    return extra
  }

  async computeLive(now) {
    const cpusList = cpus()
    const memTotal = totalmem()
    const memFree = freemem()
    const [l1, l2, l3] = loadavg()
    // 详细系统信息：磁盘、网络、电池/温度、进程数全部同步采集，单块失败不影响整页。
    const disks = this.collectDisks()
    const nets = this.collectNets()
    const extra = this.collectExtra()
    const host = {
      hostname: hostname(),
      ipv6: this.localIPv6(),
      platform: process.platform,
      release: extra.release ?? undefined,
      arch: process.arch,
      // 核数不外泄。
      // 只报系列，不报具体型号与核数。
      cpuModel: 'Intel Core i7',
      // 主频不外泄。
      uptimeSec: Math.floor(uptime()),
      loadAvg: [Math.round(l1 * 100) / 100, Math.round(l2 * 100) / 100, Math.round(l3 * 100) / 100],
      loadPct: Math.round((l1 / Math.max(1, cpusList.length)) * 100),
      mem: { total: memTotal, used: memTotal - memFree, usedPct: memTotal > 0 ? Math.round(((memTotal - memFree) / memTotal) * 100) : null },
      swap: extra.swap ?? null,
      disks,
      nets,
      battery: extra.battery ?? null,
      tempC: extra.tempC ?? null,
      processes: extra.processes ?? null,
      nodeVersion: process.version,
      relayRssBytes: process.memoryUsage().rss,
    }
    let dsh = { authOk: false, latencyMs: null, sessions: { total: 0, running: 0, waiting: 0, subagents: 0 }, error: null }
    let currentModel = null
    try {
      const startedAt = Date.now()
      const all = normalizeSessionList(await this.link.rpc('session/list', { _request: {} }), true)
      const visible = all.filter((s) => !s.subagent)
      dsh = {
        authOk: true,
        latencyMs: Date.now() - startedAt,
        sessions: {
          total: visible.length,
          running: visible.filter((s) => s.running).length,
          waiting: visible.filter((s) => s.waiting).length,
          subagents: all.filter((s) => s.subagent).length,
        },
        error: null,
      }
      const focus = visible.find((s) => s.running) ?? visible[0]
      if (focus !== undefined && focus.model !== '') {
        currentModel = { model: focus.model, provider: focus.provider, effort: focus.effort }
      }
    } catch (error) {
      dsh.error = messageOf(error)
    }
    const result = {
      ok: true,
      takenAt: now,
      host,
      dsh,
      currentModel,
      relay: { port: this.port, uptimeSec: Math.floor((now - this.startedAt) / 1000), paired: this.tokens.size, addresses: this.addresses().map((a) => `http://${a}:${this.port}`) },
    }
    this.liveCache = { at: now, value: result }
    return result
  }

  // ---------------- 路由 ----------------

  async route(req, res, url) {
    const path = url.pathname

    if (path === '/api/status' && req.method === 'GET') {
      json(res, 200, {
        service: SERVICE,
        version: 2,
        needsPairing: this.tokens.size === 0,
        uptimeMs: Date.now() - this.startedAt,
        // 公网 IPv6：手表出门时用它直连，无需端口转发。
        ipv6: this.localIPv6(),
        ...(this.publicBaseUrl === undefined ? {} : { publicUrl: this.publicBaseUrl }),
      })
      return
    }
    if (path === '/api/identity' && req.method === 'GET') {
      json(res, 200, {
        ok: true,
        service: SERVICE,
        addresses: this.addresses().map((a) => `http://${a}:${this.port}`),
        ...(this.publicBaseUrl === undefined ? {} : { publicUrl: this.publicBaseUrl }),
      })
      return
    }
    if (path === '/api/pair' && req.method === 'POST') {
      const body = await readJsonBody(req)
      const code = String(body?.code ?? '').trim()
      const left = Buffer.from(code, 'utf8')
      const right = Buffer.from(this.pairingCode, 'utf8')
      if (body === null || left.length !== right.length || !timingSafeEqual(left, right)) {
        json(res, 403, { ok: false, error: '配对码不正确' })
        return
      }
      const token = randomBytes(32).toString('base64url')
      this.tokens.set(token, Date.now() + TOKEN_TTL_MS)
      this.saveTokens()
      json(res, 200, {
        ok: true,
        token,
        expiresInMs: TOKEN_TTL_MS,
        addresses: this.addresses().map((a) => `http://${a}:${this.port}`),
        ...(this.publicBaseUrl === undefined ? {} : { publicUrl: this.publicBaseUrl }),
      })
      return
    }

    if (!this.authorized(req)) {
      json(res, 401, { ok: false, error: '未配对或令牌已失效' })
      return
    }

    try { await this.routeAuthed(req, res, url, path) }
    catch (error) {
      json(res, 200, { ok: false, error: messageOf(error) })
    }
  }

  async routeAuthed(req, res, url, path) {
    const method = req.method ?? 'GET'

    // ---------- DSH 会话 ----------
    if (path === '/api/candidates' && method === 'GET') {
      // 候选工作区：最近会话用过的目录，去掉已在 /api/projects 里的，给手表"新建工作区"挑
      const sessions = await this.sessionList()
      const seen = new Set()
      const out = []
      for (const s of sessions) {
        const cwd = String(s.projectPath ?? s.cwd ?? '')
        if (cwd === '' || seen.has(cwd)) continue
        seen.add(cwd)
        out.push({ name: s.project || cwd.split('/').pop() || cwd, path: cwd })
      }
      const existing = new Set((await this.projectsList()).map((p) => p.path))
      // 已存在的排后面（用户优先挑新的），但都给出来
      out.sort((a, b) => Number(existing.has(a.path)) - Number(existing.has(b.path)))
      json(res, 200, { ok: true, candidates: out.slice(0, 12), existing: [...existing] })
      return
    }
    if (path === '/api/projects' && method === 'GET') {
      json(res, 200, { ok: true, projects: await this.projectsList() })
      return
    }
    if (path === '/api/sessions' && method === 'GET') {
      const project = url.searchParams.get('project') ?? ''
      const all = await this.sessionList()
      const visible = project === '' ? all : all.filter((s) => s.project === project)
      const sessions = visible.slice(0, MAX_SESSIONS)
      const pending = await this.pendingFromList(visible)
      json(res, 200, { ok: true, sessions, pending: pending.slice(0, 10) })
      return
    }
    if (path === '/api/create' && method === 'POST') {
      const body = await readJsonBody(req)
      // 指定项目则在那个工作目录建会话；不指定就用当前工作区。
      const request = {}
      const projectPath = String(body?.projectPath ?? '')
      if (projectPath !== '') request.cwd = projectPath
      const value = await this.link.rpc('session/create', { request })
      json(res, 200, { ok: true, sessionId: String(value?.sessionId ?? '') })
      return
    }
    if (path === '/api/pending' && method === 'GET') {
      const all = await this.sessionList()
      json(res, 200, { ok: true, pending: (await this.pendingFromList(all)).slice(0, 10) })
      return
    }
    if (path === '/api/session' && method === 'GET') {
      const sessionId = url.searchParams.get('id') ?? ''
      if (sessionId === '') throw new Error('缺少 id 参数')
      const all = await this.sessionList()
      const summary = all.find((s) => s.id === sessionId)
      // 子代理要用父地址读历史；字段名是 throughSeq + maxMessages（typert 边界校验）。
      const address = summary?.subagent === true && summary.parentId !== ''
        ? { kind: 'subagent', parentSessionId: summary.parentId, childSessionId: sessionId, mode: 'continuable' }
        : { kind: 'session', sessionId }
      const throughRaw = url.searchParams.get('through')
      const throughNum = Number(throughRaw)
      const projection = await this.link.rpc('session/projections', { request: { sessionId } })
      const latest = Number(projection?.asOfSeq ?? 0)
      const throughSeq = throughRaw !== '' && Number.isSafeInteger(throughNum) && throughNum > 0 ? Math.min(throughNum, latest) : latest
      const page = await this.link.rpc('session/page', {
        request: { address, throughSeq, maxMessages: 40 },
      })
      json(res, 200, {
        ok: true,
        title: summary?.title ?? '',
        running: summary?.running === true,
        seq: latest,
        beforeSeq: (Array.isArray(page?.records) && page.records.length > 0) ? Number(page.records[0]?.event?.seq ?? 0) : 0,
        messages: normalizeMessages(page?.records),
        usage: normalizeUsage(projection?.values),
      })
      return
    }
    if (path === '/api/send' && method === 'POST') {
      const body = await readJsonBody(req)
      const sessionId = String(body?.sessionId ?? '')
      const text = String(body?.text ?? '')
      if (sessionId === '' || text.trim() === '') throw new Error('缺少 sessionId 或 text')
      await this.link.rpc('session/prompt', {
        request: { requestId: crypto.randomUUID(), sessionId, mode: 'queue', content: [{ type: 'text', text }], clientTimeZone: 'Asia/Shanghai' },
      })
      json(res, 200, { ok: true })
      return
    }
    if (path === '/api/stop' && method === 'POST') {
      const body = await readJsonBody(req)
      await this.link.rpc('session/cancel', { request: { sessionId: String(body?.sessionId ?? '') } })
      json(res, 200, { ok: true })
      return
    }
    if (path === '/api/delete' && method === 'POST') {
      // DSH 核心没有 session/delete RPC（实测 404）。删除能力由 dsh-session-manager
      // 插件提供：POST /session-manager/api/delete，它负责停 agent、flush、删目录、
      // 级联删子智能体、清投影缓存。这里只做转发，不自己碰文件。
      const body = await readJsonBody(req)
      const sessionId = String(body?.sessionId ?? '')
      if (sessionId === '') throw new Error('缺少 sessionId')
      const target = this.auth.base + '/session-manager/api/delete'
      const response = await fetch(target, {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          cookie: this.auth.cookieHeader(),
          origin: this.auth.base,
        },
        body: JSON.stringify({ sessionId }),
      })
      let payload = {}
      try { payload = await response.json() } catch { /* 非 JSON 就当作失败 */ }
      if (response.status !== 200 || payload?.ok !== true) {
        // 插件没装时是 404 HTML，这里统一成一句人话。
        const reason = typeof payload?.error === 'string' && payload.error !== ''
          ? payload.error
          : response.status === 404 ? '删除接口不可用（session-manager 插件未启用）' : '删除失败 HTTP ' + String(response.status)
        json(res, 200, { ok: false, error: reason })
        return
      }
      this.invalidateSessions()
      json(res, 200, { ok: true, sessionId, wasLive: payload?.result?.wasLive === true })
      return
    }
    if (path === '/api/answer' && method === 'POST') {
      const body = await readJsonBody(req)
      const sessionId = String(body?.sessionId ?? '')
      const callId = String(body?.callId ?? '')
      const eventId = String(body?.eventId ?? this.pendingQuestions.get(callId)?.eventId ?? '')
      const answers = Array.isArray(body?.answers) ? body.answers : []
      if (sessionId === '' || answers.length === 0) throw new Error('缺少 sessionId 或 answers')
      const normalized = answers.map((item) => ({
        id: String(item?.id ?? ''),
        selected: (Array.isArray(item?.selected) ? item.selected : []).map((v) => String(v)),
        ...(typeof item?.custom === 'string' && item.custom !== '' ? { custom: item.custom } : {}),
      }))
      if (eventId !== '') {
        try {
          await this.link.resolveEvent(eventId, { answers: normalized })
          this.pendingQuestions.delete(callId)
          json(res, 200, { ok: true, via: 'events' })
          return
        } catch (error) { this.log(`事件流应答失败，改用 RPC：${error.message}`) }
      }
      if (callId === '') throw new Error('这个提问已失效，请在电脑上重新操作')
      const accepted = await this.link.rpc('userQuestions/answer', { agentId: sessionId, callId, answer: { answers: normalized } })
      this.pendingQuestions.delete(callId)
      json(res, 200, { ok: true, via: 'rpc', accepted: accepted === true })
      return
    }
    if (path === '/api/models' && method === 'GET') {
      const catalog = normalizeModelCatalog(await this.link.rpc('session/modelCatalog', {}))
      let current = catalog.current
      const sessionId = url.searchParams.get('sessionId') ?? ''
      if (sessionId !== '') {
        try {
          const projection = await this.link.rpc('session/projections', { request: { sessionId } })
          const values = projection?.values ?? {}
          const selection = values.modelSelection?.next ?? values.modelSelection?.lastUsed
          if (selection !== undefined && selection !== null) {
            current = { provider: String(selection.provider ?? ''), model: String(selection.model ?? ''), effort: String(selection.reasoningEffort ?? '') }
          }
        } catch { /* 用目录 default */ }
      }
      json(res, 200, { ok: true, groups: catalog.groups, current })
      return
    }
    if (path === '/api/model' && method === 'POST') {
      const body = await readJsonBody(req)
      const request = { sessionId: String(body?.sessionId ?? ''), provider: String(body?.provider ?? ''), model: String(body?.model ?? '') }
      if (request.sessionId === '' || request.provider === '' || request.model === '') throw new Error('缺少 sessionId、provider 或 model')
      if (typeof body?.effort === 'string' && body.effort !== '') request.reasoningEffort = body.effort
      const value = await this.link.rpc('session/selectModel', { request })
      const selected = value?.selected ?? {}
      json(res, 200, { ok: true, provider: String(selected.provider ?? request.provider), model: String(selected.model ?? request.model), effort: String(selected.reasoningEffort ?? '') })
      return
    }

    // ---------- 信息面板 ----------
    if (path === '/api/live' && method === 'GET') {
      json(res, 200, await this.liveStatus())
      return
    }
    // ---------- 外部平台余额 ----------
    if (path === '/api/balance' && method === 'GET') {
      json(res, 200, { ok: true, deepseek: await this.deepseekBalance() })
      return
    }
    if (path === '/api/usage' && method === 'GET') {
      const raw = url.searchParams.get('days')
      const requested = raw === null || raw === '' ? 35 : Number(raw)
      // 上限放到 20 年：要的是"全部历史"，不是最近几天。
      const days = Number.isSafeInteger(requested) && requested > 0 ? Math.min(requested, 7300) : 3650
      try {
        // 直接扫会话文件算真实总量。插件的 /usage 只覆盖自己的统计窗口，拿不到完整历史。
        const scan = await this.usageScan()
        // 总量 = 本机扫描 + 在别处用的 327 亿
        const EXTERNAL = 32_700_000_000
        json(res, 200, {
          ok: true,
          known: true,
          totalTokens: scan.total + EXTERNAL,
          localTokens: scan.total,
          externalTokens: EXTERNAL,
          inputTokens: scan.input,
          outputTokens: scan.output,
          cacheReadTokens: scan.cacheRead,
          cacheWriteTokens: scan.cacheWrite,
          reasoningTokens: scan.reasoning,
          calls: scan.calls,
          models: scan.models,
          days: scan.days,
        })
      } catch (error) {
        json(res, 200, { ok: true, known: false, days: [], models: [], error: messageOf(error) })
      }
      return
    }

    // ---------- DeepSeek 通道 ----------
    if (path.startsWith('/api/ds/')) {
      await this.ds.handle(req, res, path, method)
      return
    }

    json(res, 404, { ok: false, error: '未知接口' })
  }

  startBeacon() {
    const socket = createSocket({ type: 'udp4', reuseAddr: true })
    socket.on('error', () => {})
    socket.bind(() => {
      socket.setBroadcast(true)
      const send = () => {
        const payload = Buffer.from(JSON.stringify({ service: SERVICE, port: this.port, name: 'DSH Watch V2' }))
        try { socket.send(payload, DISCOVERY_PORT, '255.255.255.255') } catch { /* ignore */ }
      }
      send()
      const timer = setInterval(send, BEACON_INTERVAL_MS)
      timer.unref?.()
    })
  }

  async start() {
    this.loadTokens()
    // 预热：确认能签票并连通 DSH
    const latencyStart = Date.now()
    await this.link.rpc('session/list', { _request: {} })
    this.log(`已连接 DSH（${this.auth.authority}），延迟 ${Date.now() - latencyStart}ms`)
    this.link.connectEvents()
    this.ds.restore()

    this.server = createServer((req, res) => {
      const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'relay'}`)
      this.route(req, res, url).catch((error) => {
        this.log(`路由错误 ${url.pathname}：${error.message}`)
        if (!res.headersSent) json(res, 500, { ok: false, error: messageOf(error) })
      })
    })
    await new Promise((resolve, reject) => {
      this.server.once('error', reject)
      // 绑 :: 走双栈：IPv4 和 IPv6 同时可达。
      // 国内移动宽带的 IPv6 是公网地址、入站不封，手表用流量可直连，无需端口转发。
      this.server.listen(this.port, '::', resolve)
    })
    this.startBeacon()
    return this.addresses()
  }
}

export { Relay, locateCredentialsHome, SERVICE }