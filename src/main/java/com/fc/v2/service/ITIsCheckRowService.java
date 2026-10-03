package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TIsCheckBatch;
import com.fc.v2.model.auto.TIsCheckRow;
import com.fc.v2.service.check.CheckError;
import com.fc.v2.service.check.CheckImportRow;
import com.fc.v2.service.check.CheckReceipt;

/**
 * 执法检查记录导入核对行 Service接口（batch-process 形状：整批提交，无单条增删改）。
 *
 * 一批收不收、哪行进哪行退，全由后台逐行处理方法说了算；错误清单行次、回执三个数只认它的回执。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITIsCheckRowService {

    /** 按主键回查单行 */
    TIsCheckRow selectTIsCheckRowById(Long id);

    /**
     * 整批提交（原签名不动）：逐行校验，合法的入库、非法的记"行号+类别码+原由"驳回明细，
     * 同批别行照走。返回**本次新入库行数**；行数超单批上限返回 -1 且整批不入库；
     * 空批返回 0；同批次号二次提交不覆盖头遍、本次 0 行写入。
     */
    int submitBatch(String batchNo, List<TIsCheckRow> rows);

    /**
     * 同名再叠一层的递法：喂原文件字节（只吃 UTF-8 的 CSV；GBK 进门给明确转码提示），
     * 进门定行次、逐行验、逐行落，回执报全批/入库/驳回三数（后两数之和等于第一个数）。
     */
    CheckReceipt submitBatch(String batchNo, byte[] fileBytes, String fileName, String operator);

    /** 同名再叠一层：已由读入器解成带原表行次的数据行，直接交逐行处理（供复测/外部系统对接）。 */
    CheckReceipt submitBatch(String batchNo, List<CheckImportRow> rows, String sourceFile, String operator);

    /** 该批次的失败行记录（按行号升序，沿用整表实体形状） */
    List<TIsCheckRow> listErrors(String batchNo);

    /** 该批次的错误清单："行号+类别+缘由"三段，行号照原表逐行对。 */
    List<CheckError> listErrorDetails(String batchNo);

    /** 正式检查记录：按批次号、检查日期、企业编号任一档查（同企业并记后每企业一条）。 */
    List<TIsCheckRow> listRecords(String batchNo, Date checkDate, String siteNo);

    /** 批次登记头（回执三数、进门时刻在此）。 */
    TIsCheckBatch getBatch(String batchNo);
}
