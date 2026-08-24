package io.github.sombreknight.feather.support;

import io.github.sombreknight.feather.test.AccountEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 多数据源事务测试 Service：验证指定集群事务管理器（orderTransactionManager）的提交/回滚
 *
 * @author sombreknight
 */
@Service
public class MultiTxService {

    private final OrderAccountDAO orderAccountDAO;

    public MultiTxService(OrderAccountDAO orderAccountDAO) {
        this.orderAccountDAO = orderAccountDAO;
    }

    @Transactional(transactionManager = "orderTransactionManager")
    public void saveAndRollback() {
        orderAccountDAO.saveEntity(newAccount("tx-rollback"));
        throw new IllegalStateException("trigger rollback");
    }

    @Transactional(transactionManager = "orderTransactionManager")
    public AccountEntity saveAndReadOwnWrite() {
        AccountEntity e = newAccount("tx-own-write");
        orderAccountDAO.saveEntity(e);
        // 事务内读己之写：复用同一连接，必然可见
        return orderAccountDAO.findById(e.getId());
    }

    public static AccountEntity newAccount(String name) {
        AccountEntity e = new AccountEntity();
        e.setUserName(name);
        e.setBalance(new java.math.BigDecimal("10.00"));
        e.setStatus(io.github.sombreknight.feather.test.AccountStatus.NORMAL);
        return e;
    }
}
