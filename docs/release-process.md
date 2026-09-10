# Hanako 发布流程

本文档描述 Hanako GitHub Release 的完整发布流程。版本示例使用 `0.0.17-alpha`，执行时替换为目标版本。

## 发布约束

- Git 标签、Release 名称统一使用 `v<version>`，例如 `v0.0.17-alpha`。
- `app/build.gradle.kts` 中 `versionCode` 必须递增，`versionName` 必须与标签去掉 `v` 后完全一致。
- README 顶部的 Download 和 View Release Notes 链接必须指向新版本。
- README 的 `## Changelog` 必须与 GitHub Release 的 `## Changelog` 内容一致。App 优先读取 GitHub Latest Release；API 失败时读取 `main` 分支 README 作为回退。
- Release 正文沿用上一个版本的结构和下载说明措辞。只重写 changelog；版本比较链接、附件链接、版本号和实际文件大小按新版本替换。
- Release 必须上传 Lite/Full 与 arm64-v8a/armeabi-v7a 组合的四个签名 APK，文件名不得改变。

## 1. 发布前检查

确认工作区、远端、GitHub CLI 和签名配置：

```bash
git status --short
git fetch origin --tags
git log --oneline --decorate -10
gh auth status
test -f keystore.properties
```

读取上一个 Release 正文与附件，作为本次模板：

```bash
gh release view v0.0.16-alpha \
  --json name,tagName,isPrerelease,isDraft,body,assets,publishedAt
```

不要重新设计下载说明。保留 Full/Lite、ML Kit、32 位设备和下载说明的原有顺序与措辞。

起草新版本正文时，**必须删掉上一版正文里的 The Kirari Network 段落**（形如「0.0.9 版本中加入了 The Kirari Network 在线登录的模型获取方式……记得去「模型提供方 → The Kirari Network」一键同步」）。该功能已从应用中移除，继续保留会让用户找不到入口。历史 `release-notes-v*.md` 文件本身保持原样，不要回改。

## 2. 更新版本与 changelog

修改 `app/build.gradle.kts`：

```kotlin
versionCode = 17
versionName = "0.0.17-alpha"
```

同步修改 README：

- 顶部 Download 链接的显示版本、标签和下载 URL。
- View Release Notes 的标签 URL。
- 文末 `## Changelog` 下的内容。

更新检查解析约定见 `AppUpdateApi.kt`：

- GitHub Release 正文从 `## Changelog` 开始读取，到下一个二级标题为止。
- README 回退路径读取 `## Changelog` 之后的全部内容，因此 Changelog 必须保持为 README 最后一个二级章节。

## 3. 测试与签名构建

```bash
./gradlew test
./gradlew clean assembleRelease
```

预期产物：

```text
app/build/outputs/apk/lite/release/app-lite-arm64-v8a-release.apk
app/build/outputs/apk/lite/release/app-lite-armeabi-v7a-release.apk
app/build/outputs/apk/full/release/app-full-arm64-v8a-release.apk
app/build/outputs/apk/full/release/app-full-armeabi-v7a-release.apk
```

使用 Android SDK Build Tools 校验每个 APK 的签名：

```bash
APKSIGNER="$ANDROID_SDK_ROOT/build-tools/<version>/apksigner"
for apk in app/build/outputs/apk/{lite,full}/release/*.apk; do
  "$APKSIGNER" verify --verbose --print-certs "$apk"
done
```

再检查版本、文件大小和摘要：

```bash
sha256sum app/build/outputs/apk/{lite,full}/release/*.apk
ls -lh app/build/outputs/apk/{lite,full}/release/*.apk
```

## 4. 准备 Release 正文

复制上一个 Release 正文作为模板，保持以下章节顺序：

```markdown
**Full Changelog**: https://github.com/shinanyan/HanakoAI/compare/<previous-tag>...<new-tag>

<沿用 Full/Lite 与 ML Kit 说明>

## Changelog

<与 README 完全一致的 changelog>

## 下载说明

<沿用格式，替换版本 URL 和构建后的实际文件大小>

## 说明

<沿用已有说明>
```

发布前逐项确认：

- Changelog 与 README 一致。
- 四个链接使用新标签。
- 文件大小来自本次构建，而不是复制旧版本数字。
- Full Changelog 比较范围为上一个标签到新标签。

## 5. 提交、推送与发布

先提交版本元数据和 README：

```bash
git add app/build.gradle.kts README.md
git add -f docs/release-process.md
git commit -m "发布 v0.0.17-alpha"
git push origin HEAD:main
git tag v0.0.17-alpha
git push origin v0.0.17-alpha
```

标签应指向发布提交。历史版本使用轻量标签，继续保持一致。

创建 Release：

```bash
gh release create v0.0.17-alpha \
  app/build/outputs/apk/lite/release/app-lite-arm64-v8a-release.apk \
  app/build/outputs/apk/lite/release/app-lite-armeabi-v7a-release.apk \
  app/build/outputs/apk/full/release/app-full-arm64-v8a-release.apk \
  app/build/outputs/apk/full/release/app-full-armeabi-v7a-release.apk \
  --title v0.0.17-alpha \
  --notes-file <release-notes-file>
```

除非明确改变项目发布策略，不要添加 `--prerelease`。现有 Latest Release 虽然版本名含 `alpha`，但 GitHub Release 本身不是 prerelease；App 使用 `/releases/latest` 获取它。

## 6. 发布后验证

```bash
gh release view v0.0.17-alpha --json tagName,name,isDraft,isPrerelease,body,assets,url
curl -fsSL https://api.github.com/repos/shinanyan/HanakoAI/releases/latest
curl -fsSL https://raw.githubusercontent.com/shinanyan/HanakoAI/main/README.md
```

验证结果必须满足：

- Latest Release 是新标签且不是 draft。
- 四个 APK 都存在，文件名和本地一致。
- Release 的 `## Changelog` 能被 App 截取，且不包含下载说明。
- README 顶部链接可访问，README changelog 与 Release 一致。
- 安装 Release APK 后，旧版本能够在 App 内发现新版本并显示 changelog。

如果附件上传或线上验证失败，不要复用标签发布另一个提交。修复后使用 `gh release upload <tag> <files> --clobber` 补齐附件，并确保标签仍指向预期发布提交。
