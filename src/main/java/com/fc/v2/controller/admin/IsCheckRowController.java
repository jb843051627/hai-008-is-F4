package com.fc.v2.controller.admin;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TIsCheckRow;
import com.fc.v2.service.ITIsCheckRowService;
import com.fc.v2.service.check.BatchReceipt;
import com.fc.v2.service.check.CheckImportException;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 执法检查记录整批导入 Controller。
 *
 * 一份 Excel/CSV 传上来，一行一行验、一行一行入库，哪行有毛病当场标红，
 * 错误清单照原表行号定位。一批收不收、哪行进哪行退，全以后台逐行处理方法的回执为准，
 * 页面既不加行、也不动旧批。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Api(value = "执法检查记录整批导入")
@Controller
@RequestMapping("/isCheckRow")
public class IsCheckRowController extends BaseController {

    private final String prefix = "admin/isCheckRow";

    @org.springframework.beans.factory.annotation.Autowired
    private ITIsCheckRowService checkRowService;

    @ApiOperation(value = "整批导入页跳转", notes = "整批导入页跳转")
    @GetMapping("/view")
    @RequiresPermissions("isCheckRow:view")
    public String view(ModelMap model) {
        model.put("maxRows", com.fc.v2.service.check.CheckConst.MAX_ROWS_PER_BATCH);
        return prefix + "/list";
    }

    @Log(title = "执法检查记录整批导入", action = "import")
    @ApiOperation(value = "整批导入", notes = "只收 UTF-8 的 xlsx/CSV；一行一验一行一入，错误行照原行号退回；同号批次只认头一遍")
    @PostMapping("/import")
    @RequiresPermissions("isCheckRow:import")
    @ResponseBody
    public AjaxResult importBatch(@RequestParam("batchNo") String batchNo,
                                  @RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return AjaxResult.error("没收到文件，请选好表再传");
            }
            BatchReceipt receipt = checkRowService.importBatch(batchNo, file.getBytes(),
                    file.getOriginalFilename(), ShiroUtils.getLoginName());
            return AjaxResult.success(receipt.getMessage()).put("data", receipt);
        } catch (CheckImportException e) {
            // 进门规矩类（编码/格式/表头）：只回明话，不回堆栈。
            return AjaxResult.error(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            return AjaxResult.error("导入失败：" + e.getClass().getSimpleName()
                    + "，请确认文件能在 Excel 正常打开后另存一次再传");
        }
    }

    @Log(title = "批次逐行台账", action = "list")
    @ApiOperation(value = "批次逐行台账", notes = "按原表行号升序；页面上的三数不作数，只看后台点算")
    @GetMapping("/list")
    @RequiresPermissions("isCheckRow:list")
    @ResponseBody
    public AjaxResult list(@RequestParam("batchNo") String batchNo) {
        List<TIsCheckRow> rows = checkRowService.listBatch(batchNo);
        Map<String, Object> data = new HashMap<>();
        data.put("rows", rows);
        data.put("counts", countByDb(batchNo));
        return AjaxResult.successData(200, data);
    }

    @ApiOperation(value = "错误清单", notes = "行号+类别+缘由，行号照原表（表头不算，第一行数据=1）")
    @GetMapping("/errors")
    @RequiresPermissions("isCheckRow:list")
    @ResponseBody
    public AjaxResult errors(@RequestParam("batchNo") String batchNo) {
        return AjaxResult.successData(200, checkRowService.listErrors(batchNo));
    }

    @ApiOperation(value = "正式检查记录回查", notes = "按批次号/检查日期/企业编号；同企业并入行不重复出")
    @GetMapping("/search")
    @RequiresPermissions("isCheckRow:list")
    @ResponseBody
    public AjaxResult search(@RequestParam(required = false) String batchNo,
                             @RequestParam(required = false) String siteNo,
                             @RequestParam(required = false) String checkDate) {
        Date date = null;
        if (checkDate != null && !checkDate.trim().isEmpty()) {
            try {
                date = new SimpleDateFormat("yyyy-MM-dd").parse(checkDate.trim());
            } catch (java.text.ParseException e) {
                return AjaxResult.error("检查日期格式不像样，应如 2026-09-01");
            }
        }
        return AjaxResult.successData(200, checkRowService.searchFormal(batchNo, siteNo, date));
    }

    /** 三个数以库里逐行点算为准：这里就是把后台点算原样摆出，页面不另数。 */
    private Map<String, Object> countByDb(String batchNo) {
        List<TIsCheckRow> rows = checkRowService.listBatch(batchNo);
        int total = rows.size();
        int stored = 0;
        int rejected = 0;
        for (TIsCheckRow r : rows) {
            if (r.getStatus() != null && r.getStatus() == com.fc.v2.service.check.CheckConst.ST_OK) {
                stored++;
            } else if (r.getStatus() != null && r.getStatus() == com.fc.v2.service.check.CheckConst.ST_FAIL) {
                rejected++;
            }
        }
        Map<String, Object> m = new HashMap<>();
        m.put("total", total);
        m.put("stored", stored);
        m.put("rejected", rejected);
        m.put("consistent", stored + rejected == total);
        return m;
    }
}
