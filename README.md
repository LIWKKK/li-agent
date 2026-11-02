# LI Agent (WatchAI)

[![size](https://img.shields.io/badge/APK-93KB-success)]() [![minSdk](https://img.shields.io/badge/minSdk-23-blue)]() [![targetSdk](https://img.shields.io/badge/targetSdk-30-blue)]() [![deps](https://img.shields.io/badge/第三方依赖-0-brightgreen)]()

> 一款为 **Android 手表** 打造的 AI 客户端：纯 Java + 原生 View 手写 UI，整包 93 KB，专为圆形小屏设计。抬腕指挥电脑上的 [DeepSeek Harness](https://github.com/deepseek-ai) 干活——手表是遥控器，干重活的是电脑。

```
WatchAI.apk · minSdk 23 · targetSdk 30 · standalone Wear app
```

由 **liweike** 制作 · v0.3 · 仓库中的地址与令牌均为占位符

---

## ✨ 功能

### 💬 DeepSeek Chat（SSE 流式对话）
- 直连电脑中继上的 DeepSeek 网页会话，**逐字流式输出**
- **联网搜索默认开启**：能回答实时类问题（天气、新闻、赛况）
- **深度思考可切**：DeepSeek V3（快速回答）/ R1（深度思考）真实模型切换
- 思考中的会话气泡显示「正在思考…」状态
- 气泡内实时显示生成速度（t/s）、首字延迟、总耗时
- 聊天记录本地持久化，断网也能翻历史

### 🤖 Harness 会话（指挥电脑上的 Agent）
- 浏览电脑上的工作区 / 项目列表，实时显示运行中的会话数
- 在项目下新建会话、发消息、轮询接收 Agent 回复
- 模型芯片切换 DSH 的**全部真实模型**（多 provider 目录动态拉取，切换直达 DSH）
- 会话状态实时同步：运行中 / 等待回答 / 空闲，进会话自动续接轮询

### 🗑️ 长按删除会话
- 工作区列表、对话列表、对话页标题——**长按**任意一条即弹出确认
- 确认框会提示「正在运行，删除会立即中断」，避免误删跑着的任务
- Harness 会话走中继 `/api/delete` → `dsh-session-manager` 插件，连带删除子智能体、清理投影缓存与磁盘目录
- DeepSeek 本地会话只删本机记录与消息体，不触碰服务器

### 🖥️ Information（电脑监控面板，15 秒自动刷新）
- **系统**：主机名、平台、架构、开机时长
- **硬件**：CPU 型号与负载、内存 / Swap 进度条、温度、进程数
- **磁盘**：各挂载点用量
- **DSH**：中继延迟、会话总数 / 运行中 / 待答 / 子任务数
- 实时区与用量区独立重建，刷新时数字原地更新、列表不会重复堆叠

### 📊 用量统计
- 环形图展示今日 token 占历史总量的比例（带动画）
- **GitHub 风格热力图**：最多 53 周的每日用量，4 档色阶
- 输入 / 输出 / 缓存读 / 本机与外部用量分项条形图

## 🔧 为手表做的工程细节

| 细节 | 说明 |
|---|---|
| **双链路竞速** | 局域网 IPv4 + 公网 IPv6 并发，GET 谁先回用谁；POST 单路防重复提交 |
| **IPv6 直连** | 出门走移动流量无需端口转发，中继上报地址自动学习，冷启动用上次的"好地址" |
| **离线缓存** | 列表数据全部落盘，断网秒开上次快照 |
| **幂等** | 占位气泡去重、自己发的消息不重复渲染、4xx 业务错误不换地址重试 |
| **实时刷新** | 每个页面自带前台 `Ticker`：工作区/对话列表 5 秒、对话页空闲 6 秒/运行 2 秒、监控 15 秒、用量 20 秒，退后台即停 |
| **增量渲染** | 列表按内容指纹比对，数据没变就不重画，避免定时刷新时闪屏 |
| **省电** | 只在页面可见时轮询，onPause 立刻停 |
| **全手写 UI** | RingView / HeatmapView / BarView / IconView / EdgeScrollView，Claude 风格衬线排版，无 XML 布局、无图片资源 |

## 🏗️ 架构

```
app (手表)                         电脑
┌──────────────┐   HTTP/SSE    ┌──────────────────┐
│  8 个 Java   │ ────────────→ │  DSH 中继 :19490  │
│  Activity    │  Bearer Token │   ├ /api/live     │ 系统监控
└──────────────┘               │   ├ /api/usage    │ token 统计
   Net.java：                  │   ├ /api/ds/send  │ DeepSeek SSE
   竞速+缓存+流式               │   └ /api/send     │ Harness 会话
```

| 模块 | 职责 |
|---|---|
| `Net.java` | 网络层：LAN/IPv6 竞速、地址记忆、SSE 流解析、离线缓存回退 |
| `ChatActivity` | 双模式对话、模型切换、用量面板 |
| `InformationActivity` | 电脑监控 + token 热力图 |
| `UsageActivity` | 今日用量环形图 |
| `WorkspacesActivity` / `ConversationsActivity` | 工作区 → 会话两级导航 |
| `Ui.java` + 自绘 View | 全部视觉 |

## 🚀 部署

两半都要装：**电脑上的中继**（手表的数据来源）+ **手表上的 APK**。

### 一、中继（电脑侧，一键）

```bash
git clone https://github.com/LIWKKK/li-agent.git
cd li-agent

./deploy.sh                    # 交互式，会问端口/DSH_HOME/公网地址
# 或无人值守：
./deploy.sh --port 19490 --dsh-home ~/.dsh --yes
```

脚本做四件事：校验环境 → 生成 `relay/.env` → 起服务并跑健康检查 → 装每 5 分钟一次的保活 cron。
跑完会打印**配对码**（手表配对时输入）。

依赖只有 `node >= 20` 和 `curl`，中继**零 npm 依赖**，不需要 `npm install`。

常用命令：

```bash
./deploy.sh --status           # 看运行状态 + 健康检查
./deploy.sh --restart          # 重启
./deploy.sh --stop             # 停止
./deploy.sh --uninstall        # 卸掉自启（保留配置和源码）

relay/relay-daemon.sh logs     # 看日志
relay/relay-keepalive.sh       # 手动跑一次保活（cron 每分钟自动跑）
```

**保活机制**：cron 每分钟检查 `/api/status`，非 200 就自动拉起。实测强杀进程后 3 秒内恢复，
日志在 `relay/keepalive.log`。DSH 重启、机器重启、进程崩溃都会自愈。

### 二、网络连接原理（重要，出问题先看这节）

手表连电脑有两条路，App 会自动挑能用的：

| 路径 | 地址 | 适用 | 备注 |
|---|---|---|---|
| 局域网 | `BASE_LAN` | 同一 WiFi | 最稳，优先 |
| IPv6 直连 | `http://[ipv6]:19490` | 出门在外 / 蜂窝 | 唯一的公网路径 |
| 上次成功 | `lastGood` | 冷启动 | 排最前，避免撞死地址 |

**IPv6 地址怎么来的**：中继在 `/api/status` 里上报 `ipv6`（取第一个非链路本地地址），
App 每次连上就记住并落盘，下次出门直接用。

**三个必须知道的坑**：

1. **`IPV6_DEFAULT` 不能是假地址**
   曾经填的是 `2001:db8::relay`——那是 RFC 3849 的文档示例段，公网永远不可达。
   冷启动没缓存时会去连它，表现就是"出门必连不上"。填真实 IP。

2. **`publicUrl` 这条路不存在**
   早期配过 `https://fnos.net/.../li-agent`，实测该域名任何路径都返回 FN Connect
   登录页 HTML（不是隧道），已从代码和配置里彻底删除。别再配它。

3. **IPv6 是动态的**
   运营商给的地址 `preferred_lft` 只有 30–60 分钟，会轮换。地址一变，手表存的
   旧地址立刻失效。要长期稳定必须上 DDNS（见下）。

### 三、IPv6 长期稳定（可选但推荐）

`ipv6-ddns.sh` 已实现：检测地址变化 → 推给 dynv6 → 手表只认域名。

```bash
# 去 https://dynv6.com 免费注册 → 创建 zone → 拿 token，填进 ddns.conf
DYNV6_HOST="yourname.dynv6.net"
DYNV6_TOKEN="your_token"
```

cron 已配置每 5 分钟跑一次。配好后把 `Net.java` 的 `IPV6_DEFAULT`
改成域名解析出的地址即可（或直接改用域名访问）。

### 四、手表 APK

需要 JDK 17 + Android build-tools 34 + android-34 platform（路径在 `build.sh` 顶部改）：

```bash
bash build.sh                  # 产出 WatchAI.apk（约 97 KB）
adb install WatchAI.apk
```

装之前先在 `Net.java` 里填上你的中继地址和令牌（令牌由 `./deploy.sh` 首次启动时生成在
`~/.dsh-watch-v2.json` 的 `tokens` 里）：

```java
static final String BASE_LAN = "http://192.168.x.x:19490";
static final String TOKEN    = "your-relay-token";
```

手表与电脑同一局域网即可；出门则靠 IPv6 直连（中继地址自动学习并落盘，运营商换前缀也不用手改）。
若走公网反代，把中继挂到 DSH 的 `/li-agent` 前缀下（见 `li-agent-bridge` 插件），
`BASE_LAN` 填那个公网 URL 即可。

### 五、华为手表安装（重要）

华为穿戴设备有安装白名单，直接 `adb install` 会报 `INSTALL_FAILED_ABORTED: User rejected permissions`：

```bash
# 先查出本机已装的签名哈希格式
adb shell "settings get secure hdc_install_allow_list"

# 把你的包名#签名哈希追加进去（65 位十六进制，无冒号），保留原有条目
adb shell "settings put secure hdc_install_allow_list '原有条目,你的包名#你的签名哈希'"
adb install WatchAI.apk
```

**每次手表重启后白名单都会重置**，重装前要重新写一遍。签名哈希取自你的 keystore：

```bash
keytool -list -v -keystore ~/.li-agent-signing/li-agent.keystore -alias 你的别名 \
  | grep SHA256 | awk '{print $2}' | tr -d ':' | tr 'A-F' 'a-f'
```

## License

见 [LICENSE](LICENSE)。