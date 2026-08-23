package cn.cgn.chat.service.eSearchApiTool;

import cn.cgn.chat.service.bAiService.AssistantService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.experimental.rag.content.retriever.sql.SqlDatabaseContentRetriever;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;

import java.util.List;
import java.util.stream.Collectors;

import static cn.cgn.chat.config.ModelConfig.model;
import static cn.cgn.chat.service.eSearchApiTool.SqlSearchApiRetriever.getDataSource;

/**
 * 基于 SQLite 数据库的 SQL 检索工具。
 */
public class SqlSearchApiTool {

    private static final SqlDatabaseContentRetriever databaseContentRetriever = SqlDatabaseContentRetriever.builder()
            .sqlDialect("sqlite3")
            .dataSource(getDataSource()).
            chatModel(model).build();

    @Tool("""
            根据用户的自然语言问题查询业务数据库,
            该工具只用于用户明确提出需要从数据库查询数据时才使用
            """)
    public String queryDataBase(String userMessage) {
        List<Content> contents = databaseContentRetriever.retrieve(new Query(userMessage));
        return contents.stream()
                .map(Content::textSegment).map(TextSegment::text)
                .collect(Collectors.joining("\n"));
    }

    static void main() {
        AssistantService assistantService = AiServices.builder(AssistantService.class)
                .chatModel(model)
                .tools(new SqlSearchApiTool())
                .build();
//        String answer = assistantService.chat("西游记作者是谁?");
        String answer = assistantService.chat("帮我从数据库查询上报的目标单位有哪些，并给出这些单位的介绍");
        System.out.println(answer);
    }

}
