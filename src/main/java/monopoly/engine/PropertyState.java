package monopoly.engine;

public class PropertyState {
    private Integer ownerPlayerIndex; // null = unowned
    private boolean mortgaged;
    private int buildings; // 0..4 houses, 5 = hotel

    public Integer getOwnerPlayerIndex() { return ownerPlayerIndex; }
    public void setOwnerPlayerIndex(Integer ownerPlayerIndex) { this.ownerPlayerIndex = ownerPlayerIndex; }

    public boolean isMortgaged() { return mortgaged; }
    public void setMortgaged(boolean mortgaged) { this.mortgaged = mortgaged; }

    public int getBuildings() { return buildings; }
    public void setBuildings(int buildings) { this.buildings = buildings; }

    public int getHouses() { return Math.min(buildings, 4); }
    public boolean hasHotel() { return buildings == 5; }
}



