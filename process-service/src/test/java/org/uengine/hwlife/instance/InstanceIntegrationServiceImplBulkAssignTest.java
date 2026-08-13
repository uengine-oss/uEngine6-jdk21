package org.uengine.hwlife.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.uengine.five.repository.WorklistRepository;
import org.uengine.five.service.InstanceServiceImpl;
import org.uengine.hwlife.esbclient.client.EsbClient;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.support.EsbRequestBodyAdvice;
import org.uengine.hwlife.iam.ExternalIAMService;
import org.uengine.hwlife.instance.BulkAssignItemService.BulkAssignItemException;
import org.uengine.hwlife.instance.dto.BulkAssignRequest;
import org.uengine.hwlife.instance.dto.BulkAssignRequestItem;
import org.uengine.hwlife.instance.dto.BulkAssignResponse;

class InstanceIntegrationServiceImplBulkAssignTest {

  private BulkAssignItemService itemService;
  private InstanceIntegrationServiceImpl service;

  @BeforeEach
  void setUp() {
    itemService = mock(BulkAssignItemService.class);
    service = new InstanceIntegrationServiceImpl(
        mock(InstanceServiceImpl.class),
        mock(WorklistRepository.class),
        mock(EsbClient.class),
        itemService);
    bindHeader("admin");
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void assignsFiveItemsThroughOneBulkRequest() throws Exception {
    BulkAssignRequest request = request(5, "kim");

    BulkAssignResponse response = service.assignBulk(request);

    assertEquals(5, response.getSucsCont());
    assertEquals(0, response.getFailCont());
    assertEquals(List.of(), response.getFailList());
    verify(itemService, times(5)).assign(any(), eq("kim"), any());
  }

  @Test
  void preservesSuccessfulItemsWhenOneItemFails() throws Exception {
    BulkAssignRequest request = request(5, "kim");
    BulkAssignRequestItem failing = request.getBswrList().get(2);
    doThrow(new BulkAssignItemException("LBM070012", null))
        .when(itemService).assign(eq(failing), eq("kim"), any());

    BulkAssignResponse response = service.assignBulk(request);

    assertEquals(4, response.getSucsCont());
    assertEquals(1, response.getFailCont());
    assertEquals(failing.getFncgBpmTaskLstId(), response.getFailList().get(0).getFncgBpmTaskLstId());
    assertEquals("LBM070012", response.getFailList().get(0).getPrcsRsltCntn());
  }

  @Test
  void rejectsDuplicateTaskIdWithoutCallingItemServiceTwice() throws Exception {
    BulkAssignRequest request = request(2, "kim");
    request.getBswrList().get(1).setFncgBpmTaskLstId(request.getBswrList().get(0).getFncgBpmTaskLstId());

    BulkAssignResponse response = service.assignBulk(request);

    assertEquals(1, response.getSucsCont());
    assertEquals(1, response.getFailCont());
    assertEquals("LBM070007", response.getFailList().get(0).getPrcsRsltCntn());
    verify(itemService, times(1)).assign(any(), eq("kim"), any());
  }

  @Test
  void returnsStableCodesForMissingRequestListAndHeader() throws Exception {
    BulkAssignResponse missingRequest = service.assignBulk(null);
    assertEquals("LBM070001", missingRequest.getFailList().get(0).getPrcsRsltCntn());

    BulkAssignRequest emptyList = new BulkAssignRequest();
    emptyList.setBswrList(List.of());
    assertEquals("LBM070002", service.assignBulk(emptyList).getFailList().get(0).getPrcsRsltCntn());

    RequestContextHolder.resetRequestAttributes();
    BulkAssignResponse missingHeader = service.assignBulk(request(1, "kim"));
    assertEquals("LBM070003", missingHeader.getFailList().get(0).getPrcsRsltCntn());
  }

  @Test
  void returnsStableCodesForInvalidItemFields() throws Exception {
    BulkAssignRequest missingTask = request(1, "kim");
    missingTask.getBswrList().get(0).setFncgBpmTaskLstId(" ");
    assertEquals("LBM070006",
        service.assignBulk(missingTask).getFailList().get(0).getPrcsRsltCntn());

    BulkAssignRequest invalidTask = request(1, "kim");
    invalidTask.getBswrList().get(0).setFncgBpmTaskLstId("not-a-number");
    assertEquals("LBM070008",
        service.assignBulk(invalidTask).getFailList().get(0).getPrcsRsltCntn());

    BulkAssignRequest missingHandler = request(1, " ");
    assertEquals("LBM070004",
        service.assignBulk(missingHandler).getFailList().get(0).getPrcsRsltCntn());
  }

  @Test
  void returnsCodeWhenHandlerCannotBeResolved() throws Exception {
    ExternalIAMService iamService = mock(ExternalIAMService.class);
    when(iamService.getUser("unknown")).thenReturn(null);

    try (MockedStatic<ExternalIAMService> externalIam = mockStatic(ExternalIAMService.class)) {
      externalIam.when(ExternalIAMService::getDefault).thenReturn(iamService);

      BulkAssignResponse response = service.assignBulk(request(1, "unknown"));

      assertEquals("LBM070005", response.getFailList().get(0).getPrcsRsltCntn());
      verify(itemService, times(0)).assign(any(), any(), any());
    }
  }

  @Test
  void propagatesBusinessAndUnexpectedAssignmentCodes() throws Exception {
    BulkAssignRequest businessFailure = request(1, "kim");
    doThrow(new BulkAssignItemException("LBM070019", null))
        .when(itemService).assign(any(), eq("kim"), any());
    assertEquals("LBM070019",
        service.assignBulk(businessFailure).getFailList().get(0).getPrcsRsltCntn());

    BulkAssignRequest unexpectedFailure = request(1, "lee");
    doThrow(new RuntimeException("unexpected"))
        .when(itemService).assign(any(), eq("lee"), any());
    assertEquals("LBM070020",
        service.assignBulk(unexpectedFailure).getFailList().get(0).getPrcsRsltCntn());
  }

  private static BulkAssignRequest request(int count, String handler) {
    List<BulkAssignRequestItem> items = new ArrayList<>();
    for (int index = 1; index <= count; index++) {
      BulkAssignRequestItem item = new BulkAssignRequestItem();
      item.setFncgBpmTaskLstId(String.valueOf(100 + index));
      item.setFncgBpmPcesIntcId(String.valueOf(200 + index));
      item.setHndrEmnb(handler);
      items.add(item);
    }
    BulkAssignRequest request = new BulkAssignRequest();
    request.setBswrList(items);
    return request;
  }

  private static void bindHeader(String employeeNo) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    ServletRequestAttributes attributes = new ServletRequestAttributes(request);
    RequestContextHolder.setRequestAttributes(attributes);
    EsbCommonHeader header = new EsbCommonHeader();
    header.setEmnb(employeeNo);
    attributes.setAttribute(EsbRequestBodyAdvice.HEADER_ATTR, header, RequestAttributes.SCOPE_REQUEST);
  }
}
