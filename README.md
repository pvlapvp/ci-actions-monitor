# Gitea Actions Monitor

Плагин для IntelliJ IDEA, который показывает Gitea Actions внутри стандартного окна
**Services**: серверы, репозитории, workflows, запуски, job'ы и их логи.

## Возможности

- Интеграция в **Services** через `com.intellij.serviceViewContributor`
  (плагин не создаёт отдельный Tool Window).
- Несколько Gitea-серверов и несколько репозиториев на каждом сервере.
- Дерево `Server → Repository → Workflow → Runs → Jobs`.
- Ленивая загрузка: дочерние узлы запрашиваются только при раскрытии.
- Пагинация runs и jobs с пунктом **Load more**.
- Статусы `Queued`, `Waiting`, `In progress`, `Success`, `Failed`, `Cancelled`,
  `Skipped`, `Unknown`; учёт `status=completed` + `conclusion`.
- Просмотр лога выбранной job в панели деталей Services.
- Автообновление runs/jobs каждые 8 секунд, пока есть активные выполнения.
- Автообновление лога выбранной выполняющейся job каждые 4 секунды.
- Позиция прокрутки лога сохраняется; лог заменяется целиком, без дублирования строк.
- Токены хранятся в IntelliJ **PasswordSafe**, а не в XML настроек.

## Требования

- IntelliJ IDEA 2026.3+ (`since-build="263"`).
- JDK 25 для сборки.
- Gitea 1.27.3 (целевая версия API).
- Токен Gitea с правами `read:repository` и `read:user`.

## Сборка

```bash
mvn package
```

Готовый плагин: `target/gitea-actions-monitor.jar`.

### Как устроена Maven-сборка

JetBrains не поддерживает Maven как официальную систему сборки плагинов и не
публикует IntelliJ Platform в виде обычного набора Maven-зависимостей. Поэтому
проект использует следующий подход:

1. `maven-dependency-plugin` скачивает `com.jetbrains.intellij.idea:ideaIC:2026.3`
   (ZIP, около 600 МБ) из репозитория JetBrains.
2. ZIP распаковывается в `target/idea`.
3. `maven-antrun-plugin` компилирует исходники Ant-задачей `javac` против
   `target/idea/lib/*.jar`.
4. `maven-jar-plugin` собирает обычный plugin JAR.
5. Перед компиляцией `target/classes` очищается, чтобы удалённые классы не попадали
   в JAR при инкрементальной сборке.

Ограничения:

- первый запуск требует скачивания ZIP IntelliJ Platform;
- `mvn` должен работать на JDK 25;
- официальная поддержка Maven отсутствует; для публикации плагина JetBrains
  рекомендует Gradle;
- для code insight в IDE проект нужно открыть с IntelliJ Platform Plugin SDK
  (см. ниже).

## Запуск и отладка в IDE

1. `File | Project Structure | SDKs | + | IntelliJ Platform Plugin SDK`.
2. Укажите установленную IntelliJ IDEA 2026.3 или каталог `target/idea`
   после `mvn generate-resources`.
3. Назначьте этот SDK проекту.
4. `Run | Edit Configurations | + | Plugin` и запустите конфигурацию.

Альтернатива: установить `target/gitea-actions-monitor.jar` через
`Settings | Plugins | ⚙ | Install Plugin from Disk...`.

## Первичная настройка Gitea

1. В Gitea создайте токен: **Settings → Applications → Generate New Token**.
   Нужны scopes `read:repository` и `read:user`.
2. В IDEA откройте `Settings | Tools | Gitea Actions Monitor`
   (или `Tools | Gitea Actions Monitor Settings...`).
3. Нажмите **Add...** и заполните:
   - **Name** — произвольное имя сервера;
   - **Server URL** — например `https://gitea.example.com`
     (можно указать URL с `/api/v1`, суффикс будет отброшен);
   - **Access token** — токен;
   - **Repositories** — по одному `owner/name` на строку.
     Пустой список означает «все репозитории, доступные токену».
4. Нажмите **Apply/OK**.
5. Откройте `View | Tool Windows | Services`. Узлы Gitea появятся в дереве Services.
   Если включена группировка по типу, они будут под узлом **Gitea Actions**.

Несколько серверов добавляются тем же способом. Для каждого сервера можно задать
свой список репозиториев и таймаут.

## Работа с деревом

- Раскройте `Server → Repository → Workflow → Run`, чтобы лениво загрузить следующий уровень.
- Двойной клик по **Load more** загружает следующую страницу runs/jobs.
- Выберите **job** — справа откроется панель лога.
- Для выполняющейся job лог обновляется автоматически каждые 4 секунды.
- Для завершённой job опрос лога прекращается.
- Кнопка **Refresh** в тулбаре узла перезагружает его дочерние элементы вручную.
- Кнопка **Open in Gitea** открывает соответствующий объект в браузере.
- Кнопка **Reload Logs** перезагружает лог выбранной job.

Автообновление дерева использует инкрементальные события Services
(`SERVICE_ADDED`, `SERVICE_CHANGED`, `SERVICE_REMOVED`), чтобы не сбрасывать
раскрытые узлы и выбранную job без необходимости.

## Статусы

Gitea 1.27 отдаёт GitHub-совместимые значения:

| Gitea `status` / `conclusion` | Отображение |
|---|---|
| `queued` | Queued |
| `waiting` | Waiting |
| `in_progress`, `running`, `cancelling` | In progress |
| `completed` + `success` | Success |
| `completed` + `failure` / `timed_out` | Failed |
| `completed` + `cancelled` | Cancelled |
| `completed` + `skipped` | Skipped |
| `completed` без conclusion, неизвестное значение | Unknown |

Неизвестные значения не приводят к падению: они показываются как Unknown.

## Используемые endpoint-ы Gitea 1.27.3

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

- `mvn clean package` — успешная сборка.
- Plugin Verifier 1.410 против `IU-263.7307.200` — `Compatible`.
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

- визуальная работа диалога добавления/редактирования серверов;
- отображение дерева на реальном Gitea 1.27.3 с настоящими репозиториями и Actions;
- автообновление логов выполняющейся job в реальном UI;
- поведение 401/403/404 именно через живой сервер.

Эти пункты нужно проверить вручную с тестовым Gitea-сервером, прежде чем считать
всю функциональность проверенной.

## Ограничения текущей версии

- Нет запуска/отмены workflow и rerun job.
- Нет фильтрации runs по ветке/статусу.
- Нет отображения steps в дереве (модель `WorkflowStep` сохранена для будущего).
- Автообновление работает только для уже раскрытых workflow/run.
- Если список репозиториев не задан, загружается до 300 доступных репозиториев
  (10 страниц по 30); для больших инсталляций лучше задать список явно.
- Maven-сборка неофициальная; для публикации плагина рекомендуется Gradle.

## Структура

```
src/main/java/com/company/gitea/
├── api/
│   ├── GiteaApiClient.java
│   └── ActionsApi.java
├── model/
│   ├── GiteaServer.java
│   ├── GiteaStatus.java
│   ├── Repository.java
│   ├── Workflow.java
│   ├── WorkflowRun.java
│   ├── WorkflowJob.java
│   └── WorkflowStep.java
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
```
