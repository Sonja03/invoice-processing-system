package net.etfbl.mdp.validator;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.etfbl.mdp.common.rmi.ValidatorService;

/**
 * Pokrece RMI registar i objavljuje Validator servis pod imenom.
 *
 * Mora se pokrenuti PRIJE Parsera - inace Parserov lookup baca
 * ConnectException jer registra jos nema.
 */
public class ValidatorMain {

    private static final Logger log = LoggerFactory.getLogger(ValidatorMain.class);

    private static final int RMI_PORT = 1099;
    private static final String SERVICE_NAME = "ValidatorService";

    public static void main(String[] args) {
        try {
            ValidatorService servis = new ValidatorServiceImpl();

            Registry registry = LocateRegistry.createRegistry(RMI_PORT);
            log.info("RMI registar podignut na portu {}", RMI_PORT);

            registry.rebind(SERVICE_NAME, servis);
            log.info("Servis '{}' registrovan i spreman", SERVICE_NAME);
            log.info("Validator ceka zahtjeve...");

        } catch (Exception e) {
            log.error("Greska pri pokretanju Validator servisa", e);
            System.exit(1);
        }
    }
}