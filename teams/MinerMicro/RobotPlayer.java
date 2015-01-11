package MinerMicro;

import battlecode.common.*;

import java.util.*;

public class RobotPlayer{

	static Direction facing;
	static Random rand;
	static RobotController rc;

	static int cumulativeSpending = 0;

	// Message Channels
	static int oreCountChan = 0;
	static int totalSpendingChan = 1;
	static int cumulativeSpendingChan = 2;
	static int turnCountChan = 3;
	static int oldOreCountChan = 4;
	static int netChan = 5;
	static int oldSpendingChan = 6;
	static int bestMineScoreChan = 7;
	static int bestMineXChan = 8;
	static int bestMineYChan = 9;

	// Adjustable parameters
	static int numBeavers = 16;
	static int numMinerFactories = 4;
	static int numMiners = 40;
	static int numBarracks = 1;
	static int numSoldiers = 0;

	// Main Control Loop****************************************

	public static void run(RobotController myrc) throws GameActionException{

		int oldOreCount = 0;
		int oreCount = 0;
		int net = 0;

		rc = myrc;
		rand = new Random(rc.getID());
		facing = getRandomDirection();//randomize starting direction

		while(true){

			try {

				// HQ Code - always resolves first
				if(rc.getType()==RobotType.HQ){

					// Count net difference in Ore between turn N-1 and N-2
					if (Clock.getRoundNum() > 2){
						net = oreCalculation(oldOreCount,oreCount);
					}
					attackEnemyZero();
					RobotInfo[] ourTeam = rc.senseNearbyRobots(1000, rc.getTeam());
					int n = 0; // current number of beavers
					for(RobotInfo ri: ourTeam){ // count up beavers
						if(ri.type==RobotType.BEAVER){
							n++;
						}
					}
					//should do at HQ at next turn
					int spending = rc.readBroadcast(cumulativeSpendingChan); 
					rc.broadcast(cumulativeSpendingChan, spending + RobotType.BEAVER.oreCost);
					rc.broadcast(totalSpendingChan, spending + RobotType.BEAVER.oreCost);
					int x = rc.readBroadcast(bestMineXChan);
					int y = rc.readBroadcast(bestMineYChan);
					int s = rc.readBroadcast(bestMineScoreChan);
					rc.setIndicatorString(1, "global target =  (" + x + ", " + y + ")");
					rc.setIndicatorString(2, "mine score =  " + s);
					// don't let the best mine last forever: it decays!
					int decay = 1;
					rc.broadcast(bestMineScoreChan, s-decay);
					if(Clock.getRoundNum()<300 && n<numBeavers){ // in the beginning, spawn 'numBeavers' beavers and send them out in all directions
						Direction dir = Direction.values()[n];
						if(rc.isCoreReady() && rc.canSpawn(dir, RobotType.BEAVER)){
							rc.spawn(dir, RobotType.BEAVER);
							rc.broadcast(oreCountChan, (int) rc.getTeamOre());
						}
					}else{
						rc.yield();
					}

				// Tower Code
				}else if(rc.getType()==RobotType.TOWER){
					attackEnemyZero();

				// Beaver Code
				}else if(rc.getType()==RobotType.BEAVER){
					attackEnemyZero();
					if(Clock.getRoundNum()<10){
						moveStraight();
					}else{
						RobotInfo[] ourTeam = rc.senseNearbyRobots(1000, rc.getTeam());
						int n = 0; // current number of miner factories
						int m = 0; // current number of barracks
						for(RobotInfo ri: ourTeam){ // count up miner factories
							if(ri.type==RobotType.MINERFACTORY){
								n++;
							}else if(ri.type==RobotType.BARRACKS){
								m++;
							}
						}
						if(n<numMinerFactories){
							buildUnit(RobotType.MINERFACTORY);
						}else if(m<numBarracks){
							buildUnit(RobotType.BARRACKS);
						}
						mineAndMove();
					}

				// Miner Code
				}else if(rc.getType()==RobotType.MINER){
					attackEnemyZero();
					mineAndMove();

				// Miner Factory Code
				}else if(rc.getType()==RobotType.MINERFACTORY){
					RobotInfo[] ourTeam = rc.senseNearbyRobots(100000, rc.getTeam());
					int n = 0; // current number of miners
					for(RobotInfo ri: ourTeam){ // count up miners
						if(ri.type==RobotType.MINER){
							n++;
						}
					}
					if(n<numMiners){
						spawnUnit(RobotType.MINER);
					}else{
						rc.yield();
					}

				// Barracks Code
				}else if(rc.getType()==RobotType.BARRACKS){
					RobotInfo[] ourTeam = rc.senseNearbyRobots(1000, rc.getTeam());
					int n = 0; // current number of soldiers
					for(RobotInfo ri: ourTeam){ // count up soldiers
						if(ri.type==RobotType.SOLDIER){
							n++;
						}
					}
					if(n<numSoldiers){
						spawnUnit(RobotType.SOLDIER);
					}

				// Soldier Code
				}else if(rc.getType()==RobotType.SOLDIER){
					attackEnemyZero();
					moveAround();
				}

				// All Units Transfer Supplies
				transferSupplies();

			} catch (Exception e) {
				System.out.println("Unexpected exception");
				e.printStackTrace();
			}
			rc.yield();
		}
	}

	private static int oreCalculation(int oldOreCount, int oreCount) throws GameActionException {
		// calculate net ore change
		oldOreCount = rc.readBroadcast(oldOreCountChan); // N-2
		oreCount = (int) rc.getTeamOre(); // N-1 (right now before mining)
		int net = oreCount - oldOreCount;
		rc.broadcast(netChan, net);
		// calculate spending
		int oldSpending = rc.readBroadcast(oldSpendingChan); // N-2
		int totalSpending = rc.readBroadcast(totalSpendingChan); // from turn N-1
		net = rc.readBroadcast(netChan);
		int income = net + totalSpending;
		//rc.setIndicatorString(0, "ore =  " + oreCount);
		//rc.setIndicatorString(0, "totalspending =  " + totalSpending);
		rc.setIndicatorString(0, "income =  " + income);
		// shift N-1 to N-2 slot
		rc.broadcast(oldOreCountChan,oreCount);
		// shift N to N-1 slot needs to be done by units that mine (mineAndMove();)
		// shift N-1 to N-2 slot
		rc.broadcast(oldSpendingChan,totalSpending);
		// shift N to N-1 slot needs to be done by units that build (build();)
		//reset spending counters
		rc.broadcast(cumulativeSpendingChan, 0);
		rc.broadcast(totalSpendingChan,0);
		return net;
	}


	// Supply Transfer Protocol
	private static void transferSupplies() throws GameActionException {
		RobotInfo[] nearbyAllies = rc.senseNearbyRobots(rc.getLocation(),GameConstants.SUPPLY_TRANSFER_RADIUS_SQUARED,rc.getTeam());
		double lowestSupply = rc.getSupplyLevel();
		double transferAmount = 0;
		MapLocation suppliesToThisLocation = null;
		for(RobotInfo ri:nearbyAllies){
			if(ri.supplyLevel<lowestSupply){
				lowestSupply = ri.supplyLevel;
				transferAmount = (rc.getSupplyLevel()-ri.supplyLevel)/2;
				suppliesToThisLocation = ri.location;
			}
		}
		if(suppliesToThisLocation!=null){
			rc.transferSupplies((int)transferAmount, suppliesToThisLocation);
		}
	}

	// Basic Build Unit Command
	// Added accounting for spending
	private static void buildUnit(RobotType type) throws GameActionException {
		if(rc.getTeamOre()>type.oreCost){
			Direction buildDir = getRandomDirection();
			if(rc.isCoreReady()&&rc.canBuild(buildDir, type)){
				rc.build(buildDir, type);
				rc.broadcast(oreCountChan, (int) rc.getTeamOre());
				int spending = rc.readBroadcast(cumulativeSpendingChan); 
				rc.broadcast(cumulativeSpendingChan, spending + type.oreCost);
				rc.broadcast(totalSpendingChan, spending + type.oreCost);
			}
		}
	}

	// Attack First Listed Unit in Range
	private static void attackEnemyZero() throws GameActionException {
		RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(rc.getLocation(),rc.getType().attackRadiusSquared,rc.getTeam().opponent());
		if(nearbyEnemies.length>0){//there are enemies nearby
			//try to shoot at them
			//specifically, try to shoot at enemy specified by nearbyEnemies[0]
			if(rc.isWeaponReady()&&rc.canAttackLocation(nearbyEnemies[0].location)){
				rc.attackLocation(nearbyEnemies[0].location);
			}
		}
	}

	// Basic Spawn Unit Command (random direction)
	private static void spawnUnit(RobotType type) throws GameActionException {
		Direction randomDir = getRandomDirection();
		if(rc.isCoreReady()&&rc.canSpawn(randomDir, type)){
			rc.spawn(randomDir, type);
			rc.broadcast(oreCountChan, (int) rc.getTeamOre());
			//should do at HQ at next turn
			int spending = rc.readBroadcast(cumulativeSpendingChan); 
			rc.broadcast(cumulativeSpendingChan, spending + type.oreCost);
			rc.broadcast(totalSpendingChan, spending + type.oreCost);
		}
	}

	// Movement
	private static void moveStraight() throws GameActionException {
		if(isGoodMovementDirection()){
			//try to move in the facing direction
			if(rc.isCoreReady()&&rc.canMove(facing)){
				rc.move(facing);
			}
		}else{
			facing = facing.rotateLeft();
		}
	}

	private static Direction getRandomDirection() {
		return Direction.values()[(int)(rand.nextDouble()*8)];
	}

	private static void mineAndMove() throws GameActionException {
		if(rc.senseOre(rc.getLocation())>12){ //there is plenty of ore, so try to mine
			if(rc.isCoreReady()&&rc.canMine()){
				rc.mine();
				// Update ore count for this team (only the last unit to resolve matters)
				rc.broadcast(oreCountChan, (int) rc.getTeamOre());
			}
		}else if(rc.senseOre(rc.getLocation())>0.8){ //there is a bit of ore, so maybe try to mine, maybe move on
			if(rand.nextDouble()<0.2){ // mine
				if(rc.isCoreReady()&&rc.canMine()){
					rc.mine();
					// Update ore count for this team (only the last unit to resolve matters)
					rc.broadcast(oreCountChan, (int) rc.getTeamOre());
				}
			}else{ // look for more ore
				facing = minerPotential();
				moveStraight();
			}
		}else{ //no ore, so look for more
			facing = minerPotential();
			moveStraight();
		}
	}


	private static void moveAround() throws GameActionException { // Move Around: random moves; go left if hitting barrier; avoid towers
		if(rand.nextDouble()<0.05){
			if(rand.nextDouble()<0.5){
				facing = facing.rotateLeft();
			}else{
				facing = facing.rotateRight();
			}
		}

		if(isGoodMovementDirection()){
			//try to move in the facing direction
			if(rc.isCoreReady()&&rc.canMove(facing)){
				rc.move(facing);
			}
		}else{
			facing = facing.rotateLeft();
		}
	}

	private static boolean isGoodMovementDirection() throws GameActionException { //checks if the facing direction is "good", meaning safe from towers and not a blockage or off-map or occupied
		MapLocation tileInFront = rc.getLocation().add(facing);
		boolean goodSpace = true;
		//check that we are not facing off the edge of the map or are blocked
		if(rc.senseTerrainTile(tileInFront)!=TerrainTile.NORMAL){
			goodSpace = false;
		}else{
			//check that the space is not occupied by a robot
			if(rc.isLocationOccupied(tileInFront)){
				goodSpace = false; //space occupied
			}else{
				//check that the direction in front is not a tile that can be attacked by the enemy towers
				MapLocation[] enemyTowers = rc.senseEnemyTowerLocations();
				for(MapLocation m: enemyTowers){
					if(m.distanceSquaredTo(tileInFront)<=RobotType.TOWER.attackRadiusSquared){
						goodSpace = false; //space in range of enemy towers
						break;
					}
				}
			}
		}
		return goodSpace;
	}

	// Potential fields for micro ==========================
	
	// Miner's potential field calculation.  Yields an integer representing the movement direction 0-7.  A null value means not to move.
	private static Direction minerPotential() throws GameActionException {
		// just random
		//return (int)(rand.nextDouble()*8);
		
		float mineScore = 0;
		MapLocation here = rc.getLocation();
		
		// nearest-neighbor ore sensing
		//int x1[] = {0,1,1,1,0,-1,-1,-1};
		//int y1[] = {1,1,0,-1,-1,-1,0,1};
		int x1[] = {0,1,0,-1,};
		int y1[] = {1,0,-1,-0};
		float innerPotential[] = vectorSumOreAndMiners(x1,y1);
		mineScore += innerPotential[2];
		
		// next-nearest-neighbor ore sensing
		//int x2[] = {0,2,2,2,0,-2,-2,-2};
		//int y2[] = {2,2,0,-2,-2,-2,0,2};
		int x2[] = {2,2,-2,-2};
		int y2[] = {2,-2,-2,2};
		float outerPotential[] = vectorSumOreAndMiners(x2,y2);
		mineScore += outerPotential[2];

		// global target direction: WORK ON THIS!!!!
		rc.setIndicatorString(1, "mining value =  " + mineScore);
		if(mineScore > (float)rc.readBroadcast(bestMineScoreChan)){
			rc.broadcast(bestMineScoreChan, (int)mineScore);
			rc.broadcast(bestMineXChan, here.x);
			rc.broadcast(bestMineYChan, here.y);
		}

		int targetX = rc.readBroadcast(bestMineXChan);
		int targetY = rc.readBroadcast(bestMineYChan);
		//MapLocation targetMine = new MapLocation(targetX,targetY);
		float globalPullFactor = Math.max(0,mineScore)/50; // pull towards a good mine is proportional to the value at that mine
		float dx = (targetX - here.x)*globalPullFactor;
		float dy = (targetY - here.y)*globalPullFactor;
		float globalPotential[] = {dx,dy};

		// total direction
		float totalPotentialX = innerPotential[0]*10 + outerPotential[0] + globalPotential[0];
		float totalPotentialY = innerPotential[1]*10 + outerPotential[1] + globalPotential[1];
		Direction bestDirection = here.directionTo(here.add((int)totalPotentialX,(int)totalPotentialY)); // direction to move
		if(bestDirection==Direction.OMNI){ // can't decide where to go, don't let it get stuck
			bestDirection = getRandomDirection();
		}
		rc.setIndicatorString(0, "best direction =  " + bestDirection.toString());
		return bestDirection;
	}

	private static float[] vectorSumOreAndMiners(int[] x, int[] y) throws GameActionException {
		MapLocation here = rc.getLocation();
		MapLocation sensingRegion[] = new MapLocation[x.length];
		for(int a=0; a<x.length; a++){
			sensingRegion[a] = here.add(x[a],y[a]);
		}
		double ore = 0;
		int i=0;
		float potentialX = 0;
		float potentialY = 0;
		float mineScore = -80; // makes it so that a flat region of 10 ore will have a score of 0
		for(MapLocation m: sensingRegion){
			ore = rc.senseOre(m);
			ore = (float)ore;
			RobotInfo robo = rc.senseRobotAtLocation(m);
			TerrainTile tile = rc.senseTerrainTile(m);
			mineScore += ore;
			//if(robo.type!=RobotType.MINER || robo.team!=rc.getTeam()){ // if there's a miner there, don't go toward it
			if(robo==null){
				potentialX += ore*x[i];
				potentialY += ore*y[i];
			}else{ // move away from others
				potentialX -= 1*x[i];
				potentialY -= 1*y[i];
			}
			if(!tile.isTraversable()){ // move away from non-traversable terrain and edges of map
				potentialX -= 10*x[i];
				potentialY -= 10*y[i];
			}
			i++;
		}
		float potential[] = {potentialX, potentialY, mineScore};
		return potential;
	}
	
}
