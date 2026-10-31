package edu.borisov.mcpulsor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.SneakyThrows;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CallToolUtil {

    private final static ObjectMapper mapper = new ObjectMapper();


    private static final Pattern TOOL_CALL_PATTERN =
            Pattern.compile("<tool_call>\\s*(\\{.*?})\\s*</tool_call>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
















    public static String wrapResponse(String toolResult) {
        String toolResponse = String.format(
                "<tool_response>%n%s%n</tool_response>",
                toolResult
        );
        return toolResponse;
    }


    public static boolean isToolRequired(String modelAnswer) {
        return TOOL_CALL_PATTERN.matcher(modelAnswer).find();
    }


    @SneakyThrows
    public static McpSchema.CallToolRequest getRequiredTool(String modelAnswer) {
        Matcher matcher = TOOL_CALL_PATTERN.matcher(modelAnswer);
        matcher.find();
        String toolCallRequestJson = matcher.group(1).trim();
        JsonNode tool = mapper.readTree(toolCallRequestJson);
        String toolName = tool.path("name").asText();
        JsonNode parameters = tool.path("parameters");


        return McpSchema.CallToolRequest.builder()
                .name(toolName)
                .arguments(mapper.convertValue(parameters, Map.class))
                .build();

    }
}









