package net.etfbl.mdp.watcher;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generise testne fakture u inbox folder.
 *
 * Fajl se prvo pise kao .tmp pa preimenuje u .xml - preimenovanje je
 * atomicno, pa Watcher nikad ne vidi polupun fajl.
 *
 * Pokretanje:
 *   bez argumenata        -> 100 faktura odjednom
 *   GeneratorFaktura 200  -> 200 faktura
 *   GeneratorFaktura 50 100 -> 50 faktura, pauza 100ms izmedju
 */
public class GeneratorFaktura {

    private static final Path INBOX =
            Path.of("C:/Users/WINDOWS11/eclipse-workspace/mdpProjekat/invoices/inbox");

    private static final String[] VALUTE = {"BAM", "BAM", "BAM", "EUR", "USD", "CHF", "GBP"};

    private static final String[][] KLIJENTI = {
            {"Telekom Srpske ad",      "4400644000005", "racuni@mtel.ba"},
            {"Nektar doo",             "4400958770006", "nabavka@nektar.ba"},
            {"Mala Firma doo",         "4400111222333", "info@mala.ba"},
            {"Softver Plus doo",       "4400555666777", "nabavka@softverplus.ba"},
            {"Alpen Trade doo",        "4400777888999", "office@alpen.ba"},
            {"Digitalna Rjesenja doo", "4400333444555", "racuni@digital.ba"},
            {"Gradnja Invest ad",      "4401234567890", "finansije@gradnja.ba"},
            {"Agro Kombinat doo",      "4409876543210", "racunovodstvo@agro.ba"},
            {"Elektro Servis sp",      "4405556667778", "servis@elektro.ba"},
            {"Prevoz Express doo",     "4402223334445", "dispecer@prevoz.ba"}
    };

    private static final String[] OPISI = {
            "Konsultantske usluge", "Odrzavanje sistema", "Licenca za softver",
            "Hosting usluge", "Transport robe", "Kancelarijski materijal",
            "Servis opreme", "Edukacija zaposlenih", "Marketing kampanja",
            "Pravne usluge", "Racunovodstvene usluge", "Web razvoj"
    };

    /** Fiksno sjeme - svako pokretanje daje isti skup faktura. */
    private static final Random RND = new Random(42);

    public static void main(String[] args) throws Exception {

        int broj = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        long pauza = args.length > 1 ? Long.parseLong(args[1]) : 0;

        Files.createDirectories(INBOX);

        System.out.println("Generisem " + broj + " faktura u " + INBOX);
        if (pauza > 0) {
            System.out.println("Pauza izmedju fajlova: " + pauza + " ms");
        }

        List<String> vecKoristeniId = new ArrayList<>();
        int validne = 0, nevalidne = 0, duplikati = 0;

        long pocetak = System.currentTimeMillis();

        for (int i = 1; i <= broj; i++) {

            String xml;
            String imeFajla;

            // svaka 12. je duplikat ranije fakture
            if (i % 12 == 0 && !vecKoristeniId.isEmpty()) {
                String stariId = vecKoristeniId.get(RND.nextInt(vecKoristeniId.size()));
                xml = validnaFaktura(stariId);
                imeFajla = String.format("gen-%04d-dup.xml", i);
                duplikati++;

            // svaka 9. je namjerno neispravna
            } else if (i % 9 == 0) {
                String id = String.format("INV-GEN-%04d", i);
                xml = nevalidnaFaktura(id, i);
                imeFajla = String.format("gen-%04d-bad.xml", i);
                nevalidne++;

            } else {
                String id = String.format("INV-GEN-%04d", i);
                vecKoristeniId.add(id);
                xml = validnaFaktura(id);
                imeFajla = String.format("gen-%04d.xml", i);
                validne++;
            }

            upisi(imeFajla, xml);

            if (pauza > 0) {
                Thread.sleep(pauza);
            }

            if (i % 25 == 0) {
                System.out.println("  ... " + i + " fajlova");
            }
        }

        long trajanje = System.currentTimeMillis() - pocetak;

        System.out.println();
        System.out.println("Gotovo za " + trajanje + " ms");
        System.out.println("  validne:   " + validne);
        System.out.println("  nevalidne: " + nevalidne);
        System.out.println("  duplikati: " + duplikati);
        System.out.println("  UKUPNO:    " + broj);
    }

    // ==================== pisanje fajla ====================

    /**
     * Pise u .tmp pa preimenuje u .xml.
     *
     * Watcher prati samo .xml fajlove, a preimenovanje je atomicno -
     * fajl se pojavi odjednom i vec pun. Bez ovoga bi Watcher uhvatio
     * fajl usred pisanja i preskocio ga kao nestabilan.
     */
    private static void upisi(String imeFajla, String sadrzaj) throws IOException {
        Path privremeni = INBOX.resolve(imeFajla + ".tmp");
        Path konacni = INBOX.resolve(imeFajla);

        Files.writeString(privremeni, sadrzaj, StandardCharsets.UTF_8);
        Files.move(privremeni, konacni, StandardCopyOption.REPLACE_EXISTING);
    }

    // ==================== sadrzaj faktura ====================

    private static String validnaFaktura(String id) {
        String[] klijent = KLIJENTI[RND.nextInt(KLIJENTI.length)];
        String valuta = VALUTE[RND.nextInt(VALUTE.length)];

        // datum u posljednjih 300 dana - nikad stariji od 365 ni u buducnosti
        LocalDate datum = LocalDate.now().minusDays(RND.nextInt(300));

        // 70% faktura ispod 100 BAM da se automatski odobre,
        // ostatak iznad praga pa ide na rucno odobrenje
        boolean mala = RND.nextInt(100) < 70;

        int brojStavki = 1 + RND.nextInt(3);
        StringBuilder stavke = new StringBuilder();

        for (int i = 0; i < brojStavki; i++) {
            String opis = OPISI[RND.nextInt(OPISI.length)];
            int kolicina = 1 + RND.nextInt(mala ? 2 : 8);

            BigDecimal cijena = mala
                    ? new BigDecimal(5 + RND.nextInt(25)).setScale(2, RoundingMode.HALF_UP)
                    : new BigDecimal(50 + RND.nextInt(400)).setScale(2, RoundingMode.HALF_UP);

            stavke.append(stavka(opis, String.valueOf(kolicina), cijena.toPlainString()));
        }

        return omot(id, klijent, datum.toString(), valuta, stavke.toString());
    }

    /**
     * Krsi jedno pravilo, ciklicno - da se vide razliciti razlozi odbijanja.
     */
    private static String nevalidnaFaktura(String id, int redniBroj) {
        String[] klijent = KLIJENTI[RND.nextInt(KLIJENTI.length)];
        String valuta = "BAM";
        String datum = LocalDate.now().minusDays(10).toString();
        String stavke = stavka("Sporna stavka", "2", "50.00");

        switch (redniBroj % 5) {
            case 0 -> klijent = new String[]{klijent[0], "123", klijent[2]};
            case 1 -> klijent = new String[]{klijent[0], klijent[1], "nijeemail"};
            case 2 -> datum = LocalDate.now().plusDays(30).toString();
            case 3 -> valuta = "RSD";
            default -> stavke = stavka("Negativna kolicina", "-3", "20.00");
        }

        return omot(id, klijent, datum, valuta, stavke);
    }

    private static String stavka(String opis, String kolicina, String cijena) {
        return """
                    <item>
                      <description>%s</description>
                      <quantity>%s</quantity>
                      <unitPrice>%s</unitPrice>
                    </item>
                """.formatted(opis, kolicina, cijena);
    }

    private static String omot(String id, String[] klijent,
                               String datum, String valuta, String stavke) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <invoice>
                  <id>%s</id>
                  <client>
                    <name>%s</name>
                    <jib>%s</jib>
                    <email>%s</email>
                  </client>
                  <date>%s</date>
                  <currency>%s</currency>
                  <items>
                %s  </items>
                </invoice>
                """.formatted(id, klijent[0], klijent[1], klijent[2], datum, valuta, stavke);
    }
}