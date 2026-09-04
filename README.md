# 万事屋

「万事屋」包含 Android 客户端与 Go 后端。后端提供答题领币、GPC授权发奖、APK 分发与内容服务；Android 端基于 Jetpack Compose 实现答题、题目答案、今天吃什么、设置等页面。

## 功能特性

### 后端（Go）

- **答题服务（Quiz）**：下发题目、接收作答、逐题判定并调用 GPC 发放奖励；支持「分期（period）」机制与每期每用户仅提交一次。
- **GPC OAuth 集成**：服务端代理完成 GPC 管理员登录、OAuth 客户端注册与令牌校验，并向 Android 端下发授权所需配置。
- **APK 管理与分发**：列出可用 APK、返回版本信息与更新日志，并提供文件下载。
- **Markdown 内容服务**：列出并读取 `outdate-test-markdown/` 目录下的 Markdown 文件。
- **静态文件服务**：以 gzip 压缩透传项目根目录的静态资源。
- **内存缓存**：对读取类数据做 60 秒 TTL 缓存。

### Android 应用

- **Jetpack Compose + Miuix 设计系统** 实现的原生界面。
- **四个主页面**：
  - 首页（限时答题领币）—— 金猪币 OAuth 授权、答题、逐题评判与奖励发放。
  - 题目答案 —— 答题解析与历史作答回看。
  - 今天吃什么 —— 转盘加权随机，权重 = 预算 ÷ 价格。
  - 设置 —— 主题、语言、检查更新、食物管理、开源许可证等。
- **答题体验**：作答草稿本地保存（按分期隔离）、离线回看、提交后展示逐题对错与 GPC 奖励总额。
- **自动更新**：启动时检查新版本并引导下载安装。
- **主题切换**：浅色 / 深色 / 跟随系统。
- **Markdown 渲染**：题目与内容支持内联 Markdown。

## 项目结构

```
gift/
├── android/              # Android 应用（Kotlin + Jetpack Compose）
│   ├── app/
│   │   └── src/main/
│   │       ├── java/com/chronie/gift/
│   │       │   ├── data/            # 数据管理（更新检查、主题、语言、食物、金猪币 OAuth 等）
│   │       │   ├── ui/
│   │       │   │   ├── components/  # 通用 UI 组件
│   │       │   │   ├── navigation/  # 导航（Nav3）与页签 Key
│   │       │   │   └── screens/     # 各页面（QuizScreen / AnswerKeysScreen / FoodScreen / SettingsScreen 等）
│   │       │   ├── MainActivity.kt
│   │       │   └── GiftApplication.kt
│   │       └── res/                 # 资源与多语言字符串
│   └── gradle/
├── server-go/            # Go 后端源码与可执行文件
│   ├── main.go           # 服务入口、路由、gzip 中间件、GPC 初始化
│   ├── handlers.go       # HTTP 处理器（APK、Markdown）
│   ├── quiz.go           # 答题逻辑、判定、奖励发放、GPC 调用
│   ├── gpc.go            # GPC 客户端（登录、OAuth、发币、令牌校验）
│   ├── cache.go          # 内存缓存与 JSON 读取
│   ├── go.mod
│   └── gift-server.exe   # 预编译可执行文件
├── server/               # 运行数据目录（由 server-go 在启动时读取，../server 相对路径）
│   ├── data/             # 业务数据（见「数据文件」）
│   └── apk/              # 待分发的 APK 文件
└── README.md
```

## 技术栈

### 后端

- **运行时**：Go 1.27.0
- **标准库**：`net/http`、`encoding/json`、`os`、`path/filepath`、`sync`
- **特性**：内置 HTTP 服务、gzip 压缩中间件、带 TTL 的内存缓存（60 秒）

### Android 应用

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose + Miuix KMP 设计系统
- **关键库**：
  - Ktor Client —— HTTP 请求
  - Coil —— 图片加载
  - Kotlinx Serialization —— JSON 序列化
  - Navigation3 —— 页面导航

## API 接口

所有响应均使用统一信封格式：

```json
{
  "success": true,
  "data": [ ... ]
}
```

### 答题（Quiz）

- `GET /api/quiz/questions` — 获取当前分期（period）与公开题目（不含答案）。
  响应示例：`{ "success": true, "period": "2026-09", "data": [ ... ] }`
- `POST /api/quiz/submit` — 提交作答，逐题发放 GPC 奖励。请求体：`{ "token": "<GPC访问令牌>", "answers": [ { "id": "q1", "value": 0 } ] }`。
  响应包含 `results`（每题对错与发放数）、`totalAwarded`（本次总奖励）；同一分期重复提交返回 `alreadySubmitted: true`。
- `GET /api/quiz/status?token=...` — 查询该用户当前分期是否已提交；已提交时一并返回历史作答与评判。
- `GET /api/oauth/gpc-config` — 下发 Android 端完成 GPC 授权码流程所需的客户端配置（clientId、clientSecret、gpcBaseUrl、redirectUri、scope）。

### APK 管理

- `GET /api/download_apk` — 获取可用 APK 列表与版本信息。
- `GET /api/download_apk/{filename}` — 下载指定 APK 文件。

`/api/download_apk` 响应额外包含版本信息：

```json
{
  "success": true,
  "data": ["app-release.apk"],
  "latest": "app-release.apk",
  "latestSize": "15.5",
  "versionCode": 100,
  "versionName": "1.0.0",
  "changelog": {
    "en": "Version notes",
    "zh-cn": "版本说明"
  }
}
```

### Markdown 内容

- `GET /api/outdate-test/markdown` — 列出 Markdown 文件。
- `GET /api/outdate-test/markdown/{filename}` — 获取指定 Markdown 文件内容。

## 数据文件（`server/data/`）

| 文件 | 说明 |
|------|------|
| `quiz.json` | 当前分期与题目数组（顶层 `period` + `questions`）。换期只需修改 `period` 并替换 `questions`。 |
| `quiz_submissions.json` | 每用户每分期的提交记录（作答 + 评判），用于回看与「每期仅提交一次」校验。 |
| `quiz_claims.json` | 发币幂等记录，防止同一题重复发奖。 |
| `changelog.json` | 版本更新日志（多语言）。 |
| `gpc_config.json` | GPC 服务端连接配置（地址、管理员账号、OAuth 客户端名/作用域/回调）。 |
| `gpc_oauth_client.json` | 首次注册后持久化的 GPC OAuth 客户端凭据（clientId / clientSecret）。 |
| `outdate-test-markdown/` | Markdown 内容目录。 |

`quiz.json` 题目字段说明（答案仅存于服务端，不下发给客户端）：

```json
{
  "period": "2026-09",
  "questions": [
    {
      "id": "q1",
      "type": "single",
      "content": "题干（支持 Markdown/公式）",
      "options": ["A", "B", "C", "D"],
      "answer": 0,
      "reward": 10
    }
  ]
}
```

- `type`：`single`（单选）/`bool`（判断）/`multiple`（多选）。
- `options`：单选/多选项为 `['A','B','C','D']`；判断为 `[]`。
- `answer`：单选/多选取正确选项**索引**；判断取 `true/false`。
- `reward`：答对发放的 GPC 数量。

`gpc_config.json` 示例（缺省时由 `main.go` 提供默认值）：

```json
{
  "baseUrl": "https://gpc.example.com",
  "adminUser": "admin",
  "adminPass": "admin888",
  "oauthClientName": "gift-app",
  "oauthScope": "quiz",
  "redirectUri": "gift://oauth/callback"
}
```

`changelog.json` 示例：

```json
{
  "changelog": {
    "en": "Version notes in English",
    "zh-cn": "中文（简体）版本说明",
    "zh-tw": "中文（繁體）版本說明",
    "ja": "バージョンノート"
  }
}
```

> 服务端仅在启动时读取一次 `quiz.json` 与各账本，修改数据后需**重启服务**才能生效。详见 `问卷换期维护指南.md`。

## 配置

### 服务端口

通过环境变量 `PORT` 配置，默认 `3002`：

```bash
PORT=3002 ./gift-server.exe
```

### 安卓端 API 地址

后端地址在 Android 代码中硬编码，默认 `http://192.168.10.9:3002`：

- 更新检查：`UpdateChecker.kt` 的 `apiBaseUrl`
- 答题与 OAuth：`QuizScreen.kt` 的 `QUIZ_BASE_URL`

部署到新环境时，请同步修改这两处常量。

### GPC 配置

将 GPC 连接信息写入 `server/data/gpc_config.json`（见上）。缺失字段由 `main.go` 提供默认值；OAuth 客户端凭据首次启动后自动注册并持久化到 `gpc_oauth_client.json`。

## 构建与运行

### 后端

```bash
cd server-go

# 构建（Windows）
go build -o gift-server.exe

# 启动（默认监听 http://0.0.0.0:3002，数据目录为 ../server）
./gift-server.exe
```

跨平台构建：

```bash
# Linux
GOOS=linux GOARCH=amd64 go build -o gift-server

# macOS
GOOS=darwin GOARCH=arm64 go build -o gift-server
```

> 服务从 `server-go/` 目录读取 `../server/data` 与 `../server/apk`，请在该目录下启动。

### Android 应用

1. 用 Android Studio 打开 `android/` 目录，等待 Gradle 同步完成。
2. 构建并运行到模拟器或真机。
3. 发布 APK 时，将生成的 `app-release.apk` 放入 `server/apk/`，并确认 `server/apk/output-metadata.json` 中的 `versionCode` / `versionName` 已更新，以便更新检查生效。

## 版本管理

### Android 版本号

采用基于时间戳的版本方案：`1.YYYYMMDD.HHMM`（例如 `1.20260203.0031`），构建时按系统时间自动生成。

### 版本信息接口

后端从 `server/apk/output-metadata.json` 读取版本信息：

```json
{
  "elements": [
    {
      "outputFile": "app-release.apk",
      "versionCode": 100,
      "versionName": "1.0.0"
    }
  ]
}
```

Android 端通过 `GET /api/download_apk` 获取该信息并对比自身版本，提示用户更新。
