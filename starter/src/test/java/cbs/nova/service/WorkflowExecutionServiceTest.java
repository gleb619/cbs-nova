package cbs.nova.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.entity.WorkflowExecutionEntity;
import cbs.nova.entity.WorkflowStatus;
import cbs.nova.mapper.WorkflowExecutionMapper;
import cbs.nova.model.WorkflowExecutionDto;
import cbs.nova.model.exception.EntityNotFoundException;
import cbs.nova.repository.WorkflowExecutionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class WorkflowExecutionServiceTest {

  @Mock
  private WorkflowExecutionRepository repository;

  @Mock
  private WorkflowExecutionMapper mapper;

  @InjectMocks
  private WorkflowExecutionService service;

  @Test
  @DisplayName("shouldReturnMappedDtosWhenFindAll")
  void shouldReturnMappedDtosWhenFindAll() {
    // Arrange
    WorkflowExecutionEntity entity = WorkflowExecutionEntity.builder()
        .id(1L)
        .workflowCode("loan-approval")
        .dslVersion("1.0.0-abc123")
        .currentState("approved")
        .status(WorkflowStatus.ACTIVE)
        .context("{}")
        .displayData("{}")
        .performedBy("admin1")
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();

    WorkflowExecutionDto dto = new WorkflowExecutionDto(
        1L,
        "loan-approval",
        "1.0.0-abc123",
        "approved",
        "ACTIVE",
        "{}",
        "{}",
        "admin1",
        entity.getCreatedAt(),
        entity.getUpdatedAt());

    Page<WorkflowExecutionEntity> entityPage = new PageImpl<>(List.of(entity));
    Page<WorkflowExecutionDto> dtoPage = new PageImpl<>(List.of(dto));

    when(repository.findAll(PageRequest.of(0, 20))).thenReturn(entityPage);
    when(mapper.toDto(entity)).thenReturn(dto);

    // Act
    Page<WorkflowExecutionDto> result = service.findAll(PageRequest.of(0, 20));

    // Assert
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).id()).isEqualTo(1L);
    assertThat(result.getContent().get(0).workflowCode()).isEqualTo("loan-approval");
    verify(repository).findAll(PageRequest.of(0, 20));
  }

  @Test
  @DisplayName("shouldReturnDtoWhenFindByIdFound")
  void shouldReturnDtoWhenFindByIdFound() {
    // Arrange
    WorkflowExecutionEntity entity = WorkflowExecutionEntity.builder()
        .id(1L)
        .workflowCode("loan-approval")
        .dslVersion("1.0.0-abc123")
        .currentState("approved")
        .status(WorkflowStatus.ACTIVE)
        .context("{}")
        .displayData("{}")
        .performedBy("admin1")
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();

    WorkflowExecutionDto dto = new WorkflowExecutionDto(
        1L,
        "loan-approval",
        "1.0.0-abc123",
        "approved",
        "ACTIVE",
        "{}",
        "{}",
        "admin1",
        entity.getCreatedAt(),
        entity.getUpdatedAt());

    when(repository.findById(1L)).thenReturn(Optional.of(entity));
    when(mapper.toDto(entity)).thenReturn(dto);

    // Act
    WorkflowExecutionDto result = service.findById(1L);

    // Assert
    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.workflowCode()).isEqualTo("loan-approval");
    assertThat(result.status()).isEqualTo("ACTIVE");
  }

  @Test
  @DisplayName("shouldThrowEntityNotFoundExceptionWhenFindByIdNotFound")
  void shouldThrowEntityNotFoundExceptionWhenFindByIdNotFound() {
    // Arrange
    when(repository.findById(999L)).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> service.findById(999L))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("WorkflowExecution not found: id=999");
  }
}
