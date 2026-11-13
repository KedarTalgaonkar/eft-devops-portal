-- dev profile only: sample inventory with FAKE hosts (example.internal), so every page has data
-- before the real Excel is imported. Safe to re-run; nothing is overwritten.
insert into application (name, client, owner) values
    ('Prepaid QR',   'CBC',     'Samar'),
    ('Prepaid Axis', 'Axis',    'Deepak'),
    ('Recon',        'Euronet', 'Ganesh'),
    ('IMPS Portal',  'Sample Bank', 'Narsing'),
    ('E_Mandate',    'Sample Co-op Bank', 'Shweta')
on conflict (name, client) do nothing;

insert into deployment (application_id, environment, context_name, base_url, tomcat_version, java_version, tomcat_admin_done, build_xml_done)
select a.id, v.env, v.ctx, v.url, v.tomcat, v.java, true, v.build_xml
from (values
    ('Prepaid QR',   'CBC',     'DEVELOP', 'qrapi',        'http://dev-qr.example.internal:9091',   '9.0.98', '8u351', true),
    ('Prepaid QR',   'CBC',     'SIT',     'SITCBCQRAPI',  'http://sit-qr.example.internal:9091',   '9.0.98', '8u351', true),
    ('Prepaid QR',   'CBC',     'UAT',     'UATCBCQRAPI',  'http://uat-qr.example.internal:9085',   '9.0.98', '8u351', true),
    ('Prepaid Axis', 'Axis',    'DEVELOP', 'axisprepaid',  'http://dev-axis.example.internal:10083', '9.0.63', '8u333', true),
    ('Prepaid Axis', 'Axis',    'SIT',     'axisprepaid',  'http://sit-axis.example.internal:10083', '9.0.63', '8u333', false),
    ('Recon',        'Euronet', 'SIT',     'recon',        'http://sit-recon.example.internal:8080', '9.0.73', '17.0.12', true),
    ('Recon',        'Euronet', 'UAT',     'recon',        'http://uat-recon.example.internal:8080', '9.0.73', '17.0.12', true),
    ('IMPS Portal',  'Sample Bank', 'SIT', 'imps_portal',  'https://sit-imps.example.internal:9015', '9.0.67', '8u311', true),
    ('IMPS Portal',  'Sample Bank', 'UAT', 'imps_portal',  'https://uat-imps.example.internal:9015', '9.0.67', '8u311', true),
    ('E_Mandate',    'Sample Co-op Bank', 'SIT', 'E_Mandate', 'http://sit-emandate.example.internal:8080', '9.0.53', '8u251', true),
    ('E_Mandate',    'Sample Co-op Bank', 'UAT', 'E_Mandate', 'http://uat-emandate.example.internal:8080', '9.0.53', '8u251', true)
) as v (name, client, env, ctx, url, tomcat, java, build_xml)
join application a on a.name = v.name and a.client = v.client
on conflict (application_id, environment, context_name) do nothing;
