-- =====================================================
-- My-Panel MySQL8 初始化脚本
-- 在容器首次启动时由 /docker-entrypoint-initdb.d/ 自动执行
-- 注意: MySQL 8.4 默认使用 caching_sha2_password,不再使用 mysql_native_password
-- =====================================================

-- 1. 允许 root 远程连接(使用默认认证插件 caching_sha2_password)
ALTER USER 'root'@'%' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;

-- 2. 创建应用用户 cq(远程连接)
CREATE USER IF NOT EXISTS 'cq'@'%' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON `my-panel`.* TO 'cq'@'%';

-- 3. 创建本地用户 cq
CREATE USER IF NOT EXISTS 'cq'@'localhost' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON `my-panel`.* TO 'cq'@'localhost';

-- 4. 刷新权限
FLUSH PRIVILEGES;
