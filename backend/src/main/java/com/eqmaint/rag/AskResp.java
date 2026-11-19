package com.eqmaint.rag;

import java.util.List;

public record AskResp(String answer, List<Citation> citations, long latencyMs,
                      String mode, double topScore) {
}