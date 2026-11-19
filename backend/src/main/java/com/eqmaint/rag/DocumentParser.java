package com.eqmaint.rag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentParser {

    public List<TextChunker.PageText> parse(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf")) {
            return parsePdf(path);
        }
        if (name.endsWith(".docx")) {
            return parseDocx(path);
        }
        return parsePlainText(path);
    }

    private List<TextChunker.PageText> parsePdf(Path path) throws IOException {
        List<TextChunker.PageText> pages = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pages.add(new TextChunker.PageText(i, stripper.getText(doc)));
            }
        }
        return pages;
    }

    private List<TextChunker.PageText> parseDocx(Path path) throws IOException {
        List<TextChunker.PageText> pages = new ArrayList<>();
        try (InputStream in = Files.newInputStream(path); XWPFDocument doc = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String t = p.getText();
                if (t != null && !t.isBlank()) {
                    sb.append(t.trim()).append('\n');
                }
            }
            appendPaged(pages, sb.toString(), 3000);
        }
        return pages;
    }

    private List<TextChunker.PageText> parsePlainText(Path path) throws IOException {
        List<TextChunker.PageText> pages = new ArrayList<>();
        String text = Files.readString(path, StandardCharsets.UTF_8);
        appendPaged(pages, text, 3000);
        return pages;
    }

    private void appendPaged(List<TextChunker.PageText> pages, String text, int pageSize) {
        int pageNo = 1;
        for (int i = 0; i < text.length(); i += pageSize) {
            int end = Math.min(text.length(), i + pageSize);
            pages.add(new TextChunker.PageText(pageNo++, text.substring(i, end)));
        }
        if (pages.isEmpty()) {
            pages.add(new TextChunker.PageText(1, ""));
        }
    }
}