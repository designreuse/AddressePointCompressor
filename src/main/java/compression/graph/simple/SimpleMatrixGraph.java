package compression.graph.simple;

import compression.graph.IEdge;
import compression.graph.IGraph;
import compression.graph.IVertex;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.util.*;

public class SimpleMatrixGraph<TVertex extends IVertex, TEdge extends IEdge<TVertex>>
        extends SimpleGraph<TVertex, TEdge> {

    private final Class<TEdge> edgeClass;
    private final TEdge[][] edges;

    public SimpleMatrixGraph(Collection<TVertex> items, Class<TEdge> cl){
        super(items);
        edges = (TEdge[][])Array.newInstance(cl, verticesCount, verticesCount);
        edgeClass = cl;
    }

    @Override
    public List<TEdge> getAllEdges() {
        LinkedList<TEdge> edgeList = new LinkedList<>();
        for(int from = 0; from<verticesCount; from++){
            for(int to = 0; to<verticesCount; to++){
                TEdge e = edges[from][to];
                if(e != null)
                    edgeList.add(e);
            }
        }
        return edgeList;
    }

    @Override
    public void addEdge(TEdge edge) {
        int from = verticesMap.get(edge.getFrom());
        int to = verticesMap.get(edge.getTo());
        edges[from][to] = edge;
    }

    @Override
    public IGraph<TVertex, TEdge> emptyGraph(){
        return new SimpleMatrixGraph<>(this.items, edgeClass);
    }

    @Override
    public Collection<TEdge> getEdgesFrom(IVertex vertex) {
        List<TEdge> edges = new LinkedList<>();
        int v = verticesMap.get(vertex);
        for(int i=0; i<verticesCount; i++){
            TEdge e = this.edges[v][i];
            if(e != null){
                edges.add(e);
            }
        }
        return edges;
    }
}
