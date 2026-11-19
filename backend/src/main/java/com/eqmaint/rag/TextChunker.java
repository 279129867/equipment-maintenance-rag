package com.eqmaint.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本分块：按章节标题切段，段落累积到 maxChars 后切块，块间保留 overlap 字符避免语义断裂。
 * 每块携带 sectionPath 与 pageNo，这是"答案可溯源"的数据基础。
 * sectionPath 取"块内第一个标题"，而不是最后一个，避免块内跨小节时出处标注错位。
 */
public final class TextChunker {

    public record PageText(int pageNo, String text) {
    }

    public record Chunk(int chunkNo, String sectionPath, int pageNo, String content) {
    }

    private static final Pattern HEADING = Pattern.compile(
            "^(第[一二三四五六七八九十百]+[章节]|\\d+(\\.\\d+)*[\\s、.．:：]|[一二三四五六七八九十]+、|附录|附[件表]|\\d+[\\.、]\\s*[\\u4e00-\\u9fa5])");

    private TextChunker() {
    }

    public static List<Chunk> split(List<PageText> pages, int maxChars, int overlap) {
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String section = "正文";
        String blockSection = null;
        int bufferPage = 1;
        int chunkNo = 0;

        for (PageText page : pages) {
            if (page.text() == null) {
                continue;
            }
            for (String rawLine : page.text().split("\\r?\\n")) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (isHeading(line) && buffer.length() > maxChars / 3) {
                    chunkNo = flush(chunks, buffer, blockSection != null ? blockSection : section, bufferPage, chunkNo);
                    buffer.setLength(0);
                    blockSection = null;
                }
                if (isHeading(line)) {
                    section = line.length() > 40 ? line.substring(0, 40) : line;
                    if (buffer.length() == 0) {
                        blockSection = section;
                    }
                }
                buffer.append(line).append('\n');
                bufferPage = page.pageNo();
                if (buffer.length() >= maxChars) {
                    chunkNo = flush(chunks, buffer, blockSection != null ? blockSection : section, bufferPage, chunkNo);
                    String tail = tail(buffer.toString(), overlap);
                    buffer.setLength(0);
                    buffer.append(tail);
                    blockSection = null;
                }
            }
        }
        if (buffer.toString().trim().length() > 20) {
            flush(chunks, buffer, blockSection != null ? blockSection : section, bufferPage, chunkNo);
        }
        return chunks;
    }

    private static int flush(List<Chunk> chunks, StringBuilder buffer, String section,
                             int pageNo, int chunkNo) {
        String content = buffer.toString().trim();
        if (content.isEmpty()) {
            return chunkNo;
        }
        chunks.add(new Chunk(chunkNo + 1, section, pageNo, content));
        return chunkNo + 1;
    }

    private static boolean isHeading(String line) {
        if (line.length() > 40) {
            return false;
        }
        return HEADING.matcher(line).find();
    }

    private static String tail(String text, int overlap) {
        if (text.length() <= overlap) {
            return text;
        }
        return text.substring(text.length() - overlap);
    }
}