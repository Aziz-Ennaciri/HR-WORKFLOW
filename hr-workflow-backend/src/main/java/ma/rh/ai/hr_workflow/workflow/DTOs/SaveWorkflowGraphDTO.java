package ma.rh.ai.hr_workflow.workflow.DTOs;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The whole editor canvas, saved in one transaction. Node ids are the backend ids for existing
 * nodes and any client-generated id for new ones; edges and layout may reference either.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SaveWorkflowGraphDTO {

    @NotNull
    private List<@Valid GraphNode> nodes;

    /** React Flow edges ({@code source}/{@code target} plus any presentation fields). */
    private JsonNode edges;

    /** Canvas positions keyed by node id: {@code { "<id>": { "x": .., "y": .. } }}. */
    private JsonNode layout;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GraphNode {
        @NotBlank
        private String id;

        @NotBlank
        private String type;

        private String configJson;
    }
}
