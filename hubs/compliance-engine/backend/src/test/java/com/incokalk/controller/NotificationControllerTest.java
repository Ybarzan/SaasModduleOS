package com.incokalk.controller;

import com.incokalk.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest extends ControllerTestBase {

    @MockBean
    private NotificationService notificationService;

    @Test
    @DisplayName("GET /v1/notifications/unread-count → 401 sans token")
    void unreadCountWithoutAuth() throws Exception {
        mockMvc.perform(get("/v1/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /v1/notifications/unread-count → 200 avec token valide")
    void unreadCountWithAuth() throws Exception {
        when(notificationService.getUnreadCount(any(), any())).thenReturn(5);

        mockMvc.perform(get("/v1/notifications/unread-count")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }
}
