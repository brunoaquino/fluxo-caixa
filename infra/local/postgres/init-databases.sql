CREATE USER lancamentos WITH PASSWORD 'lancamentos';
CREATE USER consolidado WITH PASSWORD 'consolidado';
CREATE DATABASE lancamentos OWNER lancamentos;
CREATE DATABASE consolidado OWNER consolidado;

\connect lancamentos
GRANT ALL ON SCHEMA public TO lancamentos;
ALTER SCHEMA public OWNER TO lancamentos;

\connect consolidado
GRANT ALL ON SCHEMA public TO consolidado;
ALTER SCHEMA public OWNER TO consolidado;
