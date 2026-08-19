# Feather ORM

[![Maven Central](https://img.shields.io/maven-central/v/io.github.sombreknight/feather-orm-spring-boot-starter)](https://search.maven.org/artifact/io.github.sombreknight/feather-orm-spring-boot-starter)
[![CI](https://github.com/SombreKnight/feather-orm/actions/workflows/ci.yml/badge.svg)](https://github.com/SombreKnight/feather-orm/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

基于 Spring `JdbcTemplate` 的轻量级 ORM 框架：注解 + 驼峰约定驱动，继承即得 CRUD，零 XML、零配置魔法。

> 📖 **完整使用教程见 [usage.md](usage.md)**（全部 API、类型映射规则、路由/事务行为契约、踩坑清单）

> 设计目标：让"写 DAO"这件事回归简单——不需要 MyBatis 的 mapper 文件，不需要 JPA 的实体关系负担，
> 一行 `@Table` + 继承 `BaseDAO`，剩下的交给约定。

## 特性

- **继承式 CRUD**：`class UserDAO extends BaseDAO<UserEntity>` 即获得增删改查、批量、分页、单字段查询全套能力
- **驼峰 ↔ 下划线约定**：`userName` 自动映射 `user_name`，`@Column` 仅用于不规则列名
- **类型映射注册表**（`TypeHandler` SPI）：
  - 简单类型（数值 / String / BigDecimal / byte[] / 时间）原生存取
  - 枚举三层约定：默认存 `name()` → 实现 `CodeEnum` 存业务码 → `@EnumValue` 逃生舱
  - 复杂对象 / 泛型集合自动 JSON 序列化，**零注解**（如 `List<String>`、`Map<String,Object>`）
- **Javassist 运行时字节码 RowMapper**：生成期预解析类型处理器，运行时零反射、零查表；可切换到纯反射实现兜底
- **一主多从读写分离（可选）**：配置 `replicas` 即启用，写操作与按主键查询走主库，普通查询随机从库；不配即单节点零路由开销；`forceMaster()` 随时强制主库
- **事务开箱即用**：`@Transactional` + 编程式 `TransactionTemplate` 均可用
- **Snowflake 默认主键**：id 为空自动生成，也可自行指定
- **Fail-fast 安全**：查询条件里的未知字段立即抛异常，杜绝 SQL 注入拼串

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.sombreknight</groupId>
    <artifactId>feather-orm-spring-boot-starter</artifactId>
    <version>0.3.0</version>
</dependency>
```

### 2. 配置数据源（一套配置接管，无需再配 `spring.datasource.*`）

```yaml
feather:
  datasource:
    primary:
      url: jdbc:mysql://localhost:3306/demo?useUnicode=true&characterEncoding=utf-8
      username: root
      password: xxx
    replicas:              # 可选，不配即单节点
      - url: jdbc:mysql://slave1:3306/demo
        username: root
        password: xxx
    hikari:                # 可选，主从共用，默认即 Hikari 默认值
      maximum-pool-size: 20
      minimum-idle: 5
  orm:
    row-mapper: javassist  # javassist（默认）| reflection
    worker-id: 1           # 可选，雪花 workerId，多实例部署建议显式配置
```

### 3. 定义实体

```java
@Table("tb_user")
public class UserEntity extends BaseEntity {

    private String userName;            // 约定映射 user_name
    private Integer age;

    @Column("phone_no")                // 不规则列名用 @Column 覆盖
    private String phone;

    private OrderStatus status;         // 枚举：实现 CodeEnum 则存业务码，否则存 name()
    private ExtInfo extInfo;            // 复杂对象：自动 JSON 列
    private List<String> tags;          // 泛型集合：自动 JSON 列

    // getter / setter ...
}
```

```java
public enum OrderStatus implements CodeEnum<Integer> {
    CREATED(1), PAID(2), CANCELLED(9);
    // ... getValue()
}
```

### 4. 定义 DAO（继承即得 CRUD）

```java
@Repository
public class UserDAO extends BaseDAO<UserEntity> {
}
```

### 5. 使用

```java
@Resource
private UserDAO userDAO;

// 新增
userDAO.saveEntity(user);

// 按主键
UserEntity user = userDAO.findById(id);
List<UserEntity> list = userDAO.findByIds(ids);

// 条件查询（面向对象拼 SQL）
List<UserEntity> adults = userDAO.findList(userDAO.getQueryHelper()
        .whereEqual("userName", "张三")
        .whereGte("age", 18)
        .orderByDesc("age"));

// 分页
PagingResult<UserEntity> page = userDAO.findPageByPageNum(
        userDAO.getQueryHelper().limit(1, 10));

// 单字段
String name = userDAO.findField(String.class,
        userDAO.getQueryHelper().selectFields("userName").whereEqual("id", id).limitOne());

// 更新（仅更新非 null 字段，null 字段不触碰）
UserEntity update = new UserEntity();
update.setId(id);
update.setUserName("李四改");
userDAO.updateEntity(update);

// 删除
userDAO.deleteEntity(user);
```

## 类型映射一览

| Java 类型 | 存储方式 | 说明 |
|---|---|---|
| `Integer/Long/Short/Byte/Double/Float/Boolean/BigDecimal/String/byte[]` | 原生 | 自动处理 null 与 wasNull |
| `java.util.Date`、`java.sql.*`、`java.time.*` | 原生 | Spring 自动绑定 |
| `FeatherDate` | 原生 | 保留零时间（`0000-00-00 00:00:00`）语义 |
| `enum` | 默认 `name()` | 实现 `CodeEnum<T>` 后按 `getValue()` 存业务码 |
| 第三方枚举 | `@EnumValue("getCode")` | 逃生舱，按指定方法返回值存取 |
| 任意复杂对象 / 集合 | JSON 字符串 | 按字段泛型类型反序列化，`List<X>`、`Map<K,V>` 均无需额外注解 |

**自定义类型处理器**：实现 `TypeHandler` 接口并注册为 Spring Bean，自动获得最高优先级。

```java
@Component
public class MyHandler implements TypeHandler {
    // supports / toJdbcValue / fromResultSet
}
```

## 版本发布

已发布到 Maven Central（`io.github.sombreknight`），发版流程：

1. 更新 pom 版本号（如 `0.3.0`）并提交
2. 打 tag 并推送：`git tag v0.3.0 && git push origin v0.3.0`
3. GitHub Actions 自动构建、测试、签名并发布（需先在仓库配置 Secrets，见 [release.yml](.github/workflows/release.yml)）

## 模块结构

```
feather-orm
├── feather-orm-core                     # 注解、映射、类型转换、SQL 生成（仅依赖 spring-jdbc + javassist + jackson）
├── feather-orm-spring-boot-starter      # 自动配置：数据源接管、Bean 装配、事务
└── feather-orm-samples                  # 可运行示例应用（H2 开箱即用）
```

运行示例：

```bash
mvn -pl feather-orm-samples spring-boot:run
# 或
cd feather-orm-samples && mvn spring-boot:run
```

启动后访问 `http://localhost:9090/users` 体验 CRUD。

## 兼容性

- Java 17+（JDK 17 / 21 已验证），Spring Boot 3.x（3.0 ~ 3.5，基线 3.5.16）
- **Java 8 / Spring Boot 2.x 用户请锁定 [v0.2.0](https://github.com/SombreKnight/feather-orm/releases/tag/v0.2.0)**（Boot 2 最终支持版本，Maven Central 可下载）
- Javassist 字节码生成已适配模块系统：JDK 9+ 走 `MethodHandles.defineClass`，无需 `--add-opens` 参数
- **多数据库方言**：`feather.orm.dialect` 自动探测（默认 `auto`），支持 MySQL/MariaDB/TiDB/OceanBase、
  PostgreSQL/openGauss/KingbaseES、SQL Server、Oracle 12c+、SQLite、H2、达梦 DM；
  标识符引用与分页语法按方言族生成（详见 [usage.md](usage.md) 第 11 节）

## 设计取舍（当前版本）

- 单库单表，不做分库分表（v1 定位）
- 无实体缓存层（缓存策略交给上层）
- `update`/批量更新采用"仅非 null 字段"语义；批量更新通过单条 `COALESCE(:列, 原列)` SQL 一次 batchUpdate 完成，与单条更新语义完全一致

## Roadmap

- [x] Spring Boot 3.x 兼容（基线 3.5.16，Java 17+；Boot 2 用户锁定 v0.2.0）
- [ ] Spring Boot 4.x 迁移评估（Spring Framework 7 / Jackson 3 / 模块化自动配置）
- [ ] PostgreSQL / MySQL / H2 多库集成测试矩阵（CI 服务容器）
- [ ] `@Transactional` 与编程式事务的传播测试
- [ ] 批量插入按"非空列集合"分组优化（已实现）
- [ ] Maven Central 发布

## License

[Apache License 2.0](LICENSE)
