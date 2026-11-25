#!/usr/bin/env python3
"""
造 100 户租金数据：楼栋 → 房间 → 租户 → 合同 → 抄表 → 账单 → 收款 → 押金。

设计约束（保证数据自洽，不产生脏状态）：
  1. 合同 ACTIVE 才占房；房间 status 与合同一一对应。
  2. 账单月份从合同起始月算到 ≤ 当月，绝不为「合同开始前」出账。
  3. 已支付账单必须有等额 payment_record；未付/部分付则按比例留缺口。
  4. 抄表读数单调递增（按房间独立序列），否则水电用量会是负数。
  5. 押金：每份合同一条 RECEIVE；已到期/已解约的补一条 REFUND。
  6. 租期 6~18 个月，按用户要求分布。

用法：python3 seed_tenants.py [--dry-run]
"""
import random
import subprocess
import sys
from datetime import date, timedelta
from calendar import monthrange

DB = "house_db"
MYSQL = ["mysql", "-uroot", "-proot", DB, "-N", "-B"]

# ---------- 基础语料 ----------
SURNAMES = list("王李张刘陈杨黄赵吴周徐孙马朱胡林郭何高罗郑梁谢宋唐许韩冯邓曹彭曾肖田董袁潘于蒋蔡余杜叶程苏魏吕丁任沈姚卢姜崔钟谭陆汪范金石廖贾夏韦付方白邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵万钱严覃武戴莫孔向汤")
GIVEN_1 = list("伟芳娜敏静丽强磊军洋勇艳杰娟涛明超秀霞平刚桂英华玉兰萍红辉建国志伟晨阳欣然子涵雨轩浩然思远嘉怡若曦")
GIVEN_2 = list("华明杰强磊军洋勇涛超刚辉建国志伟晨阳欣然涵轩然远怡曦宁康乐安福瑞祥")

LAYOUTS = [
    ("一室一厅", 45, 2200, 2800),
    ("两室一厅", 68, 3000, 3800),
    ("三室一厅", 95, 4200, 5200),
]

BUILDINGS = [
    ("阳光花园B栋", "B", 8, "杭州市西湖区文一西路 108 号"),
    ("翠竹苑1号楼", "C", 8, "杭州市拱墅区莫干山路 55 号"),
    ("梧桐里公寓", "D", 9, "杭州市滨江区江南大道 210 号"),
]

PAY_METHODS = ["WECHAT", "ALIPAY", "CASH", "BANK"]
TODAY = date(2026, 11, 25)


def sql(statements):
    """一次性执行，失败回滚。"""
    payload = "SET FOREIGN_KEY_CHECKS=0;\nSTART TRANSACTION;\n" + "\n".join(statements) + "\nCOMMIT;\n"
    p = subprocess.run(MYSQL + ["--batch"], input=payload, text=True, capture_output=True)
    if p.returncode != 0:
        print("SQL ERROR:\n" + p.stderr[:4000], file=sys.stderr)
        sys.exit(1)
    return p.stdout


def q(v):
    """SQL 字面量。"""
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return str(v)
    if isinstance(v, date):
        return f"'{v.isoformat()}'"
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def pick_name(used):
    while True:
        n = random.choice(SURNAMES) + random.choice(GIVEN_1)
        if random.random() < 0.45:
            n += random.choice(GIVEN_2)
        if n not in used:
            used.add(n)
            return n


def make_phone(idx, used):
    while True:
        p = "13" + random.choice("56789") + "".join(random.choice("0123456789") for _ in range(8))
        if p not in used:
            used.add(p)
            return p


def add_months(d, n):
    y = d.year + (d.month - 1 + n) // 12
    m = (d.month - 1 + n) % 12 + 1
    day = min(d.day, monthrange(y, m)[1])
    return date(y, m, day)


def month_list(start, end):
    """[start月, end月] 之间所有月份，YYYY-MM。"""
    out = []
    cur = date(start.year, start.month, 1)
    last = date(end.year, end.month, 1)
    while cur <= last:
        out.append(f"{cur.year:04d}-{cur.month:02d}")
        cur = add_months(cur, 1)
    return out


def main():
    dry = "--dry-run" in sys.argv
    random.seed(20261125)

    stmts = []

    # ---------- 1. 清掉上一次的种子数据 ----------
    # 用 tenant.remark='SEED_2026' 作标记，绝不碰手工建的正式数据。
    seed_codes = [b[1] for b in BUILDINGS]
    code_in = ",".join(q(c) for c in seed_codes)

    stmts.append("DELETE FROM payment_record WHERE bill_id IN ("
                 "SELECT id FROM bill WHERE tenant_phone IN (SELECT phone FROM tenant WHERE remark='SEED_2026'));")
    stmts.append("DELETE FROM bill_detail WHERE bill_id IN ("
                 "SELECT id FROM bill WHERE tenant_phone IN (SELECT phone FROM tenant WHERE remark='SEED_2026'));")
    stmts.append("DELETE FROM bill WHERE tenant_phone IN (SELECT phone FROM tenant WHERE remark='SEED_2026');")
    stmts.append("DELETE FROM deposit_record WHERE contract_id IN ("
                 "SELECT id FROM contract WHERE tenant_id IN (SELECT id FROM tenant WHERE remark='SEED_2026'));")
    stmts.append("DELETE FROM contract WHERE tenant_id IN (SELECT id FROM tenant WHERE remark='SEED_2026');")
    stmts.append("DELETE FROM meter_reading WHERE room_id IN ("
                 "SELECT r.id FROM room r JOIN building bl ON r.building_id=bl.id WHERE bl.code IN (%s));" % code_in)
    stmts.append("DELETE FROM tenant WHERE remark='SEED_2026';")
    stmts.append("DELETE FROM room WHERE building_id IN (SELECT id FROM building WHERE code IN (%s));" % code_in)
    stmts.append("DELETE FROM building WHERE code IN (%s);" % code_in)

    if dry:
        print("--- 清理语句 ---")
        for s in stmts:
            print(s)
        print(f"\n将创建 {len(BUILDINGS)} 栋楼，目标 100 户")
        return

    sql(stmts)

    stmts = []
    used_names, used_phones, used_cards = set(), set(), set()

    # 统计
    n_tenant = n_contract = n_bill = n_payment = n_detail = n_deposit = n_meter = 0

    for bname, bcode, floors, baddr in BUILDINGS:
        stmts.append(
            "INSERT INTO building(name,code,address,total_floors,water_unit_price,electric_unit_price) VALUES "
            f"({q(bname)},{q(bcode)},{q(baddr)},{floors},4.5000,1.2000);"
        )
        stmts.append(f"SET @bid = LAST_INSERT_ID();")

        # 每层 4 户，房号 楼层01~04
        rooms_per_floor = 4
        for floor in range(1, floors + 1):
            for i in range(1, rooms_per_floor + 1):
                rno = f"{floor}{i:02d}"
                layout, area, rent_lo, rent_hi = random.choice(LAYOUTS)
                stmts.append(
                    "INSERT INTO room(building_id,floor,room_number,layout,area,status) VALUES "
                    f"(@bid,{floor},{q(rno)},{q(layout)},{area},'IDLE');"
                )

    sql(stmts)
    stmts = []

    # 取回刚建的房间
    rows = sql([f"SELECT r.id, r.room_number, b.name FROM room r JOIN building b ON r.building_id=b.id "
                f"WHERE b.code IN (%s) ORDER BY r.id;" % code_in])
    rooms = []
    for line in rows.strip().splitlines():
        if not line.strip():
            continue
        rid, rno, bname_ = line.split("\t")
        rooms.append({"id": int(rid), "room_number": rno, "building_name": bname_})

    print(f"房间数：{len(rooms)}")

    # 目标 100 户：从房间里挑 100 个
    target = min(100, len(rooms))
    occupied = random.sample(rooms, target)

    # 租期 6~18 个月，均匀分布但避免全挤在端点
    tenures = [6, 7, 8, 9, 10, 11, 12, 12, 12, 13, 14, 15, 16, 17, 18]

    for idx, room in enumerate(occupied):
        # --- 租户 ---
        name = pick_name(used_names)
        phone = make_phone(idx, used_phones)
        card = "3301" + str(random.randint(10, 99)) + str(random.randint(1960, 2003)) + \
               f"{random.randint(1,12):02d}{random.randint(1,28):02d}" + f"{random.randint(1000,9999)}"
        while card in used_cards:
            card = card[:-4] + f"{random.randint(1000,9999)}"
        used_cards.add(card)

        stmts.append(
            "INSERT INTO tenant(name,phone,id_card,emergency_contact,emergency_phone,remark,register_source) VALUES "
            f"({q(name)},{q(phone)},{q(card)},{q(random.choice(SURNAMES)+'先生')},"
            f"{q(make_phone(idx, used_phones))},'SEED_2026','ADMIN');"
        )
        stmts.append("SET @tid = LAST_INSERT_ID();")

        # --- 合同期：起始日分散在过去 6~18 个月 ---
        tenure = tenures[idx % len(tenures)]
        # 结束日 = 今天 + 剩余时间；起始日 = 结束日 - tenure 月
        # 让一部分合同即将到期（1~3 月内），一部分还很久
        months_left = random.randint(1, 12)
        end_d = add_months(TODAY, months_left)
        start_d = add_months(end_d, -tenure)

        # 小概率让合同已到期/已解约，丰富状态分布
        roll = random.random()
        status = "ACTIVE"
        if roll < 0.08:
            status = "EXPIRED"
            end_d = TODAY - timedelta(days=random.randint(5, 60))
            start_d = add_months(end_d, -tenure)
        elif roll < 0.13:
            status = "TERMINATED"
            end_d = TODAY - timedelta(days=random.randint(10, 90))
            start_d = add_months(end_d, -tenure)

        layout, area, rent_lo, rent_hi = random.choice(LAYOUTS)
        rent = random.randrange(rent_lo, rent_hi + 1, 100)
        deposit = rent

        cno = f"CT{TODAY.strftime('%y%m')}{idx+1:04d}{random.randint(100,999)}"

        stmts.append(
            "INSERT INTO contract(contract_no,room_id,tenant_id,start_date,end_date,status,monthly_rent,deposit,"
            "payment_method,room_number,building_name,tenant_name,tenant_phone) VALUES "
            f"({q(cno)},{room['id']},@tid,{q(start_d)},{q(end_d)},{q(status)},{rent},{deposit},'MONTHLY',"
            f"{q(room['room_number'])},{q(room['building_name'])},{q(name)},{q(phone)});"
        )
        stmts.append("SET @cid = LAST_INSERT_ID();")

        # 占用房间
        if status == "ACTIVE":
            stmts.append(f"UPDATE room SET status='RENTED' WHERE id={room['id']};")

        # --- 押金 ---
        stmts.append(
            "INSERT INTO deposit_record(contract_id,type,amount,date,remark) VALUES "
            f"(@cid,'RECEIVE',{deposit},{q(start_d)},'签约押金');"
        )
        if status in ("EXPIRED", "TERMINATED"):
            refund = deposit if random.random() < 0.75 else deposit - random.choice([100, 200, 300])
            stmts.append(
                "INSERT INTO deposit_record(contract_id,type,amount,date,remark) VALUES "
                f"(@cid,'REFUND',{refund},{q(end_d + timedelta(days=random.randint(1,10)))},'退租退还押金');"
            )

        # --- 抄表：按房间独立递增序列，覆盖合同期 ---
        water = random.randint(100, 400)
        elec = random.randint(500, 2000)

        # 先补一条基线读数，放在「合同起始月的上一个月最后一天」。
        #
        # 账单的水电用量算法是「本期读数 - 上期读数」，其中
        # 「上期读数」= record_date < 账单月月初 的最后一条。
        # 所以基线必须落在起始月之前，否则首月算不出用量，
        # 会产出一堆「零水电」账单。
        # 早期版本把基线放在 start_d 前一天，当 start_d 在中旬时
        # 基线仍落在同一个自然月内，首月账单照样没有水电——这里修正。
        baseline_date = date(start_d.year, start_d.month, 1) - timedelta(days=1)
        stmts.append(
            "INSERT INTO meter_reading(room_id,record_date,water_reading,electric_reading) VALUES "
            f"({room['id']},{q(baseline_date)},{water},{elec});"
        )

        cur = date(start_d.year, start_d.month, 1)
        last_month = date(end_d.year, end_d.month, 1)
        if last_month > date(TODAY.year, TODAY.month, 1):
            last_month = date(TODAY.year, TODAY.month, 1)

        meter_dates = []
        while cur <= last_month:
            # 每月 1~3 号抄表
            rd = date(cur.year, cur.month, random.randint(1, 3))
            if rd <= TODAY and rd > baseline_date:
                meter_dates.append(rd)
            cur = add_months(cur, 1)

        readings = [(baseline_date, water, elec)]
        for rd in meter_dates:
            water += random.randint(3, 12)
            elec += random.randint(60, 260)
            readings.append((rd, water, elec))
            stmts.append(
                "INSERT INTO meter_reading(room_id,record_date,water_reading,electric_reading) VALUES "
                f"({room['id']},{q(rd)},{water},{elec});"
            )

        # --- 账单：合同起始月 → 当前月（或合同结束月） ---
        bill_months = month_list(start_d, min(end_d, TODAY))
        # 当月不出账（后端规则是生成上月的账单）
        current_ym = f"{TODAY.year:04d}-{TODAY.month:02d}"
        bill_months = [m for m in bill_months if m < current_ym]

        for bi, bm in enumerate(bill_months):
            by, bmon = int(bm[:4]), int(bm[5:7])
            period_start = date(by, bmon, 1)

            # 水电用量：取该月抄表与上月抄表差
            this_rd = next((r for r in readings if r[0].year == by and r[0].month == bmon), None)
            prev_rd = None
            if this_rd:
                before = [r for r in readings if r[0] < this_rd[0]]
                prev_rd = before[-1] if before else None
            if this_rd and prev_rd:
                w_use = max(0, this_rd[1] - prev_rd[1])
                e_use = max(0, this_rd[2] - prev_rd[2])
            else:
                w_use, e_use = 0, 0

            water_amt = round(w_use * 4.5, 2)
            elec_amt = round(e_use * 1.2, 2)
            total = round(rent + water_amt + elec_amt, 2)
            due = period_start + timedelta(days=5)

            # 状态分布：老的月份基本已付；最近 1~3 个月留一些未付
            months_ago = (TODAY.year - by) * 12 + (TODAY.month - bmon)
            if status in ("EXPIRED", "TERMINATED"):
                pay_state = "PAID"
            elif months_ago <= 1:
                pay_state = random.choices(["PENDING", "PAID", "PARTIAL"], weights=[45, 40, 15])[0]
            elif months_ago <= 3:
                pay_state = random.choices(["PENDING", "PAID", "PARTIAL"], weights=[15, 78, 7])[0]
            else:
                pay_state = random.choices(["PAID", "VOID"], weights=[97, 3])[0]

            stmts.append(
                "INSERT INTO bill(contract_id,bill_month,due_date,total_amount,status,generated_date,"
                "room_id,room_number,building_name,tenant_name,tenant_phone) VALUES "
                f"(@cid,{q(bm)},{q(due)},{total},{q(pay_state)},"
                f"{q(period_start + timedelta(days=1))},"
                f"{room['id']},{q(room['room_number'])},{q(room['building_name'])},{q(name)},{q(phone)});"
            )
            stmts.append("SET @bid2 = LAST_INSERT_ID();")
            n_bill += 1

            # 明细
            stmts.append(
                "INSERT INTO bill_detail(bill_id,name,usage_amount,amount) VALUES "
                f"(@bid2,'基础房费',1.0,{rent});"
            )
            if water_amt > 0:
                stmts.append(
                    "INSERT INTO bill_detail(bill_id,name,usage_amount,amount) VALUES "
                    f"(@bid2,'水费',{w_use},{water_amt});"
                )
            if elec_amt > 0:
                stmts.append(
                    "INSERT INTO bill_detail(bill_id,name,usage_amount,amount) VALUES "
                    f"(@bid2,'电费',{e_use},{elec_amt});"
                )

            # 收款
            if pay_state == "PAID":
                paid = total
            elif pay_state == "PARTIAL":
                paid = round(total * random.choice([0.3, 0.4, 0.5, 0.6]), 2)
            else:
                paid = None

            if paid:
                pay_d = period_start + timedelta(days=random.randint(0, 12))
                if pay_d > TODAY:
                    pay_d = TODAY
                stmts.append(
                    "INSERT INTO payment_record(bill_id,paid_amount,paid_date,payment_method,remark) VALUES "
                    f"(@bid2,{paid},{q(pay_d)},{q(random.choice(PAY_METHODS))},'收款');"
                )

        n_tenant += 1
        n_contract += 1

    if dry:
        print(f"将创建：{n_tenant} 租户 / {n_contract} 合同 / {n_bill} 账单")
        return

    sql(stmts)

    # ---------- 校验 ----------
    check = sql([
        "SELECT 'building', COUNT(*) FROM building WHERE code IN (%s);" % code_in,
        "SELECT 'room', COUNT(*) FROM room r JOIN building b ON r.building_id=b.id WHERE b.code IN (%s);" % code_in,
        "SELECT 'tenant', COUNT(*) FROM tenant WHERE remark='SEED_2026';",
        "SELECT 'contract', COUNT(*) FROM contract WHERE tenant_id IN (SELECT id FROM tenant WHERE remark='SEED_2026');",
        "SELECT 'bill', COUNT(*) FROM bill WHERE tenant_phone IN (SELECT phone FROM tenant WHERE remark='SEED_2026');",
        "SELECT 'payment', COUNT(*) FROM payment_record WHERE bill_id IN (SELECT id FROM bill WHERE tenant_phone IN (SELECT phone FROM tenant WHERE remark='SEED_2026'));",
        "SELECT 'meter', COUNT(*) FROM meter_reading WHERE room_id IN (SELECT r.id FROM room r JOIN building b ON r.building_id=b.id WHERE b.code IN (%s));" % code_in,
        "SELECT 'deposit', COUNT(*) FROM deposit_record WHERE contract_id IN (SELECT id FROM contract WHERE tenant_id IN (SELECT id FROM tenant WHERE remark='SEED_2026'));",
    ])
    print("\n=== 落库结果 ===")
    for line in check.strip().splitlines():
        k, v = line.split("\t")
        print(f"  {k:10s} {v}")

    # 一致性校验
    bad = sql([
        # 已支付账单金额之和 vs 收款之和
        "SELECT COUNT(*) FROM (SELECT b.id, b.total_amount, COALESCE(SUM(p.paid_amount),0) paid "
        "FROM bill b LEFT JOIN payment_record p ON p.bill_id=b.id "
        "WHERE b.status='PAID' AND b.tenant_phone IN (SELECT phone FROM tenant WHERE remark='SEED_2026') "
        "GROUP BY b.id, b.total_amount HAVING ABS(paid - b.total_amount) > 0.01) x;",
        # 账单月份早于合同起始
        "SELECT COUNT(*) FROM bill b JOIN contract c ON b.contract_id=c.id "
        "WHERE b.bill_month < DATE_FORMAT(c.start_date,'%%Y-%%m') "
        "AND b.tenant_phone IN (SELECT phone FROM tenant WHERE remark='SEED_2026');",
        # 房间被多份 ACTIVE 合同占用
        "SELECT COUNT(*) FROM (SELECT room_id FROM contract WHERE status='ACTIVE' "
        "AND tenant_id IN (SELECT id FROM tenant WHERE remark='SEED_2026') "
        "GROUP BY room_id HAVING COUNT(*)>1) y;",
    ])
    labels = ["已付账单金额不匹配", "账单早于合同起始", "房间被重复占用"]
    print("\n=== 一致性校验（应全为 0）===")
    for lab, line in zip(labels, bad.strip().splitlines()):
        print(f"  {lab}: {line}")


if __name__ == "__main__":
    main()
