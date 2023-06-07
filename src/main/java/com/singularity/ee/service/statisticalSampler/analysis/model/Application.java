package com.singularity.ee.service.statisticalSampler.analysis.model;

import java.util.ArrayList;
import java.util.List;

public class Application {
    public String name;
    public long id;
    public boolean active;
    public Tier[] tiers;
    public Node[] nodes;

    public void filterTiersDown( String tierName, int minimumNodeCount ) {
        if( tierName == null && minimumNodeCount < 0 ) return;
        List<Tier> tierList = new ArrayList<>();
        for( Tier tier : tiers ) {
            if( tierName != null && tier.name.equals(tierName) ) {
                //tiers = new Tier[]{tier};
                tierList.add(tier);
                break;
            }
            if( tier.numberOfNodes >= minimumNodeCount )
                tierList.add(tier);
        }
        tiers = tierList.toArray(new Tier[0]);
    }

    public String toString() {
        return String.format("Application '%s'(%d) Tiers: %d Nodes: %d", name, id, tiers.length, nodes.length);
    }
}
