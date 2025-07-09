// Global variables for dashboard data
let dashboardCryptoData = []; // Renamed from cryptoData to avoid conflict
let dashboardUserHoldings = []; // Renamed from userHoldings
let dashboardCurrentUser = null; // Renamed from currentUser

// DOM elements (scoped within dashboard.js for clarity)
const dashboardCryptoContainer = document.getElementById('cryptoContainer'); // For crypto cards
const dashboardHoldingsTable = document.getElementById('holdings-table'); // For user holdings
const dashboardAccountBalanceEl = document.getElementById('account-balance');
const dashboardResetAccountBtn = document.getElementById('reset-account');
const dashboardLastUpdatedEl = document.getElementById('lastUpdated');


// --- WebSocket Client and Manager (Moved here from your index.html script block) ---
// Utility functions (already defined globally by trading-app.js)
// function formatPrice(price) { ... }
function formatPercentChange(change) {
    if (change === undefined || change === null || isNaN(parseFloat(change))) return 'N/A';
    const formatted = parseFloat(change).toFixed(2);
    return parseFloat(change) >= 0 ? `+${formatted}%` : `${formatted}%`;
}

// WebSocket Client using SockJS and STOMP
class WebSocketClient {
    constructor() {
        this.stompClient = null;
        this.isConnected = false;
        this.isConnecting = false;
        this.manuallyClosed = false;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectDelay = 5000;
        this.subscriptions = new Map(); // Stores active subscriptions {destination: {subscription, callback}}
        this.pendingSubscriptions = []; // Stores subscriptions that need to be re-established after reconnect
        this.connectCallbacks = [];
        this.disconnectCallbacks = [];
        this.errorCallbacks = [];
        this.connectionPromise = null; // To prevent multiple simultaneous connect calls
    }

    connect() {
        if (this.connectionPromise) { // Return existing connection promise if already connecting
            return this.connectionPromise;
        }

        this.connectionPromise = new Promise((resolve, reject) => {
            console.log('[WebSocketClient] Attempting to connect...');
            this.isConnecting = true;
            this.manuallyClosed = false; // Reset manually closed flag

            // Close any existing connection to ensure a fresh start
            if (this.stompClient && this.stompClient.connected) {
                try {
                    this.stompClient.disconnect(() => {
                        console.log('[WebSocketClient] Previous STOMP client disconnected.');
                        this._initiateStompConnection(resolve, reject);
                    });
                } catch (e) {
                    console.warn('[WebSocketClient] Error disconnecting existing client:', e);
                    this._initiateStompConnection(resolve, reject); // Proceed anyway
                }
            } else {
                this._initiateStompConnection(resolve, reject);
            }
        });
        return this.connectionPromise;
    }

    _initiateStompConnection(resolve, reject) {
        try {
            console.log('[WebSocketClient] Creating new SockJS connection to /ws');
            const socket = new SockJS('/ws');

            this.stompClient = Stomp.over(socket);
            this.stompClient.debug = () => {}; // Disable debug logging in production

            const headers = {
                'heart-beat': '10000,10000',
                'accept-version': '1.2',
                'host': window.location.host
            };

            this.stompClient.connect(
                headers,
                (frame) => {
                    console.log('[WebSocketClient] STOMP connection established:', frame);
                    this.isConnected = true;
                    this.isConnecting = false;
                    this.reconnectAttempts = 0;
                    this.connectionPromise = null;
                    this._onConnect(frame); // Call internal connect handler
                    resolve(frame);
                },
                (error) => {
                    console.error('[WebSocketClient] STOMP connection error:', error);
                    this.isConnected = false;
                    this.isConnecting = false;
                    this.connectionPromise = null;
                    this._onError(error); // Call internal error handler
                    reject(error);
                }
            );

        } catch (error) {
            console.error('[WebSocketClient] Critical connection setup error:', error);
            this.isConnected = false;
            this.isConnecting = false;
            this.connectionPromise = null;
            this._onError(error);
            reject(error);
        }
    }

    _onConnect(frame) {
        console.log('[WebSocketClient] Connection established (internal handler)');
        this.isConnected = true;
        this.reconnectAttempts = 0;

        // Notify all external connect callbacks
        this.connectCallbacks.forEach(callback => {
            try {
                callback();
            } catch (e) {
                console.error('Error in external connect callback:', e);
            }
        });

        // Re-subscribe to any existing subscriptions
        // Create a copy of subscriptions to avoid issues during iteration if subscriptions are changed
        const activeSubscriptions = Array.from(this.subscriptions.entries());
        activeSubscriptions.forEach(([destination, { callback }]) => {
            console.log(`[WebSocketClient] Re-subscribing to ${destination}`);
            this.subscribe(destination, callback); // Use original subscribe to re-establish
        });

        // Process any pending subscriptions (from before initial connect)
        const pending = [...this.pendingSubscriptions];
        this.pendingSubscriptions = []; // Clear pending after processing
        pending.forEach(({ destination, callback }) => {
            this.subscribe(destination, callback);
        });
    }

    _onError(error) {
        console.error('[WebSocketClient] Error (internal handler):', error);
        this.isConnected = false;
        this.isConnecting = false;

        // Notify all external error callbacks
        this.errorCallbacks.forEach(callback => {
            try {
                callback(error);
            } catch (e) {
                console.error('Error in external error callback:', e);
            }
        });

        // Only attempt to reconnect if not manually closed and within max attempts
        if (!this.manuallyClosed && this.reconnectAttempts < this.maxReconnectAttempts) {
            this.scheduleReconnect();
        } else if (!this.manuallyClosed) {
            console.warn('[WebSocketClient] Max reconnection attempts reached.');
            this.disconnectCallbacks.forEach(callback => callback()); // Notify disconnected
        }
    }

    scheduleReconnect() {
        if (this.manuallyClosed || this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('[WebSocketClient] Max reconnection attempts reached or connection manually closed');
            return;
        }

        this.reconnectAttempts++;
        const baseDelay = Math.min(this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1), 30000); // Max 30s
        const jitter = Math.random() * 1000; // Add up to 1s jitter
        const delay = Math.min(baseDelay + jitter, 30000);

        console.log(`[WebSocketClient] Reconnecting in ${Math.round(delay)}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);

        if (this._reconnectTimeout) {
            clearTimeout(this._reconnectTimeout);
        }
        this._reconnectTimeout = setTimeout(() => {
            this._reconnectTimeout = null;
            this.connect().catch(error => {
                console.error('[WebSocketClient] Reconnection attempt failed:', error);
                // If the connection attempt fails again, schedule another reconnect unless manually closed
                if (!this.manuallyClosed) {
                    this.scheduleReconnect();
                }
            });
        }, delay);
    }

    subscribe(destination, callback) {
        if (!destination || typeof callback !== 'function') {
            console.error('[WebSocketClient] Invalid subscription parameters:', { destination, callback });
            return;
        }

        // Store the subscription info (including the callback) so we can re-subscribe on reconnect
        this.subscriptions.set(destination, { destination, callback });

        if (!this.isConnected || !this.stompClient) {
            console.warn(`[WebSocketClient] Not connected. Queueing subscription to ${destination}`);
            this.pendingSubscriptions.push({ destination, callback }); // Add to pending list
            return;
        }

        try {
            console.log(`[WebSocketClient] Subscribing to ${destination}`);
            const subscription = this.stompClient.subscribe(destination, (message) => {
                try {
                    let parsedBody;
                    try {
                        parsedBody = typeof message.body === 'string' ?
                            JSON.parse(message.body) : message.body;
                    } catch (e) {
                        console.warn(`[WebSocketClient] Could not parse message as JSON, using raw body`);
                        parsedBody = message.body;
                    }
                    callback(parsedBody);
                } catch (e) {
                    console.error(`[WebSocketClient] Error processing message from ${destination}:`, e);
                    console.error('[WebSocketClient] Message that caused the error:', message);
                }
            });

            // Store the actual STOMP subscription object
            const currentSubscription = this.subscriptions.get(destination);
            if (currentSubscription) {
                currentSubscription.subscription = subscription;
                this.subscriptions.set(destination, currentSubscription);
            }
            return subscription;

        } catch (error) {
            console.error(`[WebSocketClient] Error subscribing to ${destination}:`, error);
            throw error;
        }
    }

    unsubscribe(destination) {
        const subInfo = this.subscriptions.get(destination);
        if (subInfo && subInfo.subscription) {
            try {
                subInfo.subscription.unsubscribe();
                this.subscriptions.delete(destination);
                console.log(`[WebSocketClient] Unsubscribed from ${destination}`);
            } catch (error) {
                console.error(`[WebSocketClient] Error unsubscribing from ${destination}:`, error);
            }
        } else {
            console.warn(`[WebSocketClient] No active subscription found for ${destination}.`);
        }
        // Remove from pending subscriptions too, if it was there
        this.pendingSubscriptions = this.pendingSubscriptions.filter(sub => sub.destination !== destination);
    }

    send(destination, headers = {}, body = {}) {
        if (!this.isConnected || !this.stompClient) {
            console.warn(`[WebSocketClient] Not connected. Cannot send message to ${destination}`);
            return Promise.reject(new Error('WebSocket not connected'));
        }

        return new Promise((resolve, reject) => {
            try {
                // STOMP.js send method expects headers object and string body
                this.stompClient.send(destination, headers, JSON.stringify(body));
                resolve();
            } catch (error) {
                console.error(`[WebSocketClient] Error sending message to ${destination}:`, error);
                reject(error);
            }
        });
    }

    disconnect() {
        this.manuallyClosed = true;
        if (this._reconnectTimeout) {
            clearTimeout(this._reconnectTimeout);
            this._reconnectTimeout = null;
        }

        if (this.stompClient) {
            try {
                this.stompClient.disconnect(() => {
                    console.log('[WebSocketClient] Disconnected (STOMP)');
                    this.isConnected = false;
                    this.isConnecting = false;
                    this.disconnectCallbacks.forEach(callback => callback());
                });
            } catch (error) {
                console.error('[WebSocketClient] Error during STOMP disconnect:', error);
                this.isConnected = false;
                this.isConnecting = false;
                this.disconnectCallbacks.forEach(callback => callback());
            }
            this.stompClient = null;
        } else {
            this.isConnected = false;
            this.isConnecting = false;
            this.disconnectCallbacks.forEach(callback => callback());
        }
        console.log('[WebSocketClient] Disconnect initiated');
    }

    destroy() {
        console.log('[WebSocketClient] Destroying client...');
        this.disconnect();
        this.subscriptions.clear();
        this.pendingSubscriptions = [];
        this.connectCallbacks = [];
        this.disconnectCallbacks = [];
        this.errorCallbacks = [];
    }

    onConnect(callback) { if (typeof callback === 'function') { this.connectCallbacks.push(callback); } return this; }
    onDisconnect(callback) { if (typeof callback === 'function') { this.disconnectCallbacks.push(callback); } return this; }
    onError(callback) { if (typeof callback === 'function') { this.errorCallbacks.push(callback); } return this; }
}


class WebSocketManager {
    constructor() {
        this.client = new WebSocketClient();
        this.isConnected = false;
        this.isConnecting = false;
        this.connectionPromise = null; // To manage concurrent connections
        this.currentCryptoPrices = new Map(); // Store current crypto prices for `openTradeModal`

        // Set up event handlers to update manager's state
        this.client.onConnect(() => {
            this.isConnected = true;
            this.isConnecting = false;
            this.updateConnectionStatusUI();
        });

        this.client.onDisconnect(() => {
            this.isConnected = false;
            this.isConnecting = false;
            this.updateConnectionStatusUI();
        });

        this.client.onError(error => {
            console.error('WebSocketManager received error from client:', error);
            this.isConnected = false;
            this.isConnecting = false;
            this.updateConnectionStatusUI();
        });

        // Initialize UI status
        this.updateConnectionStatusUI();

        // Auto-connect the client
        this.connect().catch(error => {
            console.error('Failed to auto-connect to WebSocket server on init:', error);
        });

        // Expose for debugging
        window.webSocketManager = this;
    }

    async connect() {
        if (this.connectionPromise) {
            console.log('WebSocketManager: Connection already in progress.');
            return this.connectionPromise;
        }
        if (this.isConnected) {
            console.log('WebSocketManager: Already connected.');
            return Promise.resolve();
        }

        this.isConnecting = true;
        this.updateConnectionStatusUI();
        console.log('WebSocketManager: Initiating connection...');

        this.connectionPromise = this.client.connect()
            .then(() => {
                console.log('WebSocketManager: Connection established successfully. Subscribing to topics...');
                this.isConnected = true;
                this.isConnecting = false;
                this.updateConnectionStatusUI();
                this.subscribeToTopics(); // Subscribe to all topics on successful connect
            })
            .catch(error => {
                console.error('WebSocketManager: Connection failed:', error);
                this.isConnected = false;
                this.isConnecting = false;
                this.updateConnectionStatusUI();
                throw error; // Re-throw to propagate error
            })
            .finally(() => {
                this.connectionPromise = null;
            });
        return this.connectionPromise;
    }

    disconnect() {
        console.log('WebSocketManager: Disconnecting...');
        this.client.disconnect();
    }

    subscribe(destination, callback) {
        return this.client.subscribe(destination, callback);
    }

    unsubscribe(destination) {
        this.client.unsubscribe(destination);
    }

    send(destination, headers = {}, body = {}) {
        return this.client.send(destination, headers, body);
    }

    destroy() {
        console.log('WebSocketManager: Destroying...');
        this.client.destroy();
        this.currentCryptoPrices.clear();
        this.updateConnectionStatusUI(); // Update UI to disconnected state
    }

    // --- UI Update Methods for WebSocket Control Panel ---
    updateConnectionStatusUI() {
        const statusElement = document.getElementById('connectionStatus');
        const statusText = document.getElementById('connectionText');
        const connectButton = document.getElementById('connectButton');
        const connectionTime = document.getElementById('connectionTime');

        if (!statusElement || !statusText || !connectButton || !connectionTime) {
            console.warn('WebSocketManager: Missing connection UI elements.');
            return;
        }

        // Clear all status classes
        statusElement.className = 'connection-status';

        if (this.isConnected) {
            statusElement.classList.add('connected');
            statusText.textContent = 'Connected';
            connectButton.innerHTML = '<i class="bi bi-plug"></i> <span class="d-none d-sm-inline">Connected</span>';
            connectButton.classList.remove('btn-outline-primary', 'btn-outline-warning');
            connectButton.classList.add('btn-outline-success');
            connectButton.disabled = true; // Disable if already connected
        } else if (this.isConnecting) {
            statusElement.classList.add('connecting');
            statusText.textContent = 'Connecting...';
            connectButton.innerHTML = '<i class="bi bi-hourglass-split"></i> <span class="d-none d-sm-inline">Connecting</span>';
            connectButton.classList.remove('btn-outline-primary', 'btn-outline-success');
            connectButton.classList.add('btn-outline-warning');
            connectButton.disabled = true; // Disable if connecting
        } else {
            statusElement.classList.add('disconnected');
            statusText.textContent = 'Disconnected';
            connectButton.innerHTML = '<i class="bi bi-plug"></i> <span class="d-none d-sm-inline">Connect</span>';
            connectButton.classList.remove('btn-outline-success', 'btn-outline-warning');
            connectButton.classList.add('btn-outline-primary');
            connectButton.disabled = false; // Enable if disconnected
        }

        const now = new Date();
        connectionTime.textContent = `(${now.toLocaleTimeString()})`;

        // Enable/disable send/info buttons based on connection state
        document.getElementById('sendTestMessage')?.classList.toggle('disabled', !this.isConnected);
        document.getElementById('sendTestMessage')?.toggleAttribute('disabled', !this.isConnected);
        document.getElementById('sendBroadcast')?.classList.toggle('disabled', !this.isConnected);
        document.getElementById('sendBroadcast')?.toggleAttribute('disabled', !this.isConnected);
        document.getElementById('connectionInfo')?.classList.toggle('disabled', !this.isConnected);
        document.getElementById('connectionInfo')?.toggleAttribute('disabled', !this.isConnected);
    }

    // --- Dashboard Specific Data & UI Updates ---

    /**
     * Fetch initial cryptocurrency data from REST API and then listen for WebSocket updates
     */
    async fetchCryptoData() {
        console.log('WebSocketManager: Fetching initial crypto data...');
        if (dashboardCryptoContainer) {
            dashboardCryptoContainer.innerHTML = `
                <div class="col-12 text-center">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">Loading...</span>
                    </div>
                    <p class="mt-2">Loading cryptocurrency data...</p>
                </div>
            `;
        }

        try {
            const response = await fetch('/api/crypto'); // Assuming your backend has this REST endpoint
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            console.log('WebSocketManager: Received initial cryptocurrency data:', data);

            // Store initial data and render
            dashboardCryptoData = data;
            this.updateCryptoListUI(dashboardCryptoData);

            // Populate current prices map
            dashboardCryptoData.forEach(crypto => {
                this.currentCryptoPrices.set(crypto.symbol, crypto.currentPrice);
            });
            this.updateLastUpdatedUI(); // Update last updated timestamp for dashboard
            this.subscribeToTopics(); // Ensure topics are subscribed if not already (redundant but safe)

        } catch (error) {
            console.error('WebSocketManager: Error fetching initial cryptocurrency data:', error);
            if (dashboardCryptoContainer) {
                dashboardCryptoContainer.innerHTML = `
                    <div class="col-12 text-center text-danger">
                        <p>Error loading cryptocurrency data. Please try again later.</p>
                        <p class="small text-muted">${error.message}</p>
                        <button class="btn btn-primary btn-sm" onclick="window.webSocketManager.fetchCryptoData()">
                            <i class="bi bi-arrow-clockwise"></i> Retry
                        </button>
                    </div>
                `;
            }
            showNotification('Error loading initial crypto data. Check console.', 'error');
        }
    }

    /**
     * Update the cryptocurrency list in the UI based on `dashboardCryptoData`
     */
    updateCryptoListUI() {
        console.log('WebSocketManager: Updating crypto list UI...');
        if (!dashboardCryptoContainer) {
            console.error('Crypto container not found');
            return;
        }

        if (!Array.isArray(dashboardCryptoData) || dashboardCryptoData.length === 0) {
            dashboardCryptoContainer.innerHTML = `
                <div class="col-12 text-center">
                    <p>No cryptocurrency data available.</p>
                </div>
            `;
            return;
        }

        function getCryptoIcon(symbol) {
            const icons = {
                'BTC': '₿', 'ETH': 'Ξ', 'XRP': '✕', 'LTC': 'Ł',
                'BCH': 'Ƀ', 'BNB': '₿', 'EOS': 'ε', 'XLM': '*',
                'TRX': '₮', 'XTZ': 'ꜩ', 'ATOM': '⚛', 'LINK': '🔗',
                'DOT': '●', 'ADA': '₳', 'XMR': 'ɱ', 'DASH': 'Đ',
                'ZEC': 'ⓩ', 'DOGE': 'Ð', 'VET': 'ᐯ', 'FIL': '⨎',
                'SOL': '◎', 'UNI': '🦄', 'AAVE': '🦇', 'GRT': '🌐',
                'SNX': '₴', 'COMP': '🦄', 'MKR': '◎', 'YFI': '🏦',
                'SUSHI': '🍣', 'CRV': '🔄', 'BAL': '⚖', 'REN': '🏛',
                'NEO': 'N', 'ETC': 'ξ', 'XEM': 'X', 'ZIL': 'Z',
                'QTUM': 'Q', 'ICX': '⚡', 'WAVES': 'W', 'ONT': 'O',
                'ZRX': 'Z', 'ALGO': 'Ⱥ', 'BAT': '🦇', 'OMG': 'OMG',
                'KSM': 'K', 'AVAX': 'A', 'FTM': 'F', 'MATIC': 'M',
                'NEAR': 'Ⓝ', 'ONE': '1', 'EGLD': 'eGLD', 'CELO': 'C'
            };
            return icons[symbol] || symbol;
        }

        // Generate card HTML
        dashboardCryptoContainer.innerHTML = dashboardCryptoData.map(crypto => `
            <div class="col-md-4 mb-4" id="crypto-${crypto.symbol}">
                <div class="card h-100 crypto-card">
                    <div class="card-body">
                        <div class="d-flex justify-content-between align-items-center mb-3">
                            <div>
                                <h5 class="card-title mb-0">
                                    <span class="crypto-icon me-2" style="display: inline-block; width: 24px; text-align: center;">
                                        ${getCryptoIcon(crypto.symbol)}
                                    </span>
                                    ${crypto.name} (${crypto.symbol})
                                    ${crypto.marketRank ? `<span class="market-rank badge bg-secondary ms-2">#${crypto.marketRank}</span>` : ''}
                                </h5>
                            </div>
                            <span class="badge bg-primary">${crypto.symbol}</span>
                        </div>
                        <div class="d-flex justify-content-between align-items-center">
                            <div>
                                <div class="fs-4 fw-bold card-price" data-last-price="${crypto.currentPrice}">
                                    ${formatPrice(crypto.currentPrice)}
                                </div>
                                <div class="text-muted">Price (USD)</div>
                            </div>
                            <div class="text-end">
                                <div class="fs-5 fw-bold card-change text-muted">
                                    ${formatPercentChange(crypto.percentChange24h || 0)}
                                </div>
                                <div class="text-muted">24h</div>
                            </div>
                        </div>
                    </div>
                    <div class="card-footer bg-transparent">
                        <div class="d-flex justify-content-between align-items-center">
                            <small class="text-muted last-updated">
                                Last updated: ${new Date(crypto.lastUpdated).toLocaleTimeString()}
                            </small>
                            <button class="btn btn-sm btn-outline-primary"
                                    onclick="window.openTradeModal('${crypto.symbol}', ${crypto.currentPrice}, window.dashboardUserHoldings.find(h => h.symbol === '${crypto.symbol}')?.quantity || 0)">
                                Trade
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `).join('');
    }

    /**
     * Update a single crypto card's price and change based on WebSocket update
     * @param {object} update - The WebSocket message containing price update
     */
    updateCryptoCardUI(update) {
        // Log the received update for debugging
        console.log('[WebSocketManager: UI] Received price update for card:', update);

        // Ensure symbol and price are present
        const symbol = update.symbol?.toUpperCase();
        const newPrice = update.newPrice || update.price;
        if (!symbol || newPrice === undefined || newPrice === null) {
            console.error('Invalid price update format:', update);
            return;
        }

        const cardElement = document.getElementById(`crypto-${symbol}`);
        if (!cardElement) {
            console.log(`Card for ${symbol} not found in UI, skipping update.`);
            return;
        }

        const priceElement = cardElement.querySelector('.card-price');
        const changeElement = cardElement.querySelector('.card-change');
        const lastUpdatedElement = cardElement.querySelector('.last-updated');

        if (!priceElement || !changeElement || !lastUpdatedElement) {
            console.warn(`Missing elements in card for ${symbol}, skipping update.`);
            return;
        }

        const oldPrice = parseFloat(priceElement.dataset.lastPrice || newPrice);
        const currentPrice = parseFloat(newPrice);

        // Animate price change
        if (currentPrice > oldPrice) {
            priceElement.classList.remove('text-danger', 'text-muted');
            priceElement.classList.add('text-success', 'price-up');
            cardElement.classList.add('bg-success', 'bg-opacity-10');
        } else if (currentPrice < oldPrice) {
            priceElement.classList.remove('text-success', 'text-muted');
            priceElement.classList.add('text-danger', 'price-down');
            cardElement.classList.add('bg-danger', 'bg-opacity-10');
        } else {
            priceElement.classList.remove('text-success', 'text-danger');
            priceElement.classList.add('text-muted');
        }

        // Remove animation classes after delay
        setTimeout(() => {
            priceElement.classList.remove('price-up', 'price-down');
            cardElement.classList.remove('bg-success', 'bg-opacity-10', 'bg-danger', 'bg-opacity-10');
        }, 1500);

        // Update price text and store for next comparison
        priceElement.textContent = formatPrice(currentPrice);
        priceElement.dataset.lastPrice = currentPrice;
        this.currentCryptoPrices.set(symbol, currentPrice); // Update map of current prices

        // Update 24h change (if provided in update)
        if (update.percentChange24h !== undefined) {
            const formattedChange = formatPercentChange(update.percentChange24h);
            changeElement.textContent = formattedChange;
            changeElement.classList.remove('text-success', 'text-danger', 'text-muted');
            changeElement.classList.add(parseFloat(update.percentChange24h) >= 0 ? 'text-success' : 'text-danger');
        } else {
            // If 24h change is not in update, remove specific color and set to muted
            changeElement.classList.remove('text-success', 'text-danger');
            changeElement.classList.add('text-muted');
        }

        // Update timestamp
        const now = new Date();
        lastUpdatedElement.textContent = `Last updated: ${now.toLocaleTimeString()}`;
        lastUpdatedElement.title = now.toString();
        this.updateLastUpdatedUI(); // Update global dashboard timestamp

        console.log(`[WebSocketManager: UI] Updated ${symbol} to ${formatPrice(currentPrice)}`);
    }


    /**
     * Update the last updated timestamp in the UI
     */
    updateLastUpdatedUI() {
        if (dashboardLastUpdatedEl) {
            const now = new Date();
            dashboardLastUpdatedEl.textContent = `Last updated: ${now.toLocaleTimeString()}`;
        }
    }

    /**
     * Subscribes to necessary WebSocket topics. Called after successful connection.
     */
    subscribeToTopics() {
        console.log('WebSocketManager: Subscribing to WebSocket topics...');

        // Subscribe to price updates
        this.subscribe('/topic/prices', (priceUpdate) => {
            try {
                console.log('WebSocketManager: Received price update from topic:', priceUpdate);
                this.updateCryptoCardUI(priceUpdate); // Update individual crypto card
                // Also update the dashboardCryptoData in memory if needed for other uses
                const index = dashboardCryptoData.findIndex(c => c.symbol === priceUpdate.symbol);
                if (index !== -1) {
                    dashboardCryptoData[index].currentPrice = priceUpdate.newPrice;
                    dashboardCryptoData[index].percentChange24h = priceUpdate.percentChange24h;
                    dashboardCryptoData[index].lastUpdated = new Date().toISOString();
                }
            } catch (error) {
                console.error('WebSocketManager: Error processing price update:', error, priceUpdate);
            }
        }).catch(err => console.error('Failed to subscribe to /topic/prices:', err));

        // Subscribe to general updates (e.g., system messages)
        this.subscribe('/topic/updates', (update) => {
            console.log('WebSocketManager: Received general update:', update);
            const notificationMessage = typeof update === 'object' && update.message ? update.message : update;
            showNotification(notificationMessage, 'info');
        }).catch(err => console.error('Failed to subscribe to /topic/updates:', err));

        // Subscribe to broadcast messages
        this.subscribe('/topic/broadcast', (message) => {
            console.log('WebSocketManager: Received broadcast:', message);
            const notification = typeof message === 'object' ? message.message : message;
            showNotification(notification || 'New broadcast message', 'info');
        }).catch(err => console.error('Failed to subscribe to /topic/broadcast:', err));

        // Subscribe to private messages (user-specific queue)
        // Assuming current user ID is available from dashboardCurrentUser
        if (dashboardCurrentUser && dashboardCurrentUser.id) {
            this.subscribe(`/user/queue/notifications/${dashboardCurrentUser.id}`, (message) => {
                console.log('WebSocketManager: Received private message:', message);
                const msgText = typeof message === 'object' ? message.message : message;
                showNotification(`Private: ${msgText}`, 'private');
            }).catch(err => console.error(`Failed to subscribe to private notifications for user ${dashboardCurrentUser.id}:`, err));
        } else {
            console.warn('WebSocketManager: User ID not available for private message subscription.');
        }

        // Send a test message to verify the connection
        if (this.isConnected) {
            const testMessage = {
                action: 'connection_test',
                timestamp: new Date().toISOString(),
                clientInfo: {
                    userAgent: navigator.userAgent,
                    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                    screenResolution: `${window.screen.width}x${window.screen.height}`
                }
            };

            this.send('/app/test/echo', {}, testMessage)
                .then(() => console.log('WebSocketManager: Test message sent successfully'))
                .catch(err => {
                    console.error('WebSocketManager: Failed to send test message:', err);
                    showNotification('Failed to verify connection with the server', 'error');
                });
        }
    }

    // --- Account and Holdings (Moved from original app.js) ---

    // Load current user
    async loadCurrentUser() {
        console.log('dashboard.js: Loading current user...');
        try {
            const response = await fetch('/api/users/current');
            if (!response.ok) throw new Error('Failed to fetch current user');

            dashboardCurrentUser = await response.json();
            if (dashboardAccountBalanceEl) {
                dashboardAccountBalanceEl.textContent = formatPrice(dashboardCurrentUser.balance);
                console.log('dashboard.js: User loaded:', dashboardCurrentUser.username, 'Balance:', dashboardCurrentUser.balance);
            }
        } catch (error) {
            console.error('dashboard.js: Error loading current user:', error);
            dashboardCurrentUser = { id: 1, username: 'default_user', balance: 10000.00 }; // Default user
            if (dashboardAccountBalanceEl) {
                dashboardAccountBalanceEl.textContent = formatPrice(dashboardCurrentUser.balance);
            }
            console.log('dashboard.js: Default user set due to error.');
        }
        // After loading user, load holdings (which depends on currentUser)
        this.loadUserHoldings();
        // Also subscribe to private WebSocket messages if user ID is available
        if (this.isConnected && dashboardCurrentUser && dashboardCurrentUser.id) {
            this.subscribe(`/user/queue/notifications/${dashboardCurrentUser.id}`, (message) => {
                console.log('WebSocketManager: Received private message:', message);
                const msgText = typeof message === 'object' ? message.message : message;
                showNotification(`Private: ${msgText}`, 'private');
            }).catch(err => console.error(`Failed to subscribe to private notifications for user ${dashboardCurrentUser.id}:`, err));
        }
    }
    // Expose globally for TradingApp to refresh account balance
    // This is a common pattern for separate modules to communicate
    // In a larger app, consider a dedicated event bus
    //window.loadAccountData = () => this.loadCurrentUser();

    // Load user holdings
    async loadUserHoldings() {
        console.log('dashboard.js: Loading user holdings...');
        if (!dashboardCurrentUser || !dashboardHoldingsTable) {
            console.warn('dashboard.js: Cannot load holdings - currentUser or holdingsTable not ready.');
            if (dashboardHoldingsTable) dashboardHoldingsTable.innerHTML = `<tr><td colspan="4" class="text-center text-muted">Please log in or account data not ready.</td></tr>`;
            return;
        }
        try {
            const response = await fetch(`/api/holdings/${dashboardCurrentUser.id}`);
            if (!response.ok) throw new Error('Failed to fetch user holdings');
            dashboardUserHoldings = await response.json(); // Update global holdings variable
            this.renderHoldingsTableUI();
            console.log('dashboard.js: User holdings loaded:', dashboardUserHoldings);
        } catch (error) {
            console.error('dashboard.js: Error loading user holdings:', error);
            if (dashboardHoldingsTable) dashboardHoldingsTable.innerHTML = `<tr><td colspan="4" class="text-center text-danger">Failed to load holdings</td></tr>`;
        }
    }
    // Expose globally for TradingApp to refresh holdings
    //window.loadUserHoldings = () => this.loadUserHoldings();

    // Render holdings table
    renderHoldingsTableUI() {
        console.log('dashboard.js: Rendering holdings table...');
        if (!dashboardHoldingsTable) {
            console.error('dashboard.js: holdingsTable element not found.');
            return;
        }

        if (dashboardUserHoldings.length === 0) {
            dashboardHoldingsTable.innerHTML = `<tr><td colspan="4" class="text-center">No holdings yet</td></tr>`;
            return;
        }

        dashboardHoldingsTable.innerHTML = '';
        dashboardUserHoldings.forEach(holding => {
            const currentCrypto = dashboardCryptoData.find(c => c.symbol === holding.symbol) || { currentPrice: 0 };
            const currentValue = holding.quantity * currentCrypto.currentPrice;

            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${holding.symbol}</td>
                <td>${parseFloat(holding.quantity).toFixed(8)}</td>
                <td>${formatPrice(holding.averagePrice)}</td>
                <td>${formatPrice(currentValue)}</td>
            `;
            dashboardHoldingsTable.appendChild(row);
        });
    }

    // Reset user account
    initResetAccountButton() {
        if (dashboardResetAccountBtn) {
            dashboardResetAccountBtn.addEventListener('click', async () => {
                console.log('dashboard.js: Reset account button clicked.');
                if (!dashboardCurrentUser) {
                    console.warn('dashboard.js: Cannot reset account, no current user.');
                    return;
                }

                if (!confirm('Are you sure you want to reset your account? This action cannot be undone.')) {
                    return;
                }

                try {
                    const response = await fetch(`/api/users/${dashboardCurrentUser.id}/reset`, {
                        method: 'PUT'
                    });

                    if (!response.ok) throw new Error('Failed to reset account');

                    // Reload user data, holdings, and transaction history
                    await this.loadCurrentUser();
                    await this.loadUserHoldings();
                    if (window.tradingApp && typeof window.tradingApp.loadTransactionHistory === 'function') {
                        window.tradingApp.loadTransactionHistory();
                    }

                    showNotification('Account has been reset successfully!', 'success');
                    console.log('dashboard.js: Account reset successful.');
                } catch (error) {
                    console.error('dashboard.js: Error resetting account:', error);
                    showNotification('Failed to reset account. Please try again.', 'error');
                }
            });
        } else {
            console.warn('dashboard.js: dashboardResetAccountBtn not found.');
        }
    }

    // --- WebSocket Control Panel Event Listeners ---
    initWebSocketControlListeners() {
        document.getElementById('connectButton')?.addEventListener('click', () => {
            if (!this.isConnected && !this.isConnecting) {
                console.log('Manually initiating WebSocket connection...');
                this.connect()
                    .then(() => showNotification('Connected to WebSocket server', 'success'))
                    .catch(error => showNotification(`Connection failed: ${error.message}`, 'error'));
            } else if (this.isConnecting) {
                showNotification('Connection in progress...', 'info');
            } else {
                showNotification('Already connected', 'info');
            }
        });

        document.getElementById('sendTestMessage')?.addEventListener('click', () => {
            if (this.isConnected) {
                const testMessage = {
                    action: 'manual_test',
                    message: 'This is a test message from the UI',
                    timestamp: new Date().toISOString()
                };
                this.send('/app/test/echo', {}, testMessage)
                    .then(() => showNotification('Test message sent', 'success'))
                    .catch(error => showNotification('Failed to send test message', 'error'));
            } else {
                showNotification('Not connected to WebSocket server', 'error');
            }
        });

        document.getElementById('sendBroadcast')?.addEventListener('click', () => {
            const message = prompt('Enter broadcast message:');
            if (message) {
                // This typically calls a REST endpoint that then sends via WebSocket
                fetch('/api/ws-test/broadcast', { // Assuming this endpoint exists on your backend
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ message: message })
                })
                    .then(response => response.json())
                    .then(data => showNotification(data.message || 'Broadcast sent', 'success'))
                    .catch(error => showNotification(`Error sending broadcast: ${error.message}`, 'error'));
            }
        });

        document.getElementById('connectionInfo')?.addEventListener('click', () => {
            fetch('/api/ws-test/connection-info') // Assuming this endpoint exists on your backend
                .then(response => {
                    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
                    return response.json();
                })
                .then(data => {
                    const info = [
                        `WS Status: ${this.isConnected ? 'Connected' : 'Disconnected'}`,
                        `Active Sessions: ${data.activeConnections || 'N/A'}`,
                        `Test Messages Sent: ${data.testMessageCount || '0'}`,
                        `Last Server Update: ${new Date(data.timestamp || Date.now()).toLocaleString()}`,
                        `Reconnect Attempts: ${this.client.reconnectAttempts}`
                    ];
                    alert(info.join('\n'));
                })
                .catch(error => showNotification(`Error fetching connection info: ${error.message}`, 'error'));
        });
    }
}


// --- Notification System (Moved from index.html script block) ---
const notificationSystem = (function() {
    let notificationContainer = document.getElementById('notification-container');
    if (!notificationContainer) {
        notificationContainer = document.createElement('div');
        notificationContainer.id = 'notification-container';
        document.body.appendChild(notificationContainer);
    }

    const icons = {
        info: 'bi-info-circle',
        success: 'bi-check-circle',
        warning: 'bi-exclamation-triangle',
        error: 'bi-x-circle',
        private: 'bi-shield-lock'
    };

    function show(message, type = 'info', duration = 5000) {
        const notification = document.createElement('div');
        notification.className = `notification ${type}`;

        const icon = document.createElement('i');
        icon.className = `bi ${icons[type] || icons.info} notification-icon`;
        notification.appendChild(icon);

        const content = document.createElement('div');
        content.className = 'notification-content';
        content.textContent = typeof message === 'string' ? message : JSON.stringify(message);
        notification.appendChild(content);

        const closeBtn = document.createElement('button');
        closeBtn.className = 'notification-close';
        closeBtn.innerHTML = '&times;';
        closeBtn.onclick = () => removeNotification(notification);
        notification.appendChild(closeBtn);

        notificationContainer.appendChild(notification);

        if (duration > 0) {
            setTimeout(() => {
                removeNotification(notification);
            }, duration);
        }
        return { remove: () => removeNotification(notification) };
    }

    function removeNotification(notification) {
        if (!notification) return;
        notification.classList.add('fade-out');
        setTimeout(() => {
            if (notification.parentNode === notificationContainer) {
                notificationContainer.removeChild(notification);
            }
        }, 500);
    }

    return {
        show,
        info: (msg, duration) => show(msg, 'info', duration),
        success: (msg, duration) => show(msg, 'success', duration),
        warning: (msg, duration) => show(msg, 'warning', duration),
        error: (msg, duration) => show(msg, 'error', duration),
        private: (msg, duration) => show(msg, 'private', duration)
    };
})();

// Alias for backward compatibility and simpler calls
function showNotification(message, type = 'info', duration = 5000) {
    console.log(`[Notification - ${type.toUpperCase()}]`, message);
    const notifier = notificationSystem[type] || notificationSystem.info;
    notifier(message, duration);
}


// --- Main Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    console.log('dashboard.js: DOMContentLoaded - Initializing dashboard and WebSocketManager...');

    try {
        if (typeof WebSocketManager === 'undefined') {
            throw new Error('WebSocketManager is not defined. Check script loading order.');
        }

        const wsManager = new WebSocketManager();
        // Make it globally available for other scripts if needed, though direct access within this file is preferred
        window.webSocketManager = wsManager;

        // Initialize dashboard components
        wsManager.loadCurrentUser(); // Fetches user & holdings
        wsManager.fetchCryptoData(); // Fetches initial crypto data & subscribes to WS updates
        wsManager.initResetAccountButton(); // Setup reset button listener
        wsManager.initWebSocketControlListeners(); // Setup WS control panel listeners

        // Handle page unload to clean up WebSocket resources
        window.addEventListener('beforeunload', () => {
            console.log('Page unloading, cleaning up WebSocket resources...');
            if (wsManager) {
                wsManager.destroy();
            }
        });

        console.log('dashboard.js: Initialization complete.');

    } catch (error) {
        console.error('dashboard.js: Failed to initialize:', error);
        // Display a prominent error message on the page
        const container = document.getElementById('cryptoContainer');
        if (container) {
            container.innerHTML = `
                <div class="col-12 text-center text-danger mt-5">
                    <h3>Application Startup Error!</h3>
                    <p>Failed to initialize the dashboard. Please check the browser console for details.</p>
                    <p class="small text-muted">Error: ${error.message || 'Unknown error'}</p>
                    <button class="btn btn-primary mt-3" onclick="window.location.reload()">
                        <i class="bi bi-arrow-clockwise"></i> Reload Page
                    </button>
                </div>
            `;
        }
        showNotification('Failed to start application. See console for details.', 'error', 0); // Permanent error notification
    }
});