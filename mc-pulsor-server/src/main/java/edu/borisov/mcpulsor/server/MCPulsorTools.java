package edu.borisov.mcpulsor.server;


import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.borisov.mcpulsor.server.business.MedicalProfileProvider.getMedicalProfile;
import static edu.borisov.mcpulsor.server.business.PulseCalculator.getPulse;

/**
 * Сервис, объявляющий MCP-инструменты сервера «нативным» для MCP способом.
 *
 * Это альтернатива стилю Allergator (Spring AI @Tool + ручная регистрация через
 * ToolCallbacks.from(...) в бине McpulsorServerApplication): здесь методы помечены
 * аннотациями @McpTool / @McpToolParam из MCP-аннотаций. Благодаря флагу
 * spring.ai.mcp.server.annotation-scanner.enabled=true в application.properties
 * Spring AI сам находит такие методы у Spring-бинов и регистрирует их как
 * MCP-инструменты — код регистрации писать не нужно.
 *
 * Ключевое преимущество этого стиля: первым параметром метода можно принять
 * McpSyncRequestContext — контекст текущего обмена с клиентом. Через него доступны
 * «обратные» (сервер -> клиент) возможности MCP: sampling (попросить LLM клиента
 * сгенерировать текст), отправка логов и уведомлений.
 */
@Service
public class MCPulsorTools {

    /**
     * Системный промпт для sampling-запроса (см. callDiagnostator): задаёт «роль врача» —
     * по медкарте и пульсу выдать ровно одно название болезни (или сказать, что пациент
     * здоров), без пояснений. Этот промпт уходит не в наш код, а КЛИЕНТУ вместе с
     * sampling-запросом — исполнять его будет LLM на стороне хоста.
     */
    private static final String SAMPLING_SYSTEM_PROMPT = """
            Ты ставишь диагноз одним словом.
             На вход всегда получаешь медицинскую карту человека и его текущий пульс.
             Твоя задача — выдать ровно одно:
            название существующей болезни (может быть 1–3 слова, можно редкие или забавно звучащие),
             или
            
            
            Ответ: -сказать что пациент здоров.
            Правила:
             — Анализируй карту пациента и пульс и выбирай подходящую болезнь.
             — Отвечай только названием болезни или фразой что пациент здоров.
             — Никаких пояснений, никакого текста вокруг.
            
            """;


    /**
     * Инструмент-«диагностатор»: по имени пациента возвращает название болезни.
     *
     * Демонстрирует сразу два приёма:
     *  1) композицию локальной бизнес-логики — пульс (PulseCalculator) + медкарта
     *     (MedicalProfileProvider);
     *  2) MCP SAMPLING — у сервера НЕТ своего LLM, поэтому диагноз он просит
     *     сгенерировать клиента (у клиента-хоста есть Ollama). Получается «обратный»
     *     LLM-запрос: не модель хоста вызывает сервер, а сервер вызывает LLM хоста.
     *
     * Параметр name подставляет сама модель хоста, когда решает вызвать инструмент.
     */
    @McpTool(name = "diagnostator", description = "Используется для получения диагноза по имени человека. Всегда возвращает либо название болезни, либо сообщение, что человек ничем не болеет.")
    public String callDiagnostator(McpSyncRequestContext requestContext,
                                   @McpToolParam(description = "Имя пациента, по которому требуется определить текущий диагноз.") String name) {


        // Шаг 1. Собираем «показатели пациента»: случайный пульс и зашитую медкарту.
        // Карта — это не данные, а инструкция для LLM: при каком пульсе какую болезнь ставить.
        int pulse = getPulse(name);
        String medicalProfile = getMedicalProfile(name);

        // Шаг 2. Собираем user-часть промпта для sampling: медкарта + текущий пульс
        String samplingPrompt = "вот такой у нас пациант, вот его медицинская карта +" + medicalProfile + " а вот его текущий пульс: " + pulse;

        // Шаг 3. Собираем sampling-запрос по схеме MCP (McpSchema.CreateMessageRequest):
        // системный промпт-«роль врача», низкая temperature (0.1) — нужен предсказуемый
        // короткий ответ, лимит maxTokens=50 — ожидаем лишь название болезни,
        // и одно user-сообщение с данными пациента.
        McpSchema.CreateMessageRequest samplingMessageRequest = McpSchema.CreateMessageRequest.builder()
                .systemPrompt(SAMPLING_SYSTEM_PROMPT)
                .temperature(0.1)
                .maxTokens(50)
                .messages(List.of(new McpSchema.SamplingMessage(McpSchema.Role.USER, new McpSchema.TextContent(samplingPrompt))))
                .build();

        // Шаг 4. SAMPLING: отправляем запрос КЛИЕНТУ через контекст обмена.
        // На стороне хоста его перехватит метод с @McpSampling (см. Host.sampling),
        // выполнит генерацию в Ollama и вернёт результат сюда. Вызов синхронный —
        // стоим здесь и ждём ответа, как при обычном RPC.
        McpSchema.CreateMessageResult samplingResult = requestContext.sample(samplingMessageRequest);


        // Шаг 5. Текст, сгенерированный LLM клиента, возвращаем как результат инструмента —
        // его получит модель хоста, вызвавшая diagnostator.
        return samplingResult.content().toString();
    }




    /**
     * Инструмент-«датчик»: возвращает «показатели» пользователя.
     *
     * Демонстрирует структурированный результат: инструмент может вернуть не только
     * String, но и Map — Spring AI сериализует её в JSON-контент MCP-ответа.
     *
     * Атрибут title аннотации @McpTool — человекочитаемое имя инструмента для UI
     * (в отличие от name — технического идентификатора, по которому инструмент вызывают).
     */
    @McpTool(name = "bioSensor", title = "biosensor title - что такое title??", description = "Returns the current heart rate of the user as a simple string value")
    public final Map<String, Object> callBioSensor(int numberOfDays) {
        return calculateResult(numberOfDays);
    }

    /**
     * «Вычисление» показателей: шутейная заглушка, значения просто зашиты.
     */
    private static Map<String, Object> calculateResult(int days) {

        // Spring AI превратит эту мапу в JSON-объект ответа инструмента
        Map<String, Object> properties = new HashMap<>();
        properties.put("pulse", " твой пульс " + 42 + days);
        properties.put("state", "тебе кабзда");
        properties.put("sleepDeprivation", true);

        return properties;
    }


}
