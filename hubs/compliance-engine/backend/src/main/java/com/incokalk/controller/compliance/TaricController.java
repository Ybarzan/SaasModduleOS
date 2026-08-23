package com.incokalk.controller.compliance;

import com.incokalk.dto.taric.TaricMeasureDto;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.taric.TaricApiClient;
import com.incokalk.service.taric.TaricSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/taric")
@RequiredArgsConstructor
@Tag(name = "TARIC", description = "API TARIC (Tarif Intégré Communautaire)")
@RequiresPlan(Company.Plan.PRO)
public class TaricController {

    private final TaricSyncService taricSyncService;
    private final TaricApiClient taricApiClient;

    @PostMapping("/sync/{hsCode}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Rafraîchir les taux TARIC pour un code HS depuis l'API EU")
    public ResponseEntity<List<TaricMeasureDto>> refreshRates(
            @PathVariable String hsCode,
            @RequestParam(defaultValue = "CN") String origin,
            @RequestParam(defaultValue = "FR") String dest) {
        return ResponseEntity.ok(taricSyncService.refreshHsCode(hsCode, origin, dest));
    }

    @PostMapping("/sync/daily")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Déclencher manuellement la synchronisation quotidienne TARIC")
    public ResponseEntity<Map<String, Object>> triggerDailySync() {
        taricSyncService.syncDaily();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "triggered");
        resp.put("totalRates", taricSyncService.getTotalRates());
        resp.put("distinctHsCodes", taricSyncService.getDistinctHsCount());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/lookup")
    @Operation(summary = "Rechercher les taux TARIC pour un code HS via API EU")
    public ResponseEntity<List<TaricMeasureDto>> lookupRates(
            @RequestParam String hsCode,
            @RequestParam String origin,
            @RequestParam(defaultValue = "FR") String dest) {
        List<TaricMeasureDto> rates = taricApiClient.fetchRates(hsCode, origin, dest);
        return ResponseEntity.ok(rates);
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Statistiques TARIC")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRates", taricSyncService.getTotalRates());
        stats.put("distinctHsCodes", taricSyncService.getDistinctHsCount());
        stats.put("lastSync", "N/A");
        return ResponseEntity.ok(stats);
    }
}
