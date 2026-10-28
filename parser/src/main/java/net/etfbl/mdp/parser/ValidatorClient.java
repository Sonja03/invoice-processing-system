package net.etfbl.mdp.parser;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.model.ValidationResult;
import net.etfbl.mdp.common.rmi.ValidatorService;

/**
 * RMI klijent ka Validator servisu (Faza 2 -> Faza 3).
 *
 * Ono sto lookup vrati nije ValidatorServiceImpl nego stub - proxy
 * koji pakuje poziv i salje ga preko mreze na port 1099.
 */
public class ValidatorClient {

    private static final Logger log = LoggerFactory.getLogger(ValidatorClient.class);

    private static final String HOST = "localhost";
    private static final int PORT = 1099;
    private static final String SERVICE_NAME = "ValidatorService";

    /** Stub se uzima jednom i koristi za sve fakture. */
    private static ValidatorService validator;

    private ValidatorClient() {
    }

    /** Poziva se jednom, pri pokretanju Parsera. */
    public static void pokreni() throws Exception {
        Registry registry = LocateRegistry.getRegistry(HOST, PORT);
        validator = (ValidatorService) registry.lookup(SERVICE_NAME);

        log.info("Povezan na Validator preko RMI ({}:{})", HOST, PORT);
        log.info("Klasa dobijenog objekta: {}", validator.getClass().getName());
    }

    /**
     * Poziva udaljenu validaciju. Invoice putuje kao bajtovi,
     * ValidationResult se vraca kao bajtovi.
     */
    public static ValidationResult validiraj(Invoice faktura) {
        try {
            return validator.validate(faktura);
        } catch (Exception e) {
            log.error("Validator nije dostupan za fakturu {}", faktura.getId(), e);
            return null;
        }
    }
}