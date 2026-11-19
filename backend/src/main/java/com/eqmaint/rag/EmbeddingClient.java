package com.eqmaint.rag;

public interface EmbeddingClient {

    float[] embed(String text);

    /** 批量向量化，顺序与入参一致；远程模型覆盖此方法以减少 HTTP 往返 */
    default java.util.List<float[]> embedBatch(java.util.List<String> texts) {
        java.util.List<float[]> vectors = new java.util.ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }

    /** 向量维度；本地哈希 512，bge-m3 1024。两者不可混用，换模型必须重建库内向量 */
    int dim();

    String modelName();
}