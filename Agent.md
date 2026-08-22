# Feather ORM — Agent 开发手册

> 本文件是给 Agent（及后续维护者）的**长期迭代参考**：项目结构、设计约定、构建/测试/发布命令、CI 行为、踩坑记录。
> 完整 API 与类型映射契约见 [usage.md](usage.md)；用户视角文档见 [README.md](README.md)。

---

## 1. 项目速览

| 项 | 值 |
|---|---|
| 定位 | 基于 Spring JdbcTemplate 的轻量级 ORM（注解 + 驼峰约定，继承即得 CRUD） |
| 仓库 | https://github.com/SombreKnight/feather-orm（默认分支 `main`） |
| 包名 | `io.github.sombreknight.feather` |
| 坐标 | `io.github.sombreknight:feather-orm-spring-boot-starter`（已发布 Maven Central） |
| 当前版本 | 0.4.0（发版流程见第 6/7 节） |
| License | Apache 2.0 |
| 技术栈 | Java 17 字节码（JDK 17/21 兼容）、Spring Boot 3.5.x BOM、Javassist 3.29.2、Jackson、HikariCP |

## 2. 本地开发环境

- **默认 JDK 17**：`/Users/zhangchenxi/Library/Java/JavaVirtualMachines/jdk-17.0.20.jdk`（Zulu 17）；
  构建前 `export JAVA_HOME=/Users/zhangchenxi/Library/Java/JavaVirtualMachines/jdk-17.0.20.jdk/Contents/Home`
  （系统默认 java 是 1.8，`mvn` 直接跑会因 Boot 3 编译失败）
- Maven 3.8.6，本地 `~/.m2/settings.xml` 含：阿里云镜像 + rdc 仓库 + **`central` server（Sonatype token，本地发版用）**
- **gh CLI 已登录**（SombreKnight，keyring 认证）——查 Actions/设置 secrets 直接用 gh
- GPG 签名密钥：`EBCD864645131551`（无口令，自动化签名用），私钥备份在 `~/feather-orm-gpg-backup/`（**勿提交仓库**）

## 3. 模块与架构速览

```
feather-orm
├── feather-orm-core                   # 核心（仅依赖 spring-jdbc + javassist + jackson + slf4j）
│   └── io/github/sombreknight/feather
│       ├── annotation    @Table(value,idColumn) / @Column(value) / @EnumValue
│       ├── core          BaseEntity / BaseDAO / JdbcDAO / QueryHelper / SqlParam
│       │                 PageInfo / PagingResult / IdGenerator / SnowflakeIdGenerator / UuidIdGenerator
│       ├── type          TypeHandler(SPI) / TypeHandlerRegistry / CodeEnum
│       │                 Simple/Temporal/FeatherDate/Enum/JsonTypeHandler
│       ├── mapping       ColumnMapper / Mapper / FieldMeta / FieldHandler
│       │                 RowMapperSupport / JavassistRowMapperFactory / ReflectionRowMapperFactory / FieldRowMapper
│       ├── datasource    RoutingDataSource / DataSourceHolder / DataSourceKey
│       ├── util          FeatherDate / NamingUtils / ReflectUtils / JsonUtils / RandomUtils
│       └── exception     FeatherDaoException
├── feather-orm-spring-boot-starter    # 自动配置：FeatherAutoConfiguration + FeatherProperties
│   └── META-INF/spring.factories + AutoConfiguration.imports（双注册，兼容 2.x 全版本）
└── feather-orm-samples                # 可运行示例（H2 内存库，端口 9090，不发布）
```

**关键类职责**：
- `JdbcDAO`：核心执行引擎（约 700 行）——CRUD/批量/分页/DTO/字段查询 + 主从路由
- `QueryHelper`：面向对象拼 SQL（字段名→列名自动映射，fail-fast）
- `TypeHandlerRegistry`：类型映射单一事实源（user > 简单 > 时间 > FeatherDate > 枚举 > JSON 兜底）
- `RoutingDataSource`：自研路由数据源（**不继承 AbstractRoutingDataSource**，见第 9 节坑 3）

## 4. 核心设计约定（改代码必须遵守）

1. **命名**：实体 `XxxEntity extends BaseEntity<Long>`（雪花主键）或 `BaseEntity<String>`（UUID 主键）；DAO `XxxDAO extends BaseDAO<XxxEntity>` 且标 `@Repository`；查询投影用 DTO。**禁止再引入 DO/domain/VO 命名**（2024 重构已统一为 Entity/DTO）
2. **列名约定**：驼峰→下划线（`userName`→`user_name`）；不规则列名才用 `@Column`；主键列名默认 `id`，特殊用 `@Table(idColumn=...)`
3. **类型映射**：复杂对象/集合字段**零注解自动 JSON**；枚举默认 `name()`，业务码用 `CodeEnum<T>`（`getValue()`），第三方枚举用 `@EnumValue("方法名")` 逃生舱
4. **null 语义**：insert 跳过 null 列（DB 默认值生效）；update 只更新非 null 字段（COALESCE，null 字段不触碰）
5. **fail-fast**：QueryHelper 未知字段、null 参数、无 where 的 findList 一律抛 `FeatherDaoException`，不静默
6. **主从路由**：写 + `findById(s)` 走主库；普通查询走从库；事务内复用主库连接（读己之写）；`forceMaster()` 临时切主；不配 replicas 即单节点零路由
7. **RowMapper**：默认 Javassist 字节码生成；`feather.orm.row-mapper=reflection` 可切纯反射兜底

> 完整类型映射表、BaseDAO/JdbcDAO/QueryHelper 全 API 见 **usage.md**。

## 5. 本地构建 / 测试 / 打包

```bash
mvn test                                    # 全量测试（core 60 + starter 7 + samples 2 = 69）
mvn clean package                           # 打包：普通 jar + sources.jar + javadoc.jar（gpg 签名只在 verify/deploy 阶段）
mvn -pl feather-orm-core test               # 单模块测试
```

**测试注意**：
- 主从路由测试（`MasterSlaveRoutingTest`）用**连接计数 DataSource + 双 H2 实例**验证路由，随机选从库——**测试数据必须复制到所有从库**，否则随机路由选到空库会偶发失败
- 各测试类用独立 H2 内存库名（`feather-crud` / `feather-jdbcdao` / `feather-routing-master` 等），互不干扰
- JDK 17 验证：`JAVA17=/Users/zhangchenxi/Library/Java/JavaVirtualMachines/jdk-17.0.20.jdk/Contents/Home/bin/java` + `mvn test`

## 6. 发布到 Maven Central（本地手动方式）

前置（已完成，勿重复）：Sonatype Central 账号 + `io.github.sombreknight` 命名空间验证 + User Token 写入 `~/.m2/settings.xml`（server id=`central`）+ GPG 密钥 `EBCD864645131551`。

```bash
cd ~/IdeaProjects/feather-orm
# 1. 改版本：pom.xml 与所有子模块 + README/usage.md 的依赖版本号
# 2. 构建、测试、签名、上传（autoPublish=true 自动发布）
mvn clean deploy -pl '!feather-orm-samples'   # samples 是演示应用，不进中央仓库
# 3. 到 https://central.sonatype.com/publishing 确认状态（PUBLISHED），10分钟~几小时同步到 repo1
```

- 产物打包位置：`target/central-staging/`，最终 bundle：`target/central-publishing/central-bundle.zip`
- **认证格式**：插件发 `Authorization: UserToken <base64(username:password)>`（不是 Bearer/Basic）
- **发布后回仓库验证**：`curl -s -o /dev/null -w "%{http_code}" https://repo1.maven.org/maven2/io/github/sombreknight/feather-orm-spring-boot-starter/<版本>/feather-orm-spring-boot-starter-<版本>.pom` → 200 即同步完成

## 7. 发布到 Maven Central（CI 自动方式，推荐）

```bash
# 1. 改 pom 版本号 → 提交（版本号与 tag 保持一致，如 0.1.2 ↔ v0.1.2）
# 2. 打 tag 推送，GitHub Actions 全自动：GPG 导入 → 写凭据 → versions:set → 测试 → 签名上传自动发布
git tag v0.1.2 && git push origin v0.1.2
# 3. 监控：gh run list --repo SombreKnight/feather-orm 或 https://github.com/SombreKnight/feather-orm/actions
```

**仓库 Secrets**（`https://github.com/SombreKnight/feather-orm/settings/secrets/actions`）：
- `GPG_PRIVATE_KEY`：armored 私钥全文（来自 `~/feather-orm-gpg-backup/feather-signing-key.asc`）
- `SONATYPE_USERNAME` / `SONATYPE_PASSWORD`：Sonatype User Token

**⚠️ 关键坑**：`release.yml` 里**显式写入 `~/.m2/settings.xml`**（字面值）。不要用 setup-java 的 `server-username/server-password` 输入——它生成 `${env.变量名}` 占位，传字面值会导致空凭据 401（见第 9 节坑 4）。

## 8. CI 工作流说明

- **`.github/workflows/ci.yml`**：push/PR 触发，JDK 17 + 21 矩阵跑 `mvn test`（质量门禁，含 PG16/MySQL8 服务容器集成测试）
- **`.github/workflows/release.yml`**：`v*` tag 触发：checkout → setup-java(JDK17+GPG) → 写 settings.xml → `versions:set`（tag 版本号写入所有 pom）→ `mvn test` → `mvn clean deploy -pl '!feather-orm-samples'`
- 改 workflow 后：push 到 main 即可；已存在的 tag 需 `git tag -f vX.Y.Z && git push -f origin vX.Y.Z` 重新触发

## 9. 关键教训与坑（本仓库实测）

1. **Javassist 在 JDK 17+ 的模块系统**：`CtClass.toClass()` 走反射 `ClassLoader.defineClass` 会 `InaccessibleObjectException`。修复：JDK 9+ 用 `ctClass.toClass(referenceClass)`（内部 `MethodHandles.privateLookupIn`），JDK 8 回退 `toClass(loader, null)`；版本检测用 `Class.getModule()` 是否存在。见 `JavassistRowMapperFactory.defineGeneratedClass`
2. **生成的 RowMapper 类必须静态缓存**（按类名 + DO/DTO 后缀区分）：多个 JdbcDAO/RowMapperSupport 实例映射同一实体时，重复 defineClass 会 `LinkageError`
3. **不要用 AbstractRoutingDataSource**：其默认构造器 `new JndiDataSourceLookup()`，而 `JndiLocatorSupport` 在 spring-context——core 只依赖 spring-jdbc 时会 `NoClassDefFoundError`。自研 `RoutingDataSource`（实现 DataSource + 按 DataSourceHolder key 路由 + 默认主库）
4. **setup-java 凭据机制**：`server-username/server-password` 期望**环境变量名**（写进 settings.xml 是 `${env.NAME}` 占位），不是字面值。项目用「显式 cat 写 settings.xml」规避
5. **Sonatype Central 认证**：User Token 认证头是 `UserToken <base64(user:pass)>`；旧 OSSRH 已关停（2025-06-30），只走 central.sonatype.com
6. **`versions:set` 多模块联动正常**：`mvn versions:set -DnewVersion=x.y.z -DgenerateBackupPoms=false` 会同步父/子 pom，且 starter 里 `${project.version}` 引用保持
7. **QueryHelper 排序片段**：`KEY_WORD_ASC = " asc"`（无尾随空格），join 后才是 `age asc, id desc`——改常量注意别带回空格

## 10. Agent 协作速查

- 改代码前先读 **usage.md**（行为契约）+ 本文档第 4 节（约定）
- 实体/DAO 命名、类型映射、null 语义、fail-fast 行为与 usage.md 保持一致
- 发版 = 改版本号 → 提交 → `git tag vX.Y.Z && git push origin vX.Y.Z`
- 查 CI：`gh run list/watch --repo SombreKnight/feather-orm`
- 不要提交任何密钥/token 到仓库；`~/.m2/settings.xml` 与 `~/feather-orm-gpg-backup/` 均在仓库外
