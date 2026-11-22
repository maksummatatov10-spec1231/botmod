# Headless-тестирование клиента 26.3 в CI

> Рецепт, выведенный экспериментально на MC 26.3 + Fabric API 0.166.2+26.3 + Loom 1.19.
> Здесь всё, что не очевидно из документации и стоило нескольких итераций CI.

## Задача

Запустить **настоящий клиент** Minecraft 26.3 на GitHub Actions (без GPU, без монитора),
выполнить клиентский GameTest и получить **скриншоты**.

## Что не работает

| Попытка | Результат | Причина |
|---|---|---|
| `xvfb-run` из Loom (`useXVFB = true`) | зависание на 15+ минут | Loom стартует Xvfb с **8-битным** экраном — у него нет GLX visual, клиент ждёт окно вечно |
| свой Xvfb `-screen 0 1600x900x24` | `Failed to create window for OpenGL context: Couldn't find matching GLX visual` | 26.3 просит у SDL **sRGB-фреймбуфер**; GLX в Xvfb не даёт такого visual |
| Vulkan без ICD | `Installed Vulkan doesn't implement the VK_KHR_surface extension` | загрузчик Vulkan есть, программного драйвера нет |
| только `libgl1-mesa-dri` | то же, GLX visual не найден | Xvfb лишь *рекомендует* DRI-драйверы, без `--no-install-recommends` их может не быть |

## Что работает

1. **Mesa + lavapipe**: `libgl1-mesa-dri`, `libglx-mesa0`, `mesa-vulkan-drivers`, `libvulkan1`.
2. **SDL offscreen-драйвер** — клиент рендерит через Mesa EGL без X-сервера:
   ```yaml
   env:
     SDL_VIDEO_DRIVER: offscreen
     SDL_VIDEO_FORCE_EGL: '1'
     LIBGL_ALWAYS_SOFTWARE: '1'
     MESA_GL_VERSION_OVERRIDE: '4.6'
   ```
   Реальный лог: `Using graphics backend Vulkan, using drivers: 1.4.318 llvmpipe Mesa 25.2.8`,
   `Created window using SDL video driver: x11` (или `offscreen`).
3. **Тестовый мод должен попасть в класспас.** `fabricApi.configureTests { createSourceSet = true }`
   создаёт source set `gametest`, но production-прогон его **сам не подхватывает**:
   ```gradle
   tasks.register("gametestJar", Jar) {
       archiveClassifier = "gametest"
       from sourceSets.gametest.output
   }
   tasks.register("runProductionClientGameTest", net.fabricmc.loom.task.prod.ClientProductionRunTask) {
       mods.from tasks.named("gametestJar")
       jvmArgs.add("-Dfabric.client.gametest")
       useXVFB = false   // иначе Loom обернёт в свой 8-битный xvfb-run
   }
   ```
   Без этого в списке модов нет `botmod-test` — и тест молча не выполняется.
4. **JVM должна уметь завершиться.** `com.sun.net.httpserver` держит **не-daemon** поток
   `HTTP-Dispatcher`: клиент закрывается, а JVM висит, пока 26.3-ий `ClientShutdownWatchdog`
   не убьёт процесс (exit 248). Решение — гасить HTTP-сервер на `SERVER_STOPPING`.
5. **Не блокировать рендер**: кадры снимаются в `Screenshot.takeScreenshot(target, downscale, callback)`,
   PNG пишется во временный файл, HTTP-запрос уходит из фонового потока.

## Скриншоты

Клиентский GameTest:
```java
try (TestSingleplayerContext sp = context.worldBuilder().create()) {
    sp.getConnection().waitForChunksRender();
    sp.getServer().runCommand("bot spawn sparring diamond_sword");
    sp.getConnection().waitForClientboundEntityUpdates(ModEntities.TRAINING_BOT);
    context.waitTicks(20);
    context.takeScreenshot("botmod-bot");
}
```
Loom складывает их в `build/run/clientGameTest/screenshots`, CI коммитит в `docs/screenshots/`.

## Полезные ссылки

- SDL3 hint `SDL_VIDEO_FORCE_EGL` — https://wiki.libsdl.org/SDL3/README-migration
- Аналогичные находки: `jekitchen/mc-fontsys#3`, `Romanica-MC/RomanicaClient#39`, `Darkttd/Maple#19`
