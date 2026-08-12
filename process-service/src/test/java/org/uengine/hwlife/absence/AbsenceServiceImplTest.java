package org.uengine.hwlife.absence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.hwlife.absence.dto.AbsenceHistoryRequest;
import org.uengine.hwlife.absence.dto.AbsenceHistoryResponse;
import org.uengine.hwlife.absence.dto.AbsenceRequest;
import org.uengine.hwlife.absence.dto.AbsenceResponse;
import org.uengine.hwlife.absence.entity.AbsenceEntity;
import org.uengine.hwlife.absence.repository.AbsenceRepository;

class AbsenceServiceImplTest {

    private AbsenceRepository repository;
    private AbsenceServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(AbsenceRepository.class);
        service = new AbsenceServiceImpl(repository);
    }

    @Test
    void returnsFirstAndNextCursorPagesWithoutDuplicates() throws Exception {
        AbsenceEntity newest = absence(103L, "hong", null);
        AbsenceEntity middle = absence(102L, "hong", null);
        AbsenceEntity oldest = absence(101L, "hong", new Date());
        when(repository.findHistoryFirstPage(eq("hong"), any(Pageable.class)))
                .thenReturn(List.of(newest, middle, oldest));
        when(repository.findHistoryPageAfter(eq("hong"), eq(101L), any(Pageable.class)))
                .thenReturn(List.of(oldest));
        when(repository.countByUserId("hong")).thenReturn(3L);

        AbsenceHistoryResponse first = service.searchAbsenceHistory(history("hong", null, 2));
        assertEquals(List.of("103", "102"), first.getAbscList().stream()
                .map(item -> item.getFncgBpmAbstSqno()).toList());
        assertEquals(3, first.getTotCont());
        assertEquals("101", first.getNextKey());

        AbsenceHistoryResponse second = service.searchAbsenceHistory(history("hong", first.getNextKey(), 2));
        assertEquals(List.of("101"), second.getAbscList().stream()
                .map(item -> item.getFncgBpmAbstSqno()).toList());
        assertEquals(3, second.getTotCont());
        assertNull(second.getNextKey());
        assertFalse(second.getAbscList().get(0).getAbscRscsDttm() == null);
    }

    @Test
    void returnsEmptyHistoryWithZeroTotal() throws Exception {
        when(repository.findHistoryFirstPage(eq("nobody"), any(Pageable.class)))
                .thenReturn(List.of());
        when(repository.countByUserId("nobody")).thenReturn(0L);

        AbsenceHistoryResponse response = service.searchAbsenceHistory(history("nobody", null, null));

        assertTrue(response.getAbscList().isEmpty());
        assertEquals(0, response.getTotCont());
        assertNull(response.getNextKey());
    }

    @Test
    void rejectsInvalidCursor() {
        for (String nextKey : List.of("not-a-number", "0", "-1")) {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> service.searchAbsenceHistory(history("hong", nextKey, 20)));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }
        verify(repository, never()).findHistoryFirstPage(any(), any());
        verify(repository, never()).findHistoryPageAfter(any(), any(), any());
    }

    @Test
    void clampsPageSizeToSupportedBounds() throws Exception {
        when(repository.findHistoryFirstPage(eq("hong"), any(Pageable.class)))
                .thenReturn(List.of());
        when(repository.countByUserId("hong")).thenReturn(0L);

        service.searchAbsenceHistory(history("hong", null, 0));
        service.searchAbsenceHistory(history("hong", null, 1000));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, org.mockito.Mockito.times(2))
                .findHistoryFirstPage(eq("hong"), pageable.capture());
        assertEquals(2, pageable.getAllValues().get(0).getPageSize());
        assertEquals(101, pageable.getAllValues().get(1).getPageSize());
    }

    @Test
    void registersValidAbsence() throws Exception {
        AbsenceRequest request = registration("hong", "kim", 1_000L, 2_000L);
        when(repository.findOverlappingActiveWithEnd(eq("hong"), any(), any(), eq(-1L)))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> {
            AbsenceEntity saved = invocation.getArgument(0);
            saved.setAbseId(77L);
            return saved;
        });

        AbsenceResponse response = service.executeAbsence(request);

        assertEquals("77", response.getFncgBpmAbstSqno());
        assertEquals("hong", response.getAbscEmnb());
        assertEquals("kim", response.getAgntEmnb());
    }

    @Test
    void registersOpenEndedAbsenceWithDedicatedOverlapQuery() throws Exception {
        AbsenceRequest request = registration("hong", "kim", 1_000L, 2_000L);
        request.setAbscEndDttm(null);
        when(repository.findOverlappingActiveWithoutEnd(eq("hong"), any(), eq(-1L)))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> {
            AbsenceEntity saved = invocation.getArgument(0);
            saved.setAbseId(78L);
            return saved;
        });

        AbsenceResponse response = service.executeAbsence(request);

        assertEquals("78", response.getFncgBpmAbstSqno());
        verify(repository).findOverlappingActiveWithoutEnd(eq("hong"), any(), eq(-1L));
    }

    @Test
    void rejectsOverlappingActiveAbsence() {
        AbsenceRequest request = registration("hong", "kim", 1_000L, 2_000L);
        when(repository.findOverlappingActiveWithEnd(eq("hong"), any(), any(), eq(-1L)))
                .thenReturn(List.of(absence(9L, "hong", null)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.executeAbsence(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void releasesExistingAbsence() throws Exception {
        AbsenceEntity active = absence(88L, "hong", null);
        when(repository.findById(88L)).thenReturn(java.util.Optional.of(active));
        when(repository.save(active)).thenReturn(active);
        AbsenceRequest request = new AbsenceRequest();
        request.setFncgBpmAbstSqno("88");

        AbsenceResponse response = service.executeAbsence(request);

        assertEquals("88", response.getFncgBpmAbstSqno());
        assertTrue(active.getAbscRscsDttm() != null);
    }

    @Test
    void rejectsAlreadyReleasedAbsence() {
        AbsenceEntity released = absence(88L, "hong", new Date());
        when(repository.findById(88L)).thenReturn(java.util.Optional.of(released));
        AbsenceRequest request = new AbsenceRequest();
        request.setFncgBpmAbstSqno("88");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.executeAbsence(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    private static AbsenceHistoryRequest history(String userId, String nextKey, Integer pageSize) {
        AbsenceHistoryRequest request = new AbsenceHistoryRequest();
        request.setAbscEmnb(userId);
        request.setNextKey(nextKey);
        request.setPageSize(pageSize);
        return request;
    }

    private static AbsenceRequest registration(String userId, String agent, long start, long end) {
        AbsenceRequest request = new AbsenceRequest();
        request.setAbscEmnb(userId);
        request.setAgntEmnb(agent);
        request.setAgntFncgOrgnCode("manager");
        request.setAbscStarDttm(new Date(start));
        request.setAbscEndDttm(new Date(end));
        return request;
    }

    private static AbsenceEntity absence(Long id, String userId, Date releasedAt) {
        AbsenceEntity entity = new AbsenceEntity();
        entity.setAbseId(id);
        entity.setUserId(userId);
        entity.setAgentUserId("kim");
        entity.setAgentGroupCd("manager");
        entity.setAbscStarDttm(new Date(1_000L + id));
        entity.setAbscEndDttm(new Date(2_000L + id));
        entity.setAbscRscsDttm(releasedAt);
        entity.setAbscStupDttm(new Date(500L + id));
        return entity;
    }
}
