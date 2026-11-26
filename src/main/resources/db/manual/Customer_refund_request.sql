-- Optional customer refund metadata. Independent of the VNPay payment schema; historical rows retain NULL metadata.
ALTER TABLE hoan_tien
  ADD COLUMN customer_request_order INT NULL UNIQUE,
  ADD COLUMN requested_by INT NULL,
  ADD COLUMN bank_name VARCHAR(100) NULL,
  ADD COLUMN account_number VARCHAR(34) NULL,
  ADD COLUMN account_holder VARCHAR(120) NULL,
  ADD COLUMN customer_note VARCHAR(1000) NULL,
  ADD COLUMN transfer_reference VARCHAR(100) NULL UNIQUE,
  ADD COLUMN transfer_evidence VARCHAR(500) NULL,
  ADD COLUMN recorded_by INT NULL,
  ADD CONSTRAINT fk_refund_request_actor FOREIGN KEY (requested_by) REFERENCES tai_khoan(MaTK),
  ADD CONSTRAINT fk_refund_record_actor FOREIGN KEY (recorded_by) REFERENCES tai_khoan(MaTK),
  ADD CONSTRAINT ck_refund_request_order CHECK (customer_request_order IS NULL OR customer_request_order=MaDonThue);
