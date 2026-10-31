# aaaa0ggmc's morph extension

> [!WARNING]
> **免责声明 / Disclaimer**
> 
> 本项目处于 WIP（Work In Progress）阶段。作者不对本模组当前以及将来的质量、稳定性、可用性做任何形式的明示或暗示保证与担保，代码可能包含 AI 生成内容（AI Slop）。请在测试环境中评估并自行承担使用风险。
> 
> This project is a Work In Progress (WIP). The author makes NO warranties or guarantees of any kind regarding the quality, stability, or fitness for any purpose of this mod now or in the future. The codebase may contain AI-generated slop. Use strictly at your own risk.

1.12.2 Forge 的能力系统模组。分两层：

- **能力框架**（`ability` 包）：`IAbility` 接口 + `AbilityRegistry` 注册表。其他模组（addon）实现接口并注册，能力即进入轮盘。
- **UI 层**：
  - **InstantUI**（`client/gui/GuiInstantWheel`）：按住快捷键唤出的辐射轮盘
  - **配置 UI**（`client/gui/GuiAbilityConfig` / `GuiAbilityPicker`）：基于 McHorseLib (McLib) 的槽位绑定界面

## InstantUI 交互

**快速模式**（默认）
1. 按住轮盘键（默认 `G`，控制里可改；避开 MineMenu 的 `R`）
2. 轮盘出现，按 `maxSlots` 固定均分扇区；鼠标移动高亮
3. **点击** 或 **松开按键** → 切换到高亮能力并关闭 UI
4. **数字键 1..8**（含小键盘）→ 直接选中对应槽位并关闭，随后按释放键使用
5. 左侧 Profile Tab 显示快捷键；按 Profile 键或鼠标悬停/点击 → 切换整套轮盘配置
6. 单人游戏下打开轮盘会暂停世界；联机无法暂停，世界继续运行

**锁定槽位**
- 槽位绑定不会因为当前形态暂时没有该能力而丢失。
- 对应格子会变暗并显示锁形标记；仍然可以选中，释放时提示“我忘了这个技能了”。
- 通过能力卷轴永久解锁后即可正常使用。

**搜索模式**
- 快速模式下按非数字/非 Profile 键、或点击搜索框 → 进入
- 松键不会关闭；`Esc` 或右上角 `X` 关闭
- 列表点击 / 回车激活；滚轮滚动

右上角 `*` 按钮打开配置 UI；也可以在控制里给 `Ability Wheel Config` 绑键。

## 配置

- `config/aaaa0ggmc_morph_extension.cfg`
  - `maxSlots`（1-8，轮盘按此固定均分）
  - `profileCount` / `activeProfile`：Profile 数量（1-6）与当前 Profile
  - `profile1_name` / `profile1_key`：Profile 显示名与快捷切换键（按键名如 `Q`，或 LWJGL 数字键码；留空禁用）
  - `slot1`~`slot8`：Profile 1 的槽位绑定；Profile 2 起使用 `profile2_slot1` 等
  - `activeAbility` / `profile2_active`：各 Profile 当前选中的能力
- 游戏内配置 UI 修改即时生效

## 调试指令

- `/morphext reload`：重载阵营配置与当前存档数据包
- `/morphext-debug relation missing`：导出缺失（回退到 `MODERATE`）的阵营关系、未映射生物，并生成可直接合并的 `relations` 模板
- `/morphext-debug relation graph`：导出阵营两两关系 SVG（含继承树、定向/对称关系、图例）
- `/morphext-debug relation all`：同时导出以上两项
- 输出目录：`<实例>/aaaa0ggmc_bench/morphext`（与 modpack profiler 的归档目录一致）

## 给 addon 作者：注册一个能力


```java
AbilityRegistry.register(new Ability("mymod:fire_breath", "火焰吐息") {
    @Override
    public void onActivate(EntityPlayer player) {
        // 你的执行代码（客户端线程）
    }
});
```

内置能力类型：`SimpleAbility`（回调）、`CommandAbility`（发命令）、`KeybindAbility`（触发一个 KeyBinding）。
启动时会自动把所有 vanilla 键位注册为 `keybind:<code>` 能力，便于立刻试用轮盘。

> 架构约定：核心 morphext 只保留通用机制；任何具体模组的实体/形态/能力适配请放进对应的 `morphext-patch-<modid>`（生物量少的放 `morphext-patch-modpack`）。服务端能力使用更具体的前缀（如 `morphext:jzahar`）注册，`ServerAbilityService` 会按最长前缀匹配。

## 设计说明（为什么这样做）

- 源模组不修改：addon 通过 `AbilityRegistry` 注册能力；需要深度 patch 的模组用 MixinBooter（整合包已装）做 mixin，把内部激活点包装成 `IAbility`。
- 轮盘自绘（`GuiInstantWheel`）：辐射菜单需要角度拾取和自定义渲染，McLib 的 retained-mode 框架不适合；配置界面用 McLib 保持与 Metamorph 视觉一致。
- `KeybindAbility` 依赖目标模组监听 `InputEvent.KeyInputEvent`（绝大多数模组如此）；原生动作（如开物品栏）只在无界面时生效。

## 构建

需要 Java 8 + Gradle 4.9（wrapper 已固定）。本机直接：

```bash
cd dev/morph-extension
./gradlew build
```

产物在 `build/libs/morph-extension-1.0.0.jar`，丢进 `shaping_the_world/mods/` 即可。

- `gradle.properties` 里已配好本机代理（clash 7890）和 Java 8 路径，直连可用时删掉对应行
- 改代码后 `./gradlew build` 重新产出

## 目录

```
src/main/java/aaaa0ggmc/morphext/
  MorphExtension.java          @Mod 入口
  proxy/                       CommonProxy / ClientProxy（注册键位与内置能力）
  config/ModConfig.java        槽位数与绑定配置
  ability/                     IAbility / Ability / AbilityRegistry / SimpleAbility / KeybindAbility / CommandAbility
  client/KeyHandler.java       键位注册与输入事件
  client/gui/
    GuiInstantWheel.java       轮盘（快速 + 搜索双模式）
    GuiAbilityConfig.java      配置 UI（McLib）
    GuiAbilityPicker.java      能力选择器（McLib）
    elements/                  McLib 自定义元素：标签 / 文本框 / 能力列表
```
