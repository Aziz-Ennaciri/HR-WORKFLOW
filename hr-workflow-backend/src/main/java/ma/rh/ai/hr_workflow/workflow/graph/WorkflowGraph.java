package ma.rh.ai.hr_workflow.workflow.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The editor's edges must describe a single chain: one start node, every node reachable from it,
 * at most one incoming and one outgoing edge per node. That is what the engine can execute
 * (each step feeds the next), so anything else is rejected with a message the user can act on.
 */
public final class WorkflowGraph {

    private static final ObjectMapper JSON = new ObjectMapper();

    private WorkflowGraph() {
    }

    /** A directed edge between two node ids, as stored in a workflow's {@code edgesJson}. */
    public record Edge(String source, String target) {
    }

    /** Reads the {@code source}/{@code target} pairs from a React Flow edges array (null/blank = no edges). */
    public static List<Edge> parseEdges(String edgesJson) {
        if (edgesJson == null || edgesJson.isBlank()) {
            return List.of();
        }
        try {
            return parseEdges(JSON.readTree(edgesJson));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Workflow connections are not valid JSON");
        }
    }

    public static List<Edge> parseEdges(JsonNode edges) {
        if (edges == null || edges.isNull()) {
            return List.of();
        }
        if (!edges.isArray()) {
            throw new IllegalArgumentException("Workflow connections must be a list");
        }
        List<Edge> result = new ArrayList<>();
        for (JsonNode edge : edges) {
            String source = edge.path("source").asText("");
            String target = edge.path("target").asText("");
            if (source.isBlank() || target.isBlank()) {
                throw new IllegalArgumentException("Every connection needs a source and a target");
            }
            result.add(new Edge(source, target));
        }
        return result;
    }

    /** Rejects edges that point at nodes not on the canvas (always an error, even in a draft). */
    public static void requireKnownEndpoints(Collection<String> nodeIds, List<Edge> edges) {
        Set<String> nodes = Set.copyOf(nodeIds);
        for (Edge edge : edges) {
            if (!nodes.contains(edge.source()) || !nodes.contains(edge.target())) {
                throw new IllegalArgumentException("A connection points to a node that does not exist");
            }
        }
    }

    /**
     * Returns the node ids in execution order.
     *
     * @throws IllegalArgumentException if the edges do not form a single chain over all nodes
     */
    public static List<String> executionOrder(Collection<String> nodeIds, List<Edge> edges) {
        Set<String> nodes = new LinkedHashSet<>(nodeIds);
        if (nodes.size() != nodeIds.size()) {
            throw new IllegalArgumentException("Node ids must be unique");
        }
        if (nodes.size() <= 1 && edges.isEmpty()) {
            return new ArrayList<>(nodes);
        }

        Map<String, String> next = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        for (Edge edge : edges) {
            if (!nodes.contains(edge.source()) || !nodes.contains(edge.target())) {
                throw new IllegalArgumentException("A connection points to a node that does not exist");
            }
            if (edge.source().equals(edge.target())) {
                throw new IllegalArgumentException("A node cannot be connected to itself");
            }
            if (next.putIfAbsent(edge.source(), edge.target()) != null) {
                throw new IllegalArgumentException("A node can only connect to one next node (branching is not supported)");
            }
            if (previous.putIfAbsent(edge.target(), edge.source()) != null) {
                throw new IllegalArgumentException("A node can only have one incoming connection");
            }
        }

        List<String> starts = nodes.stream().filter(id -> !previous.containsKey(id)).toList();
        if (starts.size() != 1) {
            throw new IllegalArgumentException(starts.isEmpty()
                    ? "The workflow has a loop: there is no starting node"
                    : "Connect all nodes into a single chain (found " + starts.size() + " separate starting nodes)");
        }

        List<String> order = new ArrayList<>();
        for (String current = starts.get(0); current != null; current = next.get(current)) {
            order.add(current);
        }
        if (order.size() != nodes.size()) {
            // Every node off the chain has an incoming edge (else it would be a second start), so they form a loop.
            throw new IllegalArgumentException("The workflow has a loop: some nodes connect back to each other");
        }
        return order;
    }

    /**
     * Rewrites node ids inside a stored edges array ({@code source}/{@code target}/{@code id})
     * or layout object (its keys). Returns the input unchanged when it is blank or unparseable.
     */
    public static String remapIds(String json, Map<String, String> idMap) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = JSON.readTree(json);
            if (root.isArray()) {
                for (JsonNode edge : root) {
                    if (edge instanceof ObjectNode object) {
                        String source = idMap.getOrDefault(edge.path("source").asText(), edge.path("source").asText());
                        String target = idMap.getOrDefault(edge.path("target").asText(), edge.path("target").asText());
                        object.put("source", source);
                        object.put("target", target);
                        object.put("id", "e" + source + "-" + target);
                    }
                }
                return root.toString();
            }
            if (root.isObject()) {
                var remapped = JSON.createObjectNode();
                root.fields().forEachRemaining(entry ->
                        remapped.set(idMap.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue()));
                return remapped.toString();
            }
            return json;
        } catch (Exception e) {
            return json;
        }
    }
}
