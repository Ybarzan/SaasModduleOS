package com.incokalk.repository;

import com.incokalk.model.TrainingModule;
import com.incokalk.model.TrainingModule.Category;
import com.incokalk.model.TrainingModule.Difficulty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrainingModuleRepository extends JpaRepository<TrainingModule, UUID> {

    List<TrainingModule> findByIsPublishedTrueOrderByCreatedAtDesc();

    List<TrainingModule> findByIsPublishedTrueAndCategoryOrderByCreatedAtDesc(Category category);

    List<TrainingModule> findByIsPublishedTrueAndDifficultyOrderByCreatedAtDesc(Difficulty difficulty);

    List<TrainingModule> findByCategory(Category category);
}
