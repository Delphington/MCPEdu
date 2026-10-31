package edu.borisov.mcpulsor;


import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import org.springaicommunity.mcp.annotation.McpLogging;
import org.springaicommunity.mcp.annotation.McpSampling;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Главный сервис хоста — приложения, которое ВЛАДЕЕТ LLM (Ollama) и подключено
 * к mcpulsor-server как MCP-клиент.
 *
 * Две зоны ответственности:
 *  1) Общение с пользователем (printAnswerToUser): вопрос уходит в chatClient.
 *     ChatClient собран в McpulsorHostApplication и уже подключён к инструментам
 *     MCP-сервера через defaultToolCallbacks(ToolCallbackProvider) — модель сама
 *     решает, какой инструмент вызвать, Spring AI маршрутизирует вызов по MCP.
 *  2) Обработка «обратных» запросов сервера (сервер -> клиент):
 *     @McpSampling — сервер просит выполнить LLM-completion (см. diagnostator);
 *     @McpLogging  — сервер присылает свои логи.
 *
 * clients = "mcpulsor-server" в аннотациях — имя подключения из application.properties
 * (spring.ai.mcp.client.streamable-http.connections.mcpulsor-server.*):
 * обработчики срабатывают только для этого соединения.
 */
@Service
public class Host {

    /**
     * Низкоуровневый доступ к модели Ollama (без обвязки ChatClient).
     * Нужен для sampling-запросов сервера: под них мы собираем отдельный ChatClient
     * с параметрами из конкретного запроса (см. sampling).
     */
    @Autowired
    private ChatModel chatModel;

    /**
     * Основной ChatClient приложения (создан в McpulsorHostApplication):
     * к нему подключены все инструменты MCP-сервера и опции Ollama.
     * Используется для ответов на вопросы пользователя.
     */
    @Autowired
    private ChatClient chatClient;



    /**
     * Обработчик MCP-логов: сервер может в любой момент прислать клиенту
     * logging-нотификацию — Spring AI перехватывает её и вызывает метод с @McpLogging.
     * Здесь просто печатаем присланные данные, чтобы показать этот канал связи.
     */
    @McpLogging(clients = "mcpulsor-server")
    public void logClient(McpSchema.LoggingMessageNotification loggingMessageNotification) {
        System.out.println("\nКлиент говорит: я получил послание от сервера - " + loggingMessageNotification.data());
    }

    /**
     * Обработчик MCP SAMPLING — «исполнитель чужой просьбы о генерации».
     *
     * Когда инструмент diagnostator на сервере вызывает requestContext.sample(...),
     * запрос прилетает сюда. У сервера нет своего LLM — генерировать текст должен
     * владелец модели, то есть мы. Отвечаем через Ollama, перенося параметры
     * из MCP-запроса в параметры модели.
     */
    @McpSampling(clients = "mcpulsor-server")
    public McpSchema.CreateMessageResult sampling(McpSchema.CreateMessageRequest createMessageRequest) {
        // Собираем ОТДЕЛЬНЫЙ ChatClient (не общий chatClient!): у sampling-запроса свои
        // параметры генерации. Переносим их из MCP-запроса в опции Ollama:
        // maxTokens -> numPredict (лимит токенов ответа), temperature -> temperature.
        ChatClient samplingChatClient = ChatClient.builder(chatModel)
                .defaultOptions(OllamaChatOptions.builder()
                        .numPredict(createMessageRequest.maxTokens())
                        .temperature(createMessageRequest.temperature()).build())
                .build();
        // Отправляем в модель системный промпт из запроса и первое user-сообщение
        // (там медкарта и пульс пациента), забираем текст ответа.
        String samplingAnswer = samplingChatClient
                .prompt()
                .system(createMessageRequest.systemPrompt())
                .user(createMessageRequest.messages().getFirst().content().toString())
                .call()
                .content();
        // Упаковываем ответ в формат MCP (CreateMessageResult) и возвращаем серверу —
        // его ждёт requestContext.sample() внутри callDiagnostator.
        return McpSchema.CreateMessageResult.builder().content(new McpSchema.TextContent(samplingAnswer)).build();
    }


    /**
     * Демо-сценарий «вопрос пользователя -> ответ модели».
     *
     * Внутри одного .call() Spring AI при необходимости сам прогоняет цикл tool calling:
     * модель видит инструменты сервера (diagnostator, bioSensor, Allergator), решает
     * вызвать нужный, Spring AI отправляет вызов по MCP на сервер, получает результат
     * и отдаёт его модели для финального ответа. Со стороны кода — обычный запрос.
     */
    public void printAnswerToUser(String question) {

        System.out.println("Хост говорит: пользователь задал вот  такой вопрос: " + question);

        // Один вызов ChatClient: внутри может скрыться целый цикл
        // «модель -> вызов инструмента по MCP -> результат -> модель»
        AssistantMessage assistantMessage = chatClient.prompt().user(question).call().chatResponse().getResult().getOutput();

        // Финальный ответ модели (уже с учётом результатов инструментов, если они вызывались)
        System.out.println(assistantMessage.getText());


    }
}
