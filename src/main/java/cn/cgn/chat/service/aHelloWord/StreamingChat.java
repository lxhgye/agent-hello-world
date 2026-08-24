package cn.cgn.chat.service.aHelloWord;

import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import static cn.cgn.chat.config.ModelConfig.streamModel;

public class StreamingChat {

    public static void streamingChat(OpenAiStreamingChatModel model) {

        String userMessage = "什么是中广核";
        model.chat(userMessage, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                System.out.print(partialResponse);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                System.out.println("onPartialThinking: " + partialThinking);
            }

            @Override
            public void onPartialToolCall(PartialToolCall partialToolCall) {
                System.out.println("onPartialToolCall: " + partialToolCall);
            }

            @Override
            public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                System.out.println("onCompleteToolCall: " + completeToolCall);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                System.out.println("\r\nonCompleteResponse: " + completeResponse.aiMessage().text());
            }

            @Override
            public void onError(Throwable error) {
                error.printStackTrace();
            }
        });
    }

    static void main() throws InterruptedException {
        StreamingChat.streamingChat(streamModel);
        Thread.sleep(20000);
    }

}
