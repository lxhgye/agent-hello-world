package cn.cgn.chat.config;

import cn.cgn.chat.service.bAiService.AssistantService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.web.search.WebSearchTool;
import dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine;

import java.util.HashMap;
import java.util.Map;

/**
 * 模型基础配置
 */
public class ModelConfig {
    public static final String API_KEY = readRequiredValue("DEEPSEEK_API_KEY");

    public static final String SEARCH_API_KEY = readRequiredValue("SEARCH_API_KEY");

    public static final String URL = "https://api.deepseek.com";

    public static final String MODEL_NAME = "deepseek-v4-flash";

    public static final String RAG_FILE_PATH = "D:\\may\\doc";

    public static final String SQL_LITE_PATH = "jdbc:sqlite:D:\\SZH\\szh_data_report.sqlite3";

    public static OpenAiChatModel model = OpenAiChatModel.builder()
            .apiKey(API_KEY)
            .baseUrl(URL)
            .modelName(MODEL_NAME)
            .logRequests(true)
            .logResponses(true)
            .build();

    public static OpenAiStreamingChatModel streamModel = OpenAiStreamingChatModel.builder()
            .apiKey(API_KEY)
            .baseUrl(URL)
            .modelName(MODEL_NAME)
            .logRequests(false)
            .logResponses(false)
            .build();

    public static AssistantService assistantService = AiServices.builder(AssistantService.class)
            .chatModel(model).build();

    public static WebSearchTool webTool;

    static {
        Map<String, Object> optionalParameters = new HashMap<>();
        optionalParameters.put("gl", "us");
        optionalParameters.put("hl", "en");

        SearchApiWebSearchEngine searchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(SEARCH_API_KEY)
                .engine("google")
                .optionalParameters(optionalParameters)
                .build();
        webTool = WebSearchTool.from(searchEngine);
    }

    private static String readRequiredValue(String environmentName) {
        String value = System.getenv(environmentName);
        if (value == null || value.isBlank()) {
            value = System.getProperty(environmentName);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少模型配置，请设置环境变量或系统属性：" + environmentName);
        }
        return value;
    }
}
