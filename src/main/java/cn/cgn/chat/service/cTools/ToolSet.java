package cn.cgn.chat.service.cTools;

import cn.cgn.chat.service.bAiService.AssistantService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.web.search.WebSearchTool;
import dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine;

import java.util.Map;

import static cn.cgn.chat.config.ModelConfig.SEARCH_API_KEY;
import static cn.cgn.chat.config.ModelConfig.model;

public class ToolSet {
    @Tool("输入两个数字返回两个数字的和")
    int add(int a, int b) {
        return a + b;
    }

    @Tool("输入两个数字返回两个数字的积")
    int multiply(int a, int b) {
        return a * b;
    }



    //在这种场景下，LLM 会在给出最终答案之前请求执行 add(1, 2) 和 multiply(3, 4) 方法。
    static void main() {
        AssistantService assistantService = AiServices.builder(AssistantService.class)
                .chatModel(model).tools(new ToolSet()).build();
        Result<Integer> result1 = assistantService.chatResInteger("100+1 或 200 * 2等于多少？");
        Result<String> result = assistantService.chatResString("100+1 或 200 * 2等于多少？");
        System.out.println("tools:" + result1.toolExecutions());
        System.out.println("answer:" + result1.content());

    }
}
