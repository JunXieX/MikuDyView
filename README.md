# MikuDyView

作者：JunXieX　|　MikuMC系列插件交流群：1105054380　|　非开源项目，请勿二次分发

按世界动态调节服务端的模拟距离（simulation distance）与视距（view distance）。服务器空闲时把距离还给玩家，压力上来之前先收回去，每个世界有自己的上下限。

## 功能特性

- **按世界独立管理**：每个世界单独的模拟距离与视距上下限、权重和检查间隔，互不牵连。
- **两种信号一起判断**：MSPT 反映当前负载，区块预算反映"这个人数下应该给多少"，取两者中更保守的目标。
- **升慢降快**：升视距要给每个玩家补发外圈区块，所以逐步上升并需要连续确认；过载时一步可以降多格。
- **刚升就过载立刻回滚**：不等下降确认计数慢慢爬，直接退回升之前的值。
- **预测式否决**：用最近的（区块数, MSPT）样本做线性回归，预测升一格会不会把 MSPT 推过阈值，会就不升。
- **内存保护**：空闲堆低于阈值时禁止一切上调，下调不受影响。
- **冲突检测**：连续三次发现世界距离被别的插件改写，就停止管理该世界并在控制台点名，避免每轮互相覆盖导致玩家区块反复重载。
- **可解释**：`/mvd history` 记录每次变更的原因，`/mvd simulate` 可以在不落地的情况下预演下一轮的目标。
- **客户端视距封顶**（可选）：把视距压到该世界客户端实际能渲染的最大值，避免加载没人看得见的区块。
- **逐玩家固定视距**（可选）：按权限 `mikudyview.fixed.<2-32>` 给个别玩家固定视距。
- **Folia 支持**：调度全部走 Paper 的区域化调度 API，不依赖 Bukkit 老调度器。

## 环境要求

| 项目 | 要求 |
|---|---|
| 服务端 | Paper 26.3 及以上，或 Folia 26.3 及以上 |
| Java | 25 |
| 前置插件 | 无 |
| 客户端 | 原版即可 |

## 安装

1. 把 `MikuDyView-1.0.0.jar` 放进 `plugins/`。
2. 重启服务器。
3. 首次启动会在 `plugins/MikuDyView/` 生成两个文件：
   - `config.yml`：所有可调参数，默认视距 4–12、模拟距离 4–8。
   - `messages.yml`：命令回显文本，颜色用 MiniMessage 标签。

默认配置开箱即用：`enabled: true`、`mode: mixed`，开服 2 分钟后开始调节。

## 配置说明

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关；关闭后不再自动修改距离，手动命令仍可用 |
| `mode` | `mixed` | `mixed` 自动调节；`off` 只保留手动命令 |
| `check-interval` | `200` | 全局快照间隔（tick），40–2400 |
| `start-delay` | `2400` | 开服后等待多少 tick 再开始 |
| `increase-mspt` / `decrease-mspt` | `40` / `47` | 低于前者考虑升，高于后者考虑降 |
| `max-increase-step` / `max-decrease-step` | `2` / `4` | 单次调节的最大格数 |
| `checks-to-increase` / `checks-to-decrease` | `4` / `1` | 连续多少次检查才执行 |
| `min-increase-interval` | `200` | 两次上调之间的最小间隔（tick） |
| `revert-on-overload` | `true` | 刚升就过载时直接回滚 |
| `prediction.enabled` | `true` | 是否用回归预测否决上调 |
| `memory-safeguard.min-free-percent` | `15.0` | 空闲堆低于此值禁止上调 |
| `client-view-cap.enabled` | `false` | 是否按客户端视距封顶 |
| `per-player-fixed-view.enabled` | `false` | 是否启用权限固定视距 |
| `afk-throttle.enabled` | `false` | 是否给挂机玩家降视距 |
| `budget.ticking` / `budget.non-ticking` | `5780` / `6720` | 全局区块预算，对应 20 人、模拟 8 / 视距 12 |
| `world-settings.<世界>` | 见下 | 每个世界的上下限、权重与检查间隔 |

`world-settings.default` 的默认值：

```yaml
world-settings:
  default:
    exclude: false
    simulation:
      min: 4
      max: 8
    view:
      min: 4
      max: 12
    weight: 1.0
    ticks-per-check: 0
```

## 命令

| 命令 | 说明 | 权限 |
|---|---|---|
| `/mvd status` | 查看模式、MSPT、空闲堆与每个世界的当前距离 | `mikudyview.command.status` |
| `/mvd history [数量]` | 查看最近的变更及原因 | `mikudyview.command.history` |
| `/mvd simulate [世界]` | 预演下一轮的目标，不落盘 | `mikudyview.command.simulate` |
| `/mvd set sim\|view <2-32> [世界]` | 手动设置并保持到下次 reload | `mikudyview.command.set` |
| `/mvd reload` | 重读配置 | `mikudyview.command.reload` |
| `/mvd enable` / `/mvd disable` | 开关自动调节并写回配置文件 | `mikudyview.command.toggle` |

## 权限节点

| 节点 | 默认 | 说明 |
|---|---|---|
| `mikudyview.command` | OP | `/mvd` 的基础权限 |
| `mikudyview.command.status` | OP | 状态查询 |
| `mikudyview.command.history` | OP | 变更历史 |
| `mikudyview.command.simulate` | OP | 预演 |
| `mikudyview.command.set` | OP | 手动设置距离 |
| `mikudyview.command.reload` | OP | 重载配置 |
| `mikudyview.command.toggle` | OP | 开关自动调节 |
| `mikudyview.fixed.<2-32>` | 无 | 逐玩家固定视距，需在配置中启用 |

## 常见问题

**装上去之后距离没有变化？**
默认有 2 分钟启动延迟（`start-delay: 2400`），并且要等 MSPT 与区块预算给出同一方向。用 `/mvd status` 看当前值，用 `/mvd simulate` 看下一轮会做什么。

**为什么视距升得这么慢？**
每升一格，服务端都要给该世界每个玩家补发外圈区块。20 人、视距 8 升到 9，一次就是 1440 个区块包。慢升是为了避免带宽尖峰，调 `checks-to-increase` 与 `max-increase-step` 可以改。

**控制台出现"已停止自动管理"？**
说明连续三次检查发现世界距离被其它插件改写。先确认是哪个插件在写视距，处理完后执行 `/mvd reload` 重新接管。

**支持 Folia 吗？**
支持。所有调度使用 Paper 的区域化调度 API：全局任务走 GlobalRegionScheduler，逐玩家操作用各自实体的 EntityScheduler。
