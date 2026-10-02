package com.fc.v2.service.rect;

/**
 * 催办扫描一屏结果：扫了几张在办单、催了几张、本就没超期几张。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class UrgeScreen {

    /** 扫描到的审批中单数 */
    private int runningCnt;
    /** 本轮新催办条数（同轮同级已催过的不重复催） */
    private int urgedCnt;
    /** 未超期、仍在时限内的单数 */
    private int withinCnt;
    /** 话术 */
    private String message;

    public int getRunningCnt() {
        return runningCnt;
    }

    public void setRunningCnt(int runningCnt) {
        this.runningCnt = runningCnt;
    }

    public int getUrgedCnt() {
        return urgedCnt;
    }

    public void setUrgedCnt(int urgedCnt) {
        this.urgedCnt = urgedCnt;
    }

    public int getWithinCnt() {
        return withinCnt;
    }

    public void setWithinCnt(int withinCnt) {
        this.withinCnt = withinCnt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
