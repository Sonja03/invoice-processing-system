package net.etfbl.mdp.common.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Faktura. Prolazi kroz cijeli lanac obrade, pa nosi i polja
 * koja ne postoje u ulaznom XML-u nego ih dodaju kasnije faze.
 */
public class Invoice implements Serializable {

    private static final long serialVersionUID = 1L;

    // --- iz XML-a ---
    private String id;
    private Client client;
    private LocalDate date;
    private String currency;
    private List<Item> items = new ArrayList<>();

    // --- dodaje Enrichment (Faza 4 i 4b) ---
    private BigDecimal totalBAM;
    private String rateSource;   // CACHE ili API
    private BigDecimal rate;     // kurs koji je primijenjen
    private int retryCount;

    // --- dodaju Approval i Aggregator (Faze 5 i 6) ---
    private String status;       // approved, rejected, pending, failed
    private String rejectionReason;

    // --- dodaje Approval (Faza 5) ---
    private String approvedBy;
    private String decisionTimestamp;
    
    public Invoice() {
    }

    /**
     * Zbir svih stavki u originalnoj valuti fakture.
     */
    public BigDecimal getTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        if (items != null) {
            for (Item item : items) {
                sum = sum.add(item.getLineTotal());
            }
        }
        return sum;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public BigDecimal getTotalBAM() {
        return totalBAM;
    }

    public void setTotalBAM(BigDecimal totalBAM) {
        this.totalBAM = totalBAM;
    }

    public String getRateSource() {
        return rateSource;
    }

    public void setRateSource(String rateSource) {
        this.rateSource = rateSource;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
    
    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getDecisionTimestamp() {
        return decisionTimestamp;
    }

    public void setDecisionTimestamp(String decisionTimestamp) {
        this.decisionTimestamp = decisionTimestamp;
    }

    @Override
    public String toString() {
        return "Invoice{id='" + id + "', client=" + client + ", date=" + date
                + ", currency='" + currency + "', items=" + (items == null ? 0 : items.size())
                + ", total=" + getTotal() + ", totalBAM=" + totalBAM
                + ", status='" + status + "'}";
    }
}