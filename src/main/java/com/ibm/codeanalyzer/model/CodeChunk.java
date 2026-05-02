package com.ibm.codeanalyzer.model;

import lombok.Builder;
import lombok.Data;

/**
 * A piece of source code ready to be sent to the LLM.
 * Large files are split into overlapping chunks so no context is lost.
 */
@Data
@Builder
public class CodeChunk {

    /** Source file path */
    private String filePath;

    /** Chunk index within the file (0-based) */
    private int chunkIndex;

    /** Total chunks for this file */
    private int totalChunks;

    /** The actual source code content */
    private String content;

    /** Detected language (java, python, js, etc.) */
    private String language;

    /** Approximate line range covered */
    private int startLine;
    private int endLine;
}
