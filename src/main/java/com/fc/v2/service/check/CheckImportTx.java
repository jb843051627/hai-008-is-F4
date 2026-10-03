package com.fc.v2.service.check;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TIsCheckBatchMapper;
import com.fc.v2.mapper.auto.TIsCheckRowMapper;
import com.fc.v2.mapper.auto.TIsEnterpriseMapper;
import com.fc.v2.model.auto.TIsCheckBatch;
import com.fc.v2.model.auto.TIsCheckRow;
import com.fc.v2.model.auto.TIsEnterprise;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 执法检查记录整批导入的逐行处理方法——一批收不收、哪行进哪行退，全由本类说了算；
 * 错误清单行次、回执三个数只认本类给的回执，页面既不加行也不动旧批。
 *
 * 规矩（同一事务内办成，任一处对不上整批回滚、库里不留半截）：
 * 1. 进门先按批次号登头表统住全批；同号二次上传库里只留头一遍，后到的不覆盖、不补写驳回原因。
 * 2. 超单批上限整批退掉、一行不留（连头表都不登，改好行数可拿同号重传）。
 * 3. 空批照登 0/0/0，回执话术绝不许写"已核讫"。
 * 4. 一行归一行验：驳回行记"行号+类别码+一句原由"，别行照走；行号进门定死不重排。
 * 5. 同企业横跨多行的并成一条正式记录：得分只算一次、行次认前头那条；被并行照入库留痕、
 *    merge_head_row_no 指头行，单列正式记录与各档计数都不算它。
 * 6. 三数以库里逐行点算为准：入库（含被并行）+ 驳回 == 全批，不成立当场抛错回滚。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Component
public class CheckImportTx {

    @Resource
    private TIsCheckRowMapper rowMapper;
    @Resource
    private TIsCheckBatchMapper batchMapper;
    @Resource
    private TIsEnterpriseMapper enterpriseMapper;

    /**
     * 文件已由 {@link CsvCheckFileReader} 解成行、行号已按原文件先后定死，进本方法逐行处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public CheckReceipt run(String batchNo, List<CheckImportRow> raws,
                            String sourceFile, String operator, Date at) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("批次号不许为空");
        }
        if (raws == null) {
            throw new IllegalArgumentException("批内数据行集合不许为 null");
        }
        String no = batchNo.trim();

        // 1) 重复批次：只指头一遍，本次一行不写、旧批驳回原因一个字不补。
        TIsCheckBatch prior = findBatch(no);
        if (prior != null) {
            return receiptFromPrior(prior, "批次号 " + no + " 已交过一遍，库里只留头一遍；"
                    + "后交的不覆盖、不补录。要改得分请另起批次号走流程，别在原表上涂改");
        }

        // 2) 超上限：整批退掉、一行不留。
        if (raws.size() > CheckConst.MAX_ROWS_PER_BATCH) {
            String note = "全批 " + raws.size() + " 行，超过单批上限 "
                    + CheckConst.MAX_ROWS_PER_BATCH + " 行，整批退回、一行未入库；请拆批后重新上传";
            return CheckReceipt.overLimit(no, raws.size(), CheckConst.MAX_ROWS_PER_BATCH, note);
        }

        // 3) 进门先登头表（唯一键兜底并发同号）。
        TIsCheckBatch head = new TIsCheckBatch();
        head.setBatchNo(no);
        head.setSourceFile(sourceFile);
        head.setReceivedAt(at);
        head.setCreateBy(operator);
        head.setCreateTime(at);
        head.setDelFlag(0);
        try {
            batchMapper.insert(head);
        } catch (DuplicateKeyException e) {
            // 两个请求同期撞同号：谁先插谁说了算，后来这一遍按重复回执，不写一行。
            TIsCheckBatch winner = findBatch(no);
            return receiptFromPrior(winner, "批次号 " + no + " 同期已被另一批先登记，本遍未写入一行");
        }

        // 4) 空批：0/0/0 照登，话术不写"已核讫"。
        if (raws.isEmpty()) {
            String note = "本批没有一条数据行（空文件或只有表头）：0 行进、0 行退，"
                    + "未形成任何检查记录，不得按已清完论处";
            finishBatch(head, 0, 0, 0, note, at);
            return CheckReceipt.accepted(no, 0, 0, 0, 0, 0,
                    new ArrayList<CheckError>(), note);
        }

        // 5) 企业底档一次查对，整批复用这一份名册。
        Set<String> knownSiteNos = loadKnownSiteNos();

        List<CheckError> errors = new ArrayList<>();
        // 已通过行里每家企业的合并头行（行次最小的那条）。
        Map<String, TIsCheckRow> headBySite = new HashMap<>();
        int stored = 0;
        int rejected = 0;
        int merged = 0;

        for (CheckImportRow raw : raws) {
            final TIsCheckRow row = newBaseRow(no, raw.getRowNo(), operator, at);
            final boolean[] accepted = {false};
            CheckError err = CheckRowValidator.validate(raw,
                    new CheckRowValidator.TIsCheckRowLike() {
                        @Override
                        public void accept(String siteNo, Date checkTime, Integer hazardCnt, BigDecimal qty) {
                            row.setSiteNo(siteNo);
                            row.setCheckTime(checkTime);
                            row.setHazardCnt(hazardCnt);
                            row.setQty(qty);
                            accepted[0] = true;
                        }
                    }, knownSiteNos);

            if (err != null) {
                // 驳回：类别码 + 一句原由一次写死，之后再无第二条更新语句碰它。
                row.setStatus(CheckConst.ST_REJECTED);
                row.setItemCode(err.getItemCode());
                row.setRemark(err.getReason());
                rowMapper.insert(row);
                rejected++;
                errors.add(err);
                continue;
            }

            TIsCheckRow first = headBySite.get(row.getSiteNo());
            if (first == null) {
                // 该企业本批头一条：成正式检查记录，得分就这一次。
                row.setStatus(CheckConst.ST_STORED);
                rowMapper.insert(row);
                headBySite.put(row.getSiteNo(), row);
                stored++;
            } else {
                // 同企业横跨多行（上月一条、下月又一条）：并入前头那条，行次认前头。
                row.setStatus(CheckConst.ST_STORED);
                row.setMergeHeadRowNo(first.getRowNo());
                rowMapper.insert(row);
                merged++;
                stored++;
                String mergedList = first.getMergedRowNo() == null
                        ? String.valueOf(row.getRowNo())
                        : first.getMergedRowNo() + "," + row.getRowNo();
                // 只回写 merged_row_no 一栏，头行得分/各数据列一个不动。
                rowMapper.update(null, new UpdateWrapper<TIsCheckRow>()
                        .eq("id", first.getId())
                        .set("merged_row_no", mergedList)
                        .set("update_time", at));
                first.setMergedRowNo(mergedList);
            }
        }

        int total = raws.size();
        int recordCnt = headBySite.size();

        // 6) 三数守恒：以本事务逐行点算为准，对不上当场抛错整批回滚，绝不带错出回执。
        if (stored + rejected != total) {
            throw new IllegalStateException("逐行点算对不上：入库" + stored + "+驳回" + rejected
                    + "≠全批" + total + "，整批回滚");
        }

        String note;
        if (rejected == 0) {
            note = "全批 " + total + " 行已逐行核讫：入库 " + stored + " 行"
                    + (merged > 0 ? "（同企业并记 " + merged + " 行，正式检查记录 " + recordCnt + " 条）" : "")
                    + "，驳回 0 行";
        } else {
            note = "全批 " + total + " 行：入库 " + stored + " 行"
                    + (merged > 0 ? "（含同企业并记 " + merged + " 行，正式检查记录 " + recordCnt + " 条）"
                    : "（正式检查记录 " + recordCnt + " 条）")
                    + "、驳回 " + rejected + " 行；驳回行照错误清单上的原表行号定位改妥，另起批次重传";
        }
        finishBatch(head, total, stored, rejected, note, at);
        return CheckReceipt.accepted(no, total, stored, rejected, recordCnt, merged, errors, note);
    }

    /** 该批驳回明细（行号升序），错误清单的唯一出处。 */
    public List<CheckError> listErrors(String batchNo) {
        List<TIsCheckRow> rows = rowMapper.selectList(new QueryWrapper<TIsCheckRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .eq("status", CheckConst.ST_REJECTED)
                .orderByAsc("row_no", "id"));
        List<CheckError> errors = new ArrayList<>();
        for (TIsCheckRow r : rows) {
            errors.add(new CheckError(r.getRowNo() == null ? -1 : r.getRowNo(),
                    r.getItemCode(), r.getRemark()));
        }
        return errors;
    }

    /** 正式检查记录：同企业并记后每企业一条（被并行不算），批次/检查日期/企业编号都查得回。 */
    public List<TIsCheckRow> listRecords(String batchNo, Date checkDate, String siteNo) {
        QueryWrapper<TIsCheckRow> qw = new QueryWrapper<TIsCheckRow>()
                .eq("del_flag", 0)
                .eq("status", CheckConst.ST_STORED)
                .isNull("merge_head_row_no")
                .orderByAsc("check_time", "id");
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            qw.eq("batch_no", batchNo.trim());
        }
        if (siteNo != null && !siteNo.trim().isEmpty()) {
            qw.eq("site_no", siteNo.trim());
        }
        if (checkDate != null) {
            Date next = new Date(checkDate.getTime() + 24L * 60 * 60 * 1000);
            qw.ge("check_time", checkDate).lt("check_time", next);
        }
        return rowMapper.selectList(qw);
    }

    /** 批次头（查回执三数用）。 */
    public TIsCheckBatch getBatch(String batchNo) {
        return findBatch(batchNo);
    }

    private void finishBatch(TIsCheckBatch head, int total, int stored, int rejected, String note, Date at) {
        // 显式逐栏回写：三数就是同一事务里逐行点算的那三个，不许由页面传值。
        int rows = batchMapper.update(null, new UpdateWrapper<TIsCheckBatch>()
                .eq("id", head.getId())
                .set("total_cnt", total)
                .set("stored_cnt", stored)
                .set("rejected_cnt", rejected)
                .set("receipt_note", note)
                .set("update_time", at));
        if (rows == 0) {
            throw new IllegalStateException("批次登记回写失败：" + head.getBatchNo());
        }
        head.setTotalCnt(total);
        head.setStoredCnt(stored);
        head.setRejectedCnt(rejected);
        head.setReceiptNote(note);
    }

    private CheckReceipt receiptFromPrior(TIsCheckBatch prior, String dupNote) {
        if (prior == null) {
            // 理论上走不到（唯一键撞了必查得到赢家），兜底保守按内部错抛出。
            throw new IllegalStateException("批次号撞号但查不到头遍批次");
        }
        // 三数以头批在库里的逐行点算为准，不信回执调用方嘴上报的数。
        int stored = countRows(prior.getBatchNo(), CheckConst.ST_STORED);
        int rejected = countRows(prior.getBatchNo(), CheckConst.ST_REJECTED);
        int total = nz(prior.getTotalCnt());
        if (stored + rejected != total) {
            throw new IllegalStateException("头遍批次 " + prior.getBatchNo()
                    + " 库内点算对不上：入库" + stored + "+驳回" + rejected + "≠全批" + total);
        }
        int recordCnt = rowMapper.selectCount(new QueryWrapper<TIsCheckRow>()
                .eq("batch_no", prior.getBatchNo())
                .eq("del_flag", 0)
                .eq("status", CheckConst.ST_STORED)
                .isNull("merge_head_row_no")).intValue();
        int merged = rowMapper.selectCount(new QueryWrapper<TIsCheckRow>()
                .eq("batch_no", prior.getBatchNo())
                .eq("del_flag", 0)
                .isNotNull("merge_head_row_no")).intValue();
        List<CheckError> errors = listErrors(prior.getBatchNo());
        String note = dupNote + "；头遍回执：全批" + total + "行、入库" + stored + "行、驳回" + rejected + "行";
        return CheckReceipt.duplicate(prior.getBatchNo(), total, stored, rejected,
                recordCnt, merged, errors, note);
    }

    private int countRows(String batchNo, int status) {
        return rowMapper.selectCount(new QueryWrapper<TIsCheckRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .eq("status", status)).intValue();
    }

    private TIsCheckBatch findBatch(String batchNo) {
        List<TIsCheckBatch> list = batchMapper.selectList(new QueryWrapper<TIsCheckBatch>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private Set<String> loadKnownSiteNos() {
        List<TIsEnterprise> list = enterpriseMapper.selectList(new QueryWrapper<TIsEnterprise>()
                .eq("del_flag", 0)
                .select("site_no"));
        Set<String> nos = new HashSet<>();
        for (TIsEnterprise e : list) {
            if (e.getSiteNo() != null && !e.getSiteNo().trim().isEmpty()) {
                nos.add(e.getSiteNo().trim());
            }
        }
        return nos;
    }

    private TIsCheckRow newBaseRow(String batchNo, int rowNo, String operator, Date at) {
        TIsCheckRow r = new TIsCheckRow();
        r.setBatchNo(batchNo);
        r.setRowNo(rowNo);
        r.setDelFlag(0);
        r.setCreateBy(operator);
        r.setCreateTime(at);
        return r;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
