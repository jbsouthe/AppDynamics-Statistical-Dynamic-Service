package com.singularity.ee.service.statisticalSampler.analysis.model;

import java.util.ArrayList;
import java.util.List;

public class Application {
    public String name;
    public long id;
    public boolean active;
    public Tier[] tiers;
    public Node[] nodes;

    public void filterTiersDown( String tierName ) {
        if( tierName == null ) return;
        for( Tier tier : tiers ) {
            if( tier.name.equals(tierName) ) {
                tiers = new Tier[]{tier};
                break;
            }
        }
        if( tiers.length == 0 ) return;
        List<Node> newNodes = new ArrayList<>();
        for( Node node : nodes ) {
            if( node.tierId == tiers[0].id ) newNodes.add(node);
        }
        this.nodes = newNodes.toArray(new Node[0]);
    }

    public String toString() {
        return String.format("Application '%s'(%d) Tiers: %d Nodes: %d", name, id, tiers.length, nodes.length);
    }
}
