package com.eqmaint.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 大模型作答（OpenAI 兼容 /v1/chat/completions）。
 *
 * 约束和抽取式实现保持同一条底线：只依据给定片段回答、逐条标注来源编号，
 * 片段不足以回答就直说缺什么，不允许凭常识编造维修步骤 —— 这是维保场景的安全性要求。
 *
 * 开启方式：rag.answer.provider=remote + SF_API_KEY
 */
@Component
@ConditionalOnProperty(name = "rag.answer.provider", havingValue = "remote")
public class LlmAnswerGenerator implements AnswerGenerator {

    private static final Pattern CITED = Pattern.compile("\\[(\\d+)]");

    private static final String SYSTEM_PROMPT = """
            你是制造企业的设备维保助手，回答对象是现场维修工。
            规则：
            1. 只能使用 <片段> 中给出的内容回答，禁止使用片段之外的知识，禁止编造型号、参数、步骤。
            2. 每条结论后用方括号标注来源编号，例如 [1]、[2]，编号必须是片段里给出的编号。
            3. 如果片段不足以回答，直接说明"知识库中未找到相关内容"并指出缺少什么信息（哪份手册、哪个章节）。
            4. 需要提醒安全事项时，单独一行以"注意："开头，这一行不要加任何来源编号。
            5. 用中文回答，分点陈述，不超过 200 字，不要复述问题，不要用 Markdown 记号。
            6. 方括号只用于引用片段编号，不要在方括号里写说明文字。
            """;

    private final OpenAiCompatibleClient client;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    public LlmAnswerGenerator(
            @Value("${rag.answer.base-url:https://api.siliconflow.cn/v1}") String baseUrl,
            @Value("${rag.answer.api-key:${SF_API_KEY:}}") String apiKey,
            @Value("${rag.answer.model:Qwen/Qwen2.5-7B-Instruct}") String model,
            @Value("${rag.answer.temperature:0.2}") double temperature,
            @Value("${rag.answer.max-tokens:400}") int maxTokens,
            @Value("${rag.answer.timeout-ms:60000}") int timeoutMs) {
        this.client = new OpenAiCompatibleClient(baseUrl, apiKey, timeoutMs);
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    @Override
    public String mode() {
        return "llm";
    }

    @Override
    public AnswerResult generate(String question, List<ChunkHit> contexts) {
        List<ChunkHit> hits = contexts == null ? List.of() : contexts;
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", buildUserPrompt(question, hits)));

        String answer;
        try {
            answer = client.chat(model, messages, temperature, maxTokens).trim();
        } catch (RuntimeException e) {
            // 模型不可用时不能让问答直接 500，退回抽取式并明确说明降级原因
            AnswerResult fallback = new ExtractiveAnswerGenerator().generate(question, hits);
            return new AnswerResult("（大模型调用失败，已降级为抽取式作答：" + e.getMessage() + "）\n\n" + fallback.answer(),
                    fallback.usedChunkIds());
        }
        return new AnswerResult(answer, citedChunkIds(answer, hits));
    }

    private String buildUserPrompt(String question, List<ChunkHit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("问题：").append(question).append("\n\n<片段>\n");
        for (int i = 0; i < hits.size(); i++) {
            ChunkHit hit = hits.get(i);
            sb.append('[').append(i + 1).append("] ");
            sb.append('《').append(hit.getDocName() == null ? "未知文档" : hit.getDocName()).append('》');
            if (hit.getSectionPath() != null && !hit.getSectionPath().isBlank()) {
                sb.append(" §").append(hit.getSectionPath());
            }
            if (hit.getPageNo() != null) {
                sb.append(" 第").append(hit.getPageNo()).append("页");
            }
            sb.append('\n').append(hit.getContent() == null ? "" : hit.getContent().trim()).append("\n\n");
        }
        sb.append("</片段>");
        return sb.toString();
    }

    /** 只把答案里真正引用到的片段算作出处，避免前端把所有召回块都当引用展示 */
    private List<Long> citedChunkIds(String answer, List<ChunkHit> hits) {
        Map<Integer, Long> indexToChunk = new LinkedHashMap<>();
        for (int i = 0; i < hits.size(); i++) {
            indexToChunk.put(i + 1, hits.get(i).getChunkId());
        }
        List<Long> ids = new ArrayList<>();
        Matcher matcher = CITED.matcher(answer == null ? "" : answer);
        while (matcher.find()) {
            Long chunkId = indexToChunk.get(Integer.parseInt(matcher.group(1)));
            if (chunkId != null && !ids.contains(chunkId)) {
                ids.add(chunkId);
            }
        }
        return ids.isEmpty() ? hits.stream().map(ChunkHit::getChunkId).toList() : ids;
    }
}
