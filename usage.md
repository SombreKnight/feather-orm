# Feather ORM 使用教程

> 基于 Spring `JdbcTemplate` 的轻量级 ORM。注解 + 驼峰约定驱动，继承即得 CRUD。
> 本文档是使用本框架编码的**完整行为契约**，包含全部 API、类型映射规则、路由/事务行为与踩坑点。

---

## 1. 快速开始

### 1.1 引入依赖

```xml
<dependency>
    <groupId>io.github.sombreknight</groupId>
    <artifactId>feather-orm-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 1.2 配置数据源（一套配置接管，无需再配 `spring.datasource.*`）

```yaml
feather:
  orm:
    datasource:
      primary:                # 默认集群（必填；也可用 others.default 命名）
        url: jdbc:mysql://localhost:3306/demo?useUnicode=true&characterEncoding=utf-8
        username: root
        password: xxx
      replicas:               # 可选：默认集群的从库，不配即单节点（零路由开销）
        - url: jdbc:mysql://slave1:3306/demo
          username: root
          password: xxx
      others:                 # 可选：其他集群（多数据源，见第 8 章）
        order:
          url: jdbc:mysql://localhost:3306/order
          username: root
          password: xxx
      hikari:                 # 可选：主从共用，不配即 Hikari 默认值
        maximum-pool-size: 20
        minimum-idle: 5
    worker-id: 1                # 可选：雪花算法 workerId，多实例部署建议显式配置
```

**接管行为**：配置了 `feather.orm.datasource.primary.url` 后，框架在 Spring Boot 默认数据源自动配置**之前**创建 `DataSource` / `NamedParameterJdbcTemplate` / 事务管理器，Boot 默认自动配置自动失效。未配置时优雅回退到 Boot 默认数据源。

### 1.3 定义实体 + DAO + 使用

```java
@Table("tb_user")
public class UserEntity extends BaseEntity<Long> {
    private String userName;        // 约定映射 user_name
    private Integer age;
    private OrderStatus status;     // 枚举
    private ExtInfo extInfo;        // 复杂对象 → JSON 列
    private List<String> tags;      // 泛型集合 → JSON 列
    // getter / setter
}

@Repository
public class UserDAO extends BaseDAO<UserEntity> {
}

// 使用
userDAO.saveEntity(user);
UserEntity user = userDAO.findById(1L);
List<UserEntity> list = userDAO.findList(userDAO.getQueryHelper().whereEqual(UserEntity::getUserName, "张三"));
```

> **注意**：DAO 必须标注 `@Repository`（或 `@Component`），否则不会被 Spring 扫描为 Bean。

---

## 2. 实体定义规范

### 2.1 基类 `BaseEntity`

- 唯一强制字段：`private ID id`（泛型主键），继承即有；`Long` 与 `String` 均可
- 主键类型由泛型参数声明（`extends BaseEntity<Long>` / `extends BaseEntity<String>`），JdbcDAO 按类型自动匹配生成器
- 主键列名默认 `id`；如列名不同（如 `uid`），用 `@Table(idColumn = "uid")` 指定
- **无** 其他默认字段（无 appId、无版本号、无创建/更新时间——全部按需自行声明）

### 2.2 注解

| 注解 | 位置 | 说明 |
|---|---|---|
| `@Table(value)` | 类 | 表名（必填） |
| `@Table(idColumn)` | 类 | 主键列名，默认 `id`（可选） |
| `@Column(value)` | 字段 | 显式列名；**空值 = 走驼峰约定**（可选） |
| `@EnumValue(value)` | 字段 | 枚举存取方法名（逃生舱，见 3.3） |

### 2.3 列名约定（核心规则）

**驼峰转下划线**：Java 字段 `userName` → 列 `user_name`、`userId` → `user_id`、`userURL` → `user_url`。

- 满足约定的字段**不需要任何注解**
- 不满足约定 / 列名为保留字时，用 `@Column("phone_no")` 显式指定
- `static` / `transient` 字段不参与映射

### 2.4 完整示例（覆盖全部特性）

```java
@Table(value = "tb_user", idColumn = "id")
public class UserEntity extends BaseEntity<Long> {

    private String userName;            // → user_name（约定）
    private Integer age;                // → age（约定）

    @Column("phone_no")                 // 不规则列名显式指定
    private String phone;

    private OrderStatus status;         // 枚举（CodeEnum）→ 业务码
    private TypeEnum type;              // 普通枚举 → name()
    private ExtInfo extInfo;            // 复杂对象 → JSON 列
    private List<String> tags;          // 泛型集合 → JSON 列
    private Map<String, Object> attrs;  // Map → JSON 列
    private FeatherDate createTime;     // 日期
}
```

---

## 3. 类型映射（TypeHandler 注册表）

**解析顺序（约定优于配置）**：
1. 用户注册的自定义 `TypeHandler`（Spring Bean 自动收集，优先级最高）
2. 内置：简单类型 → 时间类型 → FeatherDate → 枚举
3. 兜底：JSON 处理器（任何未匹配的类型自动 JSON 序列化）

### 3.1 简单类型（原生存取）

`String`、`Integer/int`、`Long/long`、`Short/short`、`Byte/byte`、`Double/double`、`Float/float`、`Boolean/boolean`、`Character/char`、`BigDecimal`、`byte[]`
—— 自动处理 `NULL` 与 `wasNull`。

### 3.2 时间类型（原生存取）

`java.util.Date`、`java.sql.*`、`java.time.LocalDate/LocalDateTime/LocalTime/Instant/OffsetDateTime`、`FeatherDate`。

**FeatherDate**（框架自带，用法顺手）：
```java
FeatherDate now = new FeatherDate();        // 当前时间
FeatherDate fd = FeatherDate.of(1700000000000L);
fd.getTime();            // epoch 毫秒
fd.getTimeSecond();      // epoch 秒
fd.isZeroTime();         // 是否零时间
fd.datetimeString();     // "yyyy-MM-dd HH:mm:ss"（零时间 → "0000-00-00 00:00:00"）
fd.toLocalDateTime();    // LocalDateTime
FeatherDate.ZERO_INST;   // 零时间实例
```
零时间写库需要 MySQL 连接参数 `zeroDateTimeBehavior=convertToNull`。

### 3.3 枚举三层约定

| 层级 | 触发方式 | 存储形式 |
|---|---|---|
| 1（默认） | 普通枚举，零配置 | `name()` |
| 2（推荐） | 实现 `CodeEnum<T>` 接口 | `getValue()` 业务码 |
| 3（逃生舱） | 字段加 `@EnumValue("getCode")` | 指定方法返回值（用于无法改源码的第三方枚举） |

```java
public interface CodeEnum<T> { T getValue(); }

public enum OrderStatus implements CodeEnum<Integer> {
    CREATED(1), PAID(2), CANCELLED(9);
    private final Integer value;
    OrderStatus(Integer value) { this.value = value; }

    @JsonValue   // 推荐：让 REST 请求/响应也统一用业务码（与 DB 一致）
    @Override
    public Integer getValue() { return value; }
}
```

> **重要**：不带 `@JsonValue` 时，Jackson 会把请求里的数字按**枚举序数**解析（2 → 序数 2，不是业务码 2），会静默映射错。REST 层用业务码必须加 `@JsonValue`。

### 3.4 JSON 映射（兜底，零注解）

**任何未命中内置类型的字段自动 JSON 序列化**，按字段泛型类型还原，`List<X>`、`Map<K,V>`、嵌套泛型均无需额外注解：

```java
private ExtInfo extInfo;             // 对象 → JSON
private List<String> tags;           // 泛型集合 → JSON
private List<ExtInfo> infos;         // 嵌套泛型 → JSON
```

- **null 语义**：写库时跳过 null 列（insert 为 NULL / update 不触碰该列），**绝不写空串**
- 反序列化失败抛 `FeatherDaoException`（fail-fast，不静默返回 null）

### 3.5 自定义 TypeHandler（用户扩展）

```java
@Component
public class MyHandler implements TypeHandler {
    @Override public boolean supports(Class<?> javaType, FieldMeta meta) { ... }
    @Override public Object toJdbcValue(Object value, FieldMeta meta) { ... }   // 写库
    @Override public Object fromResultSet(ResultSet rs, String column, FieldMeta meta) { ... } // 读库
}
```
注册为 Spring Bean 即自动获得最高优先级。

---

## 4. BaseDAO API 全清单

`class XxxDAO extends BaseDAO<XxxEntity>` 后获得：

### 新增
| 方法 | 说明 |
|---|---|
| `boolean saveEntity(T entity)` | 新增；`id == null` 时雪花生成，也可自行指定 |
| `boolean saveEntityList(List<T> entityList)` | 批量新增（按"非空列集合"分组批量执行） |
| `boolean saveOrUpdate(T entity)` | 有 id 且存在 → 更新；否则新增（id 不存在时按指定 id 插入） |

### 删除
| 方法 | 说明 |
|---|---|
| `boolean deleteEntity(T entity)` | 按主键删除 |
| `boolean deleteEntities(List<T> entities)` | 按主键批量删除（`IN`） |

### 更新
| 方法 | 说明 |
|---|---|
| `boolean updateEntity(T entity)` | **仅更新非 null 字段**；null 字段不触碰 |
| `boolean updateEntityList(List<T> entityList)` | 批量更新（COALESCE 单 SQL，语义同单条） |

> **update 语义契约**：`SET 列 = COALESCE(:列, 原列)`。null 字段不参与更新（避免误清数据）；想清空字段请用 `JdbcDAO` 原生 SQL 或 `SqlParam`。

### 按主键查询
| 方法 | 说明 |
|---|---|
| `<ID> T findById(ID id)` | 单查；id 为 null/不存在返回 null；**走主库** |
| `<ID> List<T> findByIds(List<ID> ids)` | 批量；>100 自动分批（每批 100） |
| `<ID> Map<ID, T> findMapByIds(List<ID> ids)` | id → 实体 Map |

### 条件查询（配 QueryHelper）
| 方法 | 说明 |
|---|---|
| `QueryHelper<T> getQueryHelper()` | 构建查询辅助器 |
| `T findOne(QueryHelper<T>)` | 单条；多条抛异常 |
| `List<T> findList(QueryHelper<T>)` | 列表 |
| `long count(QueryHelper<T>)` | 计数 |
| `<F> F findField(Class<F>, QueryHelper<T>)` | 查询单字段（用 `selectFields` 指定） |
| `<F> List<F> findFieldList(Class<F>, QueryHelper<T>)` | 单字段列表 |
| `PagingResult<T> findPageByPageNum(QueryHelper<T>)` | 分页 |
| `<V> PagingResult<V> findDtoPageByPageNum(Class<V>, QueryHelper<T>)` | 分页映射到 DTO |
| `<F> PagingResult<F> findFieldPageByPageNum(Class<F>, QueryHelper<T>)` | 字段分页 |

### 其他
| 方法 | 说明 |
|---|---|
| `void forceMaster()` | 强制后续查询走主库（当前线程本次操作） |
| `Class<T> getEntityClass()` | 实体类型 |

---

## 5. QueryHelper 查询辅助器

以面向对象方式拼装 SQL，**字段名一律用类型安全的 Lambda 方法引用**（`实体::getXxx`），
自动映射为数据库列名；编译期检查、重构自动跟随、IDE 自动补全。

### 5.1 条件

```java
qh.whereEqual(UserEntity::getUserName, "张三")    // =
qh.whereIn(UserEntity::getId, ids)                // IN（单元素自动降级为 =）
qh.whereNotIn(UserEntity::getId, ids)             // NOT IN
qh.whereGt(UserEntity::getAge, 18)                // >
qh.whereGte(UserEntity::getAge, 18)               // >=
qh.whereLt(UserEntity::getAge, 60)                // <
qh.whereLte(UserEntity::getAge, 60)               // <=
```

**模糊查询**：

```java
qh.whereLike(UserEntity::getUserName, "张%")       // 原生 LIKE，通配符（% _）自行传入
qh.whereContains(UserEntity::getUserName, "张")    // LIKE '%张%'，自动转义通配符，可直接传用户输入
qh.whereStartsWith(UserEntity::getUserName, "张")  // LIKE '张%'
qh.whereEndsWith(UserEntity::getUserName, "三")    // LIKE '%三'
```

- getter 在父类（如 `UserEntity::getId`）与 `is` 前缀布尔 getter（如 `Entity::getActive`）均可解析
- 枚举参数自动转换（`CodeEnum` → 业务码，普通枚举 → name）
- 同一字段多个条件自动生成唯一占位符（`:age_1`、`:age_2`），无冲突
- 非法引用（非 getter 形式）立即抛 `FeatherDaoException`（fail-fast）
- `whereContains / whereStartsWith / whereEndsWith` 自动转义 `% _ \` 并拼 `ESCAPE '\'` 子句（跨库一致），防止通配符注入
- **动态字段名**（运行时变量）场景请使用 `JdbcDAO` 原生 SQL

### 5.2 列选择 / 排序 / 分组 / 分页

```java
qh.selectFields("userName", "age")                // 指定查询列（唯一字符串入口，支持别名）
qh.selectFields("userName as u")                  // 支持别名
qh.countField()                                    // count(*)
qh.countField(UserEntity::getId)                   // count(id)
qh.groupBy(UserEntity::getAge, UserEntity::getStatus)
qh.orderByAsc(UserEntity::getAge) / qh.orderByDesc(UserEntity::getId)
qh.limit(10) / qh.limitOne()                       // 普通查询限条数
qh.limit(page, pageSize)                           // 分页（配合 findPageByPageNum）
qh.withTotal(true/false)                           // 分页是否统计总数
qh.forceIndex("idx_name")                          // 强制索引（仅 MySQL 系）
qh.forUpdate()                                     // 悲观锁（SQLite 忽略；SQL Server 抛异常）
```

### 5.3 安全契约（fail-fast）

- **未知字段名立即抛异常**（不会拼进 SQL，杜绝注入）
- 传 null 参数 → 执行前抛 `FeatherDaoException`
- `findList` 不带 where 条件 → 抛异常

---

## 6. JdbcDAO 低层 API（原生 SQL 场景）

`@Autowired JdbcDAO` 可直接使用（或注入 `NamedParameterJdbcTemplate` 用原生 JDBC）：

```java
// 自定义 SQL 查询实体（whereSql 以 " where " 开头，用命名参数）
T findOne(Class<T> clazz, String whereSql, SqlParam param)
List<T> findList(Class<T> clazz, String whereSql, SqlParam param)
long count(Class<T> clazz, String whereSql, SqlParam param)
PagingResult<T> findPageByPageNum(Class<T> clazz, String whereSql, SqlParam param, int page, int size, boolean withTotal)

// DTO 投影查询（任意 POJO；查询结果列缺失自动跳过）
<T> T findDto(Class<T> dtoClass, String sql, SqlParam param)
<T> List<T> findDtoList(Class<T> dtoClass, String sql, SqlParam param)
<T> PagingResult<T> findDtoPageByPageNum(Class<T> dtoClass, String sql, SqlParam param, int page, int size, boolean withTotal)

// 单字段查询
<T> T findField(Class<T> clazz, String sql, SqlParam param)
<T> List<T> findFieldList(Class<T> clazz, String sql, SqlParam param)
<T> PagingResult<T> findFieldPageByPageNum(Class<T> clazz, String sql, SqlParam param, int page, int size, boolean withTotal)

// 写操作
int save(T entity)                    // 返回影响行数
int[] saveBatch(List<T> entities)
int update(T entity)
int[] updateBatch(List<T> entities)
int deleteEntity(Class<T> clazz, T entity)
int deleteEntities(Class<T> clazz, List<T> entities)
```

**SqlParam 用法**：
```java
SqlParam.create("id", 1L).add("name", "张三").toMap()
// FeatherDate 参数自动转换；传 null 参数会触发 fail-fast
```

---

## 7. 主从分离与强制主库

### 7.1 路由规则（配置了 `replicas` 时生效）

| 操作 | 数据源 |
|---|---|
| 写操作（save/update/delete/批量） | **主库** |
| `findById` / `findByIds` | **主库**（保证读己之写） |
| 普通查询（findList/findOne/count/分页/DTO） | **从库**（随机选一个） |
| `forceMaster()` 之后的查询 | **主库** |
| 事务内所有操作 | **主库连接**（复用事务连接） |

### 7.2 forceMaster

```java
userDAO.forceMaster();          // 或注入 JdbcDAO 后 jdbcDAO.forceMaster()
List<UserEntity> list = userDAO.findList(...);   // 本次查询走主库
```
仅对当前线程的本次操作生效，操作完成后自动清理。

### 7.3 事务行为（重要）

- 事务开始自动取**主库**连接（未指定 Key 默认主库）
- 事务内读写**复用同一主库连接** → 读己之写天然成立，不会路由到从库
- `@Transactional` 与编程式 `TransactionTemplate` 均可用

### 7.4 单节点模式

不配置 `replicas` 即单节点：**全部走主库，零路由开销**（不设置路由 Key）。

---

## 8. 多数据源（一个实例读写多个独立数据库）

> 一个 Spring Boot 应用实例可同时读写多个**独立数据库**，引擎可不同（如 MySQL + PostgreSQL + Oracle 混用），每个库独立具备读写分离能力、独立方言与池参数。一个 Spring Boot 应用实例可同时读写多个**独立数据库**，引擎可不同（如 MySQL + PostgreSQL + Oracle 混用），每个库独立具备读写分离能力、独立方言与池参数。

### 8.1 配置

```yaml
feather:
  datasource:
    primary:                    # 默认集群（兼容旧配置；也可用 others.default 命名）
      url: jdbc:mysql://localhost:3306/biz
      username: root
      password: xxx
      replicas:                 # 该集群的从库（可选，读写分离）
        - url: jdbc:mysql://slave1:3306/biz
          username: root
          password: xxx
    others:                     # 其他集群：每个独立数据库一个 key
      order:
        url: jdbc:mysql://localhost:3306/order
        username: root
        password: xxx
      log:
        url: jdbc:postgresql://localhost:5432/log
        username: postgres
        password: xxx
        dialect: postgresql     # 可选：集群级方言覆盖（缺省 auto 自动探测）
        hikari:                 # 可选：集群级池参数覆盖（缺省继承全局 hikari）
          maximum-pool-size: 5
    hikari:                     # 全局池参数（所有集群继承）
      maximum-pool-size: 20
```

- **默认集群确定规则**（按序）：`others.default` → 顶层 `primary` → `others.primary`；配置了 `others` 但无默认集群时**启动失败**；全不配置时回退 Boot 默认数据源
- **集群级可配**：`replicas`（本集群读写分离）、`dialect`（覆盖方言探测）、`hikari`（覆盖全局池参数）
- **账号不继承、不默认**：未配置 username/password 即无账号，每集群按需显式配置
- 集群 url 必填，driver 自动推导，方言缺省自动探测

### 8.2 DAO 绑定

```java
@Repository
@FeatherDataSource("order")        // 绑定 others.order 集群
public class OrderDAO extends BaseDAO<OrderEntity> { }
```

- 不标注 → 默认集群（现有代码零改动）
- `@FeatherDataSource("default")` / `("primary")` 均解析到默认集群
- **集群不存在 → 启动期 fail-fast**（BeanCreationException 指明缺失集群与可用集群），杜绝运行时跑错库

### 8.3 Bean 与注入

每个集群注册一组 Bean（默认集群另有无前缀主 Bean，标记 `@Primary`，兼容旧 `@Autowired`）：

| Bean 类型 | 命名集群 | 默认集群主 Bean |
|---|---|---|
| DataSource | `<name>DataSource` | `featherDataSource` |
| NamedParameterJdbcTemplate | `<name>NamedParameterJdbcTemplate` | `namedParameterJdbcTemplate` |
| JdbcDAO | `<name>JdbcDAO` | `jdbcDAO` |
| DataSourceTransactionManager | `<name>TransactionManager` | `transactionManager` |
| TransactionTemplate | `<name>TransactionTemplate` | `transactionTemplate` |

```java
@Autowired private JdbcDAO jdbcDAO;                            // 默认集群
@Resource(name = "orderJdbcDAO") private JdbcDAO orderJdbcDAO;  // 指定集群
@Autowired private Map<String, JdbcDAO> jdbcDAOMap;             // 全量（key 为 Bean 名）
```

### 8.4 事务（每集群独立）

```java
// 默认集群（与旧版一致）
@Transactional
public void doBiz() { ... }

// 指定集群：@Transactional 指定事务管理器 Bean 名
@Transactional(transactionManager = "orderTransactionManager")
public void doOrder() { ... }

// 编程式：注入对应集群的 TransactionTemplate
@Resource(name = "orderTransactionTemplate") private TransactionTemplate orderTx;
orderTx.executeWithoutResult(status -> { ... });
```

### 8.5 跨库事务（明确不支持）

一个 `@Transactional` 只能管**一个集群**，另一个集群的操作不受该事务保护（独立提交）。
需要跨库一致性的场景请使用 Seata / 本地消息表 / TCC 等方案，框架不实现。

### 8.6 多引擎方言

每个集群**独立探测/配置方言**（§12 方言表），同一实体类可在多个引擎中各自生成正确的 SQL（ColumnMapper 按（实体, 方言）缓存，互不污染）。
全局 `feather.orm.dialect` 显式配置时作为所有未单独指定方言集群的默认。

---

## 9. 事务

```java
// 声明式
@Transactional
public void doSomething() {
    userDAO.saveEntity(user);
    userDAO.updateEntity(user2);
}

// 编程式（注入 TransactionTemplate）
transactionTemplate.executeWithoutResult(status -> {
    userDAO.saveEntity(user);
    throw new RuntimeException("触发回滚");
});
```

---

## 10. ID 生成

- **按主键类型自动匹配**：`Long` 主键 → `SnowflakeIdGenerator`（雪花）；`String` 主键 → `UuidIdGenerator`（UUID）；均通过 `IdGenerator.idType()` 与实体泛型参数匹配
- **多实例部署**：建议配置 `feather.orm.worker-id` 避免雪花 id 冲突
- **自定义**：注册自定义 `IdGenerator<ID>` Bean 即自动纳入匹配（stater 默认按 `@ConditionalOnMissingBean` 提供雪花与 UUID 两个生成器）
- **手动指定**：`saveEntity` 前自行 `setId(...)` 即可，框架不覆盖
- **匹配不到 fail-fast**：实体主键类型没有对应生成器时保存立即报错，提示已注册生成器

---

## 11. RowMapper 实现

- 默认 **Javassist**：运行时生成字节码，零反射、零查表；已适配 JDK 8/17/21（无需 `--add-opens`）
- **自动探测（默认）**：启动时探测环境是否支持字节码生成，不允许（安全策略 / GraalVM 等）自动降级纯反射，无需任何配置
- 强制指定（仅特殊环境）：`-Dfeather.orm.row-mapper=reflection` 或 `-Dfeather.orm.row-mapper=javassist`（显式指定时不做降级）
- 生成的类按实体静态缓存，多个 JdbcDAO 实例共享，不会重复定义

---

## 12. 兼容性与限制

- **Java 17+**（JDK 17/21 已验证），**Spring Boot 3.x**（3.0~3.5，基线 3.5.16）；Java 8 / Boot 2 用户请锁定 v0.2.0
- **SQL 方言可配置**（`feather.orm.dialect`），默认 `auto` 从 JDBC 元数据自动探测：

```yaml
feather:
  orm:
    dialect: auto   # auto(默认) | mysql | postgresql | sqlserver | oracle | sqlite | h2 | dm | default
```

| 方言 | 覆盖数据库 | 标识符引用 | 分页 |
|---|---|---|---|
| `mysql` | MySQL、MariaDB、TiDB、OceanBase、PolarDB(MySQL 模式) | 反引号 | `LIMIT size OFFSET skip` |
| `postgresql` | PostgreSQL、openGauss、KingbaseES(人大金仓)、CockroachDB | 双引号 | `LIMIT size OFFSET skip` |
| `sqlserver` | SQL Server 2012+、Azure SQL | 方括号 | `OFFSET skip ROWS FETCH NEXT size ROWS ONLY`（自动补 ORDER BY） |
| `oracle` | Oracle 12c+ | 双引号 | `OFFSET skip ROWS FETCH NEXT size ROWS ONLY`（自动补 ORDER BY） |
| `sqlite` | SQLite | 双引号 | `LIMIT size OFFSET skip` |
| `h2` | H2、HSQLDB | 双引号 | `LIMIT size OFFSET skip` |
| `dm` | 达梦 DM | 双引号 | `LIMIT size OFFSET skip` |
| `default` | 未知数据库（兜底） | 最小引用 | `LIMIT size OFFSET skip` |

**标识符引用策略（最小引用）**：合法的普通列名/表名（字母数字下划线、非保留字）**不引用**，
跨库生成完全一致的 SQL；保留字（如 `order`、`user`）或含特殊字符的名称才按方言引用；
`@Column("`col`")` 等显式带引号的值原样透传。

**方言感知的差异行为**：
- `forceIndex()` 仅 MySQL 系支持，其他方言调用直接抛异常（fail-fast）
- `forUpdate()`：SQLite 单写者自动忽略；SQL Server 不支持 FOR UPDATE 语法，调用抛异常
- 分页 count 自动剥离 `order by` / `for update`，避免 SQL Server 对无 TOP/LIMIT 的子查询排序报错
- 零时间（`0000-00-00`）语义仅 MySQL 支持，非 MySQL 库请勿依赖
- 单库单表，无分库分表、无实体缓存（v1 定位）
- **支持多数据源**（多个独立数据库，引擎可不同），见第 8 章；跨库事务不支持
- 一套框架一套配置：引入后持久层即 Feather，不与 MyBatis/JPA 混用

---

## 13. 常见踩坑清单

| 场景 | 正确做法 |
|---|---|
| REST 请求枚举数字 | `CodeEnum.getValue()` 加 `@JsonValue`，否则 Jackson 按序数解析 |
| 更新时想清空某列 | update 只更新非 null 字段；清空用原生 SQL |
| 零时间 | MySQL 连接加 `zeroDateTimeBehavior=convertToNull` |
| 批量更新 | `updateEntityList` 用 COALESCE 语义，null 字段不触碰 |
| 查询列不存在 | DTO 映射自动跳过；实体映射（DO）缺列会抛异常 |
| DAO 不生效 | 记得加 `@Repository` |
| 查询条件拼错字段 | 抛异常（fail-fast），不会静默查错 |
| 多库场景 DAO 跑错库 | 用 `@FeatherDataSource("集群名")` 标注；集群不存在时启动即报错 |
| 多库事务 | 一个 `@Transactional` 只保护一个集群；跨库一致性用 Seata/本地消息表/TCC |

---

## 14. 与 Agent 协作约定

1. 实体类名 `XxxEntity extends BaseEntity<Long>`（雪花）或 `BaseEntity<String>`（UUID）；DAO `XxxDAO extends BaseDAO<XxxEntity>` 并标 `@Repository`
2. 字段映射优先**约定**（驼峰→下划线），不规则才用 `@Column`
3. 条件查询一律用 `QueryHelper`（杜绝 SQL 拼接注入）
4. 复杂对象/集合字段零注解自动 JSON；枚举存业务码用 `CodeEnum`
5. 新增/更新语义：null 字段不落库、不更新；批量用 `*List` 方法
6. 主从场景：读多写少默认即可；一致性敏感操作用 `forceMaster()` 或事务
7. 遇到列名/字段名映射异常、JSON 反序列化失败均为 fail-fast，向上抛出定位
