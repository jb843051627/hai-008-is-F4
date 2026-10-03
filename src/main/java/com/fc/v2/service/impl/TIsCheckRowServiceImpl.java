package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIsCheckRowMapper;
import com.fc.v2.model.auto.TIsCheckBatch;
import com.fc.v2.model.auto.TIsCheckRow;
import com.fc.v2.service.ITIsCheckRowService;
import com.fc.v2.service.check.CheckError;
import com.fc.v2.service.check.CheckImportRow;
import com.fc.v2.service.check.CheckImportTx;
import com.fc.v2.service.check.CheckReceipt;
import com.fc.v2.service.check.CsvCheckFileReader;

/**
 * 执法检查记录导入核对行 Service业务层（batch-process 形状：整批提交）。
 *
 * 本层只做"递法适配"：原签名拿实体集合、新递法拿文件字节/解析行，最后都交
 * {@link CheckImportTx} 那一个逐行处理方法，批的收拒、行的进退、回执三数只认它的结果。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TIsCheckRowServiceImpl implements ITIsCheckRowService {

    @javax.annotation.Resource
    private TIsCheckRowMapper isCheckRowMapper;

    /** 逐行处理方法单独一个 Bean，让其 @Transactional 经代理生效（同 RectApplyTx/HazDispatchTx 做法）。 */
    @javax.annotation.Resource
    private CheckImportTx checkImportTx;

    @Override
    public TIsCheckRow selectTIsCheckRowById(Long id) {
        return this.isCheckRowMapper.selectById(id);
    }

    /**
     * 原签名不动。实体集合照原列表先后定原表行次（表头不算、首条为 1），交逐行处理方法；
     * 同批别的行有毛病不连累好行——修正了旧实现"错一条整批返回 0、好行也不入库"以及行号被重排的毛病。
     */
    @Override
    public int submitBatch(String batchNo, List<TIsCheckRow> rows) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("批次号不许为空");
        }
        if (rows == null || rows.isEmpty()) {
            // 空批：0 行，绝不写"已核讫"（逐行处理方法里落 0/0/0 头表）。
            checkImportTx.run(batchNo.trim(), new ArrayList<CheckImportRow>(), null, "system", new Date());
            return 0;
        }

        List<CheckImportRow> raws = new ArrayList<>(rows.size());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        int rowNo = 0;
        for (TIsCheckRow r : rows) {
            rowNo++;
            CheckImportRow raw = new CheckImportRow();
            raw.setRowNo(rowNo);
            raw.setSiteNo(r.getSiteNo() == null ? "" : r.getSiteNo().trim());
            raw.setCheckTimeText(r.getCheckTime() == null ? "" : sdf.format(r.getCheckTime()));
            raw.setHazardCntText(r.getHazardCnt() == null ? "" : String.valueOf(r.getHazardCnt()));
            raw.setQtyText(r.getQty() == null ? "" : r.getQty().toPlainString());
            raws.add(raw);
        }

        CheckReceipt receipt = checkImportTx.run(batchNo.trim(), raws, null, "system", new Date());
        if (receipt.isOverLimit()) {
            // 单批行数超上限：整批不入库，沿用旧契约 -1。
            return -1;
        }
        if (receipt.isDuplicate()) {
            // 同批次号二次提交：头遍原样，本次 0 行写入。
            return 0;
        }
        return receipt.getStored();
    }

    @Override
    public CheckReceipt submitBatch(String batchNo, byte[] fileBytes, String fileName, String operator) {
        List<CheckImportRow> rows = CsvCheckFileReader.parse(fileBytes, fileName);
        return checkImportTx.run(batchNo == null ? null : batchNo.trim(),
                rows, fileName, operator, new Date());
    }

    @Override
    public CheckReceipt submitBatch(String batchNo, List<CheckImportRow> rows, String sourceFile, String operator) {
        return checkImportTx.run(batchNo == null ? null : batchNo.trim(),
                rows, sourceFile, operator, new Date());
    }

    @Override
    public List<TIsCheckRow> listErrors(String batchNo) {
        return this.isCheckRowMapper.selectList(new QueryWrapper<TIsCheckRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .eq("status", com.fc.v2.service.check.CheckConst.ST_REJECTED)
                .orderByAsc("row_no", "id"));
    }

    @Override
    public List<CheckError> listErrorDetails(String batchNo) {
        return checkImportTx.listErrors(batchNo);
    }

    @Override
    public List<TIsCheckRow> listRecords(String batchNo, Date checkDate, String siteNo) {
        return checkImportTx.listRecords(batchNo, checkDate, siteNo);
    }

    @Override
    public TIsCheckBatch getBatch(String batchNo) {
        return checkImportTx.getBatch(batchNo);
    }
}
