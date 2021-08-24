import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.MultiLineString
import org.locationtech.jts.geom.Point
import org.matsim.api.core.v01.Id
import org.matsim.api.core.v01.Scenario
import org.matsim.api.core.v01.network.Link
import org.matsim.api.core.v01.network.Network
import org.matsim.api.core.v01.network.Node
import org.matsim.core.config.ConfigUtils
import org.matsim.core.network.NetworkUtils
import org.matsim.core.population.routes.NetworkRoute
import org.matsim.core.population.routes.RouteUtils
import org.matsim.core.scenario.ScenarioUtils
import org.matsim.core.utils.geometry.CoordUtils
import org.matsim.core.utils.gis.ShapeFileReader
import org.matsim.pt.transitSchedule.api.Departure
import org.matsim.pt.transitSchedule.api.TransitLine
import org.matsim.pt.transitSchedule.api.TransitRoute
import org.matsim.pt.transitSchedule.api.TransitRouteStop
import org.matsim.pt.transitSchedule.api.TransitSchedule
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory
import org.matsim.pt.transitSchedule.api.TransitScheduleReader
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter
import org.matsim.pt.transitSchedule.api.TransitStopFacility
import org.matsim.vehicles.MatsimVehicleReader
import org.matsim.vehicles.MatsimVehicleWriter
import org.matsim.vehicles.Vehicle
import org.matsim.vehicles.VehicleType
import org.matsim.vehicles.VehicleUtils
import org.matsim.vehicles.Vehicles
import org.matsim.vehicles.VehiclesFactory
import org.opengis.feature.simple.SimpleFeature

class CarquefouGenerator {

    static void main(String[] args) {
        generate()
    }

    static void generate() {
        String main_folder = "/home/valoo/Projects/IFSTTAR/Carquefou/"
        String result_folder = "/home/valoo/Projects/IFSTTAR/Carquefou/results/"
        String input_folder = "/home/valoo/Projects/IFSTTAR/Carquefou/inputs/"
        ShapeFileReader shp_reader = new ShapeFileReader()
        Collection<SimpleFeature> line_shp = shp_reader.readFileAndInitialize(main_folder + "line.shp")
        Collection<SimpleFeature> stops_shp = shp_reader.readFileAndInitialize(main_folder + "stops.shp")

        double vehicle_speed_kmh = 20
        double vehicle_speed_ms = vehicle_speed_kmh / 3.6
        double max_interval = 300 // 5 min

        Network network = NetworkUtils.readNetwork(input_folder + "network.xml")
        Node previous_node = null
        Point previous_geom = null
        LineString line_geom = (LineString) ((MultiLineString) line_shp[0].getAttribute(0)).getGeometryN(0)
        List<Id<Link>> route_links_ids = []
        List<Id<Link>> route_links_ids_b = []

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig())
        TransitScheduleReader pt_reader = new TransitScheduleReader(scenario)
        pt_reader.readFile(input_folder + "transit_schedule.xml")
        TransitSchedule pt_ts = scenario.getTransitSchedule()
        TransitScheduleFactory tsf = pt_ts.getFactory()
        List<TransitStopFacility> stop_facilities = []
        List<TransitRouteStop> route_stops = []
        List<TransitStopFacility> stop_facilities_b = []
        List<TransitRouteStop> route_stops_b = []
        double delay = 0.0
        int i=0
        for (SimpleFeature stop in stops_shp) {
            Point geom = (Point) stop.getAttribute(0)
            TransitStopFacility stop_facility = tsf.createTransitStopFacility(Id.create("sncf_stop_" + String.format("%02d", i+1), TransitStopFacility.class), CoordUtils.createCoord(geom.getCoordinate()), false)
            Node current_node = NetworkUtils.createAndAddNode(network, Id<Node>.createNodeId("sncf_node_" + String.format("%02d", i+1)), CoordUtils.createCoord(geom.getCoordinate()))
            Link link_loop = NetworkUtils.createLink(Id<Link>.createLinkId("sncf_link_loop_" + String.format("%02d", i+1)), current_node, current_node, network, 20, 30.0, 9999.0, 2.0)
            link_loop.setAllowedModes(["artificial", "sncf", "stopFacilityLink"] as Set<String>)
            if (previous_node != null) {
                double length = geom.distance(previous_geom)
                Link link = NetworkUtils.createLink(Id<Link>.createLinkId("sncf_link_" + String.format("%02d", i)), previous_node, current_node, network, length, 30.0, 9999.0, 2.0)
                link.setAllowedModes(["artificial", "sncf"] as Set<String>)
                network.addLink(link)
                route_links_ids.add(link.getId())
                delay += length / vehicle_speed_ms
            }
            network.addLink(link_loop)
            route_links_ids.add(link_loop.getId())

            stop_facility.setLinkId(link_loop.getId())
            stop_facilities.add(stop_facility)
            pt_ts.addStopFacility(stop_facility)
            TransitRouteStop route_stop = tsf.createTransitRouteStop(stop_facility, delay, delay)
            route_stops.add(route_stop)

            previous_node = current_node
            previous_geom = geom
            i++
        }

        previous_geom = null
        previous_node = null
        delay = 0.0
        i = 0
        for (SimpleFeature stop in stops_shp.toList().reverse()) {
            Point geom = (Point) stop.getAttribute(0)
            TransitStopFacility stop_facility_b = tsf.createTransitStopFacility(Id.create("sncf_stop_" + String.format("%02d", i+1) + "_b", TransitStopFacility.class), CoordUtils.createCoord(geom.getCoordinate()), false)
            Node current_node = NetworkUtils.createAndAddNode(network, Id<Node>.createNodeId("sncf_node_" + String.format("%02d", i+1) + "_b"), CoordUtils.createCoord(geom.getCoordinate()))
            Link link_loop_b = NetworkUtils.createLink(Id<Link>.createLinkId("sncf_link_loop_" + String.format("%02d", i+1) + "_b"), current_node, current_node, network, 20, 30.0, 9999.0, 2.0)
            link_loop_b.setAllowedModes(["artificial", "sncf", "stopFacilityLink"] as Set<String>)
            if (previous_node != null) {
                double length = geom.distance(previous_geom)
                Link link_b = NetworkUtils.createLink(Id<Link>.createLinkId("sncf_link_" + String.format("%02d", i)+ "_b"), previous_node, current_node, network, length, 30.0, 9999.0, 2.0)
                link_b.setAllowedModes(["artificial", "sncf"] as Set<String>)
                network.addLink(link_b)
                route_links_ids_b.add(link_b.getId())
                delay += length / vehicle_speed_ms
            }
            network.addLink(link_loop_b)
            route_links_ids_b.add(link_loop_b.getId())

            stop_facility_b.setLinkId(link_loop_b.getId())

            stop_facilities_b.add(stop_facility_b)
            pt_ts.addStopFacility(stop_facility_b)
            TransitRouteStop route_stop_b = tsf.createTransitRouteStop(stop_facility_b, delay, delay)
            route_stops_b.add(route_stop_b)

            previous_node = current_node
            previous_geom = geom
            i++
        }

        NetworkUtils.writeNetwork(network, result_folder + "network.xml")

        Vehicles vehicles = VehicleUtils.createVehiclesContainer();
        VehiclesFactory vf = vehicles.getFactory()
        MatsimVehicleReader vehicle_reader = new MatsimVehicleReader(vehicles)
        vehicle_reader.readFile(input_folder + "transit_vehicles.xml")
        VehicleType vehicle_type = vf.createVehicleType(Id.create("Sncf", VehicleType.class))
        vehicle_type.setNetworkMode("car")
        vehicle_type.getCapacity().setSeats(20)
        vehicle_type.setLength(10)
        vehicle_type.setWidth(2.5)
        vehicle_type.setPcuEquivalents(2.8)
        vehicle_type.setFlowEfficiencyFactor(1.0)
        vehicles.addVehicleType(vehicle_type)

        TransitLine transit_line = tsf.createTransitLine(Id.create("sncf_line", TransitLine.class))

        NetworkRoute route_forward = RouteUtils.createNetworkRoute(route_links_ids)
        NetworkRoute route_backward = RouteUtils.createNetworkRoute(route_links_ids_b)
        TransitRoute transit_route_forward = tsf.createTransitRoute(Id.create("sncf_route_forward", TransitRoute.class), route_forward, route_stops, "sncf")
        TransitRoute transit_route_backward = tsf.createTransitRoute(Id.create("sncf_route_backward", TransitRoute.class), route_backward, route_stops_b, "sncf")

        for (int j = 0 ; j < (86400 / max_interval); j++) {
            Vehicle vehicle_f = vf.createVehicle(Id.create("veh_" + String.format("%03d", j) + "_sncf_f", Vehicle.class), vehicle_type)
            vehicles.addVehicle(vehicle_f)
            Departure dep_f = tsf.createDeparture(Id.create("sncf_dep_" + String.format("%03d", j) + "_f", Departure.class), j * max_interval)
            dep_f.setVehicleId(vehicle_f.getId())
            transit_route_forward.addDeparture(dep_f)
            Vehicle vehicle_b = vf.createVehicle(Id.create("veh_" + String.format("%03d", j) + "_sncf_b", Vehicle.class), vehicle_type)
            vehicles.addVehicle(vehicle_b)
            Departure dep_b = tsf.createDeparture(Id.create("sncf_dep_" + String.format("%03d", j) + "_b", Departure.class), j * max_interval)
            dep_b.setVehicleId(vehicle_b.getId())
            transit_route_backward.addDeparture(dep_b)
        }

        transit_line.addRoute(transit_route_forward)
        transit_line.addRoute(transit_route_backward)

        pt_ts.addTransitLine(transit_line)
        TransitScheduleWriter pt_writer = new TransitScheduleWriter(pt_ts)
        pt_writer.writeFile(result_folder + "transit_schedule.xml")

        MatsimVehicleWriter vehicle_writer = new MatsimVehicleWriter(vehicles)
        vehicle_writer.writeFile(result_folder + "transit_vehicles.xml")

    }

}
