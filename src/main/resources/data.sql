-- Insert default user with initial balance
INSERT IGNORE INTO users (username, balance) VALUES ('default_user', 10000.00);

-- Insert some initial cryptocurrencies if they don't exist
INSERT IGNORE INTO cryptocurrencies (symbol, name, kraken_pair_name, current_price) VALUES 
('BTC', 'Bitcoin', 'XBTUSDT', 0.00),
('ETH', 'Ethereum', 'ETHUSDT', 0.00),
('SOL', 'Solana', 'SOLUSDT', 0.00),
('XRP', 'Ripple', 'XRPUSDT', 0.00),
('ADA', 'Cardano', 'ADAUSDT', 0.00),
('DOT', 'Polkadot', 'DOTUSDT', 0.00),
('DOGE', 'Dogecoin', 'XDGUSDT', 0.00),
('SHIB', 'Shiba Inu', 'SHIBUSDT', 0.00),
('MATIC', 'Polygon', 'MATICUSDT', 0.00),
('AVAX', 'Avalanche', 'AVAXUSDT', 0.00);
