package cn.cgn.chat.service.bAiService;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;

import java.util.List;

public interface SysMesService {
    @SystemMessage("无论用户用哪种语言提问你都需要用用中文回答")
    String chat(String userMessage);


    @SystemMessage("无论用户用哪种语言提问你都需要用用中文回答")
    Result<List<String>> chatResList(String userMessage);
}
