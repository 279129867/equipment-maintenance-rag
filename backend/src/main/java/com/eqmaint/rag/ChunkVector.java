package com.eqmaint.rag;

import lombok.Data;

@Data
public class ChunkVector {
    private Long chunkId;
    private Long docId;
    private Integer chunkNo;
    private String sectionPath;
    private Integer pageNo;
    private String content;
    private byte[] embedding;
    private String docName;
    private String docAssetType;
}