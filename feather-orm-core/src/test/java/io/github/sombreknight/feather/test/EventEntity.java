package io.github.sombreknight.feather.test;

import io.github.sombreknight.feather.annotation.Table;
import io.github.sombreknight.feather.core.BaseEntity;
import io.github.sombreknight.feather.util.FeatherDate;

/**
 * 日期实体（FeatherDate DB 往返测试用）
 *
 * @author sombreknight
 */
@Table("tb_event")
public class EventEntity extends BaseEntity<Long> {

    private FeatherDate eventTime;

    public FeatherDate getEventTime() {
        return eventTime;
    }

    public void setEventTime(FeatherDate eventTime) {
        this.eventTime = eventTime;
    }
}
