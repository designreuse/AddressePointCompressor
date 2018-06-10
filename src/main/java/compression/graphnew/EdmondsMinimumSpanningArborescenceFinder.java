package compression.graphnew;

import com.sun.javafx.collections.MappingChange;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jgrapht.alg.cycle.TarjanSimpleCycles;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.util.*;

public class EdmondsMinimumSpanningArborescenceFinder<V,E>
    implements IMinimumSpanningArborescenceFinder<V, E>{

    private final IntegerEdmondsMinimumSpanningArborescenceFinder internalFinder;

    public EdmondsMinimumSpanningArborescenceFinder(){
        internalFinder = new IntegerEdmondsMinimumSpanningArborescenceFinder();
    }

    @Override
    public IMinimumSpanningArborescence<V, E> getSpanningArborescence(SimpleDirectedWeightedGraph<V, E> graph, V root) {
        SimpleDirectedWeightedGraph<Integer, Edge> intG = new SimpleDirectedWeightedGraph<>(Edge.class);
        Integer id = 1;
        Map<V, Integer> vertexMap = new HashMap<>();
        Map<Integer, V> reverseMap = new HashMap<>();
        for(V v : graph.vertexSet()){
            intG.addVertex(id);
            vertexMap.put(v, id);
            reverseMap.put(id, v);
            id = new Integer(id+1);
        }
        for(E e : graph.edgeSet()){
            V s = graph.getEdgeSource(e);
            V t = graph.getEdgeTarget(e);
            double w = graph.getEdgeWeight(e);
            Edge ee = new Edge(vertexMap.get(s), vertexMap.get(t), w);
            intG.addEdge(ee.getSource(), ee.getTarget(), ee);
        }
        IMinimumSpanningArborescence<Integer, Edge> intArb = internalFinder.getSpanningArborescence(intG, vertexMap.get(root));
        Set<E> edges = new HashSet<>();
        for(Edge e : intArb.getEdges()){
            edges.add(graph.getEdge(reverseMap.get(e.getSource()), reverseMap.get(e.getTarget())));
        }
        return new DefaultMinimumSpanningArborescence<>(root, edges);
    }
}

class IntegerEdmondsMinimumSpanningArborescenceFinder
    implements IMinimumSpanningArborescenceFinder<Integer, Edge>{

    @Override
    public IMinimumSpanningArborescence<Integer, Edge> getSpanningArborescence(SimpleDirectedWeightedGraph<Integer, Edge> graph, Integer root) {
        Integer maxVertex = graph.vertexSet().size();
        Set<Edge> arborescence = findMinimumSpanningArborescence(graph, root, maxVertex);
        return new DefaultMinimumSpanningArborescence<>(root, arborescence);
    }

    private Set<Edge> findMinimumSpanningArborescence(SimpleDirectedWeightedGraph<Integer, Edge> graph, Integer root, Integer maxVertex){
        System.out.println("Size: "+graph.vertexSet().size());
        Set<Edge> pEdges = findPEdges(graph, root);
        List<Integer> cycle = getArbitraryCycle(graph, pEdges);
        if(cycle == null){
            return pEdges;
        }
        Map<Integer, Edge> shortestIncomming = new HashMap<>();
        Map<Integer, Edge> shortestOutcomming = new HashMap<>();
        SimpleDirectedWeightedGraph<Integer, Edge> reduced = shrinkCycle(graph, cycle, pEdges, maxVertex, shortestIncomming, shortestOutcomming);
        Integer cycleVertex = maxVertex+1;
        Set<Edge> reducedArb = findMinimumSpanningArborescence(reduced, root, cycleVertex);
        return markEdges(graph, reducedArb, cycle, cycleVertex, shortestIncomming, shortestOutcomming);
    }

    private Set<Edge> markEdges(SimpleDirectedWeightedGraph<Integer, Edge> graph, Set<Edge> redArb,
                                         List<Integer> cycle, Integer cycleVertex,
                                         Map<Integer, Edge> shortestIncomming,
                                         Map<Integer, Edge> shortestOutcomming){
        Set<Edge> arb = new HashSet<>();
        Integer splittingV = null;
        for(Edge e : redArb){
            if(!e.getSource().equals(cycleVertex) && !e.getTarget().equals(cycleVertex)){
                arb.add(e);
            } else if (e.getSource().equals(cycleVertex)){
                arb.add(shortestOutcomming.get(e.getTarget()));
            } else if (e.getTarget().equals(cycleVertex)){
                arb.add(shortestIncomming.get(e.getSource()));
                if(splittingV != null)
                    throw new RuntimeException("Non unique edge incomming to cycle");
                splittingV = shortestIncomming.get(e.getSource()).getTarget();
            }
        }
        if(splittingV == null)
            throw new RuntimeException("No cycle splittingVertex");
        Integer prev = null;
        for(Integer cv : cycle){
            if(prev == null){
                prev = cv;
                continue;
            }
            if(!cv.equals(splittingV)){
                arb.add(graph.getEdge(prev, cv));
            }
            prev = cv;
        }
        Integer first = cycle.get(0);
        if(!first.equals(splittingV)){
            arb.add(graph.getEdge(prev, first));
        }
        return arb;
    }

    private SimpleDirectedWeightedGraph<Integer, Edge> shrinkCycle(SimpleDirectedWeightedGraph<Integer, Edge> graph,
                                                                            List<Integer> cycle, Set<Edge> pEdges, Integer maxVertex,
                                                                            Map<Integer, Edge> shortestIncomming,
                                                                            Map<Integer, Edge> shortestOutcomming){
        Integer cycleVertex = maxVertex +1;
        SimpleDirectedWeightedGraph<Integer, Edge> reducedGraph = new SimpleDirectedWeightedGraph<>(Edge.class);
        for(Integer v : graph.vertexSet()){
            if(!cycle.contains(v)){
                reducedGraph.addVertex(v);
            }
        }
        reducedGraph.addVertex(cycleVertex);
        for(Edge e : graph.edgeSet()){
            if(!cycle.contains(e.getSource()) && cycle.contains(e.getTarget())) { //edge to cycle
                Edge pE = findIncomming(pEdges, e.getTarget());
                Edge sh = shortestIncomming.getOrDefault(e.getSource(), null);
                if(sh == null){
                    shortestIncomming.put(e.getSource(), e);
                }else {
                    Edge psh = findIncomming(pEdges, sh.getTarget());
                    if(e.getWeight()-pE.getWeight() < sh.getWeight() - psh.getWeight()){
                        shortestIncomming.put(e.getSource(), e);
                    }
                }
            }
            else if(cycle.contains(e.getSource()) && !cycle.contains(e.getTarget())){ //edge from cycle
                Edge sh = shortestOutcomming.getOrDefault(e.getTarget(), null);
                if(sh == null || sh.getWeight() > e.getWeight()){
                    shortestOutcomming.put(e.getTarget(), e);
                }
            }
            else if(!cycle.contains(e.getSource()) && !cycle.contains(e.getTarget())){ //edge unrelated to the cycle
                //if(pEdges.contains(e))
                    reducedGraph.addEdge(e.getSource(), e.getTarget(),e);
            }
        }
        for(Map.Entry<Integer, Edge> en : shortestIncomming.entrySet()){
            Edge e = en.getValue();
            Edge pE = findIncomming(pEdges, e.getTarget());
            Edge newE = new Edge(e.getSource(), cycleVertex, e.getWeight() - pE.getWeight());
            reducedGraph.addEdge(newE.getSource(), newE.getTarget(), newE);
        }
        for(Map.Entry<Integer, Edge> en : shortestOutcomming.entrySet()){
            Edge e = en.getValue();
            Edge newE = new Edge(cycleVertex, e.getTarget(), e.getWeight());
            reducedGraph.addEdge(newE.getSource(), newE.getTarget(), newE);
        }
        return reducedGraph;
    }

    private Edge findIncomming(Set<Edge> pEdges, Integer v){
        Edge edge = null;
        for(Edge e : pEdges){
            if(e.getTarget().equals(v)){
                if(edge != null)
                    throw new RuntimeException("Non unique incomming egde to vertex");
                edge = e;
            }
        }
        return edge;
    }

    private <VV> Set<Edge> findPEdges(SimpleDirectedWeightedGraph<VV,Edge> graph, VV root){
        Set<Edge> p = new HashSet<>();
        for (VV v : graph.vertexSet()){
            if(v != root){
                Edge min = null;
                double minWeight = Double.MAX_VALUE;
                for(Edge e : graph.incomingEdgesOf(v)){
                    double eWeight = e.getWeight();
                    if(min == null || eWeight < minWeight){
                        min = e;
                        minWeight = eWeight;
                    }
                }
                if(min != null){
                    p.add(min);
                }
            }
        }
        return p;
    }

    private <VV> List<VV> getArbitraryCycle(SimpleDirectedWeightedGraph<VV,Edge> graph, Set<Edge> pEdges){
        SimpleDirectedWeightedGraph<VV, Edge> pGraph = new SimpleDirectedWeightedGraph<>(Edge.class);
        for(VV v : graph.vertexSet()){
            pGraph.addVertex(v);
        }
        for(Edge e : pEdges){
            pGraph.addEdge(e.getSource(), e.getTarget(), e);
        }
        TarjanSimpleCycles<VV,Edge> cycleFinder = new TarjanSimpleCycles<>(pGraph);
        List<List<VV>> cycles = cycleFinder.findSimpleCycles();
        if(cycles.isEmpty())
            return null;
        List<VV> maxCycle = null;
        int maxSize = 0;
        for(List<VV> c : cycles){
            if(c.size()>maxSize){
                maxCycle = c;
                maxSize = c.size();
            }
        }
        return maxCycle;
    }
}
