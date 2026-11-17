package io.github.dm0m.bankingapi.model;

import java.util.Locale;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;

@Entity
@Table(name = "addresses")
public class Address extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "addresses_id_seq")
    @SequenceGenerator(name = "addresses_id_seq", sequenceName = "addresses_id_seq", allocationSize = 50)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "street_no", nullable = false)
    private String streetNo;

    @Column(name = "street", nullable = false)
    private String street;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "zip_code", nullable = false)
    private String zipCode;

    // CHAR(2) in the database; without @JdbcTypeCode, ddl-auto=validate expects VARCHAR
    @Column(name = "country", nullable = false, length = 2)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String country;

    public String getStreetNo() {
        return streetNo;
    }

    public void setStreetNo(String streetNo) {
        this.streetNo = streetNo;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country.trim().toUpperCase(Locale.ROOT);
    }

    
}
