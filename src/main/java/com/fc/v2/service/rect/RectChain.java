package com.fc.v2.service.rect;

import com.fc.v2.model.auto.TIsRectLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 启用层级链条的只读快照：次序永远现取配置（status=0 按 level_no 升序），不抄在单子上。
 *
 * 单子提交进 first()；通过进 nextOf(当前号)；nextOf 对末级返回 null（=末级通过结案）。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class RectChain {

    private final List<TIsRectLevel> levels;

    public RectChain(List<TIsRectLevel> activeLevels) {
        List<TIsRectLevel> copy = new ArrayList<>(activeLevels);
        Collections.sort(copy, new Comparator<TIsRectLevel>() {
            @Override
            public int compare(TIsRectLevel a, TIsRectLevel b) {
                return Integer.compare(a.getLevelNo(), b.getLevelNo());
            }
        });
        this.levels = Collections.unmodifiableList(copy);
    }

    /** 链条一级都没配：提交无处可去。 */
    public boolean isEmpty() {
        return levels.isEmpty();
    }

    public int size() {
        return levels.size();
    }

    public List<TIsRectLevel> all() {
        return levels;
    }

    /** 第一级（提交/补齐重提都进这级）。 */
    public TIsRectLevel first() {
        return levels.isEmpty() ? null : levels.get(0);
    }

    /** 按层级号取启用层级；找不到（停用/没配）返回 null。 */
    public TIsRectLevel byNo(Integer levelNo) {
        if (levelNo == null) {
            return null;
        }
        for (TIsRectLevel l : levels) {
            if (l.getLevelNo().equals(levelNo)) {
                return l;
            }
        }
        return null;
    }

    /**
     * 当前号的下一号启用层级（比它大的最小号）；已是末级返回 null。
     * 注意：转交不走这里——转交可以去链条上任何别的启用级。
     */
    public TIsRectLevel nextOf(Integer levelNo) {
        TIsRectLevel hit = null;
        for (TIsRectLevel l : levels) {
            if (l.getLevelNo() > (levelNo == null ? Integer.MIN_VALUE : levelNo)) {
                hit = l;
                break;
            }
        }
        return hit;
    }

    /** 给定角色集合能签收的层级号集合：角色对不上的层级，单子连看都看不到。 */
    public Set<Integer> visibleLevelNos(Set<Long> roleIds) {
        Set<Integer> nos = new HashSet<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return nos;
        }
        for (TIsRectLevel l : levels) {
            if (l.getSignerRoleId() != null && roleIds.contains(l.getSignerRoleId())) {
                nos.add(l.getLevelNo());
            }
        }
        return nos;
    }

    public Integer urgeHoursOf(Integer levelNo) {
        TIsRectLevel l = byNo(levelNo);
        if (l == null || l.getUrgeHours() == null || l.getUrgeHours() <= 0) {
            return RectConst.DEFAULT_URGE_HOURS;
        }
        return l.getUrgeHours();
    }
}
