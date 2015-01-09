package AaronMicro;

import java.util.Random;

import battlecode.common.*;

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

	
	// Main Control Loop****************************************
	public static void run(RobotController myrc) throws GameActionException{
		
		int totalSpending = 10;
		int oldSpending = 10;
		int x = 0;
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
							
						// calc net ore change
						oldOreCount = rc.readBroadcast(oldOreCountChan); // N-2
						oreCount = (int) rc.getTeamOre(); // N-1 (right now before mining)
							net = oreCount - oldOreCount;
							
					
						rc.broadcast(netChan, net);
							
						// calc spending
						oldSpending = rc.readBroadcast(oldSpendingChan); // N-2
						totalSpending = rc.readBroadcast(totalSpendingChan); // from turn N-1
							
						net = rc.readBroadcast(netChan);
						int income = net + totalSpending;

						rc.setIndicatorString(0, "ore =  " + oreCount);
						rc.setIndicatorString(1, "totalspending =  " + totalSpending);
						rc.setIndicatorString(2, "income =  " + income);
						
						
						// shift N-1 to N-2 slot
						rc.broadcast(oldOreCountChan,oreCount);
						// shift N to N-1 slot needs to be done by units that mine (mineAndMove();)
						
						
						// shift N-1 to N-2 slot
						rc.broadcast(oldSpendingChan,totalSpending);
						// shift N to N-1 slot needs to be done by units that build (build();)
					
						
						
						//reset spending counters
						rc.broadcast(cumulativeSpendingChan, 0);
						rc.broadcast(totalSpendingChan,0);
			
					}
					
					attackEnemyZero();
					spawnUnit(RobotType.BEAVER);
					
										


					

				// Tower Code
				}else if(rc.getType()==RobotType.TOWER){
					attackEnemyZero();
						
					
				// Beaver Code
				}else if(rc.getType()==RobotType.BEAVER){
					attackEnemyZero();
					if(Clock.getRoundNum()<700){
						buildUnit(RobotType.MINERFACTORY);
					}else{
						buildUnit(RobotType.BARRACKS);
					}
					mineAndMove();
				
				// Miner Code
				}else if(rc.getType()==RobotType.MINER){
					attackEnemyZero();
					mineAndMove();
					
				// Miner Factory Code
				}else if(rc.getType()==RobotType.MINERFACTORY){
					spawnUnit(RobotType.MINER);
				
				// Barracks Code
				}else if(rc.getType()==RobotType.BARRACKS){
					spawnUnit(RobotType.SOLDIER);
				
				// Soldier Code
				}else if(rc.getType()==RobotType.SOLDIER){
					attackEnemyZero();
					moveAround();
				}
				
			// All Units Transfer Supplies	
			transferSupplies();
			
		
			
			// Default Indicator String
			//rc.setIndicatorString(0, "I am a " + rc.getType());
			
			
			
			} catch (Exception e) {
				System.out.println("Unexpected exception");
				e.printStackTrace();
			}
			
			
	

			rc.yield();
		}
		
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

	private static Direction getRandomDirection() {
		return Direction.values()[(int)(rand.nextDouble()*8)];
	}

	private static void mineAndMove() throws GameActionException {
		if(rc.senseOre(rc.getLocation())>1){//there is ore, so try to mine
			if(rc.isCoreReady()&&rc.canMine()){
				rc.mine();
				// Update ore count for this team (only the last unit to resolve matters)
				rc.broadcast(oreCountChan, (int) rc.getTeamOre());
			}
		}else{//no ore, so look for ore
			moveAround();
		}
	}

	// Move Around: random moves; go left if hitting barrier; avoid towers
	private static void moveAround() throws GameActionException {
		if(rand.nextDouble()<0.05){
			if(rand.nextDouble()<0.5){
				facing = facing.rotateLeft();
			}else{
				facing = facing.rotateRight();
			}
		}
		MapLocation tileInFront = rc.getLocation().add(facing);
		
		//check that the direction in front is not a tile that can be attacked by the enemy towers
		MapLocation[] enemyTowers = rc.senseEnemyTowerLocations();
		boolean tileInFrontSafe = true;
		for(MapLocation m: enemyTowers){
			if(m.distanceSquaredTo(tileInFront)<=RobotType.TOWER.attackRadiusSquared){
				tileInFrontSafe = false;
				break;
			}
		}

		//check that we are not facing off the edge of the map
		if(rc.senseTerrainTile(tileInFront)!=TerrainTile.NORMAL||!tileInFrontSafe){
			facing = facing.rotateLeft();
		}else{
			//try to move in the facing direction
			if(rc.isCoreReady()&&rc.canMove(facing)){
				rc.move(facing);
			}
		}
	}
	
}

