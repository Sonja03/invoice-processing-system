package net.etfbl.mdp.common.util;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.etfbl.mdp.common.model.Client;
import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.model.Item;

/**
 * Parsira XML fakturu iz niza bajtova u Invoice objekat.
 *
 * Koristi DOM iz standardnog JDK-a, a ne JAXB - JAXB je izbacen iz JDK-a
 * od Jave 11 pa bi trazio dodatne zavisnosti.
 */
public final class XmlInvoiceParser {

    private XmlInvoiceParser() {
    }

    public static Invoice parse(byte[] xmlBytes) throws Exception {
        Element root = citajKorijen(xmlBytes);

        Invoice invoice = new Invoice();
        invoice.setId(text(root, "id"));
        invoice.setCurrency(text(root, "currency"));

        String dateText = text(root, "date");
        if (dateText != null && !dateText.isBlank()) {
            invoice.setDate(LocalDate.parse(dateText.trim()));
        }

        Element clientEl = firstElement(root, "client");
        if (clientEl != null) {
            Client client = new Client();
            client.setName(text(clientEl, "name"));
            client.setJib(text(clientEl, "jib"));
            client.setEmail(text(clientEl, "email"));
            invoice.setClient(client);
        }

        List<Item> items = new ArrayList<>();
        Element itemsEl = firstElement(root, "items");
        if (itemsEl != null) {
            NodeList itemNodes = itemsEl.getElementsByTagName("item");
            for (int i = 0; i < itemNodes.getLength(); i++) {
                Element itemEl = (Element) itemNodes.item(i);
                Item item = new Item();
                item.setDescription(text(itemEl, "description"));
                item.setQuantity(decimal(text(itemEl, "quantity")));
                item.setUnitPrice(decimal(text(itemEl, "unitPrice")));
                items.add(item);
            }
        }
        invoice.setItems(items);

        return invoice;
    }

    /**
     * Izvlaci samo <id>, bez gradjenja cijele fakture.
     * Parser prvo provjerava duplikat u Redisu, pa nema smisla
     * praviti cijeli objekat ako se faktura ionako odbacuje.
     */
    public static String extractId(byte[] xmlBytes) throws Exception {
        return text(citajKorijen(xmlBytes), "id");
    }

    // --- pomocne metode ---

    /**
     * Od bajtova gradi DOM stablo i vraca korijenski element <invoice>.
     */
    private static Element citajKorijen(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        // zatvaranje XXE rupe - bez ovoga zlonamjeran XML moze preko
        // external entity deklaracije da procita fajlove sa servera
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xmlBytes));
        doc.getDocumentElement().normalize();

        return doc.getDocumentElement();
    }

    /** Tekst prvog direktnog djeteta sa datim imenom, ili null. */
    private static String text(Element parent, String tagName) {
        Element el = firstElement(parent, tagName);
        return el == null ? null : el.getTextContent().trim();
    }

    /** Prvo direktno dijete sa datim imenom, ili null. */
    private static Element firstElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    /** String u BigDecimal; prazno ili nepostojece daje null. */
    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value.trim());
    }
}