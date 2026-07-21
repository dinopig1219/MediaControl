# Media Control Patch

用 NotificationListenerService 读取 Spotify 的 MediaSession，把被 HyperOS 锁屏卡片吞掉的
`repeat` / `like` 等 custom actions 重新做成一条独立的通知（非悬浮窗）。

## 原理
1. `MediaControlListenerService`（NotificationListenerService 的子类）拿到 Spotify 的
   `MediaController`，监听 `onPlaybackStateChanged` / `onMetadataChanged`。
2. 每次状态变化时，用 `NotificationCompat.Builder` + `MediaStyle` 重新生成一条通知，
   把标准的上一首/播放暂停/下一首，加上 `PlaybackState.getCustomActions()`
   （repeat、like 等）全部塞进按钮里。
3. 按钮点击会触发 `MediaActionReceiver`，它重新拿一次 active session，
   调用 `transportControls.sendCustomAction(...)` 把动作转发给 Spotify 本体——
   跟系统那张卡片完全无关，所以不受 HyperOS 裁剪影响。

## 使用步骤
1. 用 Android Studio 打开这个项目（需要联网下载 Gradle / AGP，这边沙盒环境没有
   Android SDK 也连不到 Google 的 maven 仓库，所以我没法在这里直接跑 `gradle build`
   验证；代码逻辑是对的，但请你在真机/Android Studio 里编译跑一次）。
2. 装到手机上，打开 App，点「打开通知使用权设置」，找到「媒体控制补丁」并授权。
3. 在设置里把这个 App 加入自启动白名单 + 关闭电池优化（HyperOS 对后台 App 杀得很狠，
   不加白名单大概率过一会儿 NotificationListenerService 就被系统解绑了）。
4. 打开 Spotify 播放歌曲，通知栏应该会出现一条新通知，按钮里包含 repeat / like。

## 已知限制 / 可以改进的地方
- `TARGET_PACKAGES` 目前只写了 `com.spotify.music`，想支持别的播放器（网易云、
  YouTube Music 等）自己加包名即可。
- `customAction.icon` 用的是对方 App 提供的资源 id，如果这个 icon 资源在别的
  App 的 apk 里而不是系统公共资源，展示可能会失败或显示默认图标；
  如果遇到这个问题需要用 `PackageManager` 从 Spotify 的资源里取图标再转成 Bitmap。
- HyperOS 有些版本对「后台读取其它 App 通知」这类行为有额外弹窗确认，
  以及 MIUI 优化模式关闭后行为可能不同，实测效果因 ROM 版本而异。
- 这个通知不会出现在锁屏那张系统媒体卡片"里面"，而是作为普通通知栏的另一条通知
  存在——这是你要的效果（不用悬浮窗），但请注意它在锁屏上的样式取决于
  HyperOS 对锁屏通知的呈现规则，不一定和截图里那张卡片长得一样。
