CREATE TABLE users (
    id            UUID PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(180) NOT NULL UNIQUE,
    document      VARCHAR(14)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE wallets (
    id         UUID PRIMARY KEY,
    user_id    UUID           NOT NULL UNIQUE REFERENCES users (id),
    balance    NUMERIC(19, 2) NOT NULL DEFAULT 0.00 CHECK (balance >= 0),
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE transactions (
    id              UUID PRIMARY KEY,
    idempotency_key VARCHAR(100)   NOT NULL UNIQUE,
    payer_wallet_id UUID           REFERENCES wallets (id),
    payee_wallet_id UUID           NOT NULL REFERENCES wallets (id),
    amount          NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    type            VARCHAR(20)    NOT NULL CHECK (type IN ('DEPOSIT', 'TRANSFER')),
    status          VARCHAR(20)    NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    description     VARCHAR(255),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE ledger_entries (
    id             UUID PRIMARY KEY,
    transaction_id UUID           NOT NULL REFERENCES transactions (id),
    wallet_id      UUID           NOT NULL REFERENCES wallets (id),
    direction      VARCHAR(10)    NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount         NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    balance_after  NUMERIC(19, 2) NOT NULL CHECK (balance_after >= 0),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_payer ON transactions (payer_wallet_id);
CREATE INDEX idx_transactions_payee ON transactions (payee_wallet_id);
CREATE INDEX idx_ledger_wallet_created ON ledger_entries (wallet_id, created_at DESC);
