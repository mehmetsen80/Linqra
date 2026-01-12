class KnowledgeHubGraphWebSocketService {
    constructor() {
        this.ws = null;
        this.connected = false;
        this.connectionStatus = 'disconnected';
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        this.wsUrl = import.meta.env.VITE_WS_URL || `${protocol}//${window.location.host}/ws-linqra`;
        this.subscribers = new Set();
        this.subscriptionId = null;
    }

    connect() {
        if (this.connected || (this.ws && this.ws.readyState === WebSocket.OPEN)) {
            return;
        }

        try {
            console.log(`Connecting to WebSocket for graph extraction: ${this.wsUrl}`);
            this.connectionStatus = 'connecting';
            this.ws = new WebSocket(this.wsUrl);
            this.setupWebSocket();
        } catch (error) {
            console.error('Graph extraction WebSocket connection error:', error);
            this.connectionStatus = 'error';
        }
    }

    setupWebSocket() {
        this.ws.onopen = () => {
            console.log('Graph Extraction WebSocket Connected');
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
                console.log('Graph Extraction STOMP Connected');
                this.connected = true;

                // Subscribe to graph extraction updates
                const subscribeFrame = 'SUBSCRIBE\n' +
                    'id:graph-extraction-sub-0\n' +
                    'destination:/topic/graph-extraction\n' +
                    '\n\0';
                this.ws.send(subscribeFrame);
                console.log('Subscribed to graph extraction updates');
            } else if (frame.command === 'MESSAGE') {
                try {
                    const payload = JSON.parse(frame.body);
                    console.log('Received graph extraction update:', payload);
                    this.notifySubscribers(payload);
                } catch (error) {
                    console.error('Error parsing graph extraction message:', error);
                }
            } else if (frame.command === 'RECEIPT') {
                console.log('Graph extraction command acknowledged');
            } else if (frame.command === 'ERROR') {
                console.error('WebSocket error:', frame.body);
            }
        };

        this.ws.onerror = (error) => {
            console.error('Graph Extraction WebSocket Error:', error);
            this.connected = false;
            this.connectionStatus = 'error';
        };

        this.ws.onclose = () => {
            console.log('Graph Extraction WebSocket Closed');
            this.connected = false;
            this.connectionStatus = 'disconnected';

            // Attempt to reconnect after 3 seconds
            setTimeout(() => {
                if (this.connectionStatus === 'disconnected') {
                    console.log('Attempting to reconnect graph extraction WebSocket...');
                    this.connect();
                }
            }, 3000);
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
                console.error('Error in graph extraction update callback:', error);
            }
        });
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

export const knowledgeHubGraphWebSocketService = new KnowledgeHubGraphWebSocketService();

