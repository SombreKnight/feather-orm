package io.github.sombreknight.feather.core;

import io.github.sombreknight.feather.util.FeatherDate;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * SQL 命名参数载体
 *
 * @author sombreknight
 */
public final class SqlParam {

    private final Map<String, Object> map = new HashMap<>();
    private final List<String> nullParamList = new LinkedList<>();

    private SqlParam() {
    }

    public static SqlParam create() {
        return new SqlParam();
    }

    public static SqlParam create(String key, Object value) {
        SqlParam sqlParam = new SqlParam();
        sqlParam.add(key, value);
        return sqlParam;
    }

    /**
     * 增加参数；value 为 null 时记录到 nullParamList（执行前校验会拦截）
     */
    public SqlParam add(String key, Object value) {
        if (value == null) {
            nullParamList.add(key);
            return this;
        }
        if (value.getClass().isArray()) {
            map.put(key, Arrays.asList((Object[]) value));
        } else {
            map.put(key, value);
        }
        return this;
    }

    /**
     * 转为参数 Map（FeatherDate 自动转换为 JDBC 可识别值）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof FeatherDate) {
                FeatherDate fd = (FeatherDate) value;
                if (fd.isZeroTime()) {
                    result.put(entry.getKey(), "0000-00-00 00:00:00");
                } else {
                    result.put(entry.getKey(), new Timestamp(fd.getTime()));
                }
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public List<String> getNullParamList() {
        return nullParamList;
    }

    public void clear() {
        map.clear();
        nullParamList.clear();
    }
}
