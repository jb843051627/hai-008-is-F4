package com.fc.v2.controller.admin;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TIsRectLevel;
import com.fc.v2.service.ITIsRectApplyService;
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

import java.util.List;

/**
 * 整改申请签批层级配置 Controller。
 *
 * 层级次序由平台写死、不抄在单子上：登记层级、停用（有在途单拒停）。链条对流转实时生效。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Api(value = "整改申请签批层级配置")
@Controller
@RequestMapping("/isRectLevel")
public class IsRectLevelController extends BaseController {

    private final String prefix = "admin/isRectLevel";

    @Autowired
    private ITIsRectApplyService rectApplyService;

    @ApiOperation(value = "层级配置跳转", notes = "层级配置跳转")
    @GetMapping("/view")
    @RequiresPermissions("isRectLevel:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "签批层级配置查询", action = "list")
    @ApiOperation(value = "层级全量", notes = "启用停用都翻得到；流转只认 status=0")
    @GetMapping("/list")
    @RequiresPermissions("isRectLevel:list")
    @ResponseBody
    public ResultTable list() {
        startPage();
        List<TIsRectLevel> list = rectApplyService.listAllLevels();
        com.github.pagehelper.PageInfo<TIsRectLevel> page = new com.github.pagehelper.PageInfo<>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "签批层级登记", action = "add")
    @ApiOperation(value = "登记一级", notes = "次序不许重号；必须绑定本层签收角色（角色对不上这层的单看不到）")
    @PostMapping("/add")
    @RequiresPermissions("isRectLevel:add")
    @RequiresRoles("管理员")
    @ResponseBody
    public AjaxResult add(TIsRectLevel level) {
        try {
            TIsRectLevel saved = rectApplyService.addLevel(level);
            return AjaxResult.success("第" + saved.getLevelNo() + "级[" + saved.getLevelName() + "]已登记").put("data", saved);
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    @Log(title = "签批层级停用", action = "retire")
    @ApiOperation(value = "停用一级", notes = "有在途单挂在这级时拒停")
    @PostMapping("/retire")
    @RequiresPermissions("isRectLevel:add")
    @RequiresRoles("管理员")
    @ResponseBody
    public AjaxResult retire(Long id) {
        try {
            TIsRectLevel saved = rectApplyService.retireLevel(id);
            return AjaxResult.success("第" + saved.getLevelNo()
                    + "级已停用，新提交的单不再经过这级（在途单此前已拦在停用前）").put("data", saved);
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }
}
