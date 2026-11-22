# Серверные GameTest — отчёт прогона

Последний прогон: **успех** (job `build` зелёная).

## Что проверяется

Код: `src/gametest/java/com/botmod/gametest/BotModGameTest.java`

| Тест | Проверка |
|---|---|
| `botSpawnsWithAttributes` | бот создаётся, режим `IDLE`, максимум здоровья 20 |
| `kitAppliesEquipment` | kit `DIAMOND_SWORD` надевает меч в основную руку, щит во вторую, нагрудник |
| `botDealsDamage` | бот с назначенной целью реально снимает HP (урон проходит) |
| `modeCycles` | цикл режимов `IDLE → SPARRING → RUSH → DEFENDER → IDLE` |

## Как запустить

```bash
./gradlew build      # серверные GameTest выполняются как часть build
```

Отчёт Gradle: `build/reports/tests/test/index.html`, результаты GameTest — в логе задачи.

## Почему эти тесты важны

Они проверяют **боевую логику без клиента**: если сломается спавн, экипировка или урон —
CI упадёт до того, как это увидит игрок.
