package com.mike.workstation.assembler;

import static org.assertj.core.api.Assertions.assertThat;

import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.ProjectStatus;
import com.mike.workstation.domain.Tag;
import com.mike.workstation.dto.NoteDetailResponse;
import com.mike.workstation.dto.NoteSummaryResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResponseAssemblerTests {
    @Test
    void noteSummaryExcludesContentWhileDetailKeepsIt() {
        Note note = new Note();
        note.setId(1L);
        note.setTitle("Model boundaries");
        note.setContent("# persisted markdown");
        note.setVersion(2L);
        Tag tag = new Tag();
        tag.setId(3L);
        tag.setName("architecture");
        note.setTags(List.of(tag));

        NoteSummaryResponse summary = NoteResponseAssembler.toSummary(note);
        NoteDetailResponse detail = NoteResponseAssembler.toDetail(note);

        assertThat(NoteSummaryResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("content");
        assertThat(detail.content()).isEqualTo("# persisted markdown");
        assertThat(summary.tags()).extracting(tagResponse -> tagResponse.name()).containsExactly("architecture");
    }

    @Test
    void projectSummaryExcludesRunGuideWhileFullResponseKeepsIt() {
        Project project = new Project();
        project.setId(1L);
        project.setName("Workstation");
        project.setStatus(ProjectStatus.ACTIVE);
        project.setRunGuide("mvn spring-boot:run");

        assertThat(ProjectResponseAssembler.toSummary(project).getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("runGuide");
        assertThat(ProjectResponseAssembler.toResponse(project).runGuide()).isEqualTo("mvn spring-boot:run");
    }

    @Test
    void responseTimeIncludesSystemOffset() {
        Project project = new Project();
        project.setCreatedAt(LocalDateTime.of(2026, 9, 15, 10, 30));
        project.setUpdatedAt(LocalDateTime.of(2026, 9, 15, 11, 30));

        var response = ProjectResponseAssembler.toSummary(project);

        assertThat(response.createdAt()).isNotNull();
        assertThat(response.createdAt().getOffset()).isNotNull();
        assertThat(response.updatedAt().getOffset()).isNotNull();
    }
}
