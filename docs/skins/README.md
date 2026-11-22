# Скины бота

| Файл | Что это |
|---|---|
| `training_bot_source.png` | **основной скин** — прислал пользователь (`f25cda07d206d319.png` из `main`). Крипер-скин: чёрная база, зелёные глаза, надпись CREEPER |
| `training_bot_rgba.png` | тот же скин, перекодированный в 8-бит RGBA (то, что лежит в ресурсах мода) |
| `generated_alternative.png` | альтернативный скин от `tools/make_skin.py` (не используется в игре) |

## Какой файл использует игра

```
src/main/resources/assets/botmod/textures/entity/training_bot.png
```

Это копия `training_bot_rgba.png`. Формат — **стандартный скин игрока 64×64**,
поэтому он ложится на `HumanoidModel` без правок UV.

## Почему чистили слой «шляпы»

В исходном скине область hat (32,0)-(64,16) залита непрозрачным чёрным. Слой hat
рисуется **поверх головы**, поэтому лицо (и зелёные глаза) оказывалось закрытым —
бот выглядел полностью чёрным.

`python3 tools/skin_clear_hat.py docs/skins/training_bot_rgba.png out.png`
делает эту область прозрачной, после чего глаза видны.

## Почему перекодировали

Исходный файл — PNG с 4-битной палитрой (color type 3). stb_image (его использует
`NativeImage`) такие файлы читает, но 8-бит RGBA — самый предсказуемый вариант
для всех версий загрузчика ресурсов.

## Как заменить скин

1. Положите новый PNG 64×64 (стандартная раскладка скина) в `docs/skins/`.
2. Скопируйте его в `src/main/resources/assets/botmod/textures/entity/training_bot.png`.
3. Проверьте в игре (`/bot spawn`) и в CI-скриншотах (`docs/screenshots/`).

Скрипт `python3 tools/make_skin.py` генерирует **запасной** вариант в
`docs/skins/generated_alternative.png` и игровой скин не трогает.
