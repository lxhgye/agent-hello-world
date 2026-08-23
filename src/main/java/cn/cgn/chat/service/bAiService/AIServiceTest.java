package cn.cgn.chat.service.bAiService;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;

import java.util.List;

import static cn.cgn.chat.config.ModelConfig.assistantService;
import static cn.cgn.chat.config.ModelConfig.model;

public class AIServiceTest {

    public static void aiServiceHelloWord( AssistantService assistantService){
        String answer = assistantService.chat("你好，智能体");
        System.out.println(answer);
    }

    static void main() {
        //最简单的 AI 服务
        AIServiceTest.aiServiceHelloWord(assistantService);

        /**
         * 现在，我们来看一个更复杂的例子强制让LLM用中文回复
         */
//        SysMesService sysMesService = AiServices.builder(SysMesService.class).chatModel(model).build();
//        System.out.println(sysMesService.chat("Tell me who you are?"));

        /**
         * AI 服务方法可以返回以下类型之一：
         *
         * String —— 在此情况下，LLM 生成的输出会原样返回，不做任何处理/解析
         *      结构化输出 支持的任意类型 —— 在此情况下， AI 服务会在返回前将 LLM 生成的输出解析为所需类型
         *      任意类型都可以额外包装进 Result<T>，以获取关于 AI 服务调用的额外元数据：
         *
         * TokenUsage —— AI 服务调用期间使用的 token 总数。如果 AI 服务对 LLM 进行了多次调用（例如因为执行了工具），它会汇总所有调用的 token 用量。
         * 来源 —— RAG 检索期间获取的 Content
         * AI 服务调用期间执行的所有工具（包括请求和结果）
         * 最终聊天响应的 FinishReason
         * 所有中间的 ChatResponse
         * 最终的 ChatResponse
         */
//        SysMesService sysMesService = AiServices.builder(SysMesService.class).chatModel(model).build();
//        Result<List<String>> result = sysMesService.chatResList("Tell me who you are?");
//        System.out.println("输出"+result.content());
//        System.out.println("token信息:"+result.tokenUsage());
//        System.out.println("finishReason:"+result.finishReason());
    }
}
