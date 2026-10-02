package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIsRectApplyMapper;
import com.fc.v2.mapper.auto.TIsRectLevelMapper;
import com.fc.v2.mapper.auto.TIsRectSignMapper;
import com.fc.v2.mapper.auto.TIsRectUrgeMapper;
import com.fc.v2.model.auto.TIsRectApply;
import com.fc.v2.model.auto.TIsRectLevel;
import com.fc.v2.model.auto.TIsRectSign;
import com.fc.v2.model.auto.TIsRectUrge;
import com.fc.v2.model.auto.TSysRole;
import com.fc.v2.service.ITIsRectApplyService;
import com.fc.v2.service.ITSysRoleService;
import com.fc.v2.service.rect.RectChain;
import com.fc.v2.service.rect.RectApplyTx;
import com.fc.v2.service.rect.RectConst;
import com.fc.v2.service.rect.SignCmd;
import com.fc.v2.service.rect.UrgeScreen;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 隐患整改申请逐级签批 Service 实现。
 *
 * 职责切分（同 TIsHazTaskServiceImpl 的范式）：本类做链条现取、角色可见性、台账点算；
 * 真正落库的每一步流转都在 {@link RectApplyTx} 的单一事务里。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Service
public class TIsRectApplyServiceImpl implements ITIsRectApplyService {

    @Resource
    private TIsRectApplyMapper applyMapper;
    @Resource
    private TIsRectSignMapper signMapper;
    @Resource
    private TIsRectUrgeMapper urgeMapper;
    @Resource
    private TIsRectLevelMapper levelMapper;
    @Resource
    private ITSysRoleService roleService;
    @Resource
    private com.fc.v2.mapper.auto.TSysUserMapper userMapper;
    @Resource
    private RectApplyTx tx;

    @Override
    public TIsRectApply draft(Long hazardId, Integer hazardLevel, String remark, String applicant) {
        return tx.draft(hazardId, hazardLevel, remark, applicant, new Date());
    }

    @Override
    public TIsRectApply submit(Long id, String supplementNote, String applicant) {
        return tx.submit(id, supplementNote, applicant, activeChain(), new Date());
    }

    @Override
    public TIsRectApply sign(SignCmd cmd) {
        TIsRectApply a = mustGet(cmd.getId());
        // 不是自己这层的单，连签收都不许——与"连看都看不到"同一道墙：角色对不上直接拒。
        RectChain chain = activeChain();
        TIsRectLevel cur = chain.byNo(a.getCurLevelNo());
        if (cur == null) {
            throw new IllegalStateException("当前待签层级未启用，签收中止");
        }
        Set<Integer> visibleNos = visibleLevelNos(cmd.getOperator());
        if (!visibleNos.contains(cur.getLevelNo())) {
            throw new IllegalStateException("这张单当前停在[" + cur.getLevelName()
                    + "]，不在你的签收层级内，无权签收");
        }
        return tx.sign(cmd, chain, new Date());
    }

    @Override
    public TIsRectApply withdraw(Long id, String applicant) {
        return tx.withdraw(id, applicant, new Date());
    }

    @Override
    public TIsRectApply archive(Long id, String operator) {
        return tx.archive(id, operator, new Date());
    }

    @Override
    public TIsRectApply selectById(Long id) {
        return applyMapper.selectById(id);
    }

    @Override
    public List<TIsRectApply> listLedger(Integer status, String applyNo) {
        QueryWrapper<TIsRectApply> qw = new QueryWrapper<TIsRectApply>().eq("del_flag", 0);
        if (status != null) {
            qw.eq("status", status);
        }
        if (applyNo != null && !applyNo.trim().isEmpty()) {
            qw.likeRight("apply_no", applyNo.trim());
        }
        qw.orderByDesc("create_time", "id");
        return applyMapper.selectList(qw);
    }

    @Override
    public List<TIsRectApply> myTodo(String username) {
        // 入参在 Controller 已是登录本人；这里按用户名找到其角色，再按角色放层级。
        Set<Integer> visibleNos = visibleLevelNos(username);
        if (visibleNos.isEmpty()) {
            return new ArrayList<>();
        }
        return applyMapper.selectList(new QueryWrapper<TIsRectApply>()
                .eq("del_flag", 0)
                .eq("status", RectConst.ST_RUNNING)
                .in("cur_level_no", visibleNos)
                .orderByAsc("level_entered_at", "id"));
    }

    @Override
    public List<TIsRectApply> myApplied(String applicant) {
        return applyMapper.selectList(new QueryWrapper<TIsRectApply>()
                .eq("del_flag", 0)
                .eq("applicant", applicant)
                .orderByDesc("create_time", "id"));
    }

    @Override
    public Map<String, Object> detail(Long id) {
        TIsRectApply apply = mustGet(id);
        Map<String, Object> data = new HashMap<>();
        data.put("apply", apply);
        data.put("signs", listSigns(id));
        data.put("urges", listUrges(id));
        // "现在停在哪一级、谁该签"：把当前待签层级与它的签收角色一并给出。
        data.put("curLevel", activeChain().byNo(apply.getCurLevelNo()));
        return data;
    }

    @Override
    public List<TIsRectSign> listSigns(Long applyId) {
        return signMapper.selectList(new QueryWrapper<TIsRectSign>()
                .eq("apply_id", applyId)
                .orderByAsc("sign_time", "id"));
    }

    @Override
    public List<TIsRectUrge> listUrges(Long applyId) {
        return urgeMapper.selectList(new QueryWrapper<TIsRectUrge>()
                .eq("apply_id", applyId)
                .orderByAsc("urged_at", "id"));
    }

    @Override
    public Map<String, Object> stats(String username) {
        Map<String, Object> m = new HashMap<>();
        // 已作废不参与统计：status != 4。
        List<TIsRectApply> live = applyMapper.selectList(new QueryWrapper<TIsRectApply>()
                .eq("del_flag", 0)
                .ne("status", RectConst.ST_VOID));
        int draft = 0;
        int running = 0;
        int passed = 0;
        int rejected = 0;
        for (TIsRectApply a : live) {
            int st = a.getStatus() == null ? -1 : a.getStatus();
            if (st == RectConst.ST_DRAFT) {
                draft++;
            } else if (st == RectConst.ST_RUNNING) {
                running++;
            } else if (st == RectConst.ST_PASSED) {
                passed++;
            } else if (st == RectConst.ST_REJECTED) {
                rejected++;
            }
        }
        m.put("draft", draft);
        m.put("running", running);
        m.put("passed", passed);
        m.put("rejected", rejected);
        m.put("myTodo", myTodo(username).size());
        return m;
    }

    @Override
    public UrgeScreen urgeScan() {
        return tx.urgeScan(activeChain(), new Date());
    }

    @Override
    public List<TIsRectLevel> listActiveLevels() {
        return activeChain().all();
    }

    @Override
    public List<TIsRectLevel> listAllLevels() {
        return levelMapper.selectList(new QueryWrapper<TIsRectLevel>()
                .eq("del_flag", 0)
                .orderByAsc("level_no", "id"));
    }

    @Override
    public TIsRectLevel addLevel(TIsRectLevel level) {
        if (level.getLevelNo() == null || level.getLevelNo() < 1) {
            throw new IllegalArgumentException("层级次序必须是不小于 1 的整数");
        }
        if (level.getLevelName() == null || level.getLevelName().trim().isEmpty()) {
            throw new IllegalArgumentException("层级名不能为空");
        }
        if (level.getSignerRoleId() == null) {
            throw new IllegalArgumentException("本层签收角色不能为空：角色对不上的层级没法签收");
        }
        long dup = levelMapper.selectCount(new QueryWrapper<TIsRectLevel>()
                .eq("del_flag", 0)
                .eq("level_no", level.getLevelNo()));
        if (dup > 0) {
            throw new IllegalStateException("层级次序 " + level.getLevelNo() + " 已存在，次序由平台写死、不许重号");
        }
        if (level.getStatus() == null) {
            level.setStatus(0);
        }
        if (level.getDelFlag() == null) {
            level.setDelFlag(0);
        }
        if (level.getUrgeHours() == null) {
            level.setUrgeHours(RectConst.DEFAULT_URGE_HOURS);
        }
        level.setCreateTime(new Date());
        level.setUpdateTime(new Date());
        levelMapper.insert(level);
        return level;
    }

    @Override
    public TIsRectLevel retireLevel(Long id) {
        TIsRectLevel level = levelMapper.selectById(id);
        if (level == null || (level.getDelFlag() != null && level.getDelFlag() == 1)) {
            throw new IllegalArgumentException("层级不存在：" + id);
        }
        // 有在途单还挂在这级，不许停用：停用会让这些单再也走不动、也没人看得到。
        long onTheWay = applyMapper.selectCount(new QueryWrapper<TIsRectApply>()
                .eq("del_flag", 0)
                .eq("status", RectConst.ST_RUNNING)
                .eq("cur_level_no", level.getLevelNo()));
        if (onTheWay > 0) {
            throw new IllegalStateException("还有 " + onTheWay + " 张在途单挂在[" + level.getLevelName()
                    + "]，处理完才能停用这一级");
        }
        level.setStatus(1);
        level.setUpdateTime(new Date());
        levelMapper.updateById(level);
        return level;
    }

    /** 链条永远现取启用配置，不抄在单子上。 */
    private RectChain activeChain() {
        List<TIsRectLevel> levels = levelMapper.selectList(new QueryWrapper<TIsRectLevel>()
                .eq("del_flag", 0)
                .eq("status", 0)
                .orderByAsc("level_no"));
        return new RectChain(levels);
    }

    /** 当前登录用户名 → 用户 id → 持有的角色集合 → 可见层级号。 */
    private Set<Integer> visibleLevelNos(String username) {
        Set<Long> roleIds = new HashSet<>();
        Long userId = userIdsByUsername(username);
        if (userId != null) {
            List<TSysRole> roles = roleService.queryUserRole(userId);
            if (roles != null) {
                for (TSysRole r : roles) {
                    roleIds.add(r.getId());
                }
            }
        }
        return activeChain().visibleLevelNos(roleIds);
    }

    private Long userIdsByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        com.fc.v2.model.auto.TSysUser u = userMapper.selectOne(
                new QueryWrapper<com.fc.v2.model.auto.TSysUser>()
                        .eq("username", username)
                        .last("limit 1"));
        return u == null ? null : u.getId();
    }

    private TIsRectApply mustGet(Long id) {
        TIsRectApply a = applyMapper.selectById(id);
        if (a == null || (a.getDelFlag() != null && a.getDelFlag() == 1)) {
            throw new IllegalArgumentException("申请单不存在：" + id);
        }
        return a;
    }
}
