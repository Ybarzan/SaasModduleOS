package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Groupage;
import com.incokalk.model.GroupageMember;
import com.incokalk.repository.GroupageMemberRepository;
import com.incokalk.repository.GroupageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupageService — Consolidation multi-exportateurs")
class GroupageServiceTest {

    @Mock
    private GroupageRepository groupageRepository;
    @Mock
    private GroupageMemberRepository memberRepository;

    private GroupageService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        service = new GroupageService(groupageRepository, memberRepository);
        companyId = UUID.randomUUID();
    }

    private Groupage groupage(Groupage.Status status) {
        return Groupage.builder()
            .id(UUID.randomUUID())
            .companyId(companyId)
            .reference("GRP-2026-0001")
            .name("Conteneur 40' vers Le Havre")
            .status(status)
            .capacityWeightKg(new BigDecimal("20000"))
            .capacityVolumeM3(new BigDecimal("60"))
            .bookedWeightKg(BigDecimal.ZERO)
            .bookedVolumeM3(BigDecimal.ZERO)
            .build();
    }

    @Test
    @DisplayName("Créer un groupage génère une référence séquentielle")
    void create_generatesSequentialReference() {
        when(groupageRepository.countByCompanyId(companyId)).thenReturn(7L);
        when(groupageRepository.save(any(Groupage.class))).thenAnswer(inv -> inv.getArgument(0));

        Groupage created = service.create(companyId, "Conteneur", "SEA", "CMA CGM",
            "Lyon", "Shanghai", new BigDecimal("20000"), new BigDecimal("60"), null, null);

        assertThat(created.getReference()).isEqualTo("GRP-2026-0008");
        assertThat(created.getStatus()).isEqualTo(Groupage.Status.PLANNED);
        assertThat(created.getBookedWeightKg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Ajouter un membre recalcule le tonnage réservé")
    void addMember_recomputesUsage() {
        Groupage g = groupage(Groupage.Status.FORMING);
        when(groupageRepository.findByCompanyIdAndId(eq(companyId), any(UUID.class))).thenReturn(Optional.of(g));
        when(memberRepository.save(any(GroupageMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.sumWeightKg(g.getId())).thenReturn(new BigDecimal("5000"));
        when(memberRepository.sumVolumeM3(g.getId())).thenReturn(new BigDecimal("20"));
        when(groupageRepository.save(g)).thenReturn(g);

        GroupageMember member = service.addMember(g.getId(), companyId, null, "Exporteur B", "REF-102",
            new BigDecimal("5000"), new BigDecimal("20"));

        assertThat(member.getWeightKg()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(g.getBookedWeightKg()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(g.getBookedVolumeM3()).isEqualByComparingTo(new BigDecimal("20"));
        verify(memberRepository).save(any(GroupageMember.class));
        verify(groupageRepository).save(g);
    }

    @Test
    @DisplayName("Refuser un membre qui dépasse la capacité")
    void addMember_exceedsCapacityThrows() {
        Groupage g = groupage(Groupage.Status.PLANNED);
        g.setBookedWeightKg(new BigDecimal("19000"));
        when(groupageRepository.findByCompanyIdAndId(eq(companyId), any(UUID.class))).thenReturn(Optional.of(g));

        assertThatThrownBy(() -> service.addMember(g.getId(), companyId, null, "Exporteur C", "REF-103",
            new BigDecimal("2000"), new BigDecimal("5")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Capacité poids");
    }

    @Test
    @DisplayName("Transition de statut valide")
    void updateStatus_validTransition() {
        Groupage g = groupage(Groupage.Status.PLANNED);
        when(groupageRepository.findByCompanyIdAndId(eq(companyId), any(UUID.class))).thenReturn(Optional.of(g));
        when(groupageRepository.save(g)).thenReturn(g);

        Groupage updated = service.updateStatus(g.getId(), companyId, Groupage.Status.FORMING);

        assertThat(updated.getStatus()).isEqualTo(Groupage.Status.FORMING);
    }

    @Test
    @DisplayName("Transition de statut invalide")
    void updateStatus_invalidTransitionThrows() {
        Groupage g = groupage(Groupage.Status.DELIVERED);
        when(groupageRepository.findByCompanyIdAndId(eq(companyId), any(UUID.class))).thenReturn(Optional.of(g));

        assertThatThrownBy(() -> service.updateStatus(g.getId(), companyId, Groupage.Status.PLANNED))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Supprimer un groupage engagé est refusé")
    void delete_bookedStatusThrows() {
        Groupage g = groupage(Groupage.Status.DEPARTED);
        when(groupageRepository.findByCompanyIdAndId(eq(companyId), any(UUID.class))).thenReturn(Optional.of(g));

        assertThatThrownBy(() -> service.delete(g.getId(), companyId))
            .isInstanceOf(IllegalStateException.class);
        verify(groupageRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Détail expose l'utilisation poids/volume")
    void getDetail_exposesUtilization() {
        Groupage g = groupage(Groupage.Status.BOOKED);
        g.setBookedWeightKg(new BigDecimal("10000"));
        g.setBookedVolumeM3(new BigDecimal("30"));
        when(groupageRepository.findByCompanyIdAndId(eq(companyId), any(UUID.class))).thenReturn(Optional.of(g));
        when(memberRepository.findByGroupageIdOrderByCreatedAtAsc(g.getId())).thenReturn(java.util.List.of());

        Map<String, Object> detail = service.getDetail(g.getId(), companyId);

        assertThat(detail.get("reference")).isEqualTo("GRP-2026-0001");
        assertThat(detail.get("memberCount")).isEqualTo(0);
        assertThat((double) detail.get("weightUtilizationPct")).isEqualTo(50.0);
        assertThat((double) detail.get("volumeUtilizationPct")).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Groupage introuvable pour la société")
    void getDetail_notFoundThrows() {
        when(groupageRepository.findByCompanyIdAndId(eq(companyId), any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(UUID.randomUUID(), companyId))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
