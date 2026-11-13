# 小树苗护眼卫士 Android 版 (KidEyeGuard Android)

面向家长的安卓平板/手机护眼防沉迷工具：定时提醒休息、系统悬浮窗全屏强制锁屏、趣味口算提前解锁、家长密码默认锁定规则（防孩子自己改设置）。

## 核心特性
1. **双端通用离线激活码**：沿用与 Windows 版（`kid-eye-guard-win`）完全一致的 HMAC-SHA256 校验算法，直接用电脑端的 `keygen.py` 发卡即可激活安卓版。
2. **规则默认锁定防篡改**：所有用屏时长、休息时长、口算难度、每日上限、夜间宵禁默认锁定，必须点击「家长解锁设置」输入密码（默认 `1234`）才可修改，切到后台自动重锁。
3. **安卓防绕过权限体检**：内置一键跳转开启「悬浮窗权限（`SYSTEM_ALERT_WINDOW`）」与「忽略电池优化/后台保活」。

---

## 方式一：网上免费云端打包 APK（推荐，本机免装 Android Studio）

工程已内置 GitHub Actions 自动构建工作流：`.github/workflows/build-apk.yml`。

1. 在 GitHub 新建一个仓库（私有/公开均可），例如 `kid-eye-guard-android`。
2. 在本目录执行以下命令推送代码：
   ```bash
   git remote add origin https://github.com/<你的用户名>/kid-eye-guard-android.git
   git push -u origin master
   ```
3. 打开 GitHub 仓库顶部的 **Actions** 页面 $\rightarrow$ 点击最新一次 **Build Android APK** 运行记录 $\rightarrow$ 在底部 **Artifacts** 下载 **`KidEyeGuard-Android-APK`**（解压即得 `app-debug.apk`，可直接传到手机/平板安装）。

## 方式二：本机打包（需安装 Android Studio / Android SDK）

若后续本机安装了 Android Studio 与 Android SDK（API 34），在工程根目录执行：
```bash
gradle testDebugUnitTest assembleDebug
```
生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。
