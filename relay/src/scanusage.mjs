import { readFile, readdir } from 'node:fs/promises'
import { join } from 'node:path'
import zlib from 'node:zlib'

const MAGIC = Buffer.from([0x28, 0xB5, 0x2F, 0xFD])

/** 逐帧解压多帧 zstd。DSH 的会话文件每个 turn 追加一帧，直接解会只拿到第一帧。 */
function decompressAll(buf) {
  const chunks = []
  let pos = 0
  while (pos < buf.length) {
    const idx = buf.indexOf(MAGIC, pos)
    if (idx < 0) break
    const next = buf.indexOf(MAGIC, idx + 4)
    const end = next < 0 ? buf.length : next
    try { chunks.push(zlib.zstdDecompressSync(buf.subarray(idx, end))) } catch { /* 跳过坏帧 */ }
    pos = end
  }
  return Buffer.concat(chunks)
}

async function walk(dir, out = []) {
  let items
  try { items = await readdir(dir, { withFileTypes: true }) } catch { return out }
  for (const item of items) {
    const full = join(dir, item.name)
    if (item.isDirectory()) await walk(full, out)
    else if (item.name.startsWith('session.v4.jsonl')) out.push(full)
  }
  return out
}

/**
 * 扫描全部会话文件，累计真实 token 用量。
 *
 * 为什么不用插件的 /dsh-claude-style/usage：那个接口的 models 是插件自己的
 * 统计窗口，days 只覆盖最近几天，都拿不到完整历史。会话文件里每条
 * assistant/message 都带 usage，逐条累加才是真实总量。
 *
 * 注意：缓存读也是 token 用量的一部分——实测占 98%（3.25 亿 / 3.32 亿），
 * 剔掉它数字会严重偏小。
 */
export async function scanUsage(home) {
  const files = await walk(join(home, 'sessions'))
  let input = 0, output = 0, cacheRead = 0, cacheWrite = 0, reasoning = 0, calls = 0
  const byModel = new Map()
  const byDay = new Map()

  for (const file of files) {
    let raw
    try {
      const buf = await readFile(file)
      raw = file.endsWith('.zstd') ? decompressAll(buf) : buf
    } catch { continue }

    for (const line of raw.toString('utf8').split('\n')) {
      if (line.trim() === '') continue
      let obj
      try { obj = JSON.parse(line) } catch { continue }
      if (obj?.type !== 'assistant/message') continue
      const usage = obj?.data?.usage
      if (usage === undefined || usage === null) continue
      const i = Number(usage.inputTokens ?? 0)
      const o = Number(usage.outputTokens ?? 0)
      const cr = Number(usage.cacheReadTokens ?? 0)
      const cw = Number(usage.cacheWriteTokens ?? 0)
      if (i + o + cr + cw === 0) continue
      input += i; output += o; cacheRead += cr; cacheWrite += cw
      reasoning += Number(usage.reasoningTokens ?? 0)
      calls += 1

      const model = String(obj?.data?.model ?? obj?.data?.provider ?? 'unknown')
      byModel.set(model, (byModel.get(model) ?? 0) + i + o + cr + cw)
      const day = new Date(Number(obj.time ?? 0)).toISOString().slice(0, 10)
      byDay.set(day, (byDay.get(day) ?? 0) + i + o + cr + cw)
    }
  }

  const total = input + output + cacheRead + cacheWrite
  return {
    calls,
    input,
    output,
    cacheRead,
    cacheWrite,
    reasoning,
    total,
    models: [...byModel].sort((a, b) => b[1] - a[1]).map(([model, tokens]) => ({ model, tokens })),
    days: [...byDay].sort((a, b) => a[0].localeCompare(b[0])).map(([date, tokens]) => ({ date, tokens })),
  }
}
