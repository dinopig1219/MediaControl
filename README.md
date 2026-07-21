# 媒体控制通知 (Media Control Notification)

用一条独立的通知，把被 HyperOS 在媒体通知卡片隐藏掉的 Spotify 播放控件（智能随机播放 / 随机播放 / 收藏等）重新显示出来，点击后直接转发给 Spotify 本体。

## 背景

HyperOS 对 Spotify 的媒体通知卡片只固定显示上一首/播放暂停/下一首这几个按钮，Spotify 通过 `MediaSession` 暴露出来的其它操作（随机播放、收藏等）无法在 HyperOS 的媒体通知卡片上显示出来，不是 Spotify 没给，是系统的白名单机制把它给隐藏掉了。

本 App 不需要 root，也不修改 HyperOS 本身，而是另开一条自己的通知，把 Spotify 提供的全部操作显示出来。

## 工作原理

1. `MediaControlListenerService`（`NotificationListenerService` 的子类）通过 `MediaSessionManager` 拿到 Spotify 当前的 `MediaController`。
2. 每次播放状态变化，读取 `PlaybackState.getCustomActions()`，把 Spotify 自己提供的按钮（名字、图标、action id）原样渲染进通知，不自己猜测状态、不自己画按钮——按钮叫什么、长什么样、点了之后变成什么，完全由 Spotify 决定，我们只是把它显示出来。
   - 图标使用 `IconCompat` 跨包读取 Spotify 自己 App 内的资源，因为这些图标 ID 属于 Spotify，不属于本 App。
3. 按钮点击由 `MediaActionReceiver` 处理：标准操作（播放/暂停/上一首/下一首）直接调用 `MediaController.TransportControls`；其余按钮统一走 `sendCustomAction()`，把 Spotify 给的 action 字符串原封不动转发回去。

这也是为什么“随机播放”按钮点击后，Spotify 后续可能会把它换成“智能随机播放”这类不同状态——那是 Spotify 自己在动态更新它暴露的 custom action，本 App 只是如实转发。

## 项目结构

- `MainActivity.kt` — 引导授权（通知权限 + 通知使用权），并提供“调试通知开关”和“查看调试信息”入口
- `MediaControlListenerService.kt` — 核心逻辑，读取 Spotify 状态并生成通知
- `MediaActionReceiver.kt` — 处理按钮点击，转发给 Spotify
- `DebugActivity.kt` — App 内查看完整调试信息（不受通知长度限制）

## 使用步骤

1. 用 GitHub Actions 云编译（见下方 CI/CD 部分），下载 `app-release-apk` 这个 artifact，解压得到 `app-release.apk`
2. 手机上安装（需要允许"未知来源"安装）
3. 打开 App：
   - 点「授予通知权限」（Android 13 及以上必须，否则系统会直接吞掉本 App 发出的通知）
   - 点「打开通知使用权设置」，找到「媒体控制补丁」并授权
4. 把本 App 加入自启动白名单 / 关闭电池优化（HyperOS 后台管理较严格，不加白名单可能过一会儿服务就被系统解绑）
5. 建议重启一次手机，让系统重新绑定通知监听服务
6. 打开 Spotify 播放歌曲，通知栏应出现带完整按钮的通知

## 调试信息

- App 主界面有一个「显示调试通知」开关，默认关闭。打开后通知栏会额外出现一条调试信息（actions bitmask、Spotify 当前提供的所有 custom actions 的 name/action/icon）
- 不论开关是否打开，点「查看调试信息」都能在 App 内看到最新一次完整记录（不像通知会被系统截断）

## CI/CD（GitHub Actions 云编译）

因为本地没有 Android SDK 环境，构建通过 `.github/workflows/build.yml` 在 GitHub 服务器上完成：

- 每次 push 到 `main` 分支自动触发，也可以在 Actions 页面手动触发（workflow_dispatch）
- 同时产出 `app-debug-apk`（临时签名，体积较大，未开启代码压缩）和 `app-release-apk`（用你自己的 keystore 签名，开启 R8 压缩，体积更小）
- release 签名依赖 4 个 GitHub Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`
- 日常使用建议直接装 release 版；debug 版仅在需要用 Android Studio 连接断点调试时才有意义
- **重要**：因为 release 使用固定签名，后续更新可以直接覆盖安装，不需要每次都卸载重装（首次从旧的临时签名版本切换过来时需要先卸载一次）

## 已知限制

- `TARGET_PACKAGES` 目前只包含 `com.spotify.music`，想支持其它播放器（网易云音乐、YouTube Music 等）需要自行添加包名，且需要验证对方 App 是否也通过 `PlaybackState` 暴露了同类操作
- 部分 HyperOS 版本对"读取其它 App 通知"这类行为可能有额外的系统弹窗确认，实际表现因 ROM 版本而异
- 本通知不会出现在系统的媒体通知卡片"内部"，而是作为通知栏中的另一条独立通知存在——这是设计上的选择（非悬浮窗、不侵入系统卡片），具体在锁屏上的呈现样式取决于 HyperOS 对应用通知的展示规则
