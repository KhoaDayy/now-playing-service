package com.widdit.nowplaying.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.widdit.nowplaying.entity.Lyric;
import com.widdit.nowplaying.entity.WebSocketMessage;
import com.widdit.nowplaying.service.WebSocketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import javax.websocket.CloseReason;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WebSocketLyricControllerTest {
    private final WebSocketLyricController controller = new WebSocketLyricController();
    private final List<Session> sessions = new ArrayList<>();

    @AfterEach
    void closeSessions() {
        for (Session session : sessions) controller.onClose(session, null);
        WebSocketLyricController.setApplicationContext(null);
    }

    @Test
    void initialAndBroadcastLyricsApplyOffsetsIndependently() throws Exception {
        List<String> delayedMessages = new ArrayList<>();
        List<String> earlyMessages = new ArrayList<>();
        List<String> defaultMessages = new ArrayList<>();
        Session delayed = connect("1500", delayedMessages);
        connect("-500", earlyMessages);
        connect(null, defaultMessages);
        Lyric lyric = new Lyric();
        lyric.setHasLyric(true);
        lyric.setLrc("[00:01.00]Line");
        lyric.setTranslatedLyric("[00:01.00]Translation");
        lyric.setKaraokeLyric("[1000,500]Word(1000,500)");
        WebSocketMessage message = new WebSocketMessage("Lyric", lyric);

        WebSocketLyricController.sendToSession(delayed, message);
        WebSocketLyricController.sendToAllClients(message);

        assertEquals(2, delayedMessages.size());
        for (String text : delayedMessages) {
            JSONObject data = JSON.parseObject(text).getJSONObject("data");
            assertEquals("[00:02.50]Line", data.getString("lrc"));
            assertEquals("[00:02.50]Translation", data.getString("translatedLyric"));
            assertEquals("[2500,500]Word(2500,500)", data.getString("karaokeLyric"));
        }
        assertEquals("[00:00.50]Line", JSON.parseObject(earlyMessages.get(0)).getJSONObject("data").getString("lrc"));
        assertEquals("[00:01.00]Line", JSON.parseObject(defaultMessages.get(0)).getJSONObject("data").getString("lrc"));
        assertSame(lyric, message.getData());
        assertEquals("[00:01.00]Line", lyric.getLrc());
    }

    @Test
    void offsetDoesNotChangePlayerProgressMessages() throws Exception {
        List<String> messages = new ArrayList<>();
        connect("1500", messages);
        WebSocketLyricController.sendToAllClients(new WebSocketMessage("PlayerProgress", Map.of("progress", 10)));
        JSONObject message = JSON.parseObject(messages.get(0));
        assertEquals("PlayerProgress", message.getString("event"));
        assertEquals(10, message.getJSONObject("data").getIntValue("progress"));
    }

    @Test
    void invalidOffsetRejectsConnectionBeforeInitialData() throws Exception {
        int originalCount = WebSocketLyricController.getConnectionCount();
        Session invalid = connect("abc", new ArrayList<>());
        verify(invalid).close(any(CloseReason.class));
        assertEquals(originalCount, WebSocketLyricController.getConnectionCount());
        verify(invalid.getBasicRemote(), never()).sendText(anyString());
    }

    @Test
    void failedLastConnectionDisablesBackgroundLyricFetching() throws Exception {
        Session session = connect(null, new ArrayList<>());
        ApplicationContext context = mock(ApplicationContext.class);
        WebSocketService service = mock(WebSocketService.class);
        when(context.getBean(WebSocketService.class)).thenReturn(service);
        WebSocketLyricController.setApplicationContext(context);
        RemoteEndpoint.Basic remote = session.getBasicRemote();
        doThrow(new IOException("closed connection")).when(remote).sendText(anyString());

        WebSocketLyricController.sendToAllClients(new WebSocketMessage("PlayerProgress", 10));

        assertEquals(0, WebSocketLyricController.getConnectionCount());
        verify(service).updateLyricFetchState(0);
        verify(session).close();
    }

    private Session connect(String offset, List<String> messages) throws Exception {
        Session session = mock(Session.class);
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        when(session.getRequestParameterMap()).thenReturn(offset == null ? Map.of() : Map.of("offsetMs", List.of(offset)));
        when(session.getUserProperties()).thenReturn(new HashMap<>());
        when(session.isOpen()).thenReturn(true);
        when(session.getBasicRemote()).thenReturn(remote);
        doAnswer(invocation -> { messages.add(invocation.getArgument(0)); return null; }).when(remote).sendText(anyString());
        sessions.add(session);
        WebSocketLyricController.setApplicationContext(null);
        controller.onOpen(session);
        return session;
    }
}
