// Fill layer — solid shield color with HP-based brightness/opacity.
uniform vec4 shieldColor;
uniform float shieldHPPercent; // 0.0 to 1.0, shield health percentage

vec4 fill_color() {
    // Calculate shield color with charge progress and HP-based brightness
    vec4 finalShieldColor = shieldColor;

    // Make shield brighter as HP decreases (inverse relationship)
    // When shieldHPPercent is 1.0 (full), brightness is 1.0
    // When shieldHPPercent is 0.0 (empty), brightness is 2.0 (twice as bright)
    float hpBrightness = 2.0 - shieldHPPercent;
    finalShieldColor.rgb *= hpBrightness;

    // Make shield less transparent (more opaque) as HP decreases
    // When shieldHPPercent is 1.0 (full), alpha is unchanged (1.0 multiplier)
    // When shieldHPPercent is 0.0 (empty), alpha is increased (1.0 multiplier, fully opaque)
    float hpAlphaMultiplier = 1.0 - (shieldHPPercent * 0.0); // Range from 1.0 to 1.0 (no change)
    // Alternative: make it more opaque only when damaged
    float damageAlphaBoost = (1.0 - shieldHPPercent) * 0.6; // 0.0 to 0.3 boost when damaged
    finalShieldColor.a *= (1.0 + damageAlphaBoost);

    return finalShieldColor;
}
