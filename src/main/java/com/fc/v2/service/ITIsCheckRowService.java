package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TIsCheckRow;
import com.fc.v2.service.check.BatchReceipt;

/**
 * 执法检查记录导入核对行 Service接口（batch-process 形状：整批提交，无单条增删改）。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITIsCheckRowService {

    /** 按主键回查单行 */
    TIsCheckRow selectTIsCheckRowById(Long id);

    /**
     * 【原处理方法，签名不动】整批提交实体行：逐行校验，合法的入库、非法的记入失败明细（保留原始行号），
     * 返回**成功行数**。同一批次重复提交不得重复入库；行数超过单批上限返回 -1 且整批不入库；空批次返回 0。
     */
    int submitBatch(String batchNo, List<TIsCheckRow> rows);

    /** 该批次的失败行明细（按行号升序） */
    List<TIsCheckRow> listErrors(String batchNo);

    /**
     * 【同名叠加的一层递法：直接收上传文件】整批导入。
     * 只收 UTF-8（xlsx 或 CSV UTF-8）；GBK 给明话转码提示。回执三个数以库里逐行点算为准。
     */
    BatchReceipt importBatch(String batchNo, byte[] fileBytes, String filename, String operator);

    /** 该批次逐行台账（按原表行号升序）：三数点算、错误清单都照它 */
    List<TIsCheckRow> listBatch(String batchNo);

    /** 正式检查记录回查：按批次号 / 检查日期 / 企业编号（同企业并入行不重复出，只数前头行） */
    List<TIsCheckRow> searchFormal(String batchNo, String siteNo, Date checkDate);
}
