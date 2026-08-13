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

  @Test
  void usesApprovedLbm07FailureCodes() {
    assertEquals(List.of(
        "LBM070001", "LBM070002", "LBM070003", "LBM070004", "LBM070005",
        "LBM070006", "LBM070007", "LBM070008", "LBM070009", "LBM070010",
        "LBM070011", "LBM070012", "LBM070013", "LBM070019", "LBM070020"),
        List.of(
            BulkAssignResultCode.INVALID_REQUEST,
            BulkAssignResultCode.EMPTY_WORK_LIST,
            BulkAssignResultCode.MISSING_ACTOR,
            BulkAssignResultCode.MISSING_HANDLER,
            BulkAssignResultCode.HANDLER_NOT_FOUND,
            BulkAssignResultCode.MISSING_TASK_ID,
            BulkAssignResultCode.DUPLICATE_TASK,
            BulkAssignResultCode.INVALID_TASK_ID,
            BulkAssignResultCode.WORKITEM_NOT_FOUND,
            BulkAssignResultCode.INSTANCE_MISMATCH,
            BulkAssignResultCode.WORKITEM_NOT_NEW,
            BulkAssignResultCode.ALREADY_ASSIGNED,
            BulkAssignResultCode.NOT_BULK_ASSIGNABLE,
            BulkAssignResultCode.CLAIM_REJECTED,
            BulkAssignResultCode.ASSIGNMENT_FAILED));
  }

  private static void assertProperties(Class<?> type, String... expected) throws Exception {
    Set<String> actual = Arrays.stream(Introspector.getBeanInfo(type).getPropertyDescriptors())
        .map(descriptor -> descriptor.getName())
        .filter(name -> !"class".equals(name))
        .collect(Collectors.toSet());
    assertEquals(Set.of(expected), actual, type.getSimpleName());
  }
}
