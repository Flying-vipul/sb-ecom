package com.ecommerce.project.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "products" )
@ToString
@Getter
@Setter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long productId;

    @NotBlank
    @Size(min = 3,message = "Product name must contain at least 3 characters")
    private String productName;
    private String image;

    @NotBlank
    @Size(min = 6,message = "Product name must contain at least 6 characters")
    private String description;
    private Integer quantity;
    private double price; // 100

    private double discount; // 25 %
    private double specialPrice; // final price = 75

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "seller_id")
    private User user;

    @OneToMany(mappedBy = "product", cascade = {CascadeType.MERGE,CascadeType.PERSIST},fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<CartItem> products = new ArrayList<>();

    // specific getter/setter if using Lombok just check it exists
    @Setter
    @Column(name = "is_active")
    private Boolean isActive = true; // Default to true (Active)

    // Getters and Setters
    @Column(name = "is_featured") // Good practice for SQL naming
    private boolean isFeatured = false;

}
