package com.eqmaint.rag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("km_chunk")
public class KmChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;
    private Integer chunkNo;
    private String sectionPath;
    private Integer pageNo;
    private String content;
    private Integer tokenCount;
    private byte[] embedding;
    private String embeddingModel;
    private Integer dim;
    private LocalDateTime createTime;
}