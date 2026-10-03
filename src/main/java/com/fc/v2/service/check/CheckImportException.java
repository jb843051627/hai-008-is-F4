package com.fc.v2.service.check;

/**
 * 导入文件进门失败：编码不符、格式不支持、表头缺列等。
 *
 * 只装给人看的明话（如 GBK 转码提示），不许把乱码堆栈甩给用户。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class CheckImportException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public CheckImportException(String message) {
        super(message);
    }
}
