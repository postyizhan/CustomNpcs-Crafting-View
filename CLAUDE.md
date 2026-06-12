# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个 Minecraft 1.7.10 的 Forge Mod，为 CustomNPCs 模组的木工台（Carpentry Bench）和铁砧（Anvil）界面添加侧边配方面板，支持分类、搜索和一键填充合成格。

发布在 Modrinth：`customnpcs-crafting-view`（项目 ID：`5skiXDDB`）

## 分支结构

代码分布在多个分支，每个分支对应不同版本：

| 分支 | 目标 |
|------|------|
| `1.6.4` | CustomNPCs 1.6.4（旧版，使用 `build.gradle`） |
| `1.7.10` | **主开发分支**。单源码经反射适配层同时兼容原版 CustomNPCs 与 CustomNPC+，产出的单个 jar 双兼容（使用 GTNH Convention） |
| `1.7.10-cnpcplus` | **已废弃**。功能已合入 `1.7.10`，仅保留作历史快照，不再维护 |
| `main` | 仅含 README，无源码 |

**开发时请切换到 `1.7.10` 分支**：`git checkout 1.7.10`。CustomNPC+ 兼容已并入此分支，无需再用 `1.7.10-cnpcplus`。

## 构建命令

项目使用 [GTNH Convention Gradle 插件](https://github.com/GTNewHorizons/GTNHGradle)（`1.7.10` 分支）：

```bash
# 构建 mod jar（默认以原版 CustomNPCs 为编译/运行宿主）
./gradlew build

# 在开发环境中运行客户端（需要 Minecraft 资源）
./gradlew runClient

# 以 CustomNPC+ 为宿主编译/运行（-Pcnpcplus 切换 libs 中的宿主 jar）
./gradlew build -Pcnpcplus
./gradlew runClient -Pcnpcplus

# 刷新依赖
./gradlew --refresh-dependencies

# 1.6.4 分支使用标准 ForgeGradle
./gradlew build
```

构建产物输出到 `build/libs/`。改造后源码不再直接 import 宿主的 `RecipeCarpentry`（仅在 `compat` 反射层以字符串 FQN 出现），编译期不强依赖该类，故任一宿主 jar 都能编译；两宿主 jar 均为 `compileOnly`/`runtimeOnlyNonPublishable`，不打进产物 —— **最终单个 jar 可同时投放原版与 CustomNPC+ 整合包**。

## 架构

### 包结构（`com.customnpcs.craftingview`）

```
CraftingViewMod.java      — @Mod 入口，FML 生命周期，代理初始化
Config.java               — Forge Configuration 读写，分类定义解析
CommonProxy.java          — 服务端代理（空实现）
ClientProxy.java          — 客户端代理，注册 FML 事件总线监听器

client/
  GuiEventHandler.java    — 监听 GuiOpenEvent / DrawScreenEvent，管理面板生命周期
  RecipePanel.java        — 面板状态（配方列表、过滤、分页、搜索框）
  RecipePanelRenderer.java — 纯渲染逻辑，将 RecipePanel 状态绘制到 GUI
  TooltipHelper.java      — 物品 tooltip 渲染辅助

network/
  PacketHandler.java      — 注册 SimpleNetworkWrapper 频道
  PacketFillCraftingGrid.java — 客户端→服务端数据包，将选中配方填入合成格

compat/                   — 宿主兼容反射适配层（屏蔽原版 CustomNPCs 与 CustomNPC+ 的 API 差异）
  RecipeAccess.java       — 全 static 反射门面，preInit 时探测并缓存句柄，提供配方查询
  RecipeView.java         — RecipeCarpentry 实例的轻量包装器，业务代码统一面向此类型
```

### 宿主兼容（compat 反射适配层）

原版 CustomNPCs 与 CustomNPC+ 仅有 3 处符号差异，由 `RecipeAccess.init()`（preInit 调用）逐候选探测并锁定：

| 维度 | 原版 CustomNPCs | CustomNPC+ |
|------|------|------|
| 配方类 FQN | `noppes.npcs.controllers.RecipeCarpentry` | `noppes.npcs.controllers.data.RecipeCarpentry` |
| 单例字段 | `RecipeController.instance` | `RecipeController.Instance` |
| 配方集合字段 | `anvilRecipes` | `carpentryRecipes` |

成员方法/字段名两边一致，`RecipeController`、`ContainerCarpentryBench`、`GuiNpcCarpentryBench` 等同包同名，无需反射。两 mod 的 modid 均为 `customnpcs`（互斥，运行时只存在一个），故 `@Mod(dependencies = "required-after:customnpcs")` 在两宿主下都成立。业务代码统一使用 `RecipeView`（基于底层 delegate 身份实现 `equals`/`hashCode`），不直接接触宿主类型。探测失败时 `RecipeAccess.isAvailable()` 返回 false，面板降级隐藏而非崩溃。

### 数据流

1. `GuiEventHandler.onGuiOpen` 检测到 `GuiNpcCarpentryBench` 打开时，若 `RecipeAccess.isAvailable()` 则创建 `RecipePanel`（根据 `container.getMetadata() >= 4` 判断是否为铁砧模式）
2. `RecipePanel` 经 `RecipeAccess.getAllCarpentryRecipes()` 读取所有配方（反射宿主单例 + 配方集合），结合 `Config.categories` 构建分类列表
3. 每帧 `GuiEventHandler.onGuiDrawPost` 调用 `RecipePanelRenderer.render` 绘制面板
4. 用户点击配方后，客户端发送 `PacketFillCraftingGrid`；服务端经 `RecipeAccess.getRecipeById(id)` 取配方并填充合成格

### 配置格式

配置文件位于 `config/customnpcs_crafting_view.cfg`，分类格式：

```
分类名|recipeId1,recipeId2,...|recipeName1,recipeName2,...
```

留空字段表示不过滤（即"浏览全部"行为）。

## 依赖

- Minecraft Forge 1.7.10-10.13.4.1614
- CustomNPCs（`required-after:customnpcs`）—— 原版 CustomNPCs 或 CustomNPC+ 任一，二者 modid 同为 `customnpcs`
- 宿主 jar 置于 `libs/`：`CustomNPCs_1.7.10d(29oct17).jar`（默认）与 `CustomNPC-Plus-1.11.1.jar`（`-Pcnpcplus`），均不打进产物
- GTNH Convention 插件（`1.7.10` 分支通过 `gtnhShared/` 共享配置）
