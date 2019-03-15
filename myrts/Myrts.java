/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package myrts;

import ai.abstraction.AbstractAction;
import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Harvest;
import ai.abstraction.pathfinding.BFSPathFinding;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;


public class Myrts extends AbstractionLayerAI {

    Random r = new Random();
    Unit res; 
    protected UnitTypeTable utt;
    UnitType workerType;
    UnitType baseType;
    UnitType barracks;
    boolean resourse = true;

    // Strategy implemented by this class:
    // If we have more than 1 "Worker": send the extra workers to attack to the nearest enemy unit
    //attack base and barracks first
    // If we have a base: train workers non-stop
    // If we have a worker: do this if needed: build base, harvest resources
    public Myrts(UnitTypeTable a_utt) {
        this(a_utt, new BFSPathFinding());
    }

    public Myrts(UnitTypeTable a_utt, PathFinding a_pf) {
        super(a_pf);
        reset(a_utt);
    }

    public void reset() {
        super.reset();
    }

    public void reset(UnitTypeTable a_utt) {
        utt = a_utt;
        if (utt != null) {
            workerType = utt.getUnitType("Worker");
            baseType = utt.getUnitType("Base");
            barracks = utt.getUnitType("Barracks");
        }
    }

    public AI clone() {
        return new Myrts(utt, pf);
    }

    public PlayerAction getAction(int player, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        PlayerAction pa = new PlayerAction();
//        System.out.println("LightRushAI for player " + player + " (cycle " + gs.getTime() + ")");
        System.out.println("start");
        
        
        
        
        //添加了一点check
        if(p.getResources()==0){
            for (Unit u : pgs.getUnits()) {
                if (u.getType().canAttack
                        && u.getPlayer() == player
                        && gs.getActionAssignment(u) == null) {
                    meleeUnitBehavior(u, p, gs);
                }
            }
            return translateActions(player, gs);
        }
        // behavior of bases:
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == baseType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) {
                baseBehavior(u, p, pgs);
            }
        }
        
        // behavior of melee units:
        //可攻击但不可采集资源，通过workerbehavior调用
        for (Unit u : pgs.getUnits()) {
            if (u.getType().canAttack && !u.getType().canHarvest
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) {
                meleeUnitBehavior(u, p, gs);
            }
        }
        
        // 所有的worker，所以会比较复杂
        List<Unit> workers = new LinkedList<Unit>();
        for (Unit u : pgs.getUnits()) {
            if (u.getType().canHarvest
                    && u.getPlayer() == player) {
                workers.add(u);
            }
        }
        workersBehavior(workers, p, gs);
        System.out.println("resourse: "+resourse);
        System.out.println("base resourse"+p.getResources());
        /*
        for (Unit u : pgs.getUnits()) {
            if (resourse==false
                    &&u.getType().canHarvest
                    && u.getPlayer() == player) {
                meleeUnitBehavior(u, p, gs);
            }
        }
        */
        return translateActions(player, gs);
    }

    public void baseBehavior(Unit u, Player p, PhysicalGameState pgs) {
        //生产worker
        if (p.getResources() >= workerType.cost) {
            train(u, workerType);
        }
    }
    
    public void meleeUnitBehavior(Unit u, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        Unit closestMeleeEnemy = null;
        int closestDistance = 0;
        int enemyDistance = 0;
        int mybase = 0;
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy == null || d < closestDistance) {
                    //这里是获取距离最近的敌人
                    closestEnemy = u2;
                    closestDistance = d;
                }
            } else if (u2.getPlayer() == p.getID() && u2.getType() == baseType) {
                mybase = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
            }
        }
        if (closestEnemy != null) {
            //攻击距离最近的敌人
            attack(u, closestEnemy);
        } else {
            attack(u, null);
        }

    }

    public void workersBehavior(List<Unit> workers, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        int nbases = 0;
        int resourcesUsed = 0;
        //消耗数初始化为0
        Unit harvestWorker = null;
        List<Unit> freeWorkers = new LinkedList<Unit>();
        freeWorkers.addAll(workers);
        //一次check，可以减少计算量
        if (workers.isEmpty()) {
            return;
        }
        //对base进行check
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) {
                nbases++;
            }
        }

        List<Integer> reservedPositions = new LinkedList<Integer>();
        if (nbases == 0 && !freeWorkers.isEmpty() && resourse) {
            // build a base:
            //判断资源数大于base单位的消耗和本回合之前的消耗数之和
            if (p.getResources() >= baseType.cost + resourcesUsed) {
                Unit u = freeWorkers.remove(0);
                //去除第一个worker去建造base
                buildIfNotAlreadyBuilding(u, baseType, u.getX(), u.getY(), reservedPositions, p, pgs);
                resourcesUsed += baseType.cost;
            }
        }
        //再去除一个worker去进行采集
        if (freeWorkers.size() > 0 && resourse) {
            harvestWorker = freeWorkers.remove(0);
        }
        
        // 用于采集的worker行为，harvest为采集
        Unit closestResource = null;
        if (harvestWorker != null) {
            Unit closestBase = null;
            
            int closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isResource) {
                    res=u2;
                    int d = Math.abs(u2.getX() - harvestWorker.getX()) + Math.abs(u2.getY() - harvestWorker.getY());
                    if (closestResource == null || d < closestDistance) {
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }
            closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) {
                //判断是否是仓库
                if (u2.getType().isStockpile && u2.getPlayer() == p.getID()) {
                    int d = Math.abs(u2.getX() - harvestWorker.getX()) + Math.abs(u2.getY() - harvestWorker.getY());
                    if (closestBase == null || d < closestDistance) {
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }
            
            if (closestBase != null&&closestResource != null) {
                AbstractAction aa = getAbstractAction(harvestWorker);
                //将可攻击的worker抽象化为aa
                if (aa instanceof Harvest) {
                    //判断aa是否是harvest类型
                    Harvest h_aa = (Harvest) aa;
                    if (h_aa.getTarget() != closestResource || h_aa.getBase() != closestBase) {
                        harvest(harvestWorker, closestResource, closestBase);
                    } 
                    else {
                        //此else用于占位
                    }
                } 
                else {
                    harvest(harvestWorker, closestResource, closestBase);
                }
                // && (freeWorkers.isEmpty())
            } 
            else if (closestResource == null) {
                //freeWorkers.add(harvestWorker);
                harvest(harvestWorker,closestBase,closestBase);
                System.out.println("test");
            }
            
        }
        
        
        /*
        //下面解决resourse何时为false，，，未完成
        int flag1=0;
        int flag2=0;
        for (Unit u3 : pgs.getUnits()) { 
            if(u3.getType().isResource)
                flag2=1;
            
            if(u3.getType()==workerType&&u3.getResources() != 0)
            {
                flag1 = 1;
            }
        }
        if(closestResource == null&&closestResource == null&&flag1==0&&flag2==0)
            resourse=false;
        */
        
        //将空闲的worker派出进行攻击
        for (Unit u : freeWorkers) {
            meleeUnitBehavior(u, p, gs);
        }

    }

    @Override
    public List<ParameterSpecification> getParameters() {
        List<ParameterSpecification> parameters = new ArrayList<>();

        parameters.add(new ParameterSpecification("PathFinding", PathFinding.class, new BFSPathFinding()));

        return parameters;
    }
}
