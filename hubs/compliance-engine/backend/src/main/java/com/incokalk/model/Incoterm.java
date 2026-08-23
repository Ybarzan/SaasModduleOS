package com.incokalk.model;

public enum Incoterm {
    EXW("Ex Works",              TransportMode.ANY,      5),
    FCA("Free Carrier",          TransportMode.ANY,      4),
    FAS("Free Alongside Ship",   TransportMode.SEA_ONLY, 4),
    FOB("Free On Board",         TransportMode.SEA_ONLY, 3),
    CFR("Cost and Freight",      TransportMode.SEA_ONLY, 2),
    CIF("Cost Insurance Freight",TransportMode.SEA_ONLY, 2),
    CPT("Carriage Paid To",      TransportMode.ANY,      2),
    CIP("Carriage and Insurance",TransportMode.ANY,      2),
    DAP("Delivered At Place",    TransportMode.ANY,      1),
    DPU("Delivered at Place Unloaded",TransportMode.ANY, 1),
    DDP("Delivered Duty Paid",   TransportMode.ANY,      1);

    public final String fullName;
    public final TransportMode mode;
    public final int buyerRiskScore; // 1=min, 5=max

    Incoterm(String fullName, TransportMode mode, int buyerRiskScore) {
        this.fullName = fullName;
        this.mode = mode;
        this.buyerRiskScore = buyerRiskScore;
    }

    public enum TransportMode { ANY, SEA_ONLY }
}
