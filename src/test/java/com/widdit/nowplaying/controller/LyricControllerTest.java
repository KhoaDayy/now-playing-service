package com.widdit.nowplaying.controller;

import com.widdit.nowplaying.entity.Lyric;
import com.widdit.nowplaying.service.LyricService;
import com.widdit.nowplaying.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LyricControllerTest {
    @Test
    void bindsOffsetParameterAndRetainsDefaultRoute() throws Exception {
        LyricService service = mock(LyricService.class);
        when(service.getLyric(1500)).thenReturn(Lyric.builder().lrc("[00:02.50]Line").build());
        when(service.getLyric((Integer) null)).thenReturn(Lyric.builder().lrc("[00:01.00]Line").build());
        LyricController controller = new LyricController();
        ReflectionTestUtils.setField(controller, "lyricService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/lyric").param("offsetMs", "1500"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lrc").value("[00:02.50]Line"));
        mvc.perform(get("/api/lyric"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lrc").value("[00:01.00]Line"));
        mvc.perform(get("/api/lyric").param("offsetMs", "abc")).andExpect(status().isBadRequest());
        verify(service).getLyric(1500);
        verify(service).getLyric((Integer) null);
        verifyNoMoreInteractions(service);
    }
}
