package com.makd.afinity.data.websocket

enum class WebSocketState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
    SERVER_RESTARTING,
    SERVER_SHUTDOWN
}