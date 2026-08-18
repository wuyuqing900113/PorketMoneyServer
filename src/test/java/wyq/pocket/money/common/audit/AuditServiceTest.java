package wyq.pocket.money.common.audit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import wyq.pocket.money.common.audit.mapper.AuditLogMapper;

/**
 * AuditService 单元测试（T3）：字段落库映射、写入失败不阻断业务
 * （M1 设计 §9.1）。REQUIRES_NEW 事务语义由集成测试覆盖。
 */
class AuditServiceTest {

    private final AuditLogMapper mapper = mock(AuditLogMapper.class);

    private final AuditService service = new AuditService(mapper);

    @Test
    void recordShouldPersistEntryFields() {
        AuditEntry entry = new AuditEntry(7L, AuditAction.LOGIN_SUCCESS, "USER", "7", "{\"a\":1}");

        service.record(entry);

        verify(mapper).insert(eq(7L), eq("LOGIN_SUCCESS"), eq("USER"), eq("7"),
                eq("{\"a\":1}"), isNull(), isNull());
    }

    @Test
    void anonymousEntryShouldAllowNullUserIdAndTarget() {
        AuditEntry entry = new AuditEntry(null, AuditAction.LOGIN_FAILURE, null, null, null);

        service.record(entry);

        verify(mapper).insert(isNull(), eq("LOGIN_FAILURE"), isNull(), isNull(),
                isNull(), isNull(), isNull());
    }

    @Test
    void insertFailureShouldNotPropagateToCaller() {
        when(mapper.insert(any(), anyString(), any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("db down"));
        AuditEntry entry = new AuditEntry(9L, AuditAction.LOGOUT, "TOKEN", "9", null);

        assertThatCode(() -> service.record(entry)).doesNotThrowAnyException();
    }
}
