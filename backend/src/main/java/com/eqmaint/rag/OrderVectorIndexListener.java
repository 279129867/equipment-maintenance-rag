package com.eqmaint.rag;

import com.eqmaint.common.event.OrderClosedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderVectorIndexListener {

    private static final Logger log = LoggerFactory.getLogger(OrderVectorIndexListener.class);

    private final RagIndexService indexService;

    public OrderVectorIndexListener(RagIndexService indexService) {
        this.indexService = indexService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderClosed(OrderClosedEvent event) {
        try {
            indexService.indexOrder(event.orderId());
            log.info("工单 {} 已加入相似召回语料", event.orderId());
        } catch (Exception e) {
            log.warn("工单 {} 向量化失败，不影响关闭流程：{}", event.orderId(), e.getMessage());
        }
    }
}