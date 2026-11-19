package com.eqmaint.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容接口的最小客户端（SiliconFlow / 任何 /v1/embeddings + /v1/chat/completions 服务都能用）。
 * 只用 JDK HttpClient，不引新依赖；失败时抛带状态码与响应体的异常，便于在控制台定位是网络、鉴权还是模型名不对。
 */
public class OpenAiCompatibleClient {

    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;

    public OpenAiCompatibleClient(String baseUrl, String apiKey, int timeoutMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 10000)))
                .build();
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 批量向量化；返回与输入顺序一致的向量列表 */
    public List<float[]> embed(String model, List<String> inputs) {
        if (inputs.isEmpty()) {
            return List.of();
        }
        List<Object> payloadInputs = new ArrayList<>(inputs);
        Map<String, Object> body = Map.of("model", model, "input", payloadInputs);
        JsonNode root = post("/embeddings", body, "embedding");
        JsonNode data = root.path("data");
        List<float[]> result = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode embedding = item.path("embedding");
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            result.add(vector);
        }
        return result;
    }

    /** 对话补全，返回 choices[0].message.content */
    public String chat(String model, List<Map<String, String>> messages, double temperature, int maxTokens) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("stream", false);
        JsonNode root = post("/chat/completions", body, "chat");
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private JsonNode post(String path, Map<String, Object> body, String what) {
        if (!hasKey()) {
            throw new IllegalStateException("未配置 API key（环境变量 SF_API_KEY / rag." + what + ".api-key）");
        }
        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                String text = response.body();
                throw new IllegalStateException("模型接口返回 " + response.statusCode() + "：" +
                        (text == null ? "" : (text.length() > 300 ? text.substring(0, 300) : text)));
            }
            return mapper.readTree(response.body());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用模型接口失败：" + e.getMessage(), e);
        }
    }
}
