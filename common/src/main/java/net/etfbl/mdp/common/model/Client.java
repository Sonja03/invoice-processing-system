package net.etfbl.mdp.common.model;

import java.io.Serializable;

/**
 * Podaci o klijentu sa fakture (element <client>).
 */
public class Client implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String jib;
    private String email;

    public Client() {
        // prazan konstruktor je obavezan za Jackson
    }

    public Client(String name, String jib, String email) {
        this.name = name;
        this.jib = jib;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJib() {
        return jib;
    }

    public void setJib(String jib) {
        this.jib = jib;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return "Client{name='" + name + "', jib='" + jib + "', email='" + email + "'}";
    }
}