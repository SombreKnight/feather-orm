package io.github.sombreknight.feather.samples.config;

import io.github.sombreknight.feather.samples.dao.UserDAO;
import io.github.sombreknight.feather.samples.entity.ExtInfo;
import io.github.sombreknight.feather.samples.entity.OrderStatus;
import io.github.sombreknight.feather.samples.entity.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;

/**
 * 启动时插入演示数据（表为空时）
 *
 * @author sombreknight
 */
@Component
public class SampleDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataInitializer.class);

    @Resource
    private UserDAO userDAO;

    @Override
    public void run(String... args) {
        if (userDAO.count(userDAO.getQueryHelper()) > 0) {
            return;
        }
        for (int i = 1; i <= 3; i++) {
            UserEntity user = new UserEntity();
            user.setUserName("示例用户" + i);
            user.setAge(20 + i);
            user.setStatus(i % 2 == 0 ? OrderStatus.PAID : OrderStatus.CREATED);
            ExtInfo extInfo = new ExtInfo();
            extInfo.setLevel(i);
            extInfo.setRemark("第" + i + "个示例用户");
            user.setExtInfo(extInfo);
            user.setTags(Arrays.asList("tag-" + i, "demo"));
            userDAO.saveEntity(user);
        }
        log.info("示例数据初始化完成");
    }
}
