package com.eqmaint.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eqmaint.common.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final RagIndexService indexService;
    private final KmDocumentMapper docMapper;
    private final VectorStore vectorStore;

    public RagController(RagService ragService, RagIndexService indexService,
                         KmDocumentMapper docMapper, VectorStore vectorStore) {
        this.ragService = ragService;
        this.indexService = indexService;
        this.docMapper = docMapper;
        this.vectorStore = vectorStore;
    }

    @PostMapping("/documents")
    public R<KmDocument> upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(required = false) Integer docType,
                                @RequestParam(required = false) String assetType,
                                @RequestParam(required = false) String uploader) {
        return R.ok(indexService.ingest(file, docType, assetType, uploader));
    }

    @GetMapping("/documents")
    public R<List<KmDocument>> documents() {
        return R.ok(docMapper.selectList(new LambdaQueryWrapper<KmDocument>()
                .orderByDesc(KmDocument::getId)));
    }

    @DeleteMapping("/documents/{id}")
    public R<Void> delete(@PathVariable Long id) {
        docMapper.deleteById(id);
        vectorStore.invalidate();
        return R.ok();
    }

    @PostMapping("/ask")
    public R<AskResp> ask(@RequestBody AskReq req) {
        return R.ok(ragService.ask(req.question(), req.topK(), req.assetType()));
    }

    @GetMapping("/search")
    public R<List<ChunkHit>> search(@RequestParam String q,
                                    @RequestParam(required = false) String assetType,
                                    @RequestParam(required = false) Integer topK) {
        return R.ok(ragService.search(q, assetType, topK));
    }

    @GetMapping("/similar-orders")
    public R<List<SimilarOrder>> similarOrders(@RequestParam Long orderId,
                                               @RequestParam(required = false) Integer topK) {
        return R.ok(ragService.similarOrders(orderId, topK));
    }

    @PostMapping("/similar-orders/feedback")
    public R<Void> feedback(@RequestParam Long orderId,
                            @RequestParam Long refOrderId,
                            @RequestParam(defaultValue = "1") Integer hit) {
        ragService.markHit(orderId, refOrderId, hit);
        return R.ok();
    }

    @PostMapping("/index-orders")
    public R<Integer> indexOrders() {
        return R.ok(indexService.rebuildOrders());
    }

    /**
     * 换向量模型后重建全部向量（文档块 + 工单语料）。
     * 512 维哈希与 1024 维 bge-m3 不兼容，切换后必须跑一次，否则问答与相似工单都召回不到东西。
     */
    @PostMapping("/reindex")
    public R<ReindexResult> reindex(@RequestParam(required = false) String scope) {
        boolean withDocs = scope == null || "all".equals(scope) || "chunks".equals(scope);
        boolean withOrders = scope == null || "all".equals(scope) || "orders".equals(scope);
        int chunks = withDocs ? indexService.reindexChunks() : 0;
        int orders = withOrders ? indexService.rebuildOrders() : 0;
        return R.ok(new ReindexResult(chunks, orders));
    }

    public record ReindexResult(int chunks, int orders) {
    }
}