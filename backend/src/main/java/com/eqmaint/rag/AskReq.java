package com.eqmaint.rag;

public record AskReq(String question, Integer topK, String assetType) {
}