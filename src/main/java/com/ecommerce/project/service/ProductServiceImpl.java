package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.CartDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.repositories.CartItemRepository;
import com.ecommerce.project.repositories.CartRepository;
import com.ecommerce.project.repositories.CategoryRepository;
import com.ecommerce.project.repositories.ProductRepository;
import com.ecommerce.project.util.AuthUtil;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@Transactional
public class ProductServiceImpl implements ProductService{

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    AuthUtil authUtil;

    @Autowired
    CartItemRepository cartItemRepository;


    @Value("${project.image}")
    private String path;

    @Value("${image.base.url}")
    private String imageBaseUrl;


    @Override
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "featuredProducts", allEntries = true)
    })
    public ProductDTO addProduct(Long categoryId, ProductDTO productDTO) {
        //Check if product is present or not
        Category category  = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category","categoryId",categoryId));

        boolean isProductNotPresent = true;

        List<Product> products = category.getProducts();
        for (Product value : products) {
            if (value.getProductName().equals(productDTO.getProductName())) {
                isProductNotPresent = false;
                break;
            }

        }
        if (isProductNotPresent) {
            Product product = modelMapper.map(productDTO, Product.class);
            product.setImage("default.png");
            if (product.getImages() == null) {
                product.setImages(new java.util.ArrayList<>());
            }
            product.setCategory(category);
            double specialPrice = product.getPrice() - ((product.getDiscount() * 0.01) * product.getPrice());
            product.setSpecialPrice(specialPrice);
            Product savedProduct = productRepository.save(product);
            return mapProductToDTO(savedProduct);
        }else {
            throw new APIException("Product already exist");
        }

    }

    @Override
    @Cacheable(value = "products",
            key = "#pageNumber + '-' + #pageSize + '-' + #sortBy + '-' + #sortOrder + '-' + #keyword + '-' + #category")
    public ProductResponse getAllProducts(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String keyword, String category) {

        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber,pageSize,sortByAndOrder);

        // REPLACE THIS:
// Specification<Product> spec = Specification.where((Specification<Product>) null);

// WITH THIS (The modern standard):
        Specification<Product> spec = Specification.allOf();

        spec = spec.and((root, query, criteriaBuilder) ->
                criteriaBuilder.isTrue(root.get("isActive"))
        );

// 2. Keyword Search (Case-insensitive & Partial Match)
        if (keyword != null && !keyword.isEmpty()) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("productName")),
                            "%" + keyword.toLowerCase() + "%"
                    )
            );
        }

// 3. Category Search (Case-insensitive & Exact Match)
        if (category != null && !category.isEmpty()) {
            spec = spec.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("category").get("categoryName")),
                            category.toLowerCase()
                    )
            );
        }





// 4. Execute the query
        Page<Product> pageProducts = productRepository.findAll(spec, pageDetails);

       List<Product> products= pageProducts.getContent();
       List<ProductDTO> productDTOS = products.stream()
               .map(this::mapProductToDTO)
               .toList();

//       if (products.isEmpty()) {
//           throw new APIException("No Products Exists!!");
//       }
       ProductResponse productResponse = new ProductResponse();
       productResponse.setContent(productDTOS);
       productResponse.setPageNumber(pageProducts.getNumber());
       productResponse.setPageSize(pageProducts.getSize());
       productResponse.setTotalElements(pageProducts.getTotalElements());
       productResponse.setTotalPages(pageProducts.getTotalPages());
       productResponse.setLastPage(pageProducts.isLast());
        return productResponse;
    }


    @Override
    @Cacheable(value = "products",
            key = "'admin-' + #pageNumber + '-' + #pageSize + '-' + #sortBy + '-' + #sortOrder")
    public ProductResponse getAllProductsForAdmin(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        // -> ADD THIS: Create a simple spec to only get active products
        Specification<Product> spec = (root, query, criteriaBuilder) ->
                criteriaBuilder.isTrue(root.get("isActive"));

        // -> CHANGE THIS: Pass the spec into findAll
        Page<Product> pageProducts = productRepository.findAll(spec,pageDetails);

        List<Product> products = pageProducts.getContent();

        List<ProductDTO> productDTOS = products.stream()
                .map(this::mapProductToDTO)
                .toList();

        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOS);
        productResponse.setPageNumber(pageProducts.getNumber());
        productResponse.setPageSize(pageProducts.getSize());
        productResponse.setTotalElements(pageProducts.getTotalElements());
        productResponse.setTotalPages(pageProducts.getTotalPages());
        productResponse.setLastPage(pageProducts.isLast());
        return productResponse;
    }

    @Override
    public ProductResponse getAllProductsForSeller(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        User user = authUtil.loggedInUser();
        Page<Product> pageProducts = productRepository.findByUser(user, pageDetails);

        List<Product> products = pageProducts.getContent();

        List<ProductDTO> productDTOS = products.stream()
                .map(this::mapProductToDTO)
                .toList();

        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOS);
        productResponse.setPageNumber(pageProducts.getNumber());
        productResponse.setPageSize(pageProducts.getSize());
        productResponse.setTotalElements(pageProducts.getTotalElements());
        productResponse.setTotalPages(pageProducts.getTotalPages());
        productResponse.setLastPage(pageProducts.isLast());
        return productResponse;
    }



    private String constructImageUrl(String imageName){
        // 1. If it's already a full Cloudinary URL, just return it directly!
        if (imageName != null && (imageName.startsWith("http://") || imageName.startsWith("https://"))) {
            return imageName;
        }

        // 2. If it's the "default.png" or an old local image, append the backend URL
        return imageBaseUrl.endsWith("/") ? imageBaseUrl + imageName : imageBaseUrl + "/" + imageName;
    }

    @Override
    public ProductResponse searchByCategory(Long categoryId, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category" , "categoryId", categoryId));

        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber,pageSize,sortByAndOrder);
        Page<Product> pageProducts = productRepository.findByCategoryOrderByPriceAsc(category,pageDetails);

        List<Product> products= pageProducts.getContent();

        if (products.isEmpty()) {
            throw new APIException(category.getCategoryName()+"In this Category does not exists this product!!");
        }

        List<ProductDTO> productDTOS = products.stream()
                .map(this::mapProductToDTO)
                .toList();

        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOS);
        productResponse.setPageNumber(pageProducts.getNumber());
        productResponse.setPageSize(pageProducts.getSize());
        productResponse.setTotalElements(pageProducts.getTotalElements());
        productResponse.setTotalPages(pageProducts.getTotalPages());
        productResponse.setLastPage(pageProducts.isLast());
        return productResponse;
    }

    @Override
    public ProductResponse searchProductByKeyword(String keyword, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {

        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber,pageSize,sortByAndOrder);
        Page<Product> pageProducts = productRepository.findByProductNameLikeIgnoreCase('%' + keyword + '%',pageDetails);

        // product size is 0
        List<Product> products = pageProducts.getContent();
        List<ProductDTO> productDTOS = products.stream()
                .map(this::mapProductToDTO)
                .toList();

        if (products.isEmpty()) {
            throw new APIException("No Products Exists!!");
        }
        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOS);
        productResponse.setPageNumber(pageProducts.getNumber());
        productResponse.setPageSize(pageProducts.getSize());
        productResponse.setTotalElements(pageProducts.getTotalElements());
        productResponse.setTotalPages(pageProducts.getTotalPages());
        productResponse.setLastPage(pageProducts.isLast());
        return productResponse;
    }

    @Override
    @Cacheable(value = "productById", key = "#productId")
    public ProductDTO getProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        return mapProductToDTO(product);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productById", key = "#productId"),
            @CacheEvict(value = "featuredProducts", allEntries = true)
    })
    public ProductDTO updateProduct(Long productId, ProductDTO productDTO) {
        //Get the existing product from DB
        Product productFromDb = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product","productId",productId));

        Product product = modelMapper.map(productDTO, Product.class);
        //Update the Product info with the one in request Body
        productFromDb.setProductName(product.getProductName());
        productFromDb.setDescription(product.getDescription());
        productFromDb.setQuantity(product.getQuantity());
        productFromDb.setDiscount(product.getDiscount());
        productFromDb.setPrice(product.getPrice());
        // Copy variation fields (sizes, colors & gallery images)
        productFromDb.setSizes(product.getSizes() != null ? product.getSizes() : new java.util.ArrayList<>());
        productFromDb.setColors(product.getColors() != null ? product.getColors() : new java.util.ArrayList<>());
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            productFromDb.setImages(product.getImages());
        }
        double specialPrice = product.getPrice() - ((product.getDiscount() * 0.01) * product.getPrice());
        productFromDb.setSpecialPrice(specialPrice);
        //save to database
        Product savedProduct= productRepository.save(productFromDb);

        List<Cart> carts =cartRepository.findCardByProductId(productId);

        List<CartDTO> cartDTOs =carts.stream().map(cart ->{
            CartDTO cartDTO = modelMapper.map(cart,CartDTO.class);
            List<ProductDTO> products =cart.getCartItems().stream()
                    .map(p -> mapProductToDTO(p.getProduct()))
                    .toList();
            cartDTO.setProducts(products);
            return cartDTO;
        }).toList();

        cartDTOs.forEach(cart -> cartService.updateProductInCarts(cart.getCartId(), productId));

        return mapProductToDTO(savedProduct);

    }

//    @Transactional // <--- Essential for hard deletes to ensure Cart cleanup happens first
//    @Override
//    public ProductDTO deleteProduct(Long productId) {
//        // 1. Find the product (We need it to map it to DTO before deleting)
//        Product product = productRepository.findById(productId)
//                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
//
//        // 2. Remove this product from all Carts
//        // CRITICAL: You must do this BEFORE the hard delete to avoid Foreign Key errors!
//        List<Cart> carts = cartRepository.findCardByProductId(productId);
//        carts.forEach(cart -> cartService.deleteProductFromCart(cart.getCartId(), productId));
//
//        // 3. HARD DELETE: Permanently remove the row from the database
//        productRepository.delete(product);
//
//        // (Note: No need to call .save(), because the entity is gone)
//
//        // 4. Return the DTO of the deleted item
//        // The Java object 'product' still exists in memory here, so we can map it
//        return modelMapper.map(product, ProductDTO.class);
//    }

//    @Transactional
//    @Override
//    public ProductDTO deleteProduct(Long productId) {
//        Product product = productRepository.findById(productId)
//                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
//
//        // Step 1: Fix each cart's total price BEFORE wiping cart items
//        List<Cart> carts = cartRepository.findCardByProductId(productId);
//        carts.forEach(cart -> {
//            CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(productId, cart.getCartId());
//            if (cartItem != null) {
//                cart.setTotalPrice(cart.getTotalPrice() - (cartItem.getProductPrice() * cartItem.getQuantity()));
//                cartRepository.save(cart);
//            }
//        });
//
//        // Step 2: Delete all cart items via @Modifying (clearAutomatically evicts them from L1 cache)
//        cartItemRepository.deleteAllByProductId(productId);
//
//        // Step 3: Now safe — no dangling references to remain in the session
//        productRepository.delete(product);
//
//        return modelMapper.map(product, ProductDTO.class);
//    }


    @Transactional
    @Override
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productById", key = "#productId"),
            @CacheEvict(value = "featuredProducts", allEntries = true)
    })
    public ProductDTO deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        // Step 1: Clean up the carts
        List<Cart> carts = cartRepository.findCardByProductId(productId);
        carts.forEach(cart -> {
            CartItem cartItem = cartItemRepository.findCartItemByProductIdAndCartId(productId, cart.getCartId());
            if (cartItem != null) {
                cart.setTotalPrice(cart.getTotalPrice() - (cartItem.getProductPrice() * cartItem.getQuantity()));
                cartRepository.save(cart);
            }
        });
        cartItemRepository.deleteAllByProductId(productId);

        // Step 2: SOFT DELETE
        // Make sure this next line is DELETED or COMMENTED OUT:
        // productRepository.delete(product);

        // Make sure you are doing this INSTEAD:
        product.setIsActive(false);
        Product savedProduct = productRepository.save(product);

        return modelMapper.map(savedProduct, ProductDTO.class);
    }


    @Override
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productById", key = "#productId"),
            @CacheEvict(value = "featuredProducts", allEntries = true)
    })
    public ProductDTO updateProductImage(Long productId, MultipartFile image) throws IOException {
        // get product from DB
        Product productFromDB =productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product","product",productId));

        //Upload image to server
        String fileName= fileService.uploadImage(path ,image);

        //Updating the new file name to the product
        productFromDB.setImage(fileName);
        if (productFromDB.getImages() == null) {
            productFromDB.setImages(new java.util.ArrayList<>());
        }
        if (!productFromDB.getImages().contains(fileName) && productFromDB.getImages().size() < 5) {
            productFromDB.getImages().add(0, fileName);
        }

        //Save Updated product
        Product updatedProduct = productRepository.saveAndFlush(productFromDB);

        return mapProductToDTO(updatedProduct);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "products", allEntries = true),
            @CacheEvict(value = "productById", key = "#productId"),
            @CacheEvict(value = "featuredProducts", allEntries = true)
    })
    public ProductDTO updateProductImages(Long productId, List<MultipartFile> images) throws IOException {
        Product productFromDB = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        if (productFromDB.getImages() == null) {
            productFromDB.setImages(new java.util.ArrayList<>());
        }

        int currentCount = productFromDB.getImages().size();
        int newCount = (images != null) ? images.size() : 0;
        int totalCount = currentCount + newCount;

        if (totalCount < 2) {
            throw new APIException("A product must have at least 2 images. Currently has " + currentCount + ", uploading " + newCount + " (total: " + totalCount + "). Please upload more images.");
        }
        if (totalCount > 5) {
            throw new APIException("A product can have a maximum of 5 images. Currently has " + currentCount + ", uploading " + newCount + " (would be total: " + totalCount + "). You can upload at most " + (5 - currentCount) + " more image(s).");
        }

        List<String> uploadedUrls = fileService.uploadMultipleImages(path, images);
        for (String url : uploadedUrls) {
            if (!productFromDB.getImages().contains(url)) {
                productFromDB.getImages().add(url);
            }
        }

        if (("default.png".equals(productFromDB.getImage()) || productFromDB.getImage() == null) && !productFromDB.getImages().isEmpty()) {
            productFromDB.setImage(productFromDB.getImages().get(0));
        }

        Product updatedProduct = productRepository.saveAndFlush(productFromDB);
        return mapProductToDTO(updatedProduct);
    }

    @Override
    @Cacheable(value = "featuredProducts", key = "'featured-all'")
    public List<ProductDTO> getFeaturedProducts() {
        // 1. Fetch from DB using the new Repo method
        List<Product> featuredProducts = productRepository.findByIsFeaturedTrue();

        // 2. Map to DTOs (and fix Image URLs!)
        return featuredProducts.stream()
                .map(this::mapProductToDTO)
                .toList();
    }

    private ProductDTO mapProductToDTO(Product product) {
        ProductDTO dto = modelMapper.map(product, ProductDTO.class);
        dto.setImage(constructImageUrl(product.getImage()));
        if (product.getImages() != null) {
            dto.setImages(product.getImages().stream().map(this::constructImageUrl).toList());
        } else {
            dto.setImages(new java.util.ArrayList<>());
        }
        return dto;
    }

}
