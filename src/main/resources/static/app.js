// Global utility function for formatting prices/currency
// This is now global for both dashboard.js and TradingApp.js to use
function formatPrice(price) {
    if (typeof price !== 'number' || isNaN(price)) return '-'; // Handle non-numeric or NaN input
    return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD', // Or 'BTC', 'ETH' depending on context
        minimumFractionDigits: 2,
        maximumFractionDigits: 8 // Adjust for crypto precision
    }).format(price);
}


class TradingApp {
    constructor() {
        console.log('TradingApp: Constructor called');
        this.currentPage = 1;
        this.pageSize = 10;
        this.userId = 1; // Default user ID, in a real app this would come from authentication
        this.currentSymbol = null;
        this.currentPrice = 0;
        this.availableAmount = 0;

        // Bounded functions for event listener cleanup to prevent multiple bindings
        this._boundPlaceBuyOrder = null;
        this._boundPlaceSellOrder = null;
        this._boundUpdateEstimateOnChange = null; // For tradeType select

        // Initialize UI components
        this.initEventListeners();
        this.initToast();

        // Load initial data (e.g., transaction history)
        this.loadTransactionHistory();
    }

    initEventListeners() {
        console.log('TradingApp: initEventListeners called');
        // Trade form input listener for estimation
        const tradeAmountInput = document.getElementById('tradeAmount');
        if (tradeAmountInput) {
            tradeAmountInput.addEventListener('input', (e) => this.updateTradeEstimate());
            console.log('TradingApp: tradeAmount input listener attached.');
        } else {
            console.warn('TradingApp: Element with ID "tradeAmount" not found. Check HTML.');
        }

        // Transaction history pagination (Assuming these are in your `account.html` or main page)
        const prevPageBtn = document.getElementById('prevPage');
        const nextPageBtn = document.getElementById('nextPage');
        if (prevPageBtn) {
            prevPageBtn.addEventListener('click', (e) => {
                e.preventDefault();
                if (this.currentPage > 1) {
                    this.currentPage--;
                    this.loadTransactionHistory();
                }
                console.log('TradingApp: Previous page clicked. Current page:', this.currentPage);
            });
        } else { console.warn('TradingApp: Element with ID "prevPage" not found (for history pagination).'); }
        if (nextPageBtn) {
            nextPageBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.currentPage++;
                this.loadTransactionHistory();
                console.log('TradingApp: Next page clicked. Current page:', this.currentPage);
            });
        } else { console.warn('TradingApp: Element with ID "nextPage" not found (for history pagination).'); }


        // Page size selector (for history pagination)
        const pageSizeSelect = document.getElementById('pageSizeSelect');
        if (pageSizeSelect) {
            pageSizeSelect.addEventListener('change', (e) => {
                this.pageSize = parseInt(e.target.value);
                this.currentPage = 1; // Reset to first page
                this.loadTransactionHistory();
                console.log('TradingApp: Page size changed to:', this.pageSize);
            });
        } else { console.warn('TradingApp: Element with ID "pageSizeSelect" not found (for history pagination).'); }

        // Refresh button (for history pagination)
        const refreshHistoryBtn = document.getElementById('refreshHistoryBtn');
        if (refreshHistoryBtn) {
            refreshHistoryBtn.addEventListener('click', () => {
                this.loadTransactionHistory();
                console.log('TradingApp: Refresh history button clicked.');
            });
        } else { console.warn('TradingApp: Element with ID "refreshHistoryBtn" not found (for history pagination).'); }
    }

    initToast() {
        this.toastEl = document.getElementById('orderToast');
        if (this.toastEl) {
            this.toast = new bootstrap.Toast(this.toastEl, {
                autohide: true,
                delay: 5000
            });
            console.log('TradingApp: Toast initialized.');
        } else {
            console.warn('TradingApp: Toast element with ID "orderToast" not found. Toast functionality might be limited.');
        }
    }

    showToast(title, message, isError = false) {
        if (!this.toastEl) {
            console.error('TradingApp: Cannot show toast, toast element not found.');
            return;
        }
        document.getElementById('toastTitle').textContent = title;
        document.getElementById('toastMessage').textContent = message;

        const toastHeader = this.toastEl.querySelector('.toast-header');
        if (toastHeader) {
            if (isError) {
                toastHeader.classList.remove('bg-success', 'text-white');
                toastHeader.classList.add('bg-danger', 'text-white');
            } else {
                toastHeader.classList.remove('bg-danger', 'text-white');
                toastHeader.classList.add('bg-success', 'text-white');
            }
        }
        this.toast.show();
        console.log(`TradingApp: Showing toast - Title: "${title}", Message: "${message}", Error: ${isError}`);
    }

    // This method is called by dashboard.js when a user clicks 'Trade' on a crypto card
    openTradeModal(symbol, price, availableAmount) {
        console.log(`TradingApp: openTradeModal called for Symbol: ${symbol}, Price: ${price}, Available: ${availableAmount}`);
        this.currentSymbol = symbol;
        this.currentPrice = price;
        this.availableAmount = availableAmount || 0;

        const cryptoUnit = document.getElementById('cryptoUnit');
        const availableAmountEl = document.getElementById('availableAmount');
        const tradeAmountInput = document.getElementById('tradeAmount');
        const estimatedTotalEl = document.getElementById('estimatedTotal');
        const tradeTypeSelect = document.getElementById('tradeType');
        const tradeModalLabel = document.getElementById('tradeModalLabel');

        if (cryptoUnit) cryptoUnit.textContent = symbol; else console.warn('TradingApp: cryptoUnit not found in modal.');
        if (availableAmountEl) availableAmountEl.textContent = `${parseFloat(this.availableAmount).toFixed(8)} ${symbol}`; else console.warn('TradingApp: availableAmountEl not found in modal.');
        if (tradeAmountInput) tradeAmountInput.value = ''; else console.warn('TradingApp: tradeAmountInput not found in modal.');
        if (estimatedTotalEl) estimatedTotalEl.textContent = '-'; else console.warn('TradingApp: estimatedTotalEl not found in modal.');
        if (tradeTypeSelect) tradeTypeSelect.value = 'buy'; else console.warn('TradingApp: tradeTypeSelect not found in modal.');
        if (tradeModalLabel) tradeModalLabel.textContent = `Trade ${symbol}`; // Update modal title dynamically

        // Get the modal's specific Buy and Sell buttons
        const modalBuyButton = document.getElementById('buyButtonModal');
        const modalSellButton = document.getElementById('sellButtonModal');

        console.log('TradingApp: Modal Buy Button Element:', modalBuyButton);
        console.log('TradingApp: Modal Sell Button Element:', modalSellButton);

        // Ensure no multiple event listeners are attached if modal opens multiple times
        if (modalBuyButton) {
            if (this._boundPlaceBuyOrder) {
                modalBuyButton.removeEventListener('click', this._boundPlaceBuyOrder);
            }
            this._boundPlaceBuyOrder = () => this.placeOrder('buy', modalBuyButton);
            modalBuyButton.addEventListener('click', this._boundPlaceBuyOrder);
            console.log('TradingApp: Attached listener to modalBuyButton.');
        } else { console.warn('TradingApp: modalBuyButton not found. Check HTML.'); }

        if (modalSellButton) {
            if (this._boundPlaceSellOrder) {
                modalSellButton.removeEventListener('click', this._boundPlaceSellOrder);
            }
            this._boundPlaceSellOrder = () => this.placeOrder('sell', modalSellButton);
            modalSellButton.addEventListener('click', this._boundPlaceSellOrder);
            console.log('TradingApp: Attached listener to modalSellButton.');
        } else { console.warn('TradingApp: modalSellButton not found. Check HTML.'); }


        // Add a listener to the trade type dropdown within the modal for dynamic button enabling/disabling
        if (tradeTypeSelect) {
            if (this._boundUpdateEstimateOnChange) { // Remove existing listener to prevent duplicates on modal re-open
                tradeTypeSelect.removeEventListener('change', this._boundUpdateEstimateOnChange);
            }
            this._boundUpdateEstimateOnChange = () => this.updateTradeEstimate();
            tradeTypeSelect.addEventListener('change', this._boundUpdateEstimateOnChange);
        }

        // Initial call to update estimate and button states when modal opens
        this.updateTradeEstimate();


        // Show modal
        const modalElement = document.getElementById('tradeModal');
        if (modalElement) {
            const modal = new bootstrap.Modal(modalElement);
            modal.show();
            console.log('TradingApp: Attempting to show tradeModal.');
        } else {
            console.error('TradingApp: Trade modal element with ID "tradeModal" not found.');
        }
    }

    updateTradeEstimate() {
        console.log('TradingApp: updateTradeEstimate called');
        const amountInput = document.getElementById('tradeAmount');
        const amount = parseFloat(amountInput?.value) || 0;
        const tradeTypeSelect = document.getElementById('tradeType');
        const isBuy = tradeTypeSelect?.value === 'buy';

        const estimatedTotalElement = document.getElementById('estimatedTotal');
        const tradeInfoElement = document.getElementById('tradeInfo');
        const buyButtonModal = document.getElementById('buyButtonModal');
        const sellButtonModal = document.getElementById('sellButtonModal');

        if (!estimatedTotalElement || !tradeInfoElement || !buyButtonModal || !sellButtonModal) {
            console.error('TradingApp: Missing elements for trade estimate update. Check HTML for estimatedTotal, tradeInfo, buyButtonModal, sellButtonModal');
            return;
        }

        // Disable both buttons initially until a valid amount is entered or based on action
        buyButtonModal.disabled = true;
        sellButtonModal.disabled = true;
        estimatedTotalElement.textContent = '-';
        tradeInfoElement.textContent = 'Enter amount to see estimated total'; // Reset info text


        if (amount <= 0) {
            // Keep buttons disabled and info text as default
            return;
        }

        const total = amount * this.currentPrice;
        estimatedTotalElement.textContent = formatPrice(total);

        if (isBuy) {
            tradeInfoElement.textContent =
                `You will spend ${formatPrice(total)} to buy ${amount} ${this.currentSymbol}`;
            buyButtonModal.disabled = false; // Enable Buy button
            sellButtonModal.disabled = true; // Disable Sell button
        } else { // Sell
            if (amount > this.availableAmount) {
                tradeInfoElement.textContent =
                    `Insufficient ${this.currentSymbol} balance. Available: ${parseFloat(this.availableAmount).toFixed(8)} ${this.currentSymbol}`;
                // Keep both buttons disabled due to insufficient funds for sell
                buyButtonModal.disabled = true;
                sellButtonModal.disabled = true;
                return; // Exit as insufficient funds
            }
            tradeInfoElement.textContent =
                `You will receive ${formatPrice(total)} for selling ${amount} ${this.currentSymbol}`;
            buyButtonModal.disabled = true; // Disable Buy button
            sellButtonModal.disabled = false; // Enable Sell button
        }
        console.log(`TradingApp: Trade estimate updated. Amount: ${amount}, Total: ${total}, IsBuy: ${isBuy}`);
    }

    async placeOrder(tradeType, clickedButton) { // Pass the clicked button element
        console.log(`TradingApp: placeOrder called. Type: ${tradeType}, Button:`, clickedButton);
        const amountInput = document.getElementById('tradeAmount');
        const amount = parseFloat(amountInput?.value);
        const isBuy = tradeType === 'buy';

        if (!amount || amount <= 0) {
            this.showToast('Error', 'Please enter a valid amount', true);
            console.error('TradingApp: Invalid amount entered for order.');
            return;
        }

        // Disable the specific clicked button and add a spinner
        if (clickedButton) {
            clickedButton.disabled = true;
            const spinner = document.createElement('span');
            spinner.className = 'spinner-border spinner-border-sm me-2';
            spinner.setAttribute('role', 'status');
            spinner.setAttribute('aria-hidden', 'true');
            clickedButton.prepend(spinner);
            const originalText = clickedButton.textContent;
            clickedButton.setAttribute('data-original-text', originalText); // Store original text
            clickedButton.textContent = 'Processing...';
            console.log('TradingApp: Button disabled and spinner added.');
        }

        try {
            console.log(`TradingApp: Attempting API call to /api/trade/${isBuy ? 'buy' : 'sell'} with data:`, {
                userId: this.userId,
                symbol: this.currentSymbol,
                quantity: amount
            });
            // Make API call to place the order
            const response = await fetch(`/api/trade/${isBuy ? 'buy' : 'sell'}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    userId: this.userId,
                    symbol: this.currentSymbol,
                    quantity: amount
                })
            });

            const result = await response.json();
            console.log('TradingApp: API response received:', result);

            if (!response.ok) {
                throw new Error(result.message || 'Failed to place order');
            }

            // Show success message
            this.showToast(
                'Order Placed',
                `Successfully ${isBuy ? 'bought' : 'sold'} ${amount} ${this.currentSymbol} for ${formatPrice(amount * this.currentPrice)}`,
                false
            );

            // Close the modal
            const modalElement = document.getElementById('tradeModal');
            if (modalElement) {
                const modal = bootstrap.Modal.getInstance(modalElement) || new bootstrap.Modal(modalElement);
                modal.hide();
                console.log('TradingApp: Trade modal hidden.');
                // Reset the form
                if (amountInput) amountInput.value = '';
            }

            // Refresh transaction history
            this.loadTransactionHistory();

            // Refresh account data and holdings data by calling functions from dashboard.js
            if (typeof window.loadAccountData === 'function') {
                window.loadAccountData();
                console.log('TradingApp: window.loadAccountData called.');
            }
            if (typeof window.loadUserHoldings === 'function') {
                window.loadUserHoldings();
                console.log('TradingApp: window.loadUserHoldings called.');
            }
        } catch (error) {
            console.error('TradingApp: Error placing order:', error);
            this.showToast('Error', error.message || 'Failed to place order', true);
        } finally {
            // Re-enable the specific button and reset its state
            if (clickedButton) {
                clickedButton.disabled = false;
                const spinner = clickedButton.querySelector('.spinner-border');
                if (spinner) {
                    spinner.remove();
                }
                const originalText = clickedButton.getAttribute('data-original-text');
                if (originalText) {
                    clickedButton.textContent = originalText;
                }
                console.log('TradingApp: Button re-enabled and reset.');
            }
        }
    }

    async loadTransactionHistory() {
        console.log('TradingApp: loadTransactionHistory called');
        const tbody = document.getElementById('transactionHistoryBody');
        const paginationInfo = document.getElementById('paginationInfo');
        const prevPageBtn = document.getElementById('prevPage');
        const nextPageBtn = document.getElementById('nextPage');

        if (!tbody) {
            console.warn("TradingApp: Transaction history body (ID: transactionHistoryBody) not found. This might be on a different page (e.g., account.html).");
            // If the tbody is not found, it means this section might be on another page.
            // We can return here without causing errors.
            return;
        }

        // Show loading state
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="text-center py-4">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">Loading...</span>
                    </div>
                </td>
            </tr>
        `;

        try {
            console.log(`TradingApp: Fetching transaction history for user ${this.userId}, page ${this.currentPage}, size ${this.pageSize}`);
            const response = await fetch(
                `/api/trade/history/${this.userId}?page=${this.currentPage}&size=${this.pageSize}`
            );

            if (!response.ok) {
                throw new Error('Failed to load transaction history');
            }

            const data = await response.json();
            console.log('TradingApp: Transaction history data received:', data);

            // Update UI with transaction data
            if (data.transactions && data.transactions.length > 0) {
                tbody.innerHTML = data.transactions.map(transaction => {
                    const isBuy = transaction.type === 'BUY';
                    const date = new Date(transaction.timestamp);

                    return `
                        <tr>
                            <td>${date.toLocaleString()}</td>
                            <td>
                                <span class="badge ${isBuy ? 'bg-success' : 'bg-danger'}">
                                    ${isBuy ? 'Buy' : 'Sell'}
                                </span>
                            </td>
                            <td>${transaction.symbol}</td>
                            <td>${parseFloat(transaction.quantity).toFixed(8)}</td>
                            <td>${formatPrice(transaction.price)}</td>
                            <td>${formatPrice(transaction.totalAmount)}</td>
                            <td>
                                <span class="badge bg-success">
                                    ${transaction.status || 'Completed'}
                                </span>
                            </td>
                        </tr>
                    `;
                }).join('');
            } else {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="7" class="text-center py-4">
                            No transactions found
                        </td>
                    </tr>
                `;
            }

            // Update pagination
            const totalPages = data.totalPages || 1;
            if (paginationInfo) paginationInfo.textContent = `Showing ${data.transactions?.length || 0} of ${data.totalItems || 0} transactions`;

            // Update pagination buttons
            if (prevPageBtn && prevPageBtn.parentElement) prevPageBtn.parentElement.classList.toggle('disabled', this.currentPage <= 1);
            if (nextPageBtn && nextPageBtn.parentElement) nextPageBtn.parentElement.classList.toggle('disabled', this.currentPage >= totalPages);

        } catch (error) {
            console.error('TradingApp: Error loading transaction history:', error);
            tbody.innerHTML = `
                <tr>
                    <td colspan="7" class="text-center text-danger py-4">
                        Error loading transaction history: ${error.message || 'Unknown error'}
                    </td>
                </tr>
            `;
        }
    }
}

// Initialize the trading app when the DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    console.log('DOMContentLoaded: Initializing TradingApp...');
    window.tradingApp = new TradingApp();

    // Expose openTradeModal globally for dashboard.js to call
    // This wrapper ensures consistent symbol/price/availableAmount parameters
    window.openTradeModal = (symbol, price, availableAmount) => {
        console.log('Global window.openTradeModal wrapper called from dashboard.js with:', symbol, price, availableAmount);
        window.tradingApp.openTradeModal(symbol, price, availableAmount);
    };
});