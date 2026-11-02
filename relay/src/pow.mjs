// PoW 求解：官方 sha3 wasm（wasm_solve 快速路径 + wasm_deepseek_hash_v1 暴力搜索回退）。
//
// 实测事实（改代码前先读）：
// 1) WebAssembly.Instance(module, {}) —— 该 wasm 无导入，不需要 glue 层。
// 2) challenge 不是"难度题"，而是**答案的哈希**：
//      challenge === SHA3(prefix + answer)，prefix = salt + '_' + expire_at + '_'
//    因此 wasm_solve 无解时必须用 hash 反过来暴力找原像。
// 3) 找不到就抛错，绝不返回"看起来像答案"的值——带着无效 PoW 请求上游只会得到难懂的错误。

import { readFileSync } from 'node:fs'
import { join } from 'node:path'

let instance

function powWasm() {
  if (instance !== undefined) return instance
  const file = join(import.meta.dirname, '..', 'wasm', 'sha3_wasm_bg.7b9ca65ddd.wasm')
  const bytes = readFileSync(file)
  const module = new WebAssembly.Module(bytes)
  const wasm = new WebAssembly.Instance(module, {})
  const { exports } = wasm
  if (typeof exports.wasm_solve !== 'function' || typeof exports.memory !== 'object') {
    throw new Error('PoW 运算文件缺少 wasm_solve / memory 导出')
  }
  instance = { exports, file }
  return instance
}

function writeString(exports, text) {
  const data = new TextEncoder().encode(text)
  const ptr = exports.__wbindgen_export_0(data.length, 1)
  new Uint8Array(exports.memory.buffer).set(data, ptr)
  return [ptr, data.length]
}

function runWasmSolve(exports, challenge, prefix, difficulty) {
  const retptr = exports.__wbindgen_add_to_stack_pointer(-16)
  try {
    const [cp, cl] = writeString(exports, challenge)
    const [pp, pl] = writeString(exports, prefix)
    exports.wasm_solve(retptr, cp, cl, pp, pl, difficulty)
    const view = new DataView(exports.memory.buffer)
    if (view.getInt32(retptr, true) === 0) return undefined
    const answer = view.getFloat64(retptr + 8, true)
    return Number.isFinite(answer) ? Math.trunc(answer) : undefined
  } catch {
    return undefined
  } finally {
    exports.__wbindgen_add_to_stack_pointer(16)
  }
}

function wasmHash(exports, text) {
  const retptr = exports.__wbindgen_add_to_stack_pointer(-16)
  try {
    const [ptr, len] = writeString(exports, text)
    exports.wasm_deepseek_hash_v1(retptr, ptr, len)
    const view = new DataView(exports.memory.buffer)
    const strPtr = view.getInt32(retptr, true)
    if (strPtr <= 0 || strPtr >= exports.memory.buffer.byteLength) return ''
    const bytes = new Uint8Array(exports.memory.buffer, strPtr, 96)
    let end = 0
    while (end < bytes.length && bytes[end] !== 0) end += 1
    return Buffer.from(bytes.subarray(0, end)).toString('utf8')
  } catch {
    return ''
  } finally {
    exports.__wbindgen_add_to_stack_pointer(16)
  }
}

/** 求解一道 PoW 挑战，返回 answer（整数）。 */
export function solvePow(task) {
  const { exports } = powWasm()
  const prefix = task.salt + '_' + String(task.expire_at) + '_'
  const fast = runWasmSolve(exports, task.challenge, prefix, Number(task.difficulty) || 0)
  if (fast !== undefined && fast !== 0) return fast
  for (let candidate = 0; candidate < 2_000_000; candidate += 1) {
    if (wasmHash(exports, prefix + String(candidate)) === task.challenge) return candidate
  }
  throw new Error('PoW 求解失败（algorithm=' + String(task.algorithm) + '）')
}

/** PoW 结果转成上游需要的请求头值。 */
export function powHeaderValue(task, answer) {
  return Buffer.from(JSON.stringify({
    algorithm: task.algorithm,
    challenge: task.challenge,
    salt: task.salt,
    answer,
    signature: task.signature,
    target_path: task.target_path,
  }), 'utf8').toString('base64')
}
