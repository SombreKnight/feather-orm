package io.github.sombreknight.feather.type;

import io.github.sombreknight.feather.annotation.Table;
import io.github.sombreknight.feather.core.BaseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * TemporalTypeHandlerTest 测试实体。
 */
@Table("tb_temporal")
public class TemporalEntity extends BaseEntity<Long> {

    private LocalDate dDate;
    private LocalDateTime dDateTime;
    private Instant dInstant;
    private OffsetDateTime dOffset;

    public LocalDate getDDate() {
        return dDate;
    }

    public void setDDate(LocalDate dDate) {
        this.dDate = dDate;
    }

    public LocalDateTime getDDateTime() {
        return dDateTime;
    }

    public void setDDateTime(LocalDateTime dDateTime) {
        this.dDateTime = dDateTime;
    }

    public Instant getDInstant() {
        return dInstant;
    }

    public void setDInstant(Instant dInstant) {
        this.dInstant = dInstant;
    }

    public OffsetDateTime getDOffset() {
        return dOffset;
    }

    public void setDOffset(OffsetDateTime dOffset) {
        this.dOffset = dOffset;
    }
}
