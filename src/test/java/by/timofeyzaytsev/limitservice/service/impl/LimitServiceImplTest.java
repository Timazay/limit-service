package by.timofeyzaytsev.limitservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import by.timofeyzaytsev.limitservice.config.property.AppProperties;
import by.timofeyzaytsev.limitservice.config.property.LimitProperties;
import by.timofeyzaytsev.limitservice.dto.request.LimitRequest;
import by.timofeyzaytsev.limitservice.dto.response.LimitResponse;
import by.timofeyzaytsev.limitservice.dto.response.PageResponse;
import by.timofeyzaytsev.limitservice.mapper.LimitMapper;
import by.timofeyzaytsev.limitservice.model.Limit;
import by.timofeyzaytsev.limitservice.model.enums.ExpenseCategory;
import by.timofeyzaytsev.limitservice.repository.LimitRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@DisplayName("LimitServiceImpl: установка лимита и список лимитов")
class LimitServiceImplTest {

    private static final String ACCOUNT_FROM = "0000000123";
    private static final ZoneId ZONE = ZoneId.of("Asia/Almaty");
    private static final BigDecimal DEFAULT_LIMIT_SUM = new BigDecimal("1000.00");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2022-01-15T12:00:00+06:00");

    @Mock
    private LimitRepository limitRepository;

    @Mock
    private LimitMapper limitMapper;

    private LimitServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2022-01-15T06:00:00Z"), ZONE);

        service = new LimitServiceImpl(
            limitRepository,
            limitMapper,
            clock,
            new AppProperties(ZONE, new LimitProperties(DEFAULT_LIMIT_SUM, "USD"), null));
    }

    @Test
    void create_WhenRequestHasNoDate_ShouldSetLimitDatetimeFromClock() {
        saveReturnsArgument();

        service.create(request());

        assertThat(savedLimit().getLimitDatetime()).isEqualTo(NOW);
    }

    @Test
    void create_WhenRequestHasNoDate_ShouldSetCreatedAtFromClock() {
        saveReturnsArgument();

        service.create(request());

        assertThat(savedLimit().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void create_ShouldTakeLimitCurrencyFromConfiguration() {
        saveReturnsArgument();

        service.create(request());

        assertThat(savedLimit().getLimitCurrencyShortname()).isEqualTo("USD");
    }

    @Test
    void create_ShouldCopyClientFieldsFromRequest() {
        saveReturnsArgument();

        service.create(request());

        Limit saved = savedLimit();
        assertThat(saved.getAccountFrom()).isEqualTo(ACCOUNT_FROM);
        assertThat(saved.getExpenseCategory()).isEqualTo(ExpenseCategory.PRODUCT);
        assertThat(saved.getLimitSum()).isEqualByComparingTo(new BigDecimal("2500.00"));
    }

    @Test
    void create_ShouldNotReadExistingLimitsOfClient() {
        saveReturnsArgument();

        service.create(request());

        verify(limitRepository, never()).findByAccountFrom(any(), any());
    }

    @Test
    void create_WhenSaved_ShouldReturnMappedResponse() {
        UUID id = UUID.randomUUID();
        Limit saved = Limit.builder()
            .id(id)
            .accountFrom(ACCOUNT_FROM)
            .expenseCategory(ExpenseCategory.PRODUCT)
            .limitSum(new BigDecimal("2500.00"))
            .limitDatetime(NOW)
            .limitCurrencyShortname("USD")
            .build();
        LimitResponse mapped = new LimitResponse(
            id, ACCOUNT_FROM, ExpenseCategory.PRODUCT, new BigDecimal("2500.00"), NOW, "USD");

        when(limitRepository.save(any(Limit.class))).thenReturn(saved);
        when(limitMapper.toLimitResponse(saved)).thenReturn(mapped);

        assertThat(service.create(request())).isSameAs(mapped);
    }

    @Test
    void findAll_ShouldRequestPageSortedByLimitDateDescending() {
        when(limitRepository.findByAccountFrom(eq(ACCOUNT_FROM), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        when(limitMapper.toResponseList(any())).thenReturn(List.of());

        service.findAll(ACCOUNT_FROM, 0, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(limitRepository).findByAccountFrom(eq(ACCOUNT_FROM), captor.capture());

        Pageable requested = captor.getValue();
        assertThat(requested.getPageNumber()).isZero();
        assertThat(requested.getPageSize()).isEqualTo(10);
        assertThat(requested.getSort().getOrderFor("limitDatetime"))
            .isNotNull()
            .extracting(Sort.Order::getDirection)
            .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void findAll_ShouldReturnTotalCountAlongsideContent() {
        Limit first = Limit.builder().id(UUID.randomUUID()).limitSum(new BigDecimal("1000.00")).build();
        Limit second = Limit.builder().id(UUID.randomUUID()).limitSum(new BigDecimal("2000.00")).build();

        when(limitRepository.findByAccountFrom(eq(ACCOUNT_FROM), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 2), 42));
        when(limitMapper.toResponseList(any())).thenReturn(List.of());

        PageResponse<LimitResponse> response = service.findAll(ACCOUNT_FROM, 0, 2);

        assertThat(response.total()).isEqualTo(42);
    }

    @Test
    void findAll_ShouldReturnMappedContentOfRequestedPage() {
        Limit first = Limit.builder().id(UUID.randomUUID()).limitSum(new BigDecimal("1000.00")).build();
        LimitResponse mapped = new LimitResponse(first.getId(), ACCOUNT_FROM, ExpenseCategory.PRODUCT,
            first.getLimitSum(), NOW, "USD");

        when(limitRepository.findByAccountFrom(eq(ACCOUNT_FROM), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(first), PageRequest.of(0, 1), 1));
        when(limitMapper.toResponseList(any())).thenReturn(List.of(mapped));

        PageResponse<LimitResponse> response = service.findAll(ACCOUNT_FROM, 0, 1);

        assertThat(response.content()).containsExactly(mapped);
    }

    private void saveReturnsArgument() {
        when(limitRepository.save(any(Limit.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Limit savedLimit() {
        ArgumentCaptor<Limit> captor = ArgumentCaptor.forClass(Limit.class);
        verify(limitRepository).save(captor.capture());

        return captor.getValue();
    }

    private static LimitRequest request() {
        return new LimitRequest(
            ACCOUNT_FROM, ExpenseCategory.PRODUCT, new BigDecimal("2500.00"));
    }
}