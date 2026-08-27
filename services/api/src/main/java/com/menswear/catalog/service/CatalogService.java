package com.menswear.catalog.service;

import com.menswear.catalog.dto.CatalogDtos;
import com.menswear.catalog.entity.FabricColor;
import com.menswear.catalog.entity.FabricTier;
import com.menswear.catalog.entity.Product;
import com.menswear.catalog.entity.ProductImage;
import com.menswear.catalog.repo.CategoryRepository;
import com.menswear.catalog.repo.FabricTierRepository;
import com.menswear.catalog.repo.ProductRepository;
import com.menswear.common.exception.BadRequestException;
import com.menswear.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class CatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final FabricTierRepository fabricTierRepository;
    private final FileStorageService fileStorageService;

    public CatalogService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            FabricTierRepository fabricTierRepository,
            FileStorageService fileStorageService
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.fabricTierRepository = fabricTierRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.CategoryResponse> categories() {
        return categoryRepository.findAll().stream()
                .map(c -> new CatalogDtos.CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.ProductResponse> products(Long categoryId, String q) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        List<Product> products;
        if (query == null && categoryId == null) {
            products = productRepository.findByActiveTrueOrderByCreatedAtDesc();
        } else if (query == null) {
            products = productRepository.findByActiveTrueAndCategoryIdOrderByCreatedAtDesc(categoryId);
        } else if (categoryId == null) {
            products = productRepository.searchByName(query);
        } else {
            products = productRepository.searchByCategoryAndName(categoryId, query);
        }
        return products.stream().map(this::toProduct).toList();
    }

    @Transactional(readOnly = true)
    public CatalogDtos.ProductResponse productBySlug(String slug) {
        Product product = productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        return toProduct(product);
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.FabricTierResponse> fabrics() {
        return fabricTierRepository.findAllByOrderBySortOrderAsc().stream().map(this::toTier).toList();
    }

    @Transactional
    public CatalogDtos.ProductResponse createProduct(CatalogDtos.CreateProductRequest request) {
        return createProductInternal(
                request.name(),
                request.slug(),
                request.description(),
                request.basePricePaisa(),
                request.categoryId(),
                request.supportsCustom(),
                request.active(),
                request.images() == null ? List.of() : request.images().stream().map(CatalogDtos.ProductImageRequest::url).toList(),
                List.of()
        );
    }

    @Transactional
    public CatalogDtos.ProductResponse createProductWithUploads(
            String name,
            String slug,
            String description,
            Long basePricePaisa,
            Long categoryId,
            Boolean supportsCustom,
            Boolean active,
            List<String> imageUrls,
            List<org.springframework.web.multipart.MultipartFile> files
    ) {
        return createProductInternal(
                name, slug, description, basePricePaisa, categoryId, supportsCustom, active,
                imageUrls == null ? List.of() : imageUrls,
                files == null ? List.of() : files
        );
    }

    private CatalogDtos.ProductResponse createProductInternal(
            String name,
            String slug,
            String description,
            Long basePricePaisa,
            Long categoryId,
            Boolean supportsCustom,
            Boolean active,
            List<String> imageUrls,
            List<org.springframework.web.multipart.MultipartFile> files
    ) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Name is required");
        }
        if (basePricePaisa == null || basePricePaisa < 1) {
            throw new BadRequestException("Price must be at least 1 paisa");
        }

        String finalSlug = slugify(slug == null || slug.isBlank() ? name : slug);
        if (productRepository.existsBySlugIgnoreCase(finalSlug)) {
            throw new BadRequestException("Slug already exists: " + finalSlug);
        }
        if (categoryId != null) {
            categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new NotFoundException("Category not found"));
        }

        List<String> urls = new java.util.ArrayList<>();
        for (String url : imageUrls) {
            if (url != null && !url.isBlank()) {
                urls.add(url.trim());
            }
        }
        for (org.springframework.web.multipart.MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                urls.add(fileStorageService.storeProductImage(file));
            }
        }
        if (urls.isEmpty()) {
            throw new BadRequestException("Add at least one image (upload files or URLs)");
        }

        Product product = Product.builder()
                .name(name.trim())
                .slug(finalSlug)
                .description(description)
                .basePricePaisa(basePricePaisa)
                .currency("PKR")
                .categoryId(categoryId)
                .supportsCustom(supportsCustom == null || supportsCustom)
                .active(active == null || active)
                .build();

        int index = 0;
        for (String url : urls) {
            ProductImage image = ProductImage.builder()
                    .product(product)
                    .url(url)
                    .altText(name.trim())
                    .sortOrder(index++)
                    .build();
            product.getImages().add(image);
        }

        return toProduct(productRepository.save(product));
    }

    private String slugify(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (s.isBlank()) {
            throw new BadRequestException("Invalid slug");
        }
        return s;
    }

    private CatalogDtos.ProductResponse toProduct(Product p) {
        return new CatalogDtos.ProductResponse(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getDescription(),
                p.getBasePricePaisa(),
                p.getCurrency(),
                p.isSupportsCustom(),
                p.getCategoryId(),
                p.getImages().stream()
                        .map(i -> new CatalogDtos.ProductImageResponse(i.getUrl(), i.getAltText(), i.getSortOrder()))
                        .toList()
        );
    }

    private CatalogDtos.FabricTierResponse toTier(FabricTier tier) {
        List<CatalogDtos.FabricColorResponse> colors = tier.getColors().stream()
                .map(this::toColor)
                .toList();
        return new CatalogDtos.FabricTierResponse(
                tier.getId(),
                tier.getCode(),
                tier.getName(),
                tier.getSurchargePaisa(),
                colors
        );
    }

    private CatalogDtos.FabricColorResponse toColor(FabricColor color) {
        return new CatalogDtos.FabricColorResponse(color.getId(), color.getCode(), color.getName(), color.getHexColor());
    }
}
