SET search_path = public, pg_catalog;

--
-- parameters table
--
CREATE TABLE parameters (
    id uuid NOT NULL DEFAULT uuid_generate_v1(),
    parameter_group_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    label text,
    is_visible boolean DEFAULT true,
    ordering integer,
    parameter_type uuid NOT NULL,
    required boolean DEFAULT false,
    file_parameter_id uuid,
    omit_if_blank boolean DEFAULT true
);

