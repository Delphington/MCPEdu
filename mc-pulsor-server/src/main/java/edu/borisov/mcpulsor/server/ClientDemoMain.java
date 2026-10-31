package edu.borisov.mcpulsor.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

public class ClientDemoMain {
    public static void main(String[] args) {


        ServerParameters serverParams = ServerParameters.builder("java").arg("-jar /app/app.jar").build();
        StdioClientTransport stdioTransport = new StdioClientTransport(serverParams, new JacksonMcpJsonMapper(new ObjectMapper()));

        var clientTransport = HttpClientStreamableHttpTransport
                .builder("http://localhost:8091")
                .endpoint("/mcpulsor")
                .build();
        McpSyncClient client = McpClient.sync(clientTransport).build();
        client.initialize();
        client.listTools().tools().forEach(System.out::println);
        client.callTool(McpSchema.CallToolRequest
                .builder()
                .name("Diagnostator")
                .arguments(Map.of("name", "Коля"))

                .build()).content().forEach(System.out::println);
    }
}
