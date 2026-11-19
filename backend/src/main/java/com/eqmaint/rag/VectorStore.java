package com.eqmaint.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 向量缓存：避免每次问答都回表读 BLOB 并反序列化，文档变更时整体失效 */
@Component
public class VectorStore {

    public record Entry(ChunkVector chunk, float[] vector) {
    }

    private final KmChunkMapper chunkMapper;
    private volatile List<Entry> cache;

    public VectorStore(KmChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    public List<Entry> all() {
        List<Entry> local = cache;
        if (local == null) {
            synchronized (this) {
                if (cache == null) {
                    List<Entry> entries = new ArrayList<>();
                    for (ChunkVector cv : chunkMapper.listVectors()) {
                        float[] v = VectorCodec.toVector(cv.getEmbedding());
                        if (v.length > 0) {
                            entries.add(new Entry(cv, v));
                        }
                    }
                    cache = List.copyOf(entries);
                }
                local = cache;
            }
        }
        return local;
    }

    public void invalidate() {
        cache = null;
    }
}