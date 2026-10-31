# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
Этот файл содержит рекомендации для Claude Code (claude.ai/code) при работе с кодом в этом репозитории.

## Обзор проекта

Учебный демо-проект MCP (Model Context Protocol) — два независимых приложения Spring Boot, которые общаются **только по протоколу MCP** (между модулями нет общей кодовой зависимости):

- `mc-pulsor-server/` — MCP-сервер: отдаёт инструменты (tools) по streamable HTTP на порту 8091, эндпоинт `/mcpulsor`
- `mc-pulsor/` — MCP-хост: Spring AI `ChatClient` на Ollama, подключается к серверу как MCP-клиент, чтобы модель могла вызывать его инструменты

Стек: Java 21, Spring Boot 3.5.8, Spring AI 1.1.2, MCP Java SDK 0.16.0, Lombok (сервер). Комментарии, промпты, описания инструментов и демо-данные — на русском: это материал курса, а «бизнес-логика» — намеренно шуточный код, не надо «исправлять» её как баги.

## Команды

Корень репозитория = корень Maven-проекта (агрегаторный `pom.xml`, `docker-compose.yml`, контекст `Dockerfile`). Maven wrapper есть только в `mc-pulsor` (`mvnw`/`mvnw.cmd`); для корня и сервера нужен системный Maven 3.9+ и JDK 21.

```bash
# Сборка обоих модулей (из корня репозитория)
mvn package

# Запуск MCP-сервера (самодостаточен, внешних зависимостей нет)
mvn -f mc-pulsor-server/pom.xml spring-boot:run

# Запуск хоста (сначала должны быть подняты Ollama на :11434 и сервер на :8091)
mvn -f mc-pulsor/pom.xml spring-boot:run

# Тесты (есть только в mc-pulsor — один @SpringBootTest на загрузку контекста)
mvn -f mc-pulsor/pom.xml test
mvn -f mc-pulsor/pom.xml test -Dtest=McpulsorHostApplicationTests#contextLoads
```

Весь стек в Docker — собирает образ сервера, поднимает MCP Inspector и Ollama (entrypoint при первом старте сам скачивает модели `PetrosStav/gemma3-tools:4b` и `gemma3:4b-it-q4_K_M`; docker-том `./ollama` в корне уже содержит модели — ~6 ГБ, в git не попадает):

```bash
docker compose up --build
```

- Эндпоинт MCP-сервера: `http://localhost:8091/mcpulsor`
- UI Inspector: `http://localhost:6274` (прокси на 6277) — Transport: Streamable HTTP, URL: `http://server-http:8091/mcpulsor` (имя docker-сервиса: инспектор подключается из docker-сети, для него не localhost)
- Ollama: `http://localhost:11434`

Порядок локального запуска: Ollama → сервер → хост. `main()` хоста сразу задаёт демо-вопрос, поэтому при недоступных зависимостях приложение падает сразу.

## Архитектура

### Сквозной поток

Вопрос пользователя → `ChatClient` хоста (Ollama) → модель вызывает инструмент → Spring AI проксирует вызов через MCP-клиент на сервер → инструмент выполняется → результат возвращается модели → финальный ответ. Инструмент `diagnostator` дополнительно демонстрирует **MCP sampling**: сервер просит *клиента* выполнить LLM-запрос (`requestContext.sample(...)` в `MCPulsorTools`), а хост выполняет его через свой `ChatModel` — LLM-запросы ходят в обе стороны.

### mc-pulsor-server (сервер)

- Два способа регистрации инструментов сосуществуют намеренно (в курсе показаны оба):
  1. Spring AI `@Tool`/`@ToolParam` на обычном объекте (`Allergator`), регистрируется вручную через `ToolCallbacks.from(...)` в бине `List<ToolCallback>` в `McpulsorServerApplication`.
  2. Методы с аннотациями `@McpTool`/`@McpToolParam` на `@Service` (`MCPulsorTools`), обнаруживаются автоматически благодаря `spring.ai.mcp.server.annotation-scanner.enabled=true` в `application.properties`.
- `MCPulsorTools.callDiagnostator` — демо sampling; комбинирует `business/PulseCalculator` (случайный пульс) и `business/MedicalProfileProvider` (зашитые профили по именам: Вася, Коля, Лена).
- `ClientDemoMain` — автономный MCP-клиент на «сыром» SDK (без Spring) для ручной проверки запущенного сервера.
- Конфигурация сервера (`application.properties`): `protocol=streamable`, `type=sync`, эндпоинт `/mcpulsor`, порт 8091.

### mc-pulsor (хост)

- `McpulsorHostApplication` — определяет бин `ChatClient`, подключённый к автонастроенному `ToolCallbackProvider` (публикует все инструменты подключённых MCP-серверов для модели) плюс опции Ollama. Активная модель: `PetrosStav/gemma3-tools:4b` (вариант, обученный формату `<tool_call>`).
- `Host` — демо-точка входа (`printAnswerToUser`) и обработчики колбэков сервер→клиент: `@McpSampling(clients = "mcpulsor-server")` отвечает на sampling-запросы через Ollama; `@McpLogging(...)` печатает log-нотификации сервера. Имя клиента `mcpulsor-server` — это ключ подключения из `spring.ai.mcp.client.streamable-http.connections.*` в `application.properties`.
- `SystemPromptFactory` / `CallToolUtil` — ручной протокол вызова инструментов для моделей без нативного function calling: системный промпт перечисляет доступные инструменты, модель отвечает блоками `<tool_call>{...}</tool_call>`, а `CallToolUtil` парсит их в `CallToolRequest` (оборачивая результаты в `<tool_response>`).

### Особенности сборки

- Агрегаторный POM в корне перечисляет модули `mc-pulsor-server` и `mc-pulsor`. `mc-pulsor-server` из него **не** наследуется — это самостоятельный POM с импортом BOM Spring Boot и Spring AI, тогда как `mc-pulsor` использует `spring-boot-starter-parent`. Версии зависимостей управляются в каждом модуле отдельно.
- Имена каталогов (`mc-pulsor`, `mc-pulsor-server`) **не совпадают** с artifactId (`mcpulsor-host`, `mcpulsor-server`): переименовывались папки для читаемости на GitHub, артефакты (jar, docker-образ `mcpulsor-server:http`) называются по artifactId. При переименовании папок меняй `<module>` в агрегаторном POM, пути в `docker-compose.yml` и `MODULE_DIR` в Dockerfile.
- `Dockerfile` сервера требует, чтобы контекст сборки был корень репозитория (копируется всё целиком, затем сборка через `-f ${MODULE_DIR}/pom.xml`); `docker-compose.yml` уже настроен именно так.
- Git: remote `origin` → https://github.com/Delphington/MCPEdu (ветка `main`).
