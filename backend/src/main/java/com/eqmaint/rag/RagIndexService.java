package com.eqmaint.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eqmaint.common.BizException;
import com.eqmaint.workorder.OrderMapper;
import com.eqmaint.workorder.WoOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagIndexService {

    private final KmDocumentMapper docMapper;
    private final KmChunkMapper chunkMapper;
    private final EmbeddingClient embedder;
    private final DocumentParser parser;
    private final OrderEmbeddingMapper orderVecMapper;
    private final OrderMapper orderMapper;
    private final VectorStore vectorStore;

    @Value("${rag.storage-dir:data/uploads}")
    private String storageDir;

    @Value("${rag.chunk-size:500}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:60}")
    private int chunkOverlap;

    public RagIndexService(KmDocumentMapper docMapper, KmChunkMapper chunkMapper,
                           EmbeddingClient embedder, DocumentParser parser,
                           OrderEmbeddingMapper orderVecMapper, OrderMapper orderMapper,
                           VectorStore vectorStore) {
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
        this.embedder = embedder;
        this.parser = parser;
        this.orderVecMapper = orderVecMapper;
        this.orderMapper = orderMapper;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public KmDocument ingest(MultipartFile file, Integer docType, String assetType, String uploader) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件为空");
        }
        try {
            byte[] bytes = file.getBytes();
            String hash = sha256(bytes);

            KmDocument exists = docMapper.selectOne(new LambdaQueryWrapper<KmDocument>()
                    .eq(KmDocument::getFileHash, hash).last("limit 1"));
            if (exists != null) {
                exists.setParseMsg(exists.getParseMsg() + "（重复上传，已复用）");
                return exists;
            }

            Path dir = Paths.get(storageDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String origin = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "document.txt";
            String ext = origin.contains(".") ? origin.substring(origin.lastIndexOf('.')).toLowerCase() : "";
            Path saved = dir.resolve(hash + ext);
            Files.write(saved, bytes);

            KmDocument doc = new KmDocument();
            doc.setDocName(origin);
            doc.setDocType(docType == null ? 1 : docType);
            doc.setAssetType(assetType == null ? "" : assetType);
            doc.setFileUrl("/files/" + saved.getFileName());
            doc.setFileHash(hash);
            doc.setParseStatus(0);
            doc.setChunkCount(0);
            doc.setVersion(1);
            doc.setUploader(uploader == null ? "" : uploader);
            docMapper.insert(doc);

            try {
                List<TextChunker.PageText> pages = parser.parse(saved);
                String all = pages.stream().map(TextChunker.PageText::text).collect(Collectors.joining(""));
                if (all.replaceAll("\\s", "").isEmpty()) {
                    return markFailed(doc, "未提取到文本，可能是扫描件或纯图片 PDF，需 OCR");
                }
                List<TextChunker.Chunk> chunks = TextChunker.split(pages, chunkSize, chunkOverlap);
                if (chunks.isEmpty()) {
                    return markFailed(doc, "文本过短，未生成有效分块");
                }
                for (TextChunker.Chunk chunk : chunks) {
                    KmChunk entity = new KmChunk();
                    entity.setDocId(doc.getId());
                    entity.setChunkNo(chunk.chunkNo());
                    entity.setSectionPath(chunk.sectionPath());
                    entity.setPageNo(chunk.pageNo());
                    entity.setContent(chunk.content());
                    entity.setTokenCount(chunk.content().length());
                    float[] vector = embedder.embed(chunk.content());
                    entity.setEmbedding(VectorCodec.toBytes(vector));
                    entity.setEmbeddingModel(embedder.modelName());
                    entity.setDim(vector.length);
                    chunkMapper.insert(entity);
                }
                doc.setChunkCount(chunks.size());
                doc.setParseStatus(2);
                doc.setParseMsg("解析成功，共 " + chunks.size() + " 块");
                docMapper.updateById(doc);
                vectorStore.invalidate();
                return doc;
            } catch (Exception e) {
                return markFailed(doc, "解析失败：" + e.getMessage());
            }
        } catch (IOException e) {
            throw new BizException("文件读写失败：" + e.getMessage());
        }
    }

    private KmDocument markFailed(KmDocument doc, String msg) {
        doc.setParseStatus(3);
        doc.setParseMsg(msg.length() > 250 ? msg.substring(0, 250) : msg);
        docMapper.updateById(doc);
        return doc;
    }

    @Transactional
    public void indexOrder(Long orderId) {
        WoOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        String content = buildOrderContent(order);
        if (!StringUtils.hasText(content)) {
            return;
        }
        float[] vector = embedder.embed(content);
        orderVecMapper.delete(new LambdaQueryWrapper<OrderEmbedding>()
                .eq(OrderEmbedding::getOrderId, orderId));
        OrderEmbedding entity = new OrderEmbedding();
        entity.setOrderId(orderId);
        entity.setContent(content);
        entity.setEmbedding(VectorCodec.toBytes(vector));
        entity.setEmbeddingModel(embedder.modelName());
        entity.setDim(vector.length);
        orderVecMapper.insert(entity);
    }

    @Transactional
    public int rebuildOrders() {        orderVecMapper.delete(new LambdaQueryWrapper<OrderEmbedding>());
        List<WoOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<WoOrder>()
                .orderByAsc(WoOrder::getId));
        int count = 0;
        for (WoOrder order : orders) {
            String content = buildOrderContent(order);
            if (!StringUtils.hasText(content)) {
                continue;
            }
            float[] vector = embedder.embed(content);
            OrderEmbedding entity = new OrderEmbedding();
            entity.setOrderId(order.getId());
            entity.setContent(content);
            entity.setEmbedding(VectorCodec.toBytes(vector));
            entity.setEmbeddingModel(embedder.modelName());
            entity.setDim(vector.length);
            orderVecMapper.insert(entity);
            count++;
        }
        return count;
    }

    /**
     * 重建文档块向量：不重新解析文件，只把库里已存的 chunk 文本重新向量化。
     * 换向量模型（512 维哈希 → 1024 维 bge-m3）后必须执行一次，否则新旧向量维度不一致，余弦恒算成 0。
     */
    @Transactional
    public int reindexChunks() {
        List<ChunkVector> chunks = chunkMapper.listVectors();
        if (chunks.isEmpty()) {
            return 0;
        }
        int batchSize = 16;
        int count = 0;
        for (int from = 0; from < chunks.size(); from += batchSize) {
            List<ChunkVector> batch = chunks.subList(from, Math.min(from + batchSize, chunks.size()));
            List<String> texts = batch.stream().map(ChunkVector::getContent).collect(Collectors.toList());
            List<float[]> vectors = embedder.embedBatch(texts);
            for (int i = 0; i < batch.size(); i++) {
                KmChunk entity = chunkMapper.selectById(batch.get(i).getChunkId());
                if (entity == null) {
                    continue;
                }
                float[] vector = vectors.get(i);
                entity.setEmbedding(VectorCodec.toBytes(vector));
                entity.setEmbeddingModel(embedder.modelName());
                entity.setDim(vector.length);
                chunkMapper.updateById(entity);
                count++;
            }
        }
        vectorStore.invalidate();
        return count;
    }

    private String buildOrderContent(WoOrder order) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(order.getFaultType())) {
            sb.append(order.getFaultType()).append(' ');
        }
        if (StringUtils.hasText(order.getFaultDesc())) {
            sb.append(order.getFaultDesc());
        }
        if (StringUtils.hasText(order.getRepairSummary())) {
            sb.append("；处置：").append(order.getRepairSummary());
        }
        return sb.toString().trim();
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException("哈希计算失败：" + e.getMessage());
        }
    }
}