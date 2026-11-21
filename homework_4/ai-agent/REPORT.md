# REPORT — LangChain4j wrapper над REST API fileserver

## 1. Что реализовано

Реализовано отдельное самостоятельное Spring Boot/Gradle-приложение `homework_4/ai-agent`, которое не включается в root `settings.gradle` fileserver и не изменяет основной проект.

Агент не имеет доступа к `BookService` или другим внутренним Java-классам fileserver. Все операции с книгами выполняются через HTTP-вызовы существующего REST API.

### Поддерживаемые операции

| operation в Tool | Реальный HTTP-вызов |
|---|---|
| `getAll` | `GET /files` |
| `getByAuthor` | `GET /getByAuthor?author=<surname>` |
| `getByName` | `GET /file?name=<exact file name>` |
| `archive` | `POST /files/archive` |
| `restore` | `POST /archive/restore` |

Отдельного endpoint `/getByName` в проекте нет. Для пятой операции используется реальный endpoint `GET /file`, подтверждённый в `src/main/java/ars/home/fileserver/controller/Dashboard.java:69-73`.

---

## 2. Файлы и диапазоны строк

### Исходный fileserver — подтверждение API

- `src/main/java/ars/home/fileserver/controller/Dashboard.java:33-36` — `GET /files`.
- `src/main/java/ars/home/fileserver/controller/Dashboard.java:38-41` — `GET /getByAuthor`.
- `src/main/java/ars/home/fileserver/controller/Dashboard.java:43-48` — `POST /files/archive`.
- `src/main/java/ars/home/fileserver/controller/Dashboard.java:55-60` — `POST /archive/restore`.
- `src/main/java/ars/home/fileserver/controller/Dashboard.java:69-73` — `GET /file`.
- `src/main/java/ars/home/fileserver/service/BookService.java:255-269` — проверка имени файла, граница безопасности.

### Новый проект `ai-agent`

- `homework_4/ai-agent/settings.gradle:1` — имя standalone-проекта `ai-agent`; в root `settings.gradle` не включается.
- `homework_4/ai-agent/build.gradle:22` — BOM `dev.langchain4j:langchain4j-bom:1.24.0`.
- `homework_4/ai-agent/build.gradle:27` — `langchain4j-spring-boot4-starter:1.24.0-beta34`.
- `homework_4/ai-agent/build.gradle:28` — `langchain4j-open-ai-spring-boot4-starter:1.24.0-beta34`.
- `homework_4/ai-agent/src/main/resources/application.yaml:8-10` — модель-независимая конфигурация `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL` с fallback на `OPENAI_*`.
- `homework_4/ai-agent/src/main/resources/application.yaml:15` — `fileserver.base-url`.
- `homework_4/ai-agent/src/main/resources/application.yaml:17-19` — debug-логирование агента.
- `homework_4/ai-agent/.env.example` — шаблон переменных без реального ключа.
- `homework_4/ai-agent/src/main/java/ars/home/aiagent/AiAgentApplication.java:1-13` — точка входа Spring Boot.
- `homework_4/ai-agent/src/main/java/ars/home/aiagent/cli/AgentCli.java:8-26` — CLI, передаёт весь user input как один natural-language request.
- `homework_4/ai-agent/src/main/java/ars/home/aiagent/assistant/SystemPrompt.java:5-60` — system prompt.
- `homework_4/ai-agent/src/main/java/ars/home/aiagent/assistant/BookAgent.java:1-9` — `@AiService`-интерфейс.
- `homework_4/ai-agent/src/main/java/ars/home/aiagent/config/AgentConfig.java:18-40` — сборка AI Service, tool callbacks и возврат результата инструмента как финального ответа.
- `homework_4/ai-agent/src/main/java/ars/home/aiagent/assistant/FixedContractBookAgent.java:1-29` — гарантирует фиксированный контракт, если инструмент был вызван.
- `homework_4/ai-agent/src/main/java/ars/home/aiagent/tool/FileserverTool.java:29-132` — один `@Tool` на пять операций.
- `homework_4/ai-agent/src/main/java/ars/home/aiagent/tool/ToolResponseFormatter.java:1-41` — фиксированный контракт из четырёх строк.
- `homework_4/ai-agent/src/main/java/ars/home/aiagent/client/FileserverApiClient.java:31-131` — только HTTP-вызовы; публичный конструктор помечен `@Autowired`, чтобы Spring выбрал его при наличии тестового конструктора с `RestClient.Builder`.
- `homework_4/ai-agent/src/test/java/ars/home/aiagent/client/FileserverApiClientTest.java:1-113` — тесты HTTP-контракта.
- `homework_4/ai-agent/src/test/java/ars/home/aiagent/tool/FileserverToolTest.java:1-131` — тесты dispatch и валидации.

---

## 3. System prompt

Полный текст совпадает с `SystemPrompt.java`:

```text
Ты — оператор REST API файл-сервера электронной библиотеки.
Твоя задача — помогать пользователю работать с книгами ТОЛЬКО через доступный инструмент fileserver_api.

ПРАВИЛА

1. Поддерживаются ровно пять операций:
   - getAll — получить все книги;
   - getByAuthor — получить книги по фамилии автора;
   - getByName — получить метаданные книги по точному имени файла;
   - archive — архивировать книгу;
   - restore — восстановить книгу из архива.
   Других операций не существует.

2. Любая просьба, связанная с книгами, архивом или файлами библиотеки, должна выполняться вызовом инструмента fileserver_api.
   Никогда не отвечай от имени API и не выдумывай имена файлов, авторов, размеры, даты, счётчики или результаты операций.

3. Для задач, не относящихся к fileserver (погода, общие вопросы, написание кода и т.п.), НЕ вызывай инструмент.
   Ответь, что ты оператор fileserver API и можешь выполнить только пять перечисленных операций.

4. Параметры инструмента:
   - getAll: параметры не нужны;
   - getByAuthor: обязателен параметр author — фамилия автора. Если пользователь её не указал, задай уточняющий вопрос и не вызывай инструмент;
   - getByName: обязателен name или names — точное имя файла. Это метаданные одной книги, а не поиск по подстроке;
   - archive: обязателен name или names. Если имя файла не указано, задай уточняющий вопрос;
   - restore: обязателен name или names, причём это имя архивного файла с суффиксом .arch.

5. Запрещено придумывать неподдерживаемые операции: delete, download, search, findByName, listArchive, update и любые другие.
   Если пользователь просит удалить или скачать книгу, объясни, что агент поддерживает только пять операций выше.

6. Не обращайся напрямую к файловой системе, к BookService или к внутренним Java-классам fileserver.
   Единственный источник данных — результат вызова инструмента fileserver_api.

7. Если пользователь просит найти книгу по части названия, НЕ выдумывай endpoint поиска.
   Используй getAll и выполни фильтрацию по полям name/title в своём ответе, честно указав, что данные получены из общего списка.

8. После получения результата инструмента обязательно воспроизведи контракт ответа строго в виде четырёх строк в этом порядке:

Status: success | error
Action: <operation>
Data: <API result>
Errors: <error or null>

9. Если инструмент вернул Status: error, не меняй его на success и не скрывай ошибку.
   Если ошибка связана с неполными параметрами, попроси пользователя уточнить их.

10. Не добавляй в ответ собственные вымышленные поля и не изменяй значения, полученные от API.
```

---

## 4. Пользовательские шаблоны

| # | Пользовательский запрос | Ожидаемая_operation | Ожидаемый HTTP |
|---|---|---|---|
| 1 | `Покажи все книги` | `getAll` | `GET /files` |
| 2 | `Какие книги Толстого есть в библиотеке?` | `getByAuthor`, `author=Толстой` | `GET /getByAuthor?author=Толстой` |
| 3 | `Дай информацию о файле Ivanov_I-Book.zip` | `getByName`, `name=Ivanov_I-Book.zip` | `GET /file?name=Ivanov_I-Book.zip` |
| 4 | `Архивируй Ivanov_I-Book.zip` | `archive`, `names=[Ivanov_I-Book.zip]` | `POST /files/archive` |
| 5 | `Восстанови Ivanov_I-Book.zip.arch` | `restore`, `names=[Ivanov_I-Book.zip.arch]` | `POST /archive/restore` |
| 6 | `Какая погода в Москве?` | Tool не вызывается | нет |
| 7 | `Архивируй книгу` | Tool error или уточнение | HTTP-вызова нет |

---

# Agent_Contract

Фиксированный контракт:

```text
Status: success | error
Action: <operation>
Data: <API result>
Errors: <error or null>
```

Он зафиксирован:

1. в Java-коде — `ToolResponseFormatter.java:1-41`;
2. в system prompt — `SystemPrompt.java:44`.

Пример tool result:

```text
Status: success
Action: getAll
Data: {"files":[...],"refreshIntervalSeconds":5}
Errors: null
```

---

## 6. Логирование

### Tool call

В `AgentConfig.java:25-35` подключены callbacks:

- `LLM_TOOL_REQUEST name={} arguments={}` — аргументы, которые LLM передала в tool;
- `LLM_TOOL_RESULT name={} result={}` — результат tool.

Ожидаемый вид строк:

```text
DEBUG LLM_TOOL_REQUEST name=fileserver_api arguments={"operation":"getByAuthor","author":"Толстой"}
DEBUG LLM_TOOL_RESULT name=fileserver_api result=Status: success...
```

### HTTP call

В `FileserverApiClient.java:106-108`:

```text
DEBUG FILESERVER_CALL operation={} method={} url={} requestBody={} status={} result={}
```

### Статус доказательной части

Код логирования реализован. Фактические строки `LLM_TOOL_REQUEST`, `LLM_TOOL_RESULT` и `FILESERVER_CALL` получены в реальном прогоне через RouterAI с моделью `openai/gpt-6.2-sol`. Ключ использовался только через environment variable и не записан в файлы проекта.

---

## 7. Проведённые проверки

### 7.1. Сборка и тесты

Команды:

```bash
cd homework_4/ai-agent
./gradlew test
./gradlew bootJar

cd ../..
./gradlew test
```

Результаты:

- `./gradlew test` в `homework_4/ai-agent` — **BUILD SUCCESSFUL**, 17 tests completed, 0 failed;
- `./gradlew bootJar` — **BUILD SUCCESSFUL**, создан `build/libs/ai-agent-0.0.1-SNAPSHOT.jar`;
- `./gradlew test` в root fileserver — **BUILD SUCCESSFUL**, существующие тесты не затронуты.

### 7.2. Реальные HTTP-вызовы fileserver

Для smoke-проверки был поднят существующий fileserver:

```bash
BOOKS_DIR=/tmp/opencode/fileserver-books FILESERVER_REFRESH_INTERVAL_SECONDS=5 ./gradlew bootRun
```

Тестовые файлы:

```text
Ivanov_I-Book.zip
Tolstoy_L-N-Vojna.zip.arch
```

Фактические ответы:

| operation | Команда | HTTP status | Результат |
|---|---|---:|---|
| `getAll` | `curl http://localhost:8080/files` | 200 | `{"files":[{"name":"Ivanov_I-Book.zip",...}],"refreshIntervalSeconds":5}` |
| `getByAuthor` | `curl 'http://localhost:8080/getByAuthor?author=Ivanov'` | 200 | тот же список, отфильтрованный сервером |
| `getByName` | `curl 'http://localhost:8080/file?name=Ivanov_I-Book.zip'` | 200 | `{"name":"Ivanov_I-Book.zip","author":"Ivanov I.",...}` |
| `archive` | `curl -H 'Content-Type: application/json' -d '["Ivanov_I-Book.zip"]' http://localhost:8080/files/archive` | 200 | `{"succeeded":1,"failed":0}` |
| `restore` | `curl -H 'Content-Type: application/json' -d '["Ivanov_I-Book.zip.arch"]' http://localhost:8080/archive/restore` | 200 | `{"succeeded":1,"failed":0}` |
| error case | `curl 'http://localhost:8080/file?name=missing.zip'` | 404 | `{"status":404,"error":"Not Found",...}` |

После первой проверки fileserver был остановлен; в логе `bootRun` ожидаемо появился `exit value 143` из-за отправленного `SIGTERM`.

### 7.3. Запуск после provider-independent изменений

Изменённая конфигурация:

```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: ${LLM_BASE_URL:${OPENAI_BASE_URL:https://api.openai.com/v1}}
      api-key: ${LLM_API_KEY:${OPENAI_API_KEY}}
      model-name: ${LLM_MODEL:${OPENAI_MODEL:gpt-4o-mini}}
```

Fileserver был повторно поднят:

```bash
BOOKS_DIR=/tmp/opencode/fileserver-books FILESERVER_REFRESH_INTERVAL_SECONDS=5 ./gradlew bootRun
```

Проверка доступности:

```bash
curl http://localhost:8080/files
```

Результат: `HTTP 200`, в списке `Ivanov_I-Book.zip`.

Попытка запуска агента с OpenRouter без ключа:

```bash
LLM_BASE_URL=https://openrouter.ai/api/v1 \
LLM_MODEL='qwen/qwen3-30b-a3b:free' \
java -jar build/libs/ai-agent-0.0.1-SNAPSHOT.jar 'покажи все книги'
```

Результат — ожидаемая ошибка конфигурации:

```text
PlaceholderResolutionException: Could not resolve placeholder 'OPENAI_API_KEY' in value "${LLM_API_KEY:${OPENAI_API_KEY}}"
```

Повторный запуск с тестовым невалидным ключом:

```bash
LLM_BASE_URL=https://openrouter.ai/api/v1 \
LLM_API_KEY=invalid-key-for-config-check \
LLM_MODEL='qwen/qwen3-30b-a3b:free' \
java -jar build/libs/ai-agent-0.0.1-SNAPSHOT.jar 'покажи все книги'
```

Фактические логи подтвердили:

- `Started AiAgentApplication`;
- `HTTP request` на `POST https://openrouter.ai/api/v1/chat/completions`;
- `"model" : "qwen/qwen3-30b-a3b:free"`;
- system prompt в теле запроса;
- секция `"tools"` с функцией `fileserver_api`;
- ответ провайдера:

```text
AuthenticationException: { "success": false, "error": "Access denied by security policy." }
```

Аналогичный запуск через Groq:

```bash
LLM_BASE_URL=https://api.groq.com/openai/v1 \
LLM_API_KEY=invalid-key-for-config-check \
LLM_MODEL='qwen/qwen3-32b' \
java -jar build/libs/ai-agent-0.0.1-SNAPSHOT.jar 'покажи все книги'
```

Результат — запрос действительно уходит на `https://api.groq.com/openai/v1/chat/completions`, но провайдер возвращает `AuthenticationException: {"error":{"message":"Forbidden"}}`.

Сборка и тесты после изменений:

```bash
cd homework_4/ai-agent
./gradlew test bootJar
```

Результат: **BUILD SUCCESSFUL**, 17 тестов, 0 провалов.

### 7.4. Реальный прогон агента через RouterAI

Параметры прогона, заданные через environment variables:

```bash
LLM_BASE_URL='https://routerai.ru/api/v1'
LLM_MODEL='openai/gpt-6.2-sol'
FILESERVER_BASE_URL='http://localhost:8080'
# LLM_API_KEY задан пользователем, в отчёт и файлы проекта не попадает
```

Модель успешно вызывала tool, но её собственный финальный текст нарушал требуемый контракт. Поэтому добавлен `FixedContractBookAgent`: если инструмент был вызван, пользователю возвращается дословный `tool result`, а не произвольный финальный текст модели. System prompt дополнительно уточнён: правило отказа для посторонних задач действует только когда инструмент не вызывался.

После исправления выполнены реальные прогоны:

| Сценарий | tool arguments | HTTP-вызов | Итоговый ответ |
|---|---|---|---|
| `покажи все книги` | `{"operation":"getAll"}` | `GET /files`, 200 | `Status: success`, `Action: getAll`, данные со списком файлов, `Errors: null` |
| `покажи книги автора Ivanov` | `{"operation":"getByAuthor","author":"Ivanov"}` | `GET /getByAuthor?author=Ivanov`, 200 | `Status: success`, `Action: getByAuthor`, отфильтрованный список, `Errors: null` |
| `покажи метаданные файла Ivanov_I-Book.zip` | `{"operation":"getByName","name":"Ivanov_I-Book.zip"}` | `GET /file?name=Ivanov_I-Book.zip`, 200 | `Status: success`, `Action: getByName`, метаданные файла, `Errors: null` |
| `архивируй книгу Ivanov_I-Book.zip` | `{"operation":"archive","name":"Ivanov_I-Book.zip"}` | `POST /files/archive`, 200 | `Status: success`, `Action: archive`, `{"succeeded":1,"failed":0}`, `Errors: null` |
| `восстанови книгу Ivanov_I-Book.zip.arch` | `{"operation":"restore","name":"Ivanov_I-Book.zip.arch"}` | `POST /archive/restore`, 200 | `Status: success`, `Action: restore`, `{"succeeded":1,"failed":0}`, `Errors: null` |
| `удали книгу Ivanov_I-Book.zip` | инструмент не вызван | нет | Ответ об отсутствии поддержки удаления |

После archive/restore тестовый файл вернулся к исходному имени `Ivanov_I-Book.zip`.

Логи реального прогона содержат:

```text
LLM_TOOL_REQUEST name=fileserver_api arguments={"operation":"getAll"}
FILESERVER_CALL operation=getAll method=GET url=http://localhost:8080/files status=200
LLM_TOOL_RESULT name=fileserver_api result=Status: success...
```

Финальный stdout для `getAll`:

```text
Status: success
Action: getAll
Data: {"files":[{"name":"Ivanov_I-Book.zip","author":"Ivanov I.","title":"Book","size":0,"modified":1795268335272}],"refreshIntervalSeconds":5}
Errors: null
```

---

## 8. Неудачные попытки

1. **Первый запуск тестов API client:** mock-ожидания использовали относительные URI (`/files`, `/file?...`), а `MockRestServiceServer` в Spring 7 сравнивал абсолютный URI (`http://localhost:8080/...`). Исправлено: в тестах используются полные URI.

2. **Первый запуск тестов Tool:** текстовые блоки Java добавляли завершающий `\n`, из-за чего точные сравнения контракта падали. Исправлено через `.stripTrailing()`.

3. **Первоначальный запуск CLI без ключа до provider-independent изменений:**

   ```bash
   java -jar build/libs/ai-agent-0.0.1-SNAPSHOT.jar "покажи все книги"
   ```

   Ошибка:

   ```text
   PlaceholderResolutionException: Could not resolve placeholder 'OPENAI_API_KEY' in value "${OPENAI_API_KEY}"
   ```

   Причина: в окружении нет `OPENAI_API_KEY`. После изменений конфигурации эта проблема заменена более общей ошибкой для `LLM_API_KEY`/`OPENAI_API_KEY`.

4. **Попытка найти локальный Ollama:** `command -v ollama` ничего не вернул. Запасной провайдер в этой среде недоступен.

5. **Запуск с OpenRouter/Groq без рабочего ключа после изменений:** приложение стартует только если задан непустой `LLM_API_KEY`; с невалидным ключом запрос доходит до провайдера, но заканчивается `AuthenticationException`. Tool-calling и финальный ответ агента поэтому не получены.

6. **Первый рабочий прогон через RouterAI:** tool был вызван корректно, но модель вернула общую фразу вместо четырёх строк контракта. Исправлено добавлением `FixedContractBookAgent` и уточнением system prompt.

---

## 9. Текущее подтверждение требований задания

| Требование | Статус |
|---|---|
| Отдельное Spring Boot/Gradle-приложение | Выполнено |
| Не включается в root `settings.gradle` | Выполнено |
| Provider-independent конфигурация `LLM_*` | Выполнено |
| Только HTTP-вызовы существующего REST API | Выполнено |
| Один `@Tool` на пять операций | Выполнено |
| System prompt с ограничениями | Выполнено |
| Фиксированный контракт ответа | Выполнено |
| Debug-логирование operation/method/url/status/result | Выполнено |
| Callback-логирование tool call | Выполнено в реальном прогоне: `LLM_TOOL_REQUEST`, `LLM_TOOL_RESULT`, `FILESERVER_CALL` |
| `.env.example`, реального ключа нет | Выполнено |
| Автоматические тесты | Выполнено: 17 tests, 0 failed |
| Smoke-проверка пяти API-вызовов | Выполнено через curl на реальном fileserver |
| Проверка OpenAI-compatible запроса к OpenRouter/Groq | Выполнено с невалидным ключом: endpoint, модель, system prompt и `tools` видны в логах |
| Реальный прогон через RouterAI | Выполнено: getAll, getByAuthor, getByName, archive, restore и отказ для delete |
| Фиксированный контракт в реальном ответе | Выполнено: итоговый ответ возвращается из tool result через `FixedContractBookAgent` |

---

## 10. Как запустить

Проверенный в текущем окружении провайдер — RouterAI:

```bash
cd homework_4/ai-agent
export LLM_BASE_URL='https://routerai.ru/api/v1'
export LLM_API_KEY='<routerai-key>'
export LLM_MODEL='openai/gpt-6.2-sol'
export FILESERVER_BASE_URL='http://localhost:8080'

./gradlew bootRun --args="покажи все книги"
```

Для открытых/free-моделей рекомендуемый вариант — OpenRouter и Qwen3:

```bash
export LLM_BASE_URL='https://openrouter.ai/api/v1'
export LLM_API_KEY='<free-openrouter-key>'
export LLM_MODEL='qwen/qwen3-30b-a3b:free'
```

Или через jar:

```bash
./gradlew bootJar
java -jar build/libs/ai-agent-0.0.1-SNAPSHOT.jar "покажи все книги"
```

Альтернативные провайдеры задаются только environment variables:

```bash
# Groq
export LLM_BASE_URL='https://api.groq.com/openai/v1'
export LLM_API_KEY='<free-groq-key>'
export LLM_MODEL='qwen/qwen3-32b'

# Cerebras (trial, не постоянно бесплатный tier)
export LLM_BASE_URL='https://api.cerebras.ai/v1'
export LLM_API_KEY='<cerebras-key>'
export LLM_MODEL='qwen-3.8-27b'

# OpenAI, обратная совместимость
export LLM_BASE_URL='https://api.openai.com/v1'
export LLM_API_KEY='<openai-key>'
export LLM_MODEL='gpt-4o-mini'
```

Шаблон переменных находится в `.env.example`. Реальные ключи не следует добавлять в git. Точный free-модель ID нужно сверить с текущим каталогом провайдера: бесплатные модели и их suffixes могут меняться.

Fileserver должен быть запущен заранее, например:

```bash
BOOKS_DIR=/path/to/books ./gradlew bootRun
```

---

## 11. Состояние git

До начала реализации уже были незакоммиченные изменения в root-проекте:

```text
AM homework_4/OPENCODE_REPORT_1.md
AM homework_4/REPORT.md
 M src/main/java/ars/home/fileserver/controller/Dashboard.java
 M src/main/java/ars/home/fileserver/service/BookService.java
 M src/test/java/ars/home/fileserver/service/BookServiceTest.java
```

Эти файлы во время реализации не изменялись. Единственная новая запись:

```text
?? homework_4/ai-agent/
```

Существующий `homework_4/REPORT.md` не перезаписывался; этот отчёт создан отдельно в `homework_4/ai-agent/REPORT.md`.
