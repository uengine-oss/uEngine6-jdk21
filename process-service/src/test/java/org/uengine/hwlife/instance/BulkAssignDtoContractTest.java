package org.uengine.hwlife.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.uengine.hwlife.instance.dto.BulkAssignRequest;
import org.uengine.hwlife.instance.dto.BulkAssignRequestItem;
import org.uengine.hwlife.instance.dto.BulkAssignResponse;
import org.uengine.hwlife.instance.dto.BulkAssignResponseItem;
import org.uengine.hwlife.search.dto.BulkAssignSearchRequest;
import org.uengine.hwlife.search.dto.BulkAssignSearchResponse;
import org.uengine.hwlife.search.dto.BulkAssignSearchResponseItem;

class BulkAssignDtoContractTest {

  @Test
  void matchesApprovedBulkAssignDtoPropertySet() throws Exception {
    assertProperties(BulkAssignRequest.class, "bswrList");
    assertProperties(BulkAssignRequestItem.class, "fncgBpmPcesIntcId", "fncgBpmTaskLstId", "hndrEmnb");
    assertProperties(BulkAssignResponse.class, "failCont", "failList", "sucsCont");
    assertProperties(BulkAssignResponseItem.class, "fncgBpmPcesIntcId", "fncgBpmTaskLstId", "prcsRsltCntn");
    assertProperties(BulkAssignSearchRequest.class,
        "bpmBswrClsfCode", "bswrDvsnVal", "custId", "endDate", "fncgMneyUsagClsfCode",
        "fncgSuptTrgtDvsnCode", "fncgWndwOrgnCode", "hopeEndDate", "hopeStarDate",
        "loanCntcNo", "loanPcesMgmtNo", "loanSubjDvsnCode", "starDate", "uworNm");
    assertProperties(BulkAssignSearchResponse.class, "bswrList", "totCont");
    assertProperties(BulkAssignSearchResponseItem.class,
        "bpmBswrClsfCode", "bswrDvsnVal", "fncgBpmPcesId", "fncgBpmPcesIntcId",
        "fncgBpmTaskLstId", "uworNm");
  }


  private static void assertProperties(Class<?> type, String... expected) throws Exception {
    Set<String> actual = Arrays.stream(Introspector.getBeanInfo(type).getPropertyDescriptors())
        .map(descriptor -> descriptor.getName())
        .filter(name -> !"class".equals(name))
        .collect(Collectors.toSet());
    assertEquals(Set.of(expected), actual, type.getSimpleName());
  }
}
