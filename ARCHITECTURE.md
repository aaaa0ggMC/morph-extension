# 能力扩展架构（第二阶段）

## 边界

- `client/`：键鼠、展示、客户端预测与旧客户端能力。
- `ability/`：能力契约、结果、执行器注册、玩家 JSON 存储和客户端镜像门面。
- `network/AbilityNetwork`：C2S 携带能力 ID 或无数据的同步请求；S2C 携带结果与玩家数据快照。
- `integration/metamorph/MetamorphServerAbilities`：Metamorph 服务端执行器。
- `MetamorphIntegration`、`MorphGrantedAbility`：客户端列表/图标/详情包装，不承担服务端授权。

单人也使用逻辑客户端→服务端请求，不能因处于同一 JVM 就跨侧传递玩家、形态或动作实例。

开发目标为 Forge+OptiFine/Java 8 与 Cleanroom+Actinium/Java 25 双运行时。构建仍保持 Java 8 字节码与 common API；当前静态检查不等于两套运行时均通过行为验收。安装时更新 `_shared/mods/common/` 并运行同步脚本，不能直接覆盖实例 mods 中的硬链接。

## 统一释放路径

键位和状态页 → `ClientAbilityExecutor.activate(ability)`：

1. 客户端预检被动、解锁、冷却，仅改善交互。
2. `ExecutionSide.CLIENT`：在客户端调用 `ability.activate(player)`。
3. `ExecutionSide.SERVER`：只发 ID，不在客户端执行或预报成功。
4. 网络处理器从连接上下文取得服务端玩家，调度到服务端主线程。
5. `ServerAbilityService` 分发给 common 初始化时注册的执行器。
6. 执行器从该玩家的服务端状态验证能力，并返回 `ActivationResult`。
7. 结果通过 sided proxy 切回客户端主线程，显示 Actionbar/确认冷却。

`SUCCESS` 表示执行器接受并返回；第三方 `void` API 未提供结果时，不能据此断言火球已生成等世界后置条件。`COOLDOWN`、`LOCKED`、`UNAVAILABLE`、`FAILED` 为明确的拒绝/失败结果。

## 扩展入口

### 旧能力

现有 `onActivate(EntityPlayer)` 保留，默认 `CLIENT`。新契约 `activate` 会调用旧方法并把基础冷却包装到结果中。这里是兼容层，不代表所有旧能力都适合客户端执行。

### 服务端能力

1. 客户端能力声明 `getExecutionSide() == SERVER`。
2. common 初始化注册 `ServerAbilityService.register("addon:", handler)`。
3. handler 验证自己的 ID 格式、玩家是否实际拥有、当前形态/资源/冷却等；验证失败直接返回拒绝结果。
4. handler 只使用服务端玩家及服务端重新取得的状态执行，不使用客户端 `AbilityRegistry` 或 JSON 解锁表作为授权依据。
5. 服务端计算冷却，客户端不再套一次缩减器。

`Handler.isAvailable(player, id)` 声明该玩家天生或动态拥有的能力，默认为 false。
`ServerAbilityService` 在执行前统一检查玩家永久 `removed`（优先于所有授予）、`unlocked` 和该动态条件，再检查服务端持久冷却。handler 仍必须校验执行条件；例如卷轴解锁一个 morph ID 不代表玩家已获得对应 Metamorph 形态。

需要授予永久权限时，在服务端主线程调用：

```java
PlayerAbilityStore.update(player, data -> data.setUnlocked(id, true));
AbilityNetwork.syncPlayer(player, "");
```

状态同理使用 `data.putState(id, json)`。`update` 提交成功后才发布缓存；`get` 返回副本，不能修改副本后期待自动保存。
旧 `AbilityUnlocks.unlock/lock/flush`、`AbilityStateStore.put/remove/flush` 已弃用并明确抛错，防止 addon 继续绕过服务端修改本地权限。查询接口保留。

未知命名空间默认拒绝，没有“拿客户端注册对象直接执行”的回退。

### 临时授予

客户端主线程调用 `AbilityRegistry.replaceGrants(source, abilities, player)`：

- `source` 为授予来源，如 `metamorph:current`、`metamorph:acquired`。
- 同来源按 ID 差量替换，相同 ID 保留原包装实例。
- 新增调用 `onGranted`，移除调用 `onRevoked`。
- 退出世界/更换客户端玩家清除临时来源。
- 临时授予不写永久解锁 JSON；卷轴剔除由服务端玩家记录控制，客户端按快照过滤。
- 永久注册与其他来源不允许占用同一临时 ID。

这些钩子目前是**客户端展示/旧状态生命周期**，不是服务端效果授予授权。改变服务端状态必须通过自己的服务端执行器实现。

Metamorph 被动包装只展示。原模组已运行 `update/onMorph/onDemorph`，包装层不能再次调用。

## 玩家存档、迁移与卷轴

- 文件：`<overworld>/data/morphext/players/<UUID>.json`，包含 schema、owner、unlocked、removed、states、cooldowns。
- 服务端缓存按 MinecraftServer 实例和 UUID 隔离。客户端镜像绑定当前网络连接和玩家 UUID，不落地永久权限文件。
- 首次创建单人存档主人记录时，读取原 `config/aaaa0ggmc_morph_extension/{unlocks,ability_states}.json`。旧文件不改；其他玩家/独立服务器不自动继承这份全局数据。
- `legacyImported` 记录是否导入过旧文件；新记录已存在时绝不重导。旧数据里无法区分同步残留和卷轴意图的条目原样保留。
- 用同目录临时文件+原子替换保存（文件系统不支持时回退替换）。损坏、owner/schema 不符或超过 256 KiB 时拒绝读取/覆盖。手改存档应在玩家离线或服务器停止后操作，不能指望缓存自动热重载。
- 卷轴由原版物品使用协议抵达服务端，仅读取该玩家实际手持物品 NBT。普通版移除保留 states/cooldowns；强力版移除并删除框架 states/cooldowns，再调用 handler.onRevoked/onDataWiped 清理模组侧效果。
- 已提交移除但 addon 清理失败时仍同步权限，同时给出单独警告，不能假称清理成功。
- 客户端只接收服务器给本玩家的快照；不存在上传整个权限/状态 JSON 的 C2S 接口。
- 登录、重生、维度变化、卷轴操作时同步；成功释放仅在权限/状态实际变化时同步全量快照，纯冷却变化只发送释放结果。客户端可请求一次快照，服务端对此做频率限制；无每 tick 自定义网络查询。
- Sprint 已使用服务端玩家数据和客户端只读镜像，不再保留共享 enabled 字段。服务端/客户端 tick 分别维持权威/本地表现。
- 服务端冷却保存为主世界总 tick 的截止值，快照同样发送截止值并附带当前 `serverTick`，客户端据此初始化本地倒计时。暂停存档不会消耗冷却，重登不会靠清空客户端缓存绕过冷却。
- 此时钟是 `getTotalWorldTime()`，不是可用 `/time set` 改动的昼夜时间，文件标明 `cooldownClock=overworld_total_ticks`。只在释放/数据变更时保存截止值，不每 tick 写剩余量；释放结果中的剩余 tick 仅用于客户端预测的单次确认。
- `Handler.getCooldownTicks` 可提前声明的冷却先保存再执行。执行异常时不自动退款，以防第三方动作已产生部分效果却能连发；执行器返回更长的真实冷却时再补记截止值。动态冷却的第三方动作还必须依赖原模组自己的服务端冷却检查。
- 客户端分别保存本地能力冷却、服务端确认冷却、第三方药效冷却，服务端快照不清除纯客户端能力的本地冷却。

## 当前仍需补充的部分

- 没有统一资源事务、取消协议、网络版本协商或完整跨模组冷却策略；新的 handler 仍需自行检查对应能力条件。
- 旧存档中之前写入的临时能力记录保留，不自动删除，以免误删用户卷轴解锁/剔除记录。
- 注册表的生命周期钩子不是任意玩家共用的执行对象池，不能在服务端用其中的可变实例保存多人状态。

- CLIENT 旧能力只用于客户端表现/工具，不能视为反作弊安全的游戏能力。若需要卷轴识别其天生权限，也必须注册 common handler 的 isAvailable 元数据。
- 强力清除原模组自己的 NBT/计数/属性副作用依赖 handler 实现 onDataWiped；不会自动侵入或停掉由 Metamorph 自己拥有的内置被动。
- 旧存档、LAN 多玩家及独立服务器仍需真实回归验证；客户端和服务器必须同时安装支持当前网络协议的 jar。

## 搜索交互

- 鼠标悬停搜索框时高亮。
- 悬停搜索框后松开轮盘键，直接停留在搜索模式。
- 点击进入搜索仍保留；忽略打开轮盘那次按住 G 产生的重复字符。
- 松开并重新按下 G 后可以正常输入 G。

## 本轮检查

第一阶段对 41 个 Java 文件、第二阶段对 46 个 Java 文件做过 JDK `JavacTask.parse/analyze` 静态检查，0 错误。最终结果见任务状态文件；未执行 `generate`、Gradle 构建或游戏行为测试，未更新安装 jar。

待授权构建与用户游戏验收：单人/远程服务端动作、旧形态请求拒绝、共享冷却、Sprint 切换、临时授予回收、卷轴可见性、G 搜索重复输入与悬停松键。
