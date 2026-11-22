# Клиентский GameTest — отчёт прогона

Последний прогон: **успех** (обе job зелёные).

## Что делает этот прогон

1. Поднимает **настоящий клиент Minecraft 26.3** в CI (без GPU и монитора):
   Mesa llvmpipe + SDL offscreen-драйвер, OpenGL 4.6 Core Profile.
2. Создаёт одиночный мир через Fabric Client GameTest API.
3. Спавнит бота командой `/bot spawn sparring diamond_sword`.
4. Запускает дуэль командой `/bot duel`.
5. Делает 4 скриншота: мир, бот, дуэль, HUD — они лежат в `docs/screenshots/`.

## Подтверждение из лога (реальный прогон)

```
Using graphics backend OpenGL, using drivers: 4.6 (Core Profile) Mesa 25.2.8
Created window using SDL video driver: offscreen
System chat: Spawned a Sparring bot (Training Bot)
System chat: Training Bot starts fighting you. Good luck!
BUILD SUCCESSFUL
```

## Файлы

| Файл | Что внутри |
|---|---|
| `game-latest.log` | полный лог игры (рендер, спавн, дуэль, выключение) |
| `client.log` | вывод Gradle: `runProductionClientGameTest` и код завершения |
| `graphics-diag.log` | `glxinfo -B` / `xdpyinfo`: чем рендерили (llvmpipe 4.6) |
| `artifacts.txt` | дерево `build/run` и список PNG после прогона |

## Как воспроизвести локально

```bash
./gradlew runProductionClientGameTest
# скриншоты появятся в build/run/clientGameTest/screenshots
```

Требуется JDK 25. На Linux без монитора добавьте:
```bash
export SDL_VIDEO_DRIVER=offscreen SDL_VIDEO_FORCE_EGL=1 LIBGL_ALWAYS_SOFTWARE=1
```
