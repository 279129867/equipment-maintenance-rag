package com.eqmaint.rag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_order_embedding")
public class OrderEmbedding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private String content;
    private byte[] embedding;
    private String embeddingModel;
    private Integer dim;
    private LocalDateTime createTime;
}