package compression.services.jsprit.conversion;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import compression.graph.branching.TreeBranch;
import compression.model.disjointset.DisjointSet;
import compression.model.vrp.*;
import compression.services.compression.nonmap.NonMapCompressionService;
import compression.services.compression.nonmap.graph.LocationVertex;
import compression.services.jsprit.extensions.nonmap.AggregatedService;
import lombok.RequiredArgsConstructor;

import java.rmi.activation.ActivationGroup;
import java.util.LinkedList;
import java.util.List;

@RequiredArgsConstructor
public class ExplicitMetricCompressionVrpProblemToJSpritConverter
        extends BaseProblemToJSpritConverter
        implements IVrpProblemToJSpritConverter {

    private final NonMapCompressionService compressionService;

    @Override
    protected Location convertLocation(Client client) {
        return null;
    }

    @Override
    public VehicleRoutingProblem convertToJsprit(VrpProblem problem) {
        if(problem.getProblemMetric() != VrpProblemMetric.Explicit){
            throw new ProblemConversionException("Metric type must be explicit for this converter");
        }
        if(problem.getDistanceMatrix() == null){
            throw new ProblemConversionException("Distance matrix cannot be null");
        }
        List<TreeBranch<LocationVertex>> branches = compressionService.getAggregatedClients(problem);
        VehicleRoutingProblem.Builder problemBuilder = VehicleRoutingProblem.Builder.newInstance();
        Location depotLocation = Location.newInstance(problem.getDepot().getId().toString());
        addVehicles(problemBuilder, problem, depotLocation);
        List<AggregatedService> services = aggregateServices(branches);
        DistanceMatrix matrix = compressMatrix(services, problem.getDepot(), problem.getDistanceMatrix());
        VehicleRoutingTransportCostsMatrix.Builder matrixBuilder = VehicleRoutingTransportCostsMatrix.Builder.newInstance(false);
        copyDistanceMatrix(matrix, matrixBuilder);
        for(AggregatedService s : services){
            Service service = Service.Builder.newInstance(s.getId().toString())
                    .setLocation(Location.newInstance(s.getId().toString()))
                    .addSizeDimension(0, s.getInternalCost().intValue())
                    .build();
            problemBuilder.addJob(service);
        }
        problemBuilder.setRoutingCost(matrixBuilder.build());
        return problemBuilder.build();
    }

    private List<AggregatedService> aggregateServices(List<TreeBranch<LocationVertex>> branches){
        List<AggregatedService> services = new LinkedList<>();
        Long id = 2l;
        for(TreeBranch<LocationVertex> branch : branches){
            Double cost = 0.0;
            LocationVertex prev = null;
            for(LocationVertex v : branch.getVertices()){
                if(prev != null){
                    cost += v.getDemand();
                }
                prev = v;
            }
            branch.getVertices().remove(branch.getStartVertex());
            AggregatedService service = new AggregatedService(branch.getVertices(), branch.getVertices().get(0), branch.getEndVertex(), cost, id);
            id++;
            services.add(service);
        }
        return services;
    }

    private DistanceMatrix compressMatrix(List<AggregatedService> services, Depot depot, DistanceMatrix distances){
        DistanceMatrix matrix = new DistanceMatrix(services.size()+1);
        for(AggregatedService from : services){
            for(AggregatedService to : services){
                if(from != to){
                    Double fromTo = distances.getDistance(from.getOutputVertex().getId(), to.getInputVertex().getId()) + to.getInternalCost();
                    matrix.setDistance(from.getId(), to.getId(), fromTo);
                    Double toFrom = distances.getDistance(to.getOutputVertex().getId(), from.getInputVertex().getId()) + from.getInternalCost();
                    matrix.setDistance(to.getId(), from.getId(), toFrom);
                }
            }
            Double fromDepot = distances.getDistance(depot.getId(), from.getInputVertex().getId())+from.getInternalCost();
            matrix.setDistance(depot.getId(), from.getId(), fromDepot);
            Double toDepot = distances.getDistance(from.getOutputVertex().getId(), depot.getId());
            matrix.setDistance(from.getId(), depot.getId(), toDepot);
        }
        return matrix;
    }

    private void copyDistanceMatrix(DistanceMatrix matrix, VehicleRoutingTransportCostsMatrix.Builder matrixCostBuilder){
        for(Long from = 1l; from <= matrix.getDimensions(); from++){
            for(Long to = 1l; to <= matrix.getDimensions(); to++){
                if(from != to) {
                    matrixCostBuilder.addTransportDistance(from.toString(), to.toString(), matrix.getDistance(from, to));
                }
            }
        }
    }
}