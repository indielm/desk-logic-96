package io.anuke.mindustry.world.blocks.logic.CustomBlock;

import io.anuke.arc.graphics.Color;
import io.anuke.arc.math.Mathf;
import io.anuke.mindustry.content.Blocks;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.defense.Door.DoorEntity;
import io.anuke.mindustry.world.blocks.distribution.Sorter.SorterEntity;
import io.anuke.mindustry.world.blocks.logic.CustomBlock.StorageSensor.D2;
import io.anuke.mindustry.world.blocks.power.PowerGraph;

import java.text.DecimalFormat;

import static io.anuke.mindustry.Vars.content;

class Oscillator extends CustomBlock{
    Tile node, node2;
    String sname = "oscillator;";
    long delay, last, delayNano;
    boolean flip;
    Oscillator(Tile tile){
        super(tile);
        addComponent(node = tile.getNearby(0), Blocks.powerNode);
        addComponent(node2 = tile.getNearby(2), Blocks.powerNode);
        delay = 1000;
        try{
            delay = Integer.parseInt((entity.message.split(";")[1]));

        }
        catch(Exception e){
            entity.message += ";[scarlet]"+e.toString();
        }
        delayNano = delay * 100000;
        Call.setMessageBlockText(null,tile,sname + delay);
    }

    @Override
    public void logic(){
        if (System.nanoTime()-last>delayNano){
            last = System.nanoTime();
            flip = !flip;
            digitalWrite(flip, node,node2);
        }
    }
}

class PowerInfo extends CustomBlock{
    String name = "[accent]Power;";
    DecimalFormat df = new DecimalFormat("#,##0.00");
    PowerGraph pg;
    Tile found = null;
    public PowerInfo(Tile tile){
        super(tile);
        nanoWait = 500000000;
    }
    @Override
    public void logic(){
        found = nearbyPowerBlock(tile);
        if (found!=null) {
            pg = found.entity().power.graph;
            float produced = 60 * pg.lastPowerProduced/pg.lastDelta;
            float needed = 60 * pg.lastPowerNeeded/pg.lastDelta;
            String k = "\n[lime]+"
            + df.format(produced) + "\n[scarlet]-" + df.format(needed) + "\n[accent]" + df.format(produced-needed);
            Call.setMessageBlockText(null,tile,name + k);
        }
    }
}

class Diode extends CustomBlock {
    Tile node, node2, battery, battery2;
    String sname = "[accent]Diode; ";

    Diode(Tile tile){
        super(tile);
        nanoWait = 500000000;
        addComponent(node = tile.getNearby(0), Blocks.powerNode);
        addComponent(node2 = tile.getNearby(2), Blocks.powerNode);
        addComponent(battery = tile.getNearby(1), Blocks.battery);
        addComponent(battery2 = tile.getNearby(3), Blocks.battery);
        preventLinks = new Tile[]{node,node2};
    }

    @Override
    void logic(){
        if (battery2.entity().power.graph.getBatteryStored() == 0) {
            flip(battery,battery2);
            Call.setMessageBlockText(null,tile,sname + " flop");
        } else if (battery.entity().power.graph.getBatteryStored() == 0) {
            flip(battery2,battery);
            Call.setMessageBlockText(null,tile,sname + " flip");
        }
    }

    void flip(Tile batt1, Tile batt2){
        Call.unlinkPowerNodes(null,node2,batt1);
        Call.linkPowerNodes(null,node2,batt2);
        Call.unlinkPowerNodes(null,node,batt2);
        Call.linkPowerNodes(null,node,batt1);
    }
}

class Light extends CustomBlock{
    Tile node;
    String name = "[accent]Light; ";
    Color c = Color.white;
    Team sparkTeam;
    Light(Tile tile){
        super(tile);
        nanoWait = 100000000;
        sparkTeam = team;
        addComponent(node = tile.getNearby(0), Blocks.powerNode);
        try{
            c = Color.valueOf(entity.message.split(";")[1]);
            if (entity.message.split(";")[2].contains("spark")) sparkTeam = Team.derelict;
        }
        catch(Exception e){
            entity.message += ";[scarlet]"+e.toString();
        }
    }
    @Override
    void logic(){
        if (digitalRead(node) || node.entity().power.graph.getBatteryStored()>0f)
            Call.createLighting(0,sparkTeam,c,0,tile.worldx() + analogRead(node),tile.worldy(), Mathf.random(360),2);
    }

}

class Breaker extends CustomBlock{
    Tile node, node2;
    PowerGraph pg, pg2;
    String sname;
    Breaker(Tile tile){
        super(tile);
        nanoWait = 10000000;
        addComponent(node = tile.getNearby(0), Blocks.powerNode);
        addComponent(node2 = tile.getNearby(2), Blocks.powerNode);
        sname = "[scarlet]Breaker;";
    }
    @Override
    void logic(){
        pg = node.entity().power.graph;
        pg2 = node2.entity().power.graph;
        if((!digitalRead(node)) && isLinked(node,node2)){
            Call.unlinkPowerNodes(null, node, node2);
            Call.setMessageBlockText(null,tile,sname + off);
        }

        if (analogRead(node)+analogRead(node2)>0){
            Call.linkPowerNodes(null, node, node2);
            Call.setMessageBlockText(null, tile, sname + on);
        }
    }
}

class RisingTrigger extends CustomBlock{
    boolean last, flip;
    Tile trigger, out, out2;
    RisingTrigger(Tile tile){
        super(tile);
        addComponent(trigger = tile.getNearby(2),Blocks.powerNode);
        addComponent(out = tile.getNearby(1),Blocks.powerNode);
        addComponent(out2 = tile.getNearby(3),Blocks.powerNode);
        preventLinks = null;
    }

    @Override
    void logic(){
        boolean read = digitalRead(trigger);
        trigger(read);
        checkUnlink(trigger,out);
        checkUnlink(trigger,out2);
        digitalWrite(flip, out, out2);
    }

    void trigger(boolean read){
        if (read && !last) flip = !flip;
        last = read;
    }
}

class FallingTrigger extends RisingTrigger{
    FallingTrigger(Tile tile){
        super(tile);
    }
    @Override
    void trigger(boolean read){
        if (!read && last) flip = !flip;
        last = read;
    }
}

class DoorDisplay extends CustomBlock {
    Tile[] doors;
    Tile address, value;
    DoorDisplay(Tile tile){
        super(tile);
        doors = new Tile[16];
        for (int i = 0; i < 16; i++) addComponent(doors[i] = to(i%4 + 1,i/4 + 1),Blocks.door);
        addComponent(address = to(-1,0),Blocks.powerNode);
        addComponent(value =  to(0,-1),Blocks.powerNode);
    }
    @Override
    void logic(){
        doorWrite(Mathf.clamp(analogRead(address),0,15), digitalRead(value));
    }

    void doorWrite(int i, boolean state){
        boolean current = ((DoorEntity)doors[i].entity()).open;
        if (current != state) Call.onDoorToggle(null, doors[i], state);
    }
}

class DoorDisplay16 extends CustomBlock {
    Tile[] doors;
    Tile tx,ty, value, sel,out, out2;
    DoorDisplay16(Tile tile){
        super(tile);
        doors = new Tile[16*16];
        for (int i = 0; i < doors.length; i++) addComponent(doors[i] = to(i%16 + 1,i/16 + 1),Blocks.door);
        addComponent(tx = to(-1,0),Blocks.powerNode);
        addComponent(ty = to(0,1),Blocks.powerNode);
        addComponent(sel = to(1,0),Blocks.powerNode);
        addComponent(out = to(3,0),Blocks.powerNode);
        addComponent(out2 = to(2,-1),Blocks.solarPanel);
        addComponent(value =  to(0,-1),Blocks.powerNode);
    }
    @Override
    void logic(){
        int x = Mathf.clamp(analogRead(tx),0,15);
        int y = Mathf.clamp(analogRead(ty),0,15);
        doorWrite(y+x*16, digitalRead(value));
    }

    void doorWrite(int i, boolean state){
        boolean current = ((DoorEntity)doors[i].entity()).open;
        if (current != state) Call.onDoorToggle(null, doors[i], state);
    }
}

class SorterDisplay16 extends CustomBlock {
    Tile[] sorters;
    Tile tx,ty, value;
    SorterDisplay16(Tile tile){
        super(tile);
        sorters = new Tile[16*16];
        for (int i = 0; i < sorters.length; i++) addComponent(sorters[i] = to(i%16 + 1,i/16 + 1),Blocks.sorter);
        addComponent(tx = to(-1,0),Blocks.powerNode);
        addComponent(ty = to(0,1),Blocks.powerNode);
        addComponent(value =  to(0,-1),Blocks.powerNode);
    }
    @Override
    void logic(){
        int x = Mathf.clamp(analogRead(tx),0,15);
        int y = Mathf.clamp(analogRead(ty),0,15);
        sorterWrite(y + x*16, analogRead(value));
    }

    void sorterWrite(int i, int state){
        int current = content.items().indexOf(((SorterEntity)sorters[i].entity()).sortItem);
        if (current != state) Call.setSorterItem(null, sorters[i], content.items().get(Math.max(Math.min(state,15),0)));
    }
}

class SorterDisplayD2 extends CustomBlock {
    Tile[] sorters;
    Tile tx,ty, value;
    int pos = 0;
    SorterDisplayD2(Tile tile){
        super(tile);
        sorters = new Tile[16*16];
        for (int i = 0; i < sorters.length; i++) addComponent(sorters[i] = to(i%16 + 1,i/16 + 1),Blocks.sorter);
        addComponent(tx = to(-1,0),Blocks.powerNode);
        addComponent(ty = to(0,1),Blocks.powerNode);
        addComponent(value =  to(0,-1),Blocks.powerNode);
        ds = new D2(22, this);
    }
    @Override
    void logic(){
        int x = Mathf.clamp(analogRead(tx),0,15);
        int y = Mathf.clamp(analogRead(ty),0,15);
        sorterWrite(y + x*16, analogRead(value));
    }

    void sorterWrite(int i, int state){
        int current = content.items().indexOf(((SorterEntity)sorters[i].entity()).sortItem);
        if (current != state) Call.setSorterItem(null, sorters[i], content.items().get(Math.max(Math.min(state,15),0)));
    }

    class D2 extends DeskSquared{
        D2(int address, CustomBlock c){
            super(value, address, c);
        }
        @Override
        int getData(){
            if (pos>=sorters.length) pos = 0;
            return content.items().indexOf(((SorterEntity)sorters[pos++].entity()).sortItem);
        }
        @Override
        void setData(int d){
            if (d<0){
                pos = 0;
            }
            else {
                sorterWrite(pos++, d);
                if (pos>=sorters.length) pos = 0;
            }
        }
    }
}

class Bit extends CustomLogic{
    Tile door;
    boolean last, flip;
    Bit(Tile tile){
        super(tile);
        addComponent(door = to(1,1),Blocks.door);
    }
    @Override
    void logic(){
        boolean read = digitalRead(in) | digitalRead(in2);
        if (read != last){
            last = read;
            digitalWrite(read,out,out2);
            doorWrite(door,read);
        }

        boolean current = ((DoorEntity)door.entity()).open;
        digitalWrite(!current, out, out2);
    }

    void doorWrite(Tile t, boolean state){
        boolean current = ((DoorEntity)t.entity()).open;
        if (current != state) Call.onDoorToggle(null, door, state);
    }
}