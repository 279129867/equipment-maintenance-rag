package com.eqmaint.asset;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eqmaint.common.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    public R<Page<EqAsset>> page(@RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "10") int size,
                                 @RequestParam(required = false) String workshop,
                                 @RequestParam(required = false) String assetType,
                                 @RequestParam(required = false) String keyword) {
        return R.ok(service.page(page, size, workshop, assetType, keyword));
    }

    @GetMapping("/{id}")
    public R<EqAsset> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @PostMapping
    public R<EqAsset> create(@Valid @RequestBody EqAsset asset) {
        return R.ok(service.create(asset));
    }

    @PutMapping("/{id}")
    public R<EqAsset> update(@PathVariable Long id, @RequestBody EqAsset asset) {
        return R.ok(service.update(id, asset));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}