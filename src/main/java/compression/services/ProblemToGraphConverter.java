package compression.services;

import compression.model.graph.Edge;
import compression.model.vrp.helpers.LocationVertex;
import compression.model.graph.RoutedGraph;
import compression.model.vrp.*;
import compression.services.distance.DistanceService;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

/**
 * Implementation of IProblemToGraphConverter interface for graphs with LocationVertex vertex type.
 */
public class ProblemToGraphConverter implements IProblemToGraphConverter<LocationVertex>{

    /**
     * Converts VRP problem to graph.
     * @param problem VRP problem.
     * @return Graph.
     */
    public RoutedGraph<LocationVertex, Edge> convert(VrpProblem problem){
        switch (problem.getProblemMetric()){
            case Euclidean:
                generateDistanceMatrix(problem);
                break;
            case Explicit:
                break;
            default:
                throw new IllegalArgumentException();
        }
        return convertExplicit(problem);
    }

    private void generateDistanceMatrix(VrpProblem problem){
        DistanceService s = new DistanceService();
        DistanceMatrix m = new SymmetricalDistanceMatrix(problem.getDimensions());
        for(Client c1 : problem.getClients()){
            for(Client c2 : problem.getClients()){
                if(c1.getId() != c2.getId()){
                    m.setDistance(c1.getId(), c2.getId(), s.getEuclideanDistance(c1.getLocation(), c2.getLocation()));
                }
                else
                    m.setDistance(c1.getId(), c2.getId(), 0.0);
            }
            m.setDistance(c1.getId(), problem.getDepot().getId(), s.getEuclideanDistance(c1.getLocation(), problem.getDepot().getLocation()));
        }
        problem.setDistanceMatrix(m);
    }

    private RoutedGraph<LocationVertex, Edge> convertExplicit(VrpProblem problem){
        SimpleDirectedWeightedGraph<LocationVertex, Edge> g = new SimpleDirectedWeightedGraph<>(Edge.class);
        LocationVertex[] vertices = new LocationVertex[problem.getDimensions()];
        vertices[0] = new LocationVertex(problem.getDepot().getId(), problem.getDepot().getLocation(), 0.0);
        g.addVertex(vertices[0]);
        for(Client cf : problem.getClients()) {
            LocationVertex v = new LocationVertex(cf.getId(), cf.getLocation(), cf.getAmount());
            vertices[v.getId().intValue()-1] = v;
            g.addVertex(v);
        }
        addEdges(g, problem.getDimensions(), vertices, problem.getDistanceMatrix());
        return new RoutedGraph<>(g, vertices[0]);
    }

    private void addEdges(SimpleDirectedWeightedGraph<LocationVertex, Edge> g, int dim, LocationVertex[] vertices, DistanceMatrix matrix){
        for(int i=0; i<dim; i++){
            for(int j=0; j<dim; j++){
                if(i!=j){
                    Edge e = new Edge(vertices[i], vertices[j], matrix.getDistance(vertices[i].getId(), vertices[j].getId()));
                    g.addEdge(e.getSource(), e.getTarget(), e);
                    g.setEdgeWeight(e, e.getWeight());
                }
            }
        }
    }
}
