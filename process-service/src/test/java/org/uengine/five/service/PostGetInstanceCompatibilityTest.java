package org.uengine.five.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PostGetInstanceCompatibilityTest {
    InstanceServiceImpl service;
    MockMvc mvc;
    @BeforeEach void setup() throws Exception {
        service = spy(new InstanceServiceImpl());
        doNothing().when(service).deleteInstance(anyString());
        doNothing().when(service).deleteTest(any(), any());
        doReturn(null).when(service).putRoleMapping(anyString(), anyString(), any());
        doReturn(null).when(service).setRoleMapping(anyString(), anyString(), any());
        mvc = MockMvcBuilders.standaloneSetup(service).build();
    }
    @Test void postDeleteUsesOriginalInstanceDeletion() throws Exception {
        mvc.perform(post("/instance/42/delete")).andExpect(status().isOk());
        verify(service).deleteInstance("42");
    }
    @Test void partialUpdateDoesNotUseReplacingPost() throws Exception {
        mvc.perform(post("/instance/42/role-mapping/team").param("action", "update")
                .contentType("application/json").content("{\"scope\":\"new\"}"))
                .andExpect(status().isOk()).andExpect(handler().methodName("updateRoleMappingPost"));
        verify(service).putRoleMapping(eq("42"), eq("team"), any());
        verify(service, never()).setRoleMapping(anyString(), anyString(), any());
    }
    @Test void originalRolePostKeepsReplacingSemantics() throws Exception {
        mvc.perform(post("/instance/42/role-mapping/team").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(handler().methodName("setRoleMapping"));
    }
    @Test void testAndRecordDeletionKeepBody() throws Exception {
        for (String path : new String[]{"/test/folder/process", "/test/folder/process/record"}) {
            mvc.perform(post(path).param("action", "delete").contentType("application/json")
                    .content("{\"tracingTag\":\"Task1\",\"idx\":2}"))
                    .andExpect(status().isOk()).andExpect(handler().methodName("deleteTestPost"));
        }
        verify(service, times(2)).deleteTest(any(), argThat(body -> Integer.valueOf(2).equals(body.get("idx"))));
    }
    @Test void getCannotDeleteInstance() throws Exception {
        mvc.perform(get("/instance/42/delete")).andExpect(status().isMethodNotAllowed());
        verify(service, never()).deleteInstance(anyString());
    }
}
