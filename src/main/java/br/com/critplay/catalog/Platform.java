package br.com.critplay.catalog;
public enum Platform {
    PC("PC"), PLAYSTATION("PlayStation"), XBOX("Xbox"), NINTENDO("Nintendo");
    private final String label;
    Platform(String label) { this.label = label; }
    public String getLabel() { return label; }
}
