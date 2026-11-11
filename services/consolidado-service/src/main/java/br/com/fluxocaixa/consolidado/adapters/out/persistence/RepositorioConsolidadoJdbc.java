package br.com.fluxocaixa.consolidado.adapters.out.persistence;

import br.com.fluxocaixa.consolidado.application.port.out.AplicacaoLancamentoPort;
import br.com.fluxocaixa.consolidado.application.port.out.ConsultaSaldoPort;
import br.com.fluxocaixa.consolidado.domain.model.EfeitoLancamento;
import br.com.fluxocaixa.consolidado.domain.model.LinhaRelatorio;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoAcumulado;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoDia;
import br.com.fluxocaixa.consolidado.domain.model.ValoresMonetarios;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Idempotência e incremento do saldo acontecem na mesma transação.
 * O INSERT do event_id usa ON CONFLICT DO NOTHING. O saldo do dia usa UPSERT atômico.
 * Leituras são somente leitura para o roteador enviar à réplica quando DB_READER_URL existe.
 * O relatório equivale a {@code RelatorioDiario.montar}: uma sentença com window function,
 * inclusive os dias sem movimento.
 */
@Repository
public class RepositorioConsolidadoJdbc implements AplicacaoLancamentoPort, ConsultaSaldoPort {

    private final JdbcTemplate jdbc;

    public RepositorioConsolidadoJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean aplicarSeNovo(
            UUID eventId,
            UUID comercianteId,
            LocalDate data,
            EfeitoLancamento efeito,
            Instant processadoEm) {
        int inseridos = jdbc.update(
                """
                INSERT INTO eventos_processados (event_id, processado_em)
                VALUES (?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                ps -> {
                    ps.setObject(1, eventId);
                    ps.setObject(2, processadoEm.atOffset(ZoneOffset.UTC));
                });
        if (inseridos == 0) {
            return false;
        }
        jdbc.update(
                """
                INSERT INTO saldos_diarios (
                    comerciante_id, data, total_creditos, total_debitos,
                    quantidade_lancamentos, versao, atualizado_em)
                VALUES (?, ?, ?, ?, ?, 1, ?)
                ON CONFLICT (comerciante_id, data) DO UPDATE SET
                    total_creditos = saldos_diarios.total_creditos + EXCLUDED.total_creditos,
                    total_debitos = saldos_diarios.total_debitos + EXCLUDED.total_debitos,
                    quantidade_lancamentos = saldos_diarios.quantidade_lancamentos + EXCLUDED.quantidade_lancamentos,
                    versao = saldos_diarios.versao + 1,
                    atualizado_em = EXCLUDED.atualizado_em
                """,
                ps -> {
                    ps.setObject(1, comercianteId);
                    ps.setObject(2, data);
                    ps.setBigDecimal(3, efeito.totalCreditos());
                    ps.setBigDecimal(4, efeito.totalDebitos());
                    ps.setInt(5, efeito.quantidade());
                    ps.setObject(6, processadoEm.atOffset(ZoneOffset.UTC));
                });
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public MovimentoAcumulado buscarDia(UUID comercianteId, LocalDate data) {
        MovimentoAcumulado encontrado = jdbc.queryForObject(
                """
                SELECT
                    COALESCE(SUM(total_creditos) FILTER (WHERE data = ?), 0) AS total_creditos,
                    COALESCE(SUM(total_debitos) FILTER (WHERE data = ?), 0) AS total_debitos,
                    COALESCE(SUM(quantidade_lancamentos) FILTER (WHERE data = ?), 0) AS quantidade_lancamentos,
                    MAX(atualizado_em) FILTER (WHERE data = ?) AS atualizado_em,
                    COALESCE(SUM(total_creditos - total_debitos), 0) AS saldo_acumulado
                FROM saldos_diarios
                WHERE comerciante_id = ? AND data <= ?
                """,
                (rs, row) -> mapearDia(rs, data),
                data,
                data,
                data,
                data,
                comercianteId,
                data);
        if (encontrado == null) {
            return new MovimentoAcumulado(MovimentoDia.vazio(data), ValoresMonetarios.ZERO);
        }
        return encontrado;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinhaRelatorio> buscarPeriodo(UUID comercianteId, LocalDate inicio, LocalDate fim) {
        return jdbc.query(
                """
                WITH abertura AS (
                    SELECT COALESCE(SUM(total_creditos - total_debitos), 0) AS acumulado
                    FROM saldos_diarios
                    WHERE comerciante_id = ? AND data < ?
                ),
                calendario AS (
                    SELECT generate_series(?::date, ?::date, interval '1 day')::date AS data
                ),
                movimentos AS (
                    SELECT data, total_creditos, total_debitos, quantidade_lancamentos, atualizado_em
                    FROM saldos_diarios
                    WHERE comerciante_id = ? AND data BETWEEN ? AND ?
                )
                SELECT
                    c.data,
                    COALESCE(m.total_creditos, 0) AS total_creditos,
                    COALESCE(m.total_debitos, 0) AS total_debitos,
                    COALESCE(m.quantidade_lancamentos, 0) AS quantidade_lancamentos,
                    m.atualizado_em,
                    a.acumulado
                        + SUM(COALESCE(m.total_creditos, 0) - COALESCE(m.total_debitos, 0))
                            OVER (ORDER BY c.data) AS saldo_acumulado
                FROM calendario c
                CROSS JOIN abertura a
                LEFT JOIN movimentos m ON m.data = c.data
                ORDER BY c.data
                """,
                (rs, row) -> mapearLinha(rs),
                comercianteId,
                inicio,
                inicio,
                fim,
                comercianteId,
                inicio,
                fim);
    }

    private static MovimentoAcumulado mapearDia(ResultSet rs, LocalDate data) throws SQLException {
        int quantidade = rs.getInt("quantidade_lancamentos");
        OffsetDateTime atualizado = rs.getObject("atualizado_em", OffsetDateTime.class);
        MovimentoDia movimento = quantidade == 0
                ? MovimentoDia.vazio(data)
                : new MovimentoDia(
                        data,
                        rs.getBigDecimal("total_creditos"),
                        rs.getBigDecimal("total_debitos"),
                        quantidade,
                        atualizado == null ? null : atualizado.toInstant());
        return new MovimentoAcumulado(movimento, rs.getBigDecimal("saldo_acumulado"));
    }

    private static LinhaRelatorio mapearLinha(ResultSet rs) throws SQLException {
        LocalDate data = rs.getObject("data", LocalDate.class);
        int quantidade = rs.getInt("quantidade_lancamentos");
        OffsetDateTime atualizado = rs.getObject("atualizado_em", OffsetDateTime.class);
        MovimentoDia movimento = quantidade == 0
                ? MovimentoDia.vazio(data)
                : new MovimentoDia(
                        data,
                        rs.getBigDecimal("total_creditos"),
                        rs.getBigDecimal("total_debitos"),
                        quantidade,
                        atualizado == null ? null : atualizado.toInstant());
        return new LinhaRelatorio(movimento, rs.getBigDecimal("saldo_acumulado"));
    }
}
