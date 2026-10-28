package net.etfbl.mdp.restapi;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpServer;

/**
 * REST API servis - pokretanje.
 *
 * HTTP server koji cita iskljucivo iz Redisa. Ne slusa nijedan MQ topic
 * i ne zna nista o lancu obrade.
 *
 * Moze se pokrenuti bilo kad - ne zavisi od redoslijeda ostalih servisa.
 */
public class RestApiMain {

    private static final Logger log = LoggerFactory.getLogger(RestApiMain.class);

    private static final int PORT = 8080;
    private static final int BROJ_NITI = 8;

    public static void main(String[] args) {

        ExecutorService pool = Executors.newFixedThreadPool(BROJ_NITI);

        try {
            RedisReader.provjeriVezu();

            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // Handleri se biraju po najduzem poklapanju prefiksa:
            // /api/invoices/INV-001 pogadja InvoiceHandler,
            // /api/clients/.../summary pogadja ClientHandler.
            server.createContext("/api/invoices", new InvoiceHandler());
            server.createContext("/api/clients", new ClientHandler());
            server.createContext("/api/stats", new StatsHandler());

            // bez ovoga server sve zahtjeve obradjuje u jednoj niti
            server.setExecutor(pool);

            server.start();

            log.info("REST API sluša na portu {} ({} radnih niti)", PORT, BROJ_NITI);
            log.info("  GET http://localhost:{}/api/invoices", PORT);
            log.info("  GET http://localhost:{}/api/invoices?status=approved", PORT);
            log.info("  GET http://localhost:{}/api/invoices/{{id}}", PORT);
            log.info("  GET http://localhost:{}/api/clients/{{jib}}/summary", PORT);
            log.info("  GET http://localhost:{}/api/stats", PORT);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Gasim REST API...");
                server.stop(1);
                pool.shutdown();
            }, "shutdown-hook"));

        } catch (Exception e) {
            log.error("REST API ne moze da se pokrene - provjeri Docker", e);
            pool.shutdown();
            System.exit(1);
        }
    }
}