package net.etfbl.mdp.parser;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParserServer {

    private static final Logger log = LoggerFactory.getLogger(ParserServer.class);

    private static final int PORT = 9000;
    private static final int BROJ_NITI = 8;

    public static void main(String[] args) {

        ExecutorService pool = Executors.newFixedThreadPool(BROJ_NITI);

        try {
            // --- veze prema ostalim sistemima ---
            RedisClient.provjeriVezu();
            MqPublisher.pokreni();
            ValidatorClient.pokreni();

        } catch (Exception e) {
            log.error("Parser ne moze da se pokrene - provjeri Docker i Validator", e);
            pool.shutdown();
            return;
        }

        try (ServerSocket server = new ServerSocket(PORT)) {
            log.info("Parser slusa na portu {} ({} radnih niti)", PORT, BROJ_NITI);

            while (true) {
                Socket socket = server.accept();
                pool.submit(new ConnectionHandler(socket));
            }

        } catch (Exception e) {
            log.error("Greska u Parser serveru", e);
        } finally {
            pool.shutdown();
            MqPublisher.zaustavi();
        }
    }
}