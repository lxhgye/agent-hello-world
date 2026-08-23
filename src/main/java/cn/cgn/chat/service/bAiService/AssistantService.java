package cn.cgn.chat.service.bAiService;

import dev.langchain4j.service.Result;

public interface AssistantService {
    String chat(String userMessage);
    Result<String> chatResString(String userMessage);
    Result<Integer> chatResInteger(String userMessage);
}
