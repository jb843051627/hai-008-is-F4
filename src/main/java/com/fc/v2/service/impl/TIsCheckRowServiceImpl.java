package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIsCheckRowMapper;
import com.fc.v2.model.auto.TIsCheckRow;
import com.fc.v2.service.ITIsCheckRowService;
import com.fc.v2.service.check.BatchReceipt;
import com.fc.v2.service.check.CheckConst;
import com.fc.v2.service.check.CheckFileReader;
import com.fc.v2.service.check.CheckRowLine;
import com.fc.v2.service.check.CheckRowTx;

import javax.annotation.Resource;

/**
 * 执法检查记录导入核对行 Service业务层处理（batch-process 形状：整批提交）。
 *
 * 职责切分（同 TIsRectApplyServiceImpl 的范式）：本类只管读文件、传日子、回查台账；
 * 一批收不收、哪行进哪行退，全由 {@link CheckRowTx} 那个逐行处理方法说了算，
 * 错误清单行次、回执三个数以它给的回执为准，页面既不加行、也不动旧批。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TIsCheckRowServiceImpl implements ITIsCheckRowService {

    @Resource
    private TIsCheckRowMapper isCheckRowMapper;
    @Resource
    private CheckRowTx checkRowTx;

    @Override
    public TIsCheckRow selectTIsCheckRowById(Long id) {
        return this.isCheckRowMapper.selectById(id);
    }

    /**
     * 【原处理方法，签名不动】委托同名事务执行器；换一种递法（文件）在
     * {@link #importBatch} 同名下再叠一层。
     *
     * 锁加在本类（事务外）：{@link CheckRowTx#submitBatch} 经代理在锁内开启并提交事务，
     * 保证同批次号后到者一定在先来者事务提交之后才查"批次在不在"，不会双写。
     */
    @Override
    public int submitBatch(String batchNo, List<TIsCheckRow> rows) {
        String no = batchNo;
        if ((no == null || no.trim().isEmpty()) && rows != null && !rows.isEmpty()) {
            no = rows.get(0).getBatchNo();
        }
        if (no == null || no.trim().isEmpty()) {
            throw new IllegalArgumentException("批次号不能为空：一批进门先登记批次号统住全批");
        }
        synchronized (checkRowTx.lockOf(no.trim())) {
            return checkRowTx.submitBatch(batchNo, rows);
        }
    }

    @Override
    public BatchReceipt importBatch(String batchNo, byte[] fileBytes, String filename, String operator) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("批次号不能为空：一批进门先登记个批次号统住全批");
        }
        String no = batchNo.trim();
        // 进门第一步：按原文件先后把行次定死（表头不算，第一行数据=1），之后谁查都照这个号。
        List<CheckRowLine> lines = CheckFileReader.read(fileBytes, filename);
        // 锁罩住整个事务（含提交）：同号重传/双击一批一批办，后到者看库说话、只认头一遍。
        synchronized (checkRowTx.lockOf(no)) {
            return checkRowTx.submitBatch(no, lines, operator, new Date());
        }
    }

    @Override
    public List<TIsCheckRow> listErrors(String batchNo) {
        return this.isCheckRowMapper.selectList(new QueryWrapper<TIsCheckRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .eq("status", CheckConst.ST_FAIL)
                .orderByAsc("row_no", "id"));
    }

    @Override
    public List<TIsCheckRow> listBatch(String batchNo) {
        return this.isCheckRowMapper.selectList(new QueryWrapper<TIsCheckRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .orderByAsc("row_no", "id"));
    }

    @Override
    public List<TIsCheckRow> searchFormal(String batchNo, String siteNo, Date checkDate) {
        QueryWrapper<TIsCheckRow> qw = new QueryWrapper<TIsCheckRow>()
                .eq("del_flag", 0)
                .eq("status", CheckConst.ST_OK)
                // 同企业并入的后头行不算正式记录：只数 merge_into 为空的前头行。
                .isNull("merge_into");
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            qw.eq("batch_no", batchNo.trim());
        }
        if (siteNo != null && !siteNo.trim().isEmpty()) {
            qw.eq("site_no", siteNo.trim());
        }
        if (checkDate != null) {
            qw.eq("check_date", checkDate);
        }
        qw.orderByDesc("check_date", "id");
        return this.isCheckRowMapper.selectList(qw);
    }
}
