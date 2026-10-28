package net.etfbl.mdp.common;

import java.nio.charset.StandardCharsets;

import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.util.HashUtil;
import net.etfbl.mdp.common.util.JsonUtil;
import net.etfbl.mdp.common.util.XmlInvoiceParser;

public class TestCommon {

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <invoice>
              <id>INV-2025-001</id>
              <client>
                <name>ETF</name>
                <jib>1234567891012</jib>
                <email>fakture@etf.com</email>
              </client>
              <date>2025-05-01</date>
              <currency>EUR</currency>
              <items>
                <item>
                  <description>Web development</description>
                  <quantity>10</quantity>
                  <unitPrice>50.00</unitPrice>
                </item>
                <item>
                  <description>Hosting</description>
                  <quantity>1</quantity>
                  <unitPrice>120.00</unitPrice>
                </item>
              </items>
            </invoice>
            """;

    public static void main(String[] args) throws Exception {

        byte[] bajtovi = XML.getBytes(StandardCharsets.UTF_8);

        System.out.println("=== 1. Samo ID (za provjeru duplikata) ===");
        System.out.println(XmlInvoiceParser.extractId(bajtovi));

        System.out.println();
        System.out.println("=== 2. Parsirana faktura ===");
        Invoice faktura = XmlInvoiceParser.parse(bajtovi);
        System.out.println(faktura);

        System.out.println();
        System.out.println("=== 3. Ukupan iznos ===");
        System.out.println(faktura.getTotal() + " " + faktura.getCurrency());

        System.out.println();
        System.out.println("=== 4. Stavke ===");
        faktura.getItems().forEach(i ->
                System.out.println("  " + i.getDescription() + " -> " + i.getLineTotal()));

        System.out.println();
        System.out.println("=== 5. SHA-256 hash ===");
        byte[] hash = HashUtil.sha256(bajtovi);
        System.out.println("duzina: " + hash.length + " bajta");
        System.out.println("hex: " + HashUtil.toHex(hash));

        System.out.println();
        System.out.println("=== 6. JSON ===");
        String json = JsonUtil.toPrettyJson(faktura);
        System.out.println(json);

        System.out.println();
        System.out.println("=== 7. JSON nazad u objekat ===");
        Invoice vracena = JsonUtil.fromJson(json, Invoice.class);
        System.out.println(vracena);
        System.out.println("iznosi se poklapaju: "
                + (faktura.getTotal().compareTo(vracena.getTotal()) == 0));
    }
}