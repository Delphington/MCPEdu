# MCPEdu — MCP на Java: сервер и хост

Учебный проект по протоколу **MCP (Model Context Protocol)** на Java — по материалам курса
Евгения Борисова «MCP на Java шаг за шагом» (2025). Проект демонстрирует полный цикл
взаимодействия MCP-сервера и MCP-хоста: инструменты, streamable HTTP-транспорт, sampling и
отладку через MCP Inspector.

> Проект учебный: «бизнес-логика» (случайный пульс, шутейные диагнозы) намеренно примитивна —
> смысл в механике MCP, а не в медицине 🙂

## Состав

| Модуль | Роль | Порт |
|---|---|---|
| `mc-pulsor-server/` | **MCP-сервер** — публикует инструменты | 8091 (эндпоинт `/mcpulsor`) |
| `mc-pulsor/` | **MCP-хост** — LLM-приложение на Ollama, выступает MCP-клиентом | — |

Дополнительно в `docker-compose.yml`:

- **MCP Inspector** — веб-UI для ручной отладки инструментов (порт 6274, прокси 6277)
- **Ollama** — локальный сервер моделей (порт 11434)

## Как это работает

```
Пользователь ──► mc-pulsor (хост) ──► Ollama (LLM, :11434)
                     │   ▲
                MCP  │   │  sampling: сервер просит LLM клиента
                     ▼   │  сгенерировать диагноз
              mc-pulsor-server (:8091/mcpulsor)
              инструменты: diagnostator · bioSensor · Allergator
```

1. Пользователь задаёт вопрос **хосту**.
2. Модель (Ollama) понимает, что ей нужен инструмент, и просит его вызвать.
3. Spring AI отправляет вызов по MCP с хоста **на сервер**.
4. Инструмент выполняется на сервере. Инструмент `diagnostator` дополнительно использует
   **MCP sampling** — у сервера нет своего LLM, поэтому он «разворачивает» запрос обратно:
   просит хоста сгенерировать диагноз его моделью.
5. Результат возвращается модели, и она формулирует финальный ответ пользователю.

### Инструменты сервера

| Инструмент | Что делает |
|---|---|
| `diagnostator` | «Ставит диагноз» по имени пациента: случайный пульс + зашитая медкарта + генерация диагноза через MCP sampling |
| `bioSensor` | Возвращает «показатели датчика» в виде JSON (демонстрация структурированного результата) |
| `Allergator` | Шутейная «проверка на аллергию» (демонстрация Spring AI `@Tool`) |

В проекте намеренно показаны **два способа регистрации инструментов**:
Spring AI `@Tool` с ручной регистрацией бина (`Allergator`) и MCP-аннотации `@McpTool`
с автосканером (`MCPulsorTools`).

## Быстрый старт (Docker)

```bash
docker compose up --build
```

При первом старте Ollama скачает модели (`PetrosStav/gemma3-tools:4b`,
`gemma3:4b-it-q4_K_M`) — это несколько ГБ, запаситесь временем и трафиком.

| Что | Адрес |
|---|---|
| MCP-сервер (эндпоинт) | `http://localhost:8091/mcpulsor` |
| MCP Inspector (UI) | `http://localhost:6274` |
| Ollama | `http://localhost:11434` |

### Отладка через MCP Inspector

1. Открой `http://localhost:6274`
2. **Transport Type** → `Streamable HTTP`
3. **URL** → `http://server-http:8091/mcpulsor` — имя docker-сервиса, а не `localhost`:
   к серверу подключается контейнер инспектора из docker-сети
4. **Connect** → **List Tools** → выбирай инструмент и **Run Tool**

Вызвав `diagnostator`, можно увидеть и sampling: сервер пришлёт запрос «сгенерируй диагноз»,
и в UI инспектора его можно обработать вручную, сыграв роль LLM.

## Локальный запуск без Docker

Требования: JDK 21, Maven 3.9+ (в `mc-pulsor` есть свой `mvnw`), локальная Ollama
с моделью `PetrosStav/gemma3-tools:4b`.

```bash
# 1. MCP-сервер (самодостаточен)
mvn -f mc-pulsor-server/pom.xml spring-boot:run

# 2. Хост (в отдельном терминале)
mvn -f mc-pulsor/pom.xml spring-boot:run
```

Хост сразу задаст демо-вопрос («Я Коля, чем я болен?») и напечатает ответ в консоль.

## Структура репозитория

```
mc-pulsor/            # MCP-хост: ChatClient, @McpSampling/@McpLogging, ручной протокол <tool_call>
mc-pulsor-server/     # MCP-сервер: инструменты, streamable HTTP, демо sampling
docker-compose.yml    # сервер + MCP Inspector + Ollama
pom.xml               # агрегаторный POM (модули собираются независимо)
CLAUDE.md             # шпаргалка по архитектуре для Claude Code
```

## Стек

Java 21 · Spring Boot 3.5.8 · Spring AI 1.1.2 · MCP Java SDK 0.16.0 · Ollama (gemma3) · Docker
