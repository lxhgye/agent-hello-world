package cn.cgn.chat.service.dRAG;

import cn.cgn.chat.service.bAiService.AssistantService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;

import static cn.cgn.chat.config.ModelConfig.RAG_FILE_PATH;
import static cn.cgn.chat.config.ModelConfig.model;

public class ARAG {
    /**
     * 什么是 RAG？ RAG（Retrieval-Augmented Generation，检索增强生成）
     * 简单来说，RAG 是在将提示发送给 LLM 之前，从你的数据中查找并注入相关信息片段的方法。 这样 LLM 将获得（希望是）相关的信息，
     * 并能够利用这些信息进行回复， 从而降低产生幻觉的概率。
     * <p>
     * 相关信息片段可以通过各种 信息检索 方法找到。 最流行的有：
     * <p>
     * 全文（关键词）搜索。通过将查询（例如，用户提出的问题）中的关键词与文档数据库进行匹配来搜索文档。
     * 它根据每个文档中这些关键词的频率和相关性对结果进行排序。
     * 向量搜索，也称为“语义搜索”。 使用嵌入模型将文本文档转换为数字向量。
     * 然后根据查询向量与文档向量之间的余弦相似度 或其他相似度/距离度量来查找和排序文档， 从而捕捉更深层的语义含义。
     * 混合搜索。结合多种搜索方法（例如，全文 + 向量）通常可以提高搜索的有效性。
     */


    //文档加载
    public static List<Document> LoadDocument() {
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(RAG_FILE_PATH);
        return documents;
    }

    static void main() {
        //加载文档
        List<Document> documents = LoadDocument();
        //演示处理结果
        documents.forEach(document -> {
            if ("放射性废物安全监督管理规定.html".equals(document.metadata().getString("file_name"))) {
                System.out.println(document.text());
            }
        });
        /**
         * 现在，我们需要对文档进行预处理，向量数据库 中。 这是为了在用户提问时能够快速找到相关的信息片段。
         * 但为了简单起见，我们将使用内存中的一种：
         */
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
//        //将文档分片转向量并存储向量库中
        /************默认向量模型***************/
        EmbeddingStoreIngestor.ingest(documents, embeddingStore);
//        将文档接入到LLM对话中
        AssistantService assistantService = AiServices.builder(AssistantService.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore))
                .build();


        /************自定义向量模型***************/
//        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
//                .baseUrl("http://192.168.2.6:9997/v1")
//                .modelName("bge-m3")
//                .build();
//        EmbeddingStoreContentRetriever contentRetriever =
//                EmbeddingStoreContentRetriever.builder()
//                        .embeddingModel(embeddingModel)
//                        .embeddingStore(embeddingStore)
//                        .build();

//        AssistantService assistantService = AiServices.builder(AssistantService.class)
//                .chatModel(model)
//                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
//                .contentRetriever(contentRetriever)
//                .build();






        //对话对比
        AssistantService assistantService1 = AiServices.builder(AssistantService.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10)).build();
//        System.out.println("未引入知识库结果："+assistantService1.chat("什么是放射性废物（radioactive waste）?"));
//        System.out.println("=====================================");
//        System.out.println("引入知识库结果："+assistantService.chat("什么是放射性废物（radioactive waste）?"));


    }
}
