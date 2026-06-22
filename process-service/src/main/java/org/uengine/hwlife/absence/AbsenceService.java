package org.uengine.hwlife.absence;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.uengine.hwlife.absence.entity.AbsenceEntity;

/**
 * 한화생명 융자차세대 - 부재자/대결자 설정 REST API.
 *
 * <p>구현: {@link AbsenceServiceImpl}.</p>
 */
public interface AbsenceService {

    @RequestMapping(value = "/absences", method = RequestMethod.POST)
    AbsenceEntity register(@RequestBody AbsenceEntity body) throws Exception;

    @RequestMapping(value = "/absences/user/{userId}", method = RequestMethod.GET)
    List<AbsenceEntity> findHistory(@PathVariable("userId") String userId) throws Exception;

    @RequestMapping(value = "/absences/{abseId}/release", method = RequestMethod.POST)
    AbsenceEntity release(@PathVariable("abseId") Long abseId) throws Exception;
}
