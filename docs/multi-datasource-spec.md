# Feather ORM 多数据源支持 — 方案 Spec（v1.0 评审通过）

> 状态：**评审通过（2026-08-23）**，已按本文档实施完成（M1 方言去全局化 / M2 多集群装配 / M3 DAO 绑定 / M4 事务与文档），随 0.6.0 发布。1.0.0 起配置前缀归一化为 `feather.orm.datasource.*`（原 `feather.datasource.*`）。
>
> 评审决议：① 多集群配置 key 用 `others`；② 默认集群别名 `default`/`primary` 同时支持；
> ③ 注解名 `@FeatherDataSource`；④ **不做账号继承**——未配置 username/password 即无账号；
> ⑤ `@FeatherTx` 组合注解第一版不做；⑥ 方法级 `@DataSource` 不做；⑦ Bean 命名 `<name>JdbcDAO` 通过。

---

## 1. 背景与目标

### 1.1 现状问题

当前架构是一条"全局单链"，一个应用实例只能接一个数据库集群：

```
feather.orm.datasource.primary(+replicas)
  → 单个 DataSource / RoutingDataSource（一主多从）
    → 单个 NamedParameterJdbcTemplate
      → 单个 JdbcDAO 单例（持有 dialect / slaveCount）
        → 所有 BaseDAO 共享注入该 JdbcDAO
```

读写分离是通过 `JdbcDAO` 每次操作前向 `ThreadLocal(DataSourceHolder)` 塞主/从 Key、由 `RoutingDataSource` 按 Key 取连接实现的——本质是**单个集群内部的路由**。

三个隐藏耦合点决定了"再加一个 URL"解决不了问题：

| 耦合点 | 现状 | 多库下的问题 |
|---|---|---|
| `Mapper` 全局单例持有单一 `SqlDialect` | `getColumnMapper(clazz)` 按全局 dialect 生成表名/列名引用并缓存 | 两个不同引擎（如 MySQL + PG）共存时，分页语法、保留字引用、forceIndex 只能满足一个 |
| `JdbcDAO` 持有单一 `slaveCount` / `NamedParameterJdbcTemplate` | 一主多从是全局配置 | 无法表达"库 A 有从库、库 B 单节点" |
| `BaseDAO` 字段注入全局 JdbcDAO | 无归属表达 | 没有"这个 DAO 属于哪个库"的概念 |

### 1.2 目标

- [x] 一个应用实例可同时读写 **N 个独立数据库**，各库引擎可不同（MySQL/PG/Oracle/…）
- [x] 每个库独立具备读写分离能力（各自的主从配置）
- [x] 每个库的 SQL 方言、池参数独立
- [x] DAO 归属**编译期可见、启动期校验**，杜绝运行时跑错库
- [x] 现有配置与代码**零迁移**兼容
- [x] **不支持跨库事务**（明确声明，见 §7.3）

### 1.3 非目标（评审时确认）

- 不支持跨库事务（XA / Seata / 本地消息表由用户自选，框架只做文档指引）
- 不支持同 schema 多库复用同一 DAO 类（如多租户按库分片）——第一版不做，避免引入动态路由魔法
- 不支持分库分表（属 ShardingSphere 范畴）
- 不支持方法级切换数据源（`@DataSource` 只放 DAO 类上）

---

## 2. 方案选型回顾

| 方案 | 结论 | 理由 |
|---|---|---|
| **A. DAO 级绑定**：每数据源一个 `JdbcDAO` 实例，DAO 注解声明归属 | ✅ **采用** | 类型安全、方言/池/从库各自独立、事务天然按库隔离、零运行时魔法 |
| B. ThreadLocal + AOP 动态路由（dynamic-datasource 风格） | ❌ 否决 | 单一 JdbcDAO 的 dialect 无法服务多引擎；ThreadLocal 在事务/异步场景状态泄漏；运行时才能发现跑错库；与"零配置魔法"哲学相悖 |
| C. ShardingSphere 等中间件 | ❌ 否决 | 重依赖，对"只想连第二个库"是杀鸡用牛刀 |

---

## 3. 配置层设计

### 3.1 设计原则：把"必要信息"减到最少

每个库**必填的只有 `url`**（+ 实际需要时 username/password）。其余全部可缺省继承：

- `driverClassName` → 缺省自动推导（维持现状，按 JDBC URL 自动识别）
- `dialect` → 缺省 `auto`（启动时按各库元数据独立探测）
- `hikari` 池参数 → 缺省继承全局 `feather.orm.datasource.hikari`，集群级可覆盖单项
- `username / password` → **不继承、不默认**：未配置即无账号（按评审决议④）

### 3.2 配置示例

```yaml
feather:
  datasource:
    # ── 默认集群（两种写法等价：default 或 primary）──
    default:                        # ← 推荐新名字；primary 仍可用作别名
      url: jdbc:mysql://localhost:3306/biz
      username: root
      password: xxx
      replicas:                     # 该集群专属从库（读写分离），可选
        - url: jdbc:mysql://slave1:3306/biz
          username: root
          password: xxx

    # ── 其他集群：每个独立数据库一个 key ──
    others:
      order:
        url: jdbc:mysql://localhost:3306/order
        username: root                            # 每集群必填自己的账号（不继承）
        password: xxx
      log:
        url: jdbc:postgresql://localhost:5432/log
        username: postgres
        password: xxx
      audit:
        url: jdbc:oracle:thin:@//localhost:1521/audit
        username: audit
        password: xxx
        dialect: oracle                            # 显式指定方言（可选，缺省 auto 探测）
        hikari:
          maximum-pool-size: 5                     # 覆盖全局池参数
          pool-name: feather-audit

    # ── 全局池参数：所有集群继承，集群级可覆盖 ──
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

### 3.3 默认集群确定规则（互斥，按序匹配）

1. 配置了 `feather.orm.datasource.default` → 默认集群 = `default`
2. 否则配置了 `feather.orm.datasource.primary` → 默认集群 = `primary`（**兼容现状，零迁移**）
3. 否则 `others` 中存在名为 `default` 或 `primary` 的项 → 取其为首
4. 都不满足 → 未进入多数据源模式：有 `primary.url` 走现有单集群逻辑；都没有则回退 Spring Boot 默认数据源（维持现状）

> 设计决策：**不**支持"others 里第一个自动当默认"，避免隐式规则。默认集群必须显式存在。

### 3.4 评审决议记录

- ~~username/password 缺省继承 default~~ → **不做继承**：未配置即无账号（评审决议④）
- 配置 key 命名 `sources` → **`others`**（评审决议①）

---

## 4. Bean 装配设计

### 4.1 每个集群 `name` 生成一组 bean（命名约定）

| Bean 类型 | Bean 名 |
|---|---|
| `DataSource`（有 replicas 则为 `RoutingDataSource`） | `<name>DataSource` |
| `NamedParameterJdbcTemplate` | `<name>NamedParameterJdbcTemplate` |
| `JdbcDAO` | `<name>JdbcDAO` |
| `DataSourceTransactionManager` | `<name>TransactionManager` |
| `TransactionTemplate` | `<name>TransactionTemplate` |

默认集群除命名 bean 外，**额外注册无 qualifier 主 bean**（`jdbcDAO` / `transactionManager` 等），保证现有 `@Autowired JdbcDAO`、`@Autowired TransactionTemplate` 等注入点不动。

### 4.2 关键实现点

- 每个集群独立构建：`HikariDataSource`（池名 `feather-<name>`，保持现状风格）→ 有 replicas 则包 `RoutingDataSource` → `NamedParameterJdbcTemplate` → `JdbcDAO(name, dialect, slaveCount)` → `DataSourceTransactionManager` → `TransactionTemplate`
- 各集群的 `SqlDialect` 独立探测/指定，互不影响
- `Hikari` 池参数解析：集群级覆盖项优先，其余取全局
- 激活条件变更：现 `@ConditionalOnProperty("feather.orm.datasource.primary.url")` 改为 **default 集群有 url 或 `sources` 非空** 时接管持久层
- 自定义扩展点：用户可自行注册任意 `<name>JdbcDAO` bean 覆盖（配合 `@ConditionalOnMissingBean` 逻辑，参照现状 JdbcDAO 的处理方式）

---

## 5. 方言去全局化（前置依赖，必须先做）

现状 `Mapper` 是全局单例：

```java
private final Map<Class<?>, ColumnMapper<?>> clazzMapperCache; // 单一 dialect
private volatile SqlDialect dialect;
public <T> ColumnMapper<T> getColumnMapper(Class<T> clazz);   // 按全局 dialect 构建并缓存
```

### 5.1 改造

```java
// ColumnMapper 缓存按 (clazz, dialect) 分组
private final Map<CacheKey, ColumnMapper<?>> clazzMapperCache;
record CacheKey(Class<?> clazz, SqlDialect dialect) {}

public <T> ColumnMapper<T> getColumnMapper(Class<T> clazz, SqlDialect dialect); // 新增，多库主路径
public <T> ColumnMapper<T> getColumnMapper(Class<T> clazz);  // 保留，内部委托 getColumnMapper(clazz, this.dialect)，兼容旧调用
public void setDialect(SqlDialect d);                        // 语义不变：仅更新"默认 dialect"，不再清空缓存（缓存已按 dialect 分组）
```

### 5.2 调用方改造

| 调用方 | 改动 |
|---|---|
| `JdbcDAO`（9 处 `getColumnMapper(clazz)`） | 改为 `getColumnMapper(clazz, this.dialect)` |
| `JdbcDAO` | 暴露 `getDialect()` getter（供 BaseDAO/QueryHelper 使用） |
| `RowMapperSupport.resolveHandlers(clazz)` | 签名加 `SqlDialect dialect`，内部用它取 ColumnMapper；`JdbcDAO` 调用处传 `this.dialect` |
| `QueryHelper` 构造 | 加 `SqlDialect` 参数，不再读全局 `Mapper.getDialect()`；`BaseDAO.getQueryHelper()` 改传 `jdbcDAO.getDialect()` |
| `FeatherAutoConfiguration` | 不再调用 `Mapper.setDialect()` 作为唯一方言来源（各 JdbcDAO 自带 dialect）；对自定义 `JdbcDAO` bean 场景仍保留兼容 |

> 效果：同一实体类可安全存在于多个引擎不同的库中，各自拿到按本库方言生成的引用，互不污染。

---

## 6. DAO 绑定设计

### 6.1 注解

```java
@Repository
@FeatherDataSource("order")              // 绑定 order 集群；不标 → 默认集群
public class OrderDAO extends BaseDAO<OrderEntity> { }
```

- 注解名：`io.github.sombreknight.feather.annotation.FeatherDataSource`（评审决议③）
- 值 = 配置中的集群名（`default`/`primary`/`others` 中的 key）
- 放 DAO 类上，支持继承解析（用 Spring `AnnotatedElementUtils.findMergedAnnotation` 处理中间父类/接口）

### 6.2 BaseDAO 注入改造

```java
public abstract class BaseDAO<T extends BaseEntity<?>> {
    @Autowired
    private Map<String, JdbcDAO> jdbcDAOs;   // Spring 注入全部按名注册的 JdbcDAO

    private JdbcDAO resolvedJdbcDAO;          // 解析后的归属

    @PostConstruct
    void resolveDataSource() {
        DataSource ann = AnnotatedElementUtils.findMergedAnnotation(getClass(), DataSource.class);
        resolvedJdbcDAO = (ann == null)
            ? jdbcDAOs.get("jdbcDAO")                          // 默认集群主 bean
            : jdbcDAOs.get(ann.value() + "JdbcDAO");
        if (resolvedJdbcDAO == null) {
            throw new BeanCreationException(...);             // fail-fast，见 6.3
        }
        this.jdbcDAO = resolvedJdbcDAO;                        // 兼容：jdbcDAO 字段保留
    }
    // 其余所有方法改为使用 resolvedJdbcDAO（原 jdbcDAO 字段内部替换，外部行为不变）
}
```

### 6.3 fail-fast 校验（启动期，非运行期）

| 场景 | 行为 |
|---|---|
| `@FeatherDataSource("order")` 但配置里无 `order` | 启动失败，异常信息含"未配置的数据源：order，请检查 feather.orm.datasource.others" |
| 配了 `others` 但无默认集群（无 default/primary） | 启动失败，明确提示 |
| 集群 url 缺失 | 维持现状（`IllegalStateException`） |
| 不标注解 | 走默认集群，与现有行为完全一致 |

---

## 7. 事务设计

### 7.1 每个集群一个独立事务管理器

- `DataSourceTransactionManager` 按集群各建一个（§4.1 命名 `<name>TransactionManager`）
- 默认集群的 `transactionManager` 是主事务管理器

### 7.2 用法

```java
// 声明式：默认集群（与现状完全一致）
@Transactional
public void doBiz() { ... }

// 声明式：指定集群（Spring 原生 qualifier 语法，无需框架魔法）
@Transactional(transactionManager = "orderTransactionManager")
public void doOrder() { ... }

// 编程式：注入对应集群的 TransactionTemplate
@Resource(name = "orderTransactionTemplate")
private TransactionTemplate orderTx;
orderTx.executeWithoutResult(status -> { ... });
```

### 7.3 跨库事务：明确不支持

- 一个 `@Transactional` 只能管一个集群；另一个集群的操作不受该事务保护（独立提交）
- 文档明确声明，并给出替代路径指引：分布式事务中间件（Seata）、本地消息表、TCC 等——框架不实现

> 评审决议⑤：`@FeatherTx` 组合注解第一版不做，`@Transactional(transactionManager = "orderTransactionManager")` 已足够。
> 评审决议⑥：方法级 `@DataSource` 不做。
> 评审决议⑦：Bean 命名 `<name>JdbcDAO` 通过。

---

## 8. 兼容性影响清单

### 8.1 破坏性变更（仅内部 API，public 使用面不动）

| 变更 | 影响面 |
|---|---|
| `RowMapperSupport.resolveHandlers` 签名加 dialect | 仅框架内部调用；外部自定义 `RowMapperSupport` bean 极少，文档标注 |
| `QueryHelper` 构造签名加 dialect | 用户直接 `new QueryHelper<>()` 属罕见用法，文档标注；`BaseDAO.getQueryHelper()` 路径无感 |
| `Mapper.getColumnMapper` 新增重载 | 纯新增，旧签名保留 |

### 8.2 零迁移保证

- 配置：`primary` + `replicas` + `hikari` 写法原样可用（映射为默认集群）
- 代码：`@Repository class XxxDAO extends BaseDAO<T>` + `@Autowired JdbcDAO` / `TransactionTemplate` 原样可用
- 行为：单集群场景完全等同现状（`JdbcDAO` 构造、路由逻辑、forceMaster 不变）

### 8.3 版本与文档

- 版本：0.6.0（minor，新特性）
- 文档：`usage.md` 增加"多数据源"章节（配置、注解、事务、跨库事务说明）；README 特性更新；`Agent.md` 记录

---

## 9. 测试计划

### 9.1 单元测试（core）

- `Mapper` 多方言缓存隔离：同一实体类分别以 mysql/postgres dialect 取 ColumnMapper，引用互不串
- `JdbcDAO` 各集群独立 slaveCount / dialect 构造

### 9.2 集成测试（starter）

- **双库同引擎**：本地 colima `feather-mysql` 单实例建两个 database（`feather_test` + `feather_test2`）模拟双 MySQL 数据源；`@DataSource("order")` DAO 写库 A、默认 DAO 写库 B，交叉验证不串
- **混合引擎**：默认集群 MySQL + `sources.log` 连 PostgreSQL（本地 `feather-pg` 已有），验证分页/保留字按各库方言正确生成（这是去全局化改造的验收项）
- **fail-fast**：`@DataSource("不存在")` 启动失败断言
- **事务**：`@Transactional(transactionManager="orderTransactionManager")` 回滚验证；跨库事务不保护验证（文档行为）

### 9.3 CI

- 现有 PG 服务容器基础上增加 MySQL 服务容器，跑混合引擎用例（参考记忆中的 CI 矩阵结构）

---

## 10. 实施步骤（里程碑）

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M1 方言去全局化 | Mapper 缓存按 (clazz, dialect) 分组；JdbcDAO/RowMapperSupport/QueryHelper 改造 | 单集群全量测试通过，行为不变 |
| M2 多集群装配 | FeatherProperties 多集群结构 + 默认集群别名兼容；每集群 DataSource/JdbcTemplate/JdbcDAO/TxManager/TxTemplate 装配 | 双 MySQL 集成测试通过 |
| M3 DAO 绑定 | `@DataSource` 注解 + BaseDAO 归属解析 + fail-fast | 绑定/fail-fast 用例通过 |
| M4 事务与文档 | 每集群事务用法、usage.md/README 更新 | 事务用例通过，文档评审 |

---

## 11. 评审决议（已确认，2026-08-23）

1. **配置 key 命名**：`others` ✅
2. **默认集群别名**：`default` 与 `primary` 同时支持 ✅
3. **注解名**：`@FeatherDataSource` ✅
4. **账号继承**：不做，未配置即无账号 ✅
5. **`@FeatherTx` 组合注解**：第一版不做 ✅
6. **方法级 `@DataSource`**：第一版不做 ✅
7. **Bean 命名**：`<name>JdbcDAO` 风格 ✅
