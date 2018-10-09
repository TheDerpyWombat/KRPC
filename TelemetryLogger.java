import java.io.IOException;
import java.io.PrintWriter;

import org.javatuples.Triplet;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.Stream;
import krpc.client.StreamException;
import krpc.client.services.SpaceCenter;
import krpc.client.services.SpaceCenter.Vessel;
import krpc.client.services.SpaceCenter.VesselSituation;

public class TelemetryLogger 
{
	public static void main(String[] args) throws RPCException, IOException, StreamException, InterruptedException
	{
		Connection connection = Connection.newInstance("Logger");
		SpaceCenter ksc = SpaceCenter.newInstance(connection);
		Vessel v = ksc.getActiveVessel();
		KRPC_Methods m = new KRPC_Methods();
		
		PrintWriter log = new PrintWriter("log.txt");
		
		Stream<Triplet<Double, Double, Double>> alt = connection.addStream(v, "position", v.getOrbit().getBody().getReferenceFrame());
		Stream<Double> longitude = connection.addStream(v.flight(v.getOrbit().getBody().getReferenceFrame()), "getLongitude");
		Stream<VesselSituation> situ = connection.addStream(v, "getSituation");
		Stream<Double> t = connection.addStream(v, "getMET");
		Stream<Triplet<Double, Double, Double>> velocity = connection.addStream(v, "velocity", v.getOrbit().getBody().getReferenceFrame());
		Stream<Double> Gs = connection.addStream(v.flight(v.getOrbit().getBody().getReferenceFrame()), "getGForce");
		
		log.println("\"MET\", \"Longitude\", \"Altitude\", \"Velocity\", \"G-force\"");
		while (/*situ.get() == VesselSituation.SUB_ORBITAL*/ m.getMagnitude(alt.get()) < 670000)
		{
			log.println(t.get() + ", " + longitude.get() + ", " + m.getMagnitude(alt.get()) + ", " + m.getMagnitude(velocity.get()) + ", " + Gs.get());
			Thread.sleep(100);
		}
		
		log.close();
		connection.close();
	}
}
