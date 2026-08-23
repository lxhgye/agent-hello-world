package cn.cgn.chat.service.eSearchApiTool;

import cn.cgn.chat.service.bAiService.AssistantService;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

import static cn.cgn.chat.config.ModelConfig.*;

public class WebSearchApiTool {

    interface Assistant {
        @SystemMessage({
                "您是一个网络搜索支持代理。",
                "如果有任何尚未发生的事件，",
                "您必须创建一个带有用户查询的网络搜索请求，并",
                "使用网络搜索工具搜索网络上的有机网页结果。",
                "在您的最终回复中包含来源链接。"
        })
        String answer(String userMessage);
    }

    static void main() {
        //直接提问
        AssistantService assistantService = AiServices.builder(AssistantService.class)
                .chatModel(model)
                .build();
        String answer = assistantService.chat("我在深圳，我想知道这周末的天气，请帮我规划一个今年最火的出行计划");
        System.out.println(answer);

        //增加搜索引擎工具
//        Assistant assistant = AiServices.builder(Assistant.class)
//                .chatModel(model)
//                .tools(webTool,new ToolSet())
//                .build();
        //不走搜索
//        String searchAnswer = assistant.answer("西游记的作者是谁？");
//        System.out.println(searchAnswer);
        //需要查询近期事件时
//        String searchAnswer = assistant.answer("我在深圳，我想知道这周末的天气，请帮我规划一个今年最火的出行计划");
//        System.out.println(searchAnswer);


    }
}
