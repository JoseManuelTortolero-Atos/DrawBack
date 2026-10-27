INSERT INTO external_competition (code, name, colour)
SELECT refs.code, refs.name, refs.colour FROM (VALUES
    ('D1', 'Botola Pro D1', 'WHITE'),
    ('D2', 'Botola D2', 'WHITE'),
    ('CT', 'Coupe du Trone', 'YELLOW'),
    ('CL', 'CAF Champions League', 'RED'),
    ('CC', 'CAF Confederation Cup', 'RED'),
    ('ASC', 'Arab Super Cup', 'AQUA'),
    ('UAFA', 'UAFA Club Cup', 'AQUA'),
    ('CHAN', 'CHAN', 'GREY_25_PERCENT'),
    ('CLHOME', 'CAF Champions League (home)', 'RED'),
    ('CCHOME', 'CAF Confederation Cup (home)', 'RED'),
    ('FIFA', 'FIFA window', 'GREY_25_PERCENT'),
    ('AC', 'Arab Cup', 'GREY_25_PERCENT'),
    ('WC', 'World Cup', 'GREY_25_PERCENT'),
    ('UAFAHOME', 'UAFA Club Cup (home)', 'AQUA'),
    ('ASLHOME', 'Arab Super League (home)', 'GREY_25_PERCENT'),
    ('ASL', 'Arab Super League', 'GREY_25_PERCENT'),
    ('CAN', 'AFCON', 'GREY_25_PERCENT'),
    ('F1', 'CNPFF D1', 'GREY_25_PERCENT'),
    ('F2', 'CNPFF D2', 'WHITE')
) AS refs(code, name, colour)
WHERE NOT EXISTS (SELECT 1 FROM external_competition existing WHERE existing.code = refs.code);

INSERT INTO rule_type_catalog (
    federation_code, rule_type, technical_code, functional_name, functional_description, phase_code,
    category_code, parameter_schema, support_status, engine_class, engine_version, editable_definition,
    enabled, sort_order
)
SELECT f.code, base.rule_type, base.technical_code, base.functional_name, base.functional_description,
       base.phase_code, base.category_code, base.parameter_schema, base.support_status, base.engine_class,
       base.engine_version, base.editable_definition, base.enabled, base.sort_order
FROM federation f CROSS JOIN rule_type_catalog base
WHERE base.federation_code = 'FRMF'
  AND NOT EXISTS (
    SELECT 1 FROM rule_type_catalog existing
    WHERE existing.federation_code = f.code AND existing.rule_type = base.rule_type
  );

INSERT INTO rule_type_allowed_scope (federation_code, rule_type, scope_type)
SELECT f.code, base.rule_type, base.scope_type FROM federation f CROSS JOIN rule_type_allowed_scope base
WHERE base.federation_code = 'FRMF'
  AND NOT EXISTS (
    SELECT 1 FROM rule_type_allowed_scope existing
    WHERE existing.federation_code = f.code AND existing.rule_type = base.rule_type AND existing.scope_type = base.scope_type
  );
