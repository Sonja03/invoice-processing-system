package net.etfbl.mdp.common.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Jedna stavka fakture (element <item>).
 */
public class Item implements Serializable {

    private static final long serialVersionUID = 1L;

    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;

    public Item() {
    }

    public Item(String description, BigDecimal quantity, BigDecimal unitPrice) {
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /**
     * quantity * unitPrice.
     * Vraca nulu ako neko polje fali, da racunanje ukupnog iznosa
     * ne pukne na NullPointerException kod nepotpune fakture.
     */
    public BigDecimal getLineTotal() {
        if (quantity == null || unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return quantity.multiply(unitPrice);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    @Override
    public String toString() {
        return "Item{description='" + description + "', quantity=" + quantity
                + ", unitPrice=" + unitPrice + "}";
    }
}