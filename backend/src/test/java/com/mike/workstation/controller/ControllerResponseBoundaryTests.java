package com.mike.workstation.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.mike.workstation.domain.Note;
import com.mike.workstation.domain.NoteFolder;
import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.Tag;
import com.mike.workstation.domain.Task;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

class ControllerResponseBoundaryTests {
    private static final Set<Class<?>> ENTITY_TYPES =
            Set.of(Note.class, NoteFolder.class, Project.class, Tag.class, Task.class);

    @Test
    void businessControllersDoNotExposePersistenceEntities() {
        Class<?>[] controllers = {
            DashboardController.class,
            NoteController.class,
            NoteFolderController.class,
            ProjectController.class,
            TagController.class,
            TaskController.class
        };

        for (Class<?> controller : controllers) {
            assertThat(controller.getDeclaredMethods())
                    .filteredOn(method -> !method.isSynthetic())
                    .allSatisfy(this::assertResponseDoesNotExposeEntity);
        }
    }

    private void assertResponseDoesNotExposeEntity(Method method) {
        assertThat(ENTITY_TYPES)
                .as(
                        "%s.%s must return a response DTO",
                        method.getDeclaringClass().getSimpleName(), method.getName())
                .doesNotContain(method.getReturnType());
        assertThat(method.getGenericReturnType().getTypeName())
                .doesNotContain("com.mike.workstation.domain");
    }
}
