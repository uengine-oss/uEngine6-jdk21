package org.uengine.hwlife.absence;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.uengine.hwlife.absence.dto.AbsenceHistoryRequest;
import org.uengine.hwlife.absence.dto.AbsenceHistoryResponse;
import org.uengine.hwlife.absence.dto.AbsenceRequest;
import org.uengine.hwlife.absence.dto.AbsenceResponse;

/**
 * 한화생명 융자차세대 - 부재자/대결자 설정 REST API.
 *
 * <p>구현: {@link AbsenceServiceImpl}.</p>
 */
public interface AbsenceService {

    @RequestMapping(value = "/absences", method = RequestMethod.POST)
    AbsenceResponse executeAbsence(@RequestBody AbsenceRequest request) throws Exception;

    @RequestMapping(value = "/absences/history", method = RequestMethod.POST)
    AbsenceHistoryResponse searchAbsenceHistory(@RequestBody AbsenceHistoryRequest request) throws Exception;
}
