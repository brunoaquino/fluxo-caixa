package br.com.fluxocaixa.consolidado.adapters.out.persistence;

import br.com.fluxocaixa.consolidado.application.port.out.AplicacaoLancamentoPort;
import br.com.fluxocaixa.consolidado.application.port.out.ConsultaSaldoPort;
import br.com.fluxocaixa.consolidado.domain.model.EfeitoLancamento;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoDia;
import br.com.fluxocaixa.consolidado.domain.model.ValoresMonetarios;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotência e incremento do saldo acontecem na mesma transação.
 * O INSERT do event_id usa ON CONFLICT DO NOTHING: a transação concorrente espera o commit
 * da primeira e então não aplica o delta de novo. O saldo do dia usa UPSERT atômico
 * (total = total + EXCLUDED), então eventos diferentes do mesmo dia não se perdem
 * sem optimistic locking nem retry.
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
    public Optional<MovimentoDia> buscarDia(UUID comercianteId, LocalDate data) {
        return jdbc.query(
                """
                SELECT data, total_creditos, total_debitos, quantidade_lancamentos, atualizado_em
                FROM saldos_diarios
                WHERE comerciante_id = ? AND data = ?
                """,
                (rs, row) -> mapear(rs),
                comercianteId,
                data).stream().findFirst();
    }

    @Override
    public BigDecimal saldoAcumuladoAte(UUID comercianteId, LocalDate data) {
        return somar("data <= ?", comercianteId, data);
    }

    @Override
    public BigDecimal saldoAcumuladoAntesDe(UUID comercianteId, LocalDate data) {
        return somar("data < ?", comercianteId, data);
    }

    @Override
    public Map<LocalDate, MovimentoDia> buscarNoPeriodo(UUID comercianteId, LocalDate inicio, LocalDate fim) {
        Map<LocalDate, MovimentoDia> movimentos = new HashMap<>();
        jdbc.query(
                """
                SELECT data, total_creditos, total_debitos, quantidade_lancamentos, atualizado_em
                FROM saldos_diarios
                WHERE comerciante_id = ? AND data BETWEEN ? AND ?
                ORDER BY data
                """,
                (rs, row) -> {
                    MovimentoDia movimento = mapear(rs);
                    movimentos.put(movimento.data(), movimento);
                    return movimento;
                },
                comercianteId,
                inicio,
                fim);
        return movimentos;
    }

    private BigDecimal somar(String predicadoData, UUID comercianteId, LocalDate data) {
        BigDecimal valor = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(total_creditos - total_debitos), 0)
                FROM saldos_diarios
                WHERE comerciante_id = ? AND %s
                """.formatted(predicadoData),
                BigDecimal.class,
                comercianteId,
                data);
        return ValoresMonetarios.comDuasCasas(valor);
    }

    private static MovimentoDia mapear(ResultSet rs) throws SQLException {
        OffsetDateTime atualizado = rs.getObject("atualizado_em", OffsetDateTime.class);
        return new MovimentoDia(
                rs.getObject("data", LocalDate.class),
                rs.getBigDecimal("total_creditos"),
                rs.getBigDecimal("total_debitos"),
                rs.getInt("quantidade_lancamentos"),
                atualizado == null ? null : atualizado.toInstant());
    }
}
