# BotMod — тренировочный PvP-бот для Minecraft 26.3

Мод добавляет бота, против которого можно **тренировать PvP**: он дерётся, стрейфит,
кайтит и подстраивается под игрока. Рядом идёт **BotLab** — личная лаборатория,
которая показывает телеметрию, кадры игры и статистику «обучения» бота.

- **Minecraft 26.3**, Fabric Loader 0.20.1, Fabric API 0.166.2+26.3, Java 25.
- Всё проверяется автоматически: сборка, серверные GameTest, **настоящий клиент в CI**
  со скриншотами.

---

## Быстрый старт

```bash
# собрать и прогнать серверные GameTest
./gradlew build

# поднять настоящего клиента в CI-режиме (headless)
./gradlew runProductionClientGameTest

# запустить в игре
./gradlew runClient
```

Готовый jar: [`releases/`](releases/) · Скриншоты: [`docs/screenshots/`](docs/screenshots/)

## Что умеет бот

| Режим | Поведение |
|---|---|
| `IDLE` | стоит, не провоцирует; отвечает, только если ударить |
| `SPARRING` | сближение, орбита вокруг игрока, удары с кулдауном |
| `RUSH` | агрессивный напор, быстрый стрейф, частые крит-прыжки |
| `DEFENDER` | держит дистанцию, поднимает щит в ближнем бою |

Киты: `IRON_SWORD`, `DIAMOND_SWORD`, `NETHERITE_SWORD`, `AXE`, `BOW` — реальная экипировка,
видимая игроку.

Адаптация: агрессия `0.5..1.5` растёт при попаданиях и падает при полученном уроне;
после пропущенного удара бот уклоняется, при HP < 30% — кайтит.

Команды:

```
/bot spawn [режим] [kit]     /bot mode <режим>     /bot kit <kit>
/bot duel                    /bot stop             /bot remove
/bot list                    /bot lab [url|on|off]
```

## Структура репозитория

```
├── src/main/java/com/botmod/       мод (сущность, AI, команды, телеметрия)
│   ├── entity/                     TrainingBotEntity, BotMode, BotKit
│   ├── entity/ai/                  BotCombatGoal
│   ├── command/                    /bot
│   ├── config/                     конфиг
│   └── lab/                        HTTP-телеметрия (127.0.0.1:17777)
├── src/client/java/com/botmod/     клиент (рендер, HUD, клавиши, кадры)
├── src/gametest/                   серверные + клиентские GameTest
├── lab/                            BotLab: дашборд, кадры, сценарии
├── tools/make_skin.py              генератор скина бота
├── docs/
│   ├── 01-research-minecraft-26.3.md   исследование 26.3
│   ├── 02-libraries-and-mods.md        библиотеки и моды
│   ├── 03-plan-and-status.md           план и статус
│   ├── 04-ci-headless-testing.md       рецепт headless-тестирования
│   ├── 05-bot-ai-and-learning.md       AI и адаптация
│   ├── 06-laboratory.md                BotLab
│   ├── screenshots/                    скриншоты настоящего клиента
│   └── tests/                          отчёты прогонов CI
├── releases/                       готовые jar
└── .github/workflows/build.yml     CI: сборка + GameTest + скриншоты
```

## BotLab — лаборатория

```bash
python3 lab/server.py --port 8099
```

Дашборд показывает: TPS/FPS, карточки ботов (режим, HP, **агрессия**, попадания),
кадры игры (1 кадр/сек), журнал событий, стадию проекта и галерею скриншотов из CI.

Подробнее: [`docs/06-laboratory.md`](docs/06-laboratory.md)

## Тесты

| Уровень | Что проверяет | Отчёт |
|---|---|---|
| Серверные GameTest | спавн, экипировка, урон, циклы режимов | [`docs/tests/ci/server-gametest/`](docs/tests/ci/server-gametest/) |
| Клиентский GameTest | настоящий клиент, спавн бота, дуэль, HUD, скриншоты | [`docs/tests/ci/client-gametest/`](docs/tests/ci/client-gametest/) |

Как устроено тестирование: [`docs/tests/README.md`](docs/tests/README.md)

## Статус

Этап 4 (GameTest в CI + скриншоты) — закрыт, этап 5 (продвинутый AI) — в работе.
Актуальная таблица этапов: [`docs/03-plan-and-status.md`](docs/03-plan-and-status.md)
