package com.eqmaint.rag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_similar_ref")
public class AiSimilarRef {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long refOrderId;
    private BigDecimal score;
    private Integer hit;
    private LocalDateTime recallTime;
}