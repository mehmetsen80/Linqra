class KnowledgeHubWebSocketService {
    constructor() {
        this.ws = null;
        this.connected = false;
        this.connectionStatus = 'disconnected';
        this.wsUrl = import.meta.env.VITE_WS_URL || 'wss://localhost:7777/ws-linqra';
        this.subscribers = new Set();
        this.subscriptionId = null;
    }

    connect() {
        if (this.connected || (this.ws && this.ws.readyState === WebSocket.OPEN)) {
            return;
        }

        try {
            console.log(`Connecting to WebSocket for knowledge hub: ${this.wsUrl}`);
            this.connectionStatus = 'connecting';
            this.ws = new WebSocket(this.wsUrl);
            this.setupWebSocket();
        } catch (error) {
            console.error('WebSocket connection error:', error);
            this.connectionStatus = 'error';
        }
    }

    setupWebSocket() {
        this.ws.onopen = () => {
            console.log('Knowledge Hub WebSocket Connected');
            this.connectionStatus = 'connected';
            
            // Send STOMP CONNECT frame
            setTimeout(() => {
                if (this.ws.readyState === WebSocket.OPEN) {
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
                console.log('Knowledge Hub STOMP Connected');
                this.connected = true;
                
                // Subscribe to document status updates via /topic/execution
                const subscribeFrame = 'SUBSCRIBE\n' +
                    'id:doc-status-sub-0\n' +
                    'destination:/topic/execution\n' +
                    '\n\0';
                this.ws.send(subscribeFrame);
                console.log('Subscribed to document status updates');
            } else if (frame.command === 'MESSAGE') {
                try {
                    const payload = JSON.parse(frame.body);
                    
                    // Only process document status updates (filter by type)
                    if (payload.type === 'DOCUMENT_STATUS_UPDATE') {
                        console.log('Received document status update:', payload);
                        this.notifySubscribers(payload);
                    }
                } catch (error) {
                    console.error('Error parsing document status message:', error);
                }
            } else if (frame.command === 'RECEIPT') {
                console.log('Knowledge hub command acknowledged');
            } else if (frame.command === 'ERROR') {
                console.error('WebSocket error:', frame.body);
            }
        };

        this.ws.onerror = (error) => {
            console.error('WebSocket Error:', error);
            this.connected = false;
            this.connectionStatus = 'error';
        };

        this.ws.onclose = () => {
            console.log('Knowledge Hub WebSocket Closed');
            this.connected = false;
            this.connectionStatus = 'disconnected';
        };
    }

    parseStompFrame(data) {
        const lines = data.split('\n');
        const command = lines[0];
        const headers = {};
        let body = '';
        let i = 1;

        while (i < lines.length && lines[i]) {
            const colonIndex = lines[i].indexOf(':');
            if (colonIndex > 0) {
                const key = lines[i].substring(0, colonIndex);
                const value = lines[i].substring(colonIndex + 1);
                headers[key] = value;
            }
            i++;
        }

        i++;
        body = lines.slice(i).join('\n').replace(/\0$/, '');

        return { command, headers, body };
    }

    subscribe(callback) {
        this.subscribers.add(callback);
        return () => {
            this.subscribers.delete(callback);
        };
    }

    notifySubscribers(data) {
        this.subscribers.forEach(callback => {
            try {
                callback(data);
            } catch (error) {
                console.error('Error in document status update callback:', error);
            }
        });
    }

    sendDocumentProcessingCommand(documentId, status, teamId) {
        if (!this.connected || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
            console.warn('WebSocket not connected, attempting to connect...');
            this.connect();
            // Wait a bit for connection
            setTimeout(() => {
                this.sendDocumentProcessingCommand(documentId, status, teamId);
            }, 500);
            return;
        }

        try {
            const command = JSON.stringify({
                documentId,
                status,
                teamId
            });

            const sendFrame = 'SEND\n' +
                'destination:/app/document-processing\n' +
                'content-type:application/json\n' +
                '\n' +
                command +
                '\0';

            this.ws.send(sendFrame);
            console.log('Sent document processing command:', { documentId, status, teamId });
        } catch (error) {
            console.error('Error sending document processing command:', error);
        }
    }

    sendMetadataExtractionCommand(documentId, status, teamId) {
        if (!this.connected || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
            console.warn('WebSocket not connected, attempting to connect...');
            this.connect();
            // Wait a bit for connection
            setTimeout(() => {
                this.sendMetadataExtractionCommand(documentId, status, teamId);
            }, 500);
            return;
        }

        try {
            const command = JSON.stringify({
                documentId,
                status,
                teamId
            });

            const sendFrame = 'SEND\n' +
                'destination:/app/metadata-extraction\n' +
                'content-type:application/json\n' +
                '\n' +
                command +
                '\0';

            this.ws.send(sendFrame);
            console.log('Sent metadata extraction command:', { documentId, status, teamId });
        } catch (error) {
            console.error('Error sending metadata extraction command:', error);
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
        }
    }
}

export const knowledgeHubWebSocketService = new KnowledgeHubWebSocketService();

