package com.fc.v2.service;

import com.fc.v2.model.auto.TIsRectApply;
import com.fc.v2.model.auto.TIsRectLevel;
import com.fc.v2.model.auto.TIsRectSign;
import com.fc.v2.model.auto.TIsRectUrge;
import com.fc.v2.service.rect.SignCmd;
import com.fc.v2.service.rect.UrgeScreen;

import java.util.List;
import java.util.Map;

/**
 * 隐患整改申请逐级签批 Service 接口。
 *
 * 申请单字段固定、状态五档；逐级签收、四动作；作废只两入口；结案写死。
 *
 * @author fuce
 * @date 2026-10-02
 */
public interface ITIsRectApplyService {

    /** 建未提交草稿：关联隐患（哪条、什么级别）落单后不许改 */
    TIsRectApply draft(Long hazardId, Integer hazardLevel, String remark, String applicant);

    /** 申请人提交（首轮）或补齐材料后重新提交（自动从第一级重头走） */
    TIsRectApply submit(Long id, String supplementNote, String applicant);

    /** 当前层级签收：通过/驳回/补充材料/转交他级；cmd.operator 为实际落笔者 */
    TIsRectApply sign(SignCmd cmd);

    /** 申请人在单子进任何一级签署前撤回作废 */
    TIsRectApply withdraw(Long id, String applicant);

    /** 管理员对已结案单归档作废（结论与结案时间原样封死） */
    TIsRectApply archive(Long id, String operator);

    /** 按主键回查 */
    TIsRectApply selectById(Long id);

    /** 台账：全部状态都翻得到（含已作废，作废单只是不进待办与统计） */
    List<TIsRectApply> listLedger(Integer status, String applyNo);

    /** 我的待签：只返回审批中且当前待签层级恰好在当前登录人签收角色内的单 */
    List<TIsRectApply> myTodo(String username);

    /** 我提交的单（含未提交草稿） */
    List<TIsRectApply> myApplied(String applicant);

    /** 单张全貌：单子 + 逐级签署意见 + 催办留痕 */
    Map<String, Object> detail(Long id);

    /** 一张单的全部签署意见（按时刻） */
    List<TIsRectSign> listSigns(Long applyId);

    /** 一张单的全部催办留痕（按时刻） */
    List<TIsRectUrge> listUrges(Long applyId);

    /** 待办/统计点算：已作废不参与；返回各状态单数与当前用户待签数 */
    Map<String, Object> stats(String username);

    /** 定时/手工催办扫描，返回一屏结果 */
    UrgeScreen urgeScan();

    // ---------- 层级配置（平台写死） ----------

    /** 启用链条（status=0 按 level_no 升序），流转永远现取 */
    List<TIsRectLevel> listActiveLevels();

    /** 配置台账：启用停用都翻得到 */
    List<TIsRectLevel> listAllLevels();

    /** 登记一级 */
    TIsRectLevel addLevel(TIsRectLevel level);

    /** 停用一级：有在途单还挂在这级时拒停 */
    TIsRectLevel retireLevel(Long id);
}
