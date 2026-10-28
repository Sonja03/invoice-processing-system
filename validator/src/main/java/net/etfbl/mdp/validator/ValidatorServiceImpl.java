package net.etfbl.mdp.validator;

import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.model.Item;
import net.etfbl.mdp.common.model.ValidationResult;
import net.etfbl.mdp.common.rmi.ValidatorService;

/**
 * Faza 3 - implementacija Validator servisa.
 *
 * Izvrsava sest grupa provjera iz zadatka. Ne staje na prvoj gresci
 * nego prodje sve i skupi ih u listu.
 */
public class ValidatorServiceImpl extends UnicastRemoteObject implements ValidatorService {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ValidatorServiceImpl.class);

    private static final Pattern JIB_PATTERN = Pattern.compile("\\d{13}");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$");

    private static final Set<String> DOZVOLJENE_VALUTE =
            Set.of("BAM", "EUR", "USD", "CHF", "GBP");

    private static final BigDecimal MAX_IZNOS = new BigDecimal("1000000");
    private static final int MAX_STAROST_DANA = 365;

    public ValidatorServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public ValidationResult validate(Invoice invoice) throws RemoteException {

        String id = invoice == null ? "null" : invoice.getId();
        log.info("Primljen zahtjev za validaciju fakture {}", id);

        List<String> errors = new ArrayList<>();

        if (invoice == null) {
            errors.add("Faktura je prazna");
            return rezultat(errors, "null");
        }

        provjeriId(invoice, errors);
        provjeriKlijenta(invoice, errors);
        provjeriDatum(invoice, errors);
        provjeriValutu(invoice, errors);
        provjeriStavke(invoice, errors);
        provjeriUkupanIznos(invoice, errors);

        return rezultat(errors, id);
    }

    // ---------- pojedinacne provjere ----------

    private void provjeriId(Invoice invoice, List<String> errors) {
        if (invoice.getId() == null || invoice.getId().isBlank()) {
            errors.add("Faktura nema ID");
        }
    }

    private void provjeriKlijenta(Invoice invoice, List<String> errors) {
        if (invoice.getClient() == null) {
            errors.add("Faktura nema podatke o klijentu");
            return;
        }

        String jib = invoice.getClient().getJib();
        if (jib == null || !JIB_PATTERN.matcher(jib).matches()) {
            errors.add("JIB mora imati tacno 13 cifara, dobijeno: " + jib);
        }

        String email = invoice.getClient().getEmail();
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            errors.add("Email nije u ispravnom formatu: " + email);
        }
    }

    private void provjeriDatum(Invoice invoice, List<String> errors) {
        LocalDate datum = invoice.getDate();
        if (datum == null) {
            errors.add("Faktura nema datum");
            return;
        }

        LocalDate danas = LocalDate.now();

        if (datum.isAfter(danas)) {
            errors.add("Datum fakture je u buducnosti: " + datum);
        }

        if (datum.isBefore(danas.minusDays(MAX_STAROST_DANA))) {
            errors.add("Datum fakture je stariji od " + MAX_STAROST_DANA + " dana: " + datum);
        }
    }

    private void provjeriValutu(Invoice invoice, List<String> errors) {
        String valuta = invoice.getCurrency();
        if (valuta == null || !DOZVOLJENE_VALUTE.contains(valuta.toUpperCase())) {
            errors.add("Valuta nije podrzana: " + valuta + ". Dozvoljene: " + DOZVOLJENE_VALUTE);
        }
    }

    private void provjeriStavke(Invoice invoice, List<String> errors) {
        List<Item> stavke = invoice.getItems();

        if (stavke == null || stavke.isEmpty()) {
            errors.add("Faktura mora imati barem jednu stavku");
            return;
        }

        for (int i = 0; i < stavke.size(); i++) {
            Item stavka = stavke.get(i);
            int redniBroj = i + 1;

            if (stavka.getQuantity() == null) {
                errors.add("Stavka " + redniBroj + ": nedostaje kolicina");
            } else if (stavka.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Stavka " + redniBroj + ": kolicina mora biti pozitivna, dobijeno "
                        + stavka.getQuantity());
            }

            if (stavka.getUnitPrice() == null) {
                errors.add("Stavka " + redniBroj + ": nedostaje jedinicna cijena");
            } else if (stavka.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Stavka " + redniBroj + ": cijena mora biti pozitivna, dobijeno "
                        + stavka.getUnitPrice());
            }
        }
    }

    private void provjeriUkupanIznos(Invoice invoice, List<String> errors) {
        BigDecimal ukupno = invoice.getTotal();

        if (ukupno.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Ukupan iznos mora biti veci od nule, dobijeno: " + ukupno);
        }

        if (ukupno.compareTo(MAX_IZNOS) >= 0) {
            errors.add("Ukupan iznos mora biti manji od 1.000.000, dobijeno: " + ukupno);
        }
    }

    // ---------- sklapanje rezultata ----------

    private ValidationResult rezultat(List<String> errors, String id) {
        boolean valid = errors.isEmpty();
        String timestamp = LocalDateTime.now().toString();

        if (valid) {
            log.info("Faktura {} je VALIDNA", id);
        } else {
            log.warn("Faktura {} je NEVALIDNA, broj gresaka: {}", id, errors.size());
            errors.forEach(e -> log.warn("   -> {}", e));
        }

        return new ValidationResult(valid, errors, timestamp);
    }
}