# Reimagined Intro — 1.21.11 移植版（VulkanMod 兼容）

## 这是什么

基于官方 v3.0.0（MC 26.3）源码移植到 **Minecraft 1.21.11 + Fabric** 的版本，
渲染路径专门为 **VulkanMod 0.6.8+1.21.11** 的兼容层设计。

## v3.2.0 新增：菜单过渡动画（v3.2.1 重构）

在菜单之间切换（如主菜单 → 单人游戏存档列表）时播放过渡动画：

- **Slide** 横向推入（默认）：旧屏左移滑出、新屏自右侧滑入
- **Slide Up** 纵向推入：旧屏上移、新屏自底部滑入
- **Cover** 覆盖：旧屏静止、新屏自右侧盖上（滑入侧外露黑边）
- **Fade** 穿黑淡入淡出（两段式：旧屏渐暗 → 新屏渐亮）
- **Zoom** 纵深缩放：旧屏微缩后退、新屏自 92% 放大至全屏
- **Off** 关闭

速度可在 100–600ms 之间调节（配置屏滑条）。动画期间屏蔽鼠标/键盘输入，
防止快速双击误触新界面。开场动画（IntroScreen）进出的切换保持原有的
专属过渡效果，不受此功能影响。

**v3.2.1 架构**：过渡不再"再渲染"任何屏幕——拦截 `Minecraft.setScreen`
延迟真实切换，任何时刻都只有 active 屏走 vanilla 原生渲染路径，动画只是
在它的渲染包装器外叠加位姿变换与黑幕。模组菜单等带滚动列表/复杂 GUI
状态的界面因此绝对安全（v3.2.0 的出屏再渲染方式与它们的剪刀裁剪/
z 序导航交互存在崩溃风险，已废弃）。

实现方式与 VulkanMod 的兼容性：动画仅使用 vanilla GUI 管线的
`GuiGraphics` 位姿变换（JOML Matrix3x2fStack 的 translate/scale）、
`fill`（黑色幕布/衬底）与屏幕自身的 `renderWithTooltipAndSubtitles` 调用——
**零 GL 调用、零自建 Framebuffer、零逐像素 alpha**，全部走 VulkanMod
完整接管的路径（与帧动画上传同一安全等级）。

## 为什么官方 1.21.11 版（v2.1.0）与 VulkanMod 不兼容

官方 v2.1.0（1.21.11）依赖 **FancyMenu + Drippy Loading Screen + WaterMedia**，
其中 WaterMedia 使用外部解码器（VLC 内核）做视频渲染，绕过了 Minecraft 的纹理
系统。VulkanMod 只接管 vanilla 渲染管线（`GpuDevice` / `GlStateManager`），
对这类外部渲染无能为力——这也是 VulkanMod 官方 wiki 把视频类模组
（Video Tape / Exordium 等）列入"no effect / crash"的原因。

## 本移植版的兼容原理

v3.0.0 架构本身就不依赖任何视频解码器：

```
JPEG 帧 (assets) → STBImage 解码 → NativeImage → DynamicTexture
                                              → GpuDevice.writeToTexture (Vulkan 路径)
```

逐帧走 vanilla 纹理上传接口。VulkanMod 0.6.8 对这条链路的接管点全部验证过：

| 环节 | vanilla 调用 | VulkanMod 接管 | 验证方式 |
|---|---|---|---|
| 纹理创建 | `GpuDevice.createTexture` | `VkGpuDevice.createTexture` → `VulkanImage` | 源码 |
| 帧上传 | `writeToTexture` | `VulkanImage` 拷贝 | 源码 |
| 双线性过滤 | `SamplerCache.getSampler(CLAMP_TO_EDGE, LINEAR)` | `VkGpuDevice.createSampler` → `VkSampler` | 源码 |
| GUI 绘制 | `GuiGraphics.blit` (GUI_TEXTURED pipeline) | VkRenderPass 内建 screenquad 管线 | 源码 |
| 每帧 tick | `TextureManager.tick` | `MTextureManager`（保留 tickable 逻辑） | 源码 |

**刻意避开的坑：**
- `generateMipmap` 在 VulkanMod 是空操作——本模组不请求 mipmap（`maxLevel=0`），
  播放 1440p JPEG 帧不需要 mipmap。
- 光照贴图（`MLightTexture`）本身就是"DynamicTexture 每帧上传"模式且在
  VulkanMod 下正常工作——本模组与它同路径。

## 移植的 API 变更（26.3 → 1.21.11）

| 26.3 (renderpearl / 提取式渲染) | 1.21.11 (blaze3d / 直接渲染) |
|---|---|
| `GuiGraphicsExtractor` | `GuiGraphics` |
| `Screen.extractRenderState` | `Screen.render` |
| `Screen.extractBackground` | `Screen.renderBackground` |
| `TitleScreen.extractPanorama` | `Screen.renderPanorama` |
| `com.mojang.renderpearl.api.textures.*` | `com.mojang.blaze3d.textures.*` |
| `net.minecraft.client.renderer.Panorama` | `net.minecraft.client.renderer.PanoramaRenderer` |
| `Minecraft.gui.setScreen/setOverlay` | `Minecraft.setScreen/setOverlay` |
| `GuiGraphics.text(Font, ...)` | `GuiGraphics.drawString(Font, ...)` |

所有 mixin 目标方法均已用 1.21.11 官方客户端字节码逐一核对
（TitleScreen.render / LogoRenderer.renderLogo / SplashRenderer.render /
LoadingOverlay.render 13 参 blit / PanoramaRenderer.render 12 参 blit）。

## 构建

```bash
./gradlew :fabric:build
```

- loom 锁定 **1.14.10**（与 VulkanMod 0.6.8+1.21.11 官方构建同一代；loom 1.18+ 需
  Java 25 运行时，而 1.21.11 工具链是 Java 21）
- `mappings loom.officialMojangMappings()`（v3 源码本身按 Mojang 官方映射编写）
- NeoForge 模块在此分支排除（VulkanMod 仅支持 Fabric）

## 安装（玩家）

1. Minecraft 1.21.11 + Fabric Loader ≥0.18.4
2. 放入本模组 jar + Fabric API（0.140.0+1.21.11）
3. 放入 VulkanMod 0.6.8+1.21.11
4. **不要**安装 FancyMenu / Drippy Loading Screen / WaterMedia——本版本不需要它们，
   且它们与 VulkanMod 冲突
5. （可选）Mod Menu 17.0.1 用于图形化配置；也可用 F7 直开配置屏

## 配置

- `config/reimagined-intro.json`：`enableIntro` / `introVolume` /
  `enableTitleScreenLoop` / `showSkipPrompt`
- F6：重播开场动画；F7：配置屏；ESC：跳过

## 已知限制

- VulkanMod 下 `Screenshot` 等仍走 GL 兼容层，与本模组无关
- 若同时安装 Sodium/Iris 等优化模组，与 VulkanMod 本身的冲突需要自行评估
  （两者都在替换渲染器，一般不建议同装）
