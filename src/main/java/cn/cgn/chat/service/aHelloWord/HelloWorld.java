package cn.cgn.chat.service.aHelloWord;

import dev.langchain4j.model.openai.OpenAiChatModel;

import static cn.cgn.chat.config.ModelConfig.model;

public class HelloWorld {
    public static void helloWorld(OpenAiChatModel model) {
        String answer = model.chat("你好，智能体。");
        System.out.println(answer);
    }

    //简单对话
    static void main() {
        HelloWorld.helloWorld(model);
    }
}
