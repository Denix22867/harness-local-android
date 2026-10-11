# Сборка и происхождение компонентов

## Компактный APK

Требования: Linux, Java 21, Python 3, Android SDK platform 35 и build-tools 35.0.0, ECJ 3.42.0. Gradle не используется. Нативные ARM64-библиотеки, исходники и все необходимые небольшие assets уже включены в репозиторий; архив ядра отдельно размещён в Releases.

```sh
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
python3 scripts/setup_ecj.py
export DSH_ANDROID_SDK="$ANDROID_HOME"
python3 scripts/build_apk.py
```

Результат: `dist/Harness-Local-Mobile.apk` и `dist/SHA256SUMS`. При отсутствии `tools/preview.keystore` создаётся новый локальный ключ. Он не совпадает с ключом опубликованных APK. Для сборки владельцем проекта с прежней подписью задайте `DSH_SIGNING_KEYSTORE`; приватный ключ хранится отдельно от Git. Пароль и alias в текущем preview-рецепте — `android` и `preview`; не используйте этот рецепт без изменений для промышленной подписи.

SDK можно указать через `DSH_ANDROID_SDK`, ECJ через `DSH_ECJ_JAR`. Архив ядра не требуется для сборки APK. HTTPS-адрес ядра, размер, SHA-256 и вложенные ZIP-пути заданы в `app/src/main/assets/runtime-download.json`; перечень файлов и режимов — в `runtime-manifest.json`.

## Пересборка изменённого PRoot

Установите Android NDK r27d, `make`, `patch` и `readelf` (binutils). Подготовлены заголовки и опубликованные Termux-библиотеки talloc/shmem в `native-deps/termux`; их исходники и build recipes находятся в `third-party-sources`.

```sh
export DSH_ANDROID_NDK=/path/to/android-ndk-r27d
python3 scripts/build_proot_android.py
python3 scripts/build_apk.py
```

PRoot исходного тега `v5.1.107.96` модифицируется `proot-dirent-types.patch`. Для эмулированных hardlink меняется `DT_LNK` на `DT_UNKNOWN`, чтобы потребители выполняли lstat. Настоящие симлинки сохраняют прежнюю семантику. Обёртка компилятора и вычисление offset loader создаются скриптом сборки и доступны для проверки. Нативные ELF собираются с выравниванием 16 КиБ.

## Ядро и upstream

Архив ядра сохраняется в первоначальном формате ZIP → APK → `assets/runtime.zip`, чтобы не менять уже установленную версию `dsh-0.2.0-rc.2-node-24.9.0-v1`. Наружный APK — контейнер данных, устанавливать его вместо компактного APK не требуется.

`scripts/fetch_runtime.py` скачивает проверенный архив и извлекает ядро в `runtime/rootfs`; не запускает код из него. Это восстановление опубликованных бинарников, а не сборка всех Ubuntu-пакетов из исходников.

Для изменений ядра доступны:

- upstream Harness: https://github.com/deepseek-ai/deepseek-harness, исходный commit `d743267388641bc76f17c45ce8b4c231aed1d32c`;
- `runtime/package-lock.json`: точное дерево npm-зависимостей опубликованного ядра; устанавливайте через `npm ci` на Linux ARM64;
- Node.js 24.9.0: https://nodejs.org/dist/v24.9.0/, MIT с third-party notices;
- Ubuntu Base 24.04.3 ARM64: https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/; версии установленных пакетов и исходные source records перечислены в `ubuntu-source-inventory.json`;
- SSH-пакеты: точные URL, версии и SHA-256 в `app/src/main/assets/ssh-tools-packages.json`;
- Android-bootstrap, capabilities, mobile UI и JSON-патчи полностью включены в `app/src/main/assets`.

`scripts/pack_runtime.py` упаковывает подготовленный rootfs и генерирует манифест. Изменение самого ядра требует обновления адреса/хэша и версии установщика; версия оболочки сама по себе повторную загрузку ядра не вызывает. Сборка ядра из обновлённых системных репозиториев не гарантирует совпадение с опубликованным архивом байт в байт.

## Проверки

```sh
python3 -m compileall -q scripts tests
mkdir -p build/downloader-test
javac -d build/downloader-test app/src/main/java/org/dsh/mobile/CoreDownloader.java tests/DownloaderCheck.java
java -cp build/downloader-test org.dsh.mobile.DownloaderCheck
```

Тест downloader проверяет докачку, cookies/redirect, сервер без Range, отклонение плохого хэша и отмену. `tests/dirent_regression.py` запускается внутри PRoot с `DIRENT_TEST_ROOT` и `EXPECT_FIXED=1` и проверяет hardlink/write, rename, glob/grep и реальные симлинки. Browser/tool roundtrip тесты — интеграционные fixtures: требуют поднятого локального Harness и тестового mock-провайдера; см. сохранённые сценарии в `tests/`. Секреты и логи этих запусков не публикуются.

