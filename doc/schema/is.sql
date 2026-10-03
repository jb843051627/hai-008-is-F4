-- is 工业安全生产隐患管理 -- schema (hai-008)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：hai_008

CREATE TABLE IF NOT EXISTS t_is_acc_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '事故报告处置单号',
  stage int DEFAULT NULL COMMENT '当前处置段 0..3（初报/调查/处置/结案封卷）',
  status int DEFAULT NULL COMMENT '单子落定 0未起 1在办 2已封存',
  content varchar(255) DEFAULT NULL COMMENT '单上记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事故报告调查处置单';

CREATE TABLE IF NOT EXISTS t_is_check_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '执法检查数据上传批次号',
  row_no int DEFAULT NULL COMMENT '原表行次（表头不算，第一行数据=1；进门那一刻按原文件先后定死）',
  site_no varchar(64) DEFAULT NULL COMMENT '企业编号（对 t_is_enterprise.site_no）',
  check_date date DEFAULT NULL COMMENT '检查日期',
  hazard_cnt int DEFAULT NULL COMMENT '隐患条数（单次检查上限100）',
  item_code varchar(64) DEFAULT NULL COMMENT '被校验出的数据项类别码（入库行为空；退回填类别码）',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行检查得分（0..100，至多两位小数；并入行为空，得分只算一次）',
  merge_into int DEFAULT NULL COMMENT '并入前头行行次（同企业两行并一条时，后头行指向前头行；正式记录只数前头行）',
  status int DEFAULT NULL COMMENT '行落地情形 0待核 1已入库 2退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注（退回原由落这里，一行只一句，不许补第二句）',
  PRIMARY KEY (id),
  KEY idx_check_batch (batch_no, status),
  KEY idx_check_site_date (site_no, check_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='执法检查记录导入核对行';

-- 已建库补列（旧库逐句执行一次）：
-- ALTER TABLE t_is_check_row ADD COLUMN site_no varchar(64) DEFAULT NULL COMMENT '企业编号' AFTER row_no;
-- ALTER TABLE t_is_check_row ADD COLUMN check_date date DEFAULT NULL COMMENT '检查日期' AFTER site_no;
-- ALTER TABLE t_is_check_row ADD COLUMN hazard_cnt int DEFAULT NULL COMMENT '隐患条数' AFTER check_date;
-- ALTER TABLE t_is_check_row ADD COLUMN merge_into int DEFAULT NULL COMMENT '并入前头行行次' AFTER qty;
-- ALTER TABLE t_is_check_row ADD KEY idx_check_batch (batch_no, status);
-- ALTER TABLE t_is_check_row ADD KEY idx_check_site_date (site_no, check_date);

CREATE TABLE IF NOT EXISTS t_is_enterprise (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '危化企业底档代号',
  site_name varchar(128) DEFAULT NULL COMMENT '底档名称',
  site_type varchar(32) DEFAULT NULL COMMENT '底档类别(生产企业/储存企业)',
  road_name varchar(128) DEFAULT NULL COMMENT '所属行政区划(省—市—区—街道)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单档储存规模一档上限(吨)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档上限(吨)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档上限(吨)',
  status int DEFAULT NULL COMMENT '底档情形 0在用 1已退役',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='危化企业底档';

-- 重大危险源辨识单（档案卡）：一罐一评，写过不改。
-- 用哪条线只看 ident_date（辨识基准日）那天，不看开单的今天：补辨识的老罐子按建成那年有效的线评。
-- grade_level/rule_code/rule_eff_start 是落锤时的死档，事后复核另起 t_is_haz_card_review，永不回改本单。
-- grade_level：1/2/3 入名录各档；0 未达临界线、不入名录；NULL 无可用标准（no_standard=1）或脏值退回（不落单）。
CREATE TABLE IF NOT EXISTS t_is_haz_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '重大危险源档案卡号（辨识单号）',
  node_no int DEFAULT NULL COMMENT '本卡版本次序（同一罐多版辨识，只添不改）',
  site_id int DEFAULT NULL COMMENT '所属底档',
  site_no varchar(64) DEFAULT NULL COMMENT '所属底档代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '原始储存量(吨，报录原样落档)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本年度新增储存量(吨)',
  ident_date date DEFAULT NULL COMMENT '辨识基准日（取线只认这天；补辨识取建成日）',
  grade_level int DEFAULT NULL COMMENT '评定级别 1一级 2二级 3三级 0未达临界线；NULL=无可用标准',
  rule_code varchar(64) DEFAULT NULL COMMENT '所依判定线代号（落锤写死）',
  rule_eff_start datetime DEFAULT NULL COMMENT '所依判定线启用日（落锤写死）',
  no_standard int DEFAULT '0' COMMENT '无可用标准 0否 1是（明写，不许拿临近日期的线凑合）',
  content varchar(255) DEFAULT NULL COMMENT '校验串（随版本次序走）',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已压死',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_card_bill (bill_no, node_no),
  KEY idx_card_grade (grade_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='重大危险源辨识单（档案卡）';

-- 历史复核：凭原单代号调阅当年那条线重评。原结论与复核结论各留各的代号上下文，谁也不覆盖谁。
-- 复核只许按原单的 ident_date 取线，不许拿今天的标准改昨天的单。
CREATE TABLE IF NOT EXISTS t_is_haz_card_review (
  id bigint NOT NULL COMMENT '主键',
  review_no varchar(64) DEFAULT NULL COMMENT '复核结论号',
  card_id bigint NOT NULL COMMENT '被复核辨识单',
  bill_no varchar(64) DEFAULT NULL COMMENT '被复核辨识单号（抄存）',
  review_date date DEFAULT NULL COMMENT '复核作业日',
  qty decimal(12,2) DEFAULT NULL COMMENT '原始储存量抄存（取自原单，不重新采录）',
  ident_date date DEFAULT NULL COMMENT '辨识基准日抄存（取线仍按这天）',
  orig_grade_level int DEFAULT NULL COMMENT '原单级别',
  orig_rule_code varchar(64) DEFAULT NULL COMMENT '原单所依线代号',
  orig_rule_eff_start datetime DEFAULT NULL COMMENT '原单所依线启用日',
  review_grade_level int DEFAULT NULL COMMENT '复核级别（凭代号调线重评）',
  review_rule_code varchar(64) DEFAULT NULL COMMENT '复核对上的线代号（一般与原单同；缺线时为空）',
  review_rule_eff_start datetime DEFAULT NULL COMMENT '复核对上的线启用日',
  no_standard int DEFAULT '0' COMMENT '复核时无可用标准 0否 1是',
  consistent int DEFAULT NULL COMMENT '复核与原结论是否一致 1一致 0不一致',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_no (review_no),
  KEY idx_review_card (card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='辨识单历史复核结论';

-- 隐患源：检查发现、限期整改的隐患。催办条由本表生成；closed=1 表示已销号结案。
CREATE TABLE IF NOT EXISTS t_is_hazard (
  id bigint NOT NULL COMMENT '主键',
  hazard_no varchar(64) DEFAULT NULL COMMENT '隐患号（检查记录内唯一）',
  summary varchar(500) DEFAULT NULL COMMENT '隐患概况',
  dept_group varchar(32) DEFAULT NULL COMMENT '责任条线（候选部门分组代号）',
  pin_dept_id bigint DEFAULT NULL COMMENT '原定责任部门（可空；撤并判定看它）',
  rectify_date date DEFAULT NULL COMMENT '限改之日（当天最后一刻为条子期限）',
  closed int DEFAULT '0' COMMENT '是否已销号结案 0否 1是',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hazard_no (hazard_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='限期整改隐患源';

-- 责任部门候选池：条线内按在手活数加权派发；status=1 表示整个撤并。
CREATE TABLE IF NOT EXISTS t_is_dispatch_dept (
  id bigint NOT NULL COMMENT '主键',
  dept_name varchar(128) DEFAULT NULL COMMENT '责任部门名称',
  dept_group varchar(32) DEFAULT NULL COMMENT '所属责任条线',
  status int DEFAULT '0' COMMENT '部门情形 0在岗 1已撤并',
  order_num int DEFAULT '0' COMMENT '同权重时的次序',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_dept_group (dept_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='催办派发责任部门候选';

-- 派发窗口：日常一条（起止日为空，逢工作日/非节假日按点开闭）；专项检查月按起止日单开，不走日常。
CREATE TABLE IF NOT EXISTS t_is_dispatch_window (
  id bigint NOT NULL COMMENT '主键',
  win_type int DEFAULT NULL COMMENT '窗口种类 1日常 2专项检查月',
  win_name varchar(128) DEFAULT NULL COMMENT '窗口名',
  start_date date DEFAULT NULL COMMENT '专项起始日（含，日常为空）',
  end_date date DEFAULT NULL COMMENT '专项截止日（含，日常为空）',
  open_hh int DEFAULT NULL COMMENT '开窗时点（整点，含）',
  close_hh int DEFAULT NULL COMMENT '关窗时点（整点，不含）',
  status int DEFAULT '0' COMMENT '情形 0启用 1停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='催办派发窗口';

-- 法定节假日：落在表内的日子整段静默；周末若不调休上班由本表维护，全系统只认这一处。
CREATE TABLE IF NOT EXISTS t_is_holiday (
  id bigint NOT NULL COMMENT '主键',
  holiday_date date NOT NULL COMMENT '法定节假日日期',
  holiday_name varchar(64) DEFAULT NULL COMMENT '节假日名',
  PRIMARY KEY (id),
  UNIQUE KEY uk_holiday_date (holiday_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='法定节假日历';

-- 催办条。一条五样写死：条号 bill_no、期限最后一刻 due_at、还剩几日露面 amount、
-- 当前情形 status、隐患概况 hazard_summary 与负责部门（pin/accept 两栏，派出前看原定、派出后看接手）。
-- 期限口径：due_at 生成后任何动作都不许改；要调只许动 amount（还剩几日档）。
CREATE TABLE IF NOT EXISTS t_is_haz_task (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '催办条号',
  item_no varchar(64) DEFAULT NULL COMMENT '整改事项号（溯源）',
  due_at datetime DEFAULT NULL COMMENT '整改期限那一天最后一刻（23:59:59，定了不许动）',
  amount decimal(12,2) DEFAULT NULL COMMENT '到期前还剩几日就该露面（唯一允许调度的一档）',
  hazard_summary varchar(500) DEFAULT NULL COMMENT '隐患概况',
  dept_group varchar(32) DEFAULT NULL COMMENT '责任条线（候选部门分组）',
  pin_dept_id bigint DEFAULT NULL COMMENT '原定负责部门（条子生成时落定）',
  pin_dept_name varchar(128) DEFAULT NULL COMMENT '原定负责部门名',
  accept_dept_id bigint DEFAULT NULL COMMENT '实际接手部门',
  accept_dept_name varchar(128) DEFAULT NULL COMMENT '实际接手部门名',
  accepted_at datetime DEFAULT NULL COMMENT '接手时刻（与翻已派同写，缺一不算派成）',
  first_dispatched_at datetime DEFAULT NULL COMMENT '最初真正派出时刻（撤条留痕，永不覆盖）',
  urge_rounds int DEFAULT '0' COMMENT '催过几轮',
  suspend_reason varchar(500) DEFAULT NULL COMMENT '挂起原由',
  content varchar(255) DEFAULT NULL COMMENT '隐患事由与受派部门记要',
  status int DEFAULT NULL COMMENT '情形 0待派 1已派 2派不出去 3挂起 4已撤 5转办',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_bill_no (bill_no),
  KEY idx_status_due (status, due_at),
  KEY idx_accept_dept (accept_dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='隐患整改催办条';

-- 重大危险源辨识判定线册子：一行一条，换标准只添行、翻旗，永不删旧行。
-- 界值是各档"入门门槛（含本数）"：qty>=th1 一级、>=th2 二级、>=th3 三级；踩着界值算界值上那一档。
-- 某一档界值为空 = 不设这档（早年老线一级那格常空），顺到下一档继续推，不当断档、不报错。
-- 生效窗半开区间 [eff_start, eff_end)：交棒日当天起认新线；交棒日当天仍在进程中的跨日单沿用旧线
-- （旧线管到进程、新线从结局算）。status=1 的让位行只许历史复核按代号调阅，不许再参与现评。
CREATE TABLE IF NOT EXISTS t_is_lim_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '辨识判定线代号（市局SC专号/园区PZ专号；同代号可有多版，配启用日定位）',
  rule_name varchar(128) DEFAULT NULL COMMENT '判定线名',
  rule_source int DEFAULT NULL COMMENT '代号来源 1市局下发 2园区自拟',
  apply_type varchar(16) DEFAULT NULL COMMENT '适用企业类别（生产企业/储存企业各管各；空=两类通吃）',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '一级入门界值（含；空=不设一级档）',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二级入门界值（含；空=不设二级档）',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三级入门界值（含；空=不设三级档）',
  eff_start datetime DEFAULT NULL COMMENT '启用之日（辨识基准日落在此刻起算本线）',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含；空=仍现行)',
  priority int DEFAULT NULL COMMENT '判定线顺位(数值大的先说话；并列时代号排后者赢)',
  status int DEFAULT NULL COMMENT '判定线情形 0现行 1已让位(只许查历史)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_rule_code (rule_code),
  KEY idx_eff_window (eff_start, eff_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='重大危险源辨识判定线';

CREATE TABLE IF NOT EXISTS t_is_rect_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '整改处置签核单号',
  node_no int DEFAULT NULL COMMENT '当前所在道 0..2（班组提/安环科核/分管领导批）',
  sign_mode int DEFAULT NULL COMMENT '同口并印办法 0任一人 1两名点齐',
  need_count int DEFAULT NULL COMMENT '本道应落印数',
  sign_count int DEFAULT NULL COMMENT '本道已落印数',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='重大隐患整改处置签核单';

-- 轮次点算：同一事务里随派发一起落库；一屏三个数与库底数同一次点算。
-- result_type 只记真正扫描的轮次：1已扫 2本轮无活。窗口关闭/出错不写，不算轮次。
CREATE TABLE IF NOT EXISTS t_is_dispatch_round (
  id bigint NOT NULL COMMENT '主键',
  round_date date NOT NULL COMMENT '本轮日（墙钟 GMT+8）',
  win_type int NOT NULL COMMENT '本轮窗口 1日常 2专项',
  win_name varchar(128) DEFAULT NULL COMMENT '本轮窗口名',
  started_at datetime DEFAULT NULL COMMENT '本轮起刻',
  finished_at datetime DEFAULT NULL COMMENT '本轮止刻',
  wait_cnt int DEFAULT '0' COMMENT '本轮待派几条',
  dispatched_cnt int DEFAULT '0' COMMENT '本轮已派几条',
  suspended_cnt int DEFAULT '0' COMMENT '本轮挂起几条',
  result_type int DEFAULT NULL COMMENT '结果 1已扫 2本轮无活',
  message varchar(255) DEFAULT NULL COMMENT '一屏话术',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_round_date_win (round_date, win_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='催办派发轮次点算';

-- 派发留痕：生成/挂起/派出/催办/撤条/转办/调档全过痕；撤条时把原期限、初派时刻原样抄存。
CREATE TABLE IF NOT EXISTS t_is_dispatch_log (
  id bigint NOT NULL COMMENT '主键',
  round_id bigint DEFAULT NULL COMMENT '所属轮次（手工动作可空）',
  bill_id bigint DEFAULT NULL COMMENT '催办条',
  bill_no varchar(64) DEFAULT NULL COMMENT '催办条号',
  action varchar(32) DEFAULT NULL COMMENT '动作 CREATE生成 SUSPEND挂起 DISPATCH派出 URGE催办 REVOKE撤条 TRANSFER转办 TUNE调档',
  from_status int DEFAULT NULL COMMENT '动作前情形',
  to_status int DEFAULT NULL COMMENT '动作后情形',
  dept_id bigint DEFAULT NULL COMMENT '本动作关涉部门',
  dept_name varchar(128) DEFAULT NULL COMMENT '本动作关涉部门名',
  orig_due_at datetime DEFAULT NULL COMMENT '抄存：最初整改期限最后一刻',
  first_dispatched_at datetime DEFAULT NULL COMMENT '抄存：最初真正派出时刻',
  detail varchar(500) DEFAULT NULL COMMENT '事由/原由',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_log_bill (bill_id),
  KEY idx_log_round (round_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='催办派发留痕';

-- 初始档案数据（id=1 启用 / id=2 停用）
-- 初始档案数据：id=0 在用、id=1 已退役
-- 储存规模上限 50/200/500 吨：等于上限算高一档。
INSERT IGNORE INTO t_is_enterprise (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'EN00', '临江市东湖化工园区（在用）', '生产企业', '临江省—临江市—东湖区—东湖街道', 50.00, 200.00, 500.00, 0, 0, 'seed', NOW()),
       (1, 'EN01', '临江市城南气体储运（已退役）', '储存企业', '临江省—临江市—城南区—望湖街道', 50.00, 200.00, 500.00, 1, 0, 'seed', NOW());

-- 责任部门候选：危化条线三科在岗；另有一个整组撤并的条线（QY），用于生成时挂起演示。
INSERT IGNORE INTO t_is_dispatch_dept (id, dept_name, dept_group, status, order_num, del_flag, create_by, create_time)
VALUES (1001, '危化监管一科', 'HW', 0, 1, 0, 'seed', NOW()),
       (1002, '危化监管二科', 'HW', 0, 2, 0, 'seed', NOW()),
       (1003, '执法大队', 'HW', 0, 3, 0, 'seed', NOW()),
       (1004, '原企管三所（整组撤并）', 'QY', 1, 4, 0, 'seed', NOW());

-- 日常窗口：每天 08:00（含）—20:00（不含）；夜间与法定节假日整段静默。
INSERT IGNORE INTO t_is_dispatch_window (id, win_type, win_name, start_date, end_date, open_hh, close_hh, status, del_flag, create_by, create_time)
VALUES (1, 1, '日常派发窗口', NULL, NULL, 8, 20, 0, 0, 'seed', NOW()),
       (2, 2, '专项检查月窗口', '2026-06-01', '2026-06-15', 8, 20, 0, 0, 'seed', NOW());

-- 法定节假日示例（以当年国务院公告为准，由管理员维护）。
INSERT IGNORE INTO t_is_holiday (id, holiday_date, holiday_name)
VALUES (1, '2026-01-01', '元旦'),
       (2, '2026-02-17', '春节'),
       (3, '2026-02-18', '春节'),
       (4, '2026-02-19', '春节'),
       (5, '2026-02-20', '春节'),
       (6, '2026-02-21', '春节'),
       (7, '2026-02-22', '春节'),
       (8, '2026-02-23', '春节');

-- 隐患源示例：一条在办、一条已销号（生成时应挂起）、一条原定部门已撤并（生成时应挂起）。
INSERT IGNORE INTO t_is_hazard (id, hazard_no, summary, dept_group, pin_dept_id, rectify_date, closed, del_flag, create_by, create_time)
VALUES (1, 'YH20260001', '东湖化工园区罐区可燃气体报警器失效', 'HW', 1001, '2026-09-30', 0, 0, 'seed', NOW()),
       (2, 'YH20260002', '城南气体储运消防通道堆占（已复查销号）', 'HW', 1002, '2026-09-29', 1, 0, 'seed', NOW()),
       (3, 'YH20260003', '东湖园区动火作业票证缺签', 'QY', 1004, '2026-09-28', 0, 0, 'seed', NOW());

-- 判定线册子（只添行、翻旗，永不删旧行）。
-- SC=市局下发专号，PZ=园区自拟专号；界值为各档入门门槛（含本数），一级最重。
-- 2025-01-01 交棒：旧线管到 2024-12-31，新线从 2025-01-01 起算（[起,止) 半开）。
INSERT IGNORE INTO t_is_lim_rule
(id, rule_code, rule_name, rule_source, apply_type, th1_max, th2_max, th3_max, eff_start, eff_end, priority, status, del_flag, create_by, create_time, remark)
VALUES
-- 早年间入册的老线：一级那格空着——不设一级档，顺到二级继续推。供老罐子按建成年补辨识调阅。
(101, 'SC1990-P', '老国标生产企业判定线（1990年册）', 1, '生产企业', NULL, 300.00, 100.00,
 '1990-01-01 00:00:00', '2009-06-01 00:00:00', 10, 1, 0, 'seed', NOW(), '一级档未设，空值顺档'),
-- 旧国标（去年之前全市在用，线松）
(102, 'SC2009-P', '旧国标生产企业判定线', 1, '生产企业', 500.00, 200.00, 50.00,
 '2009-06-01 00:00:00', '2025-01-01 00:00:00', 10, 1, 0, 'seed', NOW(), '已让位，只许历史复核调阅'),
(103, 'SC2009-S', '旧国标储存企业判定线', 1, '储存企业', 1000.00, 500.00, 100.00,
 '2009-06-01 00:00:00', '2025-01-01 00:00:00', 10, 1, 0, 'seed', NOW(), '已让位，只许历史复核调阅'),
-- 现行新国标（去年换册，临界线收紧）
(104, 'SC2025-P', '新国标生产企业判定线', 1, '生产企业', 300.00, 100.00, 30.00,
 '2025-01-01 00:00:00', NULL, 10, 0, 0, 'seed', NOW(), '现行'),
(105, 'SC2025-S', '新国标储存企业判定线', 1, '储存企业', 600.00, 300.00, 60.00,
 '2025-01-01 00:00:00', NULL, 10, 0, 0, 'seed', NOW(), '现行'),
-- 园区自拟专号：自我加压，比市局线更紧、顺位更高，储存企业现评听它的
(106, 'PZ2025-S', '东湖园区储存企业加严判定线', 2, '储存企业', 500.00, 250.00, 50.00,
 '2025-01-01 00:00:00', NULL, 20, 0, 0, 'seed', NOW(), '园区自拟，顺位高于市局线');

-- ===================== 隐患整改申请逐级签批 =====================
-- 层级次序由平台配置（t_is_rect_level）写死，不抄进申请单；单子上只挂"当前待签层级"一个号，
-- 一张单同一时刻只挂在一级手上，不许两人并签。链条永远现取：status=0 按 level_no 升序。
CREATE TABLE IF NOT EXISTS t_is_rect_level (
  id bigint NOT NULL COMMENT '主键',
  level_no int DEFAULT NULL COMMENT '层级次序（1起；提交后进最小号，通过进比它大的下一号）',
  level_name varchar(64) DEFAULT NULL COMMENT '层级名（班组提/安环科核/分管领导批……）',
  signer_role_id bigint DEFAULT NULL COMMENT '本层签收角色（角色对不上，这层的单连看都看不到）',
  signer_role_name varchar(64) DEFAULT NULL COMMENT '本层签收角色名（抄存）',
  urge_hours int DEFAULT '48' COMMENT '卡在本层的时限（小时）：进层起超过此时限未签收即催办',
  status int DEFAULT '0' COMMENT '情形 0启用 1停用（有在途单挂在本层时不许停用）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_level_no (level_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='整改申请签批层级配置（平台写死）';

-- 整改申请主单：一行一张，字段固定。状态只许 0未提交/1审批中/2已通过/3已驳回/4已作废，不许自造。
-- 结论 1通过/2驳回，结案那一刻写死；结案后只许管理员把 status 翻成 4归档，其余各栏（含结论、等级）谁都不许动。
-- 补材料退回申请人时回到 0未提交，round_no 加一后重新提交、从第一级重头走起，旧轮次意见全部留着不抹。
CREATE TABLE IF NOT EXISTS t_is_rect_apply (
  id bigint NOT NULL COMMENT '主键',
  apply_no varchar(64) DEFAULT NULL COMMENT '申请单号',
  hazard_id bigint DEFAULT NULL COMMENT '关联隐患（哪条）',
  hazard_no varchar(64) DEFAULT NULL COMMENT '关联隐患号（抄存）',
  hazard_level int DEFAULT NULL COMMENT '关联隐患级别 1一级 2二级 3三级（落单后不许改）',
  applicant varchar(64) DEFAULT NULL COMMENT '申请人',
  submit_time datetime DEFAULT NULL COMMENT '最近一次提交时间（补材料重提刷新为重提时刻）',
  cur_level_no int DEFAULT NULL COMMENT '当前待签层级（只此一个；未提交/结案后为空）',
  status int DEFAULT NULL COMMENT '状态 0未提交 1审批中 2已通过 3已驳回 4已作废',
  conclusion int DEFAULT NULL COMMENT '结论 1通过 2驳回（结案写死，之前为空）',
  closed_time datetime DEFAULT NULL COMMENT '结案时间（结案写死，之前为空）',
  round_no int DEFAULT '1' COMMENT '当前签批轮次（首轮1；每补材料重提一轮加一）',
  level_entered_at datetime DEFAULT NULL COMMENT '进入当前待签层级的时刻（催办计时起点）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注（申请事由/补齐说明落这里）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_apply_no (apply_no),
  KEY idx_apply_status_level (status, cur_level_no),
  KEY idx_apply_applicant (applicant)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='隐患整改申请单';

-- 签收意见留痕：一张单多条，按轮次+层级+时刻排，谁签的、什么时候签的、什么意见一查就有。
-- 签收四动作 PASS通过/REJECT驳回/SUPPLEMENT补充材料/TRANSFER转交他级，必带层级与意见；
-- 另记流程事件 SUBMIT提交/RESUBMIT补齐重提/WITHDRAW撤回/ARCHIVE归档（层级可空）。
-- 代办：实际落笔者 signer，原签署人 intended_signer，代办原因 agent_reason——代办不改层级归属，原签署人该是谁还是谁。
-- 转交他级：本层意见 comment 必须写全（不许只留一句"转走了"），target_level_* 写明转到哪级。
CREATE TABLE IF NOT EXISTS t_is_rect_sign (
  id bigint NOT NULL COMMENT '主键',
  apply_id bigint NOT NULL COMMENT '所属申请单',
  apply_no varchar(64) DEFAULT NULL COMMENT '申请单号（抄存）',
  round_no int DEFAULT NULL COMMENT '本轮签批轮次（重提后在新轮次里重走第一级）',
  level_no int DEFAULT NULL COMMENT '意见所属层级（流程事件为空）',
  level_name varchar(64) DEFAULT NULL COMMENT '层级名（抄存）',
  action varchar(16) DEFAULT NULL COMMENT '动作 SUBMIT/PASS/REJECT/SUPPLEMENT/TRANSFER/RESUBMIT/WITHDRAW/ARCHIVE',
  signer varchar(64) DEFAULT NULL COMMENT '实际落笔者（代办时即代办人）',
  intended_signer varchar(64) DEFAULT NULL COMMENT '原签署人（代办时与落笔者不同；正常签收两者相同）',
  agent_flag int DEFAULT '0' COMMENT '是否代办 0否 1是',
  agent_reason varchar(255) DEFAULT NULL COMMENT '代办原因（代办必填）',
  target_level_no int DEFAULT NULL COMMENT '转交目标层级号（仅转交）',
  target_level_name varchar(64) DEFAULT NULL COMMENT '转交目标层级名（仅转交）',
  comment varchar(500) DEFAULT NULL COMMENT '本层处理意见（签收/转交必填，转交须把本级意见记全）',
  sign_time datetime DEFAULT NULL COMMENT '签署/事件时刻',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_sign_apply (apply_id, sign_time, id),
  KEY idx_sign_round_level (apply_id, round_no, level_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='整改申请签收意见留痕';

-- 催办留痕：超过层级配置的 urge_hours 还卡在某一级，定时扫描催一次。
-- 去重口径：同一轮同一级、且"本次进层时刻"不晚于上次催办时刻的不重复催
-- （转交出去又转回来会刷新进层时刻，再次超时照样催）。
-- 已作废/已结案的单扫不到（只扫 status=1 审批中），催不催都不碰单子各栏。
CREATE TABLE IF NOT EXISTS t_is_rect_urge (
  id bigint NOT NULL COMMENT '主键',
  apply_id bigint NOT NULL COMMENT '所属申请单',
  apply_no varchar(64) DEFAULT NULL COMMENT '申请单号（抄存）',
  round_no int DEFAULT NULL COMMENT '催办时所在轮次',
  level_no int DEFAULT NULL COMMENT '卡住的层级',
  level_name varchar(64) DEFAULT NULL COMMENT '卡住的层级名',
  overdue_hours int DEFAULT NULL COMMENT '催办时已卡了多少小时',
  urged_at datetime DEFAULT NULL COMMENT '催办时刻',
  detail varchar(500) DEFAULT NULL COMMENT '催办话术',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_urge_apply_round (apply_id, round_no, level_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='整改申请层级超时催办留痕';

-- 签批层级种子：三级次序写死——班组提 → 安环科核 → 分管领导批。
-- 签收角色取系统现有角色演示：一、二级挂"用户"角色，末级挂"管理员"角色；上线按平台实际岗位角色改配。
INSERT IGNORE INTO t_is_rect_level (id, level_no, level_name, signer_role_id, signer_role_name, urge_hours, status, del_flag, create_by, create_time, remark)
VALUES (9001, 1, '班组提报', 488289006124007424, '用户', 48, 0, 0, 'seed', NOW(), '第一级：提交后进此级'),
       (9002, 2, '安环科审核', 488289006124007424, '用户', 48, 0, 0, 'seed', NOW(), '第二级'),
       (9003, 3, '分管领导审批', 488243256161730560, '管理员', 72, 0, 0, 'seed', NOW(), '末级：此级通过即结案通过');
