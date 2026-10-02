package com.fc.v2.controller.admin;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TIsRectApply;
import com.fc.v2.service.ITIsRectApplyService;
import com.fc.v2.service.rect.SignCmd;
import com.fc.v2.service.rect.UrgeScreen;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 隐患整改申请逐级签批 Controller。
 *
 * 一张单一行：停在哪级、谁该签、已有哪些意见，台账与明细一翻便知。
 * 每级只看得到轮到自己这层的单；作废只两入口（申请人未签前撤回 / 管理员结案后归档）。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Api(value = "隐患整改申请逐级签批")
@Controller
@RequestMapping("/isRectApply")
public class IsRectApplyController extends BaseController {

    private final String prefix = "admin/isRectApply";

    @Autowired
    private ITIsRectApplyService rectApplyService;

    @ApiOperation(value = "签批台账跳转", notes = "签批台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("isRectApply:view")
    public String view(ModelMap model) {
        model.put("levels", rectApplyService.listActiveLevels());
        return prefix + "/list";
    }

    @Log(title = "整改申请签批台账", action = "list")
    @ApiOperation(value = "台账分页", notes = "作废单仍翻得到，只是不进待办与统计")
    @GetMapping("/list")
    @RequiresPermissions("isRectApply:list")
    @ResponseBody
    public ResultTable list(@org.springframework.web.bind.annotation.RequestParam(required = false) Integer status,
                            @org.springframework.web.bind.annotation.RequestParam(required = false) String applyNo) {
        startPage();
        List<TIsRectApply> list = rectApplyService.listLedger(status, applyNo);
        com.github.pagehelper.PageInfo<TIsRectApply> page = new com.github.pagehelper.PageInfo<>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "我的待签", action = "todo")
    @ApiOperation(value = "我的待签", notes = "只返回当前待签层级在本人签收角色内的审批中单，不是自己这层的看不到")
    @GetMapping("/myTodo")
    @RequiresPermissions("isRectApply:list")
    @ResponseBody
    public ResultTable myTodo() {
        startPage();
        List<TIsRectApply> list = rectApplyService.myTodo(ShiroUtils.getLoginName());
        com.github.pagehelper.PageInfo<TIsRectApply> page = new com.github.pagehelper.PageInfo<>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "我提交的单", notes = "含未提交草稿")
    @GetMapping("/myApplied")
    @RequiresPermissions("isRectApply:list")
    @ResponseBody
    public ResultTable myApplied() {
        startPage();
        List<TIsRectApply> list = rectApplyService.myApplied(ShiroUtils.getLoginName());
        com.github.pagehelper.PageInfo<TIsRectApply> page = new com.github.pagehelper.PageInfo<>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "整改申请单明细", action = "detail")
    @ApiOperation(value = "单张全貌", notes = "单子当前各栏 + 逐级签署意见 + 催办留痕")
    @GetMapping("/detail")
    @RequiresPermissions("isRectApply:list")
    @ResponseBody
    public AjaxResult detail(Long id) {
        try {
            return AjaxResult.successData(200, rectApplyService.detail(id));
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    @ApiOperation(value = "点算", notes = "已作废不参与；含本人待签数")
    @GetMapping("/stats")
    @RequiresPermissions("isRectApply:list")
    @ResponseBody
    public AjaxResult stats() {
        Map<String, Object> m = new HashMap<>(rectApplyService.stats(ShiroUtils.getLoginName()));
        return AjaxResult.successData(200, m);
    }

    @Log(title = "整改申请起草", action = "draft")
    @ApiOperation(value = "建未提交草稿", notes = "关联隐患（哪条、什么级别）落单后不许改")
    @PostMapping("/draft")
    @RequiresPermissions("isRectApply:apply")
    @ResponseBody
    public AjaxResult draft(Long hazardId, Integer hazardLevel, String remark) {
        try {
            TIsRectApply saved = rectApplyService.draft(hazardId, hazardLevel, remark, ShiroUtils.getLoginName());
            return AjaxResult.success("申请单[" + saved.getApplyNo() + "]已存为未提交").put("data", saved);
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    @Log(title = "整改申请提交", action = "submit")
    @ApiOperation(value = "提交/补齐重提", notes = "提交进第一级；补材料后重提轮次加一、从第一级重头走")
    @PostMapping("/submit")
    @RequiresPermissions("isRectApply:apply")
    @ResponseBody
    public AjaxResult submit(Long id, String supplementNote) {
        try {
            TIsRectApply saved = rectApplyService.submit(id, supplementNote, ShiroUtils.getLoginName());
            return AjaxResult.success("已提交，当前待签：第" + saved.getCurLevelNo() + "级").put("data", saved);
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    @Log(title = "整改申请签收", action = "sign")
    @ApiOperation(value = "层级签收", notes = "通过/驳回/补充材料/转交他级；转交须写全本级意见；代办写原签署人与原因")
    @PostMapping("/sign")
    @RequiresPermissions("isRectApply:sign")
    @ResponseBody
    public AjaxResult sign(Long id, String action, String comment,
                           String intendedSigner, String agentReason, Integer targetLevelNo) {
        SignCmd cmd = new SignCmd();
        cmd.setId(id);
        cmd.setAction(action);
        cmd.setComment(comment);
        cmd.setOperator(ShiroUtils.getLoginName());
        cmd.setIntendedSigner(intendedSigner);
        cmd.setAgentReason(agentReason);
        cmd.setTargetLevelNo(targetLevelNo);
        try {
            TIsRectApply saved = rectApplyService.sign(cmd);
            String msg;
            if (saved.getStatus() != null && saved.getStatus() >= 2) {
                msg = "流程结案，结论：" + (saved.getConclusion() != null && saved.getConclusion() == 1
                        ? "通过" : "驳回");
            } else if (saved.getStatus() != null && saved.getStatus() == 0) {
                msg = "已退回申请人补充材料，补齐后从第一级重新提交";
            } else {
                msg = "已签收，当前待签：第" + saved.getCurLevelNo() + "级";
            }
            return AjaxResult.success(msg).put("data", saved);
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    @Log(title = "整改申请撤回", action = "withdraw")
    @ApiOperation(value = "申请人撤回作废", notes = "只许在单子进任何一级签署前（未提交草稿）")
    @PostMapping("/withdraw")
    @RequiresPermissions("isRectApply:apply")
    @ResponseBody
    public AjaxResult withdraw(Long id) {
        try {
            rectApplyService.withdraw(id, ShiroUtils.getLoginName());
            return AjaxResult.success("已撤回作废");
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    @Log(title = "结案单归档", action = "archive")
    @ApiOperation(value = "管理员归档作废", notes = "只许对已结案单；结论与结案时间原样封死，谁都改不了")
    @PostMapping("/archive")
    @RequiresPermissions("isRectApply:archive")
    @RequiresRoles("管理员")
    @ResponseBody
    public AjaxResult archive(Long id) {
        try {
            rectApplyService.archive(id, ShiroUtils.getLoginName());
            return AjaxResult.success("已归档作废，结论与结案时间原样保留");
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    @Log(title = "整改申请超时催办扫描", action = "urge")
    @ApiOperation(value = "立即催办一轮", notes = "只催审批中超时单；同轮同级只催一次；作废/结案扫不到")
    @PostMapping("/urge")
    @RequiresPermissions("isRectApply:urge")
    @ResponseBody
    public AjaxResult urge() {
        UrgeScreen screen = rectApplyService.urgeScan();
        return AjaxResult.success(screen.getMessage()).put("data", screen);
    }
}
