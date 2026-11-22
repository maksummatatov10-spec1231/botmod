# Исследование: Minecraft 26.3 (Java Edition) и всё, что изменилось до него

> Дата среза: **22 ноября 2026**. Всё, что ниже — проверено по официальным
> источникам (FabricMC, Mojang, NeoForged) и по реальной компиляции мода в CI.
> В конце — список изменений, которые **я лично поймал на своей шкуре** при сборке.

---

## 1. Новая схема версий

С 2026 года Mojang перешла на схему **`год.дроп`** вместо `1.21.x`:

| Версия | Название | Дата | Ключевое |
|---|---|---|---|
| 1.21.11 | последняя «старая» | 2025 | последний обфусцированный релиз |
| **26.1** | Tiny Takeover | 24 марта 2026 | **Java 25**, **необфусцированный код**, Yarn больше не нужен |
| **26.2** | Chaos Cubed | 16 июня 2026 | серные пещеры, серный куб, Vulkan-тест, список друзей Java |
| **26.3** | Wilderness Bound | 15 сентября 2026 | dapled forest, тополь, заброшенные лагеря, подушки, соломенные кровати, OIT, SDL3 |

Форматы паков:

| Версия | Data Pack | Resource Pack |
|---|---|---|
| 26.2 | 107.1 | 88.0 |
| **26.3** | **121.0** | **97.1** |

> Снапшоты 26.3 шли с 108.0 по 121.0 — отсюда скачок номера.

---

## 2. Тулчейн для 26.3 (проверено в CI)

| Компонент | Версия |
|---|---|
| Minecraft | **26.3** |
| Java | **25** (JDK 25, `--release 25`) |
| Fabric Loader | **0.20.1** |
| Fabric API | **0.166.2+26.3** |
| Fabric Loom | **1.19-SNAPSHOT** |
| Gradle | **9.8.2** (`gradle-9.8.2-bin.zip`) |

```gradle
// build.gradle
plugins { id 'net.fabricmc.fabric-loom' version "${loom_version}" }
dependencies {
    minecraft "com.mojang:minecraft:26.3"
    implementation "net.fabricmc:fabric-loader:0.20.1"
    implementation "net.fabricmc.fabric-api:fabric-api:0.166.2+26.3"
}
tasks.withType(JavaCompile).configureEach { it.options.release = 25 }
```

```json
// fabric.mod.json → depends
{ "fabricloader": ">=0.20.1", "minecraft": "~26.3", "java": ">=25", "fabric-api": "*" }
```

---

## 3. Главные изменения 26.3 для мододела

### 3.1. Что удалили

| Удалено | Чем заменить |
|---|---|
| `CompostingChanceRegistry`, `FuelRegistry` (Fabric API) | компоненты `DataComponents.COMPOSTABLE`, `COOKING_FUEL`, `BREWING_FUEL` |
| `FabricPotionBrewingBuilder` | зелья теперь data-driven, JSON в `recipe/` |
| `StrippableBlockRegistry`, `TillableBlockRegistry`, `FlattenableBlockRegistry` | `BlockTransformerHelper`, JSON в `block_transformer/` |
| `Block#codec` и реестр `block_type` | удалено полностью |
| `PotionBrewing`, `fuelValues` в `MinecraftServer` | data-driven аналоги |
| `ResourceLocation` | **`Identifier`** (`net.minecraft.resources.Identifier`) |
| Константы `EntityType.VILLAGER` и т.п. | **`EntityTypes.VILLAGER`** (класс `EntityType` остался фабрикой) |

### 3.2. Что переименовали / поменяли

| Было (26.1.2 и раньше) | Стало в 26.3 |
|---|---|
| `state.blocksMotion()` | `state.isSolid()` |
| `entity.hurtMarked = true` | поле удалено; для игрока — `ClientboundSetEntityMotionPacket`, для мобов не нужно |
| `entity.invulnerableTime = 0` | `setInvulnerableTime(0)` |
| `player.swing(hand, true)` | `player.swing(hand, SwingAnimation.DEFAULT, true)` |
| `target.knockback(x, y, z)` | `knockback(power, xd, zd, DamageSource, damage)` (+ перегрузка с `comesFromEffect`) |
| `player.displayClientMessage(text, true)` | `player.sendOverlayMessage(text)` |
| `inventory.placeItemBackInInventory(stack)` | `placeItemBackInInventory(stack, Prediction.SERVER_ONLY)` |
| `new SignText().setMessage(i, line)` | `new SignText(front, filtered, DyeColor, boolean)` |
| `sign.setText(text, true)` | `sign.setText(text, SignTextSlot.FRONT)` |
| `Blocks.WHITE_CARPET` | `Blocks.CARPET.white()` / `lightGray()` / `pick(DyeColor)` |
| `CommandRegistrationCallback` `(dispatcher, registryAccess, environment)` | **`(dispatcher, CommandBuildContext, Commands.CommandSelection)`** |
| `SharedSuggestionProvider.suggest(...)` | перегрузки изменились — надёжнее собирать провайдер лямбдой |
| `Surface rules` | **material rules** (`worldgen/material_rule`) |
| Configured features | `worldgen/feature`, регистрация в `BuiltInRegistries.FEATURE_TYPE` |
| Number providers | разделены на float/int, регистрируются в JSON |
| `GuiGraphics` | **`GuiGraphicsExtractor`** + `HudElementRegistry` |
| GLFW | **SDL3** (окно, ввод, `InputConstants`, `TextInputManager`) |

### 3.3. Рендер

- **OIT (order-independent transparency)** — новый алгоритм прозрачности, тумблер `F3 + X`.
- **Renderpearl** — рендер-код вынесен из Blaze3D в `com.mojang.renderpearl.*`
  (`api`, `backend`, `frontend`). `RenderSystem#outputColorTextureOverride` удалён.
- Терраин рисуется через **MultiDrawIndirect**.
- `PoseStack#mulPose(Quaternionf)` удалён → `rotate(Axis, angle)`.
- Шейдеры: `#moj_import` → `#include`, обязательные `layout(location)` у in/out.

### 3.4. Мир и данные

- Рецепты и достижения теперь **reloadable registries** (единый путь загрузки).
- Моды могут регистрировать собственные reloadable-реестры:
  `DynamicRegistries.registerReloadable(KEY, CODEC)`.
- Ванильные блоки больше не содержат `Codec`.

### 3.5. Геймплей 26.3 (чтобы бот вёл себя «по-настоящему»)

- Новый биом **Dappled Forest**, тополя трёх цветов, полный набор тополиной древесины.
- **Abandoned Camp** в 18 биомах, новые **Explorer Maps**.
- **Cushions** (сидение), **Straw Beds** (одноразовые, не меняют точку возрождения).
- Лестницы/плиты из шерсти и бетона всех 16 цветов.
- Щит + мотыга/лопата: поднятие щита приоритетнее вскапывания.
- Эндер-жемчуг сбрасывает импульс игрока после телепорта.
- Эндермены и шалкеры больше не телепортируются на бедрок.
- Постоянные мобы «засыпают» без игроков рядом (как обычные).

---

## 4. Практический чек-лист «пишу код под 26.3»

1. Импорты: `net.minecraft.resources.Identifier`, **не** `ResourceLocation`.
2. Маппинги — только Mojang; Yarn закончился на 1.21.11. `loom.officialMojangMappings()` не нужен: код и так необфусцирован.
3. NBT — только через `ValueInput` / `ValueOutput`, не через `CompoundTag` напрямую.
4. Все константы типов сущностей — из `EntityTypes`.
5. Атака моба: `swing(hand, SwingAnimation.DEFAULT, true)`, удар — `hurtServer(ServerLevel, DamageSource, float)`.
6. Отдачу **не** вызывать вручную: её применяет сам `hurtServer` через `dealDefaultKnockback`.
7. GUI — `GuiGraphicsExtractor`, HUD — `HudElementRegistry.attachElementBefore(VanillaHudElements.X, ...)`.
8. Клавиши — `KeyMapping.Category.register(Identifier)`, коды — `InputConstants`.
9. Текстовые поля — обязательно `Minecraft#onTextInputFocusChange`, иначе ввод символов сломается на SDL3.
10. Команды — колбэк с `CommandBuildContext` и `CommandSelection`.
11. Рендер — `EntityRenderers.register(...)` в клиентском entrypoint; клиентский source set отделён (`splitEnvironmentSourceSets`).

---

## 5. Что ещё важно знать

- **26.1 сделал код открытым**: это главная причина, почему имена в этом документе совпадают с реальными именами в игре. Ошибки компиляции теперь читаются как обычные Java-ошибки.
- **Yarn** жив только для версий ≤ 1.21.11; миграция на 26.1+ требует перехода на Mojang-маппинги (`migrateMappings` в Loom, Kotlin не поддерживается).
- **Java 25** обязательна: `options.release = 25`, `java >= 25` в `fabric.mod.json`.
- Сервер 26.3 стартует **с включённым whitelist** по умолчанию — при тестах на сервере это важно.
- Fabric просит игроков не торопить мододелов: большинство модов на 26.3 обновляются с задержкой (на октябрь 2026 на Modrinth ~6.5k модов с 26.3 против ~13k на 26.2).

---

## 6. Ошибки, которые я получил при реальной сборке (и как исправил)

| # | Ошибка компиляции | Причина | Решение |
|---|---|---|---|
| 1 | `no suitable method found for swing(InteractionHand)` | в 26.3 `swing` требует `SwingAnimation` | `swing(hand, SwingAnimation.DEFAULT, true)` |
| 2 | `no suitable method found for knockback(double,double,double)` | сигнатура стала 5-аргументной | убрал ручную отдачу — её применяет `hurtServer` |
| 3 | `cannot find symbol` на `displayClientMessage` | у `LivingEntity`/`Player` его больше нет | `sendOverlayMessage(Component)` |
| 4 | `invalid method reference` на `CommandRegistrationCallback` | сигнатура колбэка изменилась | `(dispatcher, CommandBuildContext, Commands.CommandSelection)` |
| 5 | `SharedSuggestionProvider.suggest(...) cannot be applied` | перегрузки изменились | провайдер лямбдой с `SuggestionsBuilder` |

> Все пять найдены через GitHub Actions: локально в песочнице нет доступа к
> `maven.fabricmc.net` и серверам Mojang, поэтому сборка идёт в CI
> (`.github/workflows/build.yml`), а я читаю annotations через `gh api`.

---

## 7. Источники

- Fabric for Minecraft 26.3 — https://fabricmc.net/2026/09/15/263.html
- Fabric Docs: Porting / Migrating Mappings — https://docs.fabricmc.net/develop/porting/mappings/
- NeoForged 26.2 → 26.3 Migration Primer — https://docs.neoforged.net/primer/docs/26.3/
- Minecraft Java 26.3 changelog — https://mcbe.news/changelogs/java_edition_26.3
- Fabric Example Mod (ветка 26.3) — https://github.com/FabricMC/fabric-example-mod/tree/26.3
- Fabric API changelog — https://modrinth.com/mod/fabric-api/changelog
- Обзор «Porting a Fabric mod to 26.3» — https://spicesfire.com/blog/porting-a-fabric-mod-to-minecraft-26-3/
