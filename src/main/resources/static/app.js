// Global variables
let cryptoData = [];
let userHoldings = [];
let transactions = [];
let currentUser = null;

// DOM elements
const cryptoTable = document.getElementById('crypto-table');
const holdingsTable = document.getElementById('holdings-table');
const transactionsTable = document.getElementById('transactions-table');
const accountBalanceEl = document.getElementById('account-balance');
const resetAccountBtn = document.getElementById('reset-account');

// Format currency
const formatCurrency = (amount) => {
    return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD'
    }).format(amount);
};

// Load current user
const loadCurrentUser = async () => {
    try {
        const response = await fetch('/api/users/current');
        if (!response.ok) throw new Error('Failed to fetch current user');

        currentUser = await response.json();
        // Update balance display
        accountBalanceEl.textContent = formatCurrency(currentUser.balance);
    } catch (error) {
        console.error('Error loading current user:', error);
        // Set a default user if API fails
        currentUser = { id: 1, username: 'default_user', balance: 10000.00 };
        accountBalanceEl.textContent = formatCurrency(currentUser.balance);
    }
};

// Load cryptocurrencies from API
const loadCryptocurrencies = async () => {
    try {
        const response = await fetch('/api/crypto');
        if (!response.ok) throw new Error('Failed to fetch cryptocurrencies');

        cryptoData = await response.json();
        renderCryptoTable();

        // Set up polling for real-time updates (every 5 seconds)
        setTimeout(loadCryptocurrencies, 5000);
    } catch (error) {
        console.error('Error loading cryptocurrencies:', error);
        cryptoTable.innerHTML = `<tr><td colspan="4" class="text-center text-danger">Failed to load cryptocurrencies</td></tr>`;
    }
};

// Render crypto table
const renderCryptoTable = () => {
    if (cryptoData.length === 0) {
        cryptoTable.innerHTML = `<tr><td colspan="4" class="text-center">No cryptocurrencies available</td></tr>`;
        return;
    }

    cryptoTable.innerHTML = '';

    cryptoData.forEach(crypto => {
        const row = document.createElement('tr');
        row.className = 'crypto-row';
        row.innerHTML = `
            <td>${crypto.symbol}</td>
            <td>${crypto.name}</td>
            <td class="crypto-price">${formatCurrency(crypto.currentPrice)}</td>
            <td>
                <button class="btn btn-sm buy-btn me-1" data-crypto-id="${crypto.id}" data-action="buy">Buy</button>
                <button class="btn btn-sm sell-btn" data-crypto-id="${crypto.id}" data-action="sell">Sell</button>
            </td>
        `;
        cryptoTable.appendChild(row);
    });

    // Add event listeners to buy/sell buttons
    document.querySelectorAll('.buy-btn, .sell-btn').forEach(button => {
        button.addEventListener('click', (e) => {
            const cryptoId = e.target.getAttribute('data-crypto-id');
            const action = e.target.getAttribute('data-action');
            const crypto = cryptoData.find(c => c.id == cryptoId);

            if (crypto) {
                openTradeModal(crypto, action);
            }
        });
    });
};

// Reset user account
resetAccountBtn.addEventListener('click', async () => {
    if (!currentUser) return;

    try {
        const response = await fetch(`/api/users/${currentUser.id}/reset`, {
            method: 'PUT'
        });

        if (!response.ok) throw new Error('Failed to reset account');

        // Reload user data
        await loadCurrentUser();
        alert('Account has been reset successfully!');
    } catch (error) {
        console.error('Error resetting account:', error);
        alert('Failed to reset account. Please try again.');
    }
});

// Open trade modal
const openTradeModal = (crypto, action) => {
    const tradeModal = new bootstrap.Modal(document.getElementById('trade-modal'));

    // Set modal values
    document.getElementById('crypto-id').value = crypto.id;
    document.getElementById('trade-type').value = action.toUpperCase();
    document.getElementById('crypto-name-display').textContent = `${crypto.symbol} - ${crypto.name}`;
    document.getElementById('crypto-price-display').textContent = formatCurrency(crypto.currentPrice);

    // Clear previous values
    document.getElementById('quantity').value = '';
    document.getElementById('total-cost-display').textContent = '$0.00';
    document.getElementById('trade-error').classList.add('d-none');

    // Update modal title based on action
    document.getElementById('tradeModalLabel').textContent = 
        action === 'buy' ? 'Buy Cryptocurrency' : 'Sell Cryptocurrency';

    // Update confirm button based on action
    const confirmBtn = document.getElementById('confirm-trade');
    confirmBtn.textContent = action === 'buy' ? 'Buy' : 'Sell';
    confirmBtn.className = action === 'buy' ? 'btn btn-success' : 'btn btn-danger';

    // Calculate total cost on quantity change
    document.getElementById('quantity').addEventListener('input', (e) => {
        const quantity = parseFloat(e.target.value) || 0;
        const totalCost = quantity * crypto.currentPrice;
        document.getElementById('total-cost-display').textContent = formatCurrency(totalCost);
    });

    tradeModal.show();
};

// Initialize application
document.addEventListener('DOMContentLoaded', () => {
    loadCurrentUser();
    loadCryptocurrencies();
});
