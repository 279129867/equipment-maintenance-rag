package com.eqmaint.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地确定性向量实现：字符 1/2/3-gram 哈希到 512 维，L2 归一化。
 * 特点：零外部依赖、离线可用、结果稳定可复现；对中文技术术语（"主轴异响""伺服报警"）有效。
 * 局限：不具备语义泛化能力，"皮带松" 和 "张紧度不足" 这类同义改写召不回来。
 * 切换：rag.embedding.provider=remote 时由 {@link RemoteEmbeddingClient}（bge-m3）接管，本类不生效。
 */
@Component
@ConditionalOnProperty(name = "rag.embedding.provider", havingValue = "hashing", matchIfMissing = true)
public class HashingEmbeddingClient implements EmbeddingClient {

    private static final int DIM = 512;

    @Override
    public int dim() {
        return DIM;
    }

    @Override
    public String modelName() {
        return "hash-char-ngram-v1";
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIM];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String norm = normalize(text);

        for (int i = 0; i < norm.length(); i++) {
            add(vector, String.valueOf(norm.charAt(i)), 0.5f);
        }
        for (int n = 2; n <= 3; n++) {
            for (int i = 0; i + n <= norm.length(); i++) {
                add(vector, norm.substring(i, i + n), 1.0f);
            }
        }
        l2Normalize(vector);
        return vector;
    }

    private String normalize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(c) || c >= 0x4E00 && c <= 0x9FA5) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void add(float[] vector, String token, float weight) {
        int h = token.hashCode();
        int idx = (h & 0x7FFFFFFF) % DIM;
        int sign = ((h >>> 31) & 1) == 0 ? 1 : -1;
        vector[idx] += sign * weight;
    }

    private void l2Normalize(float[] v) {
        double sum = 0.0;
        for (float f : v) {
            sum += f * f;
        }
        if (sum == 0.0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < v.length; i++) {
            v[i] /= norm;
        }
    }
}