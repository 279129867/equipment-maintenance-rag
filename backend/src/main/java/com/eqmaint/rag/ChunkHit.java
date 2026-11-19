package com.eqmaint.rag;

import lombok.Data;

@Data
public class ChunkHit {
    private Long chunkId;
    private Long docId;
    private Integer chunkNo;
    private String sectionPath;
    private Integer pageNo;
    private String content;
    private String docName;
    private Double score;
    private Double cosine;
}