package com.eqmaint.rag;

public record Citation(Long chunkId, String docName, String sectionPath, Integer pageNo,
                       String snippet, double score) {
}