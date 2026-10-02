package com.fc.v2.common.quartz.task;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.service.ITIsHazTaskService;
import com.fc.v2.service.ITIsRectApplyService;
import com.fc.v2.service.ITSysUserService;
import com.fc.v2.service.haz.RoundScreen;
import com.fc.v2.service.rect.UrgeScreen;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @CLASSNAME   V2Task
 * @Description 定时调度具体工作类
 * @Auther Jan  橙寂
 * @DATE 2019/9/2 0002 15:33
 */
@Component("v2Task")
public class V2Task {

	@Autowired
    private ITSysUserService userService;

	@Autowired
	private ITIsHazTaskService isHazTaskService;

	@Autowired
	private ITIsRectApplyService rectApplyService;

    /**
     * 无参的任务
     */
    public void runTask1()
    {
        System.out.println("正在执行定时任务，无参方法");
    }

    /**
     * 有参任务
     * 目前仅执行常见的数据类型  Integer Long  带L  string  带 ''  bool Double 带 d
     * @param a
     * @param b
     */
    public void runTask2(Integer a,Long b,String c,Boolean d,Double e)
    {
    	List<TSysUser> list=  userService.selectTSysUserList(new QueryWrapper<TSysUser>());
    	System.err.println("用户查询num："+list.size());
        System.out.println("正在执行定时任务，带多个参数的方法"+a+"   "+b+" "+c+"  "+d+" "+e+"执行时间:"+DateUtil.now());
    }

	/**
	 * 隐患整改催办派发（由定时任务配置：v2Task.dispatchHazTask()）。
	 * 几点派、派不派由窗口说了算，本方法只负责到点触发；夜间/节假日静默、当日二次忽略、
	 * 本轮无活、系统出错四种结果在 runOnce 内已分开，这里如实落日志。
	 */
	public void dispatchHazTask()
	{
		RoundScreen screen = isHazTaskService.runOnce(DateUtil.date());
		System.out.println("催办派发轮次[" + screen.getState() + "] " + screen.getMessage()
				+ "（待派=" + screen.getWaitCnt() + " 已派=" + screen.getDispatchedCnt()
				+ " 挂起=" + screen.getSuspendedCnt() + "）执行时间:" + DateUtil.now());
	}
	/**
	 * 整改申请层级超时催办（由定时任务配置：v2Task.urgeRectApply()）。
	 * 只催审批中、卡在某一层超过该层时限的单；同一轮同一级只催一次并留痕。
	 * 已作废/已结案的单扫不到，催办不碰单子任何一栏。
	 */
	public void urgeRectApply()
	{
		UrgeScreen screen = rectApplyService.urgeScan();
		System.out.println("整改申请催办：" + screen.getMessage() + " 执行时间:" + DateUtil.now());
	}
}
