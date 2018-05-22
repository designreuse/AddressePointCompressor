package compression.input.parsing.vrp;

import compression.input.parsing.ParsingException;
import compression.model.vrp.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

public class VrpProblemParser implements IVrpProblemParser{

    @Override
    public VrpProblem parse(InputStream stream) {
        try(InputStreamReader reader = new InputStreamReader(stream)) {
            try(BufferedReader bufferedReader = new BufferedReader(reader)){
                return parseReader(bufferedReader);
            }
        } catch (IOException e) {
            throw new ParsingException(e.getMessage(), e);
        }
    }

    private enum EdgeWeightFormat{
        UNKNOWN,
        LOWER_ROW,
        FULL_MATRIX
    }

    private VrpProblem parseReader(BufferedReader reader) throws IOException {
        String line;
        String name = null;
        Double bestSolution = 0.0;
        Integer capacity = 0;
        Integer dimensions = 0;
        DistanceMatrix distanceMatrix = null;
        VrpProblemMetric metric = VrpProblemMetric.Unknown;
        EdgeWeightFormat format = EdgeWeightFormat.UNKNOWN;
        Location[] locations = null;
        Double[] demands = null;
        List<Integer> depotIds = null;
        while((line = reader.readLine()) != null){
            if(line.startsWith("NAME")){
                name = parseName(line);
            }
            else if(line.startsWith("COMMENT")){
                bestSolution = parseBestKnownSolution(line);
            }
            else if(line.startsWith("DIMENSION")){
                dimensions = parseDimensions(line);
            }
            else if(line.startsWith("CAPACITY")){
                capacity = parseCapacity(line);
            }
            else if(line.startsWith("EDGE_WEIGHT_FORMAT")){
                format = parseEdgeWeightFormat(line);
            }
            else if(line.startsWith("EDGE_WEIGHT_TYPE")){
                metric = parseMetrics(line);
            }
            else if(line.startsWith("EDGE_WEIGHT_SECTION")){
                if(metric != VrpProblemMetric.Explicit)
                    throw new ParsingException("EDGE_WEIGHT_SECTION is supported only when EDGE_WEIGHT_TYPE is set to EXPLICIT");
                else{
                    if(format == EdgeWeightFormat.UNKNOWN) {
                        throw new ParsingException("Unknown edge weight format");
                    } else if(format == EdgeWeightFormat.FULL_MATRIX){
                        distanceMatrix = parseNonSymmetricalDistanceMatrix(reader, dimensions);
                    } else if(format == EdgeWeightFormat.LOWER_ROW){
                        distanceMatrix = parseSymmetricalDistanceMatrix(reader, dimensions);
                    }
                }
            }
            else if(line.startsWith("NODE_COORD_SECTION")){
                if(dimensions == 0)
                    throw new ParsingException("Invalid dimensions");
                locations = parseLocations(reader, dimensions);
            }
            else if(line.startsWith("DEMAND_SECTION")){
                if(dimensions == 0)
                    throw new ParsingException("Invalid dimensions");
                demands = parseDemands(reader, dimensions);
            }
            else if(line.startsWith("DEPOT_SECTION")){
                depotIds = parseDepots(reader);
            }
            else if(line.trim() == ""){
                continue;
            }
            else if(line == "EOF"){
                break;
            }
        }
        List<Vehicle> vehicles = convertVehicle(capacity);
        if(depotIds.size() != 1){
            throw new ParsingException("Only one depot is supported");
        }
        Depot depot = null;
        List<Client> clients = new LinkedList<>();
        if(metric == VrpProblemMetric.Explicit){
            depot = new Depot(1L, locations[0]);
            Long clId = 2L;
            for(int i = 1; i< dimensions; i++){
                clients.add(new Client(clId, demands[i], 0.0, locations[i]));
                clId++;
            }
        } else {
            depot = convertDepot(depotIds.get(0), locations);
            clients = convertClients(depotIds.get(0), demands, locations);
        }
        return new VrpProblem(name, bestSolution, dimensions, metric, clients, vehicles, depot, distanceMatrix);
    }

    private String parseName(String line){
        return line.split(" : ")[1];
    }

    private Double parseBestKnownSolution(String line){
        String[] split = line.split(":");
        String bestKnown = split[split.length-1].replace(")", "");
        try{
            return Double.parseDouble(bestKnown);
        } catch(Exception ex) {
            return 0.0;
        }
    }

    private Integer parseCapacity(String line){
        return Integer.parseInt(line.split(" : ")[1]);
    }

    private Integer parseDimensions(String line){
        return Integer.parseInt(line.split(" : ")[1]);
    }

    private VrpProblemMetric parseMetrics(String line){
        String metric = line.split(" : ")[1];
        if(metric.startsWith("EXPLICIT")){
            return VrpProblemMetric.Explicit;
        }
        if(metric.startsWith("EUC_2D")){
            return VrpProblemMetric.Euclidean;
        }
        throw new ParsingException("Not supported problem metrics");
    }

    private EdgeWeightFormat parseEdgeWeightFormat(String line){
        String format = line.split(": ")[1];
        if(format.compareTo(EdgeWeightFormat.FULL_MATRIX.toString())==0)
            return EdgeWeightFormat.FULL_MATRIX;
        else if(format.compareTo(EdgeWeightFormat.LOWER_ROW.toString())==0)
            return EdgeWeightFormat.LOWER_ROW;
        return EdgeWeightFormat.UNKNOWN;
    }

    private DistanceMatrix parseNonSymmetricalDistanceMatrix(BufferedReader reader, Integer dimensions) throws IOException{
        DistanceMatrix distanceMatrix = new DistanceMatrix(dimensions);
        for (int i=0; i<dimensions; i++){
            String line = reader.readLine();
            String[] items = line.split("\\s+");
            int j=0;
            for(String item : items){
                if(item.compareTo("")==0)
                    continue;
                Double dist = Double.parseDouble(item);
                distanceMatrix.setDistance(i,j,dist);
                j++;
            }
        }
        return distanceMatrix;
    }

    private DistanceMatrix parseSymmetricalDistanceMatrix(BufferedReader reader, Integer dimensions) throws IOException {
        Integer remainingLocations = dimensions*(dimensions-1)/2;
        SymmetricalDistanceMatrix distanceMatrix = new SymmetricalDistanceMatrix(dimensions);
        Integer num = 0;
        Integer from = 0;
        Integer to = 0;
        while (num < remainingLocations){
            String[] items = reader.readLine().split(" ");
            if(items.length == 0)
                throw new ParsingException("Invalid distance matrix line");
            for(String i : items){
                if(i.equals(""))
                    continue;
                Double dist = Double.parseDouble(i);
                if(from == to){
                    to=to+1;
                }
                if(to>=dimensions) {
                    from++;
                    to = from+1;
                    to %= dimensions;
                }
                if(to == 0)
                    throw new ParsingException("Invalid distance matrix");
                distanceMatrix.setDistance(from, to, dist);
                num++;
                to++;
            }
        }
        return distanceMatrix;
    }

    private Location[] parseLocations(BufferedReader reader, Integer dimensions) throws IOException {
        List<Location> locations = new LinkedList<>();
        for(int i=1; i<=dimensions; i++){
            String[] items = reader.readLine().split(" ");
            if(items.length != 3){
                throw new ParsingException("Invalid location");
            }
            Integer id = Integer.parseInt(items[0]);
            Double latitude = Double.parseDouble(items[1]);
            Double longitude = Double.parseDouble(items[2]);
            if(id != i)
                throw new ParsingException("Invalid location id");
            locations.add(new Location(latitude, longitude));
        }
        return locations.toArray(new Location[locations.size()]);
    }

    private Double[] parseDemands(BufferedReader reader, Integer dimensions) throws IOException {
        List<Double> demands = new LinkedList<>();
        for(int i=1; i<= dimensions; i++){
            String[] items = reader.readLine().split(" ");
            if(items.length != 2)
                throw new ParsingException("Invalid demand");
            Integer id = Integer.parseInt(items[0]);
            Double demand = Double.parseDouble(items[1]);
            if(id != i)
                throw new ParsingException("Invalid demand id");
            demands.add(demand);
        }
        return demands.toArray(new Double[demands.size()]);
    }

    private List<Integer> parseDepots(BufferedReader bufferedReader) throws IOException {
        List<Integer> depotIds = new LinkedList<>();
        String line;
        while((line = bufferedReader.readLine()) != null){
            Integer id = Integer.parseInt(line.replace(" ", ""));
            if(id < 0)
                break;
            depotIds.add(id);
        }
        return depotIds;
    }

    private List<Vehicle> convertVehicle(Integer capacity){
        Vehicle v = new Vehicle(1L, capacity);
        List<Vehicle> list = new LinkedList<>();
        list.add(v);
        return list;
    }

    private Depot convertDepot(Integer depotLocationId, Location[] locations){
        return new Depot(1L, locations[depotLocationId-1]);
    }

    private List<Client> convertClients(Integer depotLocationId, Double[] demands, Location[] locations){
        List<Client> clients = new LinkedList<>();
        Long id = 2L;
        for(int i=0; i<demands.length; i++){
            if(i == depotLocationId-1){
                if(demands[i]>0)
                    throw new ParsingException("Cannot set demand to depot");
                continue;
            }
            clients.add(new Client(id, demands[i], 0.0, locations[i]));
            id++;
        }
        return clients;
    }
}
