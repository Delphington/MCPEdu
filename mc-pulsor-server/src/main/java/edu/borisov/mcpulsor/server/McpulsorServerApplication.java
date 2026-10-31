package edu.borisov.mcpulsor.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.borisov.mcpulsor.server.business.MedicalProfileProvider;
import edu.borisov.mcpulsor.server.business.PulseCalculator;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.SneakyThrows;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.borisov.mcpulsor.server.business.MedicalProfileProvider.getMedicalProfile;
import static edu.borisov.mcpulsor.server.business.PulseCalculator.getPulse;

@SpringBootApplication
public class McpulsorServerApplication {

    @Bean
    public List<ToolCallback> toolCallbacks() {
        return List.of(ToolCallbacks.from(new Allergator()));
    }


    @SneakyThrows
    public static void main(String[] args) {
        System.out.println("Server Application Started New LIFE");

        SpringApplication.run(McpulsorServerApplication.class, args);


    }


}





