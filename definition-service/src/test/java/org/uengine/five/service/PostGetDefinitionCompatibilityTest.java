package org.uengine.five.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PostGetDefinitionCompatibilityTest {
    DefinitionServiceImpl service;
    MockMvc mvc;
    @BeforeEach void setup() throws Exception {
        service = spy(new DefinitionServiceImpl());
        doReturn(null).when(service).renameOrMove(any(), any());
        doNothing().when(service).deleteDefinition(any());
        doReturn(null).when(service).createFolder(any(), any());
        doReturn(null).when(service).putRawDefinitionByParam(anyString(), any());
        doReturn(null).when(service).putRawDefinition(anyString(), any());
        doReturn(null).when(service).putRawSystem(anyString(), any());
        doReturn(null).when(service).putRawDefinitionMap(anyString(), any());
        mvc = MockMvcBuilders.standaloneSetup(service).build();
    }
    @Test void moveUsesRenameNotFolderCreation() throws Exception {
        mvc.perform(post("/definition/folder/item.bpmn").param("action", "move")
                .contentType("application/json").content("{\"path\":\"next\"}"))
                .andExpect(status().isOk()).andExpect(handler().methodName("renameOrMovePost"));
        verify(service).renameOrMove(any(), any());
        verify(service, never()).createFolder(any(), any());
    }
    @Test void fileAndSystemDeletionDoNotSaveOrCreate() throws Exception {
        for (String path : new String[]{"/definition/folder/item.bpmn", "/definition/system/test"}) {
            mvc.perform(post(path).param("action", "delete"))
                    .andExpect(status().isOk()).andExpect(handler().methodName("deleteDefinitionPost"));
        }
        verify(service, times(2)).deleteDefinition(any());
        verify(service, never()).putRawSystem(anyString(), any());
        verify(service, never()).createFolder(any(), any());
    }
    @Test void originalFolderPostRemainsSeparate() throws Exception {
        mvc.perform(post("/definition/folder").contentType("application/json")
                .content("{\"name\":\"child\",\"directory\":true}"))
                .andExpect(status().isOk()).andExpect(handler().methodName("createFolder"));
    }
    @Test void rawPostAndPutUseSameSaver() throws Exception {
        mvc.perform(post("/definition/raw").param("defPath", "sample.json")
                .contentType("application/json").content("{\"definition\":\"{}\"}"))
                .andExpect(status().isOk()).andExpect(handler().methodName("putRawDefinitionByParam"));
        mvc.perform(put("/definition/raw").param("defPath", "sample.json")
                .contentType("application/json").content("{\"definition\":\"{}\"}"))
                .andExpect(status().isOk()).andExpect(handler().methodName("putRawDefinition"));
    }
    @Test void mapAndSystemPostKeepSaveHandlers() throws Exception {
        mvc.perform(post("/definition/map").contentType("text/plain").content("{}"))
                .andExpect(status().isOk()).andExpect(handler().methodName("putRawDefinitionMap"));
        mvc.perform(post("/definition/system/test").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(handler().methodName("putRawSystem"));
    }
}
