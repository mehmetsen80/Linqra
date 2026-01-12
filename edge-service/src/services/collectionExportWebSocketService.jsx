class CollectionExportWebSocketService {
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
            console.log(`Connecting to WebSocket for collection export: ${this.wsUrl}`);
            this.connectionStatus = 'connecting';
            this.ws = new WebSocket(this.wsUrl);
            this.setupWebSocket();
        } catch (error) {
            console.error('Collection export WebSocket connection error:', error);
            this.connectionStatus = 'error';
        }
    }

    setupWebSocket() {
        this.ws.onopen = () => {
            console.log('Collection Export WebSocket Connected');
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
                console.log('Collection Export STOMP Connected');
                this.connected = true;

                // Subscribe to collection export updates
                const subscribeFrame = 'SUBSCRIBE\n' +
                    'id:collection-export-sub-0\n' +
                    'destination:/topic/collection-export\n' +
                    '\n\0';
                this.ws.send(subscribeFrame);
                console.log('Subscribed to collection export updates');
            } else if (frame.command === 'MESSAGE') {
                try {
                    const payload = JSON.parse(frame.body);
                    console.log('📦 Received collection export update:', payload);
                    this.notifySubscribers(payload);
                } catch (error) {
                    console.error('Error parsing collection export message:', error);
                }
            } else if (frame.command === 'RECEIPT') {
                console.log('Collection export command acknowledged');
            } else if (frame.command === 'ERROR') {
                console.error('WebSocket error:', frame.body);
            }
        };

        this.ws.onerror = (error) => {
            console.error('Collection Export WebSocket Error:', error);
            this.connected = false;
            this.connectionStatus = 'error';
        };

        this.ws.onclose = () => {
            console.log('Collection Export WebSocket Closed');
            this.connected = false;
            this.connectionStatus = 'disconnected';

            // Attempt to reconnect after 3 seconds
            setTimeout(() => {
                if (this.connectionStatus === 'disconnected') {
                    console.log('Attempting to reconnect collection export WebSocket...');
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
                console.error('Error in collection export update callback:', error);
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

export const collectionExportWebSocketService = new CollectionExportWebSocketService();

