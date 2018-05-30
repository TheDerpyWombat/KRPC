import org.javatuples.Triplet;

import krpc.client.RPCException;
import krpc.client.services.SpaceCenter.CelestialBody;
import krpc.client.services.SpaceCenter.Orbit;
import krpc.client.services.SpaceCenter.Vessel;

public class KRPC_Methods 
{
	public double getMagnitude(Triplet<Double, Double, Double> in)
	{
		double result = Math.sqrt(Math.pow(in.getValue0(), 2) + Math.pow(in.getValue1(), 2) + Math.pow(in.getValue2(), 2));
		return result;
	}
	
	public double circularize(Orbit orb) throws RPCException
	{
		CelestialBody planet = orb.getBody();
		double ap = orb.getApoapsis();
		double GM = planet.getGravitationalParameter();
		double targetVelocity = Math.sqrt(GM / ap);
		double sma = orb.getSemiMajorAxis();
		double currentVelocity = Math.sqrt(GM * ((2 / ap) - (1 / sma)));
		double deltaV = targetVelocity - currentVelocity;
		return deltaV;
	}
	
	public double burnTime(Vessel v, double dV) throws RPCException
	{
		double standardG = 9.807;
		double m0 = v.getMass();
		double isp = v.getSpecificImpulse();
		double ve = isp * standardG;
		double mFinal = m0 / (Math.exp(dV / ve));
		double flowRate = v.getAvailableThrust() / (standardG * isp);
		double deltaM = m0 - mFinal;
		double time = deltaM / flowRate;
		return time;
	}
	
	
}
