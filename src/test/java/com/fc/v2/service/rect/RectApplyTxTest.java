package com.fc.v2.service.rect;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIsHazardMapper;
import com.fc.v2.mapper.auto.TIsRectApplyMapper;
import com.fc.v2.mapper.auto.TIsRectSignMapper;
import com.fc.v2.mapper.auto.TIsRectUrgeMapper;
import com.fc.v2.model.auto.TIsHazard;
import com.fc.v2.model.auto.TIsRectApply;
import com.fc.v2.model.auto.TIsRectLevel;
import com.fc.v2.model.auto.TIsRectSign;
import com.fc.v2.model.auto.TIsRectUrge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 整改申请逐级签批流转规则单测（纯 Mockito，不连库）。
 *
 * @author fuce
 * @date 2026-10-02
 */
class RectApplyTxTest {

    @Mock
    private TIsRectApplyMapper applyMapper;
    @Mock
    private TIsRectSignMapper signMapper;
    @Mock
    private TIsRectUrgeMapper urgeMapper;
    @Mock
    private TIsHazardMapper hazardMapper;
    @InjectMocks
    private RectApplyTx tx;

    private final Date now = new Date(1_780_000_000_000L);
    private TIsRectApply a;
    private RectChain chain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        a = new TIsRectApply();
        a.setId(100L);
        a.setApplyNo("ZG20261002-0001");
        a.setHazardId(1L);
        a.setHazardNo("YH01");
        a.setHazardLevel(2);
        a.setApplicant("zhangsan");
        a.setStatus(RectConst.ST_DRAFT);
        a.setRoundNo(1);
        a.setDelFlag(0);
        when(applyMapper.selectById(100L)).thenReturn(a);
        when(signMapper.selectCount(any(QueryWrapper.class))).thenReturn(0);

        TIsRectLevel l1 = level(1, "班组提报", 10L);
        TIsRectLevel l2 = level(2, "安环科审核", 20L);
        TIsRectLevel l3 = level(3, "分管领导审批", 30L);
        chain = new RectChain(Arrays.asList(l3, l1, l2));
    }

    private TIsRectLevel level(int no, String name, long roleId) {
        TIsRectLevel l = new TIsRectLevel();
        l.setLevelNo(no);
        l.setLevelName(name);
        l.setSignerRoleId(roleId);
        l.setUrgeHours(48);
        l.setStatus(0);
        return l;
    }

    private SignCmd cmd(String action, String comment) {
        SignCmd c = new SignCmd();
        c.setId(100L);
        c.setAction(action);
        c.setOperator("signer1");
        c.setComment(comment);
        return c;
    }

    @Test
    void chainOrderAndNext() {
        assertEquals(Integer.valueOf(1), chain.first().getLevelNo());
        assertEquals(Integer.valueOf(2), chain.nextOf(1).getLevelNo());
        assertEquals(Integer.valueOf(3), chain.nextOf(2).getLevelNo());
        assertNull(chain.nextOf(3), "末级之后无下一级（末级通过即结案）");
        assertTrue(chain.visibleLevelNos(toSet(10L, 30L)).contains(1));
        assertTrue(chain.visibleLevelNos(toSet(10L, 30L)).contains(3));
        assertEquals(0, chain.visibleLevelNos(toSet(99L)).size());
    }

    private java.util.Set<Long> toSet(Long... ids) {
        return new java.util.HashSet<>(Arrays.asList(ids));
    }

    @Test
    void submitThenPassThroughToLastLevelCloses() {
        when(applyMapper.submit(anyLong(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            a.setStatus(RectConst.ST_RUNNING);
            a.setCurLevelNo(inv.getArgument(1));
            return 1;
        });
        tx.submit(100L, null, "zhangsan", chain, now);
        assertEquals(1, a.getCurLevelNo());
        verify(signMapper).insert(argEvent(RectConst.EVT_SUBMIT));

        // 第一级通过 → 第二级
        a.setStatus(RectConst.ST_RUNNING);
        a.setCurLevelNo(1);
        when(applyMapper.passAdvance(anyLong(), anyInt(), anyInt(), anyInt(), any())).thenReturn(1);
        when(applyMapper.passClose(anyLong(), anyInt(), anyInt(), any())).thenReturn(1);
        tx.sign(cmd(RectConst.ACT_PASS, "班组核实属实，同意"), chain, now);
        verify(applyMapper).passAdvance(100L, 1, 1, 2, now);

        // 第二级通过 → 第三级
        a.setCurLevelNo(2);
        tx.sign(cmd(RectConst.ACT_PASS, "安环科同意"), chain, now);
        verify(applyMapper).passAdvance(100L, 2, 1, 3, now);

        // 末级通过 → 结案，不再有下一级
        a.setCurLevelNo(3);
        cmd(RectConst.ACT_PASS, "同意整改结论").setOperator("boss");
        SignCmd last = cmd(RectConst.ACT_PASS, "同意整改结论");
        last.setOperator("boss");
        tx.sign(last, chain, now);
        verify(applyMapper).passClose(100L, 3, 1, now);
        verify(applyMapper, never()).rejectClose(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void rejectClosesImmediatelyAndLeavesNoNextLevel() {
        a.setStatus(RectConst.ST_RUNNING);
        a.setCurLevelNo(1);
        when(applyMapper.rejectClose(anyLong(), anyInt(), anyInt(), any())).thenReturn(1);
        tx.sign(cmd(RectConst.ACT_REJECT, "情况不实，驳回"), chain, now);
        verify(applyMapper).rejectClose(100L, 1, 1, now);
        verify(applyMapper, never()).passAdvance(anyLong(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void supplementThenResubmitRestartsFromFirstLevelAndBumpsRound() {
        // 第一级要求补材料 → 退回申请人（未提交），轮次不加
        a.setStatus(RectConst.ST_RUNNING);
        a.setCurLevelNo(1);
        when(applyMapper.backToApplicant(anyLong(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            a.setStatus(RectConst.ST_DRAFT);
            a.setCurLevelNo(null);
            return 1;
        });
        tx.sign(cmd(RectConst.ACT_SUPPLEMENT, "缺整改照片，补齐再报"), chain, now);
        verify(applyMapper).backToApplicant(100L, 1, 1, now);

        // 重提：库里已有一次 SUBMIT 且本轮有 SUPPLEMENT → 轮次加一、回第一级
        when(signMapper.selectCount(any(QueryWrapper.class))).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            String sql = qw.getSqlSegment() == null ? "" : qw.getSqlSegment();
            // everSubmitted 查 action in (...)；SUPPLEMENT 查 action = #{...}。用片段粗分。
            return sql.contains("IN") ? 1 : 1;
        });
        when(applyMapper.submit(anyLong(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            a.setStatus(RectConst.ST_RUNNING);
            a.setCurLevelNo(inv.getArgument(1));
            a.setRoundNo(2);
            return 1;
        });
        tx.submit(100L, "照片已补", "zhangsan", chain, now);
        // resubmit 实参为 1，且必须回到第一级（不是从原处第二级接）
        verify(applyMapper).submit(100L, 1, 1, now);
        assertEquals(Integer.valueOf(2), a.getRoundNo());
        verify(signMapper).insert(argEvent(RectConst.EVT_RESUBMIT));
    }

    @Test
    void resubmitWithoutSupplementRecordIsRejected() {
        a.setStatus(RectConst.ST_DRAFT);
        a.setCurLevelNo(null);
        // 已提交过（1），但本轮没有 SUPPLEMENT（0）
        when(signMapper.selectCount(any(QueryWrapper.class))).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            return (qw.getSqlSegment() != null && qw.getSqlSegment().contains("IN")) ? 1 : 0;
        });
        assertThrows(IllegalStateException.class,
                () -> tx.submit(100L, null, "zhangsan", chain, now));
        verify(applyMapper, never()).submit(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void signRequiresComment() {
        a.setStatus(RectConst.ST_RUNNING);
        a.setCurLevelNo(1);
        SignCmd c = cmd(RectConst.ACT_PASS, "   ");
        assertThrows(IllegalStateException.class, () -> tx.sign(c, chain, now));
        verify(applyMapper, never()).passAdvance(anyLong(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void transferMustRecordFullOpinionAndValidTarget() {
        a.setStatus(RectConst.ST_RUNNING);
        a.setCurLevelNo(1);
        // 只留一句"转走了"：拒
        assertThrows(IllegalStateException.class,
                () -> tx.sign(cmd(RectConst.ACT_TRANSFER, "转走了"), chain, now));

        // 转给自己这级：拒
        SignCmd self = cmd(RectConst.ACT_TRANSFER, "本级情况复杂，建议上级先行研判");
        self.setTargetLevelNo(1);
        assertThrows(IllegalStateException.class, () -> tx.sign(self, chain, now));

        // 转到停用级：拒
        SignCmd bad = cmd(RectConst.ACT_TRANSFER, "本级情况复杂，建议上级先行研判");
        bad.setTargetLevelNo(9);
        assertThrows(IllegalStateException.class, () -> tx.sign(bad, chain, now));

        // 转到有效级：落 transferTo，留痕带目标级
        when(applyMapper.transferTo(anyLong(), anyInt(), anyInt(), anyInt(), any())).thenReturn(1);
        SignCmd ok = cmd(RectConst.ACT_TRANSFER, "本级情况复杂，建议安环科先行研判后退回");
        ok.setTargetLevelNo(2);
        tx.sign(ok, chain, now);
        verify(applyMapper).transferTo(100L, 1, 1, 2, now);
        ArgumentCaptor<TIsRectSign> cap = ArgumentCaptor.forClass(TIsRectSign.class);
        verify(signMapper).insert(cap.capture());
        assertEquals(Integer.valueOf(2), cap.getValue().getTargetLevelNo());
        assertEquals("安环科审核", cap.getValue().getTargetLevelName());
    }

    @Test
    void agentSignRecordsAgentIntendedAndReason() {
        a.setStatus(RectConst.ST_RUNNING);
        a.setCurLevelNo(1);
        // 代办但没写原因：拒
        SignCmd noReason = cmd(RectConst.ACT_PASS, "同意，情况属实");
        noReason.setIntendedSigner("lisi");
        assertThrows(IllegalStateException.class, () -> tx.sign(noReason, chain, now));

        // 代办三栏齐全：落笔者=当前人，原签署人=被代办人，代办不改层级
        when(applyMapper.passAdvance(anyLong(), anyInt(), anyInt(), anyInt(), any())).thenReturn(1);
        SignCmd agent = cmd(RectConst.ACT_PASS, "同意，情况属实");
        agent.setIntendedSigner("lisi");
        agent.setAgentReason("lisi 休假，经其委托代签");
        tx.sign(agent, chain, now);
        ArgumentCaptor<TIsRectSign> cap = ArgumentCaptor.forClass(TIsRectSign.class);
        verify(signMapper).insert(cap.capture());
        assertEquals("signer1", cap.getValue().getSigner());
        assertEquals("lisi", cap.getValue().getIntendedSigner());
        assertEquals(1, cap.getValue().getAgentFlag());
        assertEquals("lisi 休假，经其委托代签", cap.getValue().getAgentReason());
    }

    @Test
    void withdrawOnlyBeforeAnySigning() {
        // 从未提交过的草稿：可撤
        when(applyMapper.withdraw(anyLong(), any())).thenReturn(1);
        tx.withdraw(100L, "zhangsan", now);
        verify(applyMapper).withdraw(100L, now);
        verify(signMapper).insert(argEvent(RectConst.EVT_WITHDRAW));

        // 已提交过：拒撤
        when(signMapper.selectCount(any(QueryWrapper.class))).thenReturn(1);
        assertThrows(IllegalStateException.class, () -> tx.withdraw(100L, "zhangsan", now));
    }

    @Test
    void withdrawRejectsNonApplicant() {
        assertThrows(IllegalStateException.class, () -> tx.withdraw(100L, "someoneelse", now));
        verify(applyMapper, never()).withdraw(anyLong(), any());
    }

    @Test
    void archiveOnlyAfterClosedAndNeverTouchesConclusion() {
        // 审批中不许归档
        a.setStatus(RectConst.ST_RUNNING);
        assertThrows(IllegalStateException.class, () -> tx.archive(100L, "admin", now));

        // 已通过结案：可归档
        a.setStatus(RectConst.ST_PASSED);
        a.setConclusion(RectConst.CON_PASS);
        when(applyMapper.archive(anyLong(), any())).thenReturn(1);
        tx.archive(100L, "admin", now);
        verify(applyMapper).archive(100L, now);
        // 归档 SQL 只翻 status，结案列不在其中（由 Mapper 注解保证；这里确认结论不被本流程改写）
        assertEquals(Integer.valueOf(RectConst.CON_PASS), a.getConclusion());
    }

    @Test
    void urgeScanUrgesOncePerRoundLevelAndSkipsWithinLimit() {
        TIsRectApply stuck = new TIsRectApply();
        stuck.setId(200L);
        stuck.setApplyNo("ZG20261002-0002");
        stuck.setStatus(RectConst.ST_RUNNING);
        stuck.setCurLevelNo(1);
        stuck.setRoundNo(1);
        // 已卡 72 小时（时限 48）
        stuck.setLevelEnteredAt(new Date(now.getTime() - 72L * 60 * 60 * 1000));

        TIsRectApply fresh = new TIsRectApply();
        fresh.setId(201L);
        fresh.setApplyNo("ZG20261002-0003");
        fresh.setStatus(RectConst.ST_RUNNING);
        fresh.setCurLevelNo(2);
        fresh.setRoundNo(1);
        fresh.setLevelEnteredAt(now);

        List<TIsRectApply> running = new ArrayList<>(Arrays.asList(stuck, fresh));
        when(applyMapper.selectList(any())).thenReturn(running);
        when(urgeMapper.selectList(any())).thenReturn(new ArrayList<TIsRectUrge>());

        UrgeScreen screen = tx.urgeScan(chain, now);
        assertEquals(2, screen.getRunningCnt());
        assertEquals(1, screen.getUrgedCnt(), "只有超时那张催");
        assertEquals(1, screen.getWithinCnt());
        ArgumentCaptor<TIsRectUrge> cap = ArgumentCaptor.forClass(TIsRectUrge.class);
        verify(urgeMapper).insert(cap.capture());
        assertEquals(Integer.valueOf(1), cap.getValue().getLevelNo());
        assertTrue(cap.getValue().getOverdueHours() >= 72);

        // 再扫一轮：上次催办时刻不早于本次进层时刻（一直没动），不重复留痕
        TIsRectUrge done = new TIsRectUrge();
        done.setUrgedAt(now);
        List<TIsRectUrge> priorOne = new ArrayList<>();
        priorOne.add(done);
        when(urgeMapper.selectList(any())).thenReturn(priorOne);
        UrgeScreen again = tx.urgeScan(chain, now);
        assertEquals(0, again.getUrgedCnt());
        verify(urgeMapper, org.mockito.Mockito.times(1)).insert(any(TIsRectUrge.class));
    }

    private TIsRectSign argEvent(String action) {
        return org.mockito.ArgumentMatchers.argThat(s -> s != null && action.equals(s.getAction()));
    }
}
