import pymysql
import bcrypt
from datetime import datetime, date

# DB config - match backend application.yml
DB_HOST = 'localhost'
DB_PORT = 3306
DB_USER = 'root'
DB_PASS = 'root'
DB_NAME = 'house_db'

conn = pymysql.connect(host=DB_HOST, port=DB_PORT, user=DB_USER, password=DB_PASS, database=DB_NAME, charset='utf8mb4')

def reset_admin_password(newpass='admin123'):
    hashed = bcrypt.hashpw(newpass.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
    with conn.cursor() as cur:
        sql = "UPDATE sys_user SET password=%s WHERE username='admin'"
        cur.execute(sql, (hashed,))
        conn.commit()
        print('reset admin rows:', cur.rowcount)

def ensure_debug_sms_config():
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM sms_config LIMIT 1")
        r = cur.fetchone()
        if r:
            print('sms_config exists id=', r[0])
            return r[0]
        cur.execute("INSERT INTO sms_config(provider, access_key, access_secret, signature, template_code, debug_mode, status) VALUES(%s,%s,%s,%s,%s,%s,%s)",
                    ('DEBUG','AK','SK','签名','TEMPLATE',1,1))
        conn.commit()
        print('inserted sms_config id=', cur.lastrowid)
        return cur.lastrowid

def ensure_remind_rule(overdue=1):
    now = datetime.now().strftime('%H:%M')
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM remind_rule WHERE overdue_days=%s AND send_time=%s AND is_enabled=1 LIMIT 1",(overdue, now))
        r = cur.fetchone()
        if r:
            print('remind_rule exists id=', r[0])
            return r[0]
        cur.execute("INSERT INTO remind_rule(overdue_days,send_time,sms_template,is_enabled,sort_order) VALUES(%s,%s,%s,%s,%s)",
                    (overdue, now, '尊敬的{租户姓名}，您有{逾期笔数}笔账单逾期，总欠费{总欠费金额}元。',1,10))
        conn.commit()
        print('inserted remind_rule id=', cur.lastrowid)
        return cur.lastrowid

def run_remind_check():
    # fetch enabled rules with send_time == now
    now = datetime.now().strftime('%H:%M')
    with conn.cursor(pymysql.cursors.DictCursor) as cur:
        cur.execute("SELECT * FROM remind_rule WHERE is_enabled=1 AND send_time=%s", (now,))
        rules = cur.fetchall()
        if not rules:
            print('no rules to run at', now)
            return
        rule_days = {r['overdue_days']: r for r in rules}
        # fetch bills with status containing '待支付' or '部分支付'
        cur.execute("SELECT id,contract_id,room_id,generated_date,total_amount,`status`,room_number,tenant_name,tenant_phone FROM bill")
        bills = cur.fetchall()
        today = date.today()
        # filter
        candidates = []
        for b in bills:
            s = b.get('status') or ''
            if '待支付' in s or '部分支付' in s:
                if b.get('generated_date') is None: continue
                gen = b['generated_date'].date() if isinstance(b['generated_date'], datetime) else b['generated_date']
                overdue = (today - gen).days - 1
                if overdue >= 0:
                    b['overdue'] = overdue
                    candidates.append(b)
        # group by tenant key
        groups = {}
        for b in candidates:
            key = b['contract_id'] if b['contract_id'] else (b['room_id'] if b['room_id'] else 0)
            groups.setdefault(key, []).append(b)
        for tenant, bls in groups.items():
            max_overdue = max(b['overdue'] for b in bls)
            rule = rule_days.get(max_overdue)
            if not rule: continue
            # dedupe: check remind_log
            cur.execute("SELECT id FROM remind_log WHERE tenant_id=%s AND overdue_days=%s LIMIT 1", (tenant, max_overdue))
            if cur.fetchone():
                print('already sent for tenant', tenant, 'overdue', max_overdue)
                continue
            count = len(bls)
            total = sum((b['total_amount'] or 0) for b in bls)
            room = bls[0].get('room_number')
            tenant_name = bls[0].get('tenant_name')
            phone = bls[0].get('tenant_phone')
            content = rule.get('sms_template') or '尊敬的{租户姓名}，您有{逾期笔数}笔账单逾期，总欠费{总欠费金额}元。'
            content = content.replace('{租户姓名}', tenant_name or '')
            content = content.replace('{房间号}', room or '')
            content = content.replace('{逾期笔数}', str(count))
            content = content.replace('{总欠费金额}', '%.2f' % total)
            content = content.replace('{最长逾期天数}', str(max_overdue))
            bill_ids = [b['id'] for b in bls]
            # insert remind_log (is_debug=1)
            nowdt = datetime.now()
            cur.execute("INSERT INTO remind_log(tenant_id,tenant_name,phone,room_number,overdue_days,bill_ids,total_amount,content,status,fail_reason,is_debug,created_at) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                        (tenant, tenant_name, phone, room, max_overdue, str(bill_ids), total, content, 'success', None, 1, nowdt))
            conn.commit()
            print('sent to', phone, 'content:', content)

if __name__ == '__main__':
    print('resetting admin password...')
    reset_admin_password('admin123')
    print('ensuring sms_config...')
    ensure_debug_sms_config()
    print('ensuring remind_rule...')
    ensure_remind_rule(1)
    print('running remind check...')
    run_remind_check()
    print('done')
