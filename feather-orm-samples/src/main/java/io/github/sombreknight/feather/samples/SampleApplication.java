package io.github.sombreknight.feather.samples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Feather ORM 示例应用
 *
 * <p>启动后访问：</p>
 * <ul>
 *     <li>GET  /users          列表（可选 ?userName= 过滤）</li>
 *     <li>GET  /users/{id}     详情</li>
 *     <li>POST /users          新增</li>
 *     <li>PUT  /users/{id}     更新（仅非 null 字段）</li>
 *     <li>DELETE /users/{id}   删除</li>
 *     <li>GET  /users/page     分页（?page=1&size=10）</li>
 * </ul>
 *
 * @author sombreknight
 */
@SpringBootApplication
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }
}
