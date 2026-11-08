# CI Actions Monitor — плагин для IntelliJ IDEA

Показывает CI/CD-запуски Gitea Actions и GitHub Actions в стандартном окне **Services**:
подключения, репозитории, workflows, запуски, job'ы и их логи.

## Возможности

- Интеграция в **Services** через `com.intellij.serviceViewContributor`
  (плагин не создаёт отдельный Tool Window).
- Несколько подключений Gitea и GitHub; несколько репозиториев на каждом подключении.
- Единое дерево `Connection → Repository → Workflow → Runs → Jobs`.
- Gitea и GitHub отображаются одинаково; провайдер виден по иконке подключения.
- Ленивая загрузка: дочерние узлы запрашиваются только при раскрытии.
- Пагинация runs и jobs с пунктом **Load more**.
- Статусы `Queued`, `Waiting`, `In progress`, `Success`, `Failed`, `Cancelled`,
  `Skipped`, `Neutral`, `Action required`, `Stale`, `Unknown`.
- Просмотр лога выбранной job в панели деталей Services.
- Потоковая загрузка логов с отменой и ограничением 10 MB.
- Автообновление runs/jobs каждые 8 секунд, пока есть активные выполнения.
- Автообновление лога выбранной выполняющейся job каждые 4 секунды.
- Позиция прокрутки лога сохраняется; лог заменяется целиком, без дублирования строк.
- Кнопка **Test Connection** в диалоге подключения: проверка в фоне, версия сервера и имя
  аккаунта или понятная ошибка.
- Токены хранятся в IntelliJ **PasswordSafe**, а не в XML настроек.

## Требования

- IntelliJ IDEA 2026.3+ (`since-build="263"`).
- JDK 25 для сборки.
- Gitea 1.27.3 (целевая версия API) или GitHub.com / GitHub Enterprise Server.
- Токен Gitea с правами `read:repository` и `read:user`.
- GitHub token: classic `repo` / `public_repo` или fine-grained PAT
  с **Actions: Read-only** и **Metadata: Read-only**.
  **Contents: No access** — плагин не читает исходный код.

## Сборка

Проект собирается Gradle Wrapper'ом; устанавливать Gradle вручную не нужно.
Windows:

```bat
gradlew.bat clean
gradlew.bat build
gradlew.bat buildPlugin
```

Linux/macOS:

```bash
./gradlew clean
./gradlew build
./gradlew buildPlugin
```

Готовый ZIP плагина:

```text
build/distributions/ci-actions-monitor-1.1.0.zip
```

Установка ZIP: `Settings | Plugins | ⚙ | Install Plugin from Disk...`.

### Версии сборки

- Gradle Wrapper: **9.8.1** (зафиксирован в `gradle/wrapper/gradle-wrapper.properties`).
- IntelliJ Platform Gradle Plugin: **2.20.0**.
- Целевая платформа: **IntelliJ IDEA 2026.3** (`intellijIdea("2026.3")`).
- Java Toolchain: **25**.
- Java bytecode: **25** (class file major version 69), совместим с JBR 25,
  на котором работает IntelliJ IDEA 2026.3.

Gradle-сборка сама скачивает IntelliJ Platform, компилирует Java, обрабатывает
ресурсы, патчит `plugin.xml` и упаковывает плагин. Ручная распаковка IDEA ZIP
и Ant javac больше не используются.

## Первичная настройка

1. В Gitea создайте токен: **Settings → Applications → Generate New Token**.
   Нужны scopes `read:repository` и `read:user`.
   Для GitHub создайте classic PAT или fine-grained PAT с permissions
   **Actions: Read-only**, **Metadata: Read-only**, **Contents: No access**.
2. В IDEA откройте `Settings | Tools | CI Actions Monitor`
   (или `Tools | CI Actions Monitor Settings...`).
3. Нажмите **Add...** и заполните:
   - **Provider** — `Gitea` или `GitHub`;
   - **Name** — произвольное имя подключения;
   - **Server URL** — например `https://gitea.example.com`,
     `https://github.com` или `https://ghe.example.com`
     (для Gitea можно указать URL с `/api/v1`, суффикс будет отброшен);
   - **API URL** — необязательно; пустое значение означает автоматическое вычисление
     (`/api/v1` для Gitea, `api.github.com` или `/api/v3` для GitHub);
   - **Access token** — токен;
   - **Repositories** — по одному `owner/name` на строку.
     Пустой список означает «все репозитории, доступные токену».
4. При необходимости нажмите **Test Connection**: проверка выполняется в фоне и
   показывает версию сервера и имя аккаунта либо текст ошибки.
5. Нажмите **Apply/OK**.
6. Откройте `View | Tool Windows | Services`. Узлы подключений появятся в дереве Services
   под общим узлом **CI Actions**.

Несколько Gitea- и GitHub-подключений добавляются тем же способом. Для каждого можно
задать свой список репозиториев и таймаут.
