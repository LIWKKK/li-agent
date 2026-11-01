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
- `deepseek-chat` / `deepseek-reasoner`（深度思考）双模型一键切换
- 气泡内实时显示生成速度（t/s）、首字延迟、总耗时
- 聊天记录本地持久化，断网也能翻历史

### 🤖 Harness 会话（指挥电脑上的 Agent）
- 浏览电脑上的工作区 / 项目列表，实时显示运行中的会话数
- 在项目下新建会话、发消息、轮询接收 Agent 回复
- 会话状态实时同步：运行中 / 等待回答 / 空闲，进会话自动续接轮询

### 🖥️ Information（电脑监控面板，15 秒自动刷新）
- **系统**：主机名、平台、架构、开机时长
- **硬件**：CPU 型号与负载、内存 / Swap 进度条、温度、进程数
- **磁盘**：各挂载点用量
- **DSH**：中继延迟、会话总数 / 运行中 / 待答 / 子任务数

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
| **省电** | 轮询 3 分钟自动停、退后台停、回前台自动续 |
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

## 🚀 构建与安装

需要 JDK 17 + Android build-tools 34 + android-34 platform（路径在 `build.sh` 顶部改）：

```bash
bash build.sh          # 产出 WatchAI.apk（约 93 KB）
adb install WatchAI.apk
```

先在 `Net.java` 里填上你自己的中继地址和令牌：

```java
static final String BASE_LAN = "http://192.168.x.x:19490";
static final String TOKEN    = "your-relay-token";
```

手表需与电脑同一局域网，或具备 IPv6 出网能力（中继地址自动学习）。

## License

见 [LICENSE](LICENSE)。
