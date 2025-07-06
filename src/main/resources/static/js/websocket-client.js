// WebSocket client implementation
const webSocketClient = (function() {
    let stompClient = null;
    let isConnected = false;
    let reconnectAttempts = 0;
    const maxReconnectAttempts = 5;
    const reconnectDelay = 3000;
    const subscriptions = {};
    const connectCallbacks = [];
    const errorCallbacks = [];
    
    // Helper function to load scripts
    function loadScript(src) {
        return new Promise((resolve, reject) => {
            if (document.querySelector(`script[src="${src}"]`)) {
                resolve();
                return;
            }
            
            const script = document.createElement('script');
            script.src = src;
            script.onload = () => resolve();
            script.onerror = (error) => {
                console.error(`Failed to load script: ${src}`, error);
                reject(new Error(`Failed to load script: ${src}`));
            };
            document.head.appendChild(script);
        });
    }
    
    async function connect() {
        // Reset connection state
        isConnected = false;
        
        try {
            console.log('Starting WebSocket connection...');
            
            // Ensure SockJS is available
            if (typeof SockJS === 'undefined') {
                const error = 'SockJS is not loaded';
                console.error(error);
                throw new Error(error);
            }
            
            // Ensure STOMP is available
            if (typeof Stomp === 'undefined') {
                const error = 'STOMP is not loaded';
                console.error(error);
                throw new Error(error);
            }
            
            return new Promise((resolve, reject) => {
                try {
                    console.log('[WebSocket] Creating SockJS connection to /ws');
                    const socket = new SockJS('/ws');
                    console.log('[WebSocket] SockJS socket created');
                    
                    // Create STOMP client over SockJS
                    stompClient = Stomp.over(socket);
                    console.log('[WebSocket] STOMP client created over SockJS');
                    
                    // Configure STOMP debug logging
                    stompClient.debug = function(str) {
                        // Uncomment for detailed STOMP frame logging
                        // console.log('[STOMP]', str);
                    };
                    
                    // Configure heartbeats
                    stompClient.heartbeat.outgoing = 10000; // Send heartbeats every 10 seconds
                    stompClient.heartbeat.incoming = 10000; // Expect heartbeats every 10 seconds
                    
                    // Connection headers
                    const headers = {
                        'accept-version': '1.2',
                        'heart-beat': '10000,10000'
                    };
                    
                    console.log('[WebSocket] Attempting to connect to STOMP endpoint...');
                    
                    // Connect to STOMP
                    stompClient.connect(headers, 
                        // Success callback
                        function(frame) {
                            console.log('[WebSocket] Successfully connected to STOMP endpoint');
                            console.log('[WebSocket] STOMP Protocol Version:', frame.headers.version);
                            console.log('[WebSocket] Server:', frame.headers.server);
                            
                            isConnected = true;
                            reconnectAttempts = 0; // Reset reconnect attempts on successful connection
                            
                            // Notify all connect callbacks
                            connectCallbacks.forEach(callback => {
                                try {
                                    callback();
                                } catch (e) {
                                    console.error('[WebSocket] Error in connect callback:', e);
                                }
                            });
                            
                            // Resubscribe to any existing subscriptions
                            Object.entries(subscriptions).forEach(([destination, { callback }]) => {
                                console.log(`[WebSocket] Resubscribing to ${destination}`);
                                try {
                                    const subscription = stompClient.subscribe(destination, (message) => {
                                        console.log(`[WebSocket] Received message on ${destination}`, message);
                                        try {
                                            const parsedBody = JSON.parse(message.body);
                                            callback(parsedBody);
                                        } catch (e) {
                                            console.error(`[WebSocket] Error parsing message from ${destination}:`, e);
                                            console.error('[WebSocket] Raw message:', message.body);
                                            callback(message.body); // Pass raw body if JSON parse fails
                                        }
                                    });
                                    subscriptions[destination].subscription = subscription;
                                } catch (e) {
                                    console.error(`[WebSocket] Error resubscribing to ${destination}:`, e);
                                }
                            });
                            
                            resolve();
                        },
                        // Error callback
                        function(error) {
                            console.error('[WebSocket] STOMP connection error:', error);
                            isConnected = false;
                            
                            // Notify error callbacks
                            errorCallbacks.forEach(callback => {
                                try {
                                    callback(error);
                                } catch (e) {
                                    console.error('[WebSocket] Error in error callback:', e);
                                }
                            });
                            
                            // Attempt to reconnect
                            handleReconnect(reject);
                        }
                    );
                    
                    // Handle WebSocket close events
                    socket.onclose = function(event) {
                        console.warn('[WebSocket] Socket closed:', event);
                        isConnected = false;
                        
                        // Notify error callbacks
                        errorCallbacks.forEach(callback => {
                            try {
                                callback(new Error(`WebSocket connection closed: ${event.code} ${event.reason || ''}`));
                            } catch (e) {
                                console.error('[WebSocket] Error in close callback:', e);
                            }
                        });
                        
                        // Attempt to reconnect if this wasn't an intentional disconnect
                        if (stompClient !== null) {
                            handleReconnect(() => {});
                        }
                    };
                    
                    // Handle WebSocket errors
                    socket.onerror = function(error) {
                        console.error('[WebSocket] Socket error:', error);
                        // The onclose handler will be called after this
                    };
                    
                } catch (error) {
                    console.error('[WebSocket] Error during connection setup:', error);
                    isConnected = false;
                    handleReconnect(reject);
                }
            });
            
        } catch (error) {
            console.error('WebSocket connection failed:', error);
            isConnected = false;
            return Promise.reject(error);
        }
    }
    
    function handleReconnect(reject) {
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++;
            console.log(`Attempting to reconnect (${reconnectAttempts}/${maxReconnectAttempts})...`);
            
            // Notify error callbacks
            errorCallbacks.forEach(cb => cb(`Connection lost. Reconnecting (${reconnectAttempts}/${maxReconnectAttempts})...`));
            
            // Attempt to reconnect after delay
            setTimeout(() => {
                connect().catch(reject);
            }, reconnectDelay);
        } else {
            console.error('Max reconnection attempts reached');
            errorCallbacks.forEach(cb => cb('Unable to connect to the server. Please refresh the page to try again.'));
            if (reject) reject(new Error('Max reconnection attempts reached'));
        }
    }
    
    function subscribe(destination, callback) {
        if (isConnected && stompClient) {
            const subscription = stompClient.subscribe(destination, (message) => {
                try {
                    const data = JSON.parse(message.body);
                    callback(data);
                } catch (error) {
                    console.error('Error parsing message:', error);
                    callback(message.body);
                }
            });
            
            // Store subscription for reconnection
            subscriptions[destination] = { subscription, callback };
            
            // Return unsubscribe function
            return () => {
                subscription.unsubscribe();
                delete subscriptions[destination];
            };
        } else {
            // Store subscription to be activated after connection
            subscriptions[destination] = { callback };
            
            // Return a no-op unsubscribe function for now
            return () => {
                delete subscriptions[destination];
            };
        }
    }
    
    function send(destination, headers, body) {
        if (isConnected && stompClient) {
            stompClient.send(destination, headers, JSON.stringify(body));
        } else {
            console.error('Cannot send message: Not connected to WebSocket');
        }
    }
    
    function disconnect() {
        if (stompClient) {
            stompClient.disconnect();
            isConnected = false;
            console.log('Disconnected from WebSocket');
        }
    }
    
    function onConnect(callback) {
        if (typeof callback === 'function') {
            connectCallbacks.push(callback);
        }
    }
    
    function onError(callback) {
        if (typeof callback === 'function') {
            errorCallbacks.push(callback);
        }
    }
    
    // Helper function to load scripts dynamically
    function loadScript(src, callback) {
        const script = document.createElement('script');
        script.src = src;
        script.onload = callback;
        script.onerror = function() {
            console.error('Failed to load script: ' + src);
            errorCallbacks.forEach(cb => cb(`Failed to load required script: ${src}`));
        };
        document.head.appendChild(script);
    }
    
    // Public API
    const publicApi = {
        connect,
        subscribe,
        send,
        disconnect,
        onConnect: function(callback) {
            if (typeof callback === 'function') {
                connectCallbacks.push(callback);
            }
            return publicApi; // For chaining
        },
        onError: function(callback) {
            if (typeof callback === 'function') {
                errorCallbacks.push(callback);
            }
            return publicApi; // For chaining
        },
        isConnected: function() {
            return isConnected;
        }
    };
    
    return publicApi;
})();

// Export for Node.js/CommonJS
if (typeof module !== 'undefined' && module.exports) {
    module.exports = webSocketClient;
}

// Export for browser globals
if (typeof window !== 'undefined') {
    window.webSocketClient = webSocketClient;
}
