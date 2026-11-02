// V2 中继入口：解析参数并启动。
import { Relay, locateCredentialsHome } from './relay.mjs'

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, item, index, all) => {
  if (item.startsWith('--')) {
    const next = all[index + 1]
    acc.push([item.slice(2), next !== undefined && !next.startsWith('--') ? next : 'true'])
  }
  return acc
}, []))

const port = Number(process.env.RELAY_PORT ?? args.port ?? 19490)
const dshPort = Number(process.env.DSH_PORT ?? args['dsh-port'] ?? 3080)
const dshHome = locateCredentialsHome(args['dsh-home'])
if (dshHome === null) { console.error('[relay] 找不到 DSH 凭据目录（.credentials.yaml）'); process.exit(1) }
const stateFile = process.env.RELAY_STATE ?? args['state-file'] ?? (process.env.HOME + '/.dsh-watch-v2.json')

const relay = new Relay({ port, dshPort, dshHome, stateFile })
relay.start().then((addresses) => {
  console.log('[relay] 监听 0.0.0.0:' + String(port))
  for (const address of addresses) console.log('[relay]   http://' + address + ':' + String(port))
  console.log('[relay] 配对码：' + relay.pairingCode)
}).catch((error) => {
  console.error('[relay] 启动失败：' + error.message)
  process.exit(1)
})

const shutdown = () => { relay.link.close(); process.exit(0) }
process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)
