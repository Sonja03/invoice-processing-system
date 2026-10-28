package net.etfbl.mdp.validator;

import java.math.BigDecimal;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalDate;
import java.util.List;

import net.etfbl.mdp.common.model.Client;
import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.model.Item;
import net.etfbl.mdp.common.model.ValidationResult;
import net.etfbl.mdp.common.rmi.ValidatorService;

/**
 * Radi tacno ono sto ce Parser raditi u Fazi 2.
 * Validator mora biti pokrenut prije ovoga.
 */
public class ValidatorTestClient {

    public static void main(String[] args) throws Exception {

        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        ValidatorService validator = (ValidatorService) registry.lookup("ValidatorService");

        System.out.println("Klasa objekta koji sam dobila: " + validator.getClass().getName());
        System.out.println();

        System.out.println("=== ISPRAVNA FAKTURA ===");
        ispisi(validator.validate(ispravna()));

        System.out.println();
        System.out.println("=== NEISPRAVNA FAKTURA ===");
        ispisi(validator.validate(neispravna()));
    }

    private static void ispisi(ValidationResult r) {
        System.out.println("valid: " + r.isValid());
        System.out.println("vrijeme: " + r.getValidatorTimestamp());
        if (!r.getErrors().isEmpty()) {
            System.out.println("greske:");
            r.getErrors().forEach(e -> System.out.println("   - " + e));
        }
    }

    private static Invoice ispravna() {
        Invoice f = new Invoice();
        f.setId("INV-2025-001");
        f.setClient(new Client("ETF", "1234567891012", "fakture@etf.com"));
        f.setDate(LocalDate.now().minusDays(10));
        f.setCurrency("EUR");
        f.setItems(List.of(
                new Item("Web development", new BigDecimal("10"), new BigDecimal("50.00")),
                new Item("Hosting", new BigDecimal("1"), new BigDecimal("120.00"))));
        return f;
    }

    private static Invoice neispravna() {
        Invoice f = new Invoice();
        f.setId("INV-2025-002");
        f.setClient(new Client("Firma", "123", "nijeemail"));
        f.setDate(LocalDate.now().plusDays(5));
        f.setCurrency("RSD");
        f.setItems(List.of(
                new Item("Nesto", new BigDecimal("-2"), new BigDecimal("0"))));
        return f;
    }
}