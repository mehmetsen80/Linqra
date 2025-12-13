class ChatWebSocketService {
    constructor() {
        this.subscribers = new Set();
        this.connectionSubscribers = new Set();
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 10;
        this.reconnectDelay = 2000; // Start with 2 seconds
        this.maxReconnectDelay = 30000; // Max 30 seconds
        this.wsUrl = import.meta.env.VITE_WS_URL || 'wss://localhost:7777/ws-linqra';
        this.ws = null;
        this.connected = false;
        this.connectionStatus = 'disconnected';
        this.conversationSubscriptions = new Map(); // Map of conversationId -> Set of callbacks
    }

    setConnectionStatus(status) {
        this.connectionStatus = status;
        this.connectionSubscribers.forEach(callback => callback(status));
    }

    onConnectionChange(callback) {
        this.connectionSubscribers.add(callback);
        callback(this.connectionStatus);
    }

    offConnectionChange(callback) {
        this.connectionSubscribers.delete(callback);
    }

    connect() {
        if (this.connected || (this.ws && this.ws.readyState === WebSocket.OPEN)) {
            return;
        }

        try {
            console.log(`💬 Attempting to connect to chat WebSocket: ${this.wsUrl}`);
            this.setConnectionStatus('connecting');
            
            this.ws = new WebSocket(this.wsUrl);
            this.setupWebSocket();
        } catch (error) {
            console.error('💬 WebSocket connection error:', error);
            this.handleReconnect();
        }
    }

    setupWebSocket() {
        this.ws.onopen = () => {
            console.log('💬 Chat WebSocket Connected');
            this.reconnectAttempts = 0;
            
            // Send STOMP CONNECT frame
            setTimeout(() => {
                if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                    const connectFrame = 'CONNECT\n' +
                        'accept-version:1.1,1.0\n' +
                        'heart-beat:4000,4000\n' +
                        '\n\0';
                    this.ws.send(connectFrame);
                }
            }, 100);
        };

        this.ws.onmessage = (event) => {
            const frame = this.parseStompFrame(event.data);
            
            if (frame.command === 'CONNECTED') {
                console.log('💬 Chat STOMP Connected');
                this.connected = true;
                this.setConnectionStatus('connected');
                
                // Subscribe to chat updates
                const subscribeFrame = 'SUBSCRIBE\n' +
                    'id:chat-sub-0\n' +
                    'destination:/topic/chat\n' +
                    '\n\0';
                this.ws.send(subscribeFrame);
                console.log('💬 Subscribed to chat updates');
            }
            else if (frame.command === 'MESSAGE') {
                try {
                    const payload = JSON.parse(frame.body);
                    // console.log('💬 Received chat update:', payload);
                    
                    // Notify all subscribers
                    this.notifySubscribers(payload);
                    
                    // Notify conversation-specific subscribers
                    if (payload.conversationId) {
                        const callbacks = this.conversationSubscriptions.get(payload.conversationId);
                        if (callbacks) {
                            callbacks.forEach(callback => {
                                try {
                                    callback(payload);
                                } catch (error) {
                                    console.error('💬 Error in conversation callback:', error);
                                }
                            });
                        }
                    }
                } catch (error) {
                    console.error('💬 Error parsing chat message payload:', error);
                }
            }
            else if (frame.command === 'RECEIPT') {
                console.log('💬 Chat command acknowledged');
            }
        };

        this.ws.onerror = (error) => {
            console.error('💬 Chat WebSocket Error:', error);
            this.connected = false;
            this.setConnectionStatus('error');
            this.handleReconnect();
        };

        this.ws.onclose = () => {
            console.log('💬 Chat WebSocket Closed');
            this.connected = false;
            this.setConnectionStatus('disconnected');
            this.handleReconnect();
        };
    }

    parseStompFrame(data) {
        const lines = data.split('\n');
        const command = lines[0];
        const headers = {};
        let body = '';
        let i = 1;

        // Parse headers
        while (i < lines.length && lines[i]) {
            const colonIndex = lines[i].indexOf(':');
            if (colonIndex > 0) {
                const key = lines[i].substring(0, colonIndex);
                const value = lines[i].substring(colonIndex + 1);
                headers[key] = value;
            }
            i++;
        }

        // Skip empty line after headers
        i++;

        // Get body - collect all remaining lines
        body = lines.slice(i).join('\n');

        // Remove null terminator and trailing newlines
        body = body.replace(/\0$/, '');

        return { command, headers, body };
    }

    handleReconnect() {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            const delay = Math.min(
                this.reconnectDelay * Math.pow(1.5, this.reconnectAttempts),
                this.maxReconnectDelay
            );
            
            setTimeout(() => {
                this.reconnectAttempts++;
                this.connect();
            }, delay);
            
            this.setConnectionStatus('reconnecting');
        } else {
            this.setConnectionStatus('failed');
        }
    }

    subscribe(callback) {
        this.subscribers.add(callback);
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            this.connect();
        }
        return () => this.subscribers.delete(callback);
    }

    subscribeToConversation(conversationId, callback) {
        if (!this.conversationSubscriptions.has(conversationId)) {
            this.conversationSubscriptions.set(conversationId, new Set());
        }
        this.conversationSubscriptions.get(conversationId).add(callback);
        
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            this.connect();
        }
        
        return () => {
            const callbacks = this.conversationSubscriptions.get(conversationId);
            if (callbacks) {
                callbacks.delete(callback);
                if (callbacks.size === 0) {
                    this.conversationSubscriptions.delete(conversationId);
                }
            }
        };
    }

    notifySubscribers(data) {
        this.subscribers.forEach(callback => {
            try {
                callback(data);
            } catch (error) {
                console.error('💬 Error in chat update callback:', error);
            }
        });
    }

    sendCancelRequest(conversationId) {
        if (!this.connected || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
            console.warn('💬 WebSocket not connected, cannot send cancel request');
            return;
        }

        try {
            const command = JSON.stringify({
                conversationId,
                action: 'CANCEL'
            });

            const sendFrame = 'SEND\n' +
                'destination:/app/chat-cancel\n' +
                'content-type:application/json\n' +
                '\n' +
                command +
                '\0';

            this.ws.send(sendFrame);
            console.log('💬 Sent cancel request for conversation:', conversationId);
        } catch (error) {
            console.error('💬 Error sending cancel request:', error);
        }
    }

    disconnect() {
        if (this.ws) {
            if (this.connected) {
                const disconnectFrame = 'DISCONNECT\n\n\0';
                this.ws.send(disconnectFrame);
            }
            this.ws.close();
            this.connected = false;
            this.connectionStatus = 'disconnected';
            this.subscribers.clear();
            this.conversationSubscriptions.clear();
        }
    }
}

export const chatWebSocketService = new ChatWebSocketService();

