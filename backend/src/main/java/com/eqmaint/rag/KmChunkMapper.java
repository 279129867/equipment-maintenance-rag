package com.eqmaint.rag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KmChunkMapper extends BaseMapper<KmChunk> {

    /** 关键词召回：MySQL ngram 全文索引，对中文与型号/代码类精确词有效 */
    @Select("""
            SELECT c.id AS chunkId, c.doc_id AS docId, c.chunk_no AS chunkNo,
                   c.section_path AS sectionPath, c.page_no AS pageNo, c.content AS content,
                   d.doc_name AS docName,
                   MATCH(c.content) AGAINST(#{q} IN NATURAL LANGUAGE MODE) AS score
            FROM km_chunk c
            JOIN km_document d ON d.id = c.doc_id
            WHERE MATCH(c.content) AGAINST(#{q} IN NATURAL LANGUAGE MODE)
              AND (#{assetType} IS NULL OR d.asset_type = #{assetType} OR d.asset_type = '')
            ORDER BY score DESC
            LIMIT #{limit}
            """)
    List<ChunkHit> keywordSearch(@Param("q") String q,
                                 @Param("assetType") String assetType,
                                 @Param("limit") int limit);

    /** 取全部带向量的块，用于内存向量检索（过滤在 Java 侧做，便于缓存） */
    @Select("""
            SELECT c.id AS chunkId, c.doc_id AS docId, c.chunk_no AS chunkNo,
                   c.section_path AS sectionPath, c.page_no AS pageNo, c.content AS content,
                   c.embedding AS embedding, d.doc_name AS docName, d.asset_type AS docAssetType
            FROM km_chunk c
            JOIN km_document d ON d.id = c.doc_id
            WHERE c.embedding IS NOT NULL
            """)
    List<ChunkVector> listVectors();
}