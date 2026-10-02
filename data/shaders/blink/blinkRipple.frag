#version 330 core
// Slipspace drive hull ripple, after the Halo 3: ODST carrier jump (c100_intro radiation_flash /
// radiation_crackle): the carrier emits, from hull points walked bow -> stern, short blue sparks
// shot out along the hull normals (radiation_energy, #2141e3 through a fire palette, 40-50 u/s,
// 0.25-0.5 s) and brief plasma puffs (radiation_plasma, plasma_clouds palette, intensity 2-5);
// a soft flash blooms at the bow afterwards. Here: one pass over the ship sprite, masked by its
// alpha, drawn additively - a wavefront band of plasma puffs with a decaying glow behind it, spark
// streaks seeded on a cell grid that fire as the front passes and fly outward from the centreline,
// and the bow flash.
in vec2 vUv;
in vec2 vLocal;        // x: -1..1 across the hull, y: 0 stern .. 1 bow
out vec4 FragColor;

uniform sampler2D hull;
uniform float front;       // wavefront position along the hull: 1 = bow, 0 = stern
uniform float width;       // wavefront half-width, in hull lengths
uniform float tail;        // e-folding length of the glow left behind the front, in hull lengths
uniform float intensity;   // master brightness
uniform float flash;       // bow flash strength 0..1
uniform float time;
uniform vec3 puffColor;    // plasma puffs (violet-blue)
uniform vec3 sparkColor;   // spark streaks (blue-white)

// Spark grid and life (hull lengths of front travel = spark lifetime)
const vec2 CELL = vec2(0.09, 0.035);   // cell size across / along the hull
const float SPARK_TRAVEL = 0.14;       // the front travels this far during a spark's life
const float SPARK_LENGTH = 0.22;       // full streak length, across the hull (x units, -1..1)
const float SPARK_THICKNESS = 0.012;
const float SPARK_CHANCE = 0.55;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * valueNoise(p);
        p = p * 2.03 + vec2(17.1, 9.7);
        amp *= 0.5;
    }
    return v;
}

// Distance from p to the segment a..b.
float segment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
    return length(pa - ba * h);
}

// Spark streaks: cells in a row fire when the front passes their y; each streak flies outward
// (away from the centreline) and fades over SPARK_TRAVEL of front travel.
float sparks(vec2 p) {
    float total = 0.0;
    vec2 cell = floor(p / CELL);
    for (int dx = -2; dx <= 2; dx++) {
        vec2 c = cell + vec2(float(dx), 0.0);
        float h = hash(c + 0.37);
        if (h > SPARK_CHANCE) continue;
        vec2 origin = (c + vec2(hash(c + 1.3), hash(c + 2.7))) * CELL;
        // age in 0..1 as the front moves past the origin
        float age = (origin.y - front) / SPARK_TRAVEL;
        if (age <= 0.0 || age >= 1.0) continue;
        float dir = origin.x >= 0.0 ? 1.0 : -1.0;
        float len = SPARK_LENGTH * (0.5 + 0.5 * hash(c + 5.1)) * age;
        vec2 tip = origin + vec2(dir * len, 0.02 * (hash(c + 7.9) - 0.5));
        float d = segment(p, origin, tip);
        float core = 1.0 - smoothstep(0.0, SPARK_THICKNESS, d);
        float glowS = 1.0 - smoothstep(0.0, SPARK_THICKNESS * 4.0, d);
        float fade = (1.0 - age) * (1.0 - age);
        total += (core + 0.35 * glowS) * fade;
    }
    return total;
}

void main() {
    float mask = texture(hull, vUv).a;
    if (mask < 0.02) discard;

    // Signed distance along the hull from the wavefront: positive where the band has passed (bow side).
    float d = vLocal.y - front;
    float head = exp(-(d * d) / (width * width));
    float passed = smoothstep(0.0, width, d) * exp(-max(d, 0.0) / tail);

    // Plasma puffs: the band and its wake, broken up by scrolling plasma noise.
    float n = fbm(vec2(vLocal.x * 6.0, vLocal.y * 14.0) + vec2(time * 0.35, -time * 0.9));
    float puffs = (head * 1.1 + passed * 0.5) * (0.55 + 0.9 * n);
    vec3 puffTint = mix(puffColor, sparkColor, smoothstep(0.55, 0.85, n));

    float streaks = sparks(vLocal);

    // Bow flash (ODST radiation_flash: soft flash at fx_forward_firing).
    vec2 bow = vec2(vLocal.x * 0.4, vLocal.y - 1.0);
    float flashGlow = flash * exp(-dot(bow, bow) / 0.045);

    vec3 color = puffTint * puffs * 0.9 + sparkColor * streaks * 1.8 + mix(sparkColor, vec3(1.0), 0.5) * flashGlow * 2.0;
    FragColor = vec4(color * mask * intensity, 1.0);
}
