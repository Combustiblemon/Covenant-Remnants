// Fill layer — solid shield colour. Damage feedback is the plasma layer (Reach look), not a tint.
uniform vec4 shieldColor;

vec4 fill_color() {
    return shieldColor;
}
