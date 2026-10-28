package net.etfbl.mdp.parser;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.model.ValidationResult;
import net.etfbl.mdp.common.util.HashUtil;
import net.etfbl.mdp.common.util.JsonUtil;
import net.etfbl.mdp.common.util.XmlInvoiceParser;

/**
 * Faza 2 - obrada jedne konekcije.
 *
 * Tok: citanje poruke -> provjera hasha -> ACK -> dedup u Redisu
 *      -> parsiranje -> RMI validacija -> slanje na MQ topic.
 */
public class ConnectionHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHandler.class);

    private static final String TOPIC_VALIDATED = "invoices.validated";
    private static final String TOPIC_REJECTED = "invoices.rejected";

    private final Socket socket;

    public ConnectionHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        String ime = "?";
        String rezervisaniId = null;

        try (Socket s = socket;
             DataInputStream in = new DataInputStream(s.getInputStream());
             DataOutputStream out = new DataOutputStream(s.getOutputStream())) {

            // === 1. citanje poruke po protokolu ===
            ime = in.readUTF();
            int duzina = in.readInt();

            byte[] xmlBajtovi = new byte[duzina];
            in.readFully(xmlBajtovi);          // readFully, ne read!

            byte[] primljeniHash = new byte[HashUtil.SHA256_LENGTH];
            in.readFully(primljeniHash);

            log.info("Primljen {} ({} bajtova)", ime, duzina);

            // === 2. provjera integriteta ===
            byte[] mojHash = HashUtil.sha256(xmlBajtovi);

            if (!HashUtil.matches(primljeniHash, mojHash)) {
                log.error("Hash se ne poklapa za {} - faktura se odbacuje", ime);
                log.error("   primljeno:  {}", HashUtil.toHex(primljeniHash));
                log.error("   izracunato: {}", HashUtil.toHex(mojHash));
                out.writeUTF("ERR:HASH_MISMATCH");
                out.flush();
                return;
            }

            // === 3. ACK - bajtovi su stigli ispravno ===
            // Salje se prije dedupa i validacije jer ACK znaci "prenos je
            // uspio", a ne "faktura je odobrena". Duplikat i nevalidna
            // faktura nisu greske prenosa - fajl svejedno treba premjestiti.
            out.writeUTF("ACK:" + ime);
            out.flush();
            log.info("ACK poslat za {}", ime);

            // === 4. dedup po <id> ===
            String id = XmlInvoiceParser.extractId(xmlBajtovi);

            if (id == null || id.isBlank()) {
                log.error("Faktura {} nema <id>, odbacujem", ime);
                return;
            }

            if (!RedisClient.rezervisiId(id)) {
                log.warn("Faktura {} ({}) je duplikat, odbacujem", id, ime);
                return;
            }

            // Od ovog trenutka je ID rezervisan. Ako obrada pukne, finally
            // ga oslobadja - inace se faktura nikad ne bi mogla ponoviti.
            rezervisaniId = id;

            // === 5. parsiranje ===
            Invoice faktura = XmlInvoiceParser.parse(xmlBajtovi);
            log.info("Parsirana {} | {} | {} {}",
                    faktura.getId(),
                    faktura.getClient() == null ? "?" : faktura.getClient().getName(),
                    faktura.getTotal(),
                    faktura.getCurrency());

            // === 6. RMI validacija ===
            ValidationResult rezultat = ValidatorClient.validiraj(faktura);

            if (rezultat == null) {
                log.error("Validacija nije izvrsena za {}, faktura ostaje neobradjena",
                        faktura.getId());
                return;   // finally oslobadja ID
            }

            // === 7. slanje na MQ ===
            if (rezultat.isValid()) {
                faktura.setStatus("validated");
                MqPublisher.posalji(TOPIC_VALIDATED, JsonUtil.toJson(faktura));
                log.info("Faktura {} VALIDNA -> {}", faktura.getId(), TOPIC_VALIDATED);

            } else {
                faktura.setStatus("rejected");
                faktura.setRejectionReason(String.join("; ", rezultat.getErrors()));

                Map<String, Object> poruka = Map.of(
                        "invoice", faktura,
                        "errors", rezultat.getErrors(),
                        "validatorTimestamp", rezultat.getValidatorTimestamp());

                MqPublisher.posalji(TOPIC_REJECTED, JsonUtil.toJson(poruka));

                log.warn("Faktura {} NEVALIDNA ({} gresaka) -> {}",
                        faktura.getId(), rezultat.getErrors().size(), TOPIC_REJECTED);
                rezultat.getErrors().forEach(e -> log.warn("   -> {}", e));
            }

            // obrada je uspjela - rezervacija ostaje da vazi
            rezervisaniId = null;

        } catch (Exception e) {
            log.error("Greska pri obradi konekcije ({})", ime, e);

        } finally {
            if (rezervisaniId != null) {
                RedisClient.oslobodiId(rezervisaniId);
            }
        }
    }
}