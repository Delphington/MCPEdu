package edu.borisov.mcpulsor;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.List;

@SpringBootApplication
public class McpulsorHostApplication {



    @Bean
    public ChatClient chatClient(ChatModel chatModel,ToolCallbackProvider toolCallbackProvider) {
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(toolCallbackProvider)
                .defaultOptions(OllamaChatOptions.builder()
                        .temperature(0.1)
                        .topK(10)
                        .topP(0.95)
                        .repeatPenalty(1.0)
                        .build())
                .build();
    }

    public static void main(String[] args) {

        Host host = SpringApplication.run(McpulsorHostApplication.class, args).getBean(Host.class);
        host.printAnswerToUser("Я Коля, чем я болен? Ответь по русски пожалуйста ");

    }

}
