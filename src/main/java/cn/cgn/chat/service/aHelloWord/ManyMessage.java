package cn.cgn.chat.service.aHelloWord;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;

import static cn.cgn.chat.config.ModelConfig.model;

public class ManyMessage {

    public static void manyMessage(OpenAiChatModel model) {
        UserMessage firstUserMessage = UserMessage.from("你好，我正在龙岗天安数码城进行智能体开发演示。");
        AiMessage firstAIMessage = model.chat(firstUserMessage).aiMessage();
        System.out.println(firstAIMessage.text());
        UserMessage secondUserMassage = UserMessage.from("我在哪里？");
        AiMessage secondAIMessage = model
                .chat(firstUserMessage, firstAIMessage, secondUserMassage).aiMessage();
        System.out.println(secondAIMessage.text());
    }

    static void main() {
        //带上下文的对话
        ManyMessage.manyMessage(model);
    }
}
