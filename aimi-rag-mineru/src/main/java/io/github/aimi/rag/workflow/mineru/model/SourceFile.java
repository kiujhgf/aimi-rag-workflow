package io.github.aimi.rag.workflow.mineru.model;

import java.util.Arrays;

/**
 * 输入模型：multipart 上传时携带的文件名与文件字节。
 */
public class SourceFile {

    private final String fileName;
    private final byte[] content;

    public SourceFile(String fileName, byte[] content) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        this.fileName = fileName;
        this.content = content;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "SourceFile{fileName='" + fileName + "', size=" + content.length + " bytes}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceFile that)) return false;
        return fileName.equals(that.fileName) && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        int result = fileName.hashCode();
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }
}
