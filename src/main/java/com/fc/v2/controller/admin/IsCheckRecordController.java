package com.fc.v2.controller.admin;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TIsCheckRow;
import com.fc.v2.service.ITIsCheckRowService;
import com.fc.v2.service.check.CheckError;
import com.fc.v2.service.check.CheckFileException;
import com.fc.v2.service.check.CheckReceipt;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 页面只管递文件、摆回执与错误清单；一批收不收、哪行进哪行退全是后台逐行处理方法说了算，
 * 页面不加行、不改旧批。错误清单按原表行号列，照号回原表定位那一行。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Api(value = "执法检查记录整批导入")
@Controller
@RequestMapping("/IsCheckRecordController")
public class IsCheckRecordController extends BaseController {

    private final String prefix = "admin/isCheckRecord";

    private static final Logger logger = LoggerFactory.getLogger(IsCheckRecordController.class);

    @Autowired
    private ITIsCheckRowService checkRowService;

    @ApiOperation(value = "整批导入页跳转", notes = "整批导入页跳转")
    @GetMapping("/view")
    @RequiresPermissions("is:checkRecord:view")
    public String view(ModelMap model) {
        return prefix + "/import";
    }

    /**
     * 整批上传：一份 CSV（UTF-8）+ 批次号。三数以库里逐行点算为准，原样摆给页面；
     * 编码不对给明确转码提示，绝不把乱码/堆栈甩给用户。
     */
    @Log(title = "执法检查记录整批导入", action = "import")
    @ApiOperation(value = "整批上传", notes = "整批上传")
    @PostMapping("/upload")
    @RequiresPermissions("is:checkRecord:import")
    @ResponseBody
    public AjaxResult upload(@RequestParam("file") MultipartFile file,
                             @RequestParam("batchNo") String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return error("批次号必须填：一批进门先登记批次号统住全批");
        }
        if (file == null || file.isEmpty()) {
            return error("没接到文件，请选好 UTF-8 的 CSV 再传");
        }
        String operator = currentUser();
        try {
            CheckReceipt receipt = checkRowService.submitBatch(
                    batchNo.trim(), file.getBytes(), file.getOriginalFilename(), operator);
            Map<String, Object> data = new HashMap<>();
            data.put("outcome", receipt.getOutcome().name());
            data.put("duplicate", receipt.isDuplicate());
            data.put("overLimit", receipt.isOverLimit());
            data.put("batchNo", receipt.getBatchNo());
            data.put("total", receipt.getTotal());
            data.put("stored", receipt.getStored());
            data.put("rejected", receipt.getRejected());
            data.put("recordCnt", receipt.getRecordCnt());
            data.put("mergedCnt", receipt.getMergedCnt());
            data.put("note", receipt.getNote());
            data.put("errors", receipt.getErrors());
            // 重复批次/超上限都不是系统异常：200 + outcome 区分，页面照实情摆话。
            return AjaxResult.success(200, receipt.getNote(), data);
        } catch (CheckFileException e) {
            // 读入器给的本就是给人看的转码/格式提示。
            return error(e.getMessage());
        } catch (Exception e) {
            logger.error("执法检查记录整批导入失败 batchNo={}", batchNo, e);
            return error("导入没办成，本批一行未入库，请拿同批次号修正后重传：" + e.getMessage());
        }
    }

    /** 正式检查记录：批次号、检查日期、企业编号三档任选查询（同企业并记后每企业一条）。 */
    @ApiOperation(value = "正式检查记录查询", notes = "批次号/检查日期/企业编号查询")
    @GetMapping("/records")
    @RequiresPermissions("is:checkRecord:list")
    @ResponseBody
    public ResultTable records(@RequestParam(value = "batchNo", required = false) String batchNo,
                               @RequestParam(value = "checkDate", required = false) String checkDate,
                               @RequestParam(value = "siteNo", required = false) String siteNo) {
        Date day = null;
        if (checkDate != null && !checkDate.trim().isEmpty()) {
            try {
                String t = checkDate.trim();
                day = new SimpleDateFormat(t.length() > 10 ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd").parse(t);
            } catch (java.text.ParseException e) {
                return pageTable(new java.util.ArrayList<>(), 0);
            }
        }
        List<TIsCheckRow> list = checkRowService.listRecords(batchNo, day, siteNo);
        return pageTable(list, list.size());
    }

    /** 错误清单：照原表行号排，"行号+类别+缘由"逐行列。 */
    @ApiOperation(value = "驳回错误清单", notes = "按批次取错误清单")
    @GetMapping("/errors")
    @RequiresPermissions("is:checkRecord:list")
    @ResponseBody
    public AjaxResult errors(@RequestParam("batchNo") String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return error("批次号必须填");
        }
        List<CheckError> errors = checkRowService.listErrorDetails(batchNo.trim());
        return AjaxResult.successData(200, errors);
    }

    private String currentUser() {
        try {
            return ShiroUtils.getUser() == null ? "anonymous" : ShiroUtils.getUser().getUsername();
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
