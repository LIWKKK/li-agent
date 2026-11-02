// 数据裁剪层：把 DSH 的原始响应压成手表形状。未知字段一律丢弃，数字一律 Number。
import { basename } from 'node:path'

export const MAX_MESSAGES = 40
export const MAX_SESSIONS = 60

const TOOL_LABELS = {
  pwsh: '命令', bash: '命令', read: '读文件', edit: '改文件', write: '写文件',
  grep: '搜索', glob: '找文件', skill: '技能', subagent: '子任务', subagent_fork: '子任务',
  web_search: '联网', web_fetch: '联网', run_code: '执行', todo_write: '清单',
  ask_user_question: '提问', update_goal: '目标', present: '交付',
}

const num = (v) => (typeof v === 'number' && Number.isFinite(v) ? v : 0)
const str = (v) => (typeof v === 'string' ? v : '')
const clip = (text, limit = 4000) => {
  const value = typeof text === 'string' ? text.trim() : ''
  return value.length <= limit ? value : value.slice(0, limit) + '…'
}

function textOfContent(content) {
  if (!Array.isArray(content)) return ''
  return content.filter((b) => b?.type === 'text' && typeof b.text === 'string').map((b) => b.text).join('\n').trim()
}

function toolsOfContent(content) {
  if (!Array.isArray(content)) return []
  const names = []
  for (const block of content) {
    if (block?.type !== 'tool-call') continue
    const label = TOOL_LABELS[block.name] ?? (typeof block.name === 'string' && block.name !== '' ? block.name : '工具')
    if (!names.includes(label)) names.push(label)
  }
  return names
}

function thinkingOfContent(content) {
  if (!Array.isArray(content)) return ''
  return content.filter((b) => b?.type === 'reasoning' && typeof b.text === 'string').map((b) => b.text).join('\n').trim()
}

export function isHuman(data) {
  const kind = data?.source?.kind
  if (kind === undefined) return data?.message?.role === 'user' || data?.role === 'user'
  return kind === 'user' || kind === 'user-question-reply'
}

export function normalizeSessionList(value, includeSubagents = false) {
  const items = Array.isArray(value?.items) ? value.items : []
  const out = []
  for (const item of items) {
    const values = item?.projections?.values ?? {}
    const cwd = str(item?.cwd)
    const model = values.modelSelection?.next ?? values.modelSelection?.lastUsed
    const title = str(values.title).trim() || (cwd !== '' ? basename(cwd) : '未命名会话')
    const active = values.userQuestions?.active
    const entry = Array.isArray(active) && active.length > 0 ? active[0] : undefined
    const questions = Array.isArray(entry?.questions) ? entry.questions : []
    out.push({
      id: str(item?.sessionId),
      title,
      cwd,
      project: cwd !== '' ? basename(cwd) : '未分组',
      projectPath: cwd,
      running: item?.running === true,
      waiting: questions.length > 0,
      // 空壳会话（blank）：DSH 会为各种入口预建会话，从未有过对话，界面上不该出现。
      blank: item?.blank === true,
      subagent: item?.origin === 'subagent',
      parentId: str(item?.parentSessionId),
      updatedAt: num(item?.updatedAt),
      seq: num(item?.projections?.asOfSeq),
      model: str(model?.model),
      provider: str(model?.provider),
      effort: str(model?.reasoningEffort),
      subagentCount: 0,
      subagentRunning: 0,
    })
  }
  out.sort((a, b) => b.updatedAt - a.updatedAt)
  // 子代理计数
  const counts = new Map()
  for (const item of out) {
    if (!item.subagent || item.parentId === '') continue
    const c = counts.get(item.parentId) ?? { total: 0, running: 0 }
    c.total += 1
    if (item.running) c.running += 1
    counts.set(item.parentId, c)
  }
  for (const item of out) {
    const c = counts.get(item.id)
    if (c) { item.subagentCount = c.total; item.subagentRunning = c.running }
  }
  return includeSubagents
    ? out.filter((s) => !s.blank || s.subagent)
    : out.filter((s) => !s.subagent && !s.blank)
}

export function questionsOf(value) {
  const active = value?.projections?.values?.userQuestions?.active
  if (!Array.isArray(active) || active.length === 0) return []
  const entry = active[0]
  const list = Array.isArray(entry?.questions) ? entry.questions : []
  if (list.length === 0) return []
  return [{
    callId: str(entry?.callId),
    state: str(entry?.state) || 'open',
    questions: list.map((q) => ({
      id: str(q?.id),
      header: str(q?.header),
      question: str(q?.question),
      multiSelect: q?.multiSelect === true,
      options: (Array.isArray(q?.options) ? q.options : []).map((o) => ({
        label: str(o?.label), description: str(o?.description),
      })).filter((o) => o.label !== ''),
    })),
  }]
}

export function normalizeMessages(records) {
  const messages = []
  let pending = undefined
  const flush = () => {
    if (pending !== undefined && (pending.text !== '' || pending.thinking !== '' || pending.tools.length > 0)) messages.push(pending)
    pending = undefined
  }
  for (const record of Array.isArray(records) ? records : []) {
    const event = record?.event
    if (event === null || event === undefined) continue
    const data = event.data ?? {}
    if (event.type === 'user/message') {
      if (!isHuman(data)) continue
      flush()
      const message = data.message ?? data
      const text = textOfContent(message?.content)
      if (text === '') continue
      messages.push({ role: 'user', text: clip(text), thinking: '', tools: [], time: num(event.time) })
      continue
    }
    if (event.type === 'assistant/message') {
      const message = data.message ?? {}
      if (pending === undefined) pending = { role: 'assistant', text: '', thinking: '', tools: [], time: num(event.time) }
      const text = textOfContent(message?.content)
      const thinking = thinkingOfContent(message?.content)
      if (text !== '') pending.text = pending.text === '' ? text : pending.text + '\n\n' + text
      if (thinking !== '') pending.thinking = pending.thinking === '' ? thinking : pending.thinking + '\n' + thinking
      for (const tool of toolsOfContent(message?.content)) if (!pending.tools.includes(tool)) pending.tools.push(tool)
      pending.time = num(event.time)
      continue
    }
    if (event.type === 'turn/end') flush()
  }
  flush()
  return messages.slice(-MAX_MESSAGES)
}

export function normalizeUsage(values) {
  const tokens = values?.tokenUsage ?? {}
  const pressure = values?.contextPressure ?? {}
  const stats = values?.sessionStats ?? {}
  const model = values?.modelSelection?.next ?? values?.modelSelection?.lastUsed
  const window = num(pressure.contextWindow)
  const used = num(pressure.pressureTokens)
  return {
    inputTokens: num(tokens.uncachedInputTokens),
    outputTokens: num(tokens.outputTokens),
    cacheReadTokens: num(tokens.cacheReadTokens),
    cacheWriteTokens: num(tokens.cacheWriteTokens),
    contextWindow: window,
    usedTokens: used,
    percent: window > 0 ? Math.round((used / window) * 1000) / 10 : 0,
    turns: num(stats.turns),
    steps: num(stats.steps),
    llmMs: num(stats.llmMs),
    model: str(model?.model),
    provider: str(model?.provider),
    effort: str(model?.reasoningEffort),
  }
}

export function normalizeModelCatalog(catalog) {
  const groups = Array.isArray(catalog?.groups) ? catalog.groups : []
  const routable = new Set(Array.isArray(catalog?.routableProviders) ? catalog.routableProviders : [])
  const out = []
  for (const group of groups) {
    const provider = str(group?.id)
    if (provider === '' || (routable.size > 0 && !routable.has(provider))) continue
    const models = []
    for (const model of Array.isArray(group?.models) ? group.models : []) {
      const id = str(model?.id)
      if (id === '') continue
      const reasoning = model?.reasoning ?? {}
      const efforts = (Array.isArray(reasoning?.efforts) ? reasoning.efforts : [])
        .map((e) => str(e?.id)).filter((e) => e !== '')
      models.push({
        id,
        name: str(model?.name) || id,
        efforts,
        defaultEffort: str(reasoning?.defaultEffort) || (efforts[0] ?? ''),
      })
    }
    if (models.length > 0) out.push({ provider, name: str(group?.name) || provider, models })
  }
  const def = catalog?.default ?? {}
  return {
    groups: out,
    current: { provider: str(def?.provider), model: str(def?.model), effort: str(def?.reasoningEffort ?? def?.effort) },
  }
}

// 用量热力图：dsh-claude-style 插件的 /usage 聚合 → 日列表。
export function normalizeUsageActivity(payload, days = 35) {
  const value = payload?.value
  const sourceDays = Array.isArray(value?.days) ? value.days : []
  const sourceModels = Array.isArray(value?.models) ? value.models : []
  const safe = (v) => (typeof v === 'number' && Number.isSafeInteger(v) && v >= 0 ? v : 0)
  return {
    known: payload?.ok === true && Array.isArray(value?.days) && value?.unavailable !== true,
    days: sourceDays.slice(-days).map((day) => {
      const inputTokens = safe(day?.input)
      const outputTokens = safe(day?.output)
      return {
        date: str(day?.date),
        inputTokens,
        outputTokens,
        // 完整口径：缓存读也是 token 用量的一部分（实测占 98%，3.22 亿 / 3.29 亿）。
        // 之前剔掉缓存是根据错误的假设，实际那才是主体。
        totalTokens: inputTokens + outputTokens + safe(day?.cacheRead) + safe(day?.cacheWrite),
      }
    }).filter((day) => day.date !== ''),
    models: sourceModels.map((m) => ({ model: str(m?.id), tokens: safe(m?.tokens) })).filter((m) => m.model !== ''),
  }
}
