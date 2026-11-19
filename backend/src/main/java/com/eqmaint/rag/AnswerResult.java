package com.eqmaint.rag;

import java.util.List;

/** 生成结果：答案 + 实际被引用的块 ID（前端据此只展示真正用到的出处） */
public record AnswerResult(String answer, List<Long> usedChunkIds) {
}