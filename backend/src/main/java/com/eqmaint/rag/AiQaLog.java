package com.eqmaint.rag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_qa_log")
public class AiQaLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private String question;
    private String answer;
    private String citedChunkIds;
    private Integer latencyMs;
    private Integer hitCache;
    private LocalDateTime createTime;
}