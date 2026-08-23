package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Groupage;
import com.incokalk.model.GroupageMember;
import com.incokalk.repository.GroupageMemberRepository;
import com.incokalk.repository.GroupageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupageService {

    private final GroupageRepository groupageRepository;
    private final GroupageMemberRepository memberRepository;

    @Transactional
    public Groupage create(UUID companyId, String name, String transportMode, String carrierName,
                           String origin, String destination, BigDecimal capacityWeightKg,
                           BigDecimal capacityVolumeM3, LocalDate plannedDeparture, LocalDate plannedArrival) {
        String reference = "GRP-" + LocalDate.now().getYear() + "-" + String.format("%04d", groupageRepository.countByCompanyId(companyId) + 1);

        Groupage groupage = Groupage.builder()
            .companyId(companyId)
            .reference(reference)
            .name(name)
            .status(Groupage.Status.PLANNED)
            .transportMode(transportMode)
            .carrierName(carrierName)
            .origin(origin)
            .destination(destination)
            .capacityWeightKg(capacityWeightKg)
            .capacityVolumeM3(capacityVolumeM3)
            .bookedWeightKg(BigDecimal.ZERO)
            .bookedVolumeM3(BigDecimal.ZERO)
            .plannedDeparture(plannedDeparture)
            .plannedArrival(plannedArrival)
            .build();
        return groupageRepository.save(groupage);
    }

    public List<Groupage> list(UUID companyId) {
        return groupageRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public Map<String, Object> getDetail(UUID id, UUID companyId) {
        Groupage groupage = get(id, companyId);
        List<GroupageMember> members = memberRepository.findByGroupageIdOrderByCreatedAtAsc(id);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", groupage.getId());
        detail.put("companyId", groupage.getCompanyId());
        detail.put("reference", groupage.getReference());
        detail.put("name", groupage.getName());
        detail.put("status", groupage.getStatus());
        detail.put("transportMode", groupage.getTransportMode());
        detail.put("carrierName", groupage.getCarrierName());
        detail.put("origin", groupage.getOrigin());
        detail.put("destination", groupage.getDestination());
        detail.put("capacityWeightKg", groupage.getCapacityWeightKg());
        detail.put("capacityVolumeM3", groupage.getCapacityVolumeM3());
        detail.put("bookedWeightKg", groupage.getBookedWeightKg());
        detail.put("bookedVolumeM3", groupage.getBookedVolumeM3());
        detail.put("plannedDeparture", groupage.getPlannedDeparture());
        detail.put("plannedArrival", groupage.getPlannedArrival());
        detail.put("createdAt", groupage.getCreatedAt());
        detail.put("updatedAt", groupage.getUpdatedAt());
        detail.put("memberCount", members.size());
        detail.put("weightUtilizationPct", utilizationPercent(groupage.getCapacityWeightKg(), groupage.getBookedWeightKg()));
        detail.put("volumeUtilizationPct", utilizationPercent(groupage.getCapacityVolumeM3(), groupage.getBookedVolumeM3()));
        detail.put("members", members);
        return detail;
    }

    @Transactional
    public Groupage update(UUID id, UUID companyId, String name, String transportMode, String carrierName,
                           String origin, String destination, BigDecimal capacityWeightKg,
                           BigDecimal capacityVolumeM3, LocalDate plannedDeparture, LocalDate plannedArrival) {
        Groupage groupage = get(id, companyId);
        if (name != null && !name.isBlank()) groupage.setName(name);
        if (transportMode != null) groupage.setTransportMode(transportMode);
        if (carrierName != null) groupage.setCarrierName(carrierName);
        if (origin != null) groupage.setOrigin(origin);
        if (destination != null) groupage.setDestination(destination);
        if (capacityWeightKg != null) groupage.setCapacityWeightKg(capacityWeightKg);
        if (capacityVolumeM3 != null) groupage.setCapacityVolumeM3(capacityVolumeM3);
        if (plannedDeparture != null) groupage.setPlannedDeparture(plannedDeparture);
        if (plannedArrival != null) groupage.setPlannedArrival(plannedArrival);
        return groupageRepository.save(groupage);
    }

    @Transactional
    public void delete(UUID id, UUID companyId) {
        Groupage groupage = get(id, companyId);
        if (groupage.getStatus() != Groupage.Status.PLANNED && groupage.getStatus() != Groupage.Status.FORMING
                && groupage.getStatus() != Groupage.Status.CANCELLED) {
            throw new IllegalStateException("Impossible de supprimer un groupage " + groupage.getStatus());
        }
        groupageRepository.delete(groupage);
    }

    @Transactional
    public GroupageMember addMember(UUID id, UUID companyId, UUID shipmentOrderId, String externalCompany,
                                    String reference, BigDecimal weightKg, BigDecimal volumeM3) {
        Groupage groupage = get(id, companyId);
        if (groupage.getStatus() != Groupage.Status.PLANNED && groupage.getStatus() != Groupage.Status.FORMING
                && groupage.getStatus() != Groupage.Status.BOOKED) {
            throw new IllegalStateException("Impossible d'ajouter une expédition à un groupage " + groupage.getStatus());
        }
        BigDecimal w = weightKg != null ? weightKg : BigDecimal.ZERO;
        BigDecimal v = volumeM3 != null ? volumeM3 : BigDecimal.ZERO;
        ensureCapacity(groupage, w, v);

        GroupageMember member = GroupageMember.builder()
            .groupageId(id)
            .shipmentOrderId(shipmentOrderId)
            .companyId(companyId)
            .externalCompany(externalCompany)
            .reference(reference)
            .weightKg(w)
            .volumeM3(v)
            .build();
        GroupageMember saved = memberRepository.save(member);
        recomputeUsage(groupage);
        return saved;
    }

    @Transactional
    public void removeMember(UUID id, UUID companyId, UUID memberId) {
        Groupage groupage = get(id, companyId);
        GroupageMember member = memberRepository.findByGroupageIdAndId(id, memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Expédition introuvable dans ce groupage"));
        memberRepository.delete(member);
        recomputeUsage(groupage);
    }

    @Transactional
    public Groupage updateStatus(UUID id, UUID companyId, Groupage.Status target) {
        Groupage groupage = get(id, companyId);
        validateTransition(groupage.getStatus(), target);
        groupage.setStatus(target);
        return groupageRepository.save(groupage);
    }

    public Map<String, Object> stats(UUID companyId) {
        List<Groupage> all = list(companyId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        for (Groupage.Status status : Groupage.Status.values()) {
            stats.put(status.name(), all.stream().filter(g -> g.getStatus() == status).count());
        }
        double totalWeight = all.stream().mapToDouble(g -> g.getBookedWeightKg() != null ? g.getBookedWeightKg().doubleValue() : 0).sum();
        stats.put("totalBookedWeightKg", totalWeight);
        return stats;
    }

    private void ensureCapacity(Groupage groupage, BigDecimal w, BigDecimal v) {
        if (groupage.getCapacityWeightKg() != null
                && groupage.getBookedWeightKg().add(w).compareTo(groupage.getCapacityWeightKg()) > 0) {
            throw new IllegalArgumentException("Capacité poids dépassée (" + groupage.getBookedWeightKg().add(w)
                + " > " + groupage.getCapacityWeightKg() + " kg)");
        }
        if (groupage.getCapacityVolumeM3() != null
                && groupage.getBookedVolumeM3().add(v).compareTo(groupage.getCapacityVolumeM3()) > 0) {
            throw new IllegalArgumentException("Capacité volume dépassée (" + groupage.getBookedVolumeM3().add(v)
                + " > " + groupage.getCapacityVolumeM3() + " m³)");
        }
    }

    private void recomputeUsage(Groupage groupage) {
        groupage.setBookedWeightKg(memberRepository.sumWeightKg(groupage.getId()));
        groupage.setBookedVolumeM3(memberRepository.sumVolumeM3(groupage.getId()));
        groupageRepository.save(groupage);
    }

    private void validateTransition(Groupage.Status from, Groupage.Status to) {
        if (from == to) return;
        if (from == Groupage.Status.CANCELLED || from == Groupage.Status.DELIVERED) {
            throw new IllegalStateException("Impossible de changer un groupage " + from);
        }
        if (to == Groupage.Status.CANCELLED) return;
        int fromOrder = from.ordinal();
        int toOrder = to.ordinal();
        if (toOrder < fromOrder) {
            throw new IllegalStateException("Transition invalide: " + from + " → " + to);
        }
    }

    private Groupage get(UUID id, UUID companyId) {
        return groupageRepository.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Groupage introuvable"));
    }

    private double utilizationPercent(BigDecimal capacity, BigDecimal booked) {
        if (capacity == null || capacity.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        return Math.min(100.0, booked.doubleValue() * 100.0 / capacity.doubleValue());
    }
}
