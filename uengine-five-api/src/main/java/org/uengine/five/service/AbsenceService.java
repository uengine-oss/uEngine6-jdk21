package org.uengine.five.service;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.uengine.five.dto.AbsenceCreateRequest;
import org.uengine.five.dto.AbsenceResponse;

/**
 * 부재자/대결자 설정 REST API.
 */
@FeignClient(name = "bpm", url = "http://process-service:9094")
public interface AbsenceService {

    @RequestMapping(value = "/absences", method = RequestMethod.POST)
    public AbsenceResponse register(@RequestBody AbsenceCreateRequest body) throws Exception;

    @RequestMapping(value = "/absences/user/{userId}", method = RequestMethod.GET)
    public List<AbsenceResponse> findHistory(@PathVariable("userId") String userId) throws Exception;

    @RequestMapping(value = "/absences/{id}/release", method = RequestMethod.POST)
    public AbsenceResponse release(@PathVariable("id") Long id) throws Exception;
}
