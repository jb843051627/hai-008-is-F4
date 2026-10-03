package com.fc.v2.service.check;

/**
 * 上传文件读不进门时抛出：话术必须是人话（编码怎么转、格式哪里不对），
 * 不许把乱码/原始堆栈直接甩给界面。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class CheckFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CheckFileException(String message) {
        super(message);
    }

    public CheckFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
