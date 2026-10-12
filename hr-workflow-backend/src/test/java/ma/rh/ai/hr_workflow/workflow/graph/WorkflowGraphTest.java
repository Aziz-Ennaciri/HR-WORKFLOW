package ma.rh.ai.hr_workflow.workflow.graph;

import ma.rh.ai.hr_workflow.workflow.graph.WorkflowGraph.Edge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WorkflowGraph")
class WorkflowGraphTest {

    @Test
    @DisplayName("a chain is ordered from its start node, whatever the input order")
    void chain_isOrdered() {
        List<String> order = WorkflowGraph.executionOrder(List.of("c", "a", "b"),
                List.of(new Edge("b", "c"), new Edge("a", "b")));

        assertThat(order).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("a single node needs no connections; an empty workflow has an empty order")
    void trivialGraphs() {
        assertThat(WorkflowGraph.executionOrder(List.of("a"), List.of())).containsExactly("a");
        assertThat(WorkflowGraph.executionOrder(List.of(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("rejects graphs the engine cannot run, with an actionable message")
    void invalidGraphs_rejected() {
        assertInvalid(List.of("a", "b"), List.of(), "single chain");
        assertInvalid(List.of("a", "b", "c"), List.of(new Edge("a", "b"), new Edge("a", "c")), "branching");
        assertInvalid(List.of("a", "b", "c"), List.of(new Edge("a", "c"), new Edge("b", "c")), "one incoming");
        assertInvalid(List.of("a", "b"), List.of(new Edge("a", "b"), new Edge("b", "a")), "loop");
        assertInvalid(List.of("a", "b", "c"), List.of(new Edge("a", "b"), new Edge("c", "c")), "itself");
        assertInvalid(List.of("a", "b"), List.of(new Edge("a", "x")), "does not exist");
        assertInvalid(List.of("a", "b", "c", "d"), List.of(new Edge("a", "b"), new Edge("c", "d"), new Edge("d", "c")), "loop");
    }

    @Test
    @DisplayName("a chain plus a detached loop is reported as a loop")
    void detachedLoop_rejected() {
        assertInvalid(List.of("a", "b", "c"), List.of(new Edge("b", "c"), new Edge("c", "b")), "loop");
        assertInvalid(List.of("s", "a", "b", "c"),
                List.of(new Edge("s", "a"), new Edge("b", "c"), new Edge("c", "b")), "loop");
    }

    @Test
    @DisplayName("parseEdges reads React Flow edges and rejects malformed input")
    void parseEdges() {
        assertThat(WorkflowGraph.parseEdges("[{\"id\":\"e1\",\"source\":\"1\",\"target\":\"2\",\"type\":\"deletable\"}]"))
                .containsExactly(new Edge("1", "2"));
        assertThat(WorkflowGraph.parseEdges((String) null)).isEmpty();
        assertThat(WorkflowGraph.parseEdges("  ")).isEmpty();

        assertThatThrownBy(() -> WorkflowGraph.parseEdges("{\"edges\":[]}")).hasMessageContaining("must be a list");
        assertThatThrownBy(() -> WorkflowGraph.parseEdges("[{\"source\":\"1\"}]")).hasMessageContaining("source and a target");
        assertThatThrownBy(() -> WorkflowGraph.parseEdges("[oops")).hasMessageContaining("not valid JSON");
    }

    @Test
    @DisplayName("remapIds rewrites edge endpoints/ids and layout keys, keeping other fields")
    void remapIds() {
        Map<String, String> ids = Map.of("tmp-1", "10", "tmp-2", "11");

        assertThat(WorkflowGraph.remapIds(
                "[{\"id\":\"x\",\"source\":\"tmp-1\",\"target\":\"tmp-2\",\"animated\":false}]", ids))
                .isEqualTo("[{\"id\":\"e10-11\",\"source\":\"10\",\"target\":\"11\",\"animated\":false}]");
        assertThat(WorkflowGraph.remapIds("{\"tmp-1\":{\"x\":1,\"y\":2}}", ids))
                .isEqualTo("{\"10\":{\"x\":1,\"y\":2}}");
        assertThat(WorkflowGraph.remapIds(null, ids)).isNull();
    }

    private static void assertInvalid(List<String> nodes, List<Edge> edges, String messagePart) {
        assertThatThrownBy(() -> WorkflowGraph.executionOrder(nodes, edges))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(messagePart);
    }
}
