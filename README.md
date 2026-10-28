# Invoice Processing System

A distributed system for automated invoice processing, built in Java as a project for the *Network and Distributed Programming* course at the Faculty of Electrical Engineering, University of Banja Luka.

The system consists of independent Java services, each handling one stage of invoice processing. The services communicate using TCP sockets, Java RMI, ActiveMQ, UDP multicast and a REST API, with Redis used for storage and caching.

## Services

- **watcher** – watches the inbox folder and sends new XML invoices over TCP with a SHA-256 hash
- **parser** – checks file integrity, parses the XML and detects duplicates in Redis
- **validator** – validates invoice data over RMI
- **enrichment** – converts amounts to BAM using an exchange rate API, with caching and retry
- **approval** – auto-approves small invoices, larger ones wait for manual approval
- **approval-client** – GUI for approving or rejecting invoices
- **aggregator** – stores invoice statuses in Redis and sends UDP multicast notifications
- **monitor-client** – GUI that shows notifications in real time
- **restapi** – read-only REST API for invoices and statistics

## Tech stack

Java, Maven, TCP/UDP sockets, Java RMI, ActiveMQ, Redis, Docker Compose

## Running

```bash
docker compose up -d
mvn clean install
```

Then start the services (validator, parser, enrichment, approval, aggregator, restapi, the GUI clients and finally watcher) and put XML invoices into `invoices/inbox`.
