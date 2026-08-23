package com.incokalk.controller.shipment;

import com.incokalk.dto.shipment.QuoteRequestDTO;
import com.incokalk.dto.shipment.QuoteResponseDTO;
import com.incokalk.model.Company;
import com.incokalk.security.RequiresPlan;
import com.incokalk.service.QuoteService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/quotes")
@RequiredArgsConstructor
@Tag(name = "Quotes", description = "Demandes de devis transport")
@RequiresPlan(Company.Plan.STARTER)
public class QuoteController {

    private final QuoteService quoteService;

    @PostMapping
    @Operation(summary = "Obtenir des devis transport")
    public ResponseEntity<List<QuoteResponseDTO>> getQuotes(
            @Valid @RequestBody QuoteRequestDTO request,
            HttpServletRequest httpReq) {
        UUID companyId = TenantContext.get();
        return ResponseEntity.ok(quoteService.getQuotes(request, companyId));
    }
}
