package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.annotation.Table;
import io.github.sombreknight.feather.core.BaseEntity;

import java.math.BigDecimal;

/**
 * 测试实体（starter 测试用）
 *
 * @author sombreknight
 */
@Table("tb_account")
public class AccountEntity extends BaseEntity {

    private String userName;

    private BigDecimal balance;

    private AccountStatus status;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }
}
