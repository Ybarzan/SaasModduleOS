package com.incokalk.service.erp;

import com.incokalk.model.ErpConfig;
import com.incokalk.model.ShipmentOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class OdooProvider implements ErpProvider {

    private final RestTemplate restTemplate;

    public OdooProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getErpType() {
        return "ODOO";
    }

    @Override
    public String getName() {
        return "Odoo";
    }

    @Override
    public boolean testConnection(ErpConfig config) {
        try {
            String url = buildXmlRpcUrl(config, "common");
            String xml = buildXmlRpcCall("version", List.of());
            ResponseEntity<String> response = postXmlRpc(url, xml);
            return response.getStatusCode().is2xxSuccessful() && response.getBody() != null;
        } catch (Exception e) {
            log.error("[Odoo] Échec test de connexion: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public ErpSyncResult importProducts(ErpConfig config) {
        log.info("[Odoo] Import des produits depuis {}", config.getApiEndpoint());
        try {
            int uid = authenticate(config);
            String url = buildXmlRpcUrl(config, "object");
            String xml = buildXmlRpcCall("execute_kw",
                    List.of(config.getDatabaseName(), uid, config.getApiKey(),
                            "product.template", "search_read",
                            List.of(List.of()),
                            Map.of("fields", List.of("name", "default_code", "list_price", "type", "categ_id", "qty_available"))));

            ResponseEntity<String> response = postXmlRpc(url, xml);
            List<Map<String, Object>> records = parseXmlRpcArray(response.getBody());

            log.info("[Odoo] {} produits importés", records.size());
            return ErpSyncResult.builder()
                    .success(true)
                    .recordsTotal(records.size())
                    .recordsSynced(records.size())
                    .recordsFailed(0)
                    .build();
        } catch (Exception e) {
            log.error("[Odoo] Erreur import produits: {}", e.getMessage());
            return ErpSyncResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public ErpSyncResult importOrders(ErpConfig config) {
        log.info("[Odoo] Import des commandes depuis {}", config.getApiEndpoint());
        try {
            int uid = authenticate(config);
            String url = buildXmlRpcUrl(config, "object");
            String xml = buildXmlRpcCall("execute_kw",
                    List.of(config.getDatabaseName(), uid, config.getApiKey(),
                            "sale.order", "search_read",
                            List.of(List.of(List.of("state", "!=", "cancel"))),
                            Map.of("fields", List.of("name", "partner_id", "date_order", "state", "amount_total", "currency_id"))));

            ResponseEntity<String> response = postXmlRpc(url, xml);
            List<Map<String, Object>> records = parseXmlRpcArray(response.getBody());

            log.info("[Odoo] {} commandes importées", records.size());
            return ErpSyncResult.builder()
                    .success(true)
                    .recordsTotal(records.size())
                    .recordsSynced(records.size())
                    .recordsFailed(0)
                    .build();
        } catch (Exception e) {
            log.error("[Odoo] Erreur import commandes: {}", e.getMessage());
            return ErpSyncResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public ErpSyncResult importContacts(ErpConfig config) {
        log.info("[Odoo] Import des contacts depuis {}", config.getApiEndpoint());
        try {
            int uid = authenticate(config);
            String url = buildXmlRpcUrl(config, "object");
            String xml = buildXmlRpcCall("execute_kw",
                    List.of(config.getDatabaseName(), uid, config.getApiKey(),
                            "res.partner", "search_read",
                            List.of(List.of()),
                            Map.of("fields", List.of("name", "email", "phone", "street", "city", "country_id", "is_company"))));

            ResponseEntity<String> response = postXmlRpc(url, xml);
            List<Map<String, Object>> records = parseXmlRpcArray(response.getBody());

            log.info("[Odoo] {} contacts importés", records.size());
            return ErpSyncResult.builder()
                    .success(true)
                    .recordsTotal(records.size())
                    .recordsSynced(records.size())
                    .recordsFailed(0)
                    .build();
        } catch (Exception e) {
            log.error("[Odoo] Erreur import contacts: {}", e.getMessage());
            return ErpSyncResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public ErpSyncResult exportShipments(ErpConfig config, List<ShipmentOrder> shipments) {
        log.info("[Odoo] Export de {} expéditions vers stock.picking", shipments.size());
        try {
            int uid = authenticate(config);
            String url = buildXmlRpcUrl(config, "object");
            int synced = 0;
            int failed = 0;

            for (ShipmentOrder shipment : shipments) {
                try {
                    Map<String, Object> pickingVals = Map.of(
                            "origin", shipment.getOrderNumber() != null ? shipment.getOrderNumber() : "",
                            "partner_id", shipment.getConsigneeName() != null ? shipment.getConsigneeName() : "",
                            "move_ids", List.of()
                    );
                    String xml = buildXmlRpcCall("execute_kw",
                            List.of(config.getDatabaseName(), uid, config.getApiKey(),
                                    "stock.picking", "create",
                                    List.of(pickingVals)));
                    ResponseEntity<String> response = postXmlRpc(url, xml);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        synced++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    log.warn("[Odoo] Échec export shipment {}: {}", shipment.getOrderNumber(), e.getMessage());
                    failed++;
                }
            }

            log.info("[Odoo] {} expéditions exportées, {} échouées", synced, failed);
            return ErpSyncResult.builder()
                    .success(failed == 0)
                    .recordsTotal(shipments.size())
                    .recordsSynced(synced)
                    .recordsFailed(failed)
                    .build();
        } catch (Exception e) {
            log.error("[Odoo] Erreur export expéditions: {}", e.getMessage());
            return ErpSyncResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public ErpSyncResult exportOrders(ErpConfig config, List<ShipmentOrder> orders) {
        log.info("[Odoo] Export de {} commandes vers sale.order", orders.size());
        try {
            int uid = authenticate(config);
            String url = buildXmlRpcUrl(config, "object");
            int synced = 0;
            int failed = 0;

            for (ShipmentOrder order : orders) {
                try {
                    Map<String, Object> orderVals = Map.of(
                            "origin", order.getOrderNumber() != null ? order.getOrderNumber() : "",
                            "partner_id", order.getConsigneeName() != null ? order.getConsigneeName() : "",
                            "order_line", List.of()
                    );
                    String xml = buildXmlRpcCall("execute_kw",
                            List.of(config.getDatabaseName(), uid, config.getApiKey(),
                                    "sale.order", "create",
                                    List.of(orderVals)));
                    ResponseEntity<String> response = postXmlRpc(url, xml);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        synced++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    log.warn("[Odoo] Échec export commande {}: {}", order.getOrderNumber(), e.getMessage());
                    failed++;
                }
            }

            log.info("[Odoo] {} commandes exportées, {} échouées", synced, failed);
            return ErpSyncResult.builder()
                    .success(failed == 0)
                    .recordsTotal(orders.size())
                    .recordsSynced(synced)
                    .recordsFailed(failed)
                    .build();
        } catch (Exception e) {
            log.error("[Odoo] Erreur export commandes: {}", e.getMessage());
            return ErpSyncResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public List<Map<String, Object>> getProducts(ErpConfig config) {
        try {
            int uid = authenticate(config);
            String url = buildXmlRpcUrl(config, "object");
            String xml = buildXmlRpcCall("execute_kw",
                    List.of(config.getDatabaseName(), uid, config.getApiKey(),
                            "product.template", "search_read",
                            List.of(List.of()),
                            Map.of("fields", List.of("name", "default_code", "list_price", "type", "categ_id", "qty_available"))));
            ResponseEntity<String> response = postXmlRpc(url, xml);
            return parseXmlRpcArray(response.getBody());
        } catch (Exception e) {
            log.error("[Odoo] Erreur récupération produits: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getOrders(ErpConfig config) {
        try {
            int uid = authenticate(config);
            String url = buildXmlRpcUrl(config, "object");
            String xml = buildXmlRpcCall("execute_kw",
                    List.of(config.getDatabaseName(), uid, config.getApiKey(),
                            "sale.order", "search_read",
                            List.of(List.of(List.of("state", "!=", "cancel"))),
                            Map.of("fields", List.of("name", "partner_id", "date_order", "state", "amount_total", "currency_id"))));
            ResponseEntity<String> response = postXmlRpc(url, xml);
            return parseXmlRpcArray(response.getBody());
        } catch (Exception e) {
            log.error("[Odoo] Erreur récupération commandes: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getContacts(ErpConfig config) {
        try {
            int uid = authenticate(config);
            String url = buildXmlRpcUrl(config, "object");
            String xml = buildXmlRpcCall("execute_kw",
                    List.of(config.getDatabaseName(), uid, config.getApiKey(),
                            "res.partner", "search_read",
                            List.of(List.of()),
                            Map.of("fields", List.of("name", "email", "phone", "street", "city", "country_id", "is_company"))));
            ResponseEntity<String> response = postXmlRpc(url, xml);
            return parseXmlRpcArray(response.getBody());
        } catch (Exception e) {
            log.error("[Odoo] Erreur récupération contacts: {}", e.getMessage());
            return List.of();
        }
    }

    private int authenticate(ErpConfig config) {
        String url = buildXmlRpcUrl(config, "common");
        String xml = buildXmlRpcCall("authenticate",
                List.of(config.getDatabaseName(), config.getUsername(), config.getApiKey(), Map.of()));
        ResponseEntity<String> response = postXmlRpc(url, xml);
        String body = response.getBody();
        if (body == null || !response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Authentification Odoo échouée");
        }
        int uid = parseXmlRpcInt(body);
        if (uid <= 0) {
            throw new RuntimeException("Authentification Odoo échouée: identifiants invalides");
        }
        return uid;
    }

    private String buildXmlRpcUrl(ErpConfig config, String endpoint) {
        String base = config.getApiEndpoint();
        if (base == null || base.isBlank()) {
            throw new RuntimeException("Endpoint API Odoo non configuré");
        }
        base = base.replaceAll("/$", "");
        return base + "/xmlrpc/2/" + endpoint;
    }

    private String buildXmlRpcCall(String method, List<Object> params) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\"?>\n");
        xml.append("<methodCall>\n");
        xml.append("  <methodName>").append(method).append("</methodName>\n");
        xml.append("  <params>\n");
        for (Object param : params) {
            xml.append("    <param>\n");
            xml.append("      ").append(serializeValue(param)).append("\n");
            xml.append("    </param>\n");
        }
        xml.append("  </params>\n");
        xml.append("</methodCall>");
        return xml.toString();
    }

    private String serializeValue(Object value) {
        if (value == null) {
            return "<value><nil/></value>";
        } else if (value instanceof String s) {
            return "<value><string>" + escapeXml(s) + "</string></value>";
        } else if (value instanceof Integer i) {
            return "<value><int>" + i + "</int></value>";
        } else if (value instanceof Long l) {
            return "<value><long>" + l + "</long></value>";
        } else if (value instanceof Double d) {
            return "<value><double>" + d + "</double></value>";
        } else if (value instanceof Boolean b) {
            return "<value><boolean>" + (b ? "1" : "0") + "</boolean></value>";
        } else if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("<value><array><data>\n");
            for (Object item : list) {
                sb.append("        ").append(serializeValue(item)).append("\n");
            }
            sb.append("      </data></array></value>");
            return sb.toString();
        } else if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("<value><struct>\n");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sb.append("        <member>\n");
                sb.append("          <name>").append(entry.getKey()).append("</name>\n");
                sb.append("          ").append(serializeValue(entry.getValue())).append("\n");
                sb.append("        </member>\n");
            }
            sb.append("      </struct></value>");
            return sb.toString();
        }
        return "<value><string>" + escapeXml(value.toString()) + "</string></value>";
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private ResponseEntity<String> postXmlRpc(String url, String xml) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.setAccept(List.of(MediaType.TEXT_XML, MediaType.APPLICATION_XML));
        HttpEntity<String> entity = new HttpEntity<>(xml, headers);
        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    private int parseXmlRpcInt(String xml) {
        int idx = xml.indexOf("<int>");
        if (idx == -1) idx = xml.indexOf("<i4>");
        if (idx == -1) return -1;
        int start = xml.indexOf('>', idx) + 1;
        int end = xml.indexOf('<', start);
        return Integer.parseInt(xml.substring(start, end).trim());
    }

    private List<Map<String, Object>> parseXmlRpcArray(String xml) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (xml == null) return result;
        int structStart = 0;
        while (true) {
            structStart = xml.indexOf("<struct>", structStart);
            if (structStart == -1) break;
            int structEnd = xml.indexOf("</struct>", structStart);
            if (structEnd == -1) break;
            String structXml = xml.substring(structStart, structEnd + 9);
            Map<String, Object> record = parseXmlRpcStruct(structXml);
            if (!record.isEmpty()) {
                result.add(record);
            }
            structStart = structEnd + 9;
        }
        return result;
    }

    private Map<String, Object> parseXmlRpcStruct(String xml) {
        Map<String, Object> map = new LinkedHashMap<>();
        int memberStart = 0;
        while (true) {
            memberStart = xml.indexOf("<member>", memberStart);
            if (memberStart == -1) break;
            int memberEnd = xml.indexOf("</member>", memberStart);
            if (memberEnd == -1) break;
            String memberXml = xml.substring(memberStart, memberEnd + 9);

            int nameStart = memberXml.indexOf("<name>") + 6;
            int nameEnd = memberXml.indexOf("</name>");
            if (nameStart < 6 || nameEnd == -1) { memberStart = memberEnd + 9; continue; }
            String name = memberXml.substring(nameStart, nameEnd);

            int valueStart = memberXml.indexOf("<value>", nameEnd);
            if (valueStart == -1) { memberStart = memberEnd + 9; continue; }
            String valuePart = memberXml.substring(valueStart);
            Object val = parseXmlRpcValue(valuePart);
            map.put(name, val);
            memberStart = memberEnd + 9;
        }
        return map;
    }

    private Object parseXmlRpcValue(String xml) {
        if (xml.contains("<int>") || xml.contains("<i4>")) {
            int idx = xml.indexOf("<int>") != -1 ? xml.indexOf("<int>") : xml.indexOf("<i4>");
            int start = xml.indexOf('>', idx) + 1;
            int end = xml.indexOf('<', start);
            return Integer.parseInt(xml.substring(start, end).trim());
        } else if (xml.contains("<string>")) {
            int start = xml.indexOf("<string>") + 8;
            int end = xml.indexOf("</string>");
            return xml.substring(start, end);
        } else if (xml.contains("<double>")) {
            int start = xml.indexOf("<double>") + 8;
            int end = xml.indexOf("</double>");
            return Double.parseDouble(xml.substring(start, end).trim());
        } else if (xml.contains("<boolean>")) {
            int start = xml.indexOf("<boolean>") + 9;
            int end = xml.indexOf("</boolean>");
            return "1".equals(xml.substring(start, end).trim());
        } else if (xml.contains("<nil")) {
            return null;
        }
        return xml;
    }
}
