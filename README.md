# AMOOsic

Android 音乐播放器，结合液态玻璃风格界面、本地音乐、云端曲库浏览和 **STEPX Neo 背屏音乐律动**。

当前版本：`1.0.0` · Android 8.0+（API 26）· 包名：`com.beans.music`

## 下载

在 [Releases](https://github.com/xiaoouj/AMOOsic/releases) 下载现有 `1.0.0` 安装包。校验值随版本提供。自行构建见下文。

## 功能

- **本地曲库**：扫描本地音乐，按文件夹、时长和文件大小过滤，管理收藏、歌单与最近播放。
- **云端浏览**：网易云音乐与 QQ 音乐搜索、排行榜、歌单浏览及扫码登录相关界面。
- **播放器**：播放队列、顺序／循环／随机、倍速、歌词、封面配色、后台播放和系统媒体控制。
- **界面**：Jetpack Compose、液态玻璃组件、黑胶唱片播放器和主题配色。
- **背屏联动**：STEPX Neo 17 × 23 点阵，支持波浪涟漪、折线波形、柱状频谱、流星律动、播放进度与时间五种模式，支持亮度和音量浮层设置。

云端功能依赖对应平台接口、账号权限和音源可用性；当前实现不保证所有登录、曲目或播放链接始终有效。普通 Android 设备可使用播放器部分，背屏联动依赖 STEPX Neo 的 StepOS 专用接口。

## 构建

需要 JDK 17、Android SDK Platform 34 和 Build Tools 34.0.0。项目附带 Gradle 8.5 Wrapper，并固定下载校验值；首次构建需要联网下载 Gradle 和 Maven 依赖。

```sh
git clone https://github.com/xiaoouj/AMOOsic.git
cd AMOOsic
export JAVA_HOME="/path/to/jdk-17"
export ANDROID_HOME="/path/to/android-sdk"
./gradlew :app:assembleDebug
```

Windows 使用 `gradlew.bat :app:assembleDebug`。也可在 Android Studio 中打开项目并配置 SDK。

调试安装包输出：`app/build/outputs/apk/debug/app-debug.apk`。可通过 `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装。

现有 `release` 构建配置使用本地 debug 签名，便于个人测试，不是生产签名方案。仓库不包含私有签名密钥；在另一台电脑生成的签名可能无法覆盖已安装版本。

## 权限和数据

本地扫描需允许音乐／存储访问；某些文件扫描入口使用系统「所有文件访问」设置。后台播放使用媒体前台服务和通知。背屏音频分析使用 Android Visualizer，可能需要录音权限；无法获取音频数据时会使用模拟动效。

登录态、收藏、历史记录和背屏偏好存储在应用本地。源码中不包含个人 Cookie、验证码或账号快照。项目未提供独立后端服务。

## 背屏联动

在应用设置中启用背屏音乐模式并选择样式。背屏方向和系统显示策略会影响可见性。播放时应用通过 StepOS 自定义帧接口推送点阵，暂停／停止时释放；与其他背屏工具同时运行时，实际切换效果取决于系统和其他应用的协作策略。

此功能使用设备专用接口，系统升级可能改变兼容性。代码包含音频采样与模拟回退逻辑，展示的动效不一定始终来自实时 FFT。

## 构建验证

公开的源码已在 JDK 17、Gradle 8.5、Android SDK 34 环境完成 `:app:assembleDebug`。本次开源整理未重新验收实体设备、平台登录和云端曲目播放，不将这些功能标记为全部验证通过。

## 项目结构

```text
app/src/main/java/com/beans/music/
  MainActivity.kt              入口与页面导航
  BeansViewModel.kt            曲库、账号与播放状态
  BeansPlaybackService.kt      Media3 后台播放服务
  LocalMusicScanner.kt         本地媒体扫描
  NetEaseCloudClient.kt        网易云客户端实现
  QQMusicCloudClient.kt        QQ 音乐客户端实现
  BackScreenMusicEngine.kt     背屏音频分析与动效调度
  BackScreenRenderer.kt        点阵渲染
  StepXBackScreenManager.kt    StepOS 背屏接口
app/src/main/java/com/kyant/backdrop/
                               适配后的第三方玻璃效果代码
```

源码保留 `BeansMusic` 的部分内部类名和包名，应用显示名称为 AMOOsic。

## 开源与致谢

AMOOsic 自有代码以 [MIT License](LICENSE) 发布。项目参考 [Beans Music](https://github.com/XIaodou0416/Beans-Music) 的 iOS 实现；内置的玻璃效果代码来自 [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)，其代码继续遵循 Apache License 2.0。

完整版权说明和许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 [LICENSES/](LICENSES/)。音乐、歌词、封面与平台商标不属于本项目源码许可证的授权范围。
