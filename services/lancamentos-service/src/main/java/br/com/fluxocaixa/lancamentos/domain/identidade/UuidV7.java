package br.com.fluxocaixa.lancamentos.domain.identidade;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UUID versão 7 (ordenável por tempo). O layout segue o RFC 9562:
 * 48 bits de timestamp em milissegundos, versão 7 e variante IETF.
 */
public final class UuidV7 {

    private UuidV7() {
    }

    public static UUID gerar() {
        long agora = Instant.now().toEpochMilli();
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        bytes[0] = (byte) (agora >>> 40);
        bytes[1] = (byte) (agora >>> 32);
        bytes[2] = (byte) (agora >>> 24);
        bytes[3] = (byte) (agora >>> 16);
        bytes[4] = (byte) (agora >>> 8);
        bytes[5] = (byte) agora;
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
