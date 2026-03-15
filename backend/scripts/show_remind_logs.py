import pymysql
import json

DB_HOST = 'localhost'
DB_PORT = 3306
DB_USER = 'root'
DB_PASS = 'root'
DB_NAME = 'house_db'

conn = pymysql.connect(host=DB_HOST, port=DB_PORT, user=DB_USER, password=DB_PASS, database=DB_NAME, charset='utf8mb4')

with conn.cursor(pymysql.cursors.DictCursor) as cur:
    cur.execute("SELECT id, tenant_id, tenant_name, phone, room_number, overdue_days, bill_ids, total_amount, content, status, fail_reason, is_debug, created_at FROM remind_log ORDER BY id DESC LIMIT 10")
    rows = cur.fetchall()
    print(json.dumps(rows, default=str, ensure_ascii=False, indent=2))
