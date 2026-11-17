package io.github.dm0m.bankingapi.model;

import java.math.BigDecimal;

import io.github.dm0m.bankingapi.model.enums.AccountCurrency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity 
@Table(name = "transfers")
public class Transfer extends AuditableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transfers_id_seq")
    @SequenceGenerator(name = "transfers_id_seq", sequenceName = "transfers_id_seq", allocationSize = 50)
    @Column(name = "id")
    private Long id;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Column(name = "currency", nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountCurrency currency;

    protected Transfer() {
    }

    public Transfer(Account fromAccount, Account toAccount, BigDecimal amount, AccountCurrency currency) {
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount.setScale(4); // same scale as NUMERIC(19,4) and account balances; @Digits on the request guarantees no rounding is needed

        this.currency = currency;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Account getFromAccount() {
        return fromAccount;
    }

    public Account getToAccount() {
        return toAccount;
    }

    public AccountCurrency getCurrency() {
        return currency;
    }
}
