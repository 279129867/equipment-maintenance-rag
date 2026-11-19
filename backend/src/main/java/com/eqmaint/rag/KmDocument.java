package com.eqmaint.rag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("km_document")
public class KmDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String docName;
    private Integer docType;
    private String assetType;
    private String fileUrl;
    private String fileHash;
    private Integer parseStatus;
    private String parseMsg;
    private Integer chunkCount;
    private Integer version;
    private String uploader;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}