package com.fc.v2.mapper.auto;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TIsRectApply;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

/**
 * 隐患整改申请单数据层。
 *
 * 所有流转都走"带前置条件的原子 UPDATE"：WHERE 里钉死 status=1、当前层级号、轮次号，
 * 更新 0 行即说明单子已被别人先推动（并发签收）或已不在这一层，调用方按失败处理。
 * 一张单同一时刻只挂在一级手上，不靠应用侧加锁，靠这条 SQL 兜底。
 *
 * @author fuce
 * @date 2026-10-02
 */
public interface TIsRectApplyMapper extends BaseMapper<TIsRectApply> {

    /**
     * 提交/补齐重提：未提交 → 审批中，挂到链条第一级。
     * resubmit=1 时轮次加一（补齐后从头轮算起）；首轮提交传 0。
     */
    @Update("UPDATE t_is_rect_apply SET "
            + " status = 1, cur_level_no = #{firstLevelNo}, "
            + " round_no = round_no + #{resubmit}, "
            + " submit_time = #{at}, level_entered_at = #{at}, update_time = #{at} "
            + " WHERE id = #{id} AND status = 0 AND del_flag = 0")
    int submit(@Param("id") Long id,
               @Param("firstLevelNo") int firstLevelNo,
               @Param("resubmit") int resubmit,
               @Param("at") Date at);

    /** 通过且本层不是末级：进下一号层级，刷新进层时刻（催办重新计时）。 */
    @Update("UPDATE t_is_rect_apply SET "
            + " cur_level_no = #{nextLevelNo}, level_entered_at = #{at}, update_time = #{at} "
            + " WHERE id = #{id} AND status = 1 AND cur_level_no = #{expectLevelNo} "
            + " AND round_no = #{expectRound} AND del_flag = 0")
    int passAdvance(@Param("id") Long id,
                    @Param("expectLevelNo") int expectLevelNo,
                    @Param("expectRound") int expectRound,
                    @Param("nextLevelNo") int nextLevelNo,
                    @Param("at") Date at);

    /** 末级通过即结案：结论=通过、结案时间同写，当前待签层级清空。结案写死，之后再无 UPDATE 能命中。 */
    @Update("UPDATE t_is_rect_apply SET "
            + " status = 2, conclusion = 1, closed_time = #{at}, "
            + " cur_level_no = NULL, update_time = #{at} "
            + " WHERE id = #{id} AND status = 1 AND cur_level_no = #{expectLevelNo} "
            + " AND round_no = #{expectRound} AND del_flag = 0")
    int passClose(@Param("id") Long id,
                  @Param("expectLevelNo") int expectLevelNo,
                  @Param("expectRound") int expectRound,
                  @Param("at") Date at);

    /** 驳回直接落已驳回并结案（结论=驳回）；不许留在流程里。 */
    @Update("UPDATE t_is_rect_apply SET "
            + " status = 3, conclusion = 2, closed_time = #{at}, "
            + " cur_level_no = NULL, update_time = #{at} "
            + " WHERE id = #{id} AND status = 1 AND cur_level_no = #{expectLevelNo} "
            + " AND round_no = #{expectRound} AND del_flag = 0")
    int rejectClose(@Param("id") Long id,
                    @Param("expectLevelNo") int expectLevelNo,
                    @Param("expectRound") int expectRound,
                    @Param("at") Date at);

    /** 补充材料：退回申请人（未提交），当前待签层级清空；轮次不动，重提时才加一。 */
    @Update("UPDATE t_is_rect_apply SET "
            + " status = 0, cur_level_no = NULL, update_time = #{at} "
            + " WHERE id = #{id} AND status = 1 AND cur_level_no = #{expectLevelNo} "
            + " AND round_no = #{expectRound} AND del_flag = 0")
    int backToApplicant(@Param("id") Long id,
                        @Param("expectLevelNo") int expectLevelNo,
                        @Param("expectRound") int expectRound,
                        @Param("at") Date at);

    /** 转交他级：本级意见已记全，单子改挂目标层级，进层时刻刷新；轮次、状态不变。 */
    @Update("UPDATE t_is_rect_apply SET "
            + " cur_level_no = #{targetLevelNo}, level_entered_at = #{at}, update_time = #{at} "
            + " WHERE id = #{id} AND status = 1 AND cur_level_no = #{expectLevelNo} "
            + " AND round_no = #{expectRound} AND del_flag = 0")
    int transferTo(@Param("id") Long id,
                   @Param("expectLevelNo") int expectLevelNo,
                   @Param("expectRound") int expectRound,
                   @Param("targetLevelNo") int targetLevelNo,
                   @Param("at") Date at);

    /** 申请人撤回：只许从"未提交且从未进过任何一级签署"的单子翻成已作废（是否进过由 Service 查留痕判）。 */
    @Update("UPDATE t_is_rect_apply SET "
            + " status = 4, update_time = #{at} "
            + " WHERE id = #{id} AND status = 0 AND del_flag = 0")
    int withdraw(@Param("id") Long id, @Param("at") Date at);

    /** 管理员归档：只许对已结案（通过/驳回）的单翻成已作废；结论、结案时间不在 SET 里，谁都改不动。 */
    @Update("UPDATE t_is_rect_apply SET "
            + " status = 4, update_time = #{at} "
            + " WHERE id = #{id} AND status IN (2, 3) AND del_flag = 0")
    int archive(@Param("id") Long id, @Param("at") Date at);
}
