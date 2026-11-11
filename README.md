# FloatScroller - 独立极速版 (单 App 全包，无任何第三方依赖)

针对**刷短视频（抖音/快手等）**与**电子书小说智能翻页**打造的**单 App 全包**悬浮按键工具。

### 🌟 核心特色

- 🎯 **单 App 独立搞定**：**不装 Shizuku、不开无障碍辅助服务、无需 Root、免连电脑**。
- ⚡ **内置 Local ADB 协议引擎**：App 自带纯 Kotlin 实现的 ADB 握手与 RSA 认证协议，直接通过系统本地回环网络（`127.0.0.1`）连接本机无线调试端口。
- 🎮 **100% 真实物理按键与滚轮**：
  - 方向上键：`input keyevent 19` (KEYCODE_DPAD_UP)
  - 方向下键：`input keyevent 20` (KEYCODE_DPAD_DOWN)
  - 鼠标滚轮：`input roll 0 1` / `input roll 0 -1`
  - 硬件极速滑动：`input swipe ...`
- 💎 **悬浮胶囊交互**：支持随手拖拽自动贴边吸附、一键折叠为半透明小气泡防遮挡、触觉微震动反馈、3~30秒定时自动连刷。

---

## 3 步使用指引 (免电脑、无需安装任何其他软件)

1. **手机开启无线调试**：
   - 打开手机系统【设置】➔【开发者选项】➔ 开启【**无线调试**】；
   - 找到页面中显示的“IP 地址和端口”（例如 `192.168.1.5:38421`），**记下冒号后面的 5 位端口号**（如 `38421`）；
2. **在 App 内一键连接**：
   - 打开本应用，在输入框中填入该 5 位端口号，点击【连接】；
   - 此时手机屏幕会弹出系统原生对话框：*“是否允许此设备进行调试？”*，勾选【**始终允许**】并点击【确定】；
   - 界面状态变为绿色的 **“本地 ADB 已连接 ✓”**；
3. **开启悬浮窗开始使用**：
   - 授予悬浮窗权限后，打开“悬浮窗开关”；
   - 切入抖音、快手、微信读书等任意 App，点击悬浮窗的【⬆】或【⬇】即可如同插入物理键盘/鼠标一般极速翻页！点击【▶】即可自动刷屏！

---

## 项目代码结构

```
FloatScroller/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/assistant/floatscroller/
│       │   ├── MainActivity.kt                      # 主界面（端口输入、连接/断开控制、参数设置）
│       │   ├── adb/
│       │   │   ├── AdbKeyPair.kt                   # RSA 密钥对生成与签名（ADB 认证必备）
│       │   │   ├── AdbProtocol.kt                  # 纯 Kotlin 官方标准 ADB 协议编解码器
│       │   │   ├── AdbConnection.kt                # Socket 连接与 Shell 数据流管理
│       │   │   └── LocalAdbManager.kt              # 本地 ADB 单例管理与上下翻页/滚轮指令分发
│       │   ├── service/
│       │   │   └── FloatingWindowService.kt         # 悬浮窗服务（拖拽吸附、折叠展开、震动反馈）
│       │   └── utils/
│       │       └── PermissionUtils.kt               # 悬浮窗权限检测与跳转
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml                # 主页设计
│           │   └── layout_floating_panel.xml        # 悬浮窗面板与气泡
│           └── ... (矢量图标与色彩样式)
```
