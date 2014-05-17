SET search_path = public, pg_catalog;

--
-- Renames the existing property_type table to parameter_types and adds updated columns.
-- cols to drop: hid, value_type_id_v187
--
ALTER TABLE property_type RENAME TO parameter_types;
ALTER TABLE ONLY parameter_types ALTER COLUMN id TYPE UUID USING
 CAST(regexp_replace(id, 'pt(.*)', '\1') AS UUID);
ALTER TABLE ONLY parameter_types RENAME COLUMN value_type_id TO value_type_id_v187;
ALTER TABLE ONLY parameter_types ADD COLUMN value_type_id UUID;
