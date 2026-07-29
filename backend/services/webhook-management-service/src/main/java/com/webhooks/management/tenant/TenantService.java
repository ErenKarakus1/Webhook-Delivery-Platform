package com.webhooks.management.tenant;

import com.webhooks.management.common.ResourceNotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {
    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        Tenant tenant = tenantRepository.save(new Tenant(request.name()));
        return TenantResponse.from(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> listTenants() {
        return tenantRepository.findAll().stream()
                .sorted(Comparator.comparing(Tenant::getCreatedAt).reversed())
                .map(TenantResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(UUID tenantId) {
        return TenantResponse.from(getTenantEntity(tenantId));
    }

    @Transactional
    public TenantResponse updateTenant(UUID tenantId, UpdateTenantRequest request) {
        Tenant tenant = getTenantEntity(tenantId);
        tenant.setName(request.name());
        return TenantResponse.from(tenant);
    }

    @Transactional
    public void deleteTenant(UUID tenantId) {
        Tenant tenant = getTenantEntity(tenantId);
        tenantRepository.delete(tenant);
    }

    @Transactional(readOnly = true)
    public Tenant getTenantEntity(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
    }
}
