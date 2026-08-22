# SPEC：QueryHelper 类型安全的 Lambda 字段引用

> 状态：**待对齐**（对齐后进入实现）
> 关联 Issue：[#4](https://github.com/SombreKnight/feather-orm/issues/4)
> 对应需求：用 Lambda 方法引用替代 where 条件中的字符串字面量字段名

---

## 1. 背景与动机

当前 QueryHelper 的条件字段以字符串字面量传入：

```java
dao.findList(dao.getQueryHelper()
        .whereEqual("userName", "张三")
        .whereGte("age", 18)
        .orderByDesc("createTime"));
```

痛点：
1. **无编译期检查**：字段名拼错只能在运行时由 ColumnMapper fail-fast 抛异常
2. **重构不跟随**：实体字段改名，IDE 不会同步字符串字面量
3. **可读性一般**：裸字符串散落在查询代码中

**选型结论**（已在 issue #4 讨论多轮，原型验证）：
- ❌ 注解处理器生成"内部类"常量：JSR 269 只能生成独立新类，无法往已有实体类塞成员（已验证 javac 报错）
- ❌ 常量接口 + `implements`：需实体加一行 implements，且常量与字段同名会被实例字段遮蔽（需改大写命名）
- ❌ Lombok `@FieldNameConstants`：需额外注解、命名风格不匹配（生成小驼峰）、与框架零注解哲学冲突
- ✅ **Lambda 方法引用**：`UserEntity::getUserName` 形式上就是"实体点字段"，零生成器、零注解、零 implements，编译期类型安全

## 2. 目标与非目标

### 目标
- 新增 `SFunction` 函数式接口与 QueryHelper 的 Lambda 重载方法
- 字段名解析：方法引用 → Java 字段名 → 复用现有 ColumnMapper 列映射（fail-fast 保留）
- 与现有字符串 API 完全并存，向后兼容，可渐进迁移
- JDK 17（当前基线，CI 同时跑 17/21）

### 非目标（第一版不做）
- ❌ 嵌套 JSON 路径（`extInfo.name`）：单层方法引用无法表达，由保留的字符串 API 覆盖
- ❌ selectFields 的别名（alias）：`selectFields("userName as u")` 由字符串 API 覆盖
- ❌ GraalVM native-image 适配（SerializedLambda 反射在 native 下有已知限制，记录不处理）

### 字符串 API 的定位（已确认：无存量代码，不为兼容保留）
框架当前无存量使用方，`FieldFunction` 为主 API。字符串版**仅保留 Lambda 表达不了的两类能力**：
1. 嵌套 JSON 路径：`whereEqual("extInfo.name", ...)`
2. 动态/运行时字段名：变量形式拼条件
其余字符串方法保留但文档定位为补充入口，不标记 deprecated。

## 3. API 设计

### 3.1 新增接口：`FieldFunction`（已确认）

位于 `io.github.sombreknight.feather.core`（与 QueryHelper 同包）：

```java
/**
 * 可序列化的字段函数式接口，用于 Lambda 方式引用实体字段。
 * 实现必须为方法引用或等价 lambda（如 UserEntity::getUserName）。
 */
@FunctionalInterface
public interface FieldFunction<T, R> extends Function<T, R>, Serializable {
}
```

**命名决策：`FieldFunction`（自解释，用户确认）**

### 3.2 QueryHelper 新增重载（全部与字符串版并存）

| 字符串 API（保留） | 新增 Lambda 重载 |
|---|---|
| 字符串 API（补充入口） | 新增 FieldFunction 重载 |
|---|---|
| `whereEqual(String, Object)` | `whereEqual(FieldFunction<T,?>, Object)` |
| `whereGt / whereGte / whereLt / whereLte(String, Object)` | 同名的 4 个 FieldFunction 重载 |
| `whereLike(String, String)` | `whereLike(FieldFunction<T,?>, String)` |
| `whereIn(String, List<R>)` | `whereIn(FieldFunction<T,?>, List<R>)` |
| `whereNotIn(String, List<R>)` | `whereNotIn(FieldFunction<T,?>, List<R>)` |
| `orderByAsc / orderByDesc(String)` | 同名的 2 个 FieldFunction 重载 |
| `groupBy(String...)` | `groupBy(FieldFunction<T,?>...)`（@SafeVarargs） |
| `countField(String...)` | `countField(FieldFunction<T,?>)` |
| `selectFields(String...)` | ❌ 不做（alias 刚需，字符串保留） |

共 **13 个新增重载** + 1 个新接口。

### 3.3 使用示例

```java
// 普通条件
dao.findList(dao.getQueryHelper()
        .whereEqual(UserEntity::getUserName, "张三")
        .whereGte(UserEntity::getAge, 18)
        .whereIn(UserEntity::getId, idList)
        .orderByDesc(UserEntity::getCreateTime));

// 继承自 BaseEntity 的字段（getter 在父类）
dao.findOne(dao.getQueryHelper().whereEqual(UserEntity::getId, 1001L));

// 布尔字段（is 前缀 getter）
qh.whereEqual(UserEntity::getActive, true);

// count
dao.count(dao.getQueryHelper().whereGte(UserEntity::getAge, 18));
```

### 3.4 null 参数的编译期歧义（已知，接受）

```java
qh.whereEqual(null, value); // ❌ 编译错误：String 与 SFunction 重载歧义
```
调用方需显式转型：`qh.whereEqual((String) null, value)`。罕见场景，文档说明即可。

## 4. 核心实现机制

### 4.1 字段名解析流程（新增工具类 `LambdaUtils`，位于 `io.github.sombreknight.feather.util`）

```
FieldFunction 实例（UserEntity::getUserName）
  → 反射调用 writeReplace()（合成 lambda 类自带，因接口 extends Serializable）
  → 得到 SerializedLambda
  → sl.getImplMethodName() = "getUserName"
  → 剥前缀推导字段名：get/is/set + 首字母小写 → "userName"
  → 复用现有 QueryHelper.getDbFieldName("userName") → ColumnMapper 校验 + 列映射
```

### 4.2 前缀推导规则

- `get` / `set` 前缀 → 剥掉，首字母小写（`getUserName` → `userName`）
- `is` 前缀（boolean getter 习惯）→ 剥掉，首字母小写（`getActive`/`isActive` → `active`）
- 其它形式（构造器引用 `UserEntity::new`、静态方法引用）→ 抛 `FeatherDaoException`，fail-fast，消息含方法名

### 4.3 缓存设计

`LambdaUtils` 内部静态 `ConcurrentHashMap<String, String>`（缓存 key 与解析均与 implClass 无关，天然兼容 CGLIB 代理）：
- key = `implClass.getName() + "#" + implMethodName`
- value = 解析出的 Java 字段名
- 避免每次调用都走反射（writeReplace + 字符串处理）
- 解析失败不缓存，每次抛异常（fail-fast 一致性）

### 4.4 父类字段（继承自 BaseEntity）

- 已验证：`ColumnMapper` 用 `ReflectUtils.findFields(clazz, true)` 收集字段（含父类，子类优先去重）
- 因此 `UserEntity::getId`（getter 定义在 BaseEntity）推导出 `id` 后，`getDbFieldName` 能正常映射 ✅
- 注意：此时 `SerializedLambda.implClass` 是 `BaseEntity`，但**推导只用方法名，不依赖 implClass**，天然兼容

### 4.5 Spring CGLIB 代理类

实体被代理时（少见），方法引用绑定在 `UserEntity$$EnhancerBySpringCGLIB$$...` 上，但 `implMethodName` 仍是 `getUserName` → 推导不受影响 ✅

### 4.6 异常处理

- 非法方法引用（构造器/静态方法）→ `FeatherDaoException("无法从方法引用 [xxx] 推导字段名...")`
- 未知字段 → 复用现有 `getDbFieldName` 的 fail-fast 异常
- 与框架"fail-fast 安全"哲学一致

## 5. 兼容性影响

| 维度 | 影响 |
|---|---|
| 二进制兼容 | ✅ 只新增接口与重载方法，不改不删（框架无存量代码，此项无压力） |
| 源码兼容 | ✅ 字符串 API 按能力需要保留（嵌套路径/动态字段名），非兼容性目的 |
| 重载擦除 | ✅ `(String, Object)` vs `(FieldFunction, Object)` 参数类型不同，无擦除冲突（已验证 javac 语义） |
| JDK | ✅ 17（SerializedLambda 自 JDK 8 引入）；CI matrix 17/21 均需通过 |
| IDE 体验 | ✅ IDEA 对 `UserEntity::` 方法引用自动补全 getter 列表，拼写不可能出错 |
| 性能 | 首调用一次反射 + 字符串解析，之后走缓存；与 SQL 执行开销相比可忽略 |

## 6. 测试计划

在 `QueryHelperTest`（无 DB 单测）基础上扩展，新增一个测试类 `QueryHelperLambdaTest`：

1. **等价性**：Lambda 版每个方法生成的 where SQL 与字符串版完全一致
   - `whereEqual(UserEntity::getUserName, ...)` → `user_name = :user_name_1`
   - `@Column` 覆盖字段（`UserEntity::getPhone`）→ `phone_no = :phone_no_1`
2. **父类字段**：`whereEqual(UserEntity::getId, 1L)` → `id = :id_1`
3. **布尔 is 前缀**：实体加 boolean 字段验证（如 `getActive`）
4. **同名占位符唯一性**：`whereGte(UserEntity::getAge, 18).whereLt(UserEntity::getAge, 60)` → `age_1`/`age_2`
5. **whereIn 单元素降级**：与字符串版行为一致
6. **异常路径**：非法方法引用（如 `UserEntity::new` 或静态方法）抛 `FeatherDaoException`
7. **方言回归**：现有 dialect 测试全量跑通（不新增）

## 7. 文档更新

- `usage.md` 第 5 章：新增 5.x"Lambda 字段引用"小节，含示例与已知限制（null 歧义、嵌套路径、alias）
- `README.md` 特性列表：提一句"类型安全的 Lambda 字段引用"
- QueryHelper Javadoc：类注释补充 Lambda 用法示例

## 8. 版本与发布

- 版本：`0.1.0` → `0.2.0`（新特性，minor）
- 发布流程沿用现有 release.yml（无新依赖、无新发布配置）

## 9. 实施步骤

1. 新增 `SFunction` 接口 + `LambdaUtils` 工具类（含缓存）
2. QueryHelper 新增 13 个 Lambda 重载，复用 `getDbFieldName`，新增 `resolveField(SFunction)` 内部方法
3. 新增 `QueryHelperLambdaTest` 全量用例
4. 更新 usage.md / README / Javadoc
5. 本地 `mvn test` 全绿（core + starter + samples）
6. 提交：feat 前缀中文 commit；可选同步更新 issue #4 关闭

## 10. 决策记录（已对齐）

| # | 决策 | 结论 |
|---|---|---|
| 1 | 接口命名 | **`FieldFunction`**（自解释，用户确认） |
| 2 | 向后兼容 | **不保留兼容负担**；字符串 API 仅按能力保留（嵌套路径/动态字段名），主 API 为 FieldFunction |
| 3 | groupBy 可变参数 | 采用 `groupBy(FieldFunction...)` + `@SafeVarargs` |
| 4 | countField | 纳入第一版 |
| 5 | 提交粒度 | 拆两次 commit：feat（接口+工具+QueryHelper+测试）/ docs（usage+README） |
| 6 | issue #4 | 完成后贴 spec 链接并关闭 |
