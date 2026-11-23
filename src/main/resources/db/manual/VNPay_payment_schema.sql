-- Apply once after the original MySQL schema. Historical transactions keep their original values.
ALTER TABLE giao_dich_thanh_toan
  ADD COLUMN txn_ref VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin UNIQUE,
  ADD COLUMN request_key CHAR(36),
  ADD COLUMN invoice_key CHAR(64),
  ADD COLUMN merchant VARCHAR(32),
  ADD COLUMN currency CHAR(3),
  ADD COLUMN created_at DATETIME(6),
  ADD COLUMN expires_at DATETIME(6),
  ADD UNIQUE KEY payment_request (MaDonThue, request_key);

CREATE TABLE payment_fee_snapshot (
  txn_ref VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  fee_id INT NOT NULL,
  amount DECIMAL(15,2) NOT NULL,
  fee_type VARCHAR(255) NOT NULL,
  reason TEXT NOT NULL,
  PRIMARY KEY (txn_ref, fee_id),
  FOREIGN KEY (txn_ref) REFERENCES giao_dich_thanh_toan(txn_ref),
  FOREIGN KEY (fee_id) REFERENCES phi_phat_sinh(MaPhi)
) ENGINE=InnoDB;

-- Shared by payment methods. Each rental balance and each individual fee has one winning attempt.
CREATE TABLE payment_allocation (
  obligation VARCHAR(64) PRIMARY KEY,
  transaction_id INT NOT NULL,
  FOREIGN KEY (transaction_id) REFERENCES giao_dich_thanh_toan(MaGD)
) ENGINE=InnoDB;

-- A verified notification may reference an unknown attempt. Preserve it for reconciliation.
CREATE TABLE payment_ipn (
  event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  signature CHAR(128) CHARACTER SET ascii NOT NULL UNIQUE,
  merchant VARCHAR(32) NOT NULL,
  ref VARCHAR(100) NOT NULL,
  provider_id VARCHAR(32),
  received_amount DECIMAL(20,2) NOT NULL,
  currency VARCHAR(8) NOT NULL,
  provider_date CHAR(14),
  response_code CHAR(2) NOT NULL,
  transaction_status CHAR(2) NOT NULL,
  received_at DATETIME(6) NOT NULL,
  disposition VARCHAR(64) NOT NULL,
  KEY payment_review (disposition, received_at)
) ENGINE=InnoDB;

CREATE TABLE payment_receipt (
  method VARCHAR(16) NOT NULL,
  merchant VARCHAR(32) NOT NULL,
  provider_id VARCHAR(32) NOT NULL,
  signature CHAR(128) CHARACTER SET ascii NOT NULL,
  PRIMARY KEY (method, merchant, provider_id),
  FOREIGN KEY (signature) REFERENCES payment_ipn(signature)
) ENGINE=InnoDB;

CREATE TABLE payment_review_note (
  note_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id BIGINT NOT NULL,
  actor_id INT NOT NULL,
  recorded_at DATETIME(6) NOT NULL,
  note VARCHAR(1000) NOT NULL,
  FOREIGN KEY (event_id) REFERENCES payment_ipn(event_id),
  FOREIGN KEY (actor_id) REFERENCES tai_khoan(MaTK)
) ENGINE=InnoDB;

-- Cash movements for allocated payments, dated independently for receipts and completed refunds.
CREATE VIEW payment_allocated_cashflow AS
SELECT g.MaGD, g.MaDonThue, g.LoaiThanhToan, g.ThoiGianGiaoDich AS recorded_at, g.SoTien AS amount
FROM giao_dich_thanh_toan g
WHERE g.TrangThaiGD='SUCCESS' AND EXISTS (SELECT 1 FROM payment_allocation a WHERE a.transaction_id=g.MaGD)
UNION ALL
SELECT g.MaGD, g.MaDonThue, g.LoaiThanhToan, h.NgayHoanTat, -h.SoTienHoan
FROM hoan_tien h JOIN giao_dich_thanh_toan g ON g.MaGD=h.MaGDGoc AND g.MaDonThue=h.MaDonThue
WHERE h.NgayHoanTat IS NOT NULL AND g.TrangThaiGD='SUCCESS'
  AND EXISTS (SELECT 1 FROM payment_allocation a WHERE a.transaction_id=g.MaGD);
