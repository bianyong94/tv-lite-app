# Global Vision Lounge

新的 Android TV 客户端工程，独立于现有 `tv-app`，目标是保留原有功能闭环，同时改成更极简的大屏视觉和更直接的遥控器交互。

## 当前状态
- 已从现有 `tv-app` 迁移数据层、页面结构和播放器能力。
- 已切换为独立包名 `com.globalvision.tvlite`。
- 已建立新的大屏主题、焦点样式和分辨率适配基线。
- 已成功生成 debug 安装包：`app/build/outputs/apk/debug/app-debug.apk`
- 已生成签名 release 安装包：[app-release-signed.apk](downloads/app-release-signed.apk)
- 已验证 `assembleDebug`、`assembleRelease`、`lintDebug` 三条构建链路通过。
- 已整理真机验收清单：`QA_CHECKLIST.md`

## 目录约定
- `app/`: Android TV 主工程
- `app/src/main/java/com/globalvision/tvlite/core`: 网络、模型、播放器等基础层
- `app/src/main/java/com/globalvision/tvlite/feature`: 首页、搜索、详情、播放器等功能页面
- `app/src/main/java/com/globalvision/tvlite/ui`: 视觉主题、布局指标和公共组件

## 本地构建
如果系统默认没有可用 JDK，可直接使用 Android Studio 自带 JBR：

```bash
cd tv-lite-app
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" \
./gradlew :app:assembleDebug
```

如需做更严格校验：

```bash
cd tv-lite-app
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" \
./gradlew :app:lintDebug :app:assembleRelease
```

## 下一步
1. 在真机上验证 1080p 和 4K 的焦点路径、列表分页和播放器操作体验。
2. 根据真机结果继续收口首页视觉节奏与大屏间距细节。
3. 持续清理剩余非阻断级 lint warnings 与依赖升级项。
