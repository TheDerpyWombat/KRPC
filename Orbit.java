import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.javatuples.Triplet;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.Stream;
import krpc.client.StreamException;
import krpc.client.services.SpaceCenter.Node;
import krpc.client.services.SpaceCenter.Vessel;
import krpc.client.services.*;
import krpc.client.services.SpaceCenter.Engine;
import krpc.client.services.SpaceCenter.Fairing;

public class Orbit 
{
	public static void main(String[] args) throws IOException, RPCException, InterruptedException, StreamException
	{
		Connection connection = Connection.newInstance();
		SpaceCenter ksc = SpaceCenter.newInstance(connection);
		Vessel v = ksc.getActiveVessel();
		v.getAutoPilot().engage();
		v.getAutoPilot().setTargetHeading(90);
		v.getAutoPilot().setTargetPitch(90);
		if (v.getAutoPilot().getTargetRoll() < 45)
		{
			v.getAutoPilot().setTargetRoll(0);
		} else
		{
			v.getAutoPilot().setTargetRoll(90);
		}
		v.getControl().setThrottle(1);
		v.getControl().activateNextStage();
		Stream<Triplet<Double, Double, Double>> speed = connection.addStream(v, "velocity", v.getOrbit().getBody().getReferenceFrame());
		Stream<Float> thrust = connection.addStream(v, "getThrust");
		while (getMagnitude(speed.get()) < 0.1)
		{
			Thread.sleep(100);
			v.getControl().activateNextStage();
		}
		
		double TWR = v.getThrust() / v.getMass();
		double f = 0; // final angle
		double s = 0; // starting angle
		double h = 0; // final height
		double n = 0; // starting height
		double A = 0; // slope
		double B = 0; // shift
		double trigger = 0; // triggers turn
		boolean turning = false;
		boolean finished = false;
		
		if (TWR < 1.2)
		{
			f = 45;
			s = 90;
			h = 20000;
		} else if (TWR >= 1.2 & TWR < 1.5)
		{
			f = 45;
			s = 90;
			h = 10000;
			trigger = 100;
		} else if (TWR >= 1.5 & TWR < 1.8)
		{
			f = 40;
			s = 90;
			h = 10000;
			trigger = 80;
		} else 
		{
			f = 30;
			s = 80;
			h = 15000;
			trigger = 100;
		}
		Stream<Double> ap = connection.addStream(v.getOrbit(), "getApoapsis");
		Stream<Triplet<Double, Double, Double>> alt = connection.addStream(v, "position", v.getOrbit().getBody().getReferenceFrame());
		Stream<Float> pitch = connection.addStream(v.getAutoPilot(), "getTargetPitch");
		List<Engine> eng = v.getParts().getEngines();
		ArrayList<Stream<Boolean>> fuels = new ArrayList<Stream<Boolean>>();
		for (Engine e : eng)
		{
			fuels.add(connection.addStream(e, "getHasFuel"));
		}
		
		while (true) // ascent loop
		{
			if (ap.get() > 675000) // check for target apoapsis, break loop
			{
				v.getControl().setThrottle(0);
				break;
			}
			if (v.getThrust() == 0) // stage if thrust drops
			{
				v.getControl().activateNextStage();
			}
			if (!turning & getMagnitude(speed.get()) >= trigger) // start turn
			{
				turning = true;
				n = getMagnitude(alt.get()) - 600000;
				B = (s * h - f * n) / (h - n);
				A = (f - B) / h;
			}
			Thread.sleep(0);
			if (turning & !finished) // gravity turn
			{
				v.getAutoPilot().setTargetPitch((float) (A * (getMagnitude(alt.get()) - 600000) + B));
			}
			if (turning & pitch.get() < f & !finished) // end turn
			{
				finished = true;
			}
			for (Stream<Boolean> st : fuels)
			{
				if (st.get() == false)
				{
					v.getControl().activateNextStage();
					eng = v.getParts().getEngines();
					fuels.clear();
					for (Engine e : eng)
					{
						fuels.add(connection.addStream(e, "getHasFuel"));
					}
					break;
				}
			}
			Thread.sleep(5);
		}
		v.getAutoPilot().disengage();
		v.getControl().setSAS(true);
		Thread.sleep(1000);
		v.getControl().setSASMode(SpaceCenter.SASMode.PROGRADE);
		
		boolean fairings = false;
		List<Fairing> fairingList = null;
		if (v.getParts().getFairings().size() > 0)
		{
			fairings = true;
			fairingList = v.getParts().getFairings();
			System.out.println(fairingList.size());
		}
		while (getMagnitude(alt.get()) < 670000)
		{
			// wait until over karman line
			if (getMagnitude(alt.get()) > 650000 & fairings)
			{
				for (Fairing fa : fairingList)
				{
					fa.jettison();
				}
				fairings = false;
			}
		}
		
		double finalV = Math.sqrt(v.getOrbit().getBody().getGravitationalParameter() / v.getOrbit().getApoapsis());
		double sma = v.getOrbit().getSemiMajorAxis();
		double currentV = Math.sqrt(v.getOrbit().getBody().getGravitationalParameter() * ((2 / ap.get()) - (1 / sma)));
		double dV = finalV - currentV;
		System.out.println(dV);
		KRPC_Methods methods = new KRPC_Methods();
		double burnTime = methods.burnTime(v, dV);
		System.out.println(burnTime);
		Node node = v.getControl().addNode(ksc.getUT() + v.getOrbit().getTimeToApoapsis(), (float) dV, 0, 0);
		
		Thread.sleep(1000);
		v.getControl().setSASMode(SpaceCenter.SASMode.MANEUVER);
		Stream<Double> timeToAP = connection.addStream(v.getOrbit(), "getTimeToApoapsis");
		while (timeToAP.get() > (burnTime / 2))
		{
			
		}
		v.getControl().setThrottle(1);
		Stream<Triplet<Double,Double,Double>> remainingBurn = connection.addStream(node, "remainingBurnVector", node.getOrbitalReferenceFrame());
		Stream<Float> throttle = connection.addStream(v.getControl(), "getThrottle");
		
		while (remainingBurn.get().getValue1() > 1)
		{
			
			if (throttle.get() < 1)
			{
				v.getControl().setThrottle(1);
			}
			Thread.sleep(0);
			if (thrust.get() == 0)
			{
				v.getControl().activateNextStage();
			}
		}
		v.getControl().setThrottle((float) 0.1);
		while (remainingBurn.get().getValue1() > 0.1)
		{
			
		}
		v.getControl().setThrottle(0);
		node.remove();
		connection.close();
	}
	
	public static double getMagnitude(Triplet<Double, Double, Double> vec)
	{
		double end = Math.sqrt(Math.pow(vec.getValue0(), 2) + Math.pow(vec.getValue1(), 2) + Math.pow(vec.getValue2(), 2));
		return end;
	}
}