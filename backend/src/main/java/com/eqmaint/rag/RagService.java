package com.eqmaint.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eqmaint.common.BizException;
import com.eqmaint.workorder.OrderMapper;
import com.eqmaint.workorder.WoOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final KmChunkMapper chunkMapper;
    private final KmDocumentMapper docMapper;
    private final OrderEmbeddingMapper orderVecMapper;
    private final OrderMapper orderMapper;
    private final AiQaLogMapper qaLogMapper;
    private final AiSimilarRefMapper similarRefMapper;
    private final EmbeddingClient embedder;
    private final AnswerGenerator answerGenerator;
    private final VectorStore vectorStore;

    @Value("${rag.top-k:4}")
    private int defaultTopK;

    @Value("${rag.min-score:0.22}")
    private double minScore;

    public RagService(KmChunkMapper chunkMapper, KmDocumentMapper docMapper,
                      OrderEmbeddingMapper orderVecMapper, OrderMapper orderMapper,
                      AiQaLogMapper qaLogMapper, AiSimilarRefMapper similarRefMapper,
                      EmbeddingClient embedder, AnswerGenerator answerGenerator,
                      VectorStore vectorStore) {
        this.chunkMapper = chunkMapper;
        this.docMapper = docMapper;
        this.orderVecMapper = orderVecMapper;
        this.orderMapper = orderMapper;
        this.qaLogMapper = qaLogMapper;
        this.similarRefMapper = similarRefMapper;
        this.embedder = embedder;
        this.answerGenerator = answerGenerator;
        this.vectorStore = vectorStore;
    }

    public AskResp ask(String question, Integer topK, String assetType) {
        if (!StringUtils.hasText(question)) {
            throw new BizException("问题不能为空");
        }
        long start = System.currentTimeMillis();
        int k = topK == null ? defaultTopK : Math.max(1, Math.min(topK, 10));
        float[] queryVector = embedder.embed(question);

        Map<Long, ChunkHit> merged = new LinkedHashMap<>();

        for (ChunkHit hit : vectorSearch(queryVector, assetType, k * 3)) {
            double cos = hit.getCosine() == null ? 0 : hit.getCosine();
            hit.setScore(0.65 * cos);
            merged.put(hit.getChunkId(), hit);
        }

        List<ChunkHit> keywordHits = chunkMapper.keywordSearch(question, blankToNull(assetType), k * 3);
        double maxKeyword = keywordHits.stream()
                .mapToDouble(h -> h.getScore() == null ? 0 : h.getScore()).max().orElse(0.0);
        for (ChunkHit hit : keywordHits) {
            double norm = maxKeyword > 0 ? (hit.getScore() == null ? 0 : hit.getScore()) / maxKeyword : 0;
            ChunkHit exist = merged.get(hit.getChunkId());
            if (exist != null) {
                exist.setScore(exist.getScore() + 0.35 * norm);
            } else {
                hit.setScore(0.35 * norm);
                merged.put(hit.getChunkId(), hit);
            }
        }

        List<ChunkHit> ranked = merged.values().stream()
                .sorted(Comparator.comparingDouble((ChunkHit h) -> h.getScore() == null ? 0 : h.getScore()).reversed())
                .limit(k)
                .collect(Collectors.toList());

        double top = ranked.isEmpty() ? 0 : ranked.get(0).getScore();
        String answer;
        List<Citation> citations = new ArrayList<>();
        if (ranked.isEmpty() || top < minScore) {
            answer = "知识库中没有找到足够相关的内容（最高相关度 " + round(top)
                    + "，阈值 " + minScore + "）。建议补充该设备的手册或维修 SOP，或转人工判断。";
        } else {
            // 只保留与首位差距不大的片段，避免把明显无关的块也塞进答案
            double floor = Math.max(minScore * 0.7, top * 0.45);
            List<ChunkHit> kept = ranked.stream()
                    .filter(h -> h.getScore() != null && h.getScore() >= floor)
                    .collect(Collectors.toList());
            AnswerResult result = answerGenerator.generate(question, kept);
            answer = result.answer();
            for (ChunkHit hit : kept) {
                if (result.usedChunkIds().isEmpty() || result.usedChunkIds().contains(hit.getChunkId())) {
                    citations.add(new Citation(hit.getChunkId(), hit.getDocName(), hit.getSectionPath(),
                            hit.getPageNo(), snippet(hit.getContent()), round(hit.getScore())));
                }
            }
        }

        long latency = System.currentTimeMillis() - start;
        writeLog(question, answer, citations, latency);
        return new AskResp(answer, citations, latency, answerGenerator.mode(), round(top));
    }

    public List<ChunkHit> search(String question, String assetType, Integer topK) {
        int k = topK == null ? 5 : Math.max(1, Math.min(topK, 20));
        return vectorSearch(embedder.embed(question), assetType, k);
    }

    public List<SimilarOrder> similarOrders(Long orderId, Integer topK) {
        WoOrder target = orderMapper.selectById(orderId);
        if (target == null) {
            throw new BizException("工单不存在：" + orderId);
        }
        int k = topK == null ? 3 : Math.max(1, Math.min(topK, 10));
        float[] queryVector = embedder.embed(orderText(target));

        record Scored(Long orderId, double score) {
        }

        List<Scored> scored = new ArrayList<>();
        // 一次取全量工单，避免逐条查库；规模在千级以内，内存里做加权足够
        Map<Long, WoOrder> allOrders = orderMapper.selectList(null).stream()
                .collect(Collectors.toMap(WoOrder::getId, o -> o, (a, b) -> a));
        for (OrderEmbedding entity : orderVecMapper.selectList(null)) {
            if (orderId.equals(entity.getOrderId())) {
                continue;
            }
            double similarity = VectorCodec.cosine(queryVector, VectorCodec.toVector(entity.getEmbedding()));
            // 同类故障加权：机械/电气/液压等分类相同，运维经验更可迁移，给 0.18 的固定加成
            WoOrder other = allOrders.get(entity.getOrderId());
            if (other != null && target.getFaultType() != null && !target.getFaultType().isBlank()
                    && target.getFaultType().equals(other.getFaultType())) {
                similarity = Math.min(1.0, similarity + 0.18);
            }
            if (similarity > 0.05) {
                scored.add(new Scored(entity.getOrderId(), similarity));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<Scored> top = scored.subList(0, Math.min(k, scored.size()));
        if (top.isEmpty()) {
            return List.of();
        }

        List<Long> ids = top.stream().map(Scored::orderId).toList();
        Map<Long, WoOrder> orderMap = orderMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(WoOrder::getId, o -> o));

        similarRefMapper.delete(new LambdaQueryWrapper<AiSimilarRef>().eq(AiSimilarRef::getOrderId, orderId));

        List<SimilarOrder> result = new ArrayList<>();
        for (Scored item : top) {
            WoOrder order = orderMap.get(item.orderId());
            if (order == null) {
                continue;
            }
            result.add(new SimilarOrder(order.getId(), order.getOrderNo(), order.getFaultType(),
                    order.getFaultDesc(), order.getRepairSummary(), order.getDowntimeMinutes(),
                    round(item.score())));

            AiSimilarRef ref = new AiSimilarRef();
            ref.setOrderId(orderId);
            ref.setRefOrderId(order.getId());
            ref.setScore(BigDecimal.valueOf(item.score()).setScale(4, RoundingMode.HALF_UP));
            ref.setHit(0);
            ref.setRecallTime(LocalDateTime.now());
            similarRefMapper.insert(ref);
        }
        return result;
    }

    /** 人工反馈：标记某条推荐是否被采纳，用于评估召回质量 */
    public void markHit(Long orderId, Long refOrderId, Integer hit) {
        List<AiSimilarRef> refs = similarRefMapper.selectList(new LambdaQueryWrapper<AiSimilarRef>()
                .eq(AiSimilarRef::getOrderId, orderId)
                .eq(AiSimilarRef::getRefOrderId, refOrderId));
        for (AiSimilarRef ref : refs) {
            ref.setHit(hit == null ? 1 : hit);
            similarRefMapper.updateById(ref);
        }
    }

    private List<ChunkHit> vectorSearch(float[] queryVector, String assetType, int limit) {
        String filter = blankToNull(assetType);
        List<ChunkHit> hits = new ArrayList<>();
        for (VectorStore.Entry entry : vectorStore.all()) {
            String docAssetType = entry.chunk().getDocAssetType();
            if (filter != null && StringUtils.hasText(docAssetType) && !filter.equals(docAssetType)) {
                continue;
            }
            double similarity = VectorCodec.cosine(queryVector, entry.vector());
            if (similarity <= 0) {
                continue;
            }
            ChunkHit hit = new ChunkHit();
            hit.setChunkId(entry.chunk().getChunkId());
            hit.setDocId(entry.chunk().getDocId());
            hit.setChunkNo(entry.chunk().getChunkNo());
            hit.setSectionPath(entry.chunk().getSectionPath());
            hit.setPageNo(entry.chunk().getPageNo());
            hit.setContent(entry.chunk().getContent());
            hit.setDocName(entry.chunk().getDocName());
            hit.setCosine(similarity);
            hits.add(hit);
        }
        hits.sort(Comparator.comparingDouble((ChunkHit h) -> h.getCosine() == null ? 0 : h.getCosine()).reversed());
        return new ArrayList<>(hits.subList(0, Math.min(limit, hits.size())));
    }

    private void writeLog(String question, String answer, List<Citation> citations, long latency) {
        try {
            AiQaLog qaLog = new AiQaLog();
            qaLog.setSessionId("api");
            qaLog.setQuestion(question.length() > 500 ? question.substring(0, 500) : question);
            qaLog.setAnswer(answer);
            qaLog.setCitedChunkIds(citations.stream().map(c -> String.valueOf(c.chunkId()))
                    .collect(Collectors.joining(",")));
            qaLog.setLatencyMs((int) latency);
            qaLog.setHitCache(0);
            qaLogMapper.insert(qaLog);
        } catch (Exception ignored) {
            // 日志失败不影响主流程
        }
    }

    private String orderText(WoOrder order) {
        String text = (order.getFaultType() == null ? "" : order.getFaultType()) + " "
                + (order.getFaultDesc() == null ? "" : order.getFaultDesc());
        return text.trim();
    }

    private String snippet(String content) {
        if (content == null) {
            return "";
        }
        String text = content.replace('\n', ' ').trim();
        return text.length() <= 120 ? text : text.substring(0, 120) + "…";
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}