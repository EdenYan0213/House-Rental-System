# 最简部署：后端 + 管理端

只需一台 Linux 服务器（Oracle Cloud 免费实例 / 任意云主机均可），
**不需要 Docker，不需要对象存储，不需要持久卷**（图片已存 MySQL）。

最终结构：

```
浏览器 ──> Nginx :80
             ├── /            → /var/www/admin（管理端静态文件）
             └── /api/        → 127.0.0.1:8081（Spring Boot）
                                  └── MySQL 8（house_db）
```

前后端同域，前端默认请求相对路径 `/api`，**不用改任何前端配置**。

---

## 0. 前置条件

- Linux（Ubuntu 22.04/24.04 已验证路径）
- 已开放 80 端口（和 22）
- **JDK 17**（JDK 21+ 会因 Lombok 报 `TypeTag :: UNKNOWN`）
- MySQL 8.x

---

## 1. 安装依赖

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk maven mysql-server nginx git
java -version    # 必须显示 17.x
```

---

## 2. 拉代码

```bash
sudo mkdir -p /opt && cd /opt
sudo git clone https://github.com/EdenYan0213/House-Rental-System.git house
sudo chown -R "$USER":"$USER" /opt/house
cd /opt/house
git checkout master
```

---

## 3. 建库并执行迁移

顺序不能乱：先 `init_db.sql`，再全部 `migrate_*.sql`。

> ⚠️ **库名必须是 `house_db`。** 所有 SQL 脚本开头都写死了 `USE house_db;`，
> 会在执行时切换到该库。如果你建了别的库名，表会被静默建到 `house_db`
> （如果它不存在则直接报错），目标库仍是空的。

```bash
sudo mysql -e "CREATE DATABASE IF NOT EXISTS house_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
sudo mysql -e "CREATE USER IF NOT EXISTS 'house'@'localhost' IDENTIFIED BY 'CHANGE_ME_DB_PASSWORD';"
sudo mysql -e "GRANT ALL ON house_db.* TO 'house'@'localhost'; FLUSH PRIVILEGES;"

sudo mysql < /opt/house/backend/sql/init_db.sql

for f in /opt/house/backend/sql/migrate_*.sql; do
  echo "==> $(basename $f)"
  sudo mysql < "$f" || { echo "失败: $f"; break; }
done
```

验证建表数量（正常应为 40+ 张）：

```bash
sudo mysql -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='house_db';"
```

> 迁移脚本未接入 Flyway，必须手工按顺序执行。脚本大多带幂等判断，可重复跑。
>
> **部分脚本在全新库上会报错，属正常现象**，不影响功能（已实测验证）：
> - `migrate_add_building_image_url.sql` / `migrate_add_theme_preference.sql`
>   → `Duplicate column`：这些列已由 `init_db.sql` 建好
> - `migrate_clean_dangling_image_refs.sql` / `migrate_files_to_db.sql`
>   → `image_blob doesn't exist`：按字母序排在 `migrate_images_to_db.sql` 之前
> - `migrate_add_ai_assistant_operation_log.sql` / `migrate_add_tenant_portal_phase2.sql`
>   → 语法告警：相关列已存在
>
> 判断标准：**最终表数应为 39 张，且 `image_blob` 表存在**。达标即可继续。
> 若某个脚本报的是权限/连接类错误，那才是真问题。



---

## 4. 生成密钥

```bash
openssl rand -base64 48   # 用作 JWT_SECRET
openssl rand -base64 48   # 用作 CRYPTO_SECRET
```

> `CRYPTO_SECRET` 用于身份证号 AES/GCM 加密。**一旦写入数据就不能再改**，
> 否则历史身份证无法解密。

---

## 5. 配置后端环境变量

```bash
sudo mkdir -p /etc/house
sudo tee /etc/house/env > /dev/null <<'EOF'
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/house_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=house
SPRING_DATASOURCE_PASSWORD=CHANGE_ME_DB_PASSWORD
JWT_SECRET=CHANGE_ME_JWT_SECRET
CRYPTO_SECRET=CHANGE_ME_CRYPTO_SECRET
ADMIN_INIT_PASSWORD=CHANGE_ME_ADMIN_PASSWORD
SERVER_PORT=8081
EOF

# 填入真实值（或用编辑器手动改）
sudo chmod 600 /etc/house/env
```

把上面四处的 `CHANGE_ME_*` 替换成真实值：
- `SPRING_DATASOURCE_PASSWORD` = 第 3 步设的数据库密码
- `JWT_SECRET` / `CRYPTO_SECRET` = 第 4 步生成的两个随机串
- `ADMIN_INIT_PASSWORD` = 你想要的 admin 初始密码（**不要用 admin123**）

> 不设 `ADMIN_INIT_PASSWORD` 会回退到开发默认 `admin123` 并打告警日志；
> 生产环境必须显式设置。

---

## 6. 构建后端

```bash
cd /opt/house/backend
mvn -DskipTests package

# 确认产物（名字可能带版本号）
ls -lh target/*.jar
```

> `init_db.sql` **不再预置 admin 账号**。管理员由应用首次启动时创建，
> 密码取自 `ADMIN_INIT_PASSWORD`。若数据库里已有一个密码未知的 admin
> （来自历史部署），`ADMIN_INIT_PASSWORD` 不会覆盖它——需要先删除该行：
> ```bash
> sudo mysql house_db -e "DELETE FROM sys_user WHERE username='admin';"
> sudo systemctl restart house-backend
> ```

---

## 7. 用 systemd 常驻

```bash
# 把 jar 复制成固定名字，避免版本变化导致 service 失效
sudo cp /opt/house/backend/target/*.jar /opt/house/backend/app.jar

sudo tee /etc/systemd/system/house-backend.service > /dev/null <<'EOF'
[Unit]
Description=House Rental Backend
After=network.target mysql.service

[Service]
User=root
WorkingDirectory=/opt/house/backend
EnvironmentFile=/etc/house/env
ExecStart=/usr/bin/java -jar /opt/house/backend/app.jar
Restart=always
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now house-backend
sudo systemctl status house-backend --no-pager
```

> 用 `root` 是为省事；若用普通用户，需保证该用户能读 `/etc/house/env`（文件权限 600）。

查看日志：

```bash
sudo journalctl -u house-backend -f
```

---

## 8. 构建管理端

```bash
cd /opt/house/frontend
npm install
npm run build

sudo mkdir -p /var/www/admin
sudo cp -r dist/* /var/www/admin/
```

> 只需 `/opt/house/frontend`，不用管 `staff-mobile` 和 `tenant-frontend`。
> 三个前端是独立项目，各自有 package.json。

---

## 9. 配置 Nginx

```bash
sudo tee /etc/nginx/sites-available/house > /dev/null <<'EOF'
server {
    listen 80;
    server_name _;

    root /var/www/admin;
    index index.html;

    # SPA 路由兜底：不加这行，刷新 /buildings 这类路径会 404
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 反向代理后端。
    # 注意 proxy_pass 结尾不要加斜杠，否则会剥掉 /api 前缀导致全部 404
    location /api/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        client_max_body_size 20m;   # 默认 1MB，上传图片会 413
        proxy_read_timeout 60s;     # 导入/导出耗时较长
    }
}
EOF

sudo rm -f /etc/nginx/sites-enabled/default
sudo ln -sf /etc/nginx/sites-available/house /etc/nginx/sites-enabled/house
sudo nginx -t
sudo systemctl reload nginx
```

---

## 10. 验收（逐项实测）

```bash
# (1) 后端存活：应为 400/401，不能是 502
curl -i http://127.0.0.1:8081/api/auth/login

# (2) 通过 Nginx 访问后端
curl -i http://127.0.0.1/api/auth/login

# (3) 管理端首页
curl -I http://127.0.0.1/

# (4) admin 登录（返回里应有 token）
curl -s -X POST http://127.0.0.1/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<你设的 ADMIN_INIT_PASSWORD>"}'
```

浏览器打开 `http://<服务器IP>/`，用 admin 登录。

---

## 11. 安全（上线前必做）

```bash
# 防火墙只放行 80/22
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw enable
```

- 登录后**立即修改 admin 密码**（右上角 / 用户管理）
- 有域名后配 HTTPS：
  ```bash
  sudo apt install -y certbot python3-certbot-nginx
  sudo certbot --nginx -d your-domain.com
  ```

> 当前 `POST /api/payment/callback` 无签名校验（模拟支付用）。
> 若对外网开放，任何人可伪造支付成功——真实收款前必须补网关验签，
> 或先用防火墙限制来源。

---

## 附：常见故障对照

| 现象 | 原因 |
|---|---|
| 页面 404、刷新路由丢失 | Nginx 缺 `try_files ... /index.html` |
| 所有 `/api` 请求 404 | `proxy_pass` 结尾多了 `/` |
| 上传图片 413 | 缺 `client_max_body_size 20m` |
| 启动即退出，日志 `TypeTag :: UNKNOWN` | JDK 版本不是 17 |
| 启动后查询报 `Unknown column` | `migrate_*.sql` 没执行全 |
| 登录失败但密码正确 | 种子里已存在 admin，`ADMIN_INIT_PASSWORD` 只在首次创建时生效 |
| 502 Bad Gateway | 后端没起：`systemctl status house-backend` |
