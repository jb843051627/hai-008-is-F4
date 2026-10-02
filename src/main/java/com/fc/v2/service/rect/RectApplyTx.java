package com.fc.v2.service.rect;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIsRectApplyMapper;
import com.fc.v2.mapper.auto.TIsRectSignMapper;
import com.fc.v2.mapper.auto.TIsRectUrgeMapper;
import com.fc.v2.mapper.auto.TIsHazardMapper;
import com.fc.v2.model.auto.TIsHazard;
import com.fc.v2.model.auto.TIsRectApply;
import com.fc.v2.model.auto.TIsRectLevel;
import com.fc.v2.model.auto.TIsRectSign;
import com.fc.v2.model.auto.TIsRectUrge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 整改申请流转的事务执行器（仿 HazDispatchTx：单开 Bean，让 {@link Transactional} 经代理生效）。
 *
 * 每一个动作在同一事务内：先原子翻转主单（WHERE 钉死状态/层级/轮次，0 行即已被别人先推动），
 * 再落一条意见/事件留痕，两步缺一不可。结案列（status 终态、结论、结案时间）一旦写死，
 * 本类再无任何 SQL 会去碰它们——要查变化只查 t_is_rect_sign。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Component
public class RectApplyTx {

    @Resource
    private TIsRectApplyMapper applyMapper;
    @Resource
    private TIsRectSignMapper signMapper;
    @Resource
    private TIsRectUrgeMapper urgeMapper;
    @Resource
    private TIsHazardMapper hazardMapper;

    /** 建草稿（未提交）。关联隐患号抄存、隐患级别落单，之后谁都不许改这两栏。 */
    @Transactional(rollbackFor = Exception.class)
    public TIsRectApply draft(Long hazardId, Integer hazardLevel, String remark, String applicant, Date at) {
        if (hazardId == null) {
            throw new IllegalArgumentException("必须关联一条隐患");
        }
        if (hazardLevel == null || hazardLevel < 1 || hazardLevel > 3) {
            throw new IllegalArgumentException("隐患级别只许 1一级 2二级 3三级");
        }
        TIsHazard h = hazardMapper.selectById(hazardId);
        if (h == null || (h.getDelFlag() != null && h.getDelFlag() == 1)) {
            throw new IllegalArgumentException("关联隐患不存在：" + hazardId);
        }
        TIsRectApply a = new TIsRectApply();
        a.setApplyNo(nextApplyNo(at));
        a.setHazardId(h.getId());
        a.setHazardNo(h.getHazardNo());
        a.setHazardLevel(hazardLevel);
        a.setApplicant(applicant);
        a.setStatus(RectConst.ST_DRAFT);
        a.setRoundNo(1);
        a.setRemark(remark);
        a.setCreateTime(at);
        a.setUpdateTime(at);
        applyMapper.insert(a);
        return a;
    }

    /**
     * 提交（首轮提交或补齐后重新提交）。
     * 首轮：库里从无提交事件；重提：上一轮必须有 SUPPLEMENT 记录，且重提交给第一级、轮次加一。
     */
    @Transactional(rollbackFor = Exception.class)
    public TIsRectApply submit(Long id, String supplementNote, String operator, RectChain chain, Date at) {
        if (chain.isEmpty()) {
            throw new IllegalStateException("平台还没配置任何启用的签批层级，单子无处可送");
        }
        TIsRectApply a = mustGet(id);
        if (a.getStatus() == null || a.getStatus() != RectConst.ST_DRAFT) {
            throw new IllegalStateException("只有未提交的单子能提交，当前状态：" + RectConst.statusText(a.getStatus()));
        }
        if (!operator.equals(a.getApplicant())) {
            throw new IllegalStateException("只有申请人本人能提交这张单");
        }
        int round = a.getRoundNo() == null ? 1 : a.getRoundNo();
        boolean everSubmitted = signMapper.selectCount(new QueryWrapper<TIsRectSign>()
                .eq("apply_id", id)
                .in("action", RectConst.EVT_SUBMIT, RectConst.EVT_RESUBMIT)) > 0;

        int resubmit;
        String event;
        if (!everSubmitted) {
            resubmit = 0;
            event = RectConst.EVT_SUBMIT;
        } else {
            // 回到未提交只可能是被要求补材料；没有 SUPPLEMENT 记录的重提是异常路径，拒掉。
            int sup = signMapper.selectCount(new QueryWrapper<TIsRectSign>()
                    .eq("apply_id", id)
                    .eq("round_no", round)
                    .eq("action", RectConst.ACT_SUPPLEMENT));
            if (sup == 0) {
                throw new IllegalStateException("这张单不是补材料退回状态，不允许重新提交");
            }
            resubmit = 1;
            event = RectConst.EVT_RESUBMIT;
        }

        if (supplementNote != null && !supplementNote.trim().isEmpty()) {
            // 补齐说明在翻转前（仍未提交时）写进备注；隐患三栏（哪条/号/级别）始终不许动。
            TIsRectApply upd = new TIsRectApply();
            upd.setId(id);
            upd.setRemark(supplementNote);
            applyMapper.updateById(upd);
        }
        int rows = applyMapper.submit(id, chain.first().getLevelNo(), resubmit, at);
        if (rows == 0) {
            throw new IllegalStateException("提交未生效：单子已被先动过，请刷新重试");
        }
        TIsRectApply saved = mustGet(id);
        // 提交事件不挂层级（层级意见只属于真正签收的那一级）。
        signMapper.insert(event(saved, null, event, operator, operator, 0, null, null, null,
                resubmit == 1 ? "补齐材料后重新提交，从第一级重头走起" : "提交申请，进第一级签批", at));
        return saved;
    }

    /**
     * 签收：四动作。调用方（Service）已验过当前登录人持有本层签收角色，这里只管业务落库。
     * 每一级签的是自己这级的意见：四种动作都必须带非空意见；转交还须把本级意见记全。
     */
    @Transactional(rollbackFor = Exception.class)
    public TIsRectApply sign(SignCmd cmd, RectChain chain, Date at) {
        TIsRectApply a = mustGet(cmd.getId());
        if (a.getStatus() == null || a.getStatus() != RectConst.ST_RUNNING) {
            throw new IllegalStateException("只有审批中的单子能签收，当前状态：" + RectConst.statusText(a.getStatus()));
        }
        int curNo = a.getCurLevelNo() == null ? -1 : a.getCurLevelNo();
        TIsRectLevel cur = chain.byNo(curNo);
        if (cur == null) {
            throw new IllegalStateException("当前待签层级已停用或不存在，签收中止，等平台配置");
        }
        int round = a.getRoundNo() == null ? 1 : a.getRoundNo();
        String comment = cmd.getComment() == null ? "" : cmd.getComment().trim();
        if (comment.isEmpty()) {
            throw new IllegalStateException("签收必须写下本级意见，不许空着落印");
        }

        // 代办三栏：代办人、原签署人、代办原因；代办不改层级归属。
        boolean agent = isAgent(cmd.getOperator(), cmd.getIntendedSigner());
        String intended = agent ? cmd.getIntendedSigner().trim() : cmd.getOperator();
        String agentReason = null;
        if (agent) {
            if (cmd.getAgentReason() == null || cmd.getAgentReason().trim().isEmpty()) {
                throw new IllegalStateException("代办必须写明代办原因");
            }
            agentReason = cmd.getAgentReason().trim();
        }

        String action = cmd.getAction();
        int rows;
        switch (action == null ? "" : action) {
            case RectConst.ACT_PASS: {
                TIsRectLevel next = chain.nextOf(curNo);
                if (next == null) {
                    rows = applyMapper.passClose(a.getId(), curNo, round, at);
                } else {
                    rows = applyMapper.passAdvance(a.getId(), curNo, round, next.getLevelNo(), at);
                }
                break;
            }
            case RectConst.ACT_REJECT:
                // 驳回直接落已驳回到此终止，不许再留在流程里。
                rows = applyMapper.rejectClose(a.getId(), curNo, round, at);
                break;
            case RectConst.ACT_SUPPLEMENT:
                // 退回申请人；轮次不在这里加，重提时才加一，重走第一级。
                rows = applyMapper.backToApplicant(a.getId(), curNo, round, at);
                break;
            case RectConst.ACT_TRANSFER: {
                rows = doTransfer(a, cur, round, cmd, comment, chain, at);
                break;
            }
            default:
                throw new IllegalArgumentException("签收动作只许 通过/驳回/补充材料/转交他级：" + action);
        }
        if (rows == 0) {
            throw new IllegalStateException("签收未生效：单子已被别人先推动，请刷新重试");
        }

        TIsRectLevel target = RectConst.ACT_TRANSFER.equals(action)
                ? chain.byNo(cmd.getTargetLevelNo()) : null;
        signMapper.insert(event(mustGet(a.getId()), cur, action, cmd.getOperator(), intended,
                agent ? 1 : 0, agentReason,
                target == null ? null : target.getLevelNo(),
                target == null ? null : target.getLevelName(),
                comment, at));
        return mustGet(a.getId());
    }

    /** 转交校验：目标级必须是链条上启用的、且不是本级；本级意见不许只留一句"转走了"。 */
    private int doTransfer(TIsRectApply a, TIsRectLevel cur, int round, SignCmd cmd,
                           String comment, RectChain chain, Date at) {
        if (cmd.getTargetLevelNo() == null) {
            throw new IllegalStateException("转交他级必须指明转到哪一级");
        }
        TIsRectLevel target = chain.byNo(cmd.getTargetLevelNo());
        if (target == null) {
            throw new IllegalStateException("转交目标层级不存在或已停用，单子不许挂空");
        }
        if (cmd.getTargetLevelNo().equals(cur.getLevelNo())) {
            throw new IllegalStateException("不许转交给本级自己");
        }
        if (comment.length() < 4 || "转走了".equals(comment)) {
            throw new IllegalStateException("转交他级必须把本级的处理意见记全，不能只留一句\"转走了\"");
        }
        return applyMapper.transferTo(a.getId(), cur.getLevelNo(), round, target.getLevelNo(), at);
    }

    /**
     * 申请人撤回作废：只许在单子还没进任何一级签署前——即从未提交过的草稿。
     * 补材料退回后的单子已经进过签署，只许补齐重提或等下一级驳回，不撤。
     */
    @Transactional(rollbackFor = Exception.class)
    public TIsRectApply withdraw(Long id, String operator, Date at) {
        TIsRectApply a = mustGet(id);
        if (!operator.equals(a.getApplicant())) {
            throw new IllegalStateException("只有申请人本人能撤自己的单");
        }
        if (a.getStatus() == null || a.getStatus() != RectConst.ST_DRAFT) {
            throw new IllegalStateException("只有未提交的单子能撤回，当前状态：" + RectConst.statusText(a.getStatus()));
        }
        long submitted = signMapper.selectCount(new QueryWrapper<TIsRectSign>()
                .eq("apply_id", id)
                .in("action", RectConst.EVT_SUBMIT, RectConst.EVT_RESUBMIT));
        if (submitted > 0) {
            throw new IllegalStateException("单子已进过签批层级，中途不许撤回作废；有异议请走驳回或补材料");
        }
        int rows = applyMapper.withdraw(id, at);
        if (rows == 0) {
            throw new IllegalStateException("撤回未生效：单子已被先动过");
        }
        signMapper.insert(event(mustGet(id), null, RectConst.EVT_WITHDRAW, operator, operator,
                0, null, null, null, "申请人在进入任何一级签署前撤回作废", at));
        return mustGet(id);
    }

    /** 管理员归档：只许对已结案（通过/驳回）的单；结论与结案时间不在更新列里，原样封死。 */
    @Transactional(rollbackFor = Exception.class)
    public TIsRectApply archive(Long id, String operator, Date at) {
        TIsRectApply a = mustGet(id);
        if (a.getStatus() == null || (a.getStatus() != RectConst.ST_PASSED
                && a.getStatus() != RectConst.ST_REJECTED)) {
            throw new IllegalStateException("只有已结案的单子能归档作废，当前状态：" + RectConst.statusText(a.getStatus()));
        }
        int rows = applyMapper.archive(id, at);
        if (rows == 0) {
            throw new IllegalStateException("归档未生效：单子状态已变");
        }
        signMapper.insert(event(mustGet(id), null, RectConst.EVT_ARCHIVE, operator, operator,
                0, null, null, null, "结案后管理员归档；结论与结案时间原样封死", at));
        return mustGet(id);
    }

    /**
     * 层级超时催办扫描：只扫审批中单子；卡在某一层超过该层 urge_hours 就催一次并留痕，
     * 同一轮同一级只催一次（uk + 先查）。作废/结案/未提交的单一律扫不到。
     */
    @Transactional(rollbackFor = Exception.class)
    public UrgeScreen urgeScan(RectChain chain, Date at) {
        List<TIsRectApply> running = applyMapper.selectList(new QueryWrapper<TIsRectApply>()
                .eq("del_flag", 0)
                .eq("status", RectConst.ST_RUNNING)
                .isNotNull("cur_level_no"));
        UrgeScreen screen = new UrgeScreen();
        screen.setRunningCnt(running.size());
        int urged = 0;
        int within = 0;
        for (TIsRectApply a : running) {
            int levelNo = a.getCurLevelNo();
            int round = a.getRoundNo() == null ? 1 : a.getRoundNo();
            Integer limitHours = chain.urgeHoursOf(levelNo);
            long used = a.getLevelEnteredAt() == null ? 0L
                    : (at.getTime() - a.getLevelEnteredAt().getTime()) / (60L * 60 * 1000);
            if (used < limitHours) {
                within++;
                continue;
            }
            // 去重：本轮本级已有催办，且"本次进层时刻"不晚于上次催办，说明催过之后一直没动——不重复催。
            // 转交出去又转回来会刷新进层时刻（level_entered_at），那次卡住超时仍要再催。
            List<TIsRectUrge> prior = urgeMapper.selectList(new QueryWrapper<TIsRectUrge>()
                    .eq("apply_id", a.getId())
                    .eq("round_no", round)
                    .eq("level_no", levelNo)
                    .orderByDesc("urged_at", "id")
                    .last("limit 1"));
            if (!prior.isEmpty()
                    && a.getLevelEnteredAt() != null
                    && !a.getLevelEnteredAt().after(prior.get(0).getUrgedAt())) {
                within++;
                continue;
            }
            TIsRectLevel lvl = chain.byNo(levelNo);
            TIsRectUrge u = new TIsRectUrge();
            u.setApplyId(a.getId());
            u.setApplyNo(a.getApplyNo());
            u.setRoundNo(round);
            u.setLevelNo(levelNo);
            u.setLevelName(lvl == null ? ("第" + levelNo + "级") : lvl.getLevelName());
            u.setOverdueHours((int) used);
            u.setUrgedAt(at);
            u.setDetail("申请单[" + a.getApplyNo() + "]卡在" + u.getLevelName()
                    + "已 " + used + " 小时（时限 " + limitHours + " 小时），请尽快签收");
            u.setCreateBy("Task");
            u.setCreateTime(at);
            urgeMapper.insert(u);
            urged++;
        }
        screen.setUrgedCnt(urged);
        screen.setWithinCnt(within);
        screen.setMessage("催办扫描：审批中 " + running.size() + " 张，新催办 " + urged
                + " 张，时限内（含已催过）" + within + " 张");
        return screen;
    }

    private TIsRectApply mustGet(Long id) {
        TIsRectApply a = applyMapper.selectById(id);
        if (a == null || (a.getDelFlag() != null && a.getDelFlag() == 1)) {
            throw new IllegalArgumentException("申请单不存在：" + id);
        }
        return a;
    }

    private boolean isAgent(String operator, String intended) {
        return intended != null && !intended.trim().isEmpty() && !intended.trim().equals(operator);
    }

    private TIsRectSign event(TIsRectApply a, TIsRectLevel level, String action,
                              String signer, String intended, int agentFlag, String agentReason,
                              Integer targetLevelNo, String targetLevelName,
                              String comment, Date at) {
        TIsRectSign s = new TIsRectSign();
        s.setApplyId(a.getId());
        s.setApplyNo(a.getApplyNo());
        s.setRoundNo(a.getRoundNo());
        s.setLevelNo(level == null ? null : level.getLevelNo());
        s.setLevelName(level == null ? null : level.getLevelName());
        s.setAction(action);
        s.setSigner(signer);
        s.setIntendedSigner(intended);
        s.setAgentFlag(agentFlag);
        s.setAgentReason(agentReason);
        s.setTargetLevelNo(targetLevelNo);
        s.setTargetLevelName(targetLevelName);
        s.setComment(comment);
        s.setSignTime(at);
        s.setCreateBy(signer);
        s.setCreateTime(at);
        return s;
    }

    private String nextApplyNo(Date at) {
        long seq = applyMapper.selectCount(new QueryWrapper<TIsRectApply>()
                .likeRight("apply_no", "ZG" + new SimpleDateFormat("yyyyMMdd").format(at)));
        return "ZG" + new SimpleDateFormat("yyyyMMdd").format(at)
                + "-" + String.format("%04d", seq + 1);
    }
}
