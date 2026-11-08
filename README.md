# Gitea / GitHub Actions Monitor

Плагин для IntelliJ IDEA, который показывает CI/CD-запуски Gitea Actions и GitHub Actions
в стандартном окне **Services**: подключения, репозитории, workflows, запуски, job'ы и их логи.

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
build/distributions/gitea-actions-monitor-1.0.0.zip
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

## Запуск и отладка в IDE

Запуск отдельного экземпляра IntelliJ IDEA с установленным плагином:

```bash
./gradlew runIde
```

Windows:

```bat
gradlew.bat runIde
```

`runIde` использует sandbox в `.intellijPlatform/sandbox/`; лог IDE находится в
`.intellijPlatform/sandbox/gitea-actions-monitor/IU-2026.3/log_runIde/idea.log`.

Проверки плагина:

```bash
./gradlew verifyPluginStructure
./gradlew verifyPlugin
```

`verifyPlugin` проверяет совместимость с IntelliJ IDEA 2026.3 через Plugin Verifier.

## Первичная настройка

1. В Gitea создайте токен: **Settings → Applications → Generate New Token**.
   Нужны scopes `read:repository` и `read:user`.
   Для GitHub создайте classic PAT или fine-grained PAT с permissions
   **Actions: Read-only**, **Metadata: Read-only**, **Contents: No access**.
2. В IDEA откройте `Settings | Tools | Gitea Actions Monitor`
   (или `Tools | Gitea Actions Monitor Settings...`).
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
4. Нажмите **Apply/OK**.
5. Откройте `View | Tool Windows | Services`. Узлы подключений появятся в дереве Services
   под общим узлом **CI Actions**.

Несколько Gitea- и GitHub-подключений добавляются тем же способом. Для каждого можно
задать свой список репозиториев и таймаут.

## Работа с деревом

- Раскройте `Server → Repository → Workflow → Run`, чтобы лениво загрузить следующий уровень.
- Двойной клик по **Load more** загружает следующую страницу runs/jobs.
- Выберите **job** — справа откроется панель лога.
- Для выполняющейся job лог обновляется автоматически каждые 4 секунды.
- Для завершённой job опрос лога прекращается.
- Кнопка **Refresh** в тулбаре узла перезагружает его дочерние элементы вручную.
- Кнопка **Open in Browser** открывает соответствующий объект в браузере.
- Кнопка **Reload Logs** перезагружает лог выбранной job.

Автообновление дерева использует инкрементальные события Services
(`SERVICE_ADDED`, `SERVICE_CHANGED`, `SERVICE_REMOVED`), чтобы не сбрасывать
раскрытые узлы и выбранную job без необходимости.

## Статусы

Gitea и GitHub отдают GitHub-совместимые значения:

| Gitea / GitHub `status` / `conclusion` | Отображение |
|---|---|
| `queued` | Queued |
| `waiting` | Waiting |
| `in_progress`, `running`, `cancelling` | In progress |
| `completed` + `success` | Success |
| `completed` + `failure` / `timed_out` / `startup_failure` | Failed |
| `completed` + `cancelled` | Cancelled |
| `completed` + `skipped` | Skipped |
| `completed` + `neutral` | Neutral |
| `completed` + `action_required` | Action required |
| `completed` + `stale` | Stale |
| `completed` без conclusion, неизвестное значение | Unknown |

Неизвестные значения не приводят к падению: они показываются как Unknown.
`action_required` не считается `Waiting`: завершённый run/job отображается как
**Action required**. `neutral` не считается успехом.

## Используемые endpoint-ы

### Gitea 1.27.3

- `GET /version`
- `GET /user/repos?page=&limit=` — если список репозиториев не задан явно
- `GET /repos/{owner}/{repo}`
- `GET /repos/{owner}/{repo}/actions/workflows`
- `GET /repos/{owner}/{repo}/actions/workflows/{workflow_id}/runs?page=&limit=`
- `GET /repos/{owner}/{repo}/actions/runs/{run}`
- `GET /repos/{owner}/{repo}/actions/runs/{run}/jobs?page=&limit=`
- `GET /repos/{owner}/{repo}/actions/jobs/{job_id}`
- `GET /repos/{owner}/{repo}/actions/jobs/{job_id}/logs`

Endpoint логов возвращает **`text/plain; charset=utf-8`**, а не JSON.
HTTP 404 для логов означает, что job ещё не стартовала, лог очищен или job была
пропущена. HTTP 401/403 показываются как ошибка авторизации без вывода токена.

### GitHub.com / GitHub Enterprise Server

- `GET /rate_limit` (GitHub.com) или `GET /meta` (GHES)
- `GET /user/repos?per_page=&page=` — если список репозиториев не задан явно
- `GET /repos/{owner}/{repo}`
- `GET /repos/{owner}/{repo}/actions/workflows?per_page=&page=`
- `GET /repos/{owner}/{repo}/actions/workflows/{workflow_id}/runs?per_page=&page=`
- `GET /repos/{owner}/{repo}/actions/runs/{run}`
- `GET /repos/{owner}/{repo}/actions/runs/{run}/jobs?filter=latest&per_page=&page=`
- `GET /repos/{owner}/{repo}/actions/jobs/{job_id}`
- `GET /repos/{owner}/{repo}/actions/jobs/{job_id}/logs`

Лог job в GitHub отдаётся через `302` на подписанный URL. Плагин следует редиректу
вручную, не передаёт `Authorization` на сторонний хост, не пишет подписанный URL
в логи и ограничивает размер загружаемого лога 10 MB.

## Безопасность токенов

- Токен не хранится в `PersistentStateComponent`.
- Используется `com.intellij.ide.passwordSafe.PasswordSafe`.
- Credential привязан к стабильному `server.id` (UUID), а не к URL.
- При изменении URL токен сохраняется; при удалении сервера credential удаляется.
- Чтение/запись PasswordSafe выполняется в фоне, не на EDT.
- Токен не попадает в сообщения об ошибках и логи.
- Миграция старого формата: если в XML был `token`, он сразу удаляется из состояния
  и асинхронно переносится в PasswordSafe. В обычном XML токена больше нет.

## Проверено и не проверено

**Проверено:**

- `./gradlew clean` — успешно.
- `./gradlew build` — успешно.
- `./gradlew buildPlugin` — успешно; ZIP создан в `build/distributions/`.
- `./gradlew verifyPluginStructure` — успешно.
- `./gradlew verifyPlugin` — `Compatible` против `IU-263.7307.200`.
- `./gradlew runIde` — отдельный экземпляр IntelliJ IDEA запускается,
  в `idea.log` плагин загружается как `Gitea Actions Monitor (1.0.0)`.
- Java bytecode: major version 69 (Java 25).
- Разбор реальных схем Gitea 1.27.3 (`Workflow`, `WorkflowRun`, `WorkflowJob`,
  `WorkflowStep`) на тестовых JSON.
- Маппинг статусов, включая `completed` без conclusion и неизвестные значения.
- HTTP-клиент на локальном mock-сервере: заголовок `Authorization`, пагинация,
  JSON, `text/plain` логи, 401/403/404, отсутствие токена в тексте ошибок.
- Изолированный запуск IntelliJ IDEA 2026.3:
  - плагин загружается без ошибок;
  - `serviceViewContributor` зарегистрирован;
  - `ServiceViewManager.getToolWindowId(...)` возвращает `Services`;
  - окно Services создаётся, содержит Gitea contributor;
  - раскрытие узла сервера через `ServiceViewManager.expand(...)` завершается успешно;
  - PasswordSafe: запись, чтение и удаление токена работают;
  - миграция старого XML: сервер создаётся, токен переносится в PasswordSafe,
    `State.token` пуст, в итоговом `gitea-actions-monitor.xml` токена нет.
- End-to-end в запущенной IDEA с mock-сервером, совместимым с Gitea 1.27.3:
  - цепочка `Server → Repository → Workflow → Run → Job` загружается через реальные
    node-классы плагина;
  - статусы run/job (`success`/`failure`) определяются корректно;
  - лог job загружается из `text/plain` endpoint и попадает в `LogsPanel`;
  - Services tool window открывается, `expand` сервера завершается успешно.

**Пока не проверено end-to-end:**

- визуальная работа диалога добавления/редактирования подключений;
- отображение дерева на реальном Gitea 1.27.3 с настоящими репозиториями и Actions;
- автообновление логов выполняющейся job в реальном UI;
- поведение 401/403/404 именно через живой сервер;
- GitHub.com и GHES на реальном сервере: пагинация, rate limit, redirect логов,
  права fine-grained PAT.

Эти пункты нужно проверить вручную с тестовыми Gitea- и GitHub-серверами, прежде
чем считать всю функциональность проверенной.

## Ограничения текущей версии

- Нет запуска/отмены workflow и rerun job.
- Нет фильтрации runs по ветке/статусу.
- Нет отображения steps в дереве (модель `WorkflowStep` сохранена для будущего).
- Автообновление работает только для уже раскрытых workflow/run.
- GitHub.com и GitHub Enterprise Server поддерживаются в read-only режиме.
- GitHub Enterprise Cloud (GHE.com) пока не поддерживается; архитектура resolver-а
  готова к добавлению отдельной ветки.
- ETag и условные запросы (`If-None-Match`/`304`) отложены до появления модели
  HTTP-ответа.
- Если список репозиториев не задан, загружается до 300 доступных репозиториев
  (10 страниц по 30); для больших инсталляций лучше задать список явно.
- `runIde` использует IntelliJ IDEA Ultimate 2026.3, поэтому для интерактивной
  работы может потребоваться соответствующая лицензия; сборка и `verifyPlugin`
  от лицензии не зависят.

## Структура

```
src/main/java/com/company/gitea/
├── api/
│   ├── GiteaApiClient.java
│   ├── GiteaApiException.java
│   └── ActionsApi.java
├── model/
│   ├── ActionsConnection.java
│   ├── ProviderType.java
│   ├── GiteaStatus.java
│   ├── Repository.java
│   ├── Workflow.java
│   ├── WorkflowRun.java
│   ├── WorkflowJob.java
│   └── WorkflowStep.java
├── provider/
│   ├── ActionsProvider.java
│   ├── ActionsProviderFactory.java
│   ├── ProviderRegistry.java
│   ├── ActionsApiException.java
│   ├── Page.java
│   ├── ProviderCapabilities.java
│   ├── ConnectionInfo.java
│   ├── JobLogStream.java
│   ├── InputStreamJobLogStream.java
│   ├── gitea/
│   │   ├── GiteaActionsProvider.java
│   │   ├── GiteaProviderFactory.java
│   │   ├── GiteaDtoMapper.java
│   │   └── dto/
│   └── github/
│       ├── GitHubActionsProvider.java
│       ├── GitHubProviderFactory.java
│       ├── GitHubApiClient.java
│       ├── GitHubActionsApi.java
│       ├── GitHubApiException.java
│       ├── GitHubEndpoint.java
│       ├── GitHubEndpointResolver.java
│       ├── GitHubDeployment.java
│       ├── GitHubDtoMapper.java
│       └── dto/
├── service/
│   ├── GiteaConnectionService.java
│   ├── GiteaCredentialService.java
│   ├── GiteaAutoRefreshService.java
│   ├── GiteaTreeStateService.java
│   ├── ActionsMonitorService.java
│   └── LogsService.java
├── ui/
│   ├── GiteaServiceViewContributor.java
│   ├── GiteaServiceNode.java
│   ├── GiteaServerNode.java
│   ├── GiteaRepositoryNode.java
│   ├── GiteaWorkflowNode.java
│   ├── GiteaRunNode.java
│   ├── GiteaJobNode.java
│   ├── GiteaErrorNode.java
│   ├── GiteaMessageNode.java
│   ├── GiteaLoadMoreNode.java
│   ├── GiteaNodeDescriptor.java
│   ├── GiteaServiceEvents.java
│   ├── GiteaActions.java
│   ├── GiteaUi.java
│   └── LogsPanel.java
└── settings/
    ├── GiteaSettings.java
    ├── GiteaSettingsConfigurable.java
    ├── GiteaServerDialog.java
    └── GiteaOpenSettingsAction.java

src/main/resources/META-INF/plugin.xml

build.gradle.kts
settings.gradle.kts
gradle.properties
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
```
