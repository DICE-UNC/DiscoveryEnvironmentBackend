SET search_path = public, pg_catalog;

--
-- validation_rule_arguments table
--
CREATE TABLE validation_rule_arguments (
    id uuid NOT NULL,
    rule_id uuid NOT NULL,
    argument_value text
);