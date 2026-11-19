package com.eqmaint.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 抽取式作答：从召回片段里挑"与问题最贴近的句子"，并补一句相邻的处置句，逐句标注出处。
 * 不调用大模型：不产生幻觉、句句可溯源，但不会归纳推理。
 * 切换：rag.answer.provider=remote 时由 {@link LlmAnswerGenerator} 接管，本类不生效。
 */
@Component
@ConditionalOnProperty(name = "rag.answer.provider", havingValue = "extractive", matchIfMissing = true)
public class ExtractiveAnswerGenerator implements AnswerGenerator {

    private static final int MAX_POINTS = 4;
    private static final int MAX_SENTENCE = 100;
    private static final Pattern HEADING_LIKE = Pattern.compile(
            "^(第[一二三四五六七八九十百]+[章节]|\\d+(\\.\\d+)*[\\s、.．:：]?|附录[一二三四五六七八九十]?)");

    @Override
    public AnswerResult generate(String question, List<ChunkHit> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return new AnswerResult("知识库中没有检索到相关内容。建议：补充该设备的手册或维修 SOP，或转人工处理。",
                    List.of());
        }
        Set<String> queryGrams = grams(question);
        List<Passage> passages = new ArrayList<>();
        for (ChunkHit hit : contexts) {
            Passage passage = bestPassage(hit, queryGrams);
            if (!passage.text().isBlank()) {
                passages.add(passage);
            }
        }
        // 优先只保留与问题字面相关的片段，避免标题行、无关块被当成建议列出来
        int minHits = Math.max(1, queryGrams.size() / 6);
        List<Passage> used = new ArrayList<>();
        for (Passage passage : passages) {
            if (passage.hits() >= minHits && used.size() < MAX_POINTS) {
                used.add(passage);
            }
        }
        if (used.isEmpty()) {
            for (Passage passage : passages) {
                if (passage.hits() > 0 && used.size() < MAX_POINTS) {
                    used.add(passage);
                }
            }
        }
        if (used.isEmpty()) {
            for (Passage passage : passages) {
                if (used.size() < MAX_POINTS) {
                    used.add(passage);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("根据知识库检索到的 ").append(used.size()).append(" 条片段，处理建议如下：\n");
        List<Long> usedIds = new ArrayList<>();
        for (int i = 0; i < used.size(); i++) {
            Passage passage = used.get(i);
            sb.append(i + 1).append(". ").append(passage.text())
                    .append("（来源：").append(source(passage.hit())).append("）\n");
            usedIds.add(passage.hit().getChunkId());
        }
        sb.append("（以上由文档片段抽取生成，未接入大模型；涉及安全或停机风险请以现场判断为准）");
        return new AnswerResult(sb.toString(), usedIds);
    }

    private record Passage(ChunkHit hit, String text, int hits) {
    }

    /** 取重叠最高的句子；若相邻句也有重叠则一并带上，形成"现象 + 处置"的完整建议 */
    private Passage bestPassage(ChunkHit hit, Set<String> queryGrams) {
        String content = hit.getContent();
        if (content == null) {
            return new Passage(hit, "", 0);
        }
        List<String> sentences = split(content);
        int bestIndex = -1;
        int bestScore = 0;
        List<Integer> scores = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            int score = 0;
            if (sentence.length() >= 8 && !isHeadingLike(sentence)) {
                score = overlap(sentence, queryGrams);
            }
            scores.add(score);
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        if (bestIndex < 0) {
            for (String sentence : sentences) {
                if (sentence.length() >= 8 && !isHeadingLike(sentence)) {
                    return new Passage(hit, trim(sentence), 0);
                }
            }
            return new Passage(hit, "", 0);
        }
        StringBuilder sb = new StringBuilder(clean(sentences.get(bestIndex)));
        int neighbor = -1;
        for (int offset : new int[]{1, -1}) {
            int idx = bestIndex + offset;
            if (idx >= 0 && idx < sentences.size() && scores.get(idx) >= 1) {
                neighbor = idx;
                break;
            }
        }
        if (neighbor >= 0) {
            sb.append('；').append(clean(sentences.get(neighbor)));
        }
        return new Passage(hit, sb.append('。').toString(), bestScore);
    }

    private List<String> split(String content) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (char c : content.toCharArray()) {
            if (c == '\n' || c == '\r') {
                if (sb.length() > 0) {
                    result.add(sb.toString().trim());
                    sb.setLength(0);
                }
                continue;
            }
            sb.append(c);
            if (c == '。' || c == '；') {
                result.add(sb.toString().trim());
                sb.setLength(0);
            }
        }
        if (sb.toString().trim().length() > 0) {
            result.add(sb.toString().trim());
        }
        return result;
    }

    private boolean isHeadingLike(String sentence) {
        return sentence.length() <= 20 && !sentence.contains("。") && HEADING_LIKE.matcher(sentence).find();
    }

    private int overlap(String sentence, Set<String> queryGrams) {
        Set<String> grams = grams(sentence);
        int count = 0;
        for (String gram : queryGrams) {
            if (grams.contains(gram)) {
                count++;
            }
        }
        return count;
    }

    private Set<String> grams(String text) {
        Set<String> grams = new HashSet<>();
        if (text == null) {
            return grams;
        }
        String clean = text.replaceAll("\\s", "");
        for (int i = 0; i + 1 < clean.length(); i++) {
            grams.add(clean.substring(i, i + 2));
        }
        return grams;
    }

    private String clean(String sentence) {
        String text = sentence.trim();
        while (text.length() > 0 && "。；;！!？?，, ".indexOf(text.charAt(text.length() - 1)) >= 0) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String trim(String sentence) {
        String text = clean(sentence);
        return text.length() <= MAX_SENTENCE ? text + "。" : text.substring(0, MAX_SENTENCE) + "…";
    }

    private String source(ChunkHit hit) {
        StringBuilder sb = new StringBuilder();
        sb.append('《').append(hit.getDocName() == null ? "未知文档" : hit.getDocName()).append('》');
        if (hit.getSectionPath() != null && !hit.getSectionPath().isBlank()) {
            sb.append("§").append(hit.getSectionPath());
        }
        if (hit.getPageNo() != null) {
            sb.append(" 第").append(hit.getPageNo()).append("页");
        }
        return sb.toString();
    }
}