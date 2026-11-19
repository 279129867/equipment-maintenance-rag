package com.eqmaint.rag;

import java.util.List;

/** 答案生成抽象：默认抽取式，rag.answer.provider=remote 时切到 {@link LlmAnswerGenerator} */
public interface AnswerGenerator {

    AnswerResult generate(String question, List<ChunkHit> contexts);

    /** 前端与问答日志里标注的作答方式 */
    default String mode() {
        return "extractive";
    }
}