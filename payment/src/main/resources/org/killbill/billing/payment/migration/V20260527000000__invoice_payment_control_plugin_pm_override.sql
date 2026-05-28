DROP TABLE IF EXISTS invoice_payment_control_plugin_pm_override;
CREATE TABLE invoice_payment_control_plugin_pm_override (
    record_id serial unique,
    account_id varchar(36) NOT NULL,
    payment_object_type varchar(32) NOT NULL,
    payment_object_id varchar(36) NOT NULL,
    payment_method_id varchar(36) NOT NULL,
    is_active boolean default true,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX invoice_payment_control_plugin_pm_override_lookup ON invoice_payment_control_plugin_pm_override(account_id, payment_object_type, payment_object_id, is_active);
