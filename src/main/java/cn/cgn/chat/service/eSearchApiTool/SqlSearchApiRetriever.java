package cn.cgn.chat.service.eSearchApiTool;

import cn.cgn.chat.service.bAiService.AssistantService;
import dev.langchain4j.experimental.rag.content.retriever.sql.SqlDatabaseContentRetriever;
import dev.langchain4j.service.AiServices;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;

import static cn.cgn.chat.config.ModelConfig.SQL_LITE_PATH;
import static cn.cgn.chat.config.ModelConfig.model;

/**
 * 基于 SQLite 数据库的 SQL 检索工具。
 */
public class SqlSearchApiRetriever {


    /**
     * 获取 SQLite DataSource
     *
     * @return DataSource 对象
     */
    public static DataSource getDataSource() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(SQL_LITE_PATH);
        return dataSource;
    }

    static void main() {
        SqlDatabaseContentRetriever databaseContentRetriever = SqlDatabaseContentRetriever.builder()
                .sqlDialect("sqlite3")
                .dataSource(getDataSource()).
                chatModel(model).build();
        AssistantService assistantService = AiServices.builder(AssistantService.class)
                .chatModel(model)
                .contentRetriever(databaseContentRetriever)
                .build();

        String answer = assistantService.chat("帮我按机组统计上报至各单位成功或者失败的数量，" +
                "成功标志根据monitor_status字段判断SUCCESS代表成功FAIL代表失败");
        System.out.println(answer);
    }
}
