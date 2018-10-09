import java.io.IOException;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.Stream;
import krpc.client.StreamException;
import krpc.client.services.SpaceCenter;
import krpc.client.services.SpaceCenter.CelestialBody;
import krpc.client.services.SpaceCenter.Node;
import krpc.client.services.SpaceCenter.SASMode;
import krpc.client.services.SpaceCenter.Vessel;

public class MunTransfer 
{
	public static void main(String[] args) throws RPCException, IOException, StreamException, InterruptedException
	{
		Connection connection = Connection.newInstance();
		SpaceCenter ksc = SpaceCenter.newInstance(connection);
		Vessel v = ksc.getActiveVessel();
		KRPC_Methods m = new KRPC_Methods();
		double pi = Math.PI;
		
		CelestialBody mun = null;
		for (CelestialBody c : v.getOrbit().getBody().getSatellites())
		{
			if (c.getName().equals("Mun"))
			{
				mun = c;
				break;
			}
		}
		//double shipPos = v.flight(v.getOrbit().getBody().getReferenceFrame()).getLongitude();
		//double munPos = mun.getOrbit().getLongitudeOfAscendingNode() + mun.getOrbit().getArgumentOfPeriapsis() + mun.getOrbit().getTrueAnomaly();
		double munParam = mun.getOrbit().getLongitudeOfAscendingNode() + mun.getOrbit().getArgumentOfPeriapsis();
		//munPos = munPos % (2 * pi);
		
		double periodF = mun.getOrbit().getPeriod();
		double smaTransfer = (v.getOrbit().getSemiMajorAxis() + mun.getOrbit().getSemiMajorAxis()) / 2;
		double periodT = 2 * pi * Math.sqrt(Math.pow(smaTransfer, 3) / v.getOrbit().getBody().getGravitationalParameter());
		double phaseAngle = pi - (pi * periodT / periodF);
		
		
		Stream<Double> shipPos = connection.addStream(v.flight(v.getOrbit().getBody().getReferenceFrame()), "getLongitude");
		Stream<Double> munomaly = connection.addStream(mun.getOrbit(), "getTrueAnomaly"); // mun-anomaly, mun-omaly
		Stream<Double> kerbinRotation = connection.addStream(v.getOrbit().getBody(), "getRotationAngle");
		double currentAngle = 0;
		//double munAngle = munomaly.get() + munParam;
		//System.out.println(phaseAngle + " " + munParam + " " + munomaly.get() + " " + shipPos.get() + " " + v.getOrbit().getBody().getRotationAngle());
		
		currentAngle = munomaly.get() + munParam - toRad(shipPos.get()) - kerbinRotation.get();
		double diffAngle = currentAngle - phaseAngle;
		diffAngle = diffAngle % (2 * pi);
		if (diffAngle < 0)
		{
			diffAngle += 2 * pi;
		}
		
		double timeToEncounter = v.getOrbit().getPeriod() * diffAngle / (2 * Math.PI);
		double requV = Math.sqrt(v.getOrbit().getBody().getGravitationalParameter() * (2 / v.getOrbit().getSemiMajorAxis() - 1 / smaTransfer));
		double dV = requV - m.getMagnitude(v.velocity(v.getOrbit().getBody().getNonRotatingReferenceFrame()));
		Node node = v.getControl().addNode(ksc.getUT() + timeToEncounter, (float) dV, 0, 0);
		System.out.println(timeToEncounter);
		while (node.getOrbit().getNextOrbit().getPeriapsisAltitude() < 10000)
		{
			node.setPrograde(node.getPrograde() - 0.05f);
		}
		double burnTime = m.burnTime(v, node.getPrograde());
		ksc.setRailsWarpFactor(3);
		v.getControl().setSAS(true);
		Thread.sleep(10);
		v.getControl().setSASMode(SASMode.MANEUVER);
		m.nodeBurn(v, ksc, connection, node);
		/*while(true)
		{
			currentAngle = munomaly.get() + munParam - toRad(shipPos.get()) - kerbinRotation.get();
			currentAngle = currentAngle % (2 * pi);
			if (currentAngle < 0)
			{
				currentAngle += 2 * pi;
			}
			//System.out.println(toDeg(currentAngle));
			Thread.sleep(100);
			if (currentAngle <= phaseAngle)
			{
				//break;
			}
		}*/
		
		connection.close();
	}
	
	public static double toDeg(double x)
	{
		return x * 180 / Math.PI;
	}
	
	public static double toRad(double x)
	{
		return x * Math.PI / 180;
	}
}
