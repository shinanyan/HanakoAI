# Hanako 发布流程

本文档描述**本 fork**（[shinanyan/HanakoAI](https://github.com/shinanyan/HanakoAI)）GitHub Release 的完整发布流程。

> 文档中的目标版本示例为 `0.0.21-alpha`（即上一次发布 `v0.0.20-alpha` 的下一版），执行时替换为目标版本。

## 为什么发布的是 CI 签名的 debug 包

这是本 fork 从 `v0.0.19-alpha` 起的既定发布方式，README 顶部也标注为 `(fork · debug)`。**不要改成上传本地构建的 release 包**，原因有三：

1. 本地没有 `keystore.properties`，`./gradlew assembleRelease` 的产物是 **unsigned**，装不上。
2. 即使补上密钥，新密钥签出的包与历史发布**签名不同**，老用户必须卸载重装，设置与历史记录全部丢失，App 内更新也会直接失败。
3. CI 使用缓存里一份固定的密钥（cache key `hanako-ci-debug-keystore-v1`，`CN=Hanako CI Debug`，SHA-256 `69937c02246f2baf74794bbb2f5ced1cf93cf0138ace44358b88abf33882ad92`），因此**每个版本的 debug 包都可以互相覆盖安装**。签名指纹由 `.github/workflows/build.yml` 里的 `Ensure stable CI debug keystore` 步骤保证稳定；该缓存一旦丢失，密钥会被重新生成，升级路径即断。

由此推出两条硬性要求：

- 发布附件**必须来自 CI 构建**，不要用本地 `assembleDebug`（本地用的是 `~/.android/debug.keystore`，与 CI 密钥不同）。
- 每次发布都要**校验签名指纹与上一版一致**（见第 3 节），这是升级路径唯一的技术保障。

## 发布约束

- Git 标签、Release 名称统一使用 `v<version>`，例如 `v0.0.20-alpha`。历史版本使用**轻量标签**，继续保持一致。
- `app/build.gradle.kts` 中 `versionCode` 必须递增，`versionName` 必须与标签去掉 `v` 后完全一致。
- README 顶部的 Download 和 View Release Notes 链接必须指向新版本。
- README 的 `## Changelog` 必须与 GitHub Release 的 `## Changelog` 内容一致。App 优先读取 GitHub Latest Release；API 失败时读取 `main` 分支 README 作为回退。
- Release 正文沿用上一个版本的结构和下载说明措辞，只重写 changelog；版本比较链接与附件链接按新版本替换。
- Release 必须上传以下 **5 个** debug APK，文件名不得改变：

  ```text
  app-lite-arm64-v8a-debug.apk          # 推荐，大多数手机
  app-full-arm64-v8a-debug.apk          # 含本地 ML Kit OCR
  app-lite-armeabi-v7a-debug.apk        # 32 位设备
  app-full-armeabi-v7a-debug.apk        # 32 位设备
  app-lite-arm64-v8a-debug-logging.apk  # 排障用，带应用内调试日志
  ```

- 除非明确改变项目发布策略，不要添加 `--prerelease`。版本名虽然含 `alpha`，但 GitHub Release 本身不是 prerelease；App 使用 `/releases/latest` 获取它。

## 0. `gh` 的使用注意

仓库同时存在 `upstream`（原作者仓库），**`gh` 的默认仓库会被解析成 `zyf2007/HanakoAI`**，命令会打到别人仓库上。因此：

```bash
gh repo set-default shinanyan/HanakoAI
```

即便如此，本文档中每条 `gh` 命令都显式带 `-R shinanyan/HanakoAI`，不要依赖默认值。

## 1. 发布前检查

```bash
git status --short
git fetch origin --tags
git log --oneline --decorate -10
gh auth status
```

读取上一个 Release 正文与附件，作为本次模板：

```bash
gh release view v0.0.20-alpha -R shinanyan/HanakoAI \
  --json name,tagName,isPrerelease,isDraft,body,assets,publishedAt
```

不要重新设计下载说明。保留 Full/Lite、ML Kit、32 位设备和排障包的原有顺序与措辞。

记录上一版发布提交，正文的「改动对比」需要用到：

```bash
git rev-parse v0.0.20-alpha
```

起草新版本正文时，**必须删掉上游模板里的 The Kirari Network 段落**（形如「0.0.9 版本中加入了 The Kirari Network 在线登录的模型获取方式……记得去「模型提供方 → The Kirari Network」一键同步」）。该功能已从应用中移除，继续保留会让用户找不到入口。历史 `release-notes-v*.md`（上游模板）保持原样，不要回改。

## 2. 更新版本与 changelog

修改 `app/build.gradle.kts`：

```kotlin
versionCode = 21
versionName = "0.0.21-alpha"
```

同步修改 README：

- 顶部 Download 链接的显示版本、标签和下载 URL。
- View Release Notes 的标签 URL。
- fork 改动清单的**版本归属**：把本次发布包含的条目从「尚未发布」移到「已随 `v<version>` 发布」分组。这一节按「哪一版发布了哪几条」分组，不要合并成一个大列表。
- 文末 `## Changelog` 下的内容。

### 格式约定（解析相关，改动前先看这里）

Download 行的形状必须保持为：

```markdown
[[Download 0.0.21-alpha (fork · debug)](https://github.com/shinanyan/HanakoAI/releases/tag/v0.0.21-alpha)]  [[View Release Notes](...)] [[Telegram](...)]
```

`AppUpdateApi.extractReadmeUpdateInfo` 用它做 README 回退解析：

- 必须匹配 `[[Download ` 前缀，版本号以数字开头。
- `(fork · debug)` 这类括号后缀会被正则忽略；**不要**把额外内容写进版本号位置，否则会把后缀吃进版本号（历史上踩过：弹窗显示「发现新版本 0.0.20-alpha (fork · debug)」）。
- 注意「无更新」时也会走 README 分支，所以这不是冷门回退，是常规第二条路径。

Release 正文的 Changelog 截取约定见 `AppUpdateApi.kt`：

- GitHub Release 正文从 `## Changelog` 开始读取，到下一个二级标题为止。
- README 回退路径读取 `## Changelog` 之后的**全部**内容，因此 `## Changelog` 必须保持为 README 最后一个二级章节。

## 3. 用 CI 构建并获取签名 APK

先确认测试通过，再让 CI 构建发布包：

```bash
./gradlew test
```

触发 CI 并等待（`main` 的 push 会自动触发；在功能分支上则手动 dispatch）：

```bash
# 功能分支预演
gh workflow run build.yml --ref <branch> -R shinanyan/HanakoAI

gh run list -R shinanyan/HanakoAI --branch <branch> --limit 3      # 取 run id
gh run watch <run-id> -R shinanyan/HanakoAI --exit-status
```

下载产物：

```bash
gh run download <run-id> -R shinanyan/HanakoAI -n debug-apks -D <下载目录>
```

产物目录结构是 `<normal|logging>/<flavor>/debug/app-<flavor>-<abi>-debug.apk`（例如 `normal/lite/debug/app-lite-arm64-v8a-debug.apk`）。按下面的映射复制成发布资产名（`logging` 的包需要加 `-logging` 后缀）：

```bash
SRC=<下载目录>
DST=<暂存目录>
mkdir -p "$DST"
cp "$SRC/normal/lite/debug/app-lite-arm64-v8a-debug.apk"   "$DST/app-lite-arm64-v8a-debug.apk"
cp "$SRC/normal/full/debug/app-full-arm64-v8a-debug.apk"   "$DST/app-full-arm64-v8a-debug.apk"
cp "$SRC/normal/lite/debug/app-lite-armeabi-v7a-debug.apk" "$DST/app-lite-armeabi-v7a-debug.apk"
cp "$SRC/normal/full/debug/app-full-armeabi-v7a-debug.apk" "$DST/app-full-armeabi-v7a-debug.apk"
cp "$SRC/logging/lite/debug/app-lite-arm64-v8a-debug.apk"  "$DST/app-lite-arm64-v8a-debug-logging.apk"
```

校验版本号与签名指纹：

```bash
APKSIGNER="$ANDROID_SDK_ROOT/build-tools/<version>/apksigner"
for apk in "$DST"/*.apk; do
  "$APKSIGNER" verify --print-certs "$apk"
done
```

**这一步是升级路径的闸门**，必须满足：

- 每个包的 `versionCode` / `versionName` 与 `app/build.gradle.kts` 一致。
- 每个包的证书 SHA-256 都是 `69937c02246f2baf74794bbb2f5ced1cf93cf0138ace44358b88abf33882ad92`（`CN=Hanako CI Debug`）。与上一版不一致就**不要发布**，去查 CI 密钥缓存是否被重建。

下载上一版的同一个包做交叉比对，是确认指纹没记错的最直接办法：

```bash
gh release download v0.0.20-alpha -R shinanyan/HanakoAI -p app-lite-arm64-v8a-debug.apk -D <临时目录>
"$APKSIGNER" verify --print-certs <临时目录>/app-lite-arm64-v8a-debug.apk
```

## 4. 准备 Release 正文

把正文写到 `docs/release-body-v<version>.md`（`docs/*.md` 被 gitignore，不进 git；与历史的 `release-body-v*.md` 保持一致）。复制上一版正文作为模板，保持以下结构：

```markdown
> ⚠️ 本 Release 来自 [zyf2007/HanakoAI](https://github.com/zyf2007/HanakoAI) 的 fork，APK 为 **debug 签名**，仅供测试安装；与上游正式版签名不同。如果你装的是上游版本，请先卸载再安装。覆盖安装本 fork 的上一版 `v<上一版>` **不需要卸载**（同一个签名密钥，设置与历史记录都会保留）。

**改动对比**：https://github.com/shinanyan/HanakoAI/compare/<上一版发布提交>...v<new-tag>

## Changelog

<与 README 完全一致的 changelog>

## 下载说明

<5 个链接，替换版本号>

## 说明

<沿用已有说明，更新「本版本基于 v<上一版>」与「完整改动」链接>
```

两个比较链接的取法不同，别弄反：

- **改动对比**：上一版标签指向的提交 → 新标签，即 `compare/$(git rev-parse v<上一版>)...v<new-tag>`。
- **完整改动（与上游的对比）**：fork 的基线提交 `2762037`（=`v0.0.18-alpha`，fork 改动清单的起点）→ 新标签。

发布前逐项确认：

- Changelog 与 README 逐字一致。
- 5 个链接都使用新标签。
- ⚠️ 段落里的「覆盖安装上一版不需要卸载」指向的是**上一版的标签**，且措辞与实际一致。
- 如果本次有影响用户数据的变更（例如模型选择会被清空、需要重新配置），必须在 Changelog 里写明，避免被当成 bug 报回来。

## 5. 提交、推送与发布

先提交版本元数据和 README：

```bash
git add app/build.gradle.kts README.md
git add -f docs/release-process.md        # 该文件已跟踪，docs/ 的 ignore 对它无效
git commit -m "发布 v0.0.21-alpha"
git push origin HEAD:main
```

标签应指向发布提交：

```bash
git tag v0.0.21-alpha
git push origin v0.0.21-alpha
```

**顺序要求**：`app/build.gradle.kts` 和 README 的改动必须在触发 CI 构建之前提交，标签指向的提交就是 CI 构建 APK 的那个提交。若在构建之后又追加了提交，先确认新增的改动不会进入 APK（例如只改了 `README.md` 这类不参与打包的文件），否则要重新构建。

创建 Release（在暂存目录里执行，`gh` 用文件名作为附件名）：

```bash
cd <暂存目录>
gh release create v0.0.21-alpha \
  app-lite-arm64-v8a-debug.apk \
  app-full-arm64-v8a-debug.apk \
  app-lite-armeabi-v7a-debug.apk \
  app-full-armeabi-v7a-debug.apk \
  app-lite-arm64-v8a-debug-logging.apk \
  -R shinanyan/HanakoAI \
  --title v0.0.21-alpha \
  --notes-file "C:\path\to\docs\release-body-v0.0.21-alpha.md"
```

## 6. 发布后验证

```bash
gh release view v0.0.21-alpha -R shinanyan/HanakoAI --json tagName,name,isDraft,isPrerelease,body,assets,url
gh api repos/shinanyan/HanakoAI/releases/latest --jq '.tag_name, .prerelease, (.assets|length)'
gh api repos/shinanyan/HanakoAI/contents/README.md --jq '.content' | base64 -d | grep -n "Download"
```

验证结果必须满足：

- Latest Release 是新标签，不是 draft、不是 prerelease，且附件数为 5。
- 5 个 APK 都存在，文件名与暂存目录一致，size 与本地相同。
- 标签指向的提交就是预期的发布提交（`git rev-parse v0.0.21-alpha` 与 `gh api repos/shinanyan/HanakoAI/git/ref/tags/v0.0.21-alpha --jq '.object.sha'` 一致）。
- Release 的 `## Changelog` 能被 App 截取（从 `## Changelog` 到下一个二级标题），且不包含「下载说明」。
- README 顶部的 Download 链接可访问（`HTTP 200/206`），README changelog 与 Release 一致。
- 安装 Release APK 后，旧版本能够在 App 内发现新版本并显示 changelog；如果能拿到上一版的安装，顺便验证可直接覆盖安装（同一个签名）。

如果附件上传或线上验证失败，不要复用标签发布另一个提交。修复后使用 `gh release upload <tag> <files> --clobber` 补齐附件，并确保标签仍指向预期发布提交。
