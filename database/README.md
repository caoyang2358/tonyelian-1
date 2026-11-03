# Copper Twin 数据库导出

`copper_twin_full.sql` 是从空数据库创建 Copper Twin 所需表结构、约束、索引和演示种子数据的完整脚本。

## 支持范围

- 生产数据库：PostgreSQL 14+
- 本地演示/自动化测试：H2 2.x（PostgreSQL 兼容模式）
- Windows、Linux、macOS 均可执行；操作系统不会影响 SQL 文件内容

MySQL、SQL Server 等数据库使用不同 SQL 方言，不能保证直接执行；如需支持，应单独生成对应方言版本。

## PostgreSQL 执行

```bash
psql -h localhost -U copper_twin -d copper_twin -f database/copper_twin_full.sql
```

脚本应在空数据库执行。生产环境建议先备份，并确认目标库为空或使用独立 schema。

## 重新生成

在后端仓库根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/Export-DatabaseSql.ps1
```

脚本按 `src/main/resources/db/migration/V*.sql` 的版本顺序合并，避免手工漏掉迁移。
