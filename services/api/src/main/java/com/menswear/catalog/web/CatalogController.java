package com.menswear.catalog.web;

import com.menswear.catalog.dto.CatalogDtos;
import com.menswear.catalog.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public List<CatalogDtos.CategoryResponse> categories() {
        return catalogService.categories();
    }

    @GetMapping("/products")
    public List<CatalogDtos.ProductResponse> products(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q
    ) {
        return catalogService.products(categoryId, q);
    }

    @GetMapping("/products/{slug}")
    public CatalogDtos.ProductResponse product(@PathVariable String slug) {
        return catalogService.productBySlug(slug);
    }

    @GetMapping("/fabrics")
    public List<CatalogDtos.FabricTierResponse> fabrics() {
        return catalogService.fabrics();
    }

    @PostMapping(value = "/admin/products", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CatalogDtos.ProductResponse createProductJson(@Valid @RequestBody CatalogDtos.CreateProductRequest request) {
        return catalogService.createProduct(request);
    }

    @PostMapping(value = "/admin/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CatalogDtos.ProductResponse createProductMultipart(
            @RequestParam String name,
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) String description,
            @RequestParam Long basePricePaisa,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "true") Boolean supportsCustom,
            @RequestParam(required = false, defaultValue = "true") Boolean active,
            @RequestParam(required = false) List<String> imageUrls,
            @RequestParam(required = false) MultipartFile[] images
    ) {
        List<MultipartFile> files = images == null ? List.of() : Arrays.asList(images);
        return catalogService.createProductWithUploads(
                name,
                slug,
                description,
                basePricePaisa,
                categoryId,
                supportsCustom,
                active,
                imageUrls,
                files
        );
    }
}
