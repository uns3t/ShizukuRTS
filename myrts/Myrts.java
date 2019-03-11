/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package myrts;

import java.util.*;  
import java.math.*;
import ai.abstraction.AbstractAction;
import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Harvest;
import ai.abstraction.pathfinding.AStarPathFinding;
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

    UnitTypeTable m_utt = null;
    
    
    Unit my_base = null;
    Unit enermy_base = null;

    public Myrts(UnitTypeTable utt, PathFinding a_pf) {
        super(a_pf);
        m_utt = utt;
    }
    public Myrts(UnitTypeTable a_utt) {
        this(a_utt, new AStarPathFinding());
    }
    
    public AI clone() {
        return new Myrts(m_utt, pf);
    }
// This will be called once at the beginning of each new game:

    public void reset() {
    }
// Called by microRTS at each game cycle.
// Returns the action the bot wants to execute.

    public PlayerAction getAction(int player, GameState gs) {
        PlayerAction pa = new PlayerAction();
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        List<Unit> HarvestWorkers = new ArrayList<>();
        List<Unit> resources = new ArrayList<>();
//获取我方和敌方初始基地，二人游戏
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == m_utt.getUnitType("Base")) {
                if (u.getPlayer() == player) {
                    my_base = u;
                } else if (u.getPlayer() != player) {
                    enermy_base = u;
                }
            }
        }
//获取资源数
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == m_utt.getUnitType("Resource")) {
                resources.add(u);
            }
        }
        System.out.println("原资源数:"+resources.size());
//挑选离我方基地近的资源作为开采点
        for (int j = 0; j < resources.size(); ++j) {
            if (Math.abs(resources.get(j).getX() - my_base.getX())
                    + Math.abs(resources.get(j).getY() - my_base.getY())
                    > Math.abs(resources.get(j).getX() - enermy_base.getX())
                    + Math.abs(resources.get(j).getY() - enermy_base.getY())) {
                resources.remove(j);
            }
        }
        System.out.println("之后的原资源数:"+resources.size());
//挑选后勤勤和先锋
        List<Unit> offendWorker = new ArrayList<>();
        int harvestWorkerFind = 0;
        if (resources.size() != 0) {           
            for (Unit u : pgs.getUnits()) {
                if (u.getType() == m_utt.getUnitType("Worker")
                        && u.getPlayer() == player) {
                    if (harvestWorkerFind < 1) {
                        HarvestWorkers.add(u);
                        harvestWorkerFind++;
                    } else {
                        offendWorker.add(u);
                    }
                }
            }
        }
        else{
            for (Unit u : pgs.getUnits()) {
                if (u.getType() == m_utt.getUnitType("Worker")
                        && u.getPlayer() == player) {
                    if (harvestWorkerFind < 2) {
                        HarvestWorkers.add(u);
                        harvestWorkerFind++;
                    } else {
                        offendWorker.add(u);
                    }
                }
            }
        }

        
        if (gs.getActionAssignment(my_base) == null && p.getResources()
                > m_utt.getUnitType("Worker").cost) {
            train(my_base, m_utt.getUnitType("Worker"));
        }
//收获资源
        for (int j = 0; j < Math.min(resources.size(), HarvestWorkers.size());
                ++j) {
            if (gs.getActionAssignment(HarvestWorkers.get(j)) == null) {
                harvest(HarvestWorkers.get(j), resources.get(j),my_base);
            }
        }
//没有灵魂地进攻
        List<Unit> enermyUnits = new ArrayList<>();
        for (Unit u : pgs.getUnits()) {
            if (u.getPlayer() != player && u.getPlayer() >= 0) {
                enermyUnits.add(u);
            }
        }
        Integer s = new Random().nextInt(enermyUnits.size());
        for (Unit warrior : offendWorker) {
            if (gs.getActionAssignment(warrior) == null
                    && warrior.getType().canAttack) {
                Unit target = enermyUnits.get(s);
                if (target == null) {
                    break;
                } else {
                    attack(warrior, target);
                }
            }
        }
//抽具化
        return translateActions(player, gs);
    }

    public List<ParameterSpecification> getParameters() {
        return new ArrayList<>();
    }
}
