package edu.borisov.mcpulsor.server;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class Allergator {


    @Tool(name = "Allergator - (Tool name)",description = "отвечает есть ли на сабдект аллергия (tool description)")
    public String detectAllergy(@ToolParam(description = "allergator param description - то на что надо проверить нету ли аллергии") String subject){
        return "о!! конечно - ты еще их нюхай больше - от таких "+subject+" даже у маленьких пчелок будет аллергия";
    }
}
