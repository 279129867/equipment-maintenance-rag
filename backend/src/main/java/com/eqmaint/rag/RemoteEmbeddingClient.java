package com.eqmaint.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 远程向量模型（默认 bge-m3，1024 维），OpenAI 兼容接口（SiliconFlow /v1/embeddings）。
 *
 * 与本地哈希向量的关键差别：维度从 512 变 1024，库里已存的向量必须全部重建
 * （POST /api/rag/reindex），否则新旧向量维度不一致，余弦会直接算成 0。
 *
 * 开启方式：rag.embedding.provider=remote + SF_API_KEY
 */
@Component
@ConditionalOnProperty(name = "rag.embedding.provider", havingValue = "remote")
public class RemoteEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteEmbeddingClient.class);

    private final OpenAiCompatibleClient client;
    private final String model;
    private volatile int dim;

    public RemoteEmbeddingClient(
            @Value("${rag.embedding.base-url:https://api.siliconflow.cn/v1}") String baseUrl,
            @Value("${rag.embedding.api-key:${SF_API_KEY:}}") String apiKey,
            @Value("${rag.embedding.model:BAAI/bge-m3}") String model,
            @Value("${rag.embedding.dim:1024}") int dim,
            @Value("${rag.embedding.timeout-ms:30000}") int timeoutMs) {
        this.client = new OpenAiCompatibleClient(baseUrl, apiKey, timeoutMs);
        this.model = model;
        this.dim = dim;
        if (!client.hasKey()) {
            log.warn("rag.embedding.provider=remote 但没有配 SF_API_KEY，向量化会在调用时失败");
        }
        log.info("向量模型：{}（{} 维，{}）", model, dim, baseUrl);
    }

    @Override
    public float[] embed(String text) {
        List<float[]> vectors = client.embed(model, List.of(text == null ? "" : text));
        if (vectors.isEmpty()) {
            return new float[0];
        }
        float[] vector = vectors.get(0);
        if (vector.length > 0 && vector.length != dim) {
            // 以实际返回维度为准，避免配置写错维度导致后续全崩
            log.warn("配置维度 {} 与实际返回 {} 不一致，以实际为准", dim, vector.length);
            dim = vector.length;
        }
        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> vectors = client.embed(model, texts);
        if (!vectors.isEmpty() && vectors.get(0).length != dim) {
            log.warn("配置维度 {} 与实际返回 {} 不一致，以实际为准", dim, vectors.get(0).length);
            dim = vectors.get(0).length;
        }
        return vectors;
    }

    @Override
    public int dim() {
        return dim;
    }

    @Override
    public String modelName() {
        return model;
    }
}
