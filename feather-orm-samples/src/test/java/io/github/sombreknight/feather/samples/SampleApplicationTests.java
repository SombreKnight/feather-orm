package io.github.sombreknight.feather.samples;

import io.github.sombreknight.feather.samples.dao.UserDAO;
import io.github.sombreknight.feather.samples.domain.OrderStatus;
import io.github.sombreknight.feather.samples.domain.UserDO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 示例应用端到端测试
 *
 * @author sombreknight
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class SampleApplicationTests {

    @Autowired
    private UserDAO userDAO;

    @Test
    public void sampleDataLoaded() {
        // CommandLineRunner 启动时插入 3 条示例数据
        assertTrue(userDAO.count(userDAO.getQueryHelper()) >= 3);
    }

    @Test
    public void crud() {
        UserDO user = new UserDO();
        user.setUserName("端到端测试");
        user.setAge(99);
        user.setStatus(OrderStatus.CANCELLED);
        assertTrue(userDAO.saveDomain(user));
        assertNotNull(user.getId());

        UserDO found = userDAO.findById(user.getId());
        assertNotNull(found);
        assertEquals("端到端测试", found.getUserName());
        assertEquals(OrderStatus.CANCELLED, found.getStatus());
    }
}
