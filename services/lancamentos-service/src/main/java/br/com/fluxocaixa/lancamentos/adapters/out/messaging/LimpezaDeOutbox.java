package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

import br.com.fluxocaixa.lancamentos.application.port.out.Relogio;
import br.com.fluxocaixa.lancamentos.config.PropriedadesOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

@Component
public class LimpezaDeOutbox {

    private static final Logger log = LoggerFactory.getLogger(LimpezaDeOutbox.class);

    private final JdbcTemplate jdbc;
    private final Relogio relogio;
    private final PropriedadesOutbox propriedades;

    public LimpezaDeOutbox(JdbcTemplate jdbc, Relogio relogio, PropriedadesOutbox propriedades) {
        this.jdbc = jdbc;
        this.relogio = relogio;
        this.propriedades = propriedades;
    }

    @Scheduled(cron = "${app.outbox.limpeza-cron:0 15 4 * * *}", zone = "UTC")
    @Transactional
    public int limpar() {
        Timestamp limite = Timestamp.from(relogio.agora().minus(propriedades.retencao()));
        int removidos = jdbc.update(
                """
                DELETE FROM outbox_eventos
                WHERE publicado_em IS NOT NULL
                  AND publicado_em < ?
                """,
                limite
        );
        if (removidos > 0) {
            log.info("Outbox limpa eventos={}", removidos);
        }
        return removidos;
    }
}
