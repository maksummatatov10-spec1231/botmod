# Тесты BotMod

Всё, что проверяет мод, лежит здесь — код, отчёты прогонов, логи и скриншоты.

```
docs/tests/
├── README.md                     ← этот файл
└── ci/                           ← артефакты прогонов на GitHub Actions
    ├── client-gametest/          ← настоящий клиент 26.3 в Xvfb/offscreen
    │   ├── README.md             ← что проверяет этот прогон
    │   ├── game-latest.log       ← полный лог игры (лучший источник правды)
    │   ├── client.log            ← вывод Gradle вокруг запуска
    │   ├── graphics-diag.log     ← glxinfo/xdpyinfo окружения
    │   └── artifacts.txt         ← что лежало в build/run после прогона
    └── server-gametest/          ← серверные GameTest (headless, без графики)
```

## Что где проверяется

| Уровень | Где код | Что проверяет | Как запустить |
|---|---|---|---|
| Серверные GameTest | `src/gametest/java/.../BotModGameTest.java` | спавн бота, атрибуты, экипировка kit-а, нанесение урона, циклы режимов | `./gradlew build` (выполняется автоматически) |
| Клиентский GameTest | `src/gametest/java/.../BotModClientGameTest.java` | настоящий клиент: мир загружается, бот спавнится, дуэль стартует, HUD рисуется | `./gradlew runProductionClientGameTest` |
| Скриншоты | `docs/screenshots/` | визуальная проверка мира, бота, боя и HUD | там же, коммитятся CI |

## Как читать логи

1. `game-latest.log` — лог самой игры: `Using graphics backend ...`, `Created window using SDL video driver: ...`,
   `System chat: Spawned a Sparring bot`, ошибки рендера.
2. `client.log` — обёртка Gradle: упала ли задача `runProductionClientGameTest` и с каким кодом.
3. `artifacts.txt` — список каталогов и PNG в `build/run` (проверка, что скриншоты вообще создались).

## Известные «нормальные» предупреждения в headless-прогоне

Их **не** надо чинить — они следствие отсутствия GPU/аудио:

- `InvalidCredentialsException: Status: 401` и `Failed to parse into SignedJWT: FabricMC` — оффлайн-сессия;
- `Failed to open OpenAL device` / `Error starting SoundSystem` — нет аудиоустройства;
- `Failed to load library flite` — нет рассказчика;
- `Illegal option value 0 for Anisotropic Filtering` — llvmpipe не умеет анизотропию.

## Правило

Каждый прогон CI перезаписывает файлы в `docs/tests/ci/` и коммитит их в ветку —
поэтому в репозитории всегда лежит **последний** отчёт, а не «где-то в облаке».
