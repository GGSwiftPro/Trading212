// Trading functionality
class TradingApp {
    constructor() {
        this.currentPage = 1;
        this.pageSize = 10;
        this.userId = 1; // Default user ID, in a real app this would come from authentication
        this.currentSymbol = null;
        this.currentPrice = 0;
        this.availableAmount = 0;
        
        // Initialize UI components
        this.initEventListeners();
        this.initToast();
        
        // Load initial data
        this.loadTransactionHistory();
    }
    
    initEventListeners() {
        // Trade form
        document.getElementById('tradeAmount').addEventListener('input', (e) => this.updateTradeEstimate());
        document.getElementById('tradeType').addEventListener('change', () => this.updateTradeEstimate());
        document.getElementById('placeOrderBtn').addEventListener('click', () => this.placeOrder());
        
        // Transaction history pagination
        document.getElementById('prevPage').addEventListener('click', (e) => {
            e.preventDefault();
            if (this.currentPage > 1) {
                this.currentPage--;
                this.loadTransactionHistory();
            }
        });
        
        document.getElementById('nextPage').addEventListener('click', (e) => {
            e.preventDefault();
            this.currentPage++;
            this.loadTransactionHistory();
        });
        
        // Page size selector
        document.getElementById('pageSizeSelect').addEventListener('change', (e) => {
            this.pageSize = parseInt(e.target.value);
            this.currentPage = 1; // Reset to first page
            this.loadTransactionHistory();
        });
        
        // Refresh button
        document.getElementById('refreshHistoryBtn').addEventListener('click', () => {
            this.loadTransactionHistory();
        });
    }
    
    initToast() {
        this.toastEl = document.getElementById('orderToast');
        this.toast = new bootstrap.Toast(this.toastEl, {
            autohide: true,
            delay: 5000
        });
    }
    
    showToast(title, message, isError = false) {
        document.getElementById('toastTitle').textContent = title;
        document.getElementById('toastMessage').textContent = message;
        
        const toastHeader = this.toastEl.querySelector('.toast-header');
        if (isError) {
            toastHeader.classList.remove('bg-success', 'text-white');
            toastHeader.classList.add('bg-danger', 'text-white');
        } else {
            toastHeader.classList.remove('bg-danger', 'text-white');
            toastHeader.classList.add('bg-success', 'text-white');
        }
        
        this.toast.show();
    }
    
    openTradeModal(symbol, price, availableAmount) {
        this.currentSymbol = symbol;
        this.currentPrice = price;
        this.availableAmount = availableAmount || 0;
        
        document.getElementById('cryptoUnit').textContent = symbol;
        document.getElementById('availableAmount').textContent = 
            `${parseFloat(this.availableAmount).toFixed(8)} ${symbol}`;
        
        // Reset form
        document.getElementById('tradeAmount').value = '';
        document.getElementById('estimatedTotal').textContent = '-';
        document.getElementById('tradeType').value = 'buy';
        
        // Show modal
        const modal = new bootstrap.Modal(document.getElementById('tradeModal'));
        modal.show();
    }
    
    updateTradeEstimate() {
        const amount = parseFloat(document.getElementById('tradeAmount').value) || 0;
        const isBuy = document.getElementById('tradeType').value === 'buy';
        
        if (amount <= 0) {
            document.getElementById('estimatedTotal').textContent = '-';
            document.getElementById('tradeInfo').textContent = 'Enter amount to see estimated total';
            return;
        }
        
        const total = amount * this.currentPrice;
        document.getElementById('estimatedTotal').textContent = formatPrice(total);
        
        if (isBuy) {
            document.getElementById('tradeInfo').textContent = 
                `You will spend ${formatPrice(total)} to buy ${amount} ${this.currentSymbol}`;
        } else {
            if (amount > this.availableAmount) {
                document.getElementById('tradeInfo').textContent = 
                    `Insufficient ${this.currentSymbol} balance. Available: ${this.availableAmount}`;
                document.getElementById('placeOrderBtn').disabled = true;
                return;
            }
            document.getElementById('tradeInfo').textContent = 
                `You will receive ${formatPrice(total)} for selling ${amount} ${this.currentSymbol}`;
        }
        
        document.getElementById('placeOrderBtn').disabled = false;
    }
    
    async placeOrder() {
        const amount = parseFloat(document.getElementById('tradeAmount').value);
        const isBuy = document.getElementById('tradeType').value === 'buy';
        
        if (!amount || amount <= 0) {
            this.showToast('Error', 'Please enter a valid amount', true);
            return;
        }
        
        // Disable button to prevent multiple clicks
        const button = document.getElementById('placeOrderBtn');
        button.disabled = true;
        button.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Processing...';
        
        try {
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
            
            if (!response.ok) {
                throw new Error(result.message || 'Failed to place order');
            }
            
            // Show success message
            this.showToast(
                'Order Placed', 
                `Successfully ${isBuy ? 'bought' : 'sold'} ${amount} ${this.currentSymbol} for ${formatPrice(amount * this.currentPrice)}`
            );
            
            // Close modal
            const modal = bootstrap.Modal.getInstance(document.getElementById('tradeModal'));
            modal.hide();
            
            // Refresh transaction history
            this.loadTransactionHistory();
            
        } catch (error) {
            console.error('Error placing order:', error);
            this.showToast('Error', error.message || 'Failed to place order', true);
        } finally {
            // Re-enable button
            button.disabled = false;
            button.textContent = 'Place Order';
        }
    }
    
    async loadTransactionHistory() {
        const tbody = document.getElementById('transactionHistoryBody');
        const paginationInfo = document.getElementById('paginationInfo');
        const prevPageBtn = document.getElementById('prevPage');
        const nextPageBtn = document.getElementById('nextPage');
        
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
            const response = await fetch(
                `/api/trade/history/${this.userId}?page=${this.currentPage}&size=${this.pageSize}`
            );
            
            if (!response.ok) {
                throw new Error('Failed to load transaction history');
            }
            
            const data = await response.json();
            
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
            paginationInfo.textContent = `Showing ${data.transactions?.length || 0} of ${data.totalItems || 0} transactions`;
            
            // Update pagination buttons
            prevPageBtn.parentElement.classList.toggle('disabled', this.currentPage <= 1);
            nextPageBtn.parentElement.classList.toggle('disabled', this.currentPage >= totalPages);
            
        } catch (error) {
            console.error('Error loading transaction history:', error);
            tbody.innerHTML = `
                <tr>
                    <td colspan="7" class="text-center text-danger py-4">
                        Error loading transaction history
                    </td>
                </tr>
            `;
        }
    }
}

// Initialize the trading app when the DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    window.tradingApp = new TradingApp();
    
    // Update the openTradeModal function to use the trading app instance
    window.openTradeModal = (symbol, price, availableAmount) => {
        window.tradingApp.openTradeModal(symbol, price, availableAmount);
    };
});
