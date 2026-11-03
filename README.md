# 铜冶炼数字孪生后端

已实现测点端到端链路、设备监控，以及模块 07 综合能源管理。能源表计直接使用 `dim_sensor` 能源测点实现，原始读数复用 `ods_sensor_data`，不建设重复采集入口。

## 环境

- Java 17
- Maven 3.9+
- PostgreSQL 16（测试使用 H2 PostgreSQL 模式）
- MySQL 5.7+（使用 `mysql` profile，独立 Flyway 迁移）

## 启动

```powershell
docker compose up -d
$env:DB_URL='jdbc:postgresql://localhost:5432/copper_twin'
$env:DB_USERNAME='copper_twin'
$env:DB_PASSWORD='copper_twin_dev'
mvn spring-boot:run
```

不安装 PostgreSQL时，可以使用仅供本地联调的 H2 演示模式：

```powershell
mvn "-Dmaven.repo.local=$PWD\target\m2" package -DskipTests
java -jar target\backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo
```

使用 MySQL 5.7/8.0 时，先创建空库并执行 `database/copper_twin_mysql_schema.sql` 和
`database/copper_twin_mysql_seed.sql`，或直接使用 `mysql` profile 的 Flyway 迁移：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/copper_twin?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true'
$env:DB_USERNAME='copper_twin'
$env:DB_PASSWORD='请替换为密码'
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

MySQL 迁移目录为 `src/main/resources/db/migration-mysql`，不会执行 PostgreSQL 的 V1-V8 脚本。

项目根目录也提供 `启动前后端演示.bat` 和 `发送模拟数据.bat`。演示模式每次启动都会重建内存数据库并自动载入 V2、V3、V6 种子数据，不影响 PostgreSQL。

若系统 Maven 仓库不可写，可指定工程内缓存：

```powershell
mvn "-Dmaven.repo.local=$PWD\target\m2" spring-boot:run
```

服务启动后：

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI：`http://localhost:8080/v3/api-docs`
- 存活检查：`http://localhost:8080/api/v1/health/live`
- 首屏测点：`http://localhost:8080/api/v1/production/process-overview/measurements`
- 综合能源：`http://localhost:8080/api/v1/energy/overview`

## 模拟采集

```powershell
.\scripts\Send-SimulatedReadings.ps1 -Rounds 24 -IntervalSeconds 5
```

脚本会发送温度、炉压、液位、牵引速度、张力和线径数据。前端或调试工具可每 10 秒轮询首屏组合接口。

## 主要接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/ingest/sensor-readings:batch` | 简单批量采集，最多 5000 条 |
| GET | `/api/v1/master/sensors` | 测点与参数定义 |
| GET | `/api/v1/measurements/latest` | 最新标准值 |
| GET | `/api/v1/measurements/trends` | raw/minute/hour/day 趋势 |
| GET | `/api/v1/production/process-overview/measurements` | 首屏两车间工序测点组合数据 |
| GET | `/api/v1/equipment/summary` | 设备运行、离线、预警和报警统计 |
| GET | `/api/v1/equipment` | 设备分页列表与状态筛选 |
| GET | `/api/v1/equipment/{id}` | 设备基础信息和当前状态 |
| GET | `/api/v1/equipment/{id}/parameters/latest` | 设备测点最新值与分布位置 |
| GET | `/api/v1/equipment/{id}/parameters/trends` | 设备多参数趋势 |
| GET | `/api/v1/energy/overview` | 电、水、煤气总量、成本、标煤、构成与峰平谷 |
| GET | `/api/v1/energy/workshops/comparison` | 两车间能源与产量口径对比 |
| GET | `/api/v1/energy/equipment/ranking` | 独立分表设备能耗排名 |
| GET | `/api/v1/energy/equipment/benchmark` | 同类设备能效对标 |
| GET | `/api/v1/energy/flow` | 总表、车间分表、设备分表流向树 |
| GET | `/api/v1/energy/balance` | 总分表平衡差与采集在线率 |
| GET | `/api/v1/energy/workshops/{id}/dashboard` | 车间能源看板 |
| GET | `/api/v1/equipment/{id}/energy` | 设备独立表计能耗；无分表时返回暂无计量 |

## 采集示例

```json
{
  "sourceSystem": "SIMULATOR",
  "readings": [{
    "sourceRecordId": "sim-20260803-temp-001-1",
    "sensorCode": "TEMP-001",
    "parameterCode": "temperature",
    "collectTime": "2026-08-03T19:30:00+08:00",
    "valueNum": 1176,
    "sourceUnit": "C",
    "qualityCode": "GOOD"
  }]
}
```

## 状态口径

- `WARNING`：超出正常阈值但未越过报警阈值
- `ALARM`：越过报警阈值
- `OFFLINE`：超过 `max(3 × 采样周期, 60秒)` 未更新，未知采样周期使用 300 秒
- `DATA_INVALID`：标准质量为 BAD 或 UNCERTAIN
- `NO_DATA`：尚无有效快照

乱序旧读数会保留在 ODS 原始趋势中，但不会覆盖较新的 ADS 快照。无法换算单位的记录保留在 ODS，并在本次接口响应中返回错误，不进入标准快照。

## 测试

```powershell
mvn test
mvn package
```

MySQL 集成测试需要设置 `MYSQL_TEST_URL` 后执行；未设置时测试自动跳过：

```powershell
$env:MYSQL_TEST_URL='jdbc:mysql://127.0.0.1:3306/copper_twin?serverTimezone=Asia/Shanghai'
$env:MYSQL_TEST_USERNAME='copper_twin'
$env:MYSQL_TEST_PASSWORD='密码'
mvn test -Dtest=MySqlIntegrationTest -Dspring.profiles.active=mysql
```

集成测试覆盖迁移、种子、直接采集、表底重置、瞬时积分回退、峰平谷与成本、标煤、能源树、平衡差、车间/设备口径，以及原有测点和设备接口回归。
