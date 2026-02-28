package com.ecommerce.project.model;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

//@Entity // <--- 1. Tells Spring this is a database table
@Entity(name = "categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    @NotBlank
    @Size(min =5, message = "Category name must contains atLeast 5 characters")
    private String categoryName;

    @OneToMany(mappedBy = "category" ,cascade =CascadeType.ALL)
    private List<Product> products;
}
