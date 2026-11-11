# FloatScroller (随心滚助手)

一款针对**刷短视频（抖音/快手/小红书等）**与**看电子书小说**设计的 Android 悬浮翻页助手。

**特点：**
- 🌟 **完全免 Root、免电脑激活**：采用 Android 官方原生 AccessibilityService 手势分发，普通用户在系统设置中一键开启即可使用。
- 🌟 **全局悬浮控制**：支持任意屏幕边缘自由拖动，松手自动贴边；支持一键折叠为半透明小圆球，不遮挡画面。
- 🌟 **多种场景模式**：
  - **短视频模式**：模拟大距离、高响应滑动，快速切换下一条/上一条视频。
  - **电子书模式**：平滑翻页，适合小说阅读、漫画滚动浏览。
- 🌟 **自动定时翻页**：支持自定义翻页秒数（3~30秒自由调节），解放双手。
- 🌟 **触觉震动反馈**：每次点击上翻/下翻提供微震动反馈，手感如同物理按键。

---

## 项目结构

```
FloatScroller/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/assistant/floatscroller/
│       │   ├── MainActivity.kt                       # 主界面（权限检测、配置保存、开关控制）
│       │   ├── service/
│       │   │   ├── AutoScrollAccessibilityService.kt # 无障碍服务（免 Root 模拟滑动手势与定时翻页）
│       │   │   └── FloatingWindowService.kt          # 悬浮窗服务（拖拽吸附、折叠展开、按钮交互）
│       │   └── utils/
│       │       └── PermissionUtils.kt                # 悬浮窗与无障碍权限检测与跳转工具
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml                 # 主页面布局
│           │   └── layout_floating_panel.xml         # 悬浮胶囊与小气泡布局
│           ├── xml/
│           │   └── accessibility_service_config.xml  # 无障碍服务配置文件 (canPerformGestures="true")
│           └── ... (图标与样式资源)
```

---

## 如何在 Android Studio 中运行

1. 打开 **Android Studio**；
2. 选择 **Open...**，选中本目录：`FloatScroller`；
3. 等待 Gradle 同步完成；
4. 手机开启开发者选项和 USB 调试，点击 **Run 'app'** 即可直接安装到手机上测试。

---

## 手机端使用步骤

1. 打开 App，点击两个授权按钮：
   - **悬浮窗权限**：允许本应用“显示在其他应用上层”；
   - **无障碍服务**：在系统“已下载的应用”或“快捷服务”中找到【随心滚助手】，点击开启；
2. 打开“开启悬浮窗”开关，屏幕上即出现悬浮控制条；
3. 打开抖音、快手或微信读书，点击悬浮窗上的【⬆】或【⬇】即可顺畅翻页；点击【▶】即可开启自动刷视频！
