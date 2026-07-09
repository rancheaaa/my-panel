-- =====================================================
-- My-Panel MySQL8 初始化脚本
-- 在容器首次启动时由 /docker-entrypoint-initdb.d/ 自动执行
-- =====================================================

-- 1. 允许 root 远程连接
ALTER USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY '123456';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;

-- 2. 创建应用用户 cq(远程连接)
CREATE USER IF NOT EXISTS 'cq'@'%' IDENTIFIED WITH mysql_native_password BY '123456';
GRANT ALL PRIVILEGES ON sms4j.* TO 'cq'@'%';

-- 3. 创建本地用户 cq
CREATE USER IF NOT EXISTS 'cq'@'localhost' IDENTIFIED WITH mysql_native_password BY '123456';
GRANT ALL PRIVILEGES ON sms4j.* TO 'cq'@'localhost';

-- 4. 刷新权限
FLUSH PRIVILEGES;
