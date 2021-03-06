package compression.services.resolving;

import com.graphhopper.jsprit.core.problem.Location;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a VRP solution route node.
 */
@AllArgsConstructor
public class VrpSolutionRouteNode {
    @Getter
    private Long nodeId;
    @Getter
    private Location nodeLocation;
}
